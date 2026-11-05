package app.tsunderegram.security;

import android.util.Base64;
import org.telegram.messenger.FileLog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Криптографический модуль TsundereGram для анонимизации Telegram ID
 * и сквозного шифрования полезной нагрузки конфигураций (значки, рамки, баннеры, админы).
 *
 * Предотвращает обнаружение токена бота автоматическими сканерами репозиториев
 * и скрывает реальные Telegram ID пользователей в публичном канале.
 */
public final class TsundereCrypto {

    // Секретная соль для одностороннего хеширования ID пользователей (HMAC-SHA256 style)
    private static final String SALT = "tsundere_tsun_dere_super_secret_salt_2026_xoxo";

    // TsundereGram: токена бота в приложении больше нет — он хранится только на сервере (TsundereApi).

    private static final String AES_PASSPHRASE = "tsunderegram_cloud_secure_encryption_key_v1";

    private TsundereCrypto() {}

    public static String hashUserId(long userId) {
        if (userId == 0) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = SALT + ":" + userId + ":" + SALT;
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) { // 32 символа hex
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Throwable t) {
            FileLog.e(t);
            return "u_" + userId;
        }
    }

    /**
     * Проверка: совпадает ли хеш из конфигурации с конкретным Telegram ID.
     */
    public static boolean matchesUserId(String hashOrId, long userId) {
        if (hashOrId == null || userId == 0) {
            return false;
        }
        if (hashOrId.equals(String.valueOf(userId))) {
            return true;
        }
        return hashOrId.equals(hashUserId(userId));
    }

    /**
     * Шифрование строки JSON с использованием AES-256-CBC.
     */
    public static String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = md.digest(AES_PASSPHRASE.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            byte[] ivBytes = Arrays.copyOfRange(keyBytes, 0, 16);
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);

            byte[] cipherBytes = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(cipherBytes, Base64.NO_WRAP);
        } catch (Throwable t) {
            FileLog.e("TsundereCrypto encrypt error: " + t);
            return plainText;
        }
    }

    /**
     * Расшифровка строки с использованием AES-256-CBC.
     * Если строка не зашифрована (начинается с '{'), возвращает как есть для обратной совместимости.
     */
    public static String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return "";
        }
        String trimmed = cipherText.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            // Старый незашифрованный JSON
            return trimmed;
        }
        if (trimmed.startsWith("ENC:")) {
            trimmed = trimmed.substring(4).trim();
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = md.digest(AES_PASSPHRASE.getBytes(StandardCharsets.UTF_8));
            SecretKeySpec secretKey = new SecretKeySpec(keyBytes, "AES");

            byte[] ivBytes = Arrays.copyOfRange(keyBytes, 0, 16);
            IvParameterSpec iv = new IvParameterSpec(ivBytes);

            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, iv);

            byte[] decoded = Base64.decode(trimmed, Base64.DEFAULT);
            byte[] plainBytes = cipher.doFinal(decoded);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            FileLog.e("TsundereCrypto decrypt error: " + t);
            return trimmed;
        }
    }
}
