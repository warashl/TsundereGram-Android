package tw.nekomimi.nekogram.helpers;

import com.google.gson.Gson;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_bots;

import java.util.HashMap;

public class CloudStorageHelper extends AccountInstance {

    private static final CloudStorageHelper[] Instance = new CloudStorageHelper[UserConfig.MAX_ACCOUNT_COUNT];
    /**
     * TsundereGram: облачная копия настроек хранится в Cloud Storage нашего бота.
     * Данные Cloud Storage видит только сам пользователь через Telegram — владелец бота прочитать их не может.
     * Старый бот tsunderegram остаётся только для чтения: если у нового бота ещё ничего нет,
     * настройки читаются оттуда, а следующая выгрузка уже уходит к нашему боту.
     */
    private static final String WEBVIEW_BOT = "TsundereGram_Banners_bot";
    private static final long LEGACY_BOT_ID = 8856509355L;
    private static final String LEGACY_BOT = WEBVIEW_BOT;

    private final Gson gson = new Gson();

    public CloudStorageHelper(int num) {
        super(num);
    }

    public static CloudStorageHelper getInstance(int num) {
        CloudStorageHelper localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (CloudStorageHelper.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new CloudStorageHelper(num);
                }
            }
        }
        return localInstance;
    }

    private TLRPC.User botUser;

    private void resolveBot(boolean legacy, Utilities.Callback<TLRPC.User> callback) {
        if (legacy) {
            TLRPC.User user = getMessagesController().getUser(LEGACY_BOT_ID);
            if (user != null) {
                callback.run(user);
                return;
            }
        } else if (botUser != null) {
            callback.run(botUser);
            return;
        }
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = legacy ? LEGACY_BOT : WEBVIEW_BOT;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            TLRPC.User found = null;
            if (response instanceof TLRPC.TL_contacts_resolvedPeer) {
                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                getMessagesController().putUsers(res.users, false);
                getMessagesController().putChats(res.chats, false);
                for (TLRPC.User u : res.users) {
                    if (u != null && u.bot) {
                        found = u;
                        break;
                    }
                }
            }
            if (found != null && !legacy) {
                botUser = found;
            }
            callback.run(found);
        }));
    }

    private void invokeWebViewCustomMethod(String method, String data, Utilities.Callback2<String, String> callback) {
        invokeWebViewCustomMethod(method, data, false, callback);
    }

    private void invokeWebViewCustomMethod(String method, String data, boolean legacy, Utilities.Callback2<String, String> callback) {
        resolveBot(legacy, user -> {
            if (user == null) {
                if (callback != null) {
                    callback.run(null, "USER_NOT_FOUND");
                }
                return;
            }
            TL_bots.invokeWebViewCustomMethod req = new TL_bots.invokeWebViewCustomMethod();
            req.bot = getMessagesController().getInputUser(user);
            req.custom_method = method;
            req.params = new TLRPC.TL_dataJSON();
            req.params.data = data;
            getConnectionsManager().sendRequest(req, (res, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (callback != null) {
                    if (error != null) {
                        callback.run(null, error.text);
                    } else if (res instanceof TLRPC.TL_dataJSON) {
                        callback.run(((TLRPC.TL_dataJSON) res).data, null);
                    } else {
                        callback.run(null, null);
                    }
                }
            }));
        });
    }

    private static boolean isEmptyValues(HashMap<?, ?> map) {
        if (map == null || map.isEmpty()) {
            return true;
        }
        for (Object v : map.values()) {
            if (v != null && !String.valueOf(v).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public void setItem(String key, String value, Utilities.Callback2<String, String> callback) {
        HashMap<String, String> map = new HashMap<>();
        map.put("key", key);
        map.put("value", value);
        String payload = gson.toJson(map);
        invokeWebViewCustomMethod("saveStorageValue", payload, false, (res, error) -> {
            if (error != null && !error.contains("FLOOD") && !error.contains("TIMEOUT")) {
                // наш бот недоступен (не найден/не поддерживает хранилище) — сохраняем по-старому
                invokeWebViewCustomMethod("saveStorageValue", payload, true, callback);
                return;
            }
            if (callback != null) {
                callback.run(res, error);
            }
        });
    }

    public void getItem(String key, Utilities.Callback2<String, String> callback) {
        getItems(new String[]{key}, (res, error) -> {
            if (error == null) {
                callback.run(res.get(key), null);
            } else {
                callback.run(null, error);
            }
        });
    }

    public void getItems(String[] keys, Utilities.Callback2<HashMap<String, String>, String> callback) {
        HashMap<String, String[]> map = new HashMap<>();
        map.put("keys", keys);
        String payload = gson.toJson(map);
        invokeWebViewCustomMethod("getStorageValues", payload, false, (res, error) -> {
            HashMap<String, String> values = null;
            if (error == null) {
                try {
                    //noinspection unchecked
                    values = gson.fromJson(res, HashMap.class);
                } catch (Throwable ignore) {
                }
            }
            if (!isEmptyValues(values)) {
                callback.run(values, null);
                return;
            }
            final HashMap<String, String> primary = values;
            final String primaryError = error;
            // у нашего бота пусто — читаем старую копию у бота tsunderegram
            invokeWebViewCustomMethod("getStorageValues", payload, true, (legacyRes, legacyError) -> {
                if (legacyError == null) {
                    try {
                        //noinspection unchecked
                        HashMap<String, String> legacy = gson.fromJson(legacyRes, HashMap.class);
                        if (!isEmptyValues(legacy)) {
                            callback.run(legacy, null);
                            return;
                        }
                    } catch (Throwable ignore) {
                    }
                }
                if (primaryError == null) {
                    callback.run(primary != null ? primary : new HashMap<>(), null);
                } else {
                    callback.run(null, primaryError);
                }
            });
        });
    }

    public void removeItem(String key, Utilities.Callback2<String, String> callback) {
        removeItems(new String[]{key}, callback);
    }

    public void removeItems(String[] keys, Utilities.Callback2<String, String> callback) {
        HashMap<String, String[]> map = new HashMap<>();
        map.put("keys", keys);
        String payload = gson.toJson(map);
        invokeWebViewCustomMethod("deleteStorageValues", payload, false, callback);
        // старая копия у бота tsunderegram тоже удаляется, чтобы не «воскресала» при чтении
        invokeWebViewCustomMethod("deleteStorageValues", payload, true, null);
    }

    public void getKeys(Utilities.Callback2<String[], String> callback) {
        invokeWebViewCustomMethod("getStorageKeys", "{}", false, (res, error) -> {
            String[] keys = null;
            if (error == null) {
                try {
                    keys = gson.fromJson(res, String[].class);
                } catch (Throwable ignore) {
                }
            }
            if (keys != null && keys.length > 0) {
                callback.run(keys, null);
                return;
            }
            final String[] primary = keys;
            final String primaryError = error;
            invokeWebViewCustomMethod("getStorageKeys", "{}", true, (legacyRes, legacyError) -> {
                if (legacyError == null) {
                    try {
                        String[] legacy = gson.fromJson(legacyRes, String[].class);
                        if (legacy != null && legacy.length > 0) {
                            callback.run(legacy, null);
                            return;
                        }
                    } catch (Throwable ignore) {
                    }
                }
                if (primaryError == null) {
                    callback.run(primary != null ? primary : new String[0], null);
                } else {
                    callback.run(null, primaryError);
                }
            });
        });
    }
}
