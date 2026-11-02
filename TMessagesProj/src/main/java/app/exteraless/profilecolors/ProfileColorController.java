package app.exteraless.profilecolors;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextUtils;

import androidx.core.graphics.ColorUtils;
import androidx.palette.graphics.Palette;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;

import java.io.BufferedReader;
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

import app.exteraless.security.TsundereCrypto;

/**
 * Контроллер кастомных цветов оформления профиля (Profile Colors) для TsundereGram.
 * Позволяет настраивать фон карточек профиля и акцентный цвет под баннер,
 * синхронизируя палитру через облачный канал @TsundereGram_Banners.
 */
public final class ProfileColorController {

    private static volatile ProfileColorController INSTANCE;

    public static final String PRESET_DEFAULT = "default";
    public static final String PRESET_SAKURA = "sakura";
    public static final String PRESET_CYBERPUNK = "cyberpunk";
    public static final String PRESET_MIDNIGHT = "midnight";
    public static final String PRESET_SUNSET = "sunset";
    public static final String PRESET_EMERALD = "emerald";
    public static final String PRESET_SEPIA = "sepia";
    public static final String PRESET_PORCELAIN = "porcelain";
    public static final String PRESET_PASTEL_PINK = "pastel_pink";
    public static final String PRESET_BANNER = "banner";
    public static final String PRESET_CUSTOM = "custom";

    private static final String PREFS_NAME = "exteraless_profile_colors";
    private static final String KEY_CARD_BG = "local_card_bg";
    private static final String KEY_PAGE_BG = "local_page_bg";
    private static final String KEY_ACCENT = "local_accent";
    private static final String KEY_PRESET = "local_preset";
    private static final String KEY_REMOTE_COLORS = "remote_colors_cache";

    public static class ProfileColorMeta {
        public int cardBgColor = 0;   // 0 = по умолчанию из темы (плашки)
        public int pageBgColor = 0;   // 0 = вычисляется автоматически для контраста
        public int accentColor = 0;   // 0 = по умолчанию из темы
        public String preset = PRESET_DEFAULT;

        public ProfileColorMeta() {}

        public ProfileColorMeta(int cardBgColor, int accentColor, String preset) {
            this(cardBgColor, 0, accentColor, preset);
        }

        public ProfileColorMeta(int cardBgColor, int pageBgColor, int accentColor, String preset) {
            this.cardBgColor = cardBgColor;
            this.pageBgColor = pageBgColor;
            this.accentColor = accentColor;
            this.preset = preset != null ? preset : PRESET_DEFAULT;
        }

        public boolean hasCustomColors() {
            return cardBgColor != 0 || pageBgColor != 0 || accentColor != 0;
        }

        public boolean isLight() {
            int bg = cardBgColor != 0 ? cardBgColor : (pageBgColor != 0 ? pageBgColor : 0xFF202020);
            return androidx.core.graphics.ColorUtils.calculateContrast(android.graphics.Color.WHITE, bg) < 4.2;
        }

        public int getCardBgColor() {
            if (cardBgColor != 0) {
                return cardBgColor;
            }
            return isLight() ? 0xFFFFFFFF : 0xFF1E1F24;
        }

        public int getPageBgColor() {
            if (pageBgColor != 0) {
                return pageBgColor;
            }
            if (cardBgColor == 0) {
                return 0;
            }
            // Вычисляем комфортный для глаз фон страницы с идеальным контрастом:
            if (isLight()) {
                // Если плашки светлые, холст приятный мягкий нейтральный (не режет глаза)
                float[] hsl = new float[3];
                androidx.core.graphics.ColorUtils.colorToHSL(cardBgColor, hsl);
                hsl[1] = Math.min(hsl[1], 0.16f);
                hsl[2] = 0.94f;
                return androidx.core.graphics.ColorUtils.HSLToColor(hsl);
            } else {
                // Если плашки тёмные, холст глубже и темнее плашек на 40%, чтобы плашки парили
                return androidx.core.graphics.ColorUtils.blendARGB(cardBgColor, 0xFF000000, 0.40f);
            }
        }

