package app.tsunderegram.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.text.TextUtils;

import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TsundereGram: доступ к общему конфигу (значки, баннеры, рамки, цвета) через свой сервер
 * (Cloudflare Worker, см. server/tsundere-api). Токен бота хранится только на сервере —
 * в APK его больше нет. Запись разрешается только авторизованным пользователям: клиент
 * получает от Telegram подписанные initData мини-приложения бота, сервер проверяет подпись
 * токеном бота и узнаёт настоящий user id — подделать его нельзя.
 */
public final class TsundereApi {

    /** Подставляется при сборке в CI из переменной репозитория TSUNDERE_API_URL. */
    private static final String DEFAULT_BASE = "@TSUNDERE_API_URL@";

    private static final String PREFS = "tsundere_api";
    private static final String KEY_BASE = "base_url";
    private static final String HEADER_AUTH = "X-Tsundere-Auth";
    private static final long INIT_DATA_TTL = 6L * 60 * 60 * 1000;

    private static String cachedInitData;
    private static long cachedInitDataTime;
    private static int cachedInitDataAccount = -1;
    private static long cachedInitDataUid;
    private static String cachedBotUsername;
    /**
     * Бот, найденный через resolveUsername, — отдельно для каждого Telegram-аккаунта:
     * access_hash у каждого аккаунта свой. Раньше кэш был общий, и после переключения
     * аккаунта запросы шли с чужим access_hash → BOT_INVALID.
     */
    private static final java.util.HashMap<Long, TLRPC.User> cachedBotUsers = new java.util.HashMap<>();

