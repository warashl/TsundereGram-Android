package app.exteraless.morph;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SendMessagesHelper;

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
    private static final String KEY_ZALGO_INTENSITY = "zalgo_intensity";
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

    private static final Map<Character, Character> DEFAULT_LEET = new HashMap<>();
    static {
        // Latin
        DEFAULT_LEET.put('A', '4'); DEFAULT_LEET.put('a', '4');
        DEFAULT_LEET.put('E', '3'); DEFAULT_LEET.put('e', '3');
        DEFAULT_LEET.put('I', '1'); DEFAULT_LEET.put('i', '1');
        DEFAULT_LEET.put('O', '0'); DEFAULT_LEET.put('o', '0');
        DEFAULT_LEET.put('S', '5'); DEFAULT_LEET.put('s', '5');
        DEFAULT_LEET.put('T', '7'); DEFAULT_LEET.put('t', '7');
        DEFAULT_LEET.put('B', '8'); DEFAULT_LEET.put('b', '8');
        DEFAULT_LEET.put('G', '6'); DEFAULT_LEET.put('g', '6');
        DEFAULT_LEET.put('Z', '2'); DEFAULT_LEET.put('z', '2');
        // Cyrillic
        DEFAULT_LEET.put('А', '4'); DEFAULT_LEET.put('а', '4');
        DEFAULT_LEET.put('Е', '3'); DEFAULT_LEET.put('е', '3');
        DEFAULT_LEET.put('О', '0'); DEFAULT_LEET.put('о', '0');
        DEFAULT_LEET.put('С', '5'); DEFAULT_LEET.put('с', '5');
        DEFAULT_LEET.put('Т', '7'); DEFAULT_LEET.put('т', '7');
        DEFAULT_LEET.put('З', '3'); DEFAULT_LEET.put('з', '3');
        DEFAULT_LEET.put('Б', '6'); DEFAULT_LEET.put('б', '6');
        DEFAULT_LEET.put('В', '8'); DEFAULT_LEET.put('в', '8');
        DEFAULT_LEET.put('Ч', '4'); DEFAULT_LEET.put('ч', '4');
    }

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

    public int getZalgoIntensity() {
        return preferences.getInt(KEY_ZALGO_INTENSITY, 2);
    }

    public void setZalgoIntensity(int val) {
        preferences.edit().putInt(KEY_ZALGO_INTENSITY, val).apply();
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

    public String transformTextPreview(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return applyTransformations(text, true);
    }

    public String transformText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        if (!isEnabled()) {
            return text;
        }

        return applyTransformations(text, false);
    }

    private String applyTransformations(String text, boolean isPreview) {
        String result = text;

        // Anti-caps
        if (isAntiCaps()) {
            int upperCount = 0;
            int letterCount = 0;
            for (int i = 0; i < result.length(); i++) {
                char ch = result.charAt(i);
                if (Character.isLetter(ch)) {
                    letterCount++;
                    if (Character.isUpperCase(ch)) {
                        upperCount++;
                    }
                }
            }
            int pct = getAntiCapsPct();
            if (letterCount > 3 && (upperCount * 100 / letterCount) >= pct) {
                result = result.toLowerCase();
                if (isAntiCapsCapitalize() && !result.isEmpty()) {
                    result = Character.toUpperCase(result.charAt(0)) + (result.length() > 1 ? result.substring(1) : "");
                }
            }
        }

        // Leet Speak
        if (isLeetSpeak()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < result.length(); i++) {
                char ch = result.charAt(i);
                Character rep = DEFAULT_LEET.get(ch);
                if (rep != null) {
                    sb.append(rep);
                } else {
                    sb.append(ch);
                }
            }
            result = sb.toString();
        }

        // Font Style
        int style = getFontStyle();
        if (!isRandomStyle() && style > 0 && FONT_MAPS.containsKey(style)) {
            Map<Character, String> map = FONT_MAPS.get(style);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < result.length(); i++) {
                char ch = result.charAt(i);
                if (map != null && map.containsKey(ch)) {
                    sb.append(map.get(ch));
                } else {
                    sb.append(ch);
                }
            }
            if (style == FONT_INVERTED) {
                result = sb.reverse().toString();
            } else {
                result = sb.toString();
            }
        }

        // Vaporwave
        if (isVaporwave()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < result.length(); i++) {
                char ch = result.charAt(i);
                if (ch >= 0x21 && ch <= 0x7E) {
                    sb.append((char) (ch + 0xFEE0));
                } else if (ch == ' ') {
                    sb.append('\u3000');
                } else {
                    sb.append(ch);
                }
            }
            result = sb.toString();
        }

        // Zalgo
        if (isZalgo()) {
            int intensity = getZalgoIntensity();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < result.length(); i++) {
                char ch = result.charAt(i);
                sb.append(ch);
                if (!Character.isWhitespace(ch)) {
                    int marks = random.nextInt(intensity + 1);
                    for (int m = 0; m < marks; m++) {
                        sb.append((char) (0x0300 + random.nextInt(0x036F - 0x0300 + 1)));
                    }
                }
            }
            result = sb.toString();
        }

        // Prefix & Suffix
        String prefix = getPrefix();
        String suffix = getSuffix();
        if (prefix != null && !prefix.isEmpty()) {
            result = prefix + result;
        }
        if (suffix != null && !suffix.isEmpty()) {
            result = result + suffix;
        }

        return result;
    }

    private static void addEntity(java.util.ArrayList<org.telegram.tgnet.TLRPC.MessageEntity> list, org.telegram.tgnet.TLRPC.MessageEntity entity, int len) {
        entity.offset = 0;
        entity.length = len;
        list.add(entity);
    }

    private static final java.util.Set<String> CODE_KEYWORDS = new java.util.HashSet<>(java.util.Arrays.asList(
        "print", "def", "import", "from", "class", "return", "for", "while", "if", "elif", "else",
        "try", "except", "finally", "with", "as", "pass", "break", "continue", "async", "await",
        "const", "let", "var", "function", "console", "echo", "cat", "grep", "cd", "ls", "git",
        "docker", "npm", "pip", "python", "node", "curl", "select", "insert", "update", "delete",
        "public", "private", "protected", "void", "int", "float", "double", "bool", "string",
        "main", "struct", "enum", "type", "package", "val", "fun"
    ));

    private boolean shouldLowercaseFirstWord(String word, String restOfLine) {
        if (word == null || word.isEmpty() || !Character.isUpperCase(word.charAt(0))) {
            return false;
        }
        if (word.length() >= 2 && word.equals(word.toUpperCase())) {
            return false;
        }
        String wLower = word.toLowerCase();
        if (CODE_KEYWORDS.contains(wLower)) {
            return true;
        }
        String rest = restOfLine.trim();
        if (rest.startsWith("(") || rest.startsWith(".")) {
            return true;
        }
        if (rest.startsWith("=") || rest.startsWith("+=") || rest.startsWith("-=")) {
            return true;
        }
        if (word.length() == 1 && (rest.startsWith("in ") || rest.startsWith(","))) {
            return true;
        }
        return false;
    }

    public String fixCodeAutocap(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String[] lines = text.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int l = 0; l < lines.length; l++) {
            String line = lines[l];
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                sb.append(line);
                if (l < lines.length - 1) sb.append("\n");
                continue;
            }
            String fixedLine = line;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (Character.isLetter(ch)) {
                    int wordEnd = i + 1;
                    while (wordEnd < line.length() && (Character.isLetterOrDigit(line.charAt(wordEnd)) || line.charAt(wordEnd) == '_')) {
                        wordEnd++;
                    }
                    String word = line.substring(i, wordEnd);
                    String rest = line.substring(wordEnd);
                    if (shouldLowercaseFirstWord(word, rest)) {
                        fixedLine = line.substring(0, i) + Character.toLowerCase(ch) + line.substring(i + 1);
                    }
                    break;
                }
            }
            sb.append(fixedLine);
            if (l < lines.length - 1) sb.append("\n");
        }
        return sb.toString();
    }

    public void applyMorph(SendMessagesHelper.SendMessageParams params) {
        if (params == null || !isEnabled()) {
            return;
        }
        if (params.peer != 0 && isChatExcluded(params.peer)) {
            return;
        }
        String initial = params.message != null ? params.message : params.caption;
        if (initial == null || initial.isEmpty() || initial.startsWith("/")) {
            return;
        }

        boolean isCode = isForceCode() || isForceMono();
        if (params.message != null && !params.message.isEmpty()) {
            if (isCode) {
                params.message = fixCodeAutocap(params.message);
            }
            params.message = transformText(params.message);
        }
        if (params.caption != null && !params.caption.isEmpty()) {
            if (isCode) {
                params.caption = fixCodeAutocap(params.caption);
            }
            params.caption = transformText(params.caption);
        }

        String raw = params.message != null ? params.message : params.caption;
        if (raw != null && !raw.isEmpty()) {
            if (params.entities == null) {
                params.entities = new java.util.ArrayList<>();
            }
            int len = raw.length();

            if (isRandomStyle()) {
                java.util.List<Integer> styles = new java.util.ArrayList<>();
                styles.add(1); // bold
                styles.add(2); // italic
                styles.add(3); // bold_italic
                styles.add(4); // mono
                styles.add(5); // quote
                styles.add(6); // strike
                styles.add(7); // spoiler
                styles.add(8); // underline
                styles.add(9); // code block
                int choice = styles.get(random.nextInt(styles.size()));
                switch (choice) {
                    case 1:
                        addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityBold(), len);
                        break;
                    case 2:
                        addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityItalic(), len);
                        break;
                    case 3:
                        addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityBold(), len);
                        addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityItalic(), len);
                        break;
                    case 4:
                        params.entities.clear();
                        addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityCode(), len);
                        break;
                    case 5:
                        addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityBlockquote(), len);
                        break;
                    case 6:
                        addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityStrike(), len);
                        break;
                    case 7:
                        addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntitySpoiler(), len);
                        break;
                    case 8:
                        addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityUnderline(), len);
                        break;
                    case 9:
                        params.entities.clear();
                        org.telegram.tgnet.TLRPC.TL_messageEntityPre pre = new org.telegram.tgnet.TLRPC.TL_messageEntityPre();
                        String lang = getCodeLang();
                        pre.language = lang != null ? lang : "";
                        addEntity(params.entities, pre, len);
                        break;
                }
            } else if (isForceCode()) {
                params.entities.clear();
                org.telegram.tgnet.TLRPC.TL_messageEntityPre pre = new org.telegram.tgnet.TLRPC.TL_messageEntityPre();
                String lang = getCodeLang();
                pre.language = lang != null ? lang : "";
                addEntity(params.entities, pre, len);
            } else if (isForceMono()) {
                params.entities.clear();
                addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityCode(), len);
            } else {
                if (isForceBold()) {
                    addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityBold(), len);
                }
                if (isForceItalic()) {
                    addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityItalic(), len);
                }
                if (isForceBoldItalic()) {
                    addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityBold(), len);
                    addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityItalic(), len);
                }
                if (isForceQuote()) {
                    addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityBlockquote(), len);
                }
                if (isForceStrike()) {
                    addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityStrike(), len);
                }
                if (isForceSpoiler()) {
                    addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntitySpoiler(), len);
                }
                if (isForceUnderline()) {
                    addEntity(params.entities, new org.telegram.tgnet.TLRPC.TL_messageEntityUnderline(), len);
                }
            }
        }
    }
}
