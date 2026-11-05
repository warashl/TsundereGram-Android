package app.tsunderegram.appearance;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.text.TextPaint;

import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import app.tsunderegram.security.TsundereCrypto;

/**
 * Контроллер настроек кастомизации чатов и профилей:
 * - Градиент никнейма (2 цвета) в сообщениях и профиле (для себя и синхронизация для клиентов)
 * - Прозрачность пузырей сообщений (bubble transparency / glassmorphism / без фона)
 */
public class TsundereChatCustomizer {

    private static volatile TsundereChatCustomizer instance;

    private static final String PREFS_NAME = "tsundere_chat_customizer";
    private static final String KEY_BUBBLE_ALPHA = "bubble_alpha_percent";
    private static final String KEY_GRADIENT_ENABLED = "nickname_gradient_enabled";
    private static final String KEY_COLOR_1 = "nickname_color_1";
    private static final String KEY_COLOR_2 = "nickname_color_2";
    private static final String KEY_GRADIENT_ANIMATED = "nickname_gradient_animated";
    /** Период «переливания» градиента, мс. */
    public static final long SHIMMER_PERIOD = 3200;
    private static final String KEY_APPLY_TO_ALL = "nickname_apply_to_all";
    private static final String KEY_MESSAGE_ANIMATION = "message_animation_type";
    private static final String KEY_LIQUID_GLASS = "tsundere_liquid_glass";

    public static final int ANIMATION_DEFAULT = 0;     // Стандартная: текст «перелетает» из поля ввода
    public static final int ANIMATION_FADE = 1;        // Плавное появление
    public static final int ANIMATION_SLIDE_RIGHT = 2; // Вылет справа
    public static final int ANIMATION_SLIDE_TOP = 3;   // Падение сверху
    public static final int ANIMATION_ZOOM = 4;        // Увеличение
    public static final int ANIMATION_SPRING = 5;      // Пружина
    public static final int ANIMATION_JELLY = 6;       // Желе
    public static final int ANIMATION_FLIP = 7;        // Переворот
    public static final int ANIMATION_SWIRL = 8;       // Закручивание

    public static final String[] ANIMATION_NAMES = {
            "Стандартная",
            "Плавное появление",
            "Вылет справа",
            "Падение сверху",
            "Увеличение",
            "Пружина",
            "Желе",
            "Переворот",
            "Закручивание"
    };

    public static String getAnimationName(int type) {
        return type >= 0 && type < ANIMATION_NAMES.length ? ANIMATION_NAMES[type] : ANIMATION_NAMES[0];
    }

    // Пресеты по умолчанию
    public static final int DEFAULT_COLOR_1 = 0xFFFF7597; // Sakura Pink
    public static final int DEFAULT_COLOR_2 = 0xFF7266ED; // Lavender Purple

    public static class Preset {
        public final String name;
        public final int color1;
        public final int color2;

        public Preset(String name, int color1, int color2) {
            this.name = name;
            this.color1 = color1;
            this.color2 = color2;
        }
    }

    public static final Preset[] PRESETS = new Preset[]{
            new Preset("Сакура", 0xFFFF7597, 0xFF7266ED),
            new Preset("Киберпанк", 0xFFA076F9, 0xFF00D2D3),
            new Preset("Закат", 0xFFFF4B2B, 0xFFFF416C),
            new Preset("Изумруд", 0xFF00B09B, 0xFF96C93D),
            new Preset("Золото", 0xFFFFB300, 0xFFFF5722),
            new Preset("Океан", 0xFF2193B0, 0xFF6DD5ED)
    };

    private final SharedPreferences preferences;
    private int bubbleAlphaPercent;
    private boolean nicknameGradientEnabled;
    private int nicknameColor1;
    private int nicknameColor2;
    private boolean applyGradientToAll;
    private int messageAnimationType;
    private boolean liquidGlassEnabled;

    private final ConcurrentHashMap<String, int[]> remoteGradientsByHash = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, int[]> remoteGradientsById = new ConcurrentHashMap<>();

    public static TsundereChatCustomizer getInstance() {
        TsundereChatCustomizer local = instance;
        if (local == null) {
            synchronized (TsundereChatCustomizer.class) {
                local = instance;
                if (local == null) {
                    instance = local = new TsundereChatCustomizer();
                }
            }
        }
        return local;
    }

    private TsundereChatCustomizer() {
        Context context = ApplicationLoader.applicationContext;
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        bubbleAlphaPercent = preferences.getInt(KEY_BUBBLE_ALPHA, 100);
        ensureAccount();
        applyGradientToAll = preferences.getBoolean(KEY_APPLY_TO_ALL, false);
        messageAnimationType = preferences.getInt(KEY_MESSAGE_ANIMATION, ANIMATION_DEFAULT);
        liquidGlassEnabled = preferences.getBoolean(KEY_LIQUID_GLASS, false);
    }

    /** id аккаунта, для которого загружен градиент ника (у каждого аккаунта на устройстве свой). */
    private long loadedUid = Long.MIN_VALUE;

