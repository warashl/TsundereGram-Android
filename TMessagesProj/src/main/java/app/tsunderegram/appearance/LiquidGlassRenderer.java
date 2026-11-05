package app.tsunderegram.appearance;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import org.telegram.messenger.AndroidUtilities;

/**
 * Ультра-премиальный рендерер эффекта Liquid Glass (в стиле Apple visionOS / iOS 18 glassmorphism).
 * Обеспечивает тончайший стеклянный кант преломления и деликатное внутреннее рассеянное свечение.
 */
public class LiquidGlassRenderer {

    private static final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint highlightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    static {
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(AndroidUtilities.dpf2(0.85f));
        highlightPaint.setStyle(Paint.Style.FILL);
    }

    public static boolean isEnabled() {
        return false;
    }

    /**
     * Отрисовка Liquid Glass для баббла сообщения Telegram (Path).
     */
    public static void drawMessageGlass(Canvas canvas, Path path, Rect bounds) {
        if (true) return;
        if (!isEnabled() || bounds == null || bounds.isEmpty() || path == null) {
            return;
        }

        LinearGradient borderShader = new LinearGradient(
                bounds.left, bounds.top, bounds.left, bounds.bottom,
                new int[]{0x42FFFFFF, 0x18FFFFFF, 0x0AFFFFFF},
                new float[]{0.0f, 0.45f, 1.0f},
                Shader.TileMode.CLAMP
        );
        borderPaint.setShader(borderShader);

        float highlightHeight = Math.min(AndroidUtilities.dp(20), bounds.height() * 0.35f);
        LinearGradient highlightShader = new LinearGradient(
                bounds.left, bounds.top, bounds.left, bounds.top + highlightHeight,
                new int[]{0x1EFFFFFF, 0x08FFFFFF, 0x00FFFFFF},
                new float[]{0.0f, 0.5f, 1.0f},
                Shader.TileMode.CLAMP
        );
        highlightPaint.setShader(highlightShader);

        canvas.save();
        canvas.clipPath(path);
        canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.top + highlightHeight, highlightPaint);
        canvas.restore();

        canvas.drawPath(path, borderPaint);
    }

    /**
     * Отрисовка Liquid Glass для прямоугольной карточки со скруглением (RectF).
     */
    public static void drawCardGlass(Canvas canvas, RectF rect, float rx, float ry) {
        if (!isEnabled() || rect == null || rect.isEmpty()) {
            return;
        }

        LinearGradient borderShader = new LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                new int[]{0x46FFFFFF, 0x1AFFFFFF, 0x0AFFFFFF},
                new float[]{0.0f, 0.45f, 1.0f},
                Shader.TileMode.CLAMP
        );
        borderPaint.setShader(borderShader);

        float highlightHeight = Math.min(AndroidUtilities.dp(24), rect.height() * 0.35f);
        LinearGradient highlightShader = new LinearGradient(
                rect.left, rect.top, rect.left, rect.top + highlightHeight,
                new int[]{0x20FFFFFF, 0x08FFFFFF, 0x00FFFFFF},
                new float[]{0.0f, 0.5f, 1.0f},
                Shader.TileMode.CLAMP
        );
        highlightPaint.setShader(highlightShader);

        canvas.save();
        canvas.clipRect(rect);
        canvas.drawRoundRect(rect.left, rect.top, rect.right, rect.top + highlightHeight, rx, ry, highlightPaint);
        canvas.restore();

        canvas.drawRoundRect(rect, rx, ry, borderPaint);
    }

    /**
     * Отрисовка Liquid Glass для произвольного Path карточки секции профиля.
     */
    public static void drawPathGlass(Canvas canvas, Path path, RectF bounds) {
        if (!isEnabled() || path == null || bounds == null || bounds.isEmpty()) {
            return;
        }

        LinearGradient borderShader = new LinearGradient(
                bounds.left, bounds.top, bounds.left, bounds.bottom,
                new int[]{0x45FFFFFF, 0x1AFFFFFF, 0x0AFFFFFF},
                new float[]{0.0f, 0.45f, 1.0f},
                Shader.TileMode.CLAMP
        );
        borderPaint.setShader(borderShader);

        float highlightHeight = Math.min(AndroidUtilities.dp(22), bounds.height() * 0.35f);
        LinearGradient highlightShader = new LinearGradient(
                bounds.left, bounds.top, bounds.left, bounds.top + highlightHeight,
                new int[]{0x20FFFFFF, 0x08FFFFFF, 0x00FFFFFF},
                new float[]{0.0f, 0.5f, 1.0f},
                Shader.TileMode.CLAMP
        );
        highlightPaint.setShader(highlightShader);

        canvas.save();
        canvas.clipPath(path);
        canvas.drawRect(bounds.left, bounds.top, bounds.right, bounds.top + highlightHeight, highlightPaint);
        canvas.restore();

        canvas.drawPath(path, borderPaint);
    }
}
