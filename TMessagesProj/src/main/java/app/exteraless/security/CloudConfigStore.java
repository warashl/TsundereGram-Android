package app.exteraless.security;

import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Хранилище облачного конфига TsundereGram.
 *
 * Раньше весь конфиг (бейджи, рамки, баннеры, цвета, список клиентов) лежал в ОДНОМ
 * текстовом закрепе канала. У текста в Telegram лимит 4096 символов, поэтому когда
 * пользователей стало много, editMessageText начал падать ("конфиг не помещается",
 * HTTP 413/400) — и настройки новых пользователей сохранялись только локально.
 *
 * Теперь: пока конфиг маленький — он остаётся текстом, а когда перестаёт влезать —
 * закреп превращается в документ tsundere_config.txt (лимит 50 МБ). Формат содержимого
 * не меняется: CONFIG_HEADER + "\nENC:" + зашифрованный JSON.
 */
public final class CloudConfigStore {

    /** Запас до лимита Telegram в 4096 символов. */
    public static final int TEXT_LIMIT = 4000;
    private static final String CONFIG_HEADER = "#TSUNDERE_BADGES_CONFIG";
    private static final String FILE_NAME = "tsundere_config.txt";

    private static volatile String cachedUniqueId;
    private static volatile String cachedContent;

    private CloudConfigStore() {}

    private static String api(String method) {
        return "https://api.telegram.org/bot" + TsundereCrypto.getBotToken() + "/" + method;
    }

    /**
     * Возвращает текст конфига из закрепа: либо text обычного сообщения,
     * либо содержимое документа. Если документ не скачался — бросает исключение,
     * чтобы вызывающий код НЕ перезаписал облако пустым конфигом.
     */
    public static String readText(JSONObject pinned) throws IOException, JSONException {
        if (pinned == null) {
            return "";
        }
        JSONObject doc = pinned.optJSONObject("document");
        if (doc == null) {
            return pinned.optString("text", "");
        }
        String uniqueId = doc.optString("file_unique_id", "");
        String cached = cachedContent;
        if (!TextUtils.isEmpty(uniqueId) && uniqueId.equals(cachedUniqueId) && cached != null) {
            return cached;
        }
        String fileId = doc.optString("file_id", "");
        if (TextUtils.isEmpty(fileId)) {
            throw new IOException("config document without file_id");
        }
        String content = downloadFileText(fileId);
        cachedUniqueId = uniqueId;
        cachedContent = content;
        return content;
    }

    private static String downloadFileText(String fileId) throws IOException, JSONException {
        HttpURLConnection conn = (HttpURLConnection) new URL(api("getFile") + "?file_id=" + URLEncoder.encode(fileId, "UTF-8")).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        int code = conn.getResponseCode();
        if (code != 200) {
            throw new IOException("getFile HTTP " + code);
        }
        JSONObject resp = new JSONObject(readAll(conn.getInputStream()));
        String path = resp.getJSONObject("result").optString("file_path", "");
        if (TextUtils.isEmpty(path)) {
            throw new IOException("getFile: empty file_path");
        }
        HttpURLConnection dl = (HttpURLConnection) new URL("https://api.telegram.org/file/bot" + TsundereCrypto.getBotToken() + "/" + path).openConnection();
        dl.setConnectTimeout(10000);
        dl.setReadTimeout(20000);
        int dlCode = dl.getResponseCode();
        if (dlCode != 200) {
            throw new IOException("config download HTTP " + dlCode);
        }
        return readAll(dl.getInputStream());
    }

