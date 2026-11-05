package org.telegram.messenger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ребрендинг строк интерфейса: «Telegram» как название приложения превращается в «TsundereGram».
 * Не трогает названия сервисов и официальных приложений Telegram (Premium, Stars, Desktop, Web…),
 * ссылки, юзернеймы и упоминания «официальных приложений».
 */
public final class TsundereBrand {

    public static final String NAME = "TsundereGram";

    private static final Pattern WORD = Pattern.compile("Telegram");
    private static final Pattern KEEP_AFTER = Pattern.compile(
            "^(?:'s\\s+)?\\s*(?:Premium|Stars|Star|Business|Desktop|Web|X\\b|Gateway|Ads|Passport|Wallet|API|FAQ|Support|Mini\\s+App|Gift|Fragment|для\\s+(?:компьютера|браузера|Android|iOS|macOS|Windows|Linux|ПК)|Премиум|Звёзд|Звезд|Stars|Бизнес)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern KEEP_BEFORE = Pattern.compile(
            "(?:фициальн\\S*\\s+(?:приложени\\S*\\s+|клиент\\S*\\s+)?|fficial\\s+(?:apps?\\s+)?|Поддержк\\S*\\s+|Premium\\s+|Звёзды\\s+|Звезды\\s+|команда\\s+|team\\s+)$",
            Pattern.CASE_INSENSITIVE);
    private static final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    private TsundereBrand() {
    }

    public static String apply(String s) {
        if (s == null || s.indexOf("Telegram") < 0) {
            return s;
        }
        final String cached = cache.get(s);
        if (cached != null) {
            return cached;
        }
        final String result = replace(s);
        if (cache.size() < 4000) {
            cache.put(s, result);
        }
        return result;
    }

    private static String replace(String s) {
        final Matcher m = WORD.matcher(s);
        final StringBuilder out = new StringBuilder(s.length() + 16);
        int last = 0;
        while (m.find()) {
            final int start = m.start();
            final int end = m.end();
            boolean keep = false;
            if (start > 0) {
                final char prev = s.charAt(start - 1);
                if (Character.isLetterOrDigit(prev) || prev == '@' || prev == '/' || prev == '.' || prev == '_' || prev == '-') {
                    keep = true;
                }
            }
            if (!keep && end < s.length()) {
                final char next = s.charAt(end);
                if (Character.isLetterOrDigit(next) || next == '_' || next == '/'
                        || (next == '.' && end + 1 < s.length() && Character.isLetter(s.charAt(end + 1)))) {
                    keep = true;
                }
            }
            if (!keep && KEEP_AFTER.matcher(s.substring(end, Math.min(s.length(), end + 32))).find()) {
                keep = true;
            }
            if (!keep && KEEP_BEFORE.matcher(s.substring(Math.max(0, start - 40), start)).find()) {
                keep = true;
            }
            out.append(s, last, start).append(keep ? "Telegram" : NAME);
            last = end;
        }
        out.append(s, last, s.length());
        return out.toString();
    }
}
