package app.tsunderegram.notifications;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;

import java.util.concurrent.ConcurrentHashMap;

/**
 * TsundereGram: до какого сообщения пользователь сам прочитал чат на этом устройстве.
 *
 * <p>В режиме призрака прочтение не уходит на сервер, поэтому сервер (и локальная база после
 * синхронизации) снова считает старые сообщения непрочитанными. При следующем пуше Telegram
 * собирал их все в уведомление — «18 сообщений», хотя пришло одно. Здесь хранится локальная
 * граница прочтения, и уведомления не показывают то, что уже было прочитано.</p>
 */
public final class TsunLocalRead {

    private static final String PREFS = "tsun_local_read";
    private static final ConcurrentHashMap<String, Integer> cache = new ConcurrentHashMap<>();
    private static volatile SharedPreferences prefs;

    private TsunLocalRead() {
    }

    private static SharedPreferences prefs() {
        if (prefs == null) {
            prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        }
        return prefs;
    }

    private static String key(int account, long dialogId) {
        return account + "_" + dialogId;
    }

    /** Чат прочитан на устройстве до сообщения maxId включительно. */
    public static void mark(int account, long dialogId, int maxId) {
        if (maxId <= 0 || maxId == Integer.MAX_VALUE || dialogId == 0 || DialogObject.isEncryptedDialog(dialogId)) {
            return;
        }
        try {
            final String k = key(account, dialogId);
            Integer old = cache.get(k);
            if (old == null) {
                old = prefs().getInt(k, 0);
            }
            if (maxId <= old) {
                cache.put(k, old);
                return;
            }
            cache.put(k, maxId);
            prefs().edit().putInt(k, maxId).apply();
        } catch (Throwable ignore) {
        }
    }

    /** Сообщение уже было прочитано на этом устройстве — в уведомление его не добавляем. */
    public static boolean isRead(int account, long dialogId, int messageId) {
        if (messageId <= 0 || dialogId == 0 || DialogObject.isEncryptedDialog(dialogId)) {
            return false;
        }
        try {
            final String k = key(account, dialogId);
            Integer v = cache.get(k);
            if (v == null) {
                v = prefs().getInt(k, 0);
                cache.put(k, v);
            }
            return v > 0 && messageId <= v;
        } catch (Throwable ignore) {
            return false;
        }
    }

    /** Аккаунт вышел — границы прочтения больше не нужны. */
    public static void clearAccount(int account) {
        try {
            final String prefix = account + "_";
            SharedPreferences.Editor e = prefs().edit();
            for (String k : prefs().getAll().keySet()) {
                if (k.startsWith(prefix)) {
                    e.remove(k);
                }
            }
            e.apply();
            for (String k : cache.keySet()) {
                if (k.startsWith(prefix)) {
                    cache.remove(k);
                }
            }
        } catch (Throwable ignore) {
        }
    }
}
