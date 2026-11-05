package org.telegram.messenger;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.unifiedpush.android.connector.UnifiedPush;
import org.unifiedpush.android.embedded_fcm_distributor.EmbeddedDistributorReceiver;
import org.unifiedpush.android.embedded_fcm_distributor.Gateway;

/**
 * TsundereGram: встроенный дистрибьютор UnifiedPush «Google FCM» без Firebase-проекта
 * (по образцу Mercurygram, GPL-2.0). Сервисы Google выдают обычный WebPush-адрес, подписанный
 * нашим ключом VAPID; Telegram шлёт пуш на наш шлюз (tsundere-push), шлюз подписывает его и
 * передаёт в FCM. Содержимое зашифровано ключами устройства — ни шлюз, ни Google его не читают,
 * и Firebase-проект tsunderegram в этой цепочке не участвует.
 *
 * Адрес шлюза и публичный ключ — одна пара (закрытая половина лежит только в Cloudflare),
 * поэтому они зашиты здесь и не зависят от настройки «Шлюз UnifiedPush».
 */
public class TsundereFcmDistributor extends EmbeddedDistributorReceiver {

    public static final String GATEWAY = "https://tsundere-push.tsunderegram.workers.dev/";
    public static final String VAPID_PUBLIC_KEY = "BNcXKdTEqzA4-aoMu6JgvgCIPRcyVVW30lr8rb76pobkfxVAxX45riwlmui80UyTafMBBBsxSjW58vpFXW8QkMA";

    private static final String FIREBASE_RECEIVER =
            "org.unifiedpush.android.embedded_fcm_distributor.impl.FirebaseReceiver";

    private static final Gateway TSUNDERE_GATEWAY = new Gateway() {
        @NonNull
        @Override
        public String getVapid() {
            return VAPID_PUBLIC_KEY;
        }

        @NonNull
        @Override
        public String getEndpoint(@NonNull String token) {
            return GATEWAY + "fcm/" + token;
        }
    };

    @Nullable
    @Override
    public Gateway getGateway() {
        return TSUNDERE_GATEWAY;
    }

    public static boolean isFcmEndpoint(String endpoint) {
        return endpoint != null && endpoint.startsWith(GATEWAY + "fcm/");
    }

    /** Без сервисов Google встроенный дистрибьютор может только отказать в регистрации. */
    public static boolean isAvailable(Context context) {
        try {
            context.getPackageManager().getPackageInfo("com.google.android.gms", 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isSelf(Context context, String distributor) {
        return distributor != null && context.getPackageName().equals(distributor);
    }

    /** Первый установленный дистрибьютор-приложение (ntfy и т.п.), не считая встроенного. */
    @Nullable
    public static String firstThirdPartyDistributor(Context context) {
        try {
            for (String d : UnifiedPush.getDistributors(context)) {
                if (!isSelf(context, d)) return d;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return null;
    }

    /** Дистрибьютор по умолчанию: приложение-дистрибьютор, если есть, иначе встроенный FCM. */
    @Nullable
    public static String defaultDistributor(Context context) {
        String third = firstThirdPartyDistributor(context);
        if (third != null) return third;
        return isAvailable(context) ? context.getPackageName() : null;
    }

    public static boolean hasAnyDistributor(Context context) {
        return defaultDistributor(context) != null;
    }

    public static CharSequence label(Context context, String distributor) {
        if (distributor == null) return "—";
        if (isSelf(context, distributor)) return "Google FCM (встроенный)";
        try {
            PackageManager pm = context.getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(distributor, 0);
            CharSequence l = pm.getApplicationLabel(ai);
            if (l != null && l.length() > 0) return l;
        } catch (Exception ignore) {
        }
        return distributor;
    }

    /**
     * Приёмник сообщений FCM из библиотеки включаем только когда выбран встроенный дистрибьютор,
     * чтобы в обычном режиме FCM он не трогал пуши Firebase.
     */
    public static void syncComponents(Context context, @Nullable String distributor) {
        try {
            boolean enable = isSelf(context, distributor);
            PackageManager pm = context.getPackageManager();
            ComponentName cn = new ComponentName(context, FIREBASE_RECEIVER);
            int want = enable ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            if (pm.getComponentEnabledSetting(cn) != want) {
                pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Текущий выбранный дистрибьютор (сохранённый или подтверждённый). */
    @Nullable
    public static String currentDistributor(Context context) {
        try {
            String ack = UnifiedPush.getAckDistributor(context);
            if (ack != null) return ack;
            return UnifiedPush.getSavedDistributor(context);
        } catch (Throwable e) {
            return null;
        }
    }
}
