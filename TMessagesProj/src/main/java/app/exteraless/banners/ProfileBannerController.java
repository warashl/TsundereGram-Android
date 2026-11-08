package app.exteraless.banners;

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

import app.exteraless.security.TsundereCrypto;

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
    private static final String PREFS_NAME = "exteraless_banners";
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
        String path = prefs.getString(KEY_LOCAL_BANNER, null);
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
            // Delete old local banners
            File[] old = storageDir.listFiles();
            if (old != null) {
                for (File f : old) {
                    if (f.getName().startsWith("local_banner")) {
                        f.delete();
                    }
                }
            }
            File dest = new File(storageDir, "local_banner" + ext);
            copyFile(source, dest);
            prefs.edit().putString(KEY_LOCAL_BANNER, dest.getAbsolutePath()).apply();
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
        prefs.edit().remove(KEY_LOCAL_BANNER).apply();
        bitmapCache.evictAll();
        blurCache.evictAll();
        detachVideo();
    }

    public boolean isUseAvatarAsBanner() {
        return prefs.getBoolean(KEY_USE_AVATAR, false);
    }

    public void setUseAvatarAsBanner(boolean use) {
        prefs.edit().putBoolean(KEY_USE_AVATAR, use).apply();
        bitmapCache.evictAll();
        blurCache.evictAll();
    }

    public boolean isHideAvatarSelf() {
        return prefs.getBoolean(KEY_HIDE_AVATAR, false);
    }

    public void setHideAvatarSelf(boolean hide) {
        prefs.edit().putBoolean(KEY_HIDE_AVATAR, hide).apply();
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

                String getUrlStr = "https://api.telegram.org/bot" + com.exteragram.messenger.badges.BadgesController.BOT_TOKEN + "/getChat?chat_id=" + com.exteragram.messenger.badges.BadgesController.CHANNEL_USERNAME;
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
                    String currentText = app.exteraless.security.CloudConfigStore.readText(pinned);

                    String payload = currentText;
                    int headerIdx = currentText.indexOf(com.exteragram.messenger.badges.BadgesController.CONFIG_HEADER);
                    if (headerIdx >= 0) {
                        payload = currentText.substring(headerIdx + com.exteragram.messenger.badges.BadgesController.CONFIG_HEADER.length()).trim();
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
                                String updatedConfigStr = com.exteragram.messenger.badges.BadgesController.CONFIG_HEADER + "\nENC:" + encPayload;

                                // Текст до 4000 символов — editMessageText, больше — документ в закрепе (см. CloudConfigStore)
                                HttpURLConnection editConn = app.exteraless.security.CloudConfigStore.sendConfig(com.exteragram.messenger.badges.BadgesController.CHANNEL_USERNAME, messageId, pinned, updatedConfigStr);
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
        bannerMetaMap.remove(myId);
        String cached = cachedBannerPaths.remove(myId);
        if (cached != null) {
            new File(cached).delete();
        }
        bitmapCache.evictAll();
        blurCache.evictAll();
        detachVideo();

        Utilities.globalQueue.postRunnable(() -> {
            try {
                String getUrlStr = "https://api.telegram.org/bot" + com.exteragram.messenger.badges.BadgesController.BOT_TOKEN + "/getChat?chat_id=" + com.exteragram.messenger.badges.BadgesController.CHANNEL_USERNAME;
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
                    String currentText = app.exteraless.security.CloudConfigStore.readText(pinned);

                    int startBrace = currentText.indexOf('{');
                    int endBrace = currentText.lastIndexOf('}');
                    if (startBrace >= 0 && endBrace > startBrace) {
                        JSONObject configJson = new JSONObject(currentText.substring(startBrace, endBrace + 1));
                        JSONObject bannersJson = configJson.optJSONObject("banners");
                        if (bannersJson != null) {
                            bannersJson.remove(String.valueOf(myId));
                            String updatedConfigStr = com.exteragram.messenger.badges.BadgesController.CONFIG_HEADER + "\nENC:" + TsundereCrypto.encrypt(configJson.toString());

                            // Текст до 4000 символов — editMessageText, больше — документ в закрепе (см. CloudConfigStore)
                            HttpURLConnection editConn = app.exteraless.security.CloudConfigStore.sendConfig(com.exteragram.messenger.badges.BadgesController.CHANNEL_USERNAME, messageId, pinned, updatedConfigStr);
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
        try {
            Iterator<String> keys = bannersJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
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
        }
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
        File diskThumb = new File(path + ".thumb.jpg");
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
            URL url = new URL("https://api.telegram.org/bot" + com.exteragram.messenger.badges.BadgesController.BOT_TOKEN + "/getFile?file_id=" + java.net.URLEncoder.encode(fileId, "UTF-8"));
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
                    return "https://api.telegram.org/file/bot" + com.exteragram.messenger.badges.BadgesController.BOT_TOKEN + "/" + filePath;
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
                        com.exteragram.messenger.badges.BadgesController.INSTANCE.loadRemoteBadges();
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
                com.exteragram.messenger.badges.BadgesController.INSTANCE.loadRemoteBadges();
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
                String catboxUrl = com.exteragram.messenger.badges.BadgesController.uploadMedia(file);
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
                String getUrlStr = "https://api.telegram.org/bot" + com.exteragram.messenger.badges.BadgesController.BOT_TOKEN + "/getChat?chat_id=" + com.exteragram.messenger.badges.BadgesController.CHANNEL_USERNAME;
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
                String currentText = app.exteraless.security.CloudConfigStore.readText(pinned);

                String payload = currentText;
                int headerIdx = currentText.indexOf(com.exteragram.messenger.badges.BadgesController.CONFIG_HEADER);
                if (headerIdx >= 0) {
                    payload = currentText.substring(headerIdx + com.exteragram.messenger.badges.BadgesController.CONFIG_HEADER.length()).trim();
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
                String updatedConfigStr = com.exteragram.messenger.badges.BadgesController.CONFIG_HEADER + "\nENC:" + encPayload;

                // 3. Save to pinned message
                // Текст до 4000 символов — editMessageText, больше — документ в закрепе (см. CloudConfigStore)
                HttpURLConnection editConn = app.exteraless.security.CloudConfigStore.sendConfig(com.exteragram.messenger.badges.BadgesController.CHANNEL_USERNAME, messageId, pinned, updatedConfigStr);

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

                    setServerStatus("approved");
                    bitmapCache.evictAll();
                    blurCache.evictAll();
                    detachVideo();

                    if (onSuccess != null) {
                        AndroidUtilities.runOnUIThread(onSuccess);
                    }
                } else {
                    if (onError != null) {
                        AndroidUtilities.runOnUIThread(() -> onError.accept("Ошибка сохранения в Telegram: HTTP " + editCode));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
                if (onError != null) {
                    AndroidUtilities.runOnUIThread(() -> onError.accept(e.getMessage()));
                }
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
                String getChatUrl = "https://api.telegram.org/bot" + com.exteragram.messenger.badges.BadgesController.BOT_TOKEN +
                        "/getChat?chat_id=" + com.exteragram.messenger.badges.BadgesController.CHANNEL_USERNAME;
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
                String currentText = app.exteraless.security.CloudConfigStore.readText(pinned);

                String payload = currentText;
                int headerIdx = currentText.indexOf(com.exteragram.messenger.badges.BadgesController.CONFIG_HEADER);
                if (headerIdx >= 0) {
                    payload = currentText.substring(headerIdx + com.exteragram.messenger.badges.BadgesController.CONFIG_HEADER.length()).trim();
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
                String updatedConfigStr = com.exteragram.messenger.badges.BadgesController.CONFIG_HEADER + "\nENC:" + encPayload;

                // Текст до 4000 символов — editMessageText, больше — документ в закрепе (см. CloudConfigStore)
                HttpURLConnection editConn = app.exteraless.security.CloudConfigStore.sendConfig(com.exteragram.messenger.badges.BadgesController.CHANNEL_USERNAME, messageId, pinned, updatedConfigStr);

                int editCode = editConn.getResponseCode();
                if (editCode == 200) {
                    cachedBannerPaths.remove(myId);
                    bannerMetaMap.remove(myId);
                    setServerStatus("none");
                    bitmapCache.evictAll();
                    blurCache.evictAll();
                    detachVideo();
                    if (onSuccess != null) {
                        AndroidUtilities.runOnUIThread(onSuccess);
                    }
                } else {
                    if (onError != null) {
                        AndroidUtilities.runOnUIThread(() -> onError.accept("Ошибка: HTTP " + editCode));
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
                if (onError != null) {
                    AndroidUtilities.runOnUIThread(() -> onError.accept(e.getMessage()));
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
            if (!isFirstFrameRendered || (activeTextureView != null && activeTextureView.getAlpha() < 0.98f)) {
                Bitmap thumb = getVideoThumbnail(path);
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
                float scale = Math.max((float) width / bw, (float) height / bh) * 1.02f;
                float dw = bw * scale;
                float dh = bh * scale;
                float dx = (width - dw) * 0.5f;
                float dy = (height - dh) * 0.5f;

                int save = canvas.save();
                canvas.clipRect(0, 0, width, height);
                activeGifDrawable.setBounds((int) dx, (int) dy, (int) (dx + dw), (int) (dy + dh));
                activeGifDrawable.draw(canvas);

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

    private void drawBitmapInternal(Canvas canvas, Bitmap bmp, int width, int height, float extraHeight, String cacheKey) {
        if (bmp == null || bmp.isRecycled()) return;

        int bw = bmp.getWidth();
        int bh = bmp.getHeight();
        if (bw <= 0 || bh <= 0) return;

        float maxEh = Math.max(extraHeight, (float) AndroidUtilities.dp(200));
        float coll = Math.max(0f, Math.min(1f, 1f - extraHeight / maxEh));

        float scale = Math.max((float) width / bw, (float) height / bh) * 1.02f;
        float dx = (width - bw * scale) * 0.5f;
        float dy = (height - bh * scale) * 0.5f;

        drawMatrix.reset();
        drawMatrix.postScale(scale, scale);
        drawMatrix.postTranslate(dx, dy);

        int save = canvas.save();
        canvas.clipRect(0, 0, width, height);

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

        float scale = Math.max((float) width / blurBmp.getWidth(), (float) height / blurBmp.getHeight()) * 1.05f;
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

        float scaleX = 1f;
        float scaleY = 1f;
        if (vw * viewHeight > viewWidth * vh) {
            scaleX = ((float) vw * viewHeight) / ((float) viewWidth * vh);
            scaleY = 1.0f;
        } else {
            scaleX = 1.0f;
            scaleY = ((float) viewWidth * vh) / ((float) vw * viewHeight);
        }

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
            topView.addView(activeTextureView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
            topView.invalidate();
            activeTextureView.measure(
                    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            );
            activeTextureView.layout(0, 0, width, height);

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

    public void updateVideoHeight(int height) {
        if (activeTextureView != null) {
            ViewGroup.LayoutParams lp = activeTextureView.getLayoutParams();
            if (lp != null && lp.height != height) {
                lp.height = height;
                activeTextureView.setLayoutParams(lp);
                updateTextureTransform();
            }
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