    private TsundereApi() {
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String normalize(String url) {
        if (url == null) {
            return "";
        }
        url = url.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (!url.isEmpty() && !url.startsWith("http")) {
            url = "https://" + url;
        }
        return url;
    }

    /** Адрес сервера: ручная настройка (если задана) или вшитый при сборке. Пустая строка — не настроен. */
    public static String base() {
        String custom = prefs().getString(KEY_BASE, null);
        if (!TextUtils.isEmpty(custom)) {
            return normalize(custom);
        }
        if (DEFAULT_BASE.startsWith("@")) {
            return "";
        }
        return normalize(DEFAULT_BASE);
    }

    public static String getCustomBase() {
        return prefs().getString(KEY_BASE, "");
    }

    public static void setCustomBase(String url) {
        prefs().edit().putString(KEY_BASE, normalize(url)).apply();
        synchronized (TsundereApi.class) {
            cachedInitData = null;
            cachedBotUsername = null;
            cachedBotUsers.clear();
        }
    }

    public static boolean isConfigured() {
        return !TextUtils.isEmpty(base());
    }

    private static String baseOrThrow() {
        String b = base();
        if (TextUtils.isEmpty(b)) {
            // осмысленная ошибка вместо запроса «в никуда»
            return "https://tsundere-api.invalid";
        }
        return b;
    }

    public static final String NOT_CONFIGURED =
            "Сервер TsundereGram не настроен. Создатель должен указать адрес сервера: «Выдать значок» → 🔗.";

    /** Понятный текст ошибки вместо «Unable to resolve host tsundere-api.invalid» и т.п. */
    public static String explain(String message) {
        if (TextUtils.isEmpty(message)) {
            return isConfigured() ? "Нет ответа от сервера TsundereGram" : NOT_CONFIGURED;
        }
        if (message.contains("tsundere-api.invalid") || !isConfigured()) {
            return NOT_CONFIGURED;
        }
        String lower = message.toLowerCase();
        if (lower.contains("unable to resolve host") || lower.contains("failed to connect") || lower.contains("timeout") || lower.contains("timed out")) {
            return "Сервер TsundereGram недоступен (" + base() + "). Проверьте адрес и интернет.";
        }
        if (message.contains("403")) {
            return "Сервер отказал: недостаточно прав (" + message + ")";
        }
        if (message.contains("HTTP 401") || message.contains("Нужна авторизация Telegram")) {
            invalidateAuth();
            return authErrorText();
        }
        return message;
    }

    public static String explain(Throwable t) {
        return explain(t != null ? (t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName()) : null);
    }

    /**
     * Проверка сервера: GET /info. Возвращает null, если всё в порядке (в {@code okInfo[0]} — имя бота),
     * иначе текст ошибки. Вызывать не из UI-потока.
     */
    public static String check(String baseUrl, String[] okInfo) {
        String b = normalize(baseUrl);
        if (TextUtils.isEmpty(b)) {
            return NOT_CONFIGURED;
        }
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(b + "/info").openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            int code = c.getResponseCode();
            String body = "";
            try {
                java.io.InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
                if (in != null) {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            sb.append(line);
                        }
                    }
                    body = sb.toString();
                }
            } catch (Exception ignore) {
            }
            JSONObject o = null;
            try {
                o = new JSONObject(body);
            } catch (Exception ignore) {
            }
            if (code == 200 && o != null && o.optBoolean("ok")) {
                if (okInfo != null && okInfo.length > 0) {
                    okInfo[0] = o.optString("bot", "");
                }
                return null;
            }
            if (o != null && o.has("description")) {
                return o.optString("description");
            }
            return "Сервер ответил кодом " + code + " — это не TsundereGram API";
        } catch (Exception e) {
            return explain(e.getMessage() != null ? e.getMessage() : e.toString());
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    /** Замена "https://api.telegram.org/bot<TOKEN>/" — методы Bot API через сервер. */
    public static String botBase() {
        return baseOrThrow() + "/bot/";
    }

    /** Замена "https://api.telegram.org/file/bot<TOKEN>/" — скачивание файлов через сервер. */
    public static String fileBase() {
        return baseOrThrow() + "/file/";
    }

    /** Добавляет подпись пользователя к запросу на запись. Только для нашего сервера. */
    public static void authorize(HttpURLConnection connection) {
        if (connection == null) {
            return;
        }
        try {
            String b = base();
            if (TextUtils.isEmpty(b) || !connection.getURL().toString().startsWith(b + "/")) {
                return; // сторонние хосты (catbox и т.п.) не должны получать данные авторизации
            }
            String initData = getInitData();
            if (!TextUtils.isEmpty(initData)) {
                connection.setRequestProperty(HEADER_AUTH, initData);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static synchronized void invalidateAuth() {
        cachedInitData = null;
    }

    private static String readAll(HttpURLConnection c) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private static String fetchBotUsername() {
        if (cachedBotUsername != null) {
            return cachedBotUsername;
        }
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(base() + "/info").openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) {
                return null;
            }
            JSONObject o = new JSONObject(readAll(c));
            String bot = o.optString("bot", null);
            if (!TextUtils.isEmpty(bot)) {
                cachedBotUsername = bot.startsWith("@") ? bot.substring(1) : bot;
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return cachedBotUsername;
    }

    private static TLObject sendSync(int account, TLObject req) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<TLObject> ref = new AtomicReference<>();
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (error == null) {
                ref.set(response);
            } else {
                lastTlError = error.text;
                FileLog.e("TsundereApi: " + error.text);
            }
            latch.countDown();
        });
        try {
            latch.await(12, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        return ref.get();
    }

    private static String extractInitData(String url) {
        if (url == null) {
            return null;
        }
        int hash = url.indexOf('#');
        String fragment = hash >= 0 ? url.substring(hash + 1) : url;
        int q = fragment.indexOf('?');
        if (!fragment.contains("tgWebAppData=") && q >= 0) {
            fragment = fragment.substring(q + 1);
        }
        for (String part : fragment.split("[&?]")) {
            if (part.startsWith("tgWebAppData=")) {
                try {
                    String raw = part.substring("tgWebAppData=".length());
                    // URLDecoder превращает «+» в пробел — внутри initData «+» быть не должно, но на всякий случай
                    return URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8");
                } catch (Throwable e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static volatile String lastTlError;
    private static volatile String lastAuthError;
    private static volatile String lastProbeReason;
    private static long lastFailTime;

    /** Почему не удалось получить подпись Telegram (для текста ошибки). */
    public static String getLastAuthError() {
        return lastAuthError;
    }

    private static final String[] METHOD_NAMES = {"simpleWebView", "webView", "botMenu", "mainWebView"};

    private static String requestInitData(int account, TLRPC.User bot, int method) {
        TLRPC.TL_inputUser inputUser = new TLRPC.TL_inputUser();
        inputUser.user_id = bot.id;
        inputUser.access_hash = bot.access_hash;
        TLRPC.TL_inputPeerUser peer = new TLRPC.TL_inputPeerUser();
        peer.user_id = bot.id;
        peer.access_hash = bot.access_hash;
        final String appUrl = base() + "/app";
        TLObject req;
        switch (method) {
            case 0: {
                TLRPC.TL_messages_requestSimpleWebView r = new TLRPC.TL_messages_requestSimpleWebView();
                r.bot = inputUser;
                r.url = appUrl;
                r.flags |= 8;
                r.platform = "android";
                req = r;
                break;
            }
            case 1: {
                TLRPC.TL_messages_requestWebView r = new TLRPC.TL_messages_requestWebView();
                r.bot = inputUser;
                r.peer = peer;
                r.url = appUrl;
                r.flags |= 2;
                r.platform = "android";
                req = r;
                break;
            }
            case 2: {
                TLRPC.TL_messages_requestWebView r = new TLRPC.TL_messages_requestWebView();
                r.bot = inputUser;
                r.peer = peer;
                r.from_bot_menu = true;
                r.platform = "android";
                req = r;
                break;
            }
            default: {
                TLRPC.TL_messages_requestMainWebView r = new TLRPC.TL_messages_requestMainWebView();
                r.bot = inputUser;
                r.peer = peer;
                r.platform = "android";
                req = r;
                break;
            }
        }
        lastTlError = null;
        TLObject res = sendSync(account, req);
        if (res instanceof TLRPC.TL_webViewResultUrl) {
            String data = extractInitData(((TLRPC.TL_webViewResultUrl) res).url);
            if (TextUtils.isEmpty(data)) {
                lastAuthError = METHOD_NAMES[method] + ": в ответе нет tgWebAppData";
            }
            return data;
        }
        lastAuthError = METHOD_NAMES[method] + ": " + (lastTlError != null ? lastTlError : "нет ответа Telegram");
        return null;
    }

    /**
     * Проверка подписи без побочных эффектов: сервер сначала проверяет авторизацию (401), а пустое
     * тело отклоняет уже после неё (400 «bad body»). Возвращает HTTP-код или -1 при ошибке сети.
     */
    private static int probeAuth(String initData) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(base() + "/bot/editMessageText").openConnection();
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            c.setRequestProperty(HEADER_AUTH, initData);
            try (java.io.OutputStream os = c.getOutputStream()) {
                os.write("{}".getBytes(StandardCharsets.UTF_8));
            }
            int code = c.getResponseCode();
            if (code == 401) {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = r.readLine()) != null) {
                        sb.append(line);
                    }
                    lastProbeReason = new JSONObject(sb.toString()).optString("description", null);
                } catch (Throwable ignored) {
                }
            }
            return code;
        } catch (Throwable e) {
            FileLog.e(e);
            return -1;
        } finally {
            if (c != null) {
                try {
                    c.disconnect();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Подписанные Telegram данные пользователя (initData мини-приложения бота сервера).
     * Пробует несколько способов получения и проверяет каждый на сервере (без побочных эффектов),
     * в кэш попадает только подпись, которую сервер принял.
     * Блокирующий вызов — только из фонового потока.
     */
    public static synchronized String getInitData() {
        final int account = UserConfig.selectedAccount;
        final long uid = UserConfig.getInstance(account).getClientUserId();
        if (cachedInitData != null && cachedInitDataAccount == account && cachedInitDataUid == uid
                && System.currentTimeMillis() - cachedInitDataTime < INIT_DATA_TTL) {
            return cachedInitData;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            lastAuthError = "запрос из UI-потока";
            return null;
        }
        if (!isConfigured() || !UserConfig.getInstance(account).isClientActivated()) {
            lastAuthError = !isConfigured() ? "сервер не настроен" : "нет входа в аккаунт";
            return null;
        }
        final String username = fetchBotUsername();
        if (TextUtils.isEmpty(username)) {
            lastAuthError = "сервер не сообщил имя бота (/info)";
            return null;
        }
        String fallback = null;
        StringBuilder reasons = new StringBuilder();
        for (int attempt = 0; attempt < 2; attempt++) {
            TLRPC.User bot = attempt == 0 ? cachedBotUsers.get(uid) : null;
            final boolean fresh = bot == null || !username.equalsIgnoreCase(bot.username);
            if (fresh) {
                bot = resolveBot(account, username);
                if (bot == null) {
                    return null;
                }
                cachedBotUsers.put(uid, bot);
            }
            reasons.setLength(0);
            boolean invalidBot = false;
            for (int method = 0; method < METHOD_NAMES.length; method++) {
                String initData = requestInitData(account, bot, method);
                if (TextUtils.isEmpty(initData)) {
                    if (isInvalidBotError(lastTlError)) {
                        invalidBot = true;
                    }
                    if (lastAuthError != null) {
                        if (reasons.length() > 0) reasons.append("; ");
                        reasons.append(lastAuthError);
                    }
                    continue;
                }
                int code = probeAuth(initData);
                if (code == 401) {
                    lastAuthError = METHOD_NAMES[method] + ": сервер не принял подпись" + (lastProbeReason != null ? " — " + lastProbeReason : "");
                    FileLog.e("TsundereApi: initData rejected, method " + METHOD_NAMES[method]);
                    if (reasons.length() > 0) reasons.append("; ");
                    reasons.append(lastAuthError);
                    continue;
                }
                if (code < 0) {
                    // сеть недоступна — проверить нельзя, используем как есть, но не кэшируем надолго
                    if (fallback == null) {
                        fallback = initData;
                    }
                    continue;
                }
                cachedInitData = initData;
                cachedInitDataTime = System.currentTimeMillis();
                cachedInitDataAccount = account;
                cachedInitDataUid = uid;
                lastAuthError = null;
                return initData;
            }
            if (fallback != null || fresh || !invalidBot) {
                break;
            }
            // устаревший access_hash бота — найти бота заново и повторить один раз
            cachedBotUsers.remove(uid);
        }
        lastFailTime = System.currentTimeMillis();
        if (fallback == null && reasons.length() > 0) {
            // все способы по очереди — чтобы в ошибке была видна настоящая причина
            lastAuthError = reasons.toString();
        }
        return fallback;
    }

    private static boolean isInvalidBotError(String err) {
        return err != null && (err.contains("BOT_INVALID") || err.contains("USER_ID_INVALID")
                || err.contains("PEER_ID_INVALID") || err.contains("USER_BOT_INVALID") || err.contains("ACCESS_HASH"));
    }

    private static TLRPC.User resolveBot(int account, String username) {
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = username;
        lastTlError = null;
        TLObject res = sendSync(account, req);
        TLRPC.User bot = null;
        if (res instanceof TLRPC.TL_contacts_resolvedPeer) {
            TLRPC.TL_contacts_resolvedPeer rp = (TLRPC.TL_contacts_resolvedPeer) res;
            for (TLRPC.User u : rp.users) {
                if (u != null && u.bot) {
                    bot = u;
                    break;
                }
            }
            try {
                org.telegram.messenger.MessagesController.getInstance(account).putUsers(rp.users, false);
            } catch (Throwable ignore) {
            }
        }
        if (bot == null) {
            lastAuthError = "не найден бот @" + username + (lastTlError != null ? " (" + lastTlError + ")" : "");
        }
        return bot;
    }
    /** Текст для ошибки 401 от нашего сервера. */
    public static String authErrorText() {
        String why = lastAuthError;
        return "Сервер TsundereGram не принял вход через Telegram (401)" + (TextUtils.isEmpty(why) ? "" : ": " + why)
                + ". Откройте бота @" + (cachedBotUsername != null ? cachedBotUsername : "TsundereGram_Banners_bot")
                + ", нажмите «Старт» и попробуйте ещё раз.";
    }

    /** Текст ошибки записи: для 401 — понятное объяснение (и сброс подписи для следующей попытки). */
    public static String httpError(String prefix, int code) {
        if (code == 401) {
            invalidateAuth();
            return authErrorText();
        }
        return (prefix != null ? prefix : "") + "HTTP " + code;
    }
}