        public int getCardStrokeColor() {
            return getCardStrokeColor(1.0f);
        }

        public int getCardStrokeColor(float alpha) {
            if (isLight()) {
                return org.telegram.ui.ActionBar.Theme.multAlpha(androidx.core.graphics.ColorUtils.blendARGB(getCardBgColor(), 0xFF000000, 0.12f), alpha);
            } else {
                return org.telegram.ui.ActionBar.Theme.multAlpha(androidx.core.graphics.ColorUtils.blendARGB(getCardBgColor(), 0xFFFFFFFF, 0.14f), alpha);
            }
        }

        public int getTextColor() {
            return isLight() ? 0xFF0F172A : 0xFFFFFFFF;
        }

        public int getSubtextColor() {
            return isLight() ? 0xFF64748B : 0xAAFFFFFF;
        }
    }

    private final SharedPreferences prefs;
    private final Map<String, ProfileColorMeta> remoteColors = new ConcurrentHashMap<>();

    private static final Paint sectionBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint sectionBackgroundStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Path sectionBackgroundPath = new Path();
    private static final float[] radii = new float[8];

    public static ProfileColorController getInstance() {
        if (INSTANCE == null) {
            synchronized (ProfileColorController.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ProfileColorController();
                }
            }
        }
        return INSTANCE;
    }

    private ProfileColorController() {
        Context context = ApplicationLoader.applicationContext;
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadCachedColors();
    }

    public long getMyId() {
        try {
            int acc = UserConfig.selectedAccount;
            if (acc < 0 || acc >= UserConfig.MAX_ACCOUNT_COUNT) {
                acc = 0;
            }
            return UserConfig.getInstance(acc).getClientUserId();
        } catch (Throwable t) {
            return 0;
        }
    }

    public ProfileColorMeta getLocalProfileColor() {
        try {
            int cardBg = prefs.getInt(KEY_CARD_BG, 0);
            int pageBg = prefs.getInt(KEY_PAGE_BG, 0);
            int accent = prefs.getInt(KEY_ACCENT, 0);
            String preset = prefs.getString(KEY_PRESET, PRESET_DEFAULT);
            return new ProfileColorMeta(cardBg, pageBg, accent, preset);
        } catch (Throwable t) {
            return new ProfileColorMeta(0, 0, 0, PRESET_DEFAULT);
        }
    }

    public void setLocalProfileColor(int cardBg, int accent, String preset) {
        setLocalProfileColor(cardBg, 0, accent, preset);
    }

    public void setLocalProfileColor(int cardBg, int pageBg, int accent, String preset) {
        try {
            prefs.edit()
                    .putInt(KEY_CARD_BG, cardBg)
                    .putInt(KEY_PAGE_BG, pageBg)
                    .putInt(KEY_ACCENT, accent)
                    .putString(KEY_PRESET, preset != null ? preset : PRESET_DEFAULT)
                    .apply();
            if (cardBg == 0 && pageBg == 0 && accent == 0) {
                long myId = getMyId();
                if (myId != 0) {
                    remoteColors.remove(TsundereCrypto.hashUserId(myId));
                    remoteColors.remove(String.valueOf(myId));
                }
            }
        } catch (Throwable ignored) {}
        refreshAllInterfaces();
    }

    public String getColorStatusDescription() {
        ProfileColorMeta meta = getLocalProfileColor();
        if (!meta.hasCustomColors() || PRESET_DEFAULT.equals(meta.preset)) {
            return "По умолчанию";
        }
        switch (meta.preset) {
            case PRESET_SAKURA:
                return "Сакура";
            case PRESET_CYBERPUNK:
                return "Киберпанк";
            case PRESET_MIDNIGHT:
                return "Полуночный Графит";
            case PRESET_SUNSET:
                return "Закат Цундере";
            case PRESET_EMERALD:
                return "Изумрудный Сад";
            case PRESET_SEPIA:
                return "Ретро-Газета";
            case PRESET_BANNER:
                return "Под баннер";
            case PRESET_CUSTOM:
            default:
                return "Свой цвет";
        }
    }

    public ProfileColorMeta getColorFor(long userId) {
        if (userId == 0) {
            return null;
        }
        long myId = getMyId();
        if (userId == myId) {
            ProfileColorMeta local = getLocalProfileColor();
            if (PRESET_DEFAULT.equals(local.preset)) {
                return null;
            }
            if (local.hasCustomColors()) {
                return local;
            }
            return null;
        }
        String userHash = TsundereCrypto.hashUserId(userId);
        ProfileColorMeta meta = remoteColors.get(userHash);
        if (meta != null) {
            return meta;
        }
        return remoteColors.get(String.valueOf(userId));
    }

    public void applyRemoteColors(JSONObject colorsObj) {
        if (colorsObj == null) {
            return;
        }
        try {
            Iterator<String> keys = colorsObj.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject item = colorsObj.optJSONObject(key);
                if (item != null) {
                    int cardBg = parseColor(item.optString("card_bg", ""));
                    int pageBg = parseColor(item.optString("page_bg", ""));
                    int accent = parseColor(item.optString("accent", ""));
                    String preset = item.optString("preset", PRESET_DEFAULT);
                    remoteColors.put(key, new ProfileColorMeta(cardBg, pageBg, accent, preset));
                }
            }
            prefs.edit().putString(KEY_REMOTE_COLORS, colorsObj.toString()).apply();
            refreshAllInterfaces();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void loadCachedColors() {
        try {
            String cached = prefs.getString(KEY_REMOTE_COLORS, null);
            if (!TextUtils.isEmpty(cached)) {
                JSONObject obj = new JSONObject(cached);
                Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONObject item = obj.optJSONObject(key);
                    if (item != null) {
                        int cardBg = parseColor(item.optString("card_bg", ""));
                        int pageBg = parseColor(item.optString("page_bg", ""));
                        int accent = parseColor(item.optString("accent", ""));
                        String preset = item.optString("preset", PRESET_DEFAULT);
                        remoteColors.put(key, new ProfileColorMeta(cardBg, pageBg, accent, preset));
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    private static int parseColor(String str) {
        if (TextUtils.isEmpty(str)) return 0;
        try {
            if (str.startsWith("#")) {
                return Color.parseColor(str);
            }
            return Integer.parseInt(str);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static String colorToHex(int color) {
        return String.format("#%08X", color);
    }

    public void setRemoteColor(long userId, int cardBg, int accent, String preset, Runnable onSuccess, Consumer<String> onError) {
        setRemoteColor(userId, cardBg, 0, accent, preset, onSuccess, onError);
    }

    public void setRemoteColor(long userId, int cardBg, int pageBg, int accent, String preset, Runnable onSuccess, Consumer<String> onError) {
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
                String currentText = pinned != null ? pinned.optString("text", "") : "";

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

                JSONObject colorsObj = configJson.optJSONObject("profile_colors");
                if (colorsObj == null) {
                    colorsObj = new JSONObject();
                    configJson.put("profile_colors", colorsObj);
                }

                String userKey = TsundereCrypto.hashUserId(userId);
                if (cardBg == 0 && pageBg == 0 && accent == 0) {
                    colorsObj.remove(userKey);
                    colorsObj.remove(String.valueOf(userId));
                    remoteColors.remove(userKey);
                    remoteColors.remove(String.valueOf(userId));
                } else {
                    JSONObject item = new JSONObject();
                    item.put("card_bg", colorToHex(cardBg));
                    if (pageBg != 0) {
                        item.put("page_bg", colorToHex(pageBg));
                    }
                    item.put("accent", colorToHex(accent));
                    item.put("preset", preset != null ? preset : PRESET_DEFAULT);
                    colorsObj.put(userKey, item);
                    remoteColors.put(userKey, new ProfileColorMeta(cardBg, pageBg, accent, preset));
                }

                // Шифруем
                String encryptedPayload = TsundereCrypto.encrypt(configJson.toString(2));
                String updatedText = com.exteragram.messenger.badges.BadgesController.CONFIG_HEADER + "\nENC:" + encryptedPayload;

                if (updatedText.equals(currentText)) {
                    AndroidUtilities.runOnUIThread(() -> {
                        refreshAllInterfaces();
                        if (onSuccess != null) onSuccess.run();
                    });
                    return;
                }

                // 2. Отправляем в Telegram
                URL editUrl = new URL("https://api.telegram.org/bot" + botToken + "/editMessageText");
                HttpURLConnection editConn = (HttpURLConnection) editUrl.openConnection();
                editConn.setRequestMethod("POST");
                editConn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                editConn.setDoOutput(true);
                editConn.setConnectTimeout(8000);
                editConn.setReadTimeout(8000);

                JSONObject editPayload = new JSONObject();
                editPayload.put("chat_id", channel);
                editPayload.put("message_id", messageId);
                editPayload.put("text", updatedText);

                try (OutputStream os = editConn.getOutputStream()) {
                    os.write(editPayload.toString().getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }

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

    public static void drawCustomSectionBackground(Canvas canvas, RectF rect, float topRadius, float bottomRadius, float alpha, ProfileColorMeta meta, Theme.ResourcesProvider resourcesProvider) {
        if (meta == null || !meta.hasCustomColors()) return;
        int cardBg = meta.getCardBgColor();
        int strokeColor = meta.getCardStrokeColor(alpha);

        if (SharedConfig.shadowsInSections) {
            sectionBackgroundStrokePaint.setShadowLayer(AndroidUtilities.dpf2(0.5f), 0, 0, Theme.multAlpha(0x0c000000, alpha));
            sectionBackgroundStrokePaint.setColor(strokeColor);
            sectionBackgroundPaint.setShadowLayer(AndroidUtilities.dpf2(3), 0, AndroidUtilities.dpf2(1f), Theme.multAlpha(0x12000000, alpha));
        } else {
            sectionBackgroundPaint.setShadowLayer(0, 0, 0, 0);
            sectionBackgroundStrokePaint.setColor(strokeColor);
        }
        sectionBackgroundStrokePaint.setStyle(Paint.Style.STROKE);
        sectionBackgroundStrokePaint.setStrokeWidth(AndroidUtilities.dp(1));
        sectionBackgroundPaint.setStyle(Paint.Style.FILL);
        sectionBackgroundPaint.setColor(Theme.multAlpha(cardBg, alpha));

        if (topRadius == bottomRadius) {
            canvas.drawRoundRect(rect, topRadius, topRadius, sectionBackgroundPaint);
            canvas.drawRoundRect(rect, topRadius, topRadius, sectionBackgroundStrokePaint);
            if (app.exteraless.appearance.LiquidGlassRenderer.isEnabled()) {
                app.exteraless.appearance.LiquidGlassRenderer.drawCardGlass(canvas, rect, topRadius, topRadius);
            }
        } else {
            sectionBackgroundPath.rewind();
            radii[0] = radii[1] = radii[2] = radii[3] = topRadius;
            radii[4] = radii[5] = radii[6] = radii[7] = bottomRadius;
            sectionBackgroundPath.addRoundRect(rect, radii, Path.Direction.CW);
            canvas.drawPath(sectionBackgroundPath, sectionBackgroundPaint);
            canvas.drawPath(sectionBackgroundPath, sectionBackgroundStrokePaint);
            if (app.exteraless.appearance.LiquidGlassRenderer.isEnabled()) {
                app.exteraless.appearance.LiquidGlassRenderer.drawPathGlass(canvas, sectionBackgroundPath, rect);
            }
        }
    }

    public static void drawCustomSectionBackground(Canvas canvas, RectF rect, float topRadius, float bottomRadius, float alpha, int cardBgColor, Theme.ResourcesProvider resourcesProvider) {
        drawCustomSectionBackground(canvas, rect, topRadius, bottomRadius, alpha, new ProfileColorMeta(cardBgColor, 0, PRESET_CUSTOM), resourcesProvider);
    }

    /**
     * Автоматически извлекает глубокий тёмный фон карточек и яркий акцент из изображения баннера.
     */
    public static ProfileColorMeta extractFromBanner(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return new ProfileColorMeta(0, 0, PRESET_DEFAULT);
        }
        try {
            Palette palette = Palette.from(bitmap).generate();
            int dominant = palette.getDominantColor(0xFF2A2A2A);
            int vibrant = palette.getVibrantColor(0);
            if (vibrant == 0) vibrant = palette.getLightVibrantColor(0);
            if (vibrant == 0) vibrant = palette.getMutedColor(0);
            if (vibrant == 0) vibrant = 0xFFFF7597;

            // Формируем глубокий элегантный тёмный оттенок для плашек
            float[] hsl = new float[3];
            ColorUtils.colorToHSL(dominant, hsl);
            hsl[1] = Math.min(hsl[1], 0.32f); // умеренная благородная насыщенность
            hsl[2] = 0.16f;                   // 16% яркости для плашек
            int cardBg = ColorUtils.HSLToColor(hsl);

            // И ещё более глубокий холст (страница)
            hsl[2] = 0.09f;
            int pageBg = ColorUtils.HSLToColor(hsl);

            // Сочный акцент
            float[] accentHsl = new float[3];
            ColorUtils.colorToHSL(vibrant, accentHsl);
            accentHsl[1] = Math.max(accentHsl[1], 0.65f);
            accentHsl[2] = Math.max(0.62f, Math.min(0.78f, accentHsl[2]));
            int accent = ColorUtils.HSLToColor(accentHsl);

            return new ProfileColorMeta(cardBg, pageBg, accent, PRESET_BANNER);
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return new ProfileColorMeta(0, 0, PRESET_DEFAULT);
    }

    public static ProfileColorMeta getPresetMeta(String preset) {
        switch (preset) {
            case PRESET_SAKURA:
                return new ProfileColorMeta(0xFF281B24, 0xFF170F15, 0xFFFF7597, PRESET_SAKURA);
            case PRESET_CYBERPUNK:
                return new ProfileColorMeta(0xFF1B192E, 0xFF0F0E1A, 0xFFA076F9, PRESET_CYBERPUNK);
            case PRESET_MIDNIGHT:
                return new ProfileColorMeta(0xFF18191D, 0xFF000000, 0xFF4DA8DA, PRESET_MIDNIGHT);
            case PRESET_SUNSET:
                return new ProfileColorMeta(0xFF261619, 0xFF160C0E, 0xFFFF7A45, PRESET_SUNSET);
            case PRESET_EMERALD:
                return new ProfileColorMeta(0xFF14241D, 0xFF0A1410, 0xFF2ED573, PRESET_EMERALD);
            case PRESET_SEPIA:
                return new ProfileColorMeta(0xFF27211C, 0xFF181411, 0xFFE5C07B, PRESET_SEPIA);
            case PRESET_PORCELAIN:
                return new ProfileColorMeta(0xFFFFFFFF, 0xFFEEF1F4, 0xFF2563EB, PRESET_PORCELAIN);
            case PRESET_PASTEL_PINK:
                return new ProfileColorMeta(0xFFFFFFFF, 0xFFFBF2F6, 0xFFE0537A, PRESET_PASTEL_PINK);
            case PRESET_DEFAULT:
            default:
                return new ProfileColorMeta(0, 0, PRESET_DEFAULT);
        }
    }

    public void refreshAllInterfaces() {
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false, true, true);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, org.telegram.messenger.MessagesController.UPDATE_MASK_ALL);
        });
    }
}