    /**
     * Сохраняет конфиг в закреп и возвращает соединение с уже отправленным запросом
     * (у него можно спокойно вызвать getResponseCode()/getErrorStream(), как раньше
     * у editMessageText).
     */
    public static HttpURLConnection sendConfig(Object chatId, int messageId, JSONObject pinned, String fullText) throws IOException, JSONException {
        final String chat = String.valueOf(chatId);
        final boolean pinnedIsDocument = pinned != null && pinned.optJSONObject("document") != null;

        if (!pinnedIsDocument && fullText.length() <= TEXT_LIMIT) {
            JSONObject payload = new JSONObject();
            payload.put("chat_id", chat);
            payload.put("message_id", messageId);
            payload.put("text", fullText);
            return postJson("editMessageText", payload);
        }

        // Конфиг не влезает в текст (или уже лежит документом) — храним файлом.
        HttpURLConnection edit = editMessageMedia(chat, messageId, fullText);
        int code = edit.getResponseCode();
        if (code == 200) {
            rememberSent(edit, fullText);
            return edit;
        }
        if (pinnedIsDocument) {
            return edit;
        }

        // Старый Bot API не умеет превращать текст в документ — шлём новый документ и закрепляем его.
        HttpURLConnection send = sendDocument(chat, fullText);
        if (send.getResponseCode() != 200) {
            return send;
        }
        JSONObject sent = new JSONObject(readAll(send.getInputStream()));
        JSONObject msg = sent.optJSONObject("result");
        if (msg == null) {
            return send;
        }
        cacheFromMessage(msg, fullText);
        JSONObject pin = new JSONObject();
        pin.put("chat_id", chat);
        pin.put("message_id", msg.optInt("message_id"));
        pin.put("disable_notification", true);
        return postJson("pinChatMessage", pin);
    }

    private static void rememberSent(HttpURLConnection conn, String fullText) {
        try {
            JSONObject resp = new JSONObject(readAll(conn.getInputStream()));
            JSONObject msg = resp.optJSONObject("result");
            if (msg != null) {
                cacheFromMessage(msg, fullText);
            }
        } catch (Throwable ignore) {
            cachedUniqueId = null;
            cachedContent = null;
        }
    }

    private static void cacheFromMessage(JSONObject msg, String fullText) {
        JSONObject doc = msg.optJSONObject("document");
        if (doc != null) {
            cachedUniqueId = doc.optString("file_unique_id", null);
            cachedContent = fullText;
        }
    }

    private static HttpURLConnection postJson(String method, JSONObject payload) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(api(method)).openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(payload.toString().getBytes(StandardCharsets.UTF_8));
            os.flush();
        }
        return conn;
    }

    private static HttpURLConnection editMessageMedia(String chat, int messageId, String fullText) throws IOException, JSONException {
        JSONObject media = new JSONObject();
        media.put("type", "document");
        media.put("media", "attach://config");
        media.put("caption", CONFIG_HEADER);
        String boundary = "----TsundereCfg" + System.currentTimeMillis();
        HttpURLConnection conn = openMultipart("editMessageMedia", boundary);
        try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
            writeField(dos, boundary, "chat_id", chat);
            writeField(dos, boundary, "message_id", String.valueOf(messageId));
            writeField(dos, boundary, "media", media.toString());
            writeFile(dos, boundary, "config", fullText);
            dos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            dos.flush();
        }
        return conn;
    }

    private static HttpURLConnection sendDocument(String chat, String fullText) throws IOException {
        String boundary = "----TsundereCfg" + System.currentTimeMillis();
        HttpURLConnection conn = openMultipart("sendDocument", boundary);
        try (DataOutputStream dos = new DataOutputStream(conn.getOutputStream())) {
            writeField(dos, boundary, "chat_id", chat);
            writeField(dos, boundary, "caption", CONFIG_HEADER);
            writeField(dos, boundary, "disable_notification", "true");
            writeFile(dos, boundary, "document", fullText);
            dos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
            dos.flush();
        }
        return conn;
    }

    private static HttpURLConnection openMultipart(String method, String boundary) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(api(method)).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(30000);
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        return conn;
    }

    private static void writeField(DataOutputStream dos, String boundary, String name, String value) throws IOException {
        dos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        dos.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        dos.write(value.getBytes(StandardCharsets.UTF_8));
        dos.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFile(DataOutputStream dos, String boundary, String name, String content) throws IOException {
        dos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        dos.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + FILE_NAME + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        dos.write("Content-Type: text/plain; charset=utf-8\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        dos.write(content.getBytes(StandardCharsets.UTF_8));
        dos.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static String readAll(InputStream is) throws IOException {
        try (InputStream in = is; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
