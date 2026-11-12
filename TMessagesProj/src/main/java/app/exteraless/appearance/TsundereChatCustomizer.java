package app.exteraless.appearance;

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

import app.exteraless.security.TsundereCrypto;

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
    private static final String KEY_APPLY_TO_ALL = "nickname_apply_to_all";
    private static final String KEY_MESSAGE_ANIMATION = "message_animation_type";
    private static final String KEY_LIQUID_GLASS = "tsundere_liquid_glass";

    public static final int ANIMATION_DEFAULT = 0; // Снизу (по умолчанию)
    public static final int ANIMATION_FADE = 1;    // Плавное появление (Fade)
    public static final int ANIMATION_SLIDE_RIGHT = 2; // Вылет справа
    public static final int ANIMATION_SLIDE_TOP = 3;   // Сверху вниз
    public static final int ANIMATION_ZOOM = 4;        // Увеличение (Zoom)

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
        nicknameGradientEnabled = preferences.getBoolean(KEY_GRADIENT_ENABLED, true);
        nicknameColor1 = preferences.getInt(KEY_COLOR_1, DEFAULT_COLOR_1);
        nicknameColor2 = preferences.getInt(KEY_COLOR_2, DEFAULT_COLOR_2);
        applyGradientToAll = preferences.getBoolean(KEY_APPLY_TO_ALL, false);
        messageAnimationType = preferences.getInt(KEY_MESSAGE_ANIMATION, ANIMATION_DEFAULT);
        liquidGlassEnabled = preferences.getBoolean(KEY_LIQUID_GLASS, false);
    }

    public boolean isLiquidGlassEnabled() {
        return false;
    }

    public void setLiquidGlassEnabled(boolean enabled) {
        liquidGlassEnabled = false;
    }

    public int getMessageAnimationType() {
        return messageAnimationType;
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
        return nicknameGradientEnabled;
    }

    public void setNicknameGradientEnabled(boolean enabled) {
        if (nicknameGradientEnabled != enabled) {
            nicknameGradientEnabled = enabled;
            preferences.edit().putBoolean(KEY_GRADIENT_ENABLED, enabled).apply();
            notifyChanges();
            if (enabled) {
                syncGradientToCloud(nicknameColor1, nicknameColor2);
            }
        }
    }

    public int getNicknameColor1() {
        return nicknameColor1;
    }

    public int getNicknameColor2() {
        return nicknameColor2;
    }

    public void setNicknameColors(int color1, int color2) {
        if (nicknameColor1 != color1 || nicknameColor2 != color2) {
            nicknameColor1 = color1;
            nicknameColor2 = color2;
            preferences.edit()
                    .putInt(KEY_COLOR_1, color1)
                    .putInt(KEY_COLOR_2, color2)
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
                    if (c1 != 0 && c2 != 0) {
                        remoteGradientsByHash.put(k, new int[]{c1, c2});
                        try {
                            long uid = Long.parseLong(k);
                            remoteGradientsById.put(uid, new int[]{c1, c2});
                        } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public int[] getGradientColorsForUser(long userId) {
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
        float w = Math.max(AndroidUtilities.dp(40), width);
        return new LinearGradient(0, 0, w, 0, colors[0], colors[1], Shader.TileMode.CLAMP);
    }

    public Shader createNicknameShader(float width) {
        float w = Math.max(AndroidUtilities.dp(40), width);
        return new LinearGradient(0, 0, w, 0, nicknameColor1, nicknameColor2, Shader.TileMode.CLAMP);
    }

    private void syncGradientToCloud(int c1, int c2) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                long self = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser() != null
                        ? UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser().id : 0;
                if (self != 0) {
                    com.exteragram.messenger.badges.BadgesController.INSTANCE.setRemoteGradient(self, c1, c2);
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