    private String scoped(String key) {
        return app.tsunderegram.security.AccountScope.key(preferences, key);
    }

    private void ensureAccount() {
        long uid = app.tsunderegram.security.AccountScope.currentUserId();
        if (uid == loadedUid) {
            return;
        }
        synchronized (this) {
            if (uid == loadedUid) {
                return;
            }
            nicknameGradientEnabled = preferences.getBoolean(scoped(KEY_GRADIENT_ENABLED), true);
            nicknameColor1 = preferences.getInt(scoped(KEY_COLOR_1), DEFAULT_COLOR_1);
            nicknameColor2 = preferences.getInt(scoped(KEY_COLOR_2), DEFAULT_COLOR_2);
            nicknameGradientAnimated = preferences.getBoolean(scoped(KEY_GRADIENT_ANIMATED), false);
            loadedUid = uid;
        }
    }

    public boolean isLiquidGlassEnabled() {
        return false;
    }

    public void setLiquidGlassEnabled(boolean enabled) {
        liquidGlassEnabled = false;
    }

    public int getMessageAnimationType() {
        return messageAnimationType >= 0 && messageAnimationType < ANIMATION_NAMES.length ? messageAnimationType : ANIMATION_DEFAULT;
    }

    public void setMessageAnimationType(int type) {
        if (messageAnimationType != type) {
            messageAnimationType = type;
            preferences.edit().putInt(KEY_MESSAGE_ANIMATION, type).apply();
            notifyChanges();
        }
    }

    public float getBubbleAlpha() {
        return Math.max(0f, Math.min(1f, bubbleAlphaPercent / 100.0f));
    }

    public int getBubbleAlphaPercent() {
        return bubbleAlphaPercent;
    }

    public void setBubbleAlphaPercent(int percent) {
        percent = Math.max(0, Math.min(100, percent));
        if (bubbleAlphaPercent != percent) {
            bubbleAlphaPercent = percent;
            preferences.edit().putInt(KEY_BUBBLE_ALPHA, percent).apply();
            try {
                xyz.nextalone.nagram.NaConfig.INSTANCE.getTranslucentDeletedMessages().setConfigBool(percent < 100);
            } catch (Throwable ignored) {
            }
            notifyChanges();
        }
    }

    public boolean isTransparentBubblesEnabled() {
        return bubbleAlphaPercent < 100;
    }

    public void setTransparentBubblesEnabled(boolean enabled) {
        setBubbleAlphaPercent(enabled ? 40 : 100);
    }

    public void setBubbleAlpha(float alpha) {
        setBubbleAlphaPercent((int) (alpha * 100));
    }

    public boolean isNicknameGradientEnabled() {
        ensureAccount();
        return nicknameGradientEnabled;
    }

    public void setNicknameGradientEnabled(boolean enabled) {
        ensureAccount();
        if (nicknameGradientEnabled != enabled) {
            nicknameGradientEnabled = enabled;
            preferences.edit().putBoolean(scoped(KEY_GRADIENT_ENABLED), enabled).apply();
            notifyChanges();
            if (enabled) {
                syncGradientToCloud(nicknameColor1, nicknameColor2);
            }
        }
    }

