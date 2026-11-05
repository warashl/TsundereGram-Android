package org.telegram.tsunderestyle.theme;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.provider.OpenableColumns;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tsunderestyle.TsundereStyleConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * Fonts for headings/names, message text and interface text: bundled OFL fonts
 * (assets/fonts/tsunderestyle, licenses next to them) and TTF/OTF files the user imported
 * (stored in the app's private files as "file:&lt;name&gt;"). Telegram caches its paints, so a change
 * applies after an app restart.
 */
public class TsundereStyleFonts {

    public static final String SYSTEM = "system";
    public static final String FILE_PREFIX = "file:";
    private static final String[] BUILT_IN = {SYSTEM, "manrope", "nunito", "comfortaa", "lora"};
    private static final int[] BUILT_IN_TITLES = {R.string.OG_FontSystem, R.string.OG_FontManrope, R.string.OG_FontNunito, R.string.OG_FontComfortaa, R.string.OG_FontLora};

    /** Шрифты Google Fonts (SIL OFL) с кириллицей: скачиваются при первом выборе. {id, название, путь в google/fonts, variable}. */
    private static final String[][] DOWNLOADABLE = {
            {"dl_montserrat", "Montserrat", "ofl/montserrat/Montserrat%5Bwght%5D.ttf", "1"},
            {"dl_rubik", "Rubik", "ofl/rubik/Rubik%5Bwght%5D.ttf", "1"},
            {"dl_onest", "Onest", "ofl/onest/Onest%5Bwght%5D.ttf", "1"},
            {"dl_golos", "Golos Text", "ofl/golostext/GolosText%5Bwght%5D.ttf", "1"},
            {"dl_jost", "Jost", "ofl/jost/Jost%5Bwght%5D.ttf", "1"},
            {"dl_raleway", "Raleway", "ofl/raleway/Raleway%5Bwght%5D.ttf", "1"},
            {"dl_exo2", "Exo 2", "ofl/exo2/Exo2%5Bwght%5D.ttf", "1"},
            {"dl_unbounded", "Unbounded", "ofl/unbounded/Unbounded%5Bwght%5D.ttf", "1"},
            {"dl_playfair", "Playfair Display", "ofl/playfairdisplay/PlayfairDisplay%5Bwght%5D.ttf", "1"},
            {"dl_jetbrains", "JetBrains Mono", "ofl/jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf", "1"},
            {"dl_caveat", "Caveat", "ofl/caveat/Caveat%5Bwght%5D.ttf", "1"},
            {"dl_marck", "Marck Script", "ofl/marckscript/MarckScript-Regular.ttf", "0"},
            {"dl_neucha", "Neucha", "ofl/neucha/Neucha.ttf", "0"},
            {"dl_lobster", "Lobster", "ofl/lobster/Lobster-Regular.ttf", "0"},
            {"dl_pacifico", "Pacifico", "ofl/pacifico/Pacifico-Regular.ttf", "0"},
            {"dl_pressstart", "Press Start 2P", "ofl/pressstart2p/PressStart2P-Regular.ttf", "0"},
    };
    private static final String[] DOWNLOAD_MIRRORS = {
            "https://raw.githubusercontent.com/google/fonts/main/",
            "https://cdn.jsdelivr.net/gh/google/fonts@main/",
    };

    public static final String TARGET_HEADINGS = "font_headings";
    public static final String TARGET_MESSAGES = "font_messages";
    public static final String TARGET_INTERFACE = "font_interface";

    private static final int HEADING_WEIGHT = 650;
    private static final long MAX_FONT_BYTES = 15L * 1024 * 1024;
    private static final HashMap<String, Typeface> cache = new HashMap<>();
    /** TsundereGram: какой шрифт (id) стоит за каждым созданным Typeface — чтобы брать жирный/курсив из того же шрифта. */
    private static final HashMap<Typeface, String> owners = new HashMap<>();

    public static String get(String target) {
        return TsundereStyleConfig.getString(target, SYSTEM);
    }

    public static void set(String target, String font) {
        TsundereStyleConfig.putString(target, font);
        regularResolved = false;
    }

    private static volatile Typeface regularCached;
    private static volatile boolean regularResolved;

    /** Built-in fonts followed by imported ones. */
    public static List<String> allFonts() {
        final ArrayList<String> list = new ArrayList<>(Arrays.asList(BUILT_IN));
        for (String[] d : DOWNLOADABLE) {
            list.add(d[0]);
        }
        list.addAll(customFonts());
        return list;
    }

    public static List<String> customFonts() {
        final ArrayList<String> list = new ArrayList<>();
        final File[] files = customDir().listFiles();
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                list.add(FILE_PREFIX + f.getName());
            }
        }
        return list;
    }

    public static String title(Context context, String font) {
        for (int i = 0; i < BUILT_IN.length; i++) {
            if (BUILT_IN[i].equals(font)) {
                return context.getString(BUILT_IN_TITLES[i]);
            }
        }
        final String[] dl = downloadable(font);
        if (dl != null) {
            return dl[1];
        }
        if (font != null && font.startsWith(FILE_PREFIX)) {
            final String name = font.substring(FILE_PREFIX.length());
            final int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }
        return context.getString(R.string.OG_FontSystem);
    }

    /** Font for bold text (headings, names), or null to keep Telegram's own. */
    public static Typeface headings() {
        return load(get(TARGET_HEADINGS), true);
    }

    /** Font for message text, or null for the system font. */
    public static Typeface messages() {
        return load(get(TARGET_MESSAGES), false);
    }

    /** Font for regular interface text such as chat list previews, or null for the system font. */
    public static Typeface regular() {
        // вызывается часто (AndroidUtilities.regular()), поэтому держим результат под рукой
        if (regularResolved) {
            return regularCached;
        }
        final String font = get(TARGET_INTERFACE);
        final Typeface typeface = load(font, false);
        regularCached = typeface;
        regularResolved = typeface != null || downloadable(font) == null;
        return typeface;
    }

    /**
     * TsundereGram: замена встроенных Roboto, которые код Telegram берёт напрямую
     * (AndroidUtilities.getTypeface("fonts/rmedium.ttf") и т.п.). null — оставить как есть.
     */
    public static Typeface substituteAsset(String assetPath) {
        if (assetPath == null) {
            return null;
        }
        switch (assetPath) {
            case "fonts/rregular.ttf":
                return regular();
            case "fonts/rmedium.ttf":
            case "fonts/rextrabold.ttf":
                return headings();
            case "fonts/ritalic.ttf": {
                final Typeface r = regular();
                return r != null ? styledFor(r, false, true) : null;
            }
            case "fonts/rmediumitalic.ttf": {
                final Typeface h = headings();
                return h != null ? styledFor(h, true, true) : null;
            }
            default:
                return null;
        }
    }

    /** TsundereGram: шрифт текста сообщений (или интерфейса) для краски, которую Telegram оставляет без шрифта. */
    public static void applyMessageFont(android.graphics.Paint paint) {
        if (paint == null) {
            return;
        }
        try {
            Typeface typeface = messages();
            if (typeface == null) {
                typeface = regular();
            }
            if (typeface != null) {
                paint.setTypeface(typeface);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public static Typeface preview(String font, boolean heading) {
        return load(font, heading);
    }

    /**
     * Makes the interface font the default font of the whole app, including plain Android text views
     * that Telegram never assigns a typeface to. Android has no public API for this, so the defaults
     * held by Typeface are replaced through reflection; on devices where that is blocked nothing
     * changes and the font still applies to the places hooked directly (names, messages, lists).
     */
    /**
     * Applies the chosen fonts right away: resets the cached bold typeface, swaps the typeface of
     * Telegram's cached paints. The interface font is applied on the next launch. Screens must be rebuilt
     * afterwards so views created earlier pick the new fonts up.
     */
    public static void applyNow() {
        try {
            final Typeface oldBold = AndroidUtilities.mediumTypeface;
            AndroidUtilities.mediumTypeface = null;
            final Typeface newBold = AndroidUtilities.bold();
            if (oldBold != null && oldBold != newBold) {
                for (java.lang.reflect.Field f : Theme.class.getDeclaredFields()) {
                    if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                        continue;
                    }
                    try {
                        final Class<?> type = f.getType();
                        if (android.graphics.Paint.class.isAssignableFrom(type)) {
                            f.setAccessible(true);
                            swapTypeface((android.graphics.Paint) f.get(null), oldBold, newBold);
                        } else if (type.isArray() && android.graphics.Paint.class.isAssignableFrom(type.getComponentType())) {
                            f.setAccessible(true);
                            final Object[] arr = (Object[]) f.get(null);
                            if (arr != null) {
                                for (Object o : arr) {
                                    swapTypeface((android.graphics.Paint) o, oldBold, newBold);
                                }
                            }
                        }
                    } catch (Throwable ignore) {
                    }
                }
            }
            if (Theme.chat_msgTextPaint != null) {
                Theme.chat_msgTextPaint.setTypeface(messages());
            }
            if (Theme.chat_replyTextPaint != null) {
                Theme.chat_replyTextPaint.setTypeface(null);
                applyMessageFont(Theme.chat_replyTextPaint);
            }
            if (Theme.chat_quoteTextPaint != null) {
                Theme.chat_quoteTextPaint.setTypeface(null);
                applyMessageFont(Theme.chat_quoteTextPaint);
            }
            if (Theme.dialogs_messagePaint != null) {
                for (android.text.TextPaint paint : Theme.dialogs_messagePaint) {
                    if (paint != null) {
                        paint.setTypeface(regular());
                    }
                }
            }
            // Шрифт всего интерфейса (подмена системного Typeface) по-прежнему
            // ставится только при запуске приложения.
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void swapTypeface(android.graphics.Paint paint, Typeface from, Typeface to) {
        if (paint != null && paint.getTypeface() == from) {
            paint.setTypeface(to);
        }
    }

    public static void applyGlobalTypeface() {
        try {
            final Typeface regular = regular();
            if (regular == null) {
                return;
            }
            final Typeface bold = headings() != null ? headings() : Typeface.create(regular, Typeface.BOLD);
            if (Build.VERSION.SDK_INT >= 28) {
                try {
                    org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Landroid/graphics/Typeface;");
                } catch (Throwable ignore) {
                }
            }
            replaceStatic("DEFAULT", regular);
            replaceStatic("SANS_SERIF", regular);
            replaceStatic("DEFAULT_BOLD", bold);
            setNativeDefault(regular);
            try {
                final java.lang.reflect.Field defaults = Typeface.class.getDeclaredField("sDefaults");
                defaults.setAccessible(true);
                final Typeface[] array = (Typeface[]) defaults.get(null);
                if (array != null && array.length >= 2) {
                    array[Typeface.NORMAL] = regular;
                    array[Typeface.BOLD] = bold;
                }
            } catch (Throwable ignore) {
            }
            try {
                final java.lang.reflect.Field map = Typeface.class.getDeclaredField("sSystemFontMap");
                map.setAccessible(true);
                @SuppressWarnings("unchecked")
                final java.util.Map<String, Typeface> fonts = (java.util.Map<String, Typeface>) map.get(null);
                if (fonts != null) {
                    try {
                        // Newer Android keeps a mutable map in a final field: change it in place
                        fonts.put("sans-serif", regular);
                        fonts.put("sans-serif-medium", bold);
                    } catch (UnsupportedOperationException e) {
                        final java.util.Map<String, Typeface> copy = new java.util.HashMap<>(fonts);
                        copy.put("sans-serif", regular);
                        copy.put("sans-serif-medium", bold);
                        map.set(null, copy);
                    }
                }
            } catch (Throwable ignore) {
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /**
     * TsundereGram: шрифт по умолчанию на уровне движка отрисовки. Краски (Paint/TextPaint), которым
     * Telegram не задаёт шрифт — время сообщений, текст ответов, счётчики, подписи и т.д., —
     * рисуются нативным «default», который подмена полей Typeface не трогает. Typeface.setDefault()
     * меняет его, и выбранный шрифт применяется действительно везде.
     */
    private static void setNativeDefault(Typeface value) {
        try {
            final java.lang.reflect.Method method = Typeface.class.getDeclaredMethod("setDefault", Typeface.class);
            method.setAccessible(true);
            method.invoke(null, value);
            return;
        } catch (Throwable ignore) {
        }
        try {
            final java.lang.reflect.Field instance = Typeface.class.getDeclaredField("native_instance");
            instance.setAccessible(true);
            final long ptr = instance.getLong(value);
            final java.lang.reflect.Method nativeSet = Typeface.class.getDeclaredMethod("nativeSetDefault", long.class);
            nativeSet.setAccessible(true);
            nativeSet.invoke(null, ptr);
            replaceStatic("sDefaultTypeface", value);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void replaceStatic(String name, Typeface value) {
        try {
            final java.lang.reflect.Field field = Typeface.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (Throwable ignore) {
        }
    }

    /** File of an imported font ("file:name.ttf"), or null for built-in fonts and missing files. */
    public static File customFontFile(String font) {
        if (font == null || !font.startsWith(FILE_PREFIX)) {
            return null;
        }
        final File file = new File(customDir(), font.substring(FILE_PREFIX.length()));
        return file.exists() ? file : null;
    }

    /**
     * Installs a font that came inside an exported settings file. Returns false when the name is
     * unsafe or Android cannot load the data as a font.
     */
    public static boolean installFont(String name, byte[] data) {
        if (name == null || data == null || data.length == 0 || data.length > MAX_FONT_BYTES
                || !name.matches("[A-Za-z0-9._ -]+") || name.startsWith(".")) {
            return false;
        }
        final File target = new File(customDir(), name);
        final File tmp = new File(customDir(), name + ".tmp");
        try (OutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
        } catch (Exception e) {
            tmp.delete();
            return false;
        }
        try {
            if (Typeface.createFromFile(tmp) == null) {
                tmp.delete();
                return false;
            }
        } catch (Exception e) {
            tmp.delete();
            return false;
        }
        if (target.exists()) {
            target.delete();
        }
        if (!tmp.renameTo(target)) {
            tmp.delete();
            return false;
        }
        synchronized (TsundereStyleFonts.class) {
            cache.remove(FILE_PREFIX + name + ":h");
            cache.remove(FILE_PREFIX + name + ":r");
        }
        regularResolved = false;
        return true;
    }

    private static String[] downloadable(String font) {
        if (font == null) {
            return null;
        }
        for (String[] d : DOWNLOADABLE) {
            if (d[0].equals(font)) {
                return d;
            }
        }
        return null;
    }

    /** Шрифт из Google Fonts, который ещё не скачан. */
    public static boolean needsDownload(String font) {
        return downloadable(font) != null && !downloadedFile(font).exists();
    }

    private static File downloadedFile(String font) {
        final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "tsundere_fonts_dl");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, font + ".ttf");
    }

    /** Скачивает шрифт Google Fonts (в фоновом потоке). Возвращает true, если файл готов. */
    public static boolean download(String font) {
        final String[] d = downloadable(font);
        if (d == null) {
            return false;
        }
        final File target = downloadedFile(font);
        if (target.exists()) {
            return true;
        }
        final File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
        for (String mirror : DOWNLOAD_MIRRORS) {
            java.net.HttpURLConnection connection = null;
            try {
                connection = (java.net.HttpURLConnection) new java.net.URL(mirror + d[2]).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                if (connection.getResponseCode() != 200) {
                    continue;
                }
                long total = 0;
                try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                    final byte[] buffer = new byte[16384];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        total += n;
                        if (total > MAX_FONT_BYTES) {
                            throw new IllegalArgumentException("font file too large");
                        }
                        out.write(buffer, 0, n);
                    }
                }
                if (total < 1024 || Typeface.createFromFile(tmp) == null) {
                    tmp.delete();
                    continue;
                }
                if (!tmp.renameTo(target)) {
                    tmp.delete();
                    continue;
                }
                synchronized (TsundereStyleFonts.class) {
                    cache.remove(font + ":h");
                    cache.remove(font + ":r");
                }
                regularResolved = false;
                return true;
            } catch (Exception e) {
                FileLog.e(e);
                tmp.delete();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return false;
    }

    private static File customDir() {
        final File dir = new File(ApplicationLoader.applicationContext.getFilesDir(), "tsunderestyle_fonts");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static synchronized Typeface load(String font, boolean heading) {
        if (font == null || SYSTEM.equals(font)) {
            return null;
        }
        final String cacheKey = font + (heading ? ":h" : ":r");
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        Typeface typeface = null;
        try {
            final String[] dl = downloadable(font);
            if (dl != null) {
                final File file = downloadedFile(font);
                if (!file.exists()) {
                    return null; // не кэшируем: после скачивания шрифт подхватится
                }
                if (Build.VERSION.SDK_INT >= 26 && "1".equals(dl[3])) {
                    typeface = new Typeface.Builder(file)
                            .setFontVariationSettings("'wght' " + (heading ? HEADING_WEIGHT : 400))
                            .setWeight(heading ? 700 : 400)
                            .build();
                } else {
                    final Typeface base = Typeface.createFromFile(file);
                    typeface = heading ? Typeface.create(base, Typeface.BOLD) : base;
                }
            } else if (font.startsWith(FILE_PREFIX)) {
                final File file = new File(customDir(), font.substring(FILE_PREFIX.length()));
                if (file.exists()) {
                    final Typeface base = Typeface.createFromFile(file);
                    typeface = heading ? Typeface.create(base, Typeface.BOLD) : base;
                }
            } else {
                final String path = "fonts/tsunderestyle/" + font + ".ttf";
                if (Build.VERSION.SDK_INT >= 26) {
                    // The bundled files are variable fonts: pick the weight through the wght axis
                    typeface = new Typeface.Builder(ApplicationLoader.applicationContext.getAssets(), path)
                            .setFontVariationSettings("'wght' " + (heading ? HEADING_WEIGHT : 400))
                            .setWeight(heading ? 700 : 400)
                            .build();
                } else {
                    final Typeface base = Typeface.createFromAsset(ApplicationLoader.applicationContext.getAssets(), path);
                    typeface = heading ? Typeface.create(base, Typeface.BOLD) : base;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        cache.put(cacheKey, typeface);
        if (typeface != null) {
            owners.put(typeface, font);
        }
        return typeface;
    }

    /**
     * TsundereGram: жирное / курсивное / жирное курсивное начертание того же шрифта, которым
     * набран текст ({@code base}). Если {@code base} — не наш шрифт, возвращает null, и
     * Telegram берёт свои начертания Roboto. Курсива в файлах шрифтов нет, поэтому наклон
     * синтезирует Android (как у системных шрифтов без italic-файла).
     */
    public static Typeface styledFor(Typeface base, boolean bold, boolean italic) {
        if (base == null) {
            base = Typeface.DEFAULT;
        }
        if (base == null || (!bold && !italic)) {
            return null;
        }
        final String font;
        synchronized (TsundereStyleFonts.class) {
            font = owners.get(base);
        }
        return font != null ? styled(font, bold, italic) : null;
    }

    private static synchronized Typeface styled(String font, boolean bold, boolean italic) {
        final String key = font + ":s" + (bold ? "b" : "") + (italic ? "i" : "");
        if (cache.containsKey(key)) {
            return cache.get(key);
        }
        final Typeface upright = load(font, bold);
        if (upright == null) {
            return null;
        }
        Typeface result = upright;
        if (italic) {
            try {
                if (Build.VERSION.SDK_INT >= 28) {
                    result = Typeface.create(upright, bold ? 700 : 400, true);
                } else {
                    result = Typeface.create(upright, bold ? Typeface.BOLD_ITALIC : Typeface.ITALIC);
                }
            } catch (Throwable e) {
                FileLog.e(e);
                result = upright;
            }
        }
        cache.put(key, result);
        if (result != null) {
            owners.put(result, font);
        }
        return result;
    }

    /**
     * Copies a TTF/OTF file the user picked into private storage after checking that Android can
     * load it. Returns the font id, or null when the file is not a usable font. Runs on a worker thread.
     */
    public static String importFont(Context context, Uri uri) {
        String name = displayName(context, uri);
        final String lower = name.toLowerCase(Locale.US);
        if (!lower.endsWith(".ttf") && !lower.endsWith(".otf")) {
            name = name + ".ttf";
        }
        name = name.replaceAll("[^A-Za-z0-9._ -]", "_");
        final File target = new File(customDir(), name);
        final File tmp = new File(customDir(), name + ".tmp");
        try (InputStream in = context.getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) {
                return null;
            }
            final byte[] buffer = new byte[16384];
            long total = 0;
            int n;
            while ((n = in.read(buffer)) > 0) {
                total += n;
                if (total > MAX_FONT_BYTES) {
                    throw new IllegalArgumentException("font file too large");
                }
                out.write(buffer, 0, n);
            }
        } catch (Exception e) {
            FileLog.e(e);
            tmp.delete();
            return null;
        }
        try {
            final Typeface probe = Typeface.createFromFile(tmp);
            if (probe == null) {
                tmp.delete();
                return null;
            }
        } catch (Exception e) {
            tmp.delete();
            return null;
        }
        if (target.exists()) {
            target.delete();
        }
        if (!tmp.renameTo(target)) {
            tmp.delete();
            return null;
        }
        synchronized (TsundereStyleFonts.class) {
            cache.remove(FILE_PREFIX + name + ":h");
            cache.remove(FILE_PREFIX + name + ":r");
        }
        regularResolved = false;
        return FILE_PREFIX + name;
    }

    /** Deletes an imported font; targets using it fall back to the system font. */
    public static void deleteCustom(String font) {
        if (font == null || !font.startsWith(FILE_PREFIX)) {
            return;
        }
        regularResolved = false;
        new File(customDir(), font.substring(FILE_PREFIX.length())).delete();
        for (String target : new String[]{TARGET_HEADINGS, TARGET_MESSAGES, TARGET_INTERFACE}) {
            if (font.equals(get(target))) {
                set(target, SYSTEM);
            }
        }
        synchronized (TsundereStyleFonts.class) {
            cache.remove(font + ":h");
            cache.remove(font + ":r");
        }
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor c = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                final String name = c.getString(0);
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "font_" + System.currentTimeMillis() + ".ttf";
    }
}
