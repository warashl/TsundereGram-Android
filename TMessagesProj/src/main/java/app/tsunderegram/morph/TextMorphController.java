package app.tsunderegram.morph;

import android.content.Context;
import android.content.SharedPreferences;

import android.text.TextUtils;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TextMorphController {
    public boolean isChatExcluded(long peer) {
        if (peer == 0) return false;
        String raw = preferences.getString("excluded_chats", "");
        if (raw.isEmpty()) return false;
        String[] parts = raw.split(",");
        String s = String.valueOf(peer);
        for (String part : parts) {
            if (part.trim().equals(s)) return true;
        }
        return false;
    }

    public void setChatExcluded(long peer, boolean excluded) {
        if (peer == 0) return;
        java.util.Set<String> set = new java.util.HashSet<>();
        String raw = preferences.getString("excluded_chats", "");
        if (!raw.isEmpty()) {
            for (String part : raw.split(",")) {
                if (!part.trim().isEmpty()) set.add(part.trim());
            }
        }
        if (excluded) {
            set.add(String.valueOf(peer));
        } else {
            set.remove(String.valueOf(peer));
        }
        preferences.edit().putString("excluded_chats", android.text.TextUtils.join(",", set)).apply();
    }

    public java.util.Set<Long> getExcludedChats() {
        java.util.Set<Long> res = new java.util.HashSet<>();
        String raw = preferences.getString("excluded_chats", "");
        if (!raw.isEmpty()) {
            for (String part : raw.split(",")) {
                try {
                    res.add(Long.parseLong(part.trim()));
                } catch (Exception ignore) {}
            }
        }
        return res;
    }


    private static volatile TextMorphController INSTANCE;

    public static final int FONT_NORMAL = 0;
    public static final int FONT_MATH_BOLD = 1;
    public static final int FONT_GOTHIC = 2;
    public static final int FONT_CURSIVE = 3;
    public static final int FONT_DOUBLE_STRUCK = 4;
    public static final int FONT_BUBBLES = 5;
    public static final int FONT_SQUARES = 6;
    public static final int FONT_MONOSPACE = 7;
    public static final int FONT_SANS_SERIF = 8;
    public static final int FONT_SMALL_CAPS = 9;
    public static final int FONT_INVERTED = 10;

    private static final String PREFS_NAME = "tsundere_text_morph_settings";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_FONT_STYLE = "font_style";
    private static final String KEY_VAPORWAVE = "vaporwave";
    private static final String KEY_ZALGO = "zalgo";
    private static final String KEY_ANTI_CAPS = "anti_caps";
    private static final String KEY_ANTI_CAPS_PCT = "anti_caps_pct";
    private static final String KEY_ANTI_CAPS_CAPITALIZE = "anti_caps_capitalize";
    private static final String KEY_LEET_SPEAK = "leet_speak";
    private static final String KEY_PREFIX = "prefix";
    private static final String KEY_SUFFIX = "suffix";
    private static final String KEY_FORCE_BOLD = "force_bold";
    private static final String KEY_FORCE_ITALIC = "force_italic";
    private static final String KEY_FORCE_BOLD_ITALIC = "force_bold_italic";
    private static final String KEY_FORCE_MONO = "force_mono";
    private static final String KEY_FORCE_CODE = "force_code";
    private static final String KEY_CODE_LANG = "code_lang";
    private static final String KEY_FORCE_QUOTE = "force_quote";
    private static final String KEY_FORCE_STRIKE = "force_strike";
    private static final String KEY_FORCE_SPOILER = "force_spoiler";
    private static final String KEY_FORCE_UNDERLINE = "force_underline";
    private static final String KEY_RANDOM_STYLE = "random_style";

    private final SharedPreferences preferences;
    private final Random random = new Random();

    private static final String NORMAL_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    // 1. Math Bold (𝗔𝗕𝗖 𝗮𝗯𝗰)
    private static final int[] FONT_1_MAP = new int[]{
        0x1D5D4, 0x1D5D5, 0x1D5D6, 0x1D5D7, 0x1D5D8, 0x1D5D9, 0x1D5DA, 0x1D5DB, 0x1D5DC, 0x1D5DD, 0x1D5DE, 0x1D5DF, 0x1D5E0, 0x1D5E1, 0x1D5E2, 0x1D5E3, 0x1D5E4, 0x1D5E5, 0x1D5E6, 0x1D5E7, 0x1D5E8, 0x1D5E9, 0x1D5EA, 0x1D5EB, 0x1D5EC, 0x1D5ED, 0x1D5EE, 0x1D5EF, 0x1D5F0, 0x1D5F1, 0x1D5F2, 0x1D5F3, 0x1D5F4, 0x1D5F5, 0x1D5F6, 0x1D5F7, 0x1D5F8, 0x1D5F9, 0x1D5FA, 0x1D5FB, 0x1D5FC, 0x1D5FD, 0x1D5FE, 0x1D5FF, 0x1D600, 0x1D601, 0x1D602, 0x1D603, 0x1D604, 0x1D605, 0x1D606, 0x1D607
    };

    // 2. Gothic / Fraktur (𝕬𝕭𝕮 𝖆𝖇𝖈)
    private static final int[] FONT_2_MAP = new int[]{
        0x1D56C, 0x1D56D, 0x1D56E, 0x1D56F, 0x1D570, 0x1D571, 0x1D572, 0x1D573, 0x1D574, 0x1D575, 0x1D576, 0x1D577, 0x1D578, 0x1D579, 0x1D57A, 0x1D57B, 0x1D57C, 0x1D57D, 0x1D57E, 0x1D57F, 0x1D580, 0x1D581, 0x1D582, 0x1D583, 0x1D584, 0x1D585, 0x1D586, 0x1D587, 0x1D588, 0x1D589, 0x1D58A, 0x1D58B, 0x1D58C, 0x1D58D, 0x1D58E, 0x1D58F, 0x1D590, 0x1D591, 0x1D592, 0x1D593, 0x1D594, 0x1D595, 0x1D596, 0x1D597, 0x1D598, 0x1D599, 0x1D59A, 0x1D59B, 0x1D59C, 0x1D59D, 0x1D59E, 0x1D59F
    };

    // 3. Cursive (Mathematical Bold Script 𝓐𝓑𝓒 𝓪𝫔𝓬... из плагина Text Morph)
    private static final int[] FONT_3_MAP = new int[]{
        0x1D4D0, 0x1D4D1, 0x1D4D2, 0x1D4D3, 0x1D4D4, 0x1D4D5, 0x1D4D6, 0x1D4D7, 0x1D4D8, 0x1D4D9, 0x1D4DA, 0x1D4DB, 0x1D4DC, 0x1D4DD, 0x1D4DE, 0x1D4DF, 0x1D4E0, 0x1D4E1, 0x1D4E2, 0x1D4E3, 0x1D4E4, 0x1D4E5, 0x1D4E6, 0x1D4E7, 0x1D4E8, 0x1D4E9, 0x1D4EA, 0x1DAD4, 0x1D4EC, 0x1D4ED, 0x1D4EE, 0x1D4EF, 0x1D4F0, 0x1D4F1, 0x1D4F2, 0x1D4F3, 0x1D4F4, 0x1D4F5, 0x1D4F6, 0x1D4F7, 0x1D4F8, 0x1D4F9, 0x1D4FA, 0x1D4FB, 0x1D4FC, 0x1D601, 0x1D602, 0x1D603, 0x1D604, 0x1D501, 0x1D502, 0x1D503
    };

    // 4. Double Struck (𝔸𝔹ℂ 𝕒𝕓𝕔 из плагина Text Morph)
    private static final int[] FONT_4_MAP = new int[]{
        0x1D538, 0x1D539, 0x2102, 0x1D53B, 0x1D53C, 0x1D53D, 0x1D53E, 0x210D, 0x1D540, 0x1D541, 0x1D542, 0x1D543, 0x1D544, 0x2115, 0x1D546, 0x2119, 0x211A, 0x211D, 0x1D54A, 0x1D54B, 0x1D54C, 0x1D54D, 0x1D54E, 0x1D54F, 0x1D550, 0x2124, 0x1D552, 0x1DAD4, 0x1D554, 0x1D555, 0x1D556, 0x1D557, 0x1D558, 0x1D559, 0x1D55A, 0x1D55B, 0x1D55C, 0x1D55D, 0x1D55E, 0x1D55F, 0xC624, 0x1D561, 0x1D562, 0x1D563, 0x1D564, 0x1D565, 0x1D566, 0x1D567, 0x1D568, 0x1D569, 0x1D56A, 0x1D56B
    };

    private static final Map<Integer, Map<Character, String>> FONT_MAPS = new HashMap<>();

    static {
        // 1. Math Bold (𝗔𝗕𝗖 𝗮𝗯𝗰)
        FONT_MAPS.put(FONT_MATH_BOLD, buildFromArray(FONT_1_MAP));
        // 2. Gothic / Fraktur (𝕬𝕭𝕮 𝖆𝖇𝖈)
        FONT_MAPS.put(FONT_GOTHIC, buildFromArray(FONT_2_MAP));
        // 3. Cursive (𝓐𝓑𝓒 𝓪𝓫𝓬)
        FONT_MAPS.put(FONT_CURSIVE, buildFromArray(FONT_3_MAP));
        // 4. Double Struck (𝔸𝔹ℂ 𝕒𝕓𝕔)
        FONT_MAPS.put(FONT_DOUBLE_STRUCK, buildFromArray(FONT_4_MAP));
        // 5. Bubbles (ⒶⒷⒸ ⓐⓑⓒ)
        FONT_MAPS.put(FONT_BUBBLES, buildRangeMap(0x24B6, 0x24D0));
        // 6. Squares (🄰🄱🄲)
        FONT_MAPS.put(FONT_SQUARES, buildRangeMap(0x1F130, 0x1F130));
        // 7. Monospace Unicode (𝙼𝚘𝚗𝚘)
        FONT_MAPS.put(FONT_MONOSPACE, buildRangeMap(0x1D670, 0x1D68A));
        // 8. Sans-Serif (𝖲𝖺𝗇𝗌)
        FONT_MAPS.put(FONT_SANS_SERIF, buildRangeMap(0x1D5A0, 0x1D5BA));
        // 9. Small Caps (ꜱᴍᴀʟʟ ᴄᴀᴘꜱ)
        FONT_MAPS.put(FONT_SMALL_CAPS, buildSmallCapsMap());
        // 10. Inverted (Upside down)
        FONT_MAPS.put(FONT_INVERTED, buildInvertedMap());

        // Добавляем поддержку кириллических гомоглифов для всех стилей шрифтов
        final String CYRILLIC_LOOKALIKES = "АВЕКМНОРСТУХаеорсух";
        final String LATIN_LOOKALIKES    = "ABEKMHOPCTYXaeopcyx";
        for (Map<Character, String> map : FONT_MAPS.values()) {
            for (int i = 0; i < CYRILLIC_LOOKALIKES.length(); i++) {
                char cyr = CYRILLIC_LOOKALIKES.charAt(i);
                char lat = LATIN_LOOKALIKES.charAt(i);
                if (map.containsKey(lat) && !map.containsKey(cyr)) {
                    map.put(cyr, map.get(lat));
                }
            }
        }
    }

    private static Map<Character, String> buildFromArray(int[] codePoints) {
        Map<Character, String> map = new HashMap<>();
        for (int i = 0; i < codePoints.length && i < NORMAL_CHARS.length(); i++) {
            map.put(NORMAL_CHARS.charAt(i), new String(Character.toChars(codePoints[i])));
        }
        return map;
    }

    private static Map<Character, String> buildRangeMap(int upperStart, int lowerStart) {
        Map<Character, String> map = new HashMap<>();
        for (int i = 0; i < 26; i++) {
            map.put((char) ('A' + i), new String(Character.toChars(upperStart + i)));
            map.put((char) ('a' + i), new String(Character.toChars(lowerStart + i)));
        }
        return map;
    }

    private static Map<Character, String> buildSmallCapsMap() {
        Map<Character, String> map = new HashMap<>();
        String smallCaps = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
        for (int i = 0; i < 26; i++) {
            char u = (char) ('A' + i);
            char l = (char) ('a' + i);
            String sc = String.valueOf(smallCaps.charAt(i));
            map.put(u, sc);
            map.put(l, sc);
        }
        String cyrCaps = "АБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯ";
        String cyrSmall = "ᴀбвгдᴇжзийᴋлᴍнопрстуфхцчшщъыьэюя";
        for (int i = 0; i < cyrCaps.length() && i < cyrSmall.length(); i++) {
            char u = cyrCaps.charAt(i);
            char l = Character.toLowerCase(u);
            String sc = String.valueOf(cyrSmall.charAt(i));
            map.put(u, sc);
            map.put(l, sc);
        }
        return map;
    }

    private static Map<Character, String> buildInvertedMap() {
        Map<Character, String> map = new HashMap<>();
        String normal = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String upside = "ɐqɔpǝɟɓɥıɾʞןɯuodbɹsʇnʌʍxʎzⱯᗺƆᗡƎℲ⅁HIſʞꞀWNOԀÒᴚS⊥∩ΛMX⅄Z";
        for (int i = 0; i < normal.length(); i++) {
            map.put(normal.charAt(i), String.valueOf(upside.charAt(i)));
        }
        String cyrNorm = "абвгдежзийклмнопрстуфхцчшщъыьэюя";
        String cyrInv = "ɐqvɹɓǝжεиņʞvwnoudɔɯʎфхһҺmmqıqєєюʁ";
        for (int i = 0; i < cyrNorm.length() && i < cyrInv.length(); i++) {
            char c = cyrNorm.charAt(i);
            char cu = Character.toUpperCase(c);
            String inv = String.valueOf(cyrInv.charAt(i));
            map.put(c, inv);
            map.put(cu, inv);
        }
        return map;
    }

    public static TextMorphController getInstance() {
        if (INSTANCE == null) {
            synchronized (TextMorphController.class) {
                if (INSTANCE == null) {
                    INSTANCE = new TextMorphController();
                }
            }
        }
        return INSTANCE;
    }

    private TextMorphController() {
        Context ctx = ApplicationLoader.applicationContext;
        preferences = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return preferences.getBoolean(KEY_ENABLED, false);
    }

    public void setEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_ENABLED, enabled).apply();
    }

    public int getFontStyle() {
        return preferences.getInt(KEY_FONT_STYLE, FONT_NORMAL);
    }

    public void setFontStyle(int style) {
        preferences.edit().putInt(KEY_FONT_STYLE, style).apply();
    }

    public boolean isVaporwave() {
        return preferences.getBoolean(KEY_VAPORWAVE, false);
    }

    public void setVaporwave(boolean val) {
        preferences.edit().putBoolean(KEY_VAPORWAVE, val).apply();
    }

    public boolean isZalgo() {
        return preferences.getBoolean(KEY_ZALGO, false);
    }

    public void setZalgo(boolean val) {
        preferences.edit().putBoolean(KEY_ZALGO, val).apply();
    }

    public boolean isAntiCaps() {
        return preferences.getBoolean(KEY_ANTI_CAPS, false);
    }

    public void setAntiCaps(boolean val) {
        preferences.edit().putBoolean(KEY_ANTI_CAPS, val).apply();
    }

    public int getAntiCapsPct() {
        return preferences.getInt(KEY_ANTI_CAPS_PCT, 70);
    }

    public void setAntiCapsPct(int val) {
        preferences.edit().putInt(KEY_ANTI_CAPS_PCT, val).apply();
    }

    public boolean isAntiCapsCapitalize() {
        return preferences.getBoolean(KEY_ANTI_CAPS_CAPITALIZE, true);
    }

    public void setAntiCapsCapitalize(boolean val) {
        preferences.edit().putBoolean(KEY_ANTI_CAPS_CAPITALIZE, val).apply();
    }

    public boolean isLeetSpeak() {
        return preferences.getBoolean(KEY_LEET_SPEAK, false);
    }

    public void setLeetSpeak(boolean val) {
        preferences.edit().putBoolean(KEY_LEET_SPEAK, val).apply();
    }

    public String getPrefix() {
        return preferences.getString(KEY_PREFIX, "");
    }

    /** Text Morph 1.4.1: настраиваемый символ быстрого обхода (по умолчанию «!!»). */
    public static final String KEY_BYPASS_PREFIX = "quick_bypass_prefix";
    public static final String DEFAULT_BYPASS = "!!";

    public String getBypassPrefix() {
        String v = preferences.getString(KEY_BYPASS_PREFIX, DEFAULT_BYPASS);
        return TextUtils.isEmpty(v) ? DEFAULT_BYPASS : v;
    }

    public void setBypassPrefix(String val) {
        preferences.edit().putString(KEY_BYPASS_PREFIX, TextUtils.isEmpty(val) ? DEFAULT_BYPASS : val).apply();
    }

    /** Text Morph 1.4.1: сохранённый текст предпросмотра. */
    public String getPreviewSampleText() {
        return preferences.getString("preview_sample_text", "");
    }

    public void setPreviewSampleText(String val) {
        preferences.edit().putString("preview_sample_text", val == null ? "" : val).apply();
    }

    public void setPrefix(String val) {
        preferences.edit().putString(KEY_PREFIX, val).apply();
    }

    public String getSuffix() {
        return preferences.getString(KEY_SUFFIX, "");
    }

    public void setSuffix(String val) {
        preferences.edit().putString(KEY_SUFFIX, val).apply();
    }

    public boolean isForceBold() {
        return preferences.getBoolean(KEY_FORCE_BOLD, false);
    }

    public void setForceBold(boolean val) {
        preferences.edit().putBoolean(KEY_FORCE_BOLD, val).apply();
    }

    public boolean isForceItalic() {
        return preferences.getBoolean(KEY_FORCE_ITALIC, false);
    }

    public void setForceItalic(boolean val) {
        preferences.edit().putBoolean(KEY_FORCE_ITALIC, val).apply();
    }

    public boolean isForceBoldItalic() {
        return preferences.getBoolean(KEY_FORCE_BOLD_ITALIC, false);
    }

    public void setForceBoldItalic(boolean val) {
        preferences.edit().putBoolean(KEY_FORCE_BOLD_ITALIC, val).apply();
    }

    public boolean isForceMono() {
        return preferences.getBoolean(KEY_FORCE_MONO, false);
    }

    public void setForceMono(boolean val) {
        preferences.edit().putBoolean(KEY_FORCE_MONO, val).apply();
    }

    public boolean isForceCode() {
        return preferences.getBoolean(KEY_FORCE_CODE, false);
    }

    public void setForceCode(boolean val) {
        preferences.edit().putBoolean(KEY_FORCE_CODE, val).apply();
    }

    public String getCodeLang() {
        return preferences.getString(KEY_CODE_LANG, "");
    }

    public void setCodeLang(String val) {
        preferences.edit().putString(KEY_CODE_LANG, val).apply();
    }

    public boolean isForceQuote() {
        return preferences.getBoolean(KEY_FORCE_QUOTE, false);
    }

    public void setForceQuote(boolean val) {
        preferences.edit().putBoolean(KEY_FORCE_QUOTE, val).apply();
    }

    public boolean isForceStrike() {
        return preferences.getBoolean(KEY_FORCE_STRIKE, false);
    }

    public void setForceStrike(boolean val) {
        preferences.edit().putBoolean(KEY_FORCE_STRIKE, val).apply();
    }

    public boolean isForceSpoiler() {
        return preferences.getBoolean(KEY_FORCE_SPOILER, false);
    }

    public void setForceSpoiler(boolean val) {
        preferences.edit().putBoolean(KEY_FORCE_SPOILER, val).apply();
    }

    public boolean isForceUnderline() {
        return preferences.getBoolean(KEY_FORCE_UNDERLINE, false);
    }

    public void setForceUnderline(boolean val) {
        preferences.edit().putBoolean(KEY_FORCE_UNDERLINE, val).apply();
    }

    public boolean isRandomStyle() {
        return preferences.getBoolean(KEY_RANDOM_STYLE, false);
    }

    public void setRandomStyle(boolean val) {
        preferences.edit().putBoolean(KEY_RANDOM_STYLE, val).apply();
    }

    // ===================== Text Morph 1.4.1 (порт плагина) =====================

    public static final String KEY_WORK_ALL_ACCOUNTS = "work_on_all_accounts";
    public static final String KEY_ALLOWED_ACCOUNTS = "allowed_accounts";
    public static final String KEY_FILTER_PM = "filter_pm";
    public static final String KEY_FILTER_GROUPS = "filter_groups";
    public static final String KEY_FILTER_CHANNELS = "filter_channels";
    public static final String KEY_FILTER_SAVED = "filter_saved";
    public static final String KEY_SKIP_LINKS = "skip_links";
    public static final String KEY_SKIP_MENTIONS = "skip_mentions";
    public static final String KEY_SKIP_COMMANDS = "skip_commands";
    public static final String KEY_SKIP_HASHTAGS = "skip_hashtags";
    public static final String KEY_QUICK_BYPASS = "quick_bypass";
    public static final String KEY_SKIP_EMOJIS = "skip_emojis";
    public static final String KEY_ZALGO_LEVEL = "zalgo_level";
    public static final String KEY_LEET_MAPPINGS = "leet_mappings";
    public static final String POOL_PREFIX = "chaos_pool_";

    /** Ключи модификаторов (как s_* в плагине) + случайный стиль. */
    public static final String S_BOLD = KEY_FORCE_BOLD, S_ITALIC = KEY_FORCE_ITALIC, S_BOLD_ITALIC = KEY_FORCE_BOLD_ITALIC,
            S_MONO = KEY_FORCE_MONO, S_CODE = KEY_FORCE_CODE, S_QUOTE = KEY_FORCE_QUOTE, S_STRIKE = KEY_FORCE_STRIKE,
            S_SPOILER = KEY_FORCE_SPOILER, S_UNDERLINE = KEY_FORCE_UNDERLINE, S_RANDOM = KEY_RANDOM_STYLE;
    public static final String[] STYLE_KEYS = {S_BOLD, S_ITALIC, S_BOLD_ITALIC, S_MONO, S_CODE, S_QUOTE, S_STRIKE, S_SPOILER, S_UNDERLINE};
    /** Пул случайного стиля: bold_italic + порядок STYLE_MAP плагина. */
    public static final String[] POOL_KEYS = {"bold_italic", "bold", "italic", "mono", "code", "quote", "strike", "spoiler", "underline"};
    public static final int[] ANTI_CAPS_THRESHOLDS = {30, 50, 70, 90, 100};
    public static final int[] ZALGO_INTENSITIES = {1, 4, 8, 15};

    public static final int CHAT_SAVED = 0, CHAT_PM = 1, CHAT_GROUP = 2, CHAT_CHANNEL = 3;

    private static final java.util.regex.Pattern MENTION_PATTERN = java.util.regex.Pattern.compile("(^|[\\s\\u00A0\\u2000-\\u200B\\u3000])@[a-zA-Z0-9_]{3,}");
    private static final java.util.regex.Pattern HASHTAG_PATTERN = java.util.regex.Pattern.compile("(^|[\\s\\u00A0\\u2000-\\u200B\\u3000])#[a-zA-Z0-9_\\u0400-\\u04FF]+");
    private static final String ASCII_PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

    public static final String[] DEFAULT_LEET_KEYS = {"A", "E", "I", "O", "S", "T", "B", "G", "Z", "А", "Е", "О", "С", "Т", "З", "Б", "В", "Ч"};
    public static final String[] DEFAULT_LEET_VALUES = {"4", "3", "1", "0", "5", "7", "8", "6", "2", "4", "3", "0", "5", "7", "3", "6", "8", "4"};

    public static boolean defaultFor(String key) {
        if (key == null) return false;
        switch (key) {
            case KEY_WORK_ALL_ACCOUNTS:
            case KEY_FILTER_PM:
            case KEY_FILTER_GROUPS:
            case KEY_FILTER_SAVED:
            case KEY_SKIP_LINKS:
            case KEY_SKIP_MENTIONS:
            case KEY_SKIP_COMMANDS:
            case KEY_QUICK_BYPASS:
            case KEY_SKIP_EMOJIS:
                return true;
            default:
                return key.startsWith(POOL_PREFIX);
        }
    }

    public boolean getBool(String key) {
        return preferences.getBoolean(key, defaultFor(key));
    }

    public void setBool(String key, boolean value) {
        preferences.edit().putBoolean(key, value).apply();
    }

    /** Правила совместимости переключателей стилей (handle_switch плагина). */
    public void applyStyleSwitch(String key, boolean val) {
        SharedPreferences.Editor e = preferences.edit();
        if (S_RANDOM.equals(key)) {
            if (val) {
                for (String k : STYLE_KEYS) e.putBoolean(k, false);
            }
            e.putBoolean(S_RANDOM, val).apply();
            return;
        }
        if (val) {
            e.putBoolean(S_RANDOM, false);
            if (S_CODE.equals(key)) {
                for (String k : STYLE_KEYS) {
                    if (!S_CODE.equals(k)) e.putBoolean(k, false);
                }
            } else {
                e.putBoolean(S_CODE, false);
            }
            if (S_MONO.equals(key)) {
                e.putBoolean(S_BOLD, false).putBoolean(S_ITALIC, false).putBoolean(S_BOLD_ITALIC, false).putBoolean(S_SPOILER, false);
            } else if (S_BOLD.equals(key) || S_ITALIC.equals(key) || S_BOLD_ITALIC.equals(key) || S_SPOILER.equals(key)) {
                e.putBoolean(S_MONO, false);
                if (S_BOLD_ITALIC.equals(key)) {
                    e.putBoolean(S_BOLD, false).putBoolean(S_ITALIC, false);
                } else if (S_BOLD.equals(key) || S_ITALIC.equals(key)) {
                    e.putBoolean(S_BOLD_ITALIC, false);
                }
            }
        }
        e.putBoolean(key, val).apply();
    }

    public boolean getStyle(String key) {
        return preferences.getBoolean(key, false);
    }

    public int getZalgoLevel() {
        return Math.max(0, Math.min(ZALGO_INTENSITIES.length - 1, preferences.getInt(KEY_ZALGO_LEVEL, 1)));
    }

    public void setZalgoLevel(int level) {
        preferences.edit().putInt(KEY_ZALGO_LEVEL, level).apply();
    }

    public int getZalgoIntensity() {
        return ZALGO_INTENSITIES[getZalgoLevel()];
    }

    // ----- Leet Speak: пользовательские замены -----

    public java.util.TreeMap<String, String> getLeetMappings() {
        java.util.TreeMap<String, String> map = new java.util.TreeMap<>();
        String raw = preferences.getString(KEY_LEET_MAPPINGS, null);
        if (raw != null) {
            try {
                org.json.JSONObject o = new org.json.JSONObject(raw);
                java.util.Iterator<String> it = o.keys();
                while (it.hasNext()) {
                    String k = it.next();
                    map.put(k, o.optString(k, ""));
                }
                return map;
            } catch (Exception ignore) {
                map.clear();
            }
        }
        for (int i = 0; i < DEFAULT_LEET_KEYS.length; i++) map.put(DEFAULT_LEET_KEYS[i], DEFAULT_LEET_VALUES[i]);
        return map;
    }

    public void setLeetMappings(java.util.Map<String, String> map) {
        org.json.JSONObject o = new org.json.JSONObject();
        try {
            for (java.util.Map.Entry<String, String> en : map.entrySet()) o.put(en.getKey(), en.getValue());
        } catch (Exception ignore) {
        }
        preferences.edit().putString(KEY_LEET_MAPPINGS, o.toString()).apply();
    }

    public void resetLeetMappings() {
        preferences.edit().remove(KEY_LEET_MAPPINGS).apply();
    }

    // ----- Аккаунты -----

    public java.util.LinkedHashSet<Long> getAllowedAccounts() {
        java.util.LinkedHashSet<Long> res = new java.util.LinkedHashSet<>();
        String raw = preferences.getString(KEY_ALLOWED_ACCOUNTS, "");
        if (!TextUtils.isEmpty(raw)) {
            for (String part : raw.split(",")) {
                try {
                    res.add(Long.parseLong(part.trim()));
                } catch (Exception ignore) {
                }
            }
        }
        return res;
    }

    public void setAllowedAccounts(java.util.Collection<Long> ids) {
        preferences.edit().putString(KEY_ALLOWED_ACCOUNTS, TextUtils.join(",", ids)).apply();
    }

    public boolean isAccountAllowed(int account) {
        if (getBool(KEY_WORK_ALL_ACCOUNTS)) return true;
        long uid = 0;
        try {
            uid = UserConfig.getInstance(account).getClientUserId();
        } catch (Throwable ignore) {
        }
        return uid == 0 || getAllowedAccounts().contains(uid);
    }

    public int getChatType(int account, long dialogId) {
        long self = 0;
        try {
            self = UserConfig.getInstance(account).getClientUserId();
        } catch (Throwable ignore) {
        }
        if (self != 0 && dialogId == self) return CHAT_SAVED;
        if (DialogObject.isEncryptedDialog(dialogId) || dialogId > 0) return CHAT_PM;
        try {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
            if (chat != null && chat.broadcast) return CHAT_CHANNEL;
        } catch (Throwable ignore) {
        }
        return CHAT_GROUP;
    }

    // ----- Отправка -----

    public void applyMorph(int account, SendMessagesHelper.SendMessageParams params) {
        if (params == null || !isEnabled()) {
            return;
        }
        try {
            applyMorphInternal(account, params);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private void applyMorphInternal(int account, SendMessagesHelper.SendMessageParams params) {
        if (!isAccountAllowed(account)) return;
        final long peer = params.peer;
        if (peer != 0) {
            if (isChatExcluded(peer)) return;
            switch (getChatType(account, peer)) {
                case CHAT_SAVED:
                    if (!getBool(KEY_FILTER_SAVED)) return;
                    break;
                case CHAT_PM:
                    if (!getBool(KEY_FILTER_PM)) return;
                    break;
                case CHAT_CHANNEL:
                    if (!getBool(KEY_FILTER_CHANNELS)) return;
                    break;
                default:
                    if (!getBool(KEY_FILTER_GROUPS)) return;
                    break;
            }
        }
        final boolean isCaption;
        final String raw;
        if (!TextUtils.isEmpty(params.message)) {
            raw = params.message;
            isCaption = false;
        } else if (!TextUtils.isEmpty(params.caption)) {
            raw = params.caption;
            isCaption = true;
        } else {
            return;
        }

        // Быстрый байпас: символ обхода в начале — отправить чистый текст без стилей
        final String bypass = getBypassPrefix();
        if (getBool(KEY_QUICK_BYPASS) && raw.startsWith(bypass) && !TextUtils.isEmpty(lstrip(raw.substring(bypass.length())).trim())) {
            String clean = lstrip(raw.substring(bypass.length()));
            params.entities = shiftEntities(params.entities, raw.length() - clean.length(), clean.length());
            if (isCaption) {
                params.caption = clean;
            } else {
                params.message = clean;
            }
            return;
        }
        if (getBool(KEY_SKIP_COMMANDS) && raw.startsWith("/")) return;
        if (getBool(KEY_SKIP_LINKS)) {
            String l = raw.toLowerCase(java.util.Locale.ROOT);
            if (l.contains("http://") || l.contains("https://") || l.contains("t.me/") || l.contains("tg://")) return;
        }
        if (getBool(KEY_SKIP_MENTIONS) && raw.indexOf('@') >= 0 && MENTION_PATTERN.matcher(raw).find()) return;
        if (getBool(KEY_SKIP_HASHTAGS) && raw.indexOf('#') >= 0 && HASHTAG_PATTERN.matcher(raw).find()) return;
        if (getBool(KEY_SKIP_EMOJIS) && isEmojiOnly(raw)) return;

        int limit = 0;
        try {
            MessagesController mc = MessagesController.getInstance(account);
            limit = isCaption ? mc.getCaptionMaxLengthLimit() : mc.maxMessageLength;
        } catch (Throwable ignore) {
        }
        Result r = process(raw, params.entities, true);
        if (r != null && limit > 0 && r.text.length() > limit && raw.length() <= limit) {
            // Zalgo раздувает текст сверх лимита Telegram — отправляем без него
            r = process(raw, params.entities, false);
            if (r != null && r.text.length() > limit) r = null;
        }
        if (r == null) return;
        if (isCaption) {
            params.caption = r.text;
        } else {
            params.message = r.text;
        }
        params.entities = r.buildEntities();
    }

    /** Результат обработки: текст + сущности (старые сдвигаются по карте позиций). */
    public static final class Result {
        public String text;
        private java.util.ArrayList<TLRPC.MessageEntity> source;
        private int[] offsets;
        private int[] lengths;
        private final java.util.ArrayList<TLRPC.MessageEntity> added = new java.util.ArrayList<>();

        public java.util.ArrayList<TLRPC.MessageEntity> buildEntities() {
            java.util.ArrayList<TLRPC.MessageEntity> out = new java.util.ArrayList<>();
            if (source != null) {
                for (int i = 0; i < source.size(); i++) {
                    TLRPC.MessageEntity e = source.get(i);
                    if (e == null || lengths[i] <= 0) continue;
                    e.offset = offsets[i];
                    e.length = lengths[i];
                    out.add(e);
                }
            }
            out.addAll(added);
            return out;
        }
    }

    private interface CodePointFn {
        String apply(int cp);
    }

    /** Текст + карта «исходный индекс → текущий индекс» + защищённые символы (кастомные эмодзи). */
    private static final class Buf {
        String text;
        final int[] map;
        java.util.BitSet keep = new java.util.BitSet();

        Buf(String text) {
            this.text = text;
            map = new int[text.length() + 1];
            for (int i = 0; i < map.length; i++) map[i] = i;
        }

        void stage(CodePointFn fn) {
            final String s = text;
            final int len = s.length();
            final StringBuilder out = new StringBuilder(len + 16);
            final int[] pos = new int[len + 1];
            final java.util.BitSet keepOut = new java.util.BitSet();
            int i = 0;
            while (i < len) {
                int cp = s.codePointAt(i);
                int n = Character.charCount(cp);
                int start = out.length();
                for (int k = 0; k < n && i + k < len; k++) pos[i + k] = start;
                boolean kept = keep.get(i);
                String rep = kept ? null : fn.apply(cp);
                if (rep == null) {
                    out.appendCodePoint(cp);
                } else {
                    out.append(rep);
                }
                if (kept) keepOut.set(start, out.length());
                i += n;
            }
            pos[len] = out.length();
            for (int k = 0; k < map.length; k++) map[k] = pos[Math.min(map[k], len)];
            text = out.toString();
            keep = keepOut;
        }

        void wrap(String prefix, String suffix) {
            final int pl = prefix.length();
            text = prefix + text + suffix;
            for (int k = 0; k < map.length; k++) map[k] += pl;
            java.util.BitSet nk = new java.util.BitSet();
            for (int b = keep.nextSetBit(0); b >= 0; b = keep.nextSetBit(b + 1)) nk.set(b + pl);
            keep = nk;
        }
    }

    /** Предпросмотр в настройках: та же обработка, без фильтров. */
    public Result preview(String text) {
        return process(text, null, true);
    }

    /** _process_text плагина. Возвращает null, если ничего не изменилось. */
    public Result process(String raw, java.util.ArrayList<TLRPC.MessageEntity> entities, boolean allowZalgo) {
        if (raw == null || raw.isEmpty()) return null;
        boolean modified = false;

        // 1. Режим кода
        boolean isCode = isForceCode() || isForceMono();
        String stripped = strip(raw);
        if (stripped.startsWith("`")) isCode = true;
        if (!isCode && entities != null) {
            for (TLRPC.MessageEntity e : entities) {
                if (e instanceof TLRPC.TL_messageEntityPre || e instanceof TLRPC.TL_messageEntityCode) {
                    isCode = true;
                    break;
                }
            }
        }
        if (!isCode && !stripped.isEmpty()) {
            int nl = stripped.indexOf('\n');
            String firstLine = strip(nl >= 0 ? stripped.substring(0, nl) : stripped);
            int k = 0;
            while (k < firstLine.length() && (Character.isLetterOrDigit(firstLine.charAt(k)) || firstLine.charAt(k) == '_')) k++;
            String fw = firstLine.substring(0, k);
            if (!fw.isEmpty()) {
                if (CODE_KEYWORDS.contains(fw.toLowerCase(java.util.Locale.ROOT))) {
                    isCode = true;
                } else {
                    String rest = lstrip(firstLine.substring(k));
                    // «Foo(», «x =», «obj.method» — но не обычная точка в конце предложения
                    if (rest.startsWith("(") || rest.startsWith("=")
                            || (rest.startsWith(".") && rest.length() > 1 && (Character.isLetter(rest.charAt(1)) || rest.charAt(1) == '_'))) {
                        isCode = true;
                    }
                }
            }
        }

        // 2. Исправление автозаглавной в коде
        String text = raw;
        if (isCode) {
            String fixed = fixCodeAutocap(text);
            if (!fixed.equals(text)) {
                text = fixed;
                modified = true;
            }
        }
        final Buf b = new Buf(text);
        if (entities != null) {
            for (TLRPC.MessageEntity e : entities) {
                if (e instanceof TLRPC.TL_messageEntityCustomEmoji) {
                    int s = Math.max(0, e.offset), en = Math.min(text.length(), e.offset + e.length);
                    if (en > s) b.keep.set(s, en);
                }
            }
        }

        // 3. Префикс и суффикс
        String prefix = getPrefix(), suffix = getSuffix();
        if (prefix == null) prefix = "";
        if (suffix == null) suffix = "";
        if (!prefix.isEmpty() || !suffix.isEmpty()) {
            b.wrap(prefix, suffix);
            modified = true;
        }

        // 4. Анти-капс
        if (!isCode && isAntiCaps()) {
            int alpha = 0, upper = 0;
            for (int i = 0; i < b.text.length(); i++) {
                char ch = b.text.charAt(i);
                if (Character.isLetter(ch)) {
                    alpha++;
                    if (Character.isUpperCase(ch)) upper++;
                }
            }
            if (alpha > 0 && upper * 100.0 / alpha >= getAntiCapsPct()) {
                char[] arr = b.text.toCharArray();
                for (int i = 0; i < arr.length; i++) {
                    if (!b.keep.get(i)) arr[i] = Character.toLowerCase(arr[i]);
                }
                if (isAntiCapsCapitalize()) {
                    for (int i = 0; i < arr.length; i++) {
                        if (!b.keep.get(i) && Character.isLetter(arr[i])) {
                            arr[i] = Character.toUpperCase(arr[i]);
                            break;
                        }
                    }
                }
                b.text = new String(arr);
                modified = true;
            }
        }

        // 5. Vaporwave
        if (isVaporwave()) {
            b.stage(cp -> {
                if (cp >= 0x21 && cp <= 0x7E) return String.valueOf((char) (cp + 0xFEE0));
                if (cp == 0x20) return "\u3000";
                return null;
            });
            modified = true;
        }

        // 6. Leet Speak (пользовательские замены)
        if (isLeetSpeak()) {
            final HashMap<Integer, String> trans = new HashMap<>();
            for (Map.Entry<String, String> en : getLeetMappings().entrySet()) {
                String k = en.getKey(), v = en.getValue();
                if (TextUtils.isEmpty(k) || TextUtils.isEmpty(v)) continue;
                String lo = k.toLowerCase(), up = k.toUpperCase();
                if (lo.codePointCount(0, lo.length()) == 1) trans.put(lo.codePointAt(0), v.toLowerCase());
                if (up.codePointCount(0, up.length()) == 1) trans.put(up.codePointAt(0), v.toUpperCase());
            }
            b.stage(trans::get);
            modified = true;
        }

        // 7. Шрифт (в TsundereGram выбор шрифта символов убран — стиль всегда обычный)
        int fontStyle = getFontStyle();
        if (fontStyle > 0 && FONT_MAPS.containsKey(fontStyle)) {
            final Map<Character, String> fmap = FONT_MAPS.get(fontStyle);
            b.stage(cp -> cp < 0x10000 && fmap != null ? fmap.get((char) cp) : null);
            modified = true;
        }

        // 8. Zalgo
        if (allowZalgo && isZalgo()) {
            final int intensity = getZalgoIntensity();
            b.stage(cp -> {
                if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) return null;
                StringBuilder sb = new StringBuilder();
                sb.appendCodePoint(cp);
                int marks = 1 + random.nextInt(intensity);
                for (int m = 0; m < marks; m++) sb.append((char) (0x0300 + random.nextInt(0x6F)));
                return sb.toString();
            });
            modified = true;
        }

        // 9. Модификаторы (сущности)
        final Result r = new Result();
        final int len = b.text.length();
        if (isRandomStyle()) {
            java.util.ArrayList<String[]> pool = new java.util.ArrayList<>();
            for (String k : new String[]{"bold", "italic", "mono", "code", "quote", "strike", "spoiler", "underline"}) {
                if (getBool(POOL_PREFIX + k)) pool.add(new String[]{k});
            }
            if (getBool(POOL_PREFIX + "bold_italic")) pool.add(new String[]{"bold", "italic"});
            String[] choice = pool.isEmpty() ? new String[]{"bold"} : pool.get(random.nextInt(pool.size()));
            for (String k : choice) addStyle(r.added, k, len);
            modified = true;
        } else {
            java.util.ArrayList<String> styles = new java.util.ArrayList<>();
            if (isForceCode()) {
                styles.add("code");
            } else if (isForceMono()) {
                styles.add("mono");
                if (isForceQuote()) styles.add("quote");
                if (isForceStrike()) styles.add("strike");
                if (isForceUnderline()) styles.add("underline");
            } else {
                if (isForceBoldItalic()) {
                    styles.add("bold");
                    styles.add("italic");
                } else {
                    if (isForceBold()) styles.add("bold");
                    if (isForceItalic()) styles.add("italic");
                }
                if (isForceQuote()) styles.add("quote");
                if (isForceStrike()) styles.add("strike");
                if (isForceSpoiler()) styles.add("spoiler");
                if (isForceUnderline()) styles.add("underline");
            }
            if (!styles.isEmpty()) {
                for (String k : styles) addStyle(r.added, k, len);
                modified = true;
            }
        }
        if (!modified) return null;

        r.text = b.text;
        if (entities != null) {
            final int origLen = raw.length();
            r.source = entities;
            r.offsets = new int[entities.size()];
            r.lengths = new int[entities.size()];
            for (int i = 0; i < entities.size(); i++) {
                TLRPC.MessageEntity e = entities.get(i);
                if (e == null) continue;
                int s = Math.max(0, Math.min(origLen, e.offset));
                int en = Math.max(0, Math.min(origLen, e.offset + e.length));
                r.offsets[i] = b.map[s];
                r.lengths[i] = b.map[en] - b.map[s];
            }
        }
        return r;
    }

    private void addStyle(java.util.ArrayList<TLRPC.MessageEntity> list, String key, int len) {
        TLRPC.MessageEntity entity;
        switch (key) {
            case "bold":
                entity = new TLRPC.TL_messageEntityBold();
                break;
            case "italic":
                entity = new TLRPC.TL_messageEntityItalic();
                break;
            case "mono":
                entity = new TLRPC.TL_messageEntityCode();
                break;
            case "code": {
                TLRPC.TL_messageEntityPre pre = new TLRPC.TL_messageEntityPre();
                String lang = getCodeLang();
                pre.language = lang != null ? lang.trim() : "";
                entity = pre;
                break;
            }
            case "quote":
                entity = new TLRPC.TL_messageEntityBlockquote();
                break;
            case "strike":
                entity = new TLRPC.TL_messageEntityStrike();
                break;
            case "spoiler":
                entity = new TLRPC.TL_messageEntitySpoiler();
                break;
            case "underline":
                entity = new TLRPC.TL_messageEntityUnderline();
                break;
            default:
                return;
        }
        entity.offset = 0;
        entity.length = len;
        list.add(entity);
    }

    private static java.util.ArrayList<TLRPC.MessageEntity> shiftEntities(java.util.ArrayList<TLRPC.MessageEntity> list, int removed, int newLen) {
        if (list == null) return null;
        java.util.ArrayList<TLRPC.MessageEntity> out = new java.util.ArrayList<>();
        for (TLRPC.MessageEntity e : list) {
            if (e == null) continue;
            int s = Math.max(0, e.offset - removed);
            int en = Math.min(newLen, e.offset + e.length - removed);
            if (en <= s) continue;
            e.offset = s;
            e.length = en - s;
            out.add(e);
        }
        return out;
    }

    private static boolean isSpace(int cp) {
        return Character.isWhitespace(cp) || Character.isSpaceChar(cp);
    }

    private static String lstrip(String s) {
        int i = 0;
        while (i < s.length() && isSpace(s.charAt(i))) i++;
        return s.substring(i);
    }

    private static String strip(String s) {
        int st = 0, en = s.length();
        while (st < en && isSpace(s.charAt(st))) st++;
        while (en > st && isSpace(s.charAt(en - 1))) en--;
        return s.substring(st, en);
    }

    private static boolean isEmojiOnly(String raw) {
        boolean any = false;
        for (int i = 0; i < raw.length(); ) {
            int cp = raw.codePointAt(i);
            i += Character.charCount(cp);
            if (isSpace(cp) || (cp < 128 && ASCII_PUNCTUATION.indexOf(cp) >= 0)) continue;
            if (Character.isLetterOrDigit(cp)) return false;
            any = true;
        }
        return any;
    }

    private static final java.util.Set<String> CODE_KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
        "print", "def", "import", "from", "class", "return", "for", "while", "if", "elif", "else",
        "try", "except", "finally", "with", "as", "pass", "break", "continue", "async", "await",
        "const", "let", "var", "function", "console", "echo", "cat", "grep", "cd", "ls", "git",
        "docker", "npm", "pip", "python", "node", "curl", "select", "insert", "update", "delete",
        "public", "private", "protected", "void", "int", "float", "double", "bool", "string",
        "main", "struct", "enum", "type", "package", "val", "fun"
    ));

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) return false;
        }
        return true;
    }

    private static boolean isUpperWord(String s) {
        boolean cased = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isLowerCase(c)) return false;
            if (Character.isUpperCase(c)) cased = true;
        }
        return cased;
    }

    private boolean shouldLowercaseFirstWord(String word, String restOfLine) {
        if (word == null || word.isEmpty() || !Character.isUpperCase(word.charAt(0))) {
            return false;
        }
        // аббревиатуры (URL, JSON, API) и не-ASCII слова («Привет») не трогаем
        if (word.length() >= 2 && isUpperWord(word)) {
            return false;
        }
        if (!isAscii(word)) {
            return false;
        }
        if (CODE_KEYWORDS.contains(word.toLowerCase(java.util.Locale.ROOT))) {
            return true;
        }
        String rest = lstrip(restOfLine);
        if (rest.startsWith("(") || rest.startsWith(".")) {
            return true;
        }
        if (rest.startsWith("=") || rest.startsWith("+=") || rest.startsWith("-=")) {
            return true;
        }
        return word.length() == 1 && (rest.startsWith("in ") || rest.startsWith(","));
    }

    private String fixFirstWord(String line) {
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (Character.isLetter(ch)) {
                int wordEnd = i + 1;
                while (wordEnd < line.length() && (Character.isLetterOrDigit(line.charAt(wordEnd)) || line.charAt(wordEnd) == '_')) {
                    wordEnd++;
                }
                if (shouldLowercaseFirstWord(line.substring(i, wordEnd), line.substring(wordEnd))) {
                    return line.substring(0, i) + Character.toLowerCase(ch) + line.substring(i + 1);
                }
                break;
            }
        }
        return line;
    }

    public String fixCodeAutocap(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        if (text.startsWith("`") && !text.startsWith("```")) {
            String inner = text.substring(1);
            int end = inner.indexOf('`');
            if (end != -1) {
                return "`" + fixFirstWord(inner.substring(0, end)) + inner.substring(end);
            }
            return "`" + fixFirstWord(inner);
        }
        String[] lines = text.split("\n", -1);
        StringBuilder sb = new StringBuilder(text.length());
        for (int l = 0; l < lines.length; l++) {
            if (l > 0) sb.append('\n');
            String line = lines[l];
            sb.append(line.trim().startsWith("```") ? line : fixFirstWord(line));
        }
        return sb.toString();
    }
}