    private volatile boolean nicknameGradientAnimated;
    private final java.util.Set<Long> remoteAnimatedById = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());
    private final java.util.Set<String> remoteAnimatedByHash = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /** «Переливающийся» градиент ника: цвета плавно бегут по имени. */
    public boolean isNicknameGradientAnimated() {
        ensureAccount();
        return nicknameGradientAnimated;
    }

    public void setNicknameGradientAnimated(boolean animated) {
        ensureAccount();
        if (nicknameGradientAnimated != animated) {
            nicknameGradientAnimated = animated;
            preferences.edit().putBoolean(scoped(KEY_GRADIENT_ANIMATED), animated).apply();
            notifyChanges();
            if (nicknameGradientEnabled) {
                syncGradientToCloud(nicknameColor1, nicknameColor2);
            }
        }
    }

    /** Переливается ли градиент ника у этого пользователя (свой — из настроек, чужой — из облака). */
    public boolean isGradientAnimatedForUser(long userId) {
        ensureAccount();
        if (userId <= 0) {
            return false;
        }
        long self = 0;
        try {
            if (UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser() != null) {
                self = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser().id;
            }
        } catch (Throwable ignored) {}
        if (self != 0 && userId == self) {
            return nicknameGradientEnabled && nicknameGradientAnimated;
        }
        if (remoteGradientsById.get(userId) != null || remoteGradientsByHash.get(TsundereCrypto.hashUserId(userId)) != null) {
            return remoteAnimatedById.contains(userId) || remoteAnimatedByHash.contains(TsundereCrypto.hashUserId(userId));
        }
        return applyGradientToAll && nicknameGradientEnabled && nicknameGradientAnimated;
    }

    /**
     * Шейдер переливающегося градиента: c1 → c2 → c1 на отрезке 2·width, повторяется и
     * сдвигается со временем (см. {@link #updateShimmer}).
     */
    public static Shader createShimmerShader(int c1, int c2, float width) {
        float w = Math.max(AndroidUtilities.dp(40), width);
        LinearGradient g = new LinearGradient(0, 0, w * 2f, 0, new int[]{c1, c2, c1}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.REPEAT);
        updateShimmer(g, w);
        return g;
    }

    private static final android.graphics.Matrix shimmerMatrix = new android.graphics.Matrix();

    /** Сдвигает переливающийся шейдер по текущему времени. Вызывать с UI-потока перед отрисовкой. */
    public static void updateShimmer(Shader shader, float width) {
        updateShimmer(shader, width, 0f);
    }

    /** То же, но текст начинается не с нуля (например, имя в списке чатов). */
    public static void updateShimmer(Shader shader, float width, float offsetX) {
        if (shader == null) {
            return;
        }
        float w = Math.max(AndroidUtilities.dp(40), width);
        float phase = (android.os.SystemClock.uptimeMillis() % SHIMMER_PERIOD) / (float) SHIMMER_PERIOD;
        shimmerMatrix.setTranslate(offsetX + phase * w * 2f, 0);
        shader.setLocalMatrix(shimmerMatrix);
    }

    public int getNicknameColor1() {
        ensureAccount();
        return nicknameColor1;
    }

    public int getNicknameColor2() {
        ensureAccount();
        return nicknameColor2;
    }

    public void setNicknameColors(int color1, int color2) {
        ensureAccount();
        if (nicknameColor1 != color1 || nicknameColor2 != color2) {
            nicknameColor1 = color1;
            nicknameColor2 = color2;
            preferences.edit()
                    .putInt(scoped(KEY_COLOR_1), color1)
                    .putInt(scoped(KEY_COLOR_2), color2)
                    .apply();
            notifyChanges();
            syncGradientToCloud(color1, color2);
        }
    }

    public boolean isApplyGradientToAll() {
        return applyGradientToAll;
    }

    public void setApplyGradientToAll(boolean applyToAll) {
        if (applyGradientToAll != applyToAll) {
            applyGradientToAll = applyToAll;
            preferences.edit().putBoolean(KEY_APPLY_TO_ALL, applyToAll).apply();
            notifyChanges();
        }
    }

    public void applyRemoteGradients(JSONObject obj) {
        if (obj == null) return;
        try {
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                JSONObject item = obj.optJSONObject(k);
                if (item != null) {
                    int c1 = (int) item.optLong("color1", 0);
                    int c2 = (int) item.optLong("color2", 0);
                    boolean anim = item.optInt("anim", 0) == 1;
                    if (c1 != 0 && c2 != 0) {
                        remoteGradientsByHash.put(k, new int[]{c1, c2});
                        if (anim) remoteAnimatedByHash.add(k); else remoteAnimatedByHash.remove(k);
                        try {
                            long uid = Long.parseLong(k);
                            remoteGradientsById.put(uid, new int[]{c1, c2});
                            if (anim) remoteAnimatedById.add(uid); else remoteAnimatedById.remove(uid);
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public int[] getGradientColorsForUser(long userId) {
        ensureAccount();
        if (userId <= 0) {
            return null; // Groups and channels are not users, do not apply user gradient!
        }
        long self = 0;
        try {
            if (UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser() != null) {
                self = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser().id;
            }
        } catch (Throwable ignored) {}

        if (self != 0 && (userId == self || userId == 0)) {
            if (nicknameGradientEnabled) {
                return new int[]{nicknameColor1, nicknameColor2};
            }
            return null;
        }

        if (userId != 0) {
            int[] remote = remoteGradientsById.get(userId);
            if (remote == null) {
                String hash = TsundereCrypto.hashUserId(userId);
                remote = remoteGradientsByHash.get(hash);
            }
            if (remote != null) {
                return remote;
            }
        }

        if (applyGradientToAll && nicknameGradientEnabled) {
            return new int[]{nicknameColor1, nicknameColor2};
        }
        return null;
    }

    public Shader getShaderForUser(long userId, float width) {
        int[] colors = getGradientColorsForUser(userId);
        if (colors == null) return null;
        if (isGradientAnimatedForUser(userId)) {
            return createShimmerShader(colors[0], colors[1], width);
        }
        float w = Math.max(AndroidUtilities.dp(40), width);
        return new LinearGradient(0, 0, w, 0, colors[0], colors[1], Shader.TileMode.CLAMP);
    }

    public Shader createNicknameShader(float width) {
        ensureAccount();
        float w = Math.max(AndroidUtilities.dp(40), width);
        return new LinearGradient(0, 0, w, 0, nicknameColor1, nicknameColor2, Shader.TileMode.CLAMP);
    }

    private void syncGradientToCloud(int c1, int c2) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                long self = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser() != null
                        ? UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser().id : 0;
                if (self != 0) {
                    com.tsunderegram.messenger.badges.BadgesController.INSTANCE.setRemoteGradient(self, c1, c2);
                }
            } catch (Throwable ignored) {}
        });
    }

    private void notifyChanges() {
        AndroidUtilities.runOnUIThread(() -> {
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false, true, true);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
        });
    }
}
