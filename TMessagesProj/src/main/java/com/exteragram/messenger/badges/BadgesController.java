package com.exteragram.messenger.badges;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.InputType;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.exteragram.messenger.api.dto.BadgeDTO;
import com.exteragram.messenger.badges.source.ApiBadgeSource;
import com.exteragram.messenger.utils.text.LocaleUtils;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import app.exteraless.security.TsundereCrypto;
import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Контроллер значков TsundereGram.
 * 
 * Значки хранятся в облаке (в закреплённом конфигурационном сообщении канала @TsundereGram_Banners),
 * кешируются в SharedPreferences и отображаются у всех пользователей TsundereGram.
 * Добавлять и удалять значки может только создатель (ID 5133421262) и назначенные администраторы значков.
 */
public final class BadgesController {

    public static final BadgesController INSTANCE = new BadgesController();

    public static final long DEV_ID_1 = 5133421262L;
    public static final long DEV_ID_2 = 1635441761L;
    public static final String TSUNDERE_DEV_TEXT = "Этот человек один из разработчиков TsundereGram";

    public static final long LITTLE_TSUNDERE_ID = 5231982801L;
    public static final String LITTLE_TSUNDERE_TEXT = "маленькая цундере";

    public static final String BOT_TOKEN = TsundereCrypto.getBotToken();
    public static final String CHANNEL_USERNAME = "@TsundereGram_Banners";
    public static final String CONFIG_HEADER = "#TSUNDERE_BADGES_CONFIG";

    private static final ApiBadgeSource apiBadgeSource = new ApiBadgeSource();

    private static final String PREFS_NAME = "exteraless_badges";
    private static final String KEY_DOCUMENT = "badge_document_";
    private static final String KEY_TEXT = "badge_text_";
    private static final String KEY_REMOTE_JSON = "remote_badges_json";

    private final ConcurrentHashMap<Long, BadgeDTO> remoteBadges = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BadgeDTO> remoteBadgesByHash = new ConcurrentHashMap<>();
    private final Set<String> adminHashes = ConcurrentHashMap.newKeySet();
    private final Set<String> clientUsers = ConcurrentHashMap.newKeySet();
    private volatile long lastLoadTime = 0;
    private volatile boolean isLoading = false;

    public boolean isClientUser(long id) {
        return false;
    }

    public BadgeDTO createTsundereClientBadge() {
        return null;
    }


    public static boolean isCreator(long id) {
        return id == DEV_ID_1;
    }

    public boolean isBadgeAdmin(long id) {
        if (id == 0) return false;
        if (isCreator(id)) return true;
        String hash = TsundereCrypto.hashUserId(id);
        return adminHashes.contains(hash) || adminHashes.contains(String.valueOf(id));
    }

    public boolean canManageBadges(long id) {
        return isBadgeAdmin(id);
    }

    public Set<String> getAdminHashes() {
        return adminHashes;
    }

    public static boolean isTsundereDev(long id) {
        return id == DEV_ID_1 || id == DEV_ID_2;
    }

    public static boolean isTsundereDev(TLObject object) {
        if (object instanceof TLRPC.User) {
            return isTsundereDev(((TLRPC.User) object).id);
        }
        return false;
    }

    public static boolean isTsundereLittle(long id) {
        return id == LITTLE_TSUNDERE_ID;
    }

    public static boolean isTsundereLittle(TLObject object) {
        if (object instanceof TLRPC.User) {
            return isTsundereLittle(((TLRPC.User) object).id);
        }
        return false;
    }

    public static boolean isCustomBadge(long id) {
        if (isTsundereDev(id) || isTsundereLittle(id)) {
            return true;
        }
        BadgeDTO badge = INSTANCE.getBadgeFor(id);
        return badge != null && (badge.isCustomBadge() || badge.isTsundereExtera());
    }

    public static boolean isCustomBadge(TLObject object) {
        if (object instanceof TLRPC.User) {
            return isCustomBadge(((TLRPC.User) object).id);
        }
        return false;
    }

    public static BadgeDTO createTsundereDevBadge() {
        return new BadgeDTO(BadgeDTO.TSUNDERE_DEV_DOC_ID, TSUNDERE_DEV_TEXT);
    }

    public static BadgeDTO createTsundereLittleBadge() {
        return new BadgeDTO(BadgeDTO.TSUNDERE_LITTLE_DOC_ID, LITTLE_TSUNDERE_TEXT);
    }

    public static class TsundereBadgeDrawable extends Drawable {
        private final Drawable inner;
        private final Bitmap bitmap;
        private final int size;
        private final Paint paint;

        public TsundereBadgeDrawable(Bitmap bitmap, int sizeDp) {
            this.bitmap = bitmap;
            this.inner = null;
            this.size = AndroidUtilities.dp(sizeDp);
            this.paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            setBounds(0, 0, size, size);
        }

        public TsundereBadgeDrawable(int resId, int sizeDp) {
            this(resId, sizeDp, 0);
        }

        public TsundereBadgeDrawable(int resId, int sizeDp, int tintColor) {
            this.bitmap = null;
            this.paint = null;
            Context context = ApplicationLoader.applicationContext;
            Drawable d = ContextCompat.getDrawable(context, resId);
            if (d != null) {
                inner = d.mutate();
                if (tintColor != 0) {
                    inner.setColorFilter(new PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN));
                }
            } else {
                inner = null;
            }
            size = AndroidUtilities.dp(sizeDp);
            setBounds(0, 0, size, size);
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            if (bitmap != null && !bitmap.isRecycled()) {
                canvas.drawBitmap(bitmap, null, getBounds(), paint);
            } else if (inner != null) {
                inner.setBounds(getBounds());
                inner.draw(canvas);
            }
        }

