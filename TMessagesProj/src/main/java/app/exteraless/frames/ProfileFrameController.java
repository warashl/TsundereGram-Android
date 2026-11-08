package app.exteraless.frames;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.LruCache;

import androidx.core.content.ContextCompat;

import app.exteraless.security.TsundereCrypto;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Контроллер рамок аватаров (Avatar Frames) для TsundereGram.
 * 
 * Рамки накладываются поверх круглой аватарки пользователя в профиле,
 * диалогах, чатах и меню. Рамки синхронизируются через зашифрованный облачный
 * конфигурационный канал @TsundereGram_Banners и видны всем пользователям клиента.
 */
public final class ProfileFrameController {

    private static volatile ProfileFrameController INSTANCE;

    public static final String PRESET_NEWSPAPER = "gazetnaya_vyrezka";
    public static final String PRESET_NONE = "none";

    private static final String PREFS_NAME = "exteraless_frames";
    private static final String KEY_LOCAL_TYPE = "local_frame_type";
    private static final String KEY_LOCAL_URL = "local_frame_url";
    private static final String KEY_REMOTE_FRAMES = "remote_frames_cache";

    // Соотношение внешнего размера рамки к диаметру внутренней аватарки (1.16x)
    public static final float FRAME_SCALE_RATIO = 1.16f;

    public static class FrameMeta {
        public String type = PRESET_NONE;
        public String url = "";

        public FrameMeta() {}

        public FrameMeta(String type, String url) {
            this.type = type != null ? type : PRESET_NONE;
            this.url = url != null ? url : "";
        }

        public boolean hasFrame() {
            return !PRESET_NONE.equalsIgnoreCase(type) && (!TextUtils.isEmpty(type) || !TextUtils.isEmpty(url));
        }
    }

    private final SharedPreferences prefs;
    private final File frameStorageDir;
    private final LruCache<String, Bitmap> bitmapCache = new LruCache<>(20);
    private final Map<String, FrameMeta> remoteFrames = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Boolean> downloading = new ConcurrentHashMap<>();

    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF drawRect = new RectF();

    private Bitmap newspaperPresetBitmap;

