package app.tsunderegram.security;

import android.content.SharedPreferences;

import org.telegram.messenger.UserConfig;

import java.util.Set;

/**
 * TsundereGram: локальные кастомизации (рамка, баннер, цвета профиля, градиент ника) отдельно
 * для каждого аккаунта на устройстве. Ключ настройки дополняется id аккаунта.
 *
 * Старое общее значение (до обновления) один раз забирает себе аккаунт, который первым его
 * прочитает — обычно активный при первом запуске; остальные аккаунты начинают с чистого листа.
 */
public final class AccountScope {

    private static final String CLAIMED_PREFIX = "acc_claimed_";

    private AccountScope() {
    }

    public static long currentUserId() {
        try {
            int account = UserConfig.selectedAccount;
            if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
                account = 0;
            }
            return UserConfig.getInstance(account).getClientUserId();
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Ключ текущего аккаунта (с однократным переносом старого общего значения). */
    public static String key(SharedPreferences prefs, String base) {
        long uid = currentUserId();
        if (uid == 0 || prefs == null) {
            return base;
        }
        String scoped = base + "@" + uid;
        try {
            String claimed = CLAIMED_PREFIX + base;
            if (!prefs.contains(scoped) && prefs.contains(base) && !prefs.getBoolean(claimed, false)) {
                Object legacy = prefs.getAll().get(base);
                SharedPreferences.Editor editor = prefs.edit();
                if (put(editor, scoped, legacy)) {
                    editor.putBoolean(claimed, true);
                    editor.commit();
                }
            }
        } catch (Throwable ignore) {
        }
        return scoped;
    }

    @SuppressWarnings("unchecked")
    private static boolean put(SharedPreferences.Editor editor, String key, Object value) {
        if (value instanceof String) {
            editor.putString(key, (String) value);
        } else if (value instanceof Boolean) {
            editor.putBoolean(key, (Boolean) value);
        } else if (value instanceof Integer) {
            editor.putInt(key, (Integer) value);
        } else if (value instanceof Long) {
            editor.putLong(key, (Long) value);
        } else if (value instanceof Float) {
            editor.putFloat(key, (Float) value);
        } else if (value instanceof Set) {
            editor.putStringSet(key, (Set<String>) value);
        } else {
            return false;
        }
        return true;
    }
}