        @Override
        public void setAlpha(int alpha) {
            if (inner != null) {
                inner.setAlpha(alpha);
            }
            if (paint != null) {
                paint.setAlpha(alpha);
            }
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            // NO-OP! Badges are full color custom art, never apply monochrome tint!
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    private final LruCache<String, Bitmap> badgeBitmaps = new LruCache<>(50);
    private final ConcurrentHashMap<String, Boolean> downloadingBadges = new ConcurrentHashMap<>();
    private final File badgeStorageDir = new File(ApplicationLoader.applicationContext.getFilesDir(), "custom_badges");

    public Bitmap getOrDownloadBadgeBitmap(String imageUrl) {
        if (TextUtils.isEmpty(imageUrl)) return null;
        Bitmap cached = badgeBitmaps.get(imageUrl);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        if (!badgeStorageDir.exists()) {
            badgeStorageDir.mkdirs();
        }
        File localFile = new File(badgeStorageDir, Utilities.MD5(imageUrl) + ".png");
        if (localFile.exists() && localFile.length() > 0) {
            try {
                Bitmap bmp = BitmapFactory.decodeFile(localFile.getAbsolutePath());
                if (bmp != null) {
                    badgeBitmaps.put(imageUrl, bmp);
                    return bmp;
                }
            } catch (Throwable ignored) {}
        }
        fetchAndCacheBadgeImage(imageUrl);
        return null;
    }

    public void fetchAndCacheBadgeImage(String imageUrl) {
        if (TextUtils.isEmpty(imageUrl)) return;
        if (Boolean.TRUE.equals(downloadingBadges.putIfAbsent(imageUrl, Boolean.TRUE))) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                if (!badgeStorageDir.exists()) badgeStorageDir.mkdirs();
                File localFile = new File(badgeStorageDir, Utilities.MD5(imageUrl) + ".png");
                if (!localFile.exists() || localFile.length() == 0) {
                    String actualUrl = imageUrl;
                    if (!TextUtils.isEmpty(actualUrl) && actualUrl.startsWith("tgfile:")) {
                        actualUrl = app.exteraless.banners.ProfileBannerController.getTelegramFileUrl(actualUrl.substring(7));
                    }
                    if (TextUtils.isEmpty(actualUrl)) {
                        return;
                    }
                    HttpURLConnection conn = (HttpURLConnection) new URL(actualUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36");
                    conn.setRequestProperty("Accept", "image/webp,image/png,image/svg+xml,image/*,*/*;q=0.8");
                    conn.setInstanceFollowRedirects(true);
                    conn.setConnectTimeout(15000);
                    conn.setReadTimeout(15000);
                    if (conn.getResponseCode() == 200) {
                        File tmp = new File(badgeStorageDir, localFile.getName() + ".tmp");
                        InputStream in = conn.getInputStream();
                        FileOutputStream out = new FileOutputStream(tmp);
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = in.read(buf)) != -1) {
                            out.write(buf, 0, len);
                        }
                        out.close();
                        in.close();
                        tmp.renameTo(localFile);
                    }
                }
                if (localFile.exists() && localFile.length() > 0) {
                    Bitmap bmp = BitmapFactory.decodeFile(localFile.getAbsolutePath());
                    if (bmp != null) {
                        badgeBitmaps.put(imageUrl, bmp);
                        AndroidUtilities.runOnUIThread(() -> {
                            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                                NotificationCenter.getInstance(a).postNotificationName(
                                        NotificationCenter.updateInterfaces,
                                        MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_STATUS
                                );
                            }
                        });
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                downloadingBadges.remove(imageUrl);
            }
        });
    }

    public void cacheCustomBadgeDirectly(String imageUrl, Bitmap bitmap) {
        if (!TextUtils.isEmpty(imageUrl) && bitmap != null) {
            badgeBitmaps.put(imageUrl, bitmap);
        }
    }

    public static String extractFileId(JSONObject resObj) {
        if (resObj == null) return null;
        JSONObject doc = resObj.optJSONObject("document");
        if (doc != null && !TextUtils.isEmpty(doc.optString("file_id", null))) {
            return doc.optString("file_id", null);
        }
        JSONObject video = resObj.optJSONObject("video");
        if (video != null && !TextUtils.isEmpty(video.optString("file_id", null))) {
            return video.optString("file_id", null);
        }
        JSONObject animation = resObj.optJSONObject("animation");
        if (animation != null && !TextUtils.isEmpty(animation.optString("file_id", null))) {
            return animation.optString("file_id", null);
        }
        org.json.JSONArray photos = resObj.optJSONArray("photo");
        if (photos != null && photos.length() > 0) {
            JSONObject p = photos.optJSONObject(photos.length() - 1);
            if (p != null && !TextUtils.isEmpty(p.optString("file_id", null))) {
                return p.optString("file_id", null);
            }
        }
        JSONObject audio = resObj.optJSONObject("audio");
        if (audio != null && !TextUtils.isEmpty(audio.optString("file_id", null))) {
            return audio.optString("file_id", null);
        }
        return findFileIdRecursive(resObj);
    }

    private static String findFileIdRecursive(JSONObject obj) {
        if (obj == null) return null;
        if (obj.has("file_id")) {
            String fid = obj.optString("file_id", null);
            if (!TextUtils.isEmpty(fid)) return fid;
        }
        java.util.Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object val = obj.opt(key);
            if (val instanceof JSONObject) {
                String fid = findFileIdRecursive((JSONObject) val);
                if (!TextUtils.isEmpty(fid)) return fid;
            } else if (val instanceof org.json.JSONArray) {
                org.json.JSONArray arr = (org.json.JSONArray) val;
                for (int i = arr.length() - 1; i >= 0; i--) {
                    Object item = arr.opt(i);
                    if (item instanceof JSONObject) {
                        String fid = findFileIdRecursive((JSONObject) item);
                        if (!TextUtils.isEmpty(fid)) return fid;
                    }
                }
            }
        }
        return null;
    }

    public static String uploadToTelegram(File file) throws Exception {
        String boundary = "===TgDoc" + System.currentTimeMillis() + "===";
        URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendDocument");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setChunkedStreamingMode(65536);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(120000);

        DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
        dos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        dos.write("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        dos.write((CHANNEL_USERNAME + "\r\n").getBytes(StandardCharsets.UTF_8));

        dos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        dos.write(("Content-Disposition: form-data; name=\"document\"; filename=\"" + file.getName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        dos.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[16384];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            dos.write(buffer, 0, bytesRead);
        }
        fis.close();
        dos.write("\r\n".getBytes(StandardCharsets.UTF_8));
        dos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        dos.flush();
        dos.close();

        int code = conn.getResponseCode();
        if (code == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = reader.readLine()) != null) sb.append(l);
            reader.close();

            JSONObject resp = new JSONObject(sb.toString());
            if (resp.optBoolean("ok")) {
                JSONObject resObj = resp.optJSONObject("result");
                String fileId = extractFileId(resObj);
                if (fileId != null) {
                    try {
                        URL getFileUrl = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/getFile?file_id=" + java.net.URLEncoder.encode(fileId, "UTF-8"));
                        HttpURLConnection gfConn = (HttpURLConnection) getFileUrl.openConnection();
                        gfConn.setConnectTimeout(15000);
                        gfConn.setReadTimeout(15000);
                        if (gfConn.getResponseCode() == 200) {
                            BufferedReader gfReader = new BufferedReader(new InputStreamReader(gfConn.getInputStream(), StandardCharsets.UTF_8));
                            StringBuilder gfsb = new StringBuilder();
                            while ((l = gfReader.readLine()) != null) gfsb.append(l);
                            gfReader.close();

                            JSONObject gfResp = new JSONObject(gfsb.toString());
                            if (gfResp.optBoolean("ok")) {
                                String filePath = gfResp.getJSONObject("result").getString("file_path");
                                return "https://api.telegram.org/file/bot" + BOT_TOKEN + "/" + filePath;
                            }
                        }
                    } catch (Throwable t) {
                        FileLog.e("getFile failed: " + t);
                    }
                    return "tgfile:" + fileId;
                }
            }
            String desc = resp.optString("description", "Не удалось получить file_id из ответа Telegram");
            throw new Exception(desc);
        }
        throw new Exception("Telegram Bot HTTP " + code);
    }

    public static String uploadToLitterbox(File file) throws Exception {
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        URL url = new URL("https://litterbox.catbox.moe/resources/internals/api.php");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36");
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setChunkedStreamingMode(65536);
        conn.setConnectTimeout(25000);
        conn.setReadTimeout(120000);

        DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
        dos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        dos.write("Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        dos.write("fileupload\r\n".getBytes(StandardCharsets.UTF_8));

        dos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        dos.write("Content-Disposition: form-data; name=\"time\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        dos.write("72h\r\n".getBytes(StandardCharsets.UTF_8));

        dos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        dos.write(("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + file.getName() + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        dos.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            dos.write(buffer, 0, bytesRead);
        }
        fis.close();
        dos.write("\r\n".getBytes(StandardCharsets.UTF_8));
        dos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        dos.flush();
        dos.close();

        int code = conn.getResponseCode();
        if (code == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String result = reader.readLine();
            reader.close();
            if (result != null && result.startsWith("http")) {
                return result.trim();
            }
        }
        throw new Exception("Litterbox HTTP " + code);
    }

    public static String uploadToCatbox(File file) throws Exception {
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        URL url = new URL("https://catbox.moe/user/api.php");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36");
        conn.setRequestProperty("Accept", "*/*");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setChunkedStreamingMode(65536);
        conn.setConnectTimeout(25000);
        conn.setReadTimeout(120000);

        DataOutputStream dos = new DataOutputStream(conn.getOutputStream());
        dos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        dos.write("Content-Disposition: form-data; name=\"reqtype\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        dos.write("fileupload\r\n".getBytes(StandardCharsets.UTF_8));

        String fileName = file.getName();
        dos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        dos.write(("Content-Disposition: form-data; name=\"fileToUpload\"; filename=\"" + fileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        dos.write("Content-Type: application/octet-stream\r\n\r\n".getBytes(StandardCharsets.UTF_8));

        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            dos.write(buffer, 0, bytesRead);
        }
        fis.close();
        dos.write("\r\n".getBytes(StandardCharsets.UTF_8));
        dos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        dos.flush();
        dos.close();

        int code = conn.getResponseCode();
        if (code == 200) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            String result = reader.readLine();
            reader.close();
            if (result != null && result.startsWith("http")) {
                return result.trim();
            }
        }
        throw new Exception("Catbox HTTP " + code);
    }

    public static String uploadMedia(File file) throws Exception {
        try {
            return uploadToTelegram(file);
        } catch (Throwable t1) {
            FileLog.e("uploadToTelegram failed, falling back to Catbox: " + t1);
            try {
                return uploadToCatbox(file);
            } catch (Throwable t2) {
                FileLog.e("uploadToCatbox failed, falling back to Litterbox: " + t2);
                try {
                    return uploadToLitterbox(file);
                } catch (Throwable t3) {
                    FileLog.e("uploadToLitterbox failed: " + t3);
                    throw new Exception("Ошибка загрузки медиа: " + (t1.getMessage() != null ? t1.getMessage() : "сеть недоступна"));
                }
            }
        }
    }

    public Drawable getTsundereBadgeDrawable(int sizeDp) {
        return new TsundereBadgeDrawable(R.drawable.tsundere_dev_badge, sizeDp);
    }

    public Drawable getTsundereLittleBadgeDrawable(int sizeDp) {
        return new TsundereBadgeDrawable(R.drawable.tsundere_little_badge, sizeDp);
    }

    public Drawable getCustomBadgeDrawable(BadgeDTO badge, int sizeDp) {
        if (badge == null) {
            return getTsundereBadgeDrawable(sizeDp);
        }
        if (badge.hasImageUrl()) {
            Bitmap bmp = getOrDownloadBadgeBitmap(badge.getImageUrl());
            if (bmp != null) {
                return new TsundereBadgeDrawable(bmp, sizeDp);
            }
        }
        if (badge.isTsundereLittle()) {
            return getTsundereLittleBadgeDrawable(sizeDp);
        }
        if (badge.isTsundereCrown()) {
            return new TsundereBadgeDrawable(R.drawable.filled_rating_crown, sizeDp, 0xFFFFD700);
        }
        if (badge.isTsundereStar()) {
            return new TsundereBadgeDrawable(R.drawable.msg_premium_liststar, sizeDp, 0xFFFFB300);
        }
        if (badge.isTsundereHeart()) {
            return new TsundereBadgeDrawable(R.drawable.heart_angle_solar, sizeDp, 0xFFFF4081);
        }
        if (badge.isTsundereShield()) {
            return new TsundereBadgeDrawable(R.drawable.outline_shield_check, sizeDp, 0xFF4CAF50);
        }
        if (badge.isTsundereExtera()) {
            return new TsundereBadgeDrawable(R.drawable.extera_outline, sizeDp, 0xFF3390EC);
        }
        if (badge.getDocumentId() > 0) {
            try {
                AnimatedEmojiDrawable d = AnimatedEmojiDrawable.make(UserConfig.selectedAccount, AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS, badge.getDocumentId());
                if (d != null) {
                    int s = AndroidUtilities.dp(sizeDp);
                    d.setBounds(0, 0, s, s);
                    return d;
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        return getTsundereBadgeDrawable(sizeDp);
    }

    public Drawable getCustomBadgeDrawable(long id, int sizeDp) {
        BadgeDTO badge = getBadgeFor(id);
        if (badge != null) {
            return getCustomBadgeDrawable(badge, sizeDp);
        }
        if (id == LITTLE_TSUNDERE_ID) {
            return getTsundereLittleBadgeDrawable(sizeDp);
        }
        return getTsundereBadgeDrawable(sizeDp);
    }

    private BadgesController() {
        loadCachedBadges();
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, 0);
    }

    private static long selfId() {
        TLRPC.User self = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
        return self == null ? 0 : self.id;
    }

    public BadgeDTO getBadge() {
        return getBadgeFor(selfId());
    }

    public BadgeDTO getBadge(TLObject object) {
        if (object instanceof TLRPC.User) {
            return getBadgeFor(((TLRPC.User) object).id);
        }
        if (object instanceof TLRPC.Chat) {
            return getBadgeFor(((TLRPC.Chat) object).id);
        }
        return null;
    }

    public ApiBadgeSource getApiBadgeSource() {
        return apiBadgeSource;
    }

    public BadgeDTO getBadgeFor(long id) {
        if (id == 0) {
            return null;
        }
        String userHash = TsundereCrypto.hashUserId(id);
        BadgeDTO remote = remoteBadgesByHash.get(userHash);
        if (remote == null) {
            remote = remoteBadges.get(id);
        }
        if (remote != null) {
            return remote;
        }
        if (isTsundereDev(id)) {
            return createTsundereDevBadge();
        }
        if (isTsundereLittle(id)) {
            return createTsundereLittleBadge();
        }
        BadgeDTO fromSource = apiBadgeSource.getBadge(id, id > 0);
        if (fromSource != null) {
            return fromSource;
        }
        if (apiBadgeSource.isSupporter(id) || apiBadgeSource.isDeveloper(id)) {
            return new BadgeDTO(BadgeDTO.TSUNDERE_EXTERA_DOC_ID, "exteraGram Supporter");
        }
        SharedPreferences preferences = prefs();
        long documentId = preferences.getLong(KEY_DOCUMENT + id, 0);
        if (documentId == 0) {
            return null;
        }
        return new BadgeDTO(documentId, preferences.getString(KEY_TEXT + id, null));
    }

    public static long docIdForType(String type, long defaultDocId) {
        if (type == null) return BadgeDTO.TSUNDERE_DEV_DOC_ID;
        String t = type.toLowerCase().trim();
        if ("dev".equals(t)) return BadgeDTO.TSUNDERE_DEV_DOC_ID;
        if ("little".equals(t)) return BadgeDTO.TSUNDERE_LITTLE_DOC_ID;
        if ("crown".equals(t)) return BadgeDTO.TSUNDERE_CROWN_DOC_ID;
        if ("star".equals(t) || "vip".equals(t)) return BadgeDTO.TSUNDERE_STAR_DOC_ID;
        if ("heart".equals(t) || "love".equals(t)) return BadgeDTO.TSUNDERE_HEART_DOC_ID;
        if ("shield".equals(t) || "admin".equals(t)) return BadgeDTO.TSUNDERE_SHIELD_DOC_ID;
        if ("extera".equals(t) || "supporter".equals(t)) return BadgeDTO.TSUNDERE_EXTERA_DOC_ID;
        if (t.startsWith("emoji:")) {
            try {
                return Long.parseLong(t.substring(6).trim());
            } catch (Exception ignored) {}
        }
        return defaultDocId != 0 ? defaultDocId : BadgeDTO.TSUNDERE_DEV_DOC_ID;
    }

    public static String typeForDocId(long docId) {
        if (docId == BadgeDTO.TSUNDERE_DEV_DOC_ID) return "dev";
        if (docId == BadgeDTO.TSUNDERE_LITTLE_DOC_ID) return "little";
        if (docId == BadgeDTO.TSUNDERE_CROWN_DOC_ID) return "crown";
        if (docId == BadgeDTO.TSUNDERE_STAR_DOC_ID) return "star";
        if (docId == BadgeDTO.TSUNDERE_HEART_DOC_ID) return "heart";
        if (docId == BadgeDTO.TSUNDERE_SHIELD_DOC_ID) return "shield";
        if (docId == BadgeDTO.TSUNDERE_EXTERA_DOC_ID) return "extera";
        if (docId > 0) return "emoji:" + docId;
        return "dev";
    }

    private void loadCachedBadges() {
        try {
            String jsonStr = prefs().getString(KEY_REMOTE_JSON, null);
            if (!TextUtils.isEmpty(jsonStr)) {
                parseAndApplyBadgesJson(jsonStr);
            }
            String exteraJson = prefs().getString("extera_profiles_compact_json", null);
            if (!TextUtils.isEmpty(exteraJson)) {
                parseExteraProfilesJson(exteraJson);
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public void fetchExteraProfiles() {
        try {
            URL url = new URL("https://api.exteragram.app/api/v1/profiles/compact");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.connect();
            if (conn.getResponseCode() == 200) {
                InputStream is = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();
                String json = sb.toString();
                prefs().edit().putString("extera_profiles_compact_json", json).apply();
                parseExteraProfilesJson(json);
                AndroidUtilities.runOnUIThread(() -> {
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                        NotificationCenter.getInstance(a).postNotificationName(
                                NotificationCenter.updateInterfaces,
                                MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_STATUS | MessagesController.UPDATE_MASK_AVATAR
                        );
                    }
                });
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void parseExteraProfilesJson(String jsonStr) {
        if (TextUtils.isEmpty(jsonStr)) return;
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONArray supporters = root.optJSONArray("supporters");
            if (supporters != null) {
                for (int i = 0; i < supporters.length(); i++) {
                    long id = supporters.optLong(i);
                    if (id != 0) {
                        apiBadgeSource.put(id, new com.exteragram.messenger.badges.source.BadgeInfo(null, com.exteragram.messenger.api.model.ProfileStatus.SUPPORTER, false));
                    }
                }
            }
            JSONArray developers = root.optJSONArray("developers");
            if (developers != null) {
                for (int i = 0; i < developers.length(); i++) {
                    long id = developers.optLong(i);
                    if (id != 0) {
                        apiBadgeSource.put(id, new com.exteragram.messenger.badges.source.BadgeInfo(null, com.exteragram.messenger.api.model.ProfileStatus.DEVELOPER, true));
                    }
                }
            }
            JSONArray customBadges = root.optJSONArray("customBadges");
            if (customBadges != null) {
                for (int i = 0; i < customBadges.length(); i++) {
                    JSONObject item = customBadges.optJSONObject(i);
                    if (item != null) {
                        long id = item.optLong("id");
                        JSONObject badgeObj = item.optJSONObject("badge");
                        if (id != 0 && badgeObj != null) {
                            long docId = badgeObj.optLong("documentId");
                            String text = badgeObj.optString("text", null);
                            apiBadgeSource.put(id, new com.exteragram.messenger.badges.source.BadgeInfo(new BadgeDTO(docId, text), com.exteragram.messenger.api.model.ProfileStatus.SUPPORTER, false));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void parseAndApplyBadgesJson(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            if (root.has("admins")) {
                adminHashes.clear();
                JSONArray adminsArr = root.optJSONArray("admins");
                if (adminsArr != null) {
                    for (int i = 0; i < adminsArr.length(); i++) {
                        String a = adminsArr.optString(i, null);
                        if (!TextUtils.isEmpty(a)) {
                            adminHashes.add(a);
                        }
                    }
                }
            }
            if (root.has("frames")) {
                try {
                    JSONObject framesObj = root.optJSONObject("frames");
                    if (framesObj != null) {
                        app.exteraless.frames.ProfileFrameController.getInstance().applyRemoteFrames(framesObj);
                    }
                } catch (Exception ignored) {}
            }
            if (root.has("banners")) {
                try {
                    JSONObject bannersObj = root.optJSONObject("banners");
                    if (bannersObj != null) {
                        app.exteraless.banners.ProfileBannerController.getInstance().applyRemoteBanners(bannersObj);
                    }
                } catch (Exception ignored) {}
            }
            if (root.has("profile_colors")) {
                try {
                    JSONObject colorsObj = root.optJSONObject("profile_colors");
                    if (colorsObj != null) {
                        app.exteraless.profilecolors.ProfileColorController.getInstance().applyRemoteColors(colorsObj);
                    }
                } catch (Exception ignored) {}
            }
            if (root.has("clients")) {
                try {
                    JSONArray arr = root.optJSONArray("clients");
                    if (arr != null) {
                        clientUsers.clear();
                        for (int i = 0; i < arr.length(); i++) {
                            String c = arr.optString(i, null);
                            if (!TextUtils.isEmpty(c)) {
                                clientUsers.add(c);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (root.has("nickname_gradients")) {
                try {
                    JSONObject gObj = root.optJSONObject("nickname_gradients");
                    if (gObj != null) {
                        app.exteraless.appearance.TsundereChatCustomizer.getInstance().applyRemoteGradients(gObj);
                    }
                } catch (Exception ignored) {}
            }
            Iterator<String> keys = root.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if ("banners".equals(key) || "frames".equals(key) || "admins".equals(key) || "profile_colors".equals(key) || "clients".equals(key) || "nickname_gradients".equals(key)) {
                    continue;
                }
                try {
                    JSONObject item = root.getJSONObject(key);
                    String url = item.optString("url", null);
                    String type = item.optString("type", "dev");
                    String text = item.optString("text", "");
                    BadgeDTO badge;
                    if (!TextUtils.isEmpty(url)) {
                        badge = new BadgeDTO(BadgeDTO.TSUNDERE_CUSTOM_DOC_ID, text, url);
                        fetchAndCacheBadgeImage(url);
                    } else {
                        long docId = docIdForType(type, item.optLong("docId", 0));
                        badge = new BadgeDTO(docId, text);
                    }
                    remoteBadgesByHash.put(key, badge);
                    try {
                        long uId = Long.parseLong(key);
                        remoteBadges.put(uId, badge);
                    } catch (Exception ignored) {}
                } catch (Exception ignored) {}
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    public boolean hasBadge(long userId) {
        String hash = TsundereCrypto.hashUserId(userId);
        return remoteBadgesByHash.containsKey(hash) || remoteBadges.containsKey(userId) || isTsundereDev(userId) || isTsundereLittle(userId) || apiBadgeSource.isSupporter(userId);
    }

    public void checkRefreshRemoteBadges() {
        checkRefreshRemoteBadges(false);
    }

    public void checkRefreshRemoteBadges(boolean force) {
        if (force || System.currentTimeMillis() - lastLoadTime > 2 * 60 * 1000) {
            loadRemoteBadges();
        }
    }

    public void loadRemoteBadges() {
        if (isLoading) {
            return;
        }
        isLoading = true;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String botToken = TsundereCrypto.getBotToken();
                String urlStr = "https://api.telegram.org/bot" + botToken + "/getChat?chat_id=" + CHANNEL_USERNAME;
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.connect();

                int code = conn.getResponseCode();
                if (code == 200) {
                    InputStream is = conn.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();

                    JSONObject resp = new JSONObject(sb.toString());
                    if (resp.optBoolean("ok")) {
                        JSONObject result = resp.optJSONObject("result");
                        JSONObject pinned = result != null ? result.optJSONObject("pinned_message") : null;
                        String text = app.exteraless.security.CloudConfigStore.readText(pinned);
                        if (text.contains(CONFIG_HEADER)) {
                            String payload = text.substring(text.indexOf(CONFIG_HEADER) + CONFIG_HEADER.length()).trim();
                            String decryptedJson = TsundereCrypto.decrypt(payload);
                            int idx = decryptedJson.indexOf('{');
                            int lastIdx = decryptedJson.lastIndexOf('}');
                            if (idx >= 0 && lastIdx > idx) {
                                String jsonContent = decryptedJson.substring(idx, lastIdx + 1);
                                parseAndApplyBadgesJson(jsonContent);
                                prefs().edit().putString(KEY_REMOTE_JSON, jsonContent).apply();
                                lastLoadTime = System.currentTimeMillis();

                                AndroidUtilities.runOnUIThread(() -> {
                                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                                        NotificationCenter.getInstance(a).postNotificationName(
                                                NotificationCenter.updateInterfaces,
                                                MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_STATUS | MessagesController.UPDATE_MASK_AVATAR
                                        );
                                    }
                                });
                                long self = selfId();
                                if (self != 0 && app.exteraless.appearance.TsundereChatCustomizer.getInstance().isNicknameGradientEnabled()) {
                                    int c1 = app.exteraless.appearance.TsundereChatCustomizer.getInstance().getNicknameColor1();
                                    int c2 = app.exteraless.appearance.TsundereChatCustomizer.getInstance().getNicknameColor2();
                                    setRemoteGradient(self, c1, c2);
                                }
                            }
                        }
                    }
                }
                fetchExteraProfiles();
            } catch (Throwable t) {
                FileLog.e(t);
            } finally {
                isLoading = false;
            }
        });
    }

    public void setRemoteBadge(long userId, String type, String text, Runnable onSuccess, Consumer<String> onError) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String botToken = TsundereCrypto.getBotToken();
                // 1. Fetch current pinned message
                String getUrlStr = "https://api.telegram.org/bot" + botToken + "/getChat?chat_id=" + CHANNEL_USERNAME;
                URL getUrl = new URL(getUrlStr);
                HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
                getConn.setRequestMethod("GET");
                getConn.setConnectTimeout(8000);
                getConn.setReadTimeout(8000);

                int getCode = getConn.getResponseCode();
                if (getCode != 200) {
                    if (onError != null) {
                        AndroidUtilities.runOnUIThread(() -> onError.accept("HTTP " + getCode));
                    }
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(getConn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                reader.close();

                JSONObject resp = new JSONObject(sb.toString());
                if (!resp.optBoolean("ok")) {
                    if (onError != null) {
                        AndroidUtilities.runOnUIThread(() -> onError.accept("Bad response: " + resp));
                    }
                    return;
                }

                JSONObject result = resp.getJSONObject("result");
                JSONObject pinned = result.optJSONObject("pinned_message");
                int messageId = pinned != null ? pinned.optInt("message_id", 26) : 26;
                String currentText = app.exteraless.security.CloudConfigStore.readText(pinned);

                String jsonContent = "";
                int nlIdx = currentText.indexOf('\n');
                if (nlIdx >= 0) {
                    jsonContent = TsundereCrypto.decrypt(currentText.substring(nlIdx + 1).trim());
                } else {
                    jsonContent = TsundereCrypto.decrypt(currentText);
                }

                JSONObject configJson;
                int startBrace = jsonContent.indexOf('{');
                int endBrace = jsonContent.lastIndexOf('}');
                if (startBrace >= 0 && endBrace > startBrace) {
                    configJson = new JSONObject(jsonContent.substring(startBrace, endBrace + 1));
                } else {
                    configJson = new JSONObject();
                }

                String userKey = TsundereCrypto.hashUserId(userId);
                String rawKey = String.valueOf(userId);

                if (type == null) {
                    // Удаление значка
                    configJson.remove(userKey);
                    configJson.remove(rawKey);
                    remoteBadgesByHash.remove(userKey);
                    remoteBadges.remove(userId);
                    prefs().edit().remove(KEY_DOCUMENT + userId).remove(KEY_TEXT + userId).apply();
                } else if (type.startsWith("http")) {
                    JSONObject item = new JSONObject();
                    item.put("url", type);
                    item.put("text", text != null ? text : "");
                    configJson.put(userKey, item);
                    configJson.remove(rawKey);
                    BadgeDTO badge = new BadgeDTO(BadgeDTO.TSUNDERE_CUSTOM_DOC_ID, text, type);
                    remoteBadgesByHash.put(userKey, badge);
                    remoteBadges.put(userId, badge);
                    fetchAndCacheBadgeImage(type);
                } else {
                    // Добавление / обновление значка
                    JSONObject item = new JSONObject();
                    item.put("type", type);
                    item.put("text", text != null ? text : "");
                    configJson.put(userKey, item);
                    configJson.remove(rawKey);

                    long docId = docIdForType(type, 0);
                    BadgeDTO badge = new BadgeDTO(docId, text);
                    remoteBadgesByHash.put(userKey, badge);
                    remoteBadges.put(userId, badge);
                    prefs().edit().putLong(KEY_DOCUMENT + userId, docId).putString(KEY_TEXT + userId, text).apply();
                }

                String encPayload = TsundereCrypto.encrypt(configJson.toString());
                String updatedConfigStr = CONFIG_HEADER + "\nENC:" + encPayload;
                prefs().edit().putString(KEY_REMOTE_JSON, configJson.toString()).apply();

                // 2. Edit pinned message in channel
                // Текст до 4000 символов — editMessageText, больше — документ в закрепе (см. CloudConfigStore)
                HttpURLConnection editConn = app.exteraless.security.CloudConfigStore.sendConfig(CHANNEL_USERNAME, messageId, pinned, updatedConfigStr);

                int editCode = editConn.getResponseCode();
                if (editCode == 200) {
                    AndroidUtilities.runOnUIThread(() -> {
                        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                            NotificationCenter.getInstance(a).postNotificationName(
                                    NotificationCenter.updateInterfaces,
                                    MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_STATUS
                            );
                        }
                        if (onSuccess != null) {
                            onSuccess.run();
                        }
                    });
                } else {
                    if (onError != null) {
                        AndroidUtilities.runOnUIThread(() -> onError.accept("Failed editMessageText: HTTP " + editCode));
                    }
                }
            } catch (Throwable t) {
                FileLog.e(t);
                if (onError != null) {
                    AndroidUtilities.runOnUIThread(() -> onError.accept(t.getMessage()));
                }
            }
        });
    }

    public void setBadgeAdmin(long targetUserId, boolean makeAdmin, Runnable onSuccess, Consumer<String> onError) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String botToken = TsundereCrypto.getBotToken();
                String getUrlStr = "https://api.telegram.org/bot" + botToken + "/getChat?chat_id=" + CHANNEL_USERNAME;
                URL getUrl = new URL(getUrlStr);
                HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
                getConn.setRequestMethod("GET");
                getConn.setConnectTimeout(8000);
                getConn.setReadTimeout(8000);

                int getRespCode = getConn.getResponseCode();
                if (getRespCode != 200) {
                    if (onError != null) AndroidUtilities.runOnUIThread(() -> onError.accept("HTTP " + getRespCode));
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(getConn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject resp = new JSONObject(sb.toString());
                if (!resp.optBoolean("ok")) {
                    if (onError != null) AndroidUtilities.runOnUIThread(() -> onError.accept("Bad response: " + resp));
                    return;
                }

                JSONObject result = resp.getJSONObject("result");
                JSONObject pinned = result.optJSONObject("pinned_message");
                int messageId = pinned != null ? pinned.optInt("message_id", 26) : 26;
                String currentText = app.exteraless.security.CloudConfigStore.readText(pinned);

                String jsonContent = "";
                int nlIdx = currentText.indexOf('\n');
                if (nlIdx >= 0) {
                    jsonContent = TsundereCrypto.decrypt(currentText.substring(nlIdx + 1).trim());
                } else {
                    jsonContent = TsundereCrypto.decrypt(currentText);
                }

                JSONObject configJson;
                int startBrace = jsonContent.indexOf('{');
                int endBrace = jsonContent.lastIndexOf('}');
                if (startBrace >= 0 && endBrace > startBrace) {
                    configJson = new JSONObject(jsonContent.substring(startBrace, endBrace + 1));
                } else {
                    configJson = new JSONObject();
                }

                JSONArray adminsArr = configJson.optJSONArray("admins");
                if (adminsArr == null) {
                    adminsArr = new JSONArray();
                    configJson.put("admins", adminsArr);
                }

                String targetHash = TsundereCrypto.hashUserId(targetUserId);
                String rawId = String.valueOf(targetUserId);

                JSONArray newAdmins = new JSONArray();
                for (int i = 0; i < adminsArr.length(); i++) {
                    String h = adminsArr.optString(i);
                    if (!h.equals(targetHash) && !h.equals(rawId)) {
                        newAdmins.put(h);
                    }
                }
                if (makeAdmin) {
                    newAdmins.put(targetHash);
                    adminHashes.add(targetHash);
                } else {
                    adminHashes.remove(targetHash);
                    adminHashes.remove(rawId);
                }
                configJson.put("admins", newAdmins);

                String encPayload = TsundereCrypto.encrypt(configJson.toString());
                String updatedConfigStr = CONFIG_HEADER + "\nENC:" + encPayload;

                // Текст до 4000 символов — editMessageText, больше — документ в закрепе (см. CloudConfigStore)
                HttpURLConnection editConn = app.exteraless.security.CloudConfigStore.sendConfig(CHANNEL_USERNAME, messageId, pinned, updatedConfigStr);

                int editCode = editConn.getResponseCode();
                if (editCode == 200) {
                    prefs().edit().putString(KEY_REMOTE_JSON, configJson.toString()).apply();
                    AndroidUtilities.runOnUIThread(() -> {
                        if (onSuccess != null) onSuccess.run();
                    });
                } else {
                    if (onError != null) AndroidUtilities.runOnUIThread(() -> onError.accept("HTTP " + editCode));
                }
            } catch (Throwable t) {
                FileLog.e(t);
                if (onError != null) AndroidUtilities.runOnUIThread(() -> onError.accept(t.getMessage()));
            }
        });
    }

    public void registerSelfAsClient(long userId) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String botToken = TsundereCrypto.getBotToken();
                String getUrlStr = "https://api.telegram.org/bot" + botToken + "/getChat?chat_id=" + CHANNEL_USERNAME;
                URL getUrl = new URL(getUrlStr);
                HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
                getConn.setRequestMethod("GET");
                getConn.setConnectTimeout(8000);
                getConn.setReadTimeout(8000);

                if (getConn.getResponseCode() != 200) return;

                BufferedReader reader = new BufferedReader(new InputStreamReader(getConn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject resp = new JSONObject(sb.toString());
                if (!resp.optBoolean("ok")) return;

                JSONObject result = resp.getJSONObject("result");
                JSONObject pinned = result.optJSONObject("pinned_message");
                int messageId = pinned != null ? pinned.optInt("message_id", 26) : 26;
                String currentText = app.exteraless.security.CloudConfigStore.readText(pinned);

                String jsonContent = "";
                int nlIdx = currentText.indexOf('\n');
                if (nlIdx >= 0) {
                    jsonContent = TsundereCrypto.decrypt(currentText.substring(nlIdx + 1).trim());
                } else {
                    jsonContent = TsundereCrypto.decrypt(currentText);
                }

                JSONObject configJson;
                int startBrace = jsonContent.indexOf('{');
                int endBrace = jsonContent.lastIndexOf('}');
                if (startBrace >= 0 && endBrace > startBrace) {
                    configJson = new JSONObject(jsonContent.substring(startBrace, endBrace + 1));
                } else {
                    configJson = new JSONObject();
                }

                JSONArray clients = configJson.optJSONArray("clients");
                if (clients == null) {
                    clients = new JSONArray();
                    configJson.put("clients", clients);
                }
                String userKey = TsundereCrypto.hashUserId(userId);
                boolean exists = false;
                for (int i = 0; i < clients.length(); i++) {
                    if (userKey.equals(clients.optString(i))) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    clients.put(userKey);
                    String encPayload = TsundereCrypto.encrypt(configJson.toString());
                    String updatedConfigStr = CONFIG_HEADER + "\nENC:" + encPayload;

                    // Текст до 4000 символов — editMessageText, больше — документ в закрепе (см. CloudConfigStore)
                    HttpURLConnection editConn = app.exteraless.security.CloudConfigStore.sendConfig(CHANNEL_USERNAME, messageId, pinned, updatedConfigStr);
                    editConn.getResponseCode();
                }
            } catch (Throwable ignored) {}
        });
    }

    public void setRemoteGradient(long userId, int c1, int c2) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String botToken = TsundereCrypto.getBotToken();
                String getUrlStr = "https://api.telegram.org/bot" + botToken + "/getChat?chat_id=" + CHANNEL_USERNAME;
                URL getUrl = new URL(getUrlStr);
                HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
                getConn.setRequestMethod("GET");
                getConn.setConnectTimeout(8000);
                getConn.setReadTimeout(8000);

                if (getConn.getResponseCode() != 200) return;

                BufferedReader reader = new BufferedReader(new InputStreamReader(getConn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject resp = new JSONObject(sb.toString());
                if (!resp.optBoolean("ok")) return;

                JSONObject result = resp.getJSONObject("result");
                JSONObject pinned = result.optJSONObject("pinned_message");
                int messageId = pinned != null ? pinned.optInt("message_id", 26) : 26;
                String currentText = app.exteraless.security.CloudConfigStore.readText(pinned);

                String jsonContent = "";
                int nlIdx = currentText.indexOf('\n');
                if (nlIdx >= 0) {
                    jsonContent = TsundereCrypto.decrypt(currentText.substring(nlIdx + 1).trim());
                } else {
                    jsonContent = TsundereCrypto.decrypt(currentText);
                }

                JSONObject configJson;
                int startBrace = jsonContent.indexOf('{');
                int endBrace = jsonContent.lastIndexOf('}');
                if (startBrace >= 0 && endBrace > startBrace) {
                    configJson = new JSONObject(jsonContent.substring(startBrace, endBrace + 1));
                } else {
                    configJson = new JSONObject();
                }

                JSONObject gradients = configJson.optJSONObject("nickname_gradients");
                if (gradients == null) {
                    gradients = new JSONObject();
                    configJson.put("nickname_gradients", gradients);
                }
                String userKey = TsundereCrypto.hashUserId(userId);
                JSONObject gradItem = new JSONObject();
                gradItem.put("color1", c1);
                gradItem.put("color2", c2);
                gradients.put(userKey, gradItem);

                JSONArray clients = configJson.optJSONArray("clients");
                if (clients == null) {
                    clients = new JSONArray();
                    configJson.put("clients", clients);
                }
                boolean exists = false;
                for (int i = 0; i < clients.length(); i++) {
                    if (userKey.equals(clients.optString(i))) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    clients.put(userKey);
                }

                String encPayload = TsundereCrypto.encrypt(configJson.toString());
                String updatedConfigStr = CONFIG_HEADER + "\nENC:" + encPayload;

                // Текст до 4000 символов — editMessageText, больше — документ в закрепе (см. CloudConfigStore)
                HttpURLConnection editConn = app.exteraless.security.CloudConfigStore.sendConfig(CHANNEL_USERNAME, messageId, pinned, updatedConfigStr);
                editConn.getResponseCode();
            } catch (Throwable ignored) {}
        });
    }


    public void showAdminBadgeDialog(BaseFragment fragment, TLRPC.User targetUser, int currentAccount) {
        if (fragment == null) {
            return;
        }
        fragment.presentFragment(new BadgeManageActivity(targetUser != null ? targetUser.id : 0, targetUser));
    }

    public BadgeDTO getDefaultBadge() {
        return null;
    }

    public BadgeDTO getDefaultBadge(TLRPC.User user) {
        return null;
    }

    public BadgeDTO getSecondaryBadge(TLRPC.User user) {
        return null;
    }

    public boolean hasBadge() {
        return getBadge() != null;
    }

    public boolean hasBadge(TLObject object) {
        return getBadge(object) != null;
    }

    public boolean canChangeBadge() {
        return canChangeBadge(UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser());
    }

    public boolean canChangeBadge(TLRPC.User user) {
        return user != null && (user.id == selfId() || apiBadgeSource.canChangeBadge(user.id));
    }

    public boolean isDeveloper() {
        return isTsundereDev(selfId()) || apiBadgeSource.isDeveloper(selfId());
    }

    public boolean isDeveloper(TLRPC.User user) {
        return user != null && (isTsundereDev(user.id) || apiBadgeSource.isDeveloper(user.id));
    }

    public boolean isExtera(long id) {
        return false;
    }

    public boolean isExtera(TLRPC.Chat chat) {
        return false;
    }

    public boolean isTrusted(long id) {
        return false;
    }

    public boolean shouldUseSecondaryBadgeSlot(TLRPC.User user, BadgeDTO badge) {
        return false;
    }

    public void updateBadge(BadgeDTO badge, Consumer<String> callback) {
        long id = selfId();
        if (id != 0) {
            SharedPreferences.Editor editor = prefs().edit();
            if (badge == null) {
                editor.remove(KEY_DOCUMENT + id).remove(KEY_TEXT + id);
            } else {
                editor.putLong(KEY_DOCUMENT + id, badge.getDocumentId())
                        .putString(KEY_TEXT + id, badge.getText());
            }
            editor.apply();
        }
        if (callback != null) {
            callback.accept("ok");
        }
    }

    public void showBadgeBulletin(BaseFragment fragment, BadgeDTO badge, TLRPC.Chat chat,
                                  Theme.ResourcesProvider resourcesProvider, int account) {
        showBadgeBulletin(fragment, badge, chat, resourcesProvider, account, null, Boolean.FALSE);
    }

    public void showBadgeBulletin(BaseFragment fragment, BadgeDTO badge, TLRPC.Chat chat,
                                  Theme.ResourcesProvider resourcesProvider, int account,
                                  FrameLayout containerLayout, Boolean showButton) {
        if (chat == null || badge == null) {
            return;
        }
        showBadgeBulletin(fragment, badge, formatBadgeText(badge, chat.title), resourcesProvider, account, containerLayout);
    }

    public void showBadgeBulletin(BaseFragment fragment, BadgeDTO badge, TLRPC.User user,
                                  Theme.ResourcesProvider resourcesProvider, int account) {
        showBadgeBulletin(fragment, badge, user, resourcesProvider, account, null, Boolean.FALSE);
    }

    public void showBadgeBulletin(BaseFragment fragment, BadgeDTO badge, TLRPC.User user,
                                  Theme.ResourcesProvider resourcesProvider, int account,
                                  FrameLayout containerLayout, Boolean showButton) {
        if (user == null || badge == null) {
            return;
        }
        if (badge.isCustomBadge()) {
            BaseFragment targetFragment = fragment != null ? fragment : LaunchActivity.getLastFragment();
            BulletinFactory factory = containerLayout != null
                    ? BulletinFactory.of(containerLayout, resourcesProvider)
                    : (targetFragment != null ? BulletinFactory.of(targetFragment) : null);
            if (factory == null) {
                return;
            }
            Drawable icon = getCustomBadgeDrawable(badge, 24);
            String name = UserObject.getUserName(user);
            String text = !TextUtils.isEmpty(badge.getText()) ? badge.getText() : "TsundereGram";
            factory.createSimpleBulletin(icon, name, text).show();
            return;
        }
        showBadgeBulletin(fragment, badge, formatBadgeText(badge, UserObject.getUserName(user)), resourcesProvider, account, containerLayout);
    }

    public void showBadgeBulletin(BaseFragment fragment, TLRPC.User user,
                                  Theme.ResourcesProvider resourcesProvider, int account,
                                  FrameLayout containerLayout, Boolean showButton) {
        showBadgeBulletin(fragment, getBadge(user), user, resourcesProvider, account, containerLayout, showButton);
    }

    public void showTsundereDevBulletin(BaseFragment fragment, TLRPC.User user,
                                        Theme.ResourcesProvider resourcesProvider,
                                        FrameLayout containerLayout) {
        BaseFragment targetFragment = fragment != null ? fragment : LaunchActivity.getLastFragment();
        BulletinFactory factory = containerLayout != null
                ? BulletinFactory.of(containerLayout, resourcesProvider)
                : (targetFragment != null ? BulletinFactory.of(targetFragment) : null);
        if (factory == null) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        Drawable icon = ContextCompat.getDrawable(context, R.drawable.tsundere_dev_badge);
        String name = user != null ? UserObject.getUserName(user) : "TsundereGram";
        factory.createSimpleBulletin(icon, name, TSUNDERE_DEV_TEXT).show();
    }

    public void showTsundereLittleBulletin(BaseFragment fragment, TLRPC.User user,
                                           Theme.ResourcesProvider resourcesProvider,
                                           FrameLayout containerLayout) {
        BaseFragment targetFragment = fragment != null ? fragment : LaunchActivity.getLastFragment();
        BulletinFactory factory = containerLayout != null
                ? BulletinFactory.of(containerLayout, resourcesProvider)
                : (targetFragment != null ? BulletinFactory.of(targetFragment) : null);
        if (factory == null) {
            return;
        }
        Context context = ApplicationLoader.applicationContext;
        Drawable icon = ContextCompat.getDrawable(context, R.drawable.tsundere_little_badge);
        String name = user != null ? UserObject.getUserName(user) : "TsundereGram";
        factory.createSimpleBulletin(icon, name, LITTLE_TSUNDERE_TEXT).show();
    }

    private void showBadgeBulletin(BaseFragment fragment, BadgeDTO badge, CharSequence text,
                                   Theme.ResourcesProvider resourcesProvider, int account,
                                   FrameLayout containerLayout) {
        BulletinFactory factory = containerLayout != null
                ? BulletinFactory.of(containerLayout, resourcesProvider)
                : BulletinFactory.of(fragment);
        if (factory == null) {
            return;
        }
        factory.createEmojiBulletin(AnimatedEmojiDrawable.findDocument(account, badge.getDocumentId()), text)
                .wrapContent()
                .show();
    }

    private CharSequence formatBadgeText(BadgeDTO badge, CharSequence fallbackText) {
        String text = badge.getText();
        if (TextUtils.isEmpty(text)) {
            return fallbackText;
        }
        CharSequence formatted = LocaleUtils.formatWithUsernames(text);
        return formatted != null ? formatted : fallbackText;
    }
}
