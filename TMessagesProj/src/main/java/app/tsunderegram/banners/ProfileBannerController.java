package app.tsunderegram.banners;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.text.TextUtils;
import android.util.LruCache;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import app.tsunderegram.security.TsundereCrypto;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.VideoPlayer;
import org.telegram.ui.ProfileActivity;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ProfileBannerController {

    private static volatile ProfileBannerController INSTANCE;

    public static final String API_BASE = "https://banners.ettacent.dev/api/v1/banners";
    private static final String PREFS_NAME = "tsunderegram_banners";
    private static final String KEY_LOCAL_BANNER = "local_banner_path";
    private static final String KEY_USE_AVATAR = "use_avatar_as_banner";
    private static final String KEY_HIDE_AVATAR = "hide_avatar";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_SERVER_STATUS = "server_status";

    private final SharedPreferences prefs;
    private final File storageDir;
    private final File cacheDir;

    private final LruCache<String, Bitmap> bitmapCache = new LruCache<>(32);
    private final LruCache<String, Bitmap> blurCache = new LruCache<>(16);
    private final Map<Long, BannerMeta> bannerMetaMap = new ConcurrentHashMap<>();
    private final Map<String, BannerMeta> bannerMetaByHash = new ConcurrentHashMap<>();
    private final Map<Long, String> cachedBannerPaths = new ConcurrentHashMap<>();
    private final Set<Long> usersWithoutBanner = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Long> loadingUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<Long> refreshingUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<Long, Long> failTimes = new ConcurrentHashMap<>();

    private final Paint bannerPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint blurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint darkPaint = new Paint();
    private final Paint gradPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix drawMatrix = new Matrix();
    /**
     * TsundereGram: высота шапки профиля в обычном (не свёрнутом) состоянии. Масштаб баннера
     * считается от неё, поэтому при прокрутке картинка не сжимается и не растягивается,
     * а просто обрезается; растягивается только при оттягивании шапки вниз.
     */
    private int fullBannerHeight;

    public void setFullHeight(int height) {
        fullBannerHeight = height;
    }

    private int stableHeight(int height) {
        return Math.max(height, fullBannerHeight > 0 ? fullBannerHeight : height);
    }

    // Video playback for active profile
    private VideoPlayer activeVideoPlayer;
    private TextureView activeTextureView;
    private FrameLayout attachedTopView;
    private String activeVideoPath;
    private long activeVideoUserId;
    private int lastVideoWidth;
    private int lastVideoHeight;
    private int lastRotation;
    private volatile boolean isFirstFrameRendered = false;

    // Animated GIF playback
    private org.telegram.ui.Components.AnimatedFileDrawable activeGifDrawable;
    private String activeGifPath;

    public static class BannerMeta {
        public boolean hasBanner;
        public String url;
        public String type = "jpg";
        public String version = "";
        public boolean hideAvatar;
        public boolean hasSound;
        public String localPath;
    }

    public static ProfileBannerController getInstance() {
        if (INSTANCE == null) {
            synchronized (ProfileBannerController.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ProfileBannerController();
                }
            }
        }
        return INSTANCE;
    }

    private ProfileBannerController() {
        Context context = ApplicationLoader.applicationContext;
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        storageDir = new File(context.getFilesDir(), "banners");
        cacheDir = new File(storageDir, "cache");
        if (!storageDir.exists()) storageDir.mkdirs();
        if (!cacheDir.exists()) cacheDir.mkdirs();

        // Index cached files
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                int under = name.indexOf('_');
                if (under > 0) {
                    try {
                        long uid = Long.parseLong(name.substring(0, under));
                        cachedBannerPaths.put(uid, file.getAbsolutePath());
                    } catch (Exception ignored) {}
                }
            }
        }
    }

    public long getMyId() {
        return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
    }

    public String getLocalBannerPath() {
        String path = prefs.getString(app.tsunderegram.security.AccountScope.key(prefs, KEY_LOCAL_BANNER), null);
        if (path != null && new File(path).exists()) {
            return path;
        }
        return null;
    }

    public boolean hasLocalBanner() {
        return getLocalBannerPath() != null;
    }

    public void setLocalBanner(File source, String ext) {
        try {
            // Удаляем только свои старые баннеры: у каждого аккаунта на устройстве — свой файл
            String previous = getLocalBannerPath();
            if (previous != null) {
                new File(previous).delete();
            }
            String ownPrefix = "local_banner_" + getMyId();
            File[] old = storageDir.listFiles();
            if (old != null) {
                for (File f : old) {
                    if (f.getName().startsWith(ownPrefix)) {
                        f.delete();
                    }
                }
            }
            File dest = new File(storageDir, ownPrefix + ext);
            copyFile(source, dest);
            prefs.edit().putString(app.tsunderegram.security.AccountScope.key(prefs, KEY_LOCAL_BANNER), dest.getAbsolutePath()).apply();
            bitmapCache.evictAll();
            blurCache.evictAll();
            detachVideo();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void deleteLocalBanner() {
        String path = getLocalBannerPath();
        if (path != null) {
            new File(path).delete();
        }
        prefs.edit().remove(app.tsunderegram.security.AccountScope.key(prefs, KEY_LOCAL_BANNER)).apply();
        bitmapCache.evictAll();
        blurCache.evictAll();
        detachVideo();
    }

    public boolean isUseAvatarAsBanner() {
        return prefs.getBoolean(app.tsunderegram.security.AccountScope.key(prefs, KEY_USE_AVATAR), false);
    }

    public void setUseAvatarAsBanner(boolean use) {
        prefs.edit().putBoolean(app.tsunderegram.security.AccountScope.key(prefs, KEY_USE_AVATAR), use).apply();
        bitmapCache.evictAll();
        blurCache.evictAll();
    }

    public boolean isHideAvatarSelf() {
        return prefs.getBoolean(app.tsunderegram.security.AccountScope.key(prefs, KEY_HIDE_AVATAR), false);
    }

    public void setHideAvatarSelf(boolean hide) {
        prefs.edit().putBoolean(app.tsunderegram.security.AccountScope.key(prefs, KEY_HIDE_AVATAR), hide).apply();
    }

    public void updateHideAvatar(boolean hide, Runnable onSuccess, Consumer<String> onError) {
        setHideAvatarSelf(hide);
        Utilities.globalQueue.postRunnable(() -> {
            try {
                long myId = getMyId();
                if (myId == 0) {
                    if (onSuccess != null) AndroidUtilities.runOnUIThread(onSuccess);
                    return;
                }
                BannerMeta meta = bannerMetaMap.get(myId);
                if (meta != null) {
                    meta.hideAvatar = hide;
                }

                String getUrlStr = app.tsunderegram.security.TsundereApi.botBase() + "getChat?chat_id=" + com.tsunderegram.messenger.badges.BadgesController.CHANNEL_USERNAME;
                URL getUrl = new URL(getUrlStr);
                HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
                getConn.setRequestMethod("GET");
                getConn.setConnectTimeout(8000);
                getConn.setReadTimeout(8000);

                if (getConn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(getConn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject resp = new JSONObject(sb.toString());
                    JSONObject result = resp.getJSONObject("result");
                    JSONObject pinned = result.optJSONObject("pinned_message");
                    int messageId = pinned != null ? pinned.optInt("message_id", 26) : 26;
                    String currentText = pinned != null ? pinned.optString("text", "") : "";

                    String payload = currentText;
                    int headerIdx = currentText.indexOf(com.tsunderegram.messenger.badges.BadgesController.CONFIG_HEADER);
                    if (headerIdx >= 0) {
                        payload = currentText.substring(headerIdx + com.tsunderegram.messenger.badges.BadgesController.CONFIG_HEADER.length()).trim();
                    }
                    String decrypted = TsundereCrypto.decrypt(payload);

                    int startBrace = decrypted.indexOf('{');
                    int endBrace = decrypted.lastIndexOf('}');
                    if (startBrace >= 0 && endBrace > startBrace) {
                        JSONObject configJson = new JSONObject(decrypted.substring(startBrace, endBrace + 1));
                        JSONObject bannersJson = configJson.optJSONObject("banners");
                        if (bannersJson != null) {
                            String idStr = String.valueOf(myId);
                            String hashStr = TsundereCrypto.hashUserId(myId);
                            boolean updated = false;
                            if (bannersJson.has(idStr)) {
                                bannersJson.getJSONObject(idStr).put("hide_avatar", hide);
                                updated = true;
                            }
                            if (bannersJson.has(hashStr)) {
                                bannersJson.getJSONObject(hashStr).put("hide_avatar", hide);
                                updated = true;
                            }
                            if (updated) {
                                String encPayload = TsundereCrypto.encrypt(configJson.toString());
                                String updatedConfigStr = com.tsunderegram.messenger.badges.BadgesController.CONFIG_HEADER + "\nENC:" + encPayload;

                                String editUrlStr = app.tsunderegram.security.TsundereApi.botBase() + "editMessageText";
                                JSONObject editPayload = new JSONObject();
                                editPayload.put("chat_id", com.tsunderegram.messenger.badges.BadgesController.CHANNEL_USERNAME);
                                editPayload.put("message_id", messageId);
                                editPayload.put("text", updatedConfigStr);

                                URL editUrl = new URL(editUrlStr);
                                HttpURLConnection editConn = (HttpURLConnection) editUrl.openConnection();
                                editConn.setRequestMethod("POST");
                                app.tsunderegram.security.TsundereApi.authorize(editConn);
                                editConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                                editConn.setDoOutput(true);
                                editConn.setConnectTimeout(8000);
                                editConn.setReadTimeout(8000);

                                byte[] outBytes = editPayload.toString().getBytes(StandardCharsets.UTF_8);
                                try (java.io.OutputStream os = editConn.getOutputStream()) {
                                    os.write(outBytes);
                                    os.flush();
                                }
                                editConn.getResponseCode();
                            }
                        }
                    }
                }
                if (onSuccess != null) AndroidUtilities.runOnUIThread(onSuccess);
            } catch (Throwable t) {
                FileLog.e(t);
                if (onSuccess != null) AndroidUtilities.runOnUIThread(onSuccess);
            }
        });
    }

    public void updateHideAvatar(boolean hide, Runnable onDone) {
        updateHideAvatar(hide, onDone, null);
    }

    public void deleteGlobalBanner(Runnable onSuccess, Consumer<String> onError) {
        long myId = getMyId();
        setServerStatus("none");
        recentlySetBanners.remove(myId);
        forgetBanner(myId);
        bitmapCache.evictAll();
        blurCache.evictAll();
        detachVideo();

        Utilities.globalQueue.postRunnable(() -> {
            try {
                String getUrlStr = app.tsunderegram.security.TsundereApi.botBase() + "getChat?chat_id=" + com.tsunderegram.messenger.badges.BadgesController.CHANNEL_USERNAME;
                URL getUrl = new URL(getUrlStr);
                HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
                getConn.setRequestMethod("GET");
                getConn.setConnectTimeout(8000);
                getConn.setReadTimeout(8000);

                if (getConn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(getConn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject resp = new JSONObject(sb.toString());
                    JSONObject result = resp.getJSONObject("result");
                    JSONObject pinned = result.optJSONObject("pinned_message");
                    int messageId = pinned != null ? pinned.optInt("message_id", 26) : 26;
                    String currentText = pinned != null ? pinned.optString("text", "") : "";

                    int startBrace = currentText.indexOf('{');
                    int endBrace = currentText.lastIndexOf('}');
                    if (startBrace >= 0 && endBrace > startBrace) {
                        JSONObject configJson = new JSONObject(currentText.substring(startBrace, endBrace + 1));
                        JSONObject bannersJson = configJson.optJSONObject("banners");
                        if (bannersJson != null) {
                            bannersJson.remove(String.valueOf(myId));
                            String updatedConfigStr = com.tsunderegram.messenger.badges.BadgesController.CONFIG_HEADER + "\n" + configJson.toString(2);

                            String editUrlStr = app.tsunderegram.security.TsundereApi.botBase() + "editMessageText";
                            JSONObject editPayload = new JSONObject();
                            editPayload.put("chat_id", com.tsunderegram.messenger.badges.BadgesController.CHANNEL_USERNAME);
                            editPayload.put("message_id", messageId);
                            editPayload.put("text", updatedConfigStr);

                            URL editUrl = new URL(editUrlStr);
                            HttpURLConnection editConn = (HttpURLConnection) editUrl.openConnection();
                            editConn.setRequestMethod("POST");
                            app.tsunderegram.security.TsundereApi.authorize(editConn);
                            editConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                            editConn.setDoOutput(true);
                            editConn.setConnectTimeout(8000);
                            editConn.setReadTimeout(8000);

                            byte[] outBytes = editPayload.toString().getBytes(StandardCharsets.UTF_8);
                            try (java.io.OutputStream os = editConn.getOutputStream()) {
                                os.write(outBytes);
                                os.flush();
                            }
                            editConn.getResponseCode();
                        }
                    }
                }
                if (onSuccess != null) AndroidUtilities.runOnUIThread(onSuccess);
            } catch (Throwable t) {
                FileLog.e(t);
                if (onSuccess != null) AndroidUtilities.runOnUIThread(onSuccess);
            }
        });
    }

    public void applyRemoteBanners(JSONObject bannersJson) {
        if (bannersJson == null) return;
        final java.util.HashSet<String> remoteKeys = new java.util.HashSet<>();
        try {
            Iterator<String> keys = bannersJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                remoteKeys.add(key);
                try {
                    JSONObject item = bannersJson.getJSONObject(key);
                    BannerMeta meta = new BannerMeta();
                    meta.hasBanner = true;
                    meta.url = item.optString("url");
                    meta.type = item.optString("type", "jpg");
                    meta.hideAvatar = item.optBoolean("hide_avatar", false);
                    meta.hasSound = item.optBoolean("has_sound", false);
                    meta.version = "1";

                    bannerMetaByHash.put(key, meta);

                    try {
                        long uId = Long.parseLong(key);
                        bannerMetaMap.put(uId, meta);
                    } catch (Exception ignored) {}

                    if (TsundereCrypto.matchesUserId(key, getMyId())) {
                        bannerMetaMap.put(getMyId(), meta);
                        setServerStatus("approved");
                        setHideAvatarSelf(meta.hideAvatar);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Throwable t) {
            FileLog.e(t);
            return;
        }
        // Конфиг — единственный источник правды: всё, чего в нём больше нет, забываем.
        // Раньше записи только добавлялись, и удалённый баннер продолжал показываться
        // (из памяти по хэшу и из файла в кэше) до переустановки.
        try {
            boolean changed = false;
            for (String key : new java.util.ArrayList<>(bannerMetaByHash.keySet())) {
                if (!remoteKeys.contains(key)) {
                    bannerMetaByHash.remove(key);
                    changed = true;
                }
            }
            final java.util.HashSet<Long> ids = new java.util.HashSet<>(bannerMetaMap.keySet());
            ids.addAll(cachedBannerPaths.keySet());
            final long myId = getMyId();
            for (Long id : ids) {
                if (id == null || id == 0 || isRecentlySet(id)) continue;
                if (remoteKeys.contains(String.valueOf(id))) continue;
                String hash = TsundereCrypto.hashUserId(id);
                if (hash != null && remoteKeys.contains(hash)) continue;
                forgetBanner(id);
                changed = true;
                if (id == myId && "approved".equals(getServerStatus())) {
                    setServerStatus("none");
                }
            }
            if (myId != 0 && !isRecentlySet(myId) && "approved".equals(getServerStatus())) {
                String myHash = TsundereCrypto.hashUserId(myId);
                if (!remoteKeys.contains(String.valueOf(myId)) && (myHash == null || !remoteKeys.contains(myHash))) {
                    setServerStatus("none");
                    changed = true;
                }
            }
            if (changed) {
                bitmapCache.evictAll();
                blurCache.evictAll();
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL));
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    // Только что загруженный/поставленный баннер не трогаем: конфиг мог прийти из кэша до записи.
    private final Map<Long, Long> recentlySetBanners = new ConcurrentHashMap<>();

    private void markRecentlySet(long id) {
        recentlySetBanners.put(id, android.os.SystemClock.elapsedRealtime());
    }

    private boolean isRecentlySet(long id) {
        Long t = recentlySetBanners.get(id);
        if (t == null) return false;
        if (android.os.SystemClock.elapsedRealtime() - t > 2 * 60 * 1000L) {
            recentlySetBanners.remove(id);
            return false;
        }
        return true;
    }

    /** Полностью забывает баннер пользователя/канала: память, хэш-ключи и файлы в кэше. */
    private void forgetBanner(long id) {
        bannerMetaMap.remove(id);
        bannerMetaByHash.remove(String.valueOf(id));
        try {
            String hash = TsundereCrypto.hashUserId(id);
            if (hash != null) bannerMetaByHash.remove(hash);
        } catch (Throwable ignored) {}
        String cached = cachedBannerPaths.remove(id);
        if (cached != null) {
            try { new File(cached).delete(); } catch (Throwable ignored) {}
        }
        try {
            File[] files = cacheDir.listFiles();
            if (files != null) {
                String prefix = id + "_";
                for (File f : files) {
                    if (f.getName().startsWith(prefix)) f.delete();
                }
            }
        } catch (Throwable ignored) {}
    }

    public String getServerStatus() {
        return prefs.getString(KEY_SERVER_STATUS, "none");
    }

    public void setServerStatus(String status) {
        prefs.edit().putString(KEY_SERVER_STATUS, status).apply();
    }

    public String getBannerStatusDescription() {
        if (hasLocalBanner()) {
            return LocaleController.getString(R.string.ProfileBannerLocalSet);
        }
        String status = getServerStatus();
        switch (status) {
            case "pending":
                return LocaleController.getString(R.string.ProfileBannerStatusPending);
            case "approved":
                return LocaleController.getString(R.string.ProfileBannerStatusApproved);
            case "rejected":
                return LocaleController.getString(R.string.ProfileBannerStatusRejected);
            case "blocked":
                return LocaleController.getString(R.string.ProfileBannerStatusBlocked);
            default:
                return LocaleController.getString(R.string.ProfileBannerStatusNone);
        }
    }

    public String getAuthToken() {
        return prefs.getString(KEY_AUTH_TOKEN, null);
    }

    public void setAuthToken(String token) {
        if (token == null) {
            prefs.edit().remove(KEY_AUTH_TOKEN).apply();
        } else {
            prefs.edit().putString(KEY_AUTH_TOKEN, token).apply();
        }
    }

    public BannerMeta getBannerMeta(long userId) {
        if (userId == 0) return null;
        BannerMeta meta = bannerMetaMap.get(userId);
        if (meta != null) return meta;
        try {
            String hash = TsundereCrypto.hashUserId(userId);
            if (hash != null) {
                meta = bannerMetaByHash.get(hash);
                if (meta != null) {
                    bannerMetaMap.put(userId, meta);
                    return meta;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public String resolveBannerPath(long userId) {
        if (userId == getMyId()) {
            String serverStatus = getServerStatus();
            if (!"approved".equals(serverStatus)) {
                String local = getLocalBannerPath();
                if (local != null) return local;
            }
        }
        BannerMeta meta = getBannerMeta(userId);
        if (meta != null && !TextUtils.isEmpty(meta.url)) {
            String hash = Utilities.MD5(meta.url);
            String fileName = userId + "_" + (hash != null ? hash : meta.version) + "." + meta.type;
            File diskFile = new File(cacheDir, fileName);
            if (diskFile.exists() && diskFile.length() > 0) {
                cachedBannerPaths.put(userId, diskFile.getAbsolutePath());
                return diskFile.getAbsolutePath();
            }
        }
        String cached = cachedBannerPaths.get(userId);
        if (cached != null && new File(cached).exists()) {
            return cached;
        }
        return null;
    }

    public boolean isVideo(String path) {
        if (path == null) return false;
        String lower = path.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".mkv");
    }

    public boolean isGif(String path) {
        if (path == null) return false;
        return path.toLowerCase(java.util.Locale.ROOT).endsWith(".gif");
    }

    public void detachGif() {
        if (activeGifDrawable != null) {
            try {
                activeGifDrawable.recycle();
            } catch (Throwable ignored) {}
            activeGifDrawable = null;
            activeGifPath = null;
        }
    }

    public Bitmap getVideoThumbnail(String path) {
        if (path == null) return null;
        Bitmap cached = bitmapCache.get("thumb_" + path);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }
        File diskThumb = new File(path + ".poster2.jpg");
        if (diskThumb.exists() && diskThumb.length() > 0) {
            try {
                Bitmap frame = BitmapFactory.decodeFile(diskThumb.getAbsolutePath());
                if (frame != null) {
                    bitmapCache.put("thumb_" + path, frame);
                    return frame;
                }
            } catch (Throwable ignored) {}
        }
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            Bitmap frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (frame == null) {
                frame = retriever.getFrameAtTime(); // TsundereGram: запасной кадр, если у видео нет ключевого кадра в начале
            }
            if (frame == null) {
                frame = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST);
            }
            if (frame != null) {
                bitmapCache.put("thumb_" + path, frame);
                try (FileOutputStream fos = new FileOutputStream(diskThumb)) {
                    frame.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                } catch (Throwable ignored) {}
                return frame;
            }
        } catch (Throwable t) {
            FileLog.e(t);
        } finally {
            try {
                retriever.release();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static String getTelegramFileUrl(String fileId) {
        if (TextUtils.isEmpty(fileId)) return null;
        try {
            URL url = new URL(app.tsunderegram.security.TsundereApi.botBase() + "getFile?file_id=" + java.net.URLEncoder.encode(fileId, "UTF-8"));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            if (conn.getResponseCode() == 200) {
                BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
                JSONObject resp = new JSONObject(sb.toString());
                if (resp.optBoolean("ok")) {
                    String filePath = resp.getJSONObject("result").getString("file_path");
                    return app.tsunderegram.security.TsundereApi.fileBase() + filePath;
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return null;
    }

    public boolean isHideAvatar(long userId) {
        if (userId == getMyId()) {
            return isHideAvatarSelf();
        }
        BannerMeta meta = getBannerMeta(userId);
        return meta != null && meta.hideAvatar;
    }

    public void checkAndFetchBanner(long userId, Runnable onLoaded) {
        if (userId == 0) return;
        if (userId == getMyId() && hasLocalBanner() && !"approved".equals(getServerStatus())) {
            return;
        }
        BannerMeta meta = getBannerMeta(userId);
        if (meta != null && !TextUtils.isEmpty(meta.url)) {
            String hash = Utilities.MD5(meta.url);
            String fileName = userId + "_" + (hash != null ? hash : meta.version) + "." + meta.type;
            File outFile = new File(cacheDir, fileName);
            if (outFile.exists() && outFile.length() > 0) {
                cachedBannerPaths.put(userId, outFile.getAbsolutePath());
                return;
            }
        } else {
            String cached = cachedBannerPaths.get(userId);
            if (cached != null && new File(cached).exists()) {
                return;
            }
        }
        if (usersWithoutBanner.contains(userId) || loadingUsers.contains(userId)) {
            return;
        }

        if (meta == null || TextUtils.isEmpty(meta.url)) {
            if (!refreshingUsers.contains(userId)) {
                refreshingUsers.add(userId);
                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        com.tsunderegram.messenger.badges.BadgesController.INSTANCE.loadRemoteBadges();
                        BannerMeta refreshedMeta = getBannerMeta(userId);
                        if (refreshedMeta != null && !TextUtils.isEmpty(refreshedMeta.url)) {
                            checkAndFetchBanner(userId, onLoaded);
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        refreshingUsers.remove(userId);
                    }
                });
            }
            return;
        }

        final BannerMeta bannerMeta = meta;
        loadingUsers.add(userId);
        Utilities.globalQueue.postRunnable(() -> {
            try {
                String hash = Utilities.MD5(bannerMeta.url);
                String fileName = userId + "_" + (hash != null ? hash : bannerMeta.version) + "." + bannerMeta.type;
                File outFile = new File(cacheDir, fileName);
                if (!outFile.exists() || outFile.length() == 0) {
                    File tmp = new File(cacheDir, fileName + ".tmp");
                    if (tmp.exists()) {
                        tmp.delete();
                    }
                    String targetUrl = bannerMeta.url;
                    if (!TextUtils.isEmpty(targetUrl) && targetUrl.startsWith("tgfile:")) {
                        targetUrl = getTelegramFileUrl(targetUrl.substring(7));
                    }
                    if (!TextUtils.isEmpty(targetUrl)) {
                        HttpURLConnection dlConn = (HttpURLConnection) new URL(targetUrl).openConnection();
                        dlConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36");
                        dlConn.setRequestProperty("Accept", "*/*");
                        dlConn.setInstanceFollowRedirects(true);
                        dlConn.setConnectTimeout(20000);
                        dlConn.setReadTimeout(30000);
                        int respCode = dlConn.getResponseCode();
                        if (respCode >= 300 && respCode < 400) {
                            String redir = dlConn.getHeaderField("Location");
                            if (!TextUtils.isEmpty(redir)) {
                                dlConn.disconnect();
                                dlConn = (HttpURLConnection) new URL(redir).openConnection();
                                dlConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36");
                                dlConn.setRequestProperty("Accept", "*/*");
                                dlConn.setInstanceFollowRedirects(true);
                                dlConn.setConnectTimeout(20000);
                                dlConn.setReadTimeout(30000);
                                respCode = dlConn.getResponseCode();
                            }
                        }
                        if (respCode == 200) {
                            InputStream in = dlConn.getInputStream();
                            FileOutputStream out = new FileOutputStream(tmp);
                            byte[] buf = new byte[8192];
                            int len;
                            while ((len = in.read(buf)) != -1) {
                                out.write(buf, 0, len);
                            }
                            out.close();
                            in.close();
                            tmp.renameTo(outFile);
                        } else {
                            tmp.delete();
                        }
                    }
                }
                if (outFile.exists() && outFile.length() > 0) {
                    bannerMeta.localPath = outFile.getAbsolutePath();
                    cachedBannerPaths.put(userId, bannerMeta.localPath);
                    if (isVideo(bannerMeta.localPath)) {
                        getVideoThumbnail(bannerMeta.localPath);
                    }
                    AndroidUtilities.runOnUIThread(() -> {
                        if (onLoaded != null) {
                            onLoaded.run();
                        }
                        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                    });
                }
            } catch (Exception e) {
                failTimes.put(userId, System.currentTimeMillis());
                FileLog.e(e);
            } finally {
                loadingUsers.remove(userId);
            }
        });
    }

    public void fetchStatus(long userId, Consumer<String> callback) {
        if (userId == 0) {
            if (callback != null) callback.accept("none");
            return;
        }
        if (bannerMetaMap.containsKey(userId) || (userId == getMyId() && "approved".equals(getServerStatus()))) {
            setServerStatus("approved");
            if (callback != null) callback.accept("approved");
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                com.tsunderegram.messenger.badges.BadgesController.INSTANCE.loadRemoteBadges();
                Thread.sleep(800);
                if (bannerMetaMap.containsKey(userId)) {
                    setServerStatus("approved");
                }
            } catch (Exception ignored) {}
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.accept(getServerStatus()));
            }
        });
    }

    public void submitBanner(File file, int currentAccount, Runnable onSuccess, Consumer<String> onError) {
        long myId = getMyId();
        if (myId == 0) {
            if (onError != null) onError.accept("User ID error");
            return;
        }

        Utilities.globalQueue.postRunnable(() -> {
            try {
                // 1. Upload file
                String catboxUrl = com.tsunderegram.messenger.badges.BadgesController.uploadMedia(file);
                if (TextUtils.isEmpty(catboxUrl)) {
                    if (onError != null) {
                        AndroidUtilities.runOnUIThread(() -> onError.accept("Ошибка загрузки файла"));
                    }
                    return;
                }

                String lower = file.getName().toLowerCase();
                String ext = "jpg";
                if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".mkv")) {
                    ext = "mp4";
                } else if (lower.endsWith(".gif")) {
                    ext = "gif";
                } else if (lower.endsWith(".png")) {
                    ext = "png";
                }

                // 2. Fetch pinned message #26 and update "banners" section
                String getUrlStr = app.tsunderegram.security.TsundereApi.botBase() + "getChat?chat_id=" + com.tsunderegram.messenger.badges.BadgesController.CHANNEL_USERNAME;
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
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject resp = new JSONObject(sb.toString());
                JSONObject result = resp.getJSONObject("result");
                JSONObject pinned = result.optJSONObject("pinned_message");
                int messageId = pinned != null ? pinned.optInt("message_id", 26) : 26;
                String currentText = pinned != null ? pinned.optString("text", "") : "";

                String payload = currentText;
                int headerIdx = currentText.indexOf(com.tsunderegram.messenger.badges.BadgesController.CONFIG_HEADER);
                if (headerIdx >= 0) {
                    payload = currentText.substring(headerIdx + com.tsunderegram.messenger.badges.BadgesController.CONFIG_HEADER.length()).trim();
                }
                String decrypted = TsundereCrypto.decrypt(payload);

                JSONObject configJson;
                int startBrace = decrypted.indexOf('{');
                int endBrace = decrypted.lastIndexOf('}');
                if (startBrace >= 0 && endBrace > startBrace) {
                    configJson = new JSONObject(decrypted.substring(startBrace, endBrace + 1));
                } else {
                    configJson = new JSONObject();
                }

                JSONObject bannersJson = configJson.optJSONObject("banners");
                if (bannersJson == null) {
                    bannersJson = new JSONObject();
                    configJson.put("banners", bannersJson);
                }

                JSONObject myBanner = new JSONObject();
                myBanner.put("url", catboxUrl);
                myBanner.put("type", ext);
                myBanner.put("hide_avatar", isHideAvatarSelf());
                bannersJson.put(String.valueOf(myId), myBanner);

                String encPayload = TsundereCrypto.encrypt(configJson.toString());
                String updatedConfigStr = com.tsunderegram.messenger.badges.BadgesController.CONFIG_HEADER + "\nENC:" + encPayload;

                // 3. Save to pinned message
                String editUrlStr = app.tsunderegram.security.TsundereApi.botBase() + "editMessageText";
                JSONObject editPayload = new JSONObject();
                editPayload.put("chat_id", com.tsunderegram.messenger.badges.BadgesController.CHANNEL_USERNAME);
                editPayload.put("message_id", messageId);
                editPayload.put("text", updatedConfigStr);

                URL editUrl = new URL(editUrlStr);
                HttpURLConnection editConn = (HttpURLConnection) editUrl.openConnection();
                editConn.setRequestMethod("POST");
                app.tsunderegram.security.TsundereApi.authorize(editConn);
                editConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                editConn.setDoOutput(true);
                editConn.setConnectTimeout(8000);
                editConn.setReadTimeout(8000);

                byte[] outBytes = editPayload.toString().getBytes(StandardCharsets.UTF_8);
                try (java.io.OutputStream os = editConn.getOutputStream()) {
                    os.write(outBytes);
                    os.flush();
                }

                int editCode = editConn.getResponseCode();
                if (editCode == 200) {
                    File dest = new File(cacheDir, myId + "_1." + ext);
                    copyFile(file, dest);
                    cachedBannerPaths.put(myId, dest.getAbsolutePath());

                    BannerMeta meta = new BannerMeta();
                    meta.hasBanner = true;
                    meta.url = catboxUrl;
                    meta.type = ext;
                    meta.version = "1";
                    meta.hideAvatar = isHideAvatarSelf();
                    meta.localPath = dest.getAbsolutePath();
                    bannerMetaMap.put(myId, meta);
                    markRecentlySet(myId);

                    setServerStatus("approved");
                    bitmapCache.evictAll();
                    blurCache.evictAll();
                    detachVideo();

                    if (onSuccess != null) {
                        AndroidUtilities.runOnUIThread(onSuccess);
                    }
                } else {
                    if (onError != null) {
                        AndroidUtilities.runOnUIThread(() -> onError.accept(app.tsunderegram.security.TsundereApi.httpError("Ошибка сохранения в Telegram: ", editCode)));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
                if (onError != null) {
                    AndroidUtilities.runOnUIThread(() -> onError.accept(app.tsunderegram.security.TsundereApi.explain(e)));
                }
            }
        });
    }

    // ---- Баннеры каналов (ключ в общем конфиге — отрицательный id диалога канала) ----

    private interface BannersMutator {
        void mutate(JSONObject bannersJson) throws Exception;
    }

    /** Читает зашифрованный конфиг из закреплённого сообщения, меняет секцию banners и сохраняет. Возвращает HTTP-код. */
    private int updateRemoteBanners(BannersMutator mutator) throws Exception {
        final String getUrlStr = app.tsunderegram.security.TsundereApi.botBase() + "getChat?chat_id=" + com.tsunderegram.messenger.badges.BadgesController.CHANNEL_USERNAME;
        HttpURLConnection getConn = (HttpURLConnection) new URL(getUrlStr).openConnection();
        getConn.setRequestMethod("GET");
        getConn.setConnectTimeout(8000);
        getConn.setReadTimeout(8000);
        final int getCode = getConn.getResponseCode();
        if (getCode != 200) {
            return getCode;
        }
        final StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getConn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        }
        final JSONObject result = new JSONObject(sb.toString()).getJSONObject("result");
        final JSONObject pinned = result.optJSONObject("pinned_message");
        final int messageId = pinned != null ? pinned.optInt("message_id", 26) : 26;
        final String currentText = pinned != null ? pinned.optString("text", "") : "";
        String payload = currentText;
        final int headerIdx = currentText.indexOf(com.tsunderegram.messenger.badges.BadgesController.CONFIG_HEADER);
        if (headerIdx >= 0) {
            payload = currentText.substring(headerIdx + com.tsunderegram.messenger.badges.BadgesController.CONFIG_HEADER.length()).trim();
        }
        final String decrypted = TsundereCrypto.decrypt(payload);
        JSONObject configJson;
        final int startBrace = decrypted.indexOf('{');
        final int endBrace = decrypted.lastIndexOf('}');
        if (startBrace >= 0 && endBrace > startBrace) {
            configJson = new JSONObject(decrypted.substring(startBrace, endBrace + 1));
        } else {
            configJson = new JSONObject();
        }
        JSONObject bannersJson = configJson.optJSONObject("banners");
        if (bannersJson == null) {
            bannersJson = new JSONObject();
            configJson.put("banners", bannersJson);
        }
        mutator.mutate(bannersJson);

        final String updatedConfigStr = com.tsunderegram.messenger.badges.BadgesController.CONFIG_HEADER + "\nENC:" + TsundereCrypto.encrypt(configJson.toString());
        if (updatedConfigStr.length() > 4096) {
            return 413; // не влезает в одно сообщение Telegram
        }
        final JSONObject editPayload = new JSONObject();
        editPayload.put("chat_id", com.tsunderegram.messenger.badges.BadgesController.CHANNEL_USERNAME);
        editPayload.put("message_id", messageId);
        editPayload.put("text", updatedConfigStr);
        HttpURLConnection editConn = (HttpURLConnection) new URL(app.tsunderegram.security.TsundereApi.botBase() + "editMessageText").openConnection();
        editConn.setRequestMethod("POST");
        app.tsunderegram.security.TsundereApi.authorize(editConn);
        editConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        editConn.setDoOutput(true);
        editConn.setConnectTimeout(8000);
        editConn.setReadTimeout(8000);
        try (java.io.OutputStream os = editConn.getOutputStream()) {
            os.write(editPayload.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        return editConn.getResponseCode();
    }

    /** Есть ли у канала (dialogId < 0) глобальный баннер. */
    public boolean hasChannelBanner(long channelDialogId) {
        return channelDialogId < 0 && getBannerMeta(channelDialogId) != null;
    }

    /** Загружает баннер канала: файл на хостинг, ссылка — в общий конфиг под ключом id канала. */
    public void submitChannelBanner(File file, long channelDialogId, boolean hideAvatar, Runnable onSuccess, Consumer<String> onError) {
        if (channelDialogId >= 0) {
            if (onError != null) onError.accept("Channel ID error");
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final String url = com.tsunderegram.messenger.badges.BadgesController.uploadMedia(file);
                if (TextUtils.isEmpty(url)) {
                    if (onError != null) AndroidUtilities.runOnUIThread(() -> onError.accept("Ошибка загрузки файла"));
                    return;
                }
                final String lower = file.getName().toLowerCase();
                String ext = "jpg";
                if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".mkv")) {
                    ext = "mp4";
                } else if (lower.endsWith(".gif")) {
                    ext = "gif";
                } else if (lower.endsWith(".png")) {
                    ext = "png";
                }
                final String type = ext;
                final int code = updateRemoteBanners(bannersJson -> {
                    final JSONObject banner = new JSONObject();
                    banner.put("url", url);
                    banner.put("type", type);
                    banner.put("hide_avatar", hideAvatar);
                    bannersJson.put(String.valueOf(channelDialogId), banner);
                });
                if (code != 200) {
                    if (onError != null) AndroidUtilities.runOnUIThread(() -> onError.accept(code == 413 ? "Общий конфиг баннеров переполнен (лимит сообщения Telegram)" : app.tsunderegram.security.TsundereApi.httpError("Ошибка сохранения в Telegram: ", code)));
                    return;
                }
                final File dest = new File(cacheDir, channelDialogId + "_" + Utilities.MD5(url) + "." + type);
                copyFile(file, dest);
                final BannerMeta meta = new BannerMeta();
                meta.hasBanner = true;
                meta.url = url;
                meta.type = type;
                meta.version = "1";
                meta.hideAvatar = hideAvatar;
                meta.localPath = dest.getAbsolutePath();
                bannerMetaMap.put(channelDialogId, meta);
                cachedBannerPaths.put(channelDialogId, dest.getAbsolutePath());
                usersWithoutBanner.remove(channelDialogId);
                markRecentlySet(channelDialogId);
                bitmapCache.evictAll();
                blurCache.evictAll();
                detachVideo();
                AndroidUtilities.runOnUIThread(() -> {
                    if (onSuccess != null) onSuccess.run();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                });
            } catch (Exception e) {
                FileLog.e(e);
                if (onError != null) AndroidUtilities.runOnUIThread(() -> onError.accept(app.tsunderegram.security.TsundereApi.explain(e)));
            }
        });
    }

    public void setChannelBannerHideAvatar(long channelDialogId, boolean hide, Runnable onSuccess, Consumer<String> onError) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final int code = updateRemoteBanners(bannersJson -> {
                    final JSONObject banner = bannersJson.optJSONObject(String.valueOf(channelDialogId));
                    if (banner != null) {
                        banner.put("hide_avatar", hide);
                    }
                });
                if (code != 200) {
                    if (onError != null) AndroidUtilities.runOnUIThread(() -> onError.accept(app.tsunderegram.security.TsundereApi.httpError("", code)));
                    return;
                }
                final BannerMeta meta = getBannerMeta(channelDialogId);
                if (meta != null) meta.hideAvatar = hide;
                AndroidUtilities.runOnUIThread(() -> {
                    if (onSuccess != null) onSuccess.run();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                });
            } catch (Exception e) {
                FileLog.e(e);
                if (onError != null) AndroidUtilities.runOnUIThread(() -> onError.accept(app.tsunderegram.security.TsundereApi.explain(e)));
            }
        });
    }

    public void deleteChannelBanner(long channelDialogId, Runnable onSuccess, Consumer<String> onError) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                final int code = updateRemoteBanners(bannersJson -> {
                    bannersJson.remove(String.valueOf(channelDialogId));
                    final String hash = TsundereCrypto.hashUserId(channelDialogId);
                    if (hash != null) bannersJson.remove(hash);
                });
                if (code != 200) {
                    if (onError != null) AndroidUtilities.runOnUIThread(() -> onError.accept(app.tsunderegram.security.TsundereApi.httpError("", code)));
                    return;
                }
                recentlySetBanners.remove(channelDialogId);
                forgetBanner(channelDialogId);
                bitmapCache.evictAll();
                blurCache.evictAll();
                detachVideo();
                AndroidUtilities.runOnUIThread(() -> {
                    if (onSuccess != null) onSuccess.run();
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                });
            } catch (Exception e) {
                FileLog.e(e);
                if (onError != null) AndroidUtilities.runOnUIThread(() -> onError.accept(app.tsunderegram.security.TsundereApi.explain(e)));
            }
        });
    }

    public boolean hasGlobalBanner() {
        return "approved".equals(getServerStatus()) || bannerMetaMap.containsKey(getMyId());
    }

    public void deleteGlobalBanner(int currentAccount, Runnable onSuccess, Consumer<String> onError) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                long myId = getMyId();
                String getChatUrl = app.tsunderegram.security.TsundereApi.botBase() + "getChat?chat_id=" + com.tsunderegram.messenger.badges.BadgesController.CHANNEL_USERNAME;
                HttpURLConnection conn = (HttpURLConnection) new URL(getChatUrl).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                InputStream is = (conn.getResponseCode() == 200) ? conn.getInputStream() : conn.getErrorStream();
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int r;
                while ((r = is.read(buf)) != -1) baos.write(buf, 0, r);
                is.close();

                JSONObject resp = new JSONObject(baos.toString("UTF-8"));
                JSONObject result = resp.getJSONObject("result");
                JSONObject pinned = result.optJSONObject("pinned_message");
                int messageId = pinned != null ? pinned.optInt("message_id", 26) : 26;
                String currentText = pinned != null ? pinned.optString("text", "") : "";

                String payload = currentText;
                int headerIdx = currentText.indexOf(com.tsunderegram.messenger.badges.BadgesController.CONFIG_HEADER);
                if (headerIdx >= 0) {
                    payload = currentText.substring(headerIdx + com.tsunderegram.messenger.badges.BadgesController.CONFIG_HEADER.length()).trim();
                }
                String decrypted = TsundereCrypto.decrypt(payload);

                JSONObject configJson;
                int startBrace = decrypted.indexOf('{');
                int endBrace = decrypted.lastIndexOf('}');
                if (startBrace >= 0 && endBrace > startBrace) {
                    configJson = new JSONObject(decrypted.substring(startBrace, endBrace + 1));
                } else {
                    configJson = new JSONObject();
                }

                JSONObject bannersJson = configJson.optJSONObject("banners");
                if (bannersJson != null) {
                    bannersJson.remove(String.valueOf(myId));
                    bannersJson.remove(TsundereCrypto.hashUserId(myId));
                }

                String encPayload = TsundereCrypto.encrypt(configJson.toString());
                String updatedConfigStr = com.tsunderegram.messenger.badges.BadgesController.CONFIG_HEADER + "\nENC:" + encPayload;

                String editUrlStr = app.tsunderegram.security.TsundereApi.botBase() + "editMessageText";
                JSONObject editPayload = new JSONObject();
                editPayload.put("chat_id", com.tsunderegram.messenger.badges.BadgesController.CHANNEL_USERNAME);
                editPayload.put("message_id", messageId);
                editPayload.put("text", updatedConfigStr);

                HttpURLConnection editConn = (HttpURLConnection) new URL(editUrlStr).openConnection();
                editConn.setRequestMethod("POST");
                app.tsunderegram.security.TsundereApi.authorize(editConn);
                editConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                editConn.setDoOutput(true);
                editConn.setConnectTimeout(8000);
                editConn.setReadTimeout(8000);

                byte[] outBytes = editPayload.toString().getBytes(StandardCharsets.UTF_8);
                try (java.io.OutputStream os = editConn.getOutputStream()) {
                    os.write(outBytes);
                    os.flush();
                }

                int editCode = editConn.getResponseCode();
                if (editCode == 200) {
                    recentlySetBanners.remove(myId);
                    forgetBanner(myId);
                    setServerStatus("none");
                    bitmapCache.evictAll();
                    blurCache.evictAll();
                    detachVideo();
                    if (onSuccess != null) {
                        AndroidUtilities.runOnUIThread(onSuccess);
                    }
                } else {
                    if (onError != null) {
                        AndroidUtilities.runOnUIThread(() -> onError.accept(app.tsunderegram.security.TsundereApi.httpError("Ошибка: ", editCode)));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
                if (onError != null) {
                    AndroidUtilities.runOnUIThread(() -> onError.accept(app.tsunderegram.security.TsundereApi.explain(e)));
                }
            }
        });
    }

    private void writeFormField(DataOutputStream dos, String boundary, String fieldName, String value) throws Exception {
        dos.writeBytes("--" + boundary + "\r\n");
        dos.writeBytes("Content-Disposition: form-data; name=\"" + fieldName + "\"\r\n\r\n");
        dos.writeBytes(value + "\r\n");
    }

    private void copyFile(File src, File dst) throws Exception {
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }

    public boolean drawBanner(Canvas canvas, View topView, ProfileActivity profileActivity, int width, int height, float extraHeight, long userId) {
        if (width <= 0 || height <= 0 || userId == 0) return false;

        checkAndFetchBanner(userId, topView::invalidate);

        String path = resolveBannerPath(userId);
        if (path == null && isUseAvatarAsBanner()) {
            // Fallback to avatar banner if allowed
            return drawAvatarAsBanner(canvas, topView, profileActivity, width, height, extraHeight, userId);
        }

        if (path == null) return false;

        if (isVideo(path)) {
            detachGif();
            Bitmap thumb = getVideoThumbnail(path);
            if (!isFirstFrameRendered || !TextUtils.equals(activeVideoPath, path) || attachedTopView != topView || (activeTextureView != null && activeTextureView.getAlpha() < 0.98f)) {
                if (thumb != null && !thumb.isRecycled()) {
                    drawBitmapInternal(canvas, thumb, width, height, extraHeight, "thumb_" + path);
                }
            }
            return true;
        }

        if (isGif(path)) {
            if (activeGifDrawable == null || !TextUtils.equals(activeGifPath, path)) {
                detachGif();
                try {
                    activeGifPath = path;
                    activeGifDrawable = new org.telegram.ui.Components.AnimatedFileDrawable(
                            new File(path),
                            true,
                            0,
                            0,
                            null,
                            null,
                            null,
                            0,
                            org.telegram.messenger.UserConfig.selectedAccount,
                            false,
                            null
                    );
                    activeGifDrawable.addSecondParentView(topView);
                    activeGifDrawable.start();
                } catch (Throwable e) {
                    FileLog.e(e);
                    detachGif();
                }
            }

            if (activeGifDrawable != null) {
                int bw = activeGifDrawable.getIntrinsicWidth();
                int bh = activeGifDrawable.getIntrinsicHeight();
                if (bw <= 0 || bh <= 0) {
                    bw = width;
                    bh = height;
                }
                // TsundereGram: GIF заполняет всю шапку (cover) без искажений и без полос сверху/снизу
                float scale = Math.max((float) width / bw, (float) stableHeight(height) / bh);
                float dw = bw * scale;
                float dh = bh * scale;
                float dx = (width - dw) * 0.5f;
                float dy = (height - dh) * 0.5f;
                final boolean letterbox = dh < height - 1;

                int save = canvas.save();
                canvas.clipRect(0, 0, width, height);
                if (letterbox) {
                    Bitmap frame = activeGifDrawable.getAnimatedBitmap();
                    Bitmap backdrop = blurCache.get("bd_gif_" + path);
                    if ((backdrop == null || backdrop.isRecycled()) && frame != null && !frame.isRecycled()) {
                        backdrop = getBackdrop("gif_" + path, frame);
                    }
                    drawBackdrop(canvas, backdrop, width, height);
                    canvas.saveLayer(0, Math.max(0, dy), width, Math.min(height, dy + dh), null);
                }
                activeGifDrawable.setBounds((int) dx, (int) dy, (int) (dx + dw), (int) (dy + dh));
                activeGifDrawable.draw(canvas);
                if (letterbox) {
                    featherEdges(canvas, width, dy, dy + dh);
                    canvas.restore();
                }

                // Dark dim overlay for readable white text
                float maxEh = Math.max(extraHeight, (float) AndroidUtilities.dp(200));
                float coll = Math.max(0f, Math.min(1f, 1f - extraHeight / maxEh));
                int darkAlpha = Math.min(70 + (int) (coll * 130), 220);
                darkPaint.setColor(Color.argb(darkAlpha, 0, 0, 0));
                canvas.drawRect(0, 0, width, height, darkPaint);

                // Bottom gradient for smooth transition into the profile list
                int gh = Math.max(1, (int) (height * 0.45f));
                LinearGradient grad = new LinearGradient(0, height - gh, 0, height,
                        Color.TRANSPARENT, Color.argb(160, 0, 0, 0), Shader.TileMode.CLAMP);
                gradPaint.setShader(grad);
                canvas.drawRect(0, height - gh, width, height, gradPaint);

                canvas.restore();
                return true;
            }
        } else {
            detachGif();
        }

        // Draw image banner
        Bitmap bmp = bitmapCache.get(path);
        if (bmp == null || bmp.isRecycled()) {
            try {
                bmp = BitmapFactory.decodeFile(path);
                if (bmp != null) {
                    bitmapCache.put(path, bmp);
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }

        if (bmp == null || bmp.isRecycled()) return false;
        drawBitmapInternal(canvas, bmp, width, height, extraHeight, path);
        return true;
    }

    private final Paint featherPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix backdropMatrix = new Matrix();
    private LinearGradient featherShader;
    private float featherTop = Float.NaN, featherBottom = Float.NaN;

    /**
     * TsundereGram: размытая уменьшенная копия баннера для заполнения шапки по краям,
     * когда сам баннер (вписанный по ширине) ниже шапки.
     */
    private Bitmap getBackdrop(String cacheKey, Bitmap src) {
        if (cacheKey == null || src == null || src.isRecycled()) {
            return null;
        }
        final String key = "bd_" + cacheKey;
        Bitmap bd = blurCache.get(key);
        if (bd != null && !bd.isRecycled()) {
            return bd;
        }
        try {
            int sw = src.getWidth(), sh = src.getHeight();
            float k = Math.min(1f, 96f / Math.max(1, Math.max(sw, sh)));
            Bitmap small = Bitmap.createScaledBitmap(src, Math.max(1, (int) (sw * k)), Math.max(1, (int) (sh * k)), true);
            if (small == src) {
                small = src.copy(Bitmap.Config.ARGB_8888, true);
            } else if (small.getConfig() != Bitmap.Config.ARGB_8888 || !small.isMutable()) {
                Bitmap c = small.copy(Bitmap.Config.ARGB_8888, true);
                small.recycle();
                small = c;
            }
            if (small != null) {
                Utilities.stackBlurBitmap(small, 6);
                blurCache.put(key, small);
            }
            return small;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    /** Подложка: размытая копия «на всю шапку» (масштаб от стабильной высоты — не прыгает при сворачивании). */
    private void drawBackdrop(Canvas canvas, Bitmap backdrop, int width, int height) {
        if (backdrop == null || backdrop.isRecycled()) {
            return;
        }
        final int fullH = stableHeight(height);
        float sc = Math.max((float) width / backdrop.getWidth(), (float) fullH / backdrop.getHeight()) * 1.08f;
        backdropMatrix.reset();
        backdropMatrix.postScale(sc, sc);
        backdropMatrix.postTranslate((width - backdrop.getWidth() * sc) * 0.5f, (height - backdrop.getHeight() * sc) * 0.5f);
        blurPaint.setAlpha(255);
        canvas.drawBitmap(backdrop, backdropMatrix, blurPaint);
        darkPaint.setColor(Color.argb(46, 0, 0, 0));
        canvas.drawRect(0, 0, width, height, darkPaint);
    }

    /** Мягкие верх/низ у вписанного баннера, чтобы он плавно переходил в подложку. */
    private void featherEdges(Canvas canvas, int width, float top, float bottom) {
        if (featherShader == null || featherTop != top || featherBottom != bottom) {
            float h = Math.max(1f, bottom - top);
            float f = Math.min(0.3f, AndroidUtilities.dp(22) / h);
            featherShader = new LinearGradient(0, top, 0, bottom,
                    new int[]{0x00000000, 0xFF000000, 0xFF000000, 0x00000000},
                    new float[]{0f, f, 1f - f, 1f}, Shader.TileMode.CLAMP);
            featherTop = top;
            featherBottom = bottom;
            featherPaint.setShader(featherShader);
            featherPaint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
        }
        canvas.drawRect(0, top, width, bottom, featherPaint);
    }

    /**
     * TsundereGram: баннер всегда виден целиком по ширине и без искажений (одинаковый масштаб по осям).
     * Если он ниже шапки — сверху/снизу размытая подложка из того же баннера; если выше — обрезается
     * только по вертикали. Масштаб не зависит от сворачивания шапки, поэтому картинка не «дышит».
     */
    private void drawBitmapInternal(Canvas canvas, Bitmap bmp, int width, int height, float extraHeight, String cacheKey) {
        if (bmp == null || bmp.isRecycled()) return;

        int bw = bmp.getWidth();
        int bh = bmp.getHeight();
        if (bw <= 0 || bh <= 0) return;

        float maxEh = Math.max(extraHeight, (float) AndroidUtilities.dp(200));
        float coll = Math.max(0f, Math.min(1f, 1f - extraHeight / maxEh));

        // TsundereGram: баннер заполняет всю шапку (cover) — без размытых полос под статус-баром
        // и над кнопками «Выбрать фото / Изменить». Масштаб от стабильной высоты, поэтому при
        // сворачивании картинка не «дышит», а только обрезается.
        float scale = Math.max((float) width / bw, (float) stableHeight(height) / bh);
        float dh = bh * scale;
        float dx = (width - bw * scale) * 0.5f;
        float dy = (height - dh) * 0.5f;
        final boolean letterbox = dh < height - 1;

        drawMatrix.reset();
        drawMatrix.postScale(scale, scale);
        drawMatrix.postTranslate(dx, dy);

        int save = canvas.save();
        canvas.clipRect(0, 0, width, height);

        if (letterbox) {
            drawBackdrop(canvas, getBackdrop(cacheKey, bmp), width, height);
            canvas.saveLayer(0, Math.max(0, dy), width, Math.min(height, dy + dh), null);
        }

        // Base image
        bannerPaint.setAlpha(255);
        canvas.drawBitmap(bmp, drawMatrix, bannerPaint);

        // Blur layer on scroll/collapse
        if (coll > 0.05f && cacheKey != null) {
            Bitmap blurBmp = blurCache.get(cacheKey);
            if (blurBmp == null || blurBmp.isRecycled()) {
                try {
                    blurBmp = bmp.copy(Bitmap.Config.ARGB_8888, true);
                    if (blurBmp != null) {
                        Utilities.stackBlurBitmap(blurBmp, 25);
                        blurCache.put(cacheKey, blurBmp);
                    }
                } catch (Throwable ignored) {}
            }
            if (blurBmp != null && !blurBmp.isRecycled()) {
                blurPaint.setAlpha((int) (coll * 255));
                canvas.drawBitmap(blurBmp, drawMatrix, blurPaint);
            }
        }

        if (letterbox) {
            featherEdges(canvas, width, dy, dy + dh);
            canvas.restore();
        }

        // Dark dim overlay only on scroll
        int darkAlpha = (int) (coll * 150);
        if (darkAlpha > 0) {
            darkPaint.setColor(Color.argb(darkAlpha, 0, 0, 0));
            canvas.drawRect(0, 0, width, height, darkPaint);
        }

        // Bottom gradient for readability
        int gh = Math.max(1, (int) (height * 0.35f));
        LinearGradient grad = new LinearGradient(0, height - gh, 0, height,
                Color.TRANSPARENT, Color.argb((int) (70 + coll * 90), 0, 0, 0), Shader.TileMode.CLAMP);
        gradPaint.setShader(grad);
        canvas.drawRect(0, height - gh, width, height, gradPaint);

        canvas.restoreToCount(save);
    }

    private boolean drawAvatarAsBanner(Canvas canvas, View topView, ProfileActivity profileActivity, int width, int height, float extraHeight, long userId) {
        String key = "avatar_" + userId;
        Bitmap blurBmp = blurCache.get(key);
        if (blurBmp == null || blurBmp.isRecycled()) {
            if (profileActivity != null && profileActivity.avatarImage != null) {
                Bitmap avatarBmp = profileActivity.avatarImage.getImageReceiver().getBitmap();
                if (avatarBmp != null && !avatarBmp.isRecycled()) {
                    try {
                        blurBmp = avatarBmp.copy(Bitmap.Config.ARGB_8888, true);
                        if (blurBmp != null) {
                            Utilities.stackBlurBitmap(blurBmp, 25);
                            blurCache.put(key, blurBmp);
                        }
                    } catch (Throwable ignored) {}
                }
            }
        }

        if (blurBmp == null || blurBmp.isRecycled()) return false;

        float scale = Math.max((float) width / blurBmp.getWidth(), (float) stableHeight(height) / blurBmp.getHeight()) * 1.05f;
        float dx = (width - blurBmp.getWidth() * scale) * 0.5f;
        float dy = (height - blurBmp.getHeight() * scale) * 0.5f;

        drawMatrix.reset();
        drawMatrix.postScale(scale, scale);
        drawMatrix.postTranslate(dx, dy);

        int save = canvas.save();
        canvas.clipRect(0, 0, width, height);

        blurPaint.setAlpha(255);
        canvas.drawBitmap(blurBmp, drawMatrix, blurPaint);

        darkPaint.setColor(Color.argb(120, 0, 0, 0));
        canvas.drawRect(0, 0, width, height, darkPaint);

        int gh = Math.max(1, (int) (height * 0.45f));
        LinearGradient grad = new LinearGradient(0, height - gh, 0, height,
                Color.TRANSPARENT, Color.argb(160, 0, 0, 0), Shader.TileMode.CLAMP);
        gradPaint.setShader(grad);
        canvas.drawRect(0, height - gh, width, height, gradPaint);

        canvas.restoreToCount(save);
        return true;
    }

    private void updateTextureTransform() {
        if (activeTextureView == null || lastVideoWidth <= 0 || lastVideoHeight <= 0) return;
        int viewWidth = activeTextureView.getWidth();
        int viewHeight = activeTextureView.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) return;

        int vw = lastVideoWidth;
        int vh = lastVideoHeight;
        if (lastRotation == 90 || lastRotation == 270) {
            int tmp = vw;
            vw = vh;
            vh = tmp;
        }

        // TsundereGram: видео заполняет всю шапку (cover) без искажений и без полос сверху/снизу
        float cover = Math.max((float) viewWidth / vw, (float) viewHeight / vh);
        float scaleX = vw * cover / viewWidth;
        float scaleY = vh * cover / viewHeight;

        Matrix matrix = new Matrix();
        if (lastRotation != 0) {
            matrix.postRotate(lastRotation, viewWidth / 2f, viewHeight / 2f);
        }
        matrix.postScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f);
        activeTextureView.setTransform(matrix);
    }

    public void attachVideoIfNeeded(FrameLayout topView, long userId, int width, int height) {
        if (topView == null || userId == 0 || width <= 0 || height <= 0) return;
        String path = resolveBannerPath(userId);
        if (!isVideo(path)) {
            if (attachedTopView == topView) {
                detachVideo();
            }
            return;
        }

        if (activeVideoPlayer != null && TextUtils.equals(activeVideoPath, path) && attachedTopView == topView) {
            topView.post(() -> updateVideoHeight(height));
            return;
        }

        topView.post(() -> attachVideoInternal(topView, userId, path, width, height));
    }

    private void attachVideoInternal(FrameLayout topView, long userId, String path, int width, int height) {
        if (topView == null || !topView.isAttachedToWindow()) return;
        if (activeVideoPlayer != null && TextUtils.equals(activeVideoPath, path) && attachedTopView == topView) {
            updateVideoHeight(height);
            return;
        }

        detachVideo();

        try {
            attachedTopView = topView;
            activeVideoPath = path;
            activeVideoUserId = userId;
            isFirstFrameRendered = false;

            activeTextureView = new TextureView(topView.getContext());
            activeTextureView.setOpaque(false);
            activeTextureView.setAlpha(0f);
            activeTextureView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
                if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                    updateTextureTransform();
                }
            });

            if (activeTextureView.getParent() != null) {
                ((ViewGroup) activeTextureView.getParent()).removeView(activeTextureView);
            }
            final int fullH = stableHeight(height);
            topView.addView(activeTextureView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, fullH));
            topView.invalidate();
            activeTextureView.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(fullH, View.MeasureSpec.EXACTLY)
            );
            activeTextureView.layout(0, 0, width, fullH);
            applyVideoClip(height, fullH);

            activeVideoPlayer = new VideoPlayer(false, true);
            activeVideoPlayer.allowMultipleInstances = true;
            activeVideoPlayer.setTextureView(activeTextureView);
            activeVideoPlayer.setDelegate(new VideoPlayer.VideoPlayerDelegate() {
                @Override
                public void onStateChanged(boolean playWhenReady, int playbackState) {}

                @Override
                public void onError(VideoPlayer player, Exception e) {
                    FileLog.e(e);
                }

                @Override
                public void onVideoSizeChanged(int width, int height, int unappliedRotationDegrees, float pixelWidthHeightRatio) {
                    lastVideoWidth = width;
                    lastVideoHeight = height;
                    lastRotation = unappliedRotationDegrees;
                    AndroidUtilities.runOnUIThread(() -> updateTextureTransform());
                }

                @Override
                public void onRenderedFirstFrame() {
                    isFirstFrameRendered = true;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (activeTextureView != null) {
                            activeTextureView.animate().alpha(1.0f).setDuration(220).start();
                        }
                        captureVideoPoster(path);
                        if (attachedTopView != null) {
                            attachedTopView.invalidate();
                        }
                    });
                }
            });
            activeVideoPlayer.preparePlayer(Uri.fromFile(new File(path)), "other");
            activeVideoPlayer.setPlayWhenReady(true);
            activeVideoPlayer.setLooping(true);
            activeVideoPlayer.setMute(true);
        } catch (Exception e) {
            FileLog.e(e);
            detachVideo();
        }
    }

    private final java.util.Set<String> capturedPosters = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    /**
     * TsundereGram: сохраняет кадр из уже играющего видео как обложку баннера. В следующий раз
     * обложка рисуется сразу, пока видео запускается, — без серого фона.
     */
    private void captureVideoPoster(String path) {
        if (path == null || capturedPosters.contains(path)) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                if (activeTextureView == null || !TextUtils.equals(activeVideoPath, path) || !activeTextureView.isAvailable()) {
                    return;
                }
                final int w = activeTextureView.getWidth(), h = activeTextureView.getHeight();
                if (w <= 0 || h <= 0) {
                    return;
                }
                final float k = Math.min(1f, 720f / Math.max(w, h));
                Bitmap full = activeTextureView.getBitmap(Math.max(1, (int) (w * k)), Math.max(1, (int) (h * k)));
                if (full == null) {
                    return;
                }
                Bitmap cropped = full;
                if (lastVideoWidth > 0 && lastVideoHeight > 0) {
                    int vw = lastVideoWidth, vh = lastVideoHeight;
                    if (lastRotation == 90 || lastRotation == 270) {
                        int tmp = vw; vw = vh; vh = tmp;
                    }
                    int fw = full.getWidth(), fh = full.getHeight();
                    int bandH = Math.max(1, Math.min(fh, Math.round((float) fw * vh / vw)));
                    if (bandH < fh) {
                        cropped = Bitmap.createBitmap(full, 0, (fh - bandH) / 2, fw, bandH);
                    }
                }
                final Bitmap frame = cropped;
                capturedPosters.add(path);
                bitmapCache.put("thumb_" + path, frame);
                blurCache.remove("thumb_" + path);
                final File diskThumb = new File(path + ".poster2.jpg");
                Utilities.globalQueue.postRunnable(() -> {
                    try (FileOutputStream fos = new FileOutputStream(diskThumb)) {
                        frame.compress(Bitmap.CompressFormat.JPEG, 88, fos);
                    } catch (Throwable ignored) {
                    }
                });
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }, 1200);
    }

    public void updateVideoHeight(int height) {
        if (activeTextureView != null) {
            final int fullH = stableHeight(height);
            ViewGroup.LayoutParams lp = activeTextureView.getLayoutParams();
            if (lp != null && lp.height != fullH) {
                lp.height = fullH;
                activeTextureView.setLayoutParams(lp);
            }
            applyVideoClip(height, fullH);
        }
    }

    /** Видео держит постоянный размер: сдвигаем его так, чтобы центр совпадал с видимой частью шапки, и обрезаем. */
    private void applyVideoClip(int height, int fullH) {
        if (activeTextureView == null) {
            return;
        }
        final int offset = Math.max(0, (fullH - height) / 2);
        activeTextureView.setTranslationY(-offset);
        final int w = activeTextureView.getWidth() > 0 ? activeTextureView.getWidth() : (attachedTopView != null ? attachedTopView.getWidth() : 0);
        if (w > 0) {
            activeTextureView.setClipBounds(new android.graphics.Rect(0, offset, w, offset + Math.max(0, height)));
        }
    }

    public void detachVideo() {
        detachGif();
        isFirstFrameRendered = false;
        if (activeVideoPlayer != null) {
            try {
                activeVideoPlayer.releasePlayer(true);
            } catch (Exception ignored) {}
            activeVideoPlayer = null;
        }
        if (activeTextureView != null && attachedTopView != null) {
            try {
                attachedTopView.removeView(activeTextureView);
            } catch (Exception ignored) {}
            activeTextureView = null;
        }
        attachedTopView = null;
        activeVideoPath = null;
        activeVideoUserId = 0;
        lastVideoWidth = 0;
        lastVideoHeight = 0;
        lastRotation = 0;
    }

    public void drawVideoOverlay(Canvas canvas, int width, int height, float extraHeight, long userId) {
        if (width <= 0 || height <= 0 || userId == 0) return;
        String path = resolveBannerPath(userId);
        if (!isVideo(path)) return;

        float maxEh = Math.max(extraHeight, (float) AndroidUtilities.dp(200));
        float coll = Math.max(0f, Math.min(1f, 1f - extraHeight / maxEh));

        // When expanded, KEEP VIDEO CRYSTAL CLEAR! Only dim when scrolling
        int darkAlpha = (int) (coll * 150);
        if (darkAlpha > 0) {
            darkPaint.setColor(Color.argb(darkAlpha, 0, 0, 0));
            canvas.drawRect(0, 0, width, height, darkPaint);
        }

        // Soft bottom gradient
        int gh = Math.max(1, (int) (height * 0.30f));
        LinearGradient grad = new LinearGradient(0, height - gh, 0, height,
                Color.TRANSPARENT, Color.argb((int) (60 + coll * 100), 0, 0, 0), Shader.TileMode.CLAMP);
        gradPaint.setShader(grad);
        canvas.drawRect(0, height - gh, width, height, gradPaint);
    }
}
