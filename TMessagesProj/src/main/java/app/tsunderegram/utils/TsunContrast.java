package app.tsunderegram.utils;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

/**
 * TsundereGram: guarantees that text/lines stay readable on custom themes where
 * a text colour is (semi-)transparent or nearly equal to the background.
 */
public final class TsunContrast {

    private TsunContrast() {
    }

    private static int opaque(int c) {
        return c | 0xFF000000;
    }

    /** Colour {@code fg} as it is actually seen when drawn over {@code bg}. */
    public static int visible(int fg, int bg) {
        bg = opaque(bg);
        if (Color.alpha(fg) == 255) {
            return fg;
        }
        return ColorUtils.compositeColors(fg, bg);
    }

    /** Returns {@code fg} or a darker/lighter version of it with contrast ≥ {@code minRatio} on {@code bg}. */
    public static int ensure(int fg, int bg, double minRatio) {
        try {
            bg = opaque(bg);
            int seen = visible(fg, bg);
            if (ColorUtils.calculateContrast(seen, bg) >= minRatio) {
                return fg;
            }
            int target = ColorUtils.calculateLuminance(bg) > 0.45 ? 0xFF000000 : 0xFFFFFFFF;
            for (float t = 0.1f; t < 1f; t += 0.1f) {
                int c = ColorUtils.blendARGB(seen, target, t);
                if (ColorUtils.calculateContrast(c, bg) >= minRatio) {
                    return c;
                }
            }
            return target == 0xFF000000 ? 0xFF141414 : 0xFFFFFFFF;
        } catch (Throwable ignore) {
            return fg;
        }
    }

    /** Readable secondary text (≥ 3:1). */
    public static int text(int fg, int bg) {
        return ensure(fg, bg, 3.0);
    }

    /** A visible but soft fill/line colour derived from the background (for tables, separators). */
    public static int line(int line, int bg, float strength) {
        return line(line, bg, strength, 1.2);
    }

    public static int line(int line, int bg, float strength, double minRatio) {
        try {
            bg = opaque(bg);
            int seen = visible(line, bg);
            if (ColorUtils.calculateContrast(seen, bg) >= minRatio) {
                return line;
            }
            int target = ColorUtils.calculateLuminance(bg) > 0.45 ? 0xFF000000 : 0xFFFFFFFF;
            return ColorUtils.blendARGB(bg, target, strength);
        } catch (Throwable ignore) {
            return line;
        }
    }

    public static void fixTextView(TextView view, int bg) {
        if (view == null) {
            return;
        }
        int cur = view.getCurrentTextColor();
        int fixed = text(cur, bg);
        if (fixed != cur) {
            view.setTextColor(fixed);
        }
        ColorStateList links = view.getLinkTextColors();
        if (links != null) {
            int lc = links.getDefaultColor();
            int lf = text(lc, bg);
            if (lf != lc) {
                view.setLinkTextColor(lf);
            }
        }
    }
}