    public static ProfileFrameController getInstance() {
        if (INSTANCE == null) {
            synchronized (ProfileFrameController.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ProfileFrameController();
                }
            }
        }
        return INSTANCE;
    }

    private ProfileFrameController() {
        Context context = ApplicationLoader.applicationContext;
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        frameStorageDir = new File(context.getFilesDir(), "custom_frames");
        if (!frameStorageDir.exists()) {
            frameStorageDir.mkdirs();
        }
        loadCachedFrames();
    }

    public long getMyId() {
        return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
    }

    public String getLocalFrameType() {
        return prefs.getString(KEY_LOCAL_TYPE, PRESET_NONE);
    }

    public String getLocalFrameUrl() {
        return prefs.getString(KEY_LOCAL_URL, "");
    }

    public void setLocalFrame(String type, String url) {
        prefs.edit()
                .putString(KEY_LOCAL_TYPE, type)
                .putString(KEY_LOCAL_URL, url)
                .apply();
        refreshAllInterfaces();
    }

    public String getFrameStatusDescription() {
        String type = getLocalFrameType();
        if (PRESET_NEWSPAPER.equals(type)) {
            return "Цундере в сети (Газета)";
        }
        if (!TextUtils.isEmpty(getLocalFrameUrl())) {
            return "Кастомная рамка";
        }
        return "Не установлена";
    }

    public FrameMeta getFrameFor(long userId) {
        if (userId == 0) {
            return null;
        }
        long myId = getMyId();
        if (userId == myId) {
            String localType = getLocalFrameType();
            String localUrl = getLocalFrameUrl();
            if (!PRESET_NONE.equals(localType) || !TextUtils.isEmpty(localUrl)) {
                return new FrameMeta(localType, localUrl);
            }
        }
        // Поиск по SHA-256 хешу
        String userHash = TsundereCrypto.hashUserId(userId);
        FrameMeta meta = remoteFrames.get(userHash);
        if (meta != null) {
            return meta;
        }
        // Fallback на raw id
        return remoteFrames.get(String.valueOf(userId));
    }

    public static boolean isNewspaperFrame(FrameMeta frame) {
        if (frame == null) return false;
        String t = frame.type != null ? frame.type.toLowerCase(java.util.Locale.ROOT) : "";
        String u = frame.url != null ? frame.url.toLowerCase(java.util.Locale.ROOT) : "";
        return t.contains("gazet") || t.contains("newspaper") || u.contains("gazet") || u.contains("newspaper");
    }

    public Bitmap getFrameBitmap(FrameMeta meta) {
        if (meta == null || !meta.hasFrame()) {
            return null;
        }
        if (isNewspaperFrame(meta)) {
            if (newspaperPresetBitmap != null && !newspaperPresetBitmap.isRecycled()) {
                return newspaperPresetBitmap;
            }
            try {
                Context context = ApplicationLoader.applicationContext;
                if (context != null) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inScaled = false;
                    newspaperPresetBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.frame_newspaper, opts);
                    if (newspaperPresetBitmap != null) {
                        return newspaperPresetBitmap;
                    }
                    Drawable d = ContextCompat.getDrawable(context, R.drawable.frame_newspaper);
                    if (d instanceof BitmapDrawable) {
                        newspaperPresetBitmap = ((BitmapDrawable) d).getBitmap();
                        return newspaperPresetBitmap;
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        String url = meta.url;
        if (!TextUtils.isEmpty(url)) {
            Bitmap cached = bitmapCache.get(url);
            if (cached != null && !cached.isRecycled()) {
                return cached;
            }
            File localFile = new File(frameStorageDir, Utilities.MD5(url) + ".png");
            if (localFile.exists() && localFile.length() > 0) {
                try {
                    Bitmap bmp = BitmapFactory.decodeFile(localFile.getAbsolutePath());
                    if (bmp != null) {
                        bitmapCache.put(url, bmp);
                        return bmp;
                    }
                } catch (Throwable ignored) {}
            }
            fetchAndCacheFrameImage(url);
        }
        return null;
    }

    public float getFrameScaleRatio(FrameMeta frame) {
        return getFrameScaleRatio(frame, 0);
    }

    public float getFrameScaleRatio(FrameMeta frame, float avatarSizePx) {
        if (isNewspaperFrame(frame)) {
            return 658f / 428f; // ~1.537f: точное соответствие диаметра отверстия (428px) к ширине холста (658px)
        }
        return FRAME_SCALE_RATIO;
    }

    public void drawFrame(Canvas canvas, long userId, float x, float y, float w, float h) {
        drawFrame(canvas, userId, x, y, w, h, 1.0f, 1.0f);
    }

    public void drawFrame(Canvas canvas, long userId, float x, float y, float w, float h, float alpha) {
        drawFrame(canvas, userId, x, y, w, h, alpha, 1.0f);
    }

    public void drawFrame(Canvas canvas, long userId, float x, float y, float w, float h, float alpha, float scale) {
        if (alpha <= 0.01f || w <= 0 || h <= 0) {
            return;
        }
        FrameMeta frame = getFrameFor(userId);
        if (frame == null || !frame.hasFrame()) {
            return;
        }
        Bitmap bmp = getFrameBitmap(frame);
        if (bmp == null || bmp.isRecycled()) {
            return;
        }

        float scaleRatio = getFrameScaleRatio(frame, w) * (scale > 0 ? scale : 1.0f);
        float cx = x + w / 2.0f;
        float cy = y + h / 2.0f;
        float fw = w * scaleRatio;
        float fh = h * scaleRatio;

        float left = cx - fw / 2.0f;
        float top = cy - fh / 2.0f;
        float right = cx + fw / 2.0f;
        float bottom = cy + fh / 2.0f;

        drawRect.set(left, top, right, bottom);
        framePaint.setAlpha((int) (255 * Utilities.clamp01(alpha)));
        canvas.drawBitmap(bmp, null, drawRect, framePaint);
    }

    public void drawFrameForBounds(Canvas canvas, FrameMeta frame, RectF bounds) {
        drawFrameForBounds(canvas, frame, bounds, 1.0f);
    }

    public void drawFrameForBounds(Canvas canvas, FrameMeta frame, RectF bounds, float alpha) {
        drawFrameForBounds(canvas, frame, bounds, alpha, 1.0f);
    }

    public void drawFrameForBounds(Canvas canvas, FrameMeta frame, RectF bounds, float alpha, float scale) {
        if (frame == null || !frame.hasFrame() || bounds == null || alpha <= 0.01f) {
            return;
        }
        Bitmap bmp = getFrameBitmap(frame);
        if (bmp == null || bmp.isRecycled()) {
            return;
        }
        float scaleRatio = getFrameScaleRatio(frame, bounds.width()) * (scale > 0 ? scale : 1.0f);
        float w = bounds.width();
        float h = bounds.height();
        float cx = bounds.centerX();
        float cy = bounds.centerY();
        float fw = w * scaleRatio;
        float fh = h * scaleRatio;

        float left = cx - fw / 2.0f;
        float top = cy - fh / 2.0f;
        float right = cx + fw / 2.0f;
        float bottom = cy + fh / 2.0f;

        drawRect.set(left, top, right, bottom);
        framePaint.setAlpha((int) (255 * Utilities.clamp01(alpha)));
        canvas.drawBitmap(bmp, null, drawRect, framePaint);
    }

    public void applyRemoteFrames(JSONObject framesObj) {
        if (framesObj == null) {
            return;
        }
        try {
            Iterator<String> keys = framesObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject item = framesObj.optJSONObject(key);
                if (item != null) {
                    String type = item.optString("type", PRESET_NONE);
                    String url = item.optString("url", "");
                    remoteFrames.put(key, new FrameMeta(type, url));
                    if (!TextUtils.isEmpty(url)) {
                        fetchAndCacheFrameImage(url);
                    }
                }
            }
            prefs.edit().putString(KEY_REMOTE_FRAMES, framesObj.toString()).apply();
            refreshAllInterfaces();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void loadCachedFrames() {
        try {
            String cached = prefs.getString(KEY_REMOTE_FRAMES, null);
            if (!TextUtils.isEmpty(cached)) {
                JSONObject obj = new JSONObject(cached);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONObject item = obj.optJSONObject(key);
                    if (item != null) {
                        remoteFrames.put(key, new FrameMeta(item.optString("type"), item.optString("url")));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private void fetchAndCacheFrameImage(String imageUrl) {
        if (TextUtils.isEmpty(imageUrl)) return;
        if (Boolean.TRUE.equals(downloading.putIfAbsent(imageUrl, Boolean.TRUE))) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                if (!frameStorageDir.exists()) frameStorageDir.mkdirs();
                File localFile = new File(frameStorageDir, Utilities.MD5(imageUrl) + ".png");
                if (!localFile.exists() || localFile.length() == 0) {
                    String actualUrl = imageUrl;
                    if (!TextUtils.isEmpty(actualUrl) && actualUrl.startsWith("tgfile:")) {
                        actualUrl = app.exteraless.banners.ProfileBannerController.getTelegramFileUrl(actualUrl.substring(7));
                    }
                    if (TextUtils.isEmpty(actualUrl)) {
                        return;
                    }
                    HttpURLConnection conn = (HttpURLConnection) new URL(actualUrl).openConnection();
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    if (conn.getResponseCode() == 200) {
                        File tmp = new File(frameStorageDir, localFile.getName() + ".tmp");
                        try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(tmp)) {
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) != -1) {
                                out.write(buf, 0, len);
                            }
                        }
                        tmp.renameTo(localFile);
                    }
                }
                if (localFile.exists() && localFile.length() > 0) {
                    Bitmap bmp = BitmapFactory.decodeFile(localFile.getAbsolutePath());
                    if (bmp != null) {
                        bitmapCache.put(imageUrl, bmp);
                        refreshAllInterfaces();
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                downloading.remove(imageUrl);
            }
        });
    }

    public void setRemoteFrame(long userId, String type, String url, Runnable onSuccess, Consumer<String> onError) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String botToken = TsundereCrypto.getBotToken();
                String channel = com.exteragram.messenger.badges.BadgesController.CHANNEL_USERNAME;

                // 1. Получаем закреплённое сообщение
                URL getUrl = new URL("https://api.telegram.org/bot" + botToken + "/getChat?chat_id=" + channel);
                HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
                getConn.setRequestMethod("GET");
                getConn.setConnectTimeout(8000);
                getConn.setReadTimeout(8000);

                int getCode = getConn.getResponseCode();
                if (getCode != 200) {
                    if (onError != null) AndroidUtilities.runOnUIThread(() -> onError.accept("HTTP " + getCode));
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(getConn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject resp = new JSONObject(sb.toString());
                JSONObject result = resp.getJSONObject("result");
                JSONObject pinned = result.optJSONObject("pinned_message");
                int messageId = pinned != null ? pinned.optInt("message_id", 26) : 26;
                String currentText = app.exteraless.security.CloudConfigStore.readText(pinned);

                // Расшифровка
                String jsonContent = "";
                int idx = currentText.indexOf('\n');
                if (idx >= 0) {
                    jsonContent = TsundereCrypto.decrypt(currentText.substring(idx + 1).trim());
                } else {
                    jsonContent = TsundereCrypto.decrypt(currentText);
                }

                JSONObject configJson;
                int sb1 = jsonContent.indexOf('{');
                int sb2 = jsonContent.lastIndexOf('}');
                if (sb1 >= 0 && sb2 > sb1) {
                    configJson = new JSONObject(jsonContent.substring(sb1, sb2 + 1));
                } else {
                    configJson = new JSONObject();
                }

                JSONObject framesObj = configJson.optJSONObject("frames");
                if (framesObj == null) {
                    framesObj = new JSONObject();
                    configJson.put("frames", framesObj);
                }

                String userKey = TsundereCrypto.hashUserId(userId);
                if (PRESET_NONE.equals(type) && TextUtils.isEmpty(url)) {
                    framesObj.remove(userKey);
                    framesObj.remove(String.valueOf(userId));
                    remoteFrames.remove(userKey);
                    remoteFrames.remove(String.valueOf(userId));
                } else {
                    JSONObject item = new JSONObject();
                    item.put("type", type != null ? type : PRESET_NONE);
                    item.put("url", url != null ? url : "");
                    framesObj.put(userKey, item);
                    remoteFrames.put(userKey, new FrameMeta(type, url));
                }

                // Шифруем
                String encryptedPayload = TsundereCrypto.encrypt(configJson.toString());
                String updatedText = com.exteragram.messenger.badges.BadgesController.CONFIG_HEADER + "\nENC:" + encryptedPayload;

                // Если конфигурация идентична текущей — облако уже актуально!
                if (updatedText.equals(currentText)) {
                    AndroidUtilities.runOnUIThread(() -> {
                        refreshAllInterfaces();
                        if (onSuccess != null) onSuccess.run();
                    });
                    return;
                }

                // 2. Отправляем в Telegram
                // Текст до 4000 символов — editMessageText, больше — документ в закрепе (см. CloudConfigStore)
                HttpURLConnection editConn = app.exteraless.security.CloudConfigStore.sendConfig(channel, messageId, pinned, updatedText);

                int editCode = editConn.getResponseCode();
                if (editCode == 200) {
                    AndroidUtilities.runOnUIThread(() -> {
                        refreshAllInterfaces();
                        if (onSuccess != null) onSuccess.run();
                    });
                } else {
                    String errorBody = "";
                    try (InputStream es = editConn.getErrorStream()) {
                        if (es != null) {
                            BufferedReader errReader = new BufferedReader(new InputStreamReader(es, StandardCharsets.UTF_8));
                            StringBuilder errSb = new StringBuilder();
                            String errLine;
                            while ((errLine = errReader.readLine()) != null) errSb.append(errLine);
                            errorBody = errSb.toString();
                        }
                    } catch (Throwable ignore) {}

                    if (errorBody.contains("message is not modified")) {
                        // В Telegram API это HTTP 400, но данные уже идентичны и сохранены!
                        AndroidUtilities.runOnUIThread(() -> {
                            refreshAllInterfaces();
                            if (onSuccess != null) onSuccess.run();
                        });
                    } else {
                        String errorMsg = "Edit error HTTP " + editCode;
                        try {
                            JSONObject errJson = new JSONObject(errorBody);
                            if (errJson.has("description")) {
                                errorMsg = errJson.getString("description");
                            }
                        } catch (Throwable ignore) {}
                        final String finalErrorMsg = errorMsg;
                        if (onError != null) AndroidUtilities.runOnUIThread(() -> onError.accept(finalErrorMsg));
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
                if (onError != null) AndroidUtilities.runOnUIThread(() -> onError.accept(t.getMessage()));
            }
        });
    }

    public static void refreshAllInterfaces() {
        AndroidUtilities.runOnUIThread(() -> {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                NotificationCenter.getInstance(a).postNotificationName(
                        NotificationCenter.updateInterfaces,
                        MessagesController.UPDATE_MASK_AVATAR | MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_STATUS
                );
            }
        });
    }
}
