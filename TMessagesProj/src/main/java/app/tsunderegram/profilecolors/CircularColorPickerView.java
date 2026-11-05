package app.tsunderegram.profilecolors;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.math.MathUtils;

import org.telegram.messenger.AndroidUtilities;

/**
 * Премиальный круглый выбор цвета (Circular HSV Color Wheel).
 * Радиальный цветовой круг (Hue + Saturation) со слайдером яркости (Value/Brightness)
 * и живым круглым превью-индикатором.
 */
public class CircularColorPickerView extends View {

    public interface OnColorChangedListener {
        void onColorChanged(int color);
    }

    private OnColorChangedListener listener;

    private final float[] hsv = new float[]{0f, 1f, 1f};
    private int currentColor = 0xFF7266ED;

    private final Paint wheelPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint sliderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Bitmap wheelBitmap;
    private final RectF wheelRect = new RectF();
    private final RectF sliderRect = new RectF();

    private float wheelCenterX;
    private float wheelCenterY;
    private float wheelRadius;

    private boolean isTouchingWheel = false;
    private boolean isTouchingSlider = false;

    public CircularColorPickerView(Context context) {
        super(context);
        init();
    }

    public CircularColorPickerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(AndroidUtilities.dp(2.5f));
        strokePaint.setColor(Color.WHITE);
        strokePaint.setShadowLayer(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(1), 0x66000000);
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        this.listener = listener;
    }

    public void setColor(int color) {
        currentColor = color;
        Color.colorToHSV(color, hsv);
        invalidate();
    }

    public int getColor() {
        return currentColor;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        // Достаточная высота под круглый диск + слайдер яркости
        int desiredHeight = width + AndroidUtilities.dp(44);
        setMeasuredDimension(width, desiredHeight);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        float padding = AndroidUtilities.dp(16);
        float sliderHeight = AndroidUtilities.dp(24);
        float sliderMarginTop = AndroidUtilities.dp(16);

        float availWheelSize = Math.min(w - padding * 2, h - sliderHeight - sliderMarginTop - padding * 2);
        wheelRadius = availWheelSize / 2f;
        wheelCenterX = w / 2f;
        wheelCenterY = padding + wheelRadius;
        wheelRect.set(wheelCenterX - wheelRadius, wheelCenterY - wheelRadius, wheelCenterX + wheelRadius, wheelCenterY + wheelRadius);

        float sliderTop = wheelCenterY + wheelRadius + sliderMarginTop;
        sliderRect.set(padding + AndroidUtilities.dp(8), sliderTop, w - padding - AndroidUtilities.dp(8), sliderTop + sliderHeight);

        createWheelBitmap();
    }

    private void createWheelBitmap() {
        int size = Math.max(1, (int) (wheelRadius * 2));
        if (wheelBitmap != null && !wheelBitmap.isRecycled()) {
            wheelBitmap.recycle();
        }
        wheelBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(wheelBitmap);

        int[] colors = new int[]{
                0xFFFF0000, // Красный (0°)
                0xFFFFFF00, // Жёлтый (60°)
                0xFF00FF00, // Зелёный (120°)
                0xFF00FFFF, // Голубой (180°)
                0xFF0000FF, // Синий (240°)
                0xFFFF00FF, // Пурпурный (300°)
                0xFFFF0000  // Красный (360°)
        };
        float cx = size / 2f;
        float cy = size / 2f;

        SweepGradient sweep = new SweepGradient(cx, cy, colors, null);
        RadialGradient radial = new RadialGradient(cx, cy, wheelRadius, 0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        ComposeShader compose = new ComposeShader(sweep, radial, PorterDuff.Mode.SRC_OVER);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(compose);
        canvas.drawCircle(cx, cy, wheelRadius, p);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (wheelBitmap != null && !wheelBitmap.isRecycled()) {
            canvas.drawBitmap(wheelBitmap, wheelRect.left, wheelRect.top, wheelPaint);

            // Тёмная вуаль при уменьшении яркости (Value < 1f)
            if (hsv[2] < 1.0f) {
                Paint dark = new Paint(Paint.ANTI_ALIAS_FLAG);
                dark.setColor(Color.argb((int) ((1.0f - hsv[2]) * 255), 0, 0, 0));
                canvas.drawCircle(wheelCenterX, wheelCenterY, wheelRadius, dark);
            }
        }

        // Внешнее кольцо вокруг круга
        Paint ringBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        ringBorder.setStyle(Paint.Style.STROKE);
        ringBorder.setStrokeWidth(AndroidUtilities.dp(1.5f));
        ringBorder.setColor(0x33FFFFFF);
        canvas.drawCircle(wheelCenterX, wheelCenterY, wheelRadius, ringBorder);

        // Индикатор выбора на круге
        float rad = (float) Math.toRadians(hsv[0]);
        float dist = hsv[1] * wheelRadius;
        float selX = wheelCenterX + (float) Math.cos(rad) * dist;
        float selY = wheelCenterY + (float) Math.sin(rad) * dist;

        thumbPaint.setColor(currentColor);
        thumbPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(selX, selY, AndroidUtilities.dp(12), thumbPaint);
        canvas.drawCircle(selX, selY, AndroidUtilities.dp(12), strokePaint);

        // Слайдер яркости (Brightness / Value)
        int maxColor = Color.HSVToColor(new float[]{hsv[0], hsv[1], 1.0f});
        LinearGradient grad = new LinearGradient(
                sliderRect.left, 0, sliderRect.right, 0,
                0xFF000000, maxColor, Shader.TileMode.CLAMP
        );
        sliderPaint.setShader(grad);
        canvas.drawRoundRect(sliderRect, sliderRect.height() / 2f, sliderRect.height() / 2f, sliderPaint);

        // Тонкая рамка слайдера
        Paint sliderBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
        sliderBorder.setStyle(Paint.Style.STROKE);
        sliderBorder.setStrokeWidth(AndroidUtilities.dp(1.2f));
        sliderBorder.setColor(0x33FFFFFF);
        canvas.drawRoundRect(sliderRect, sliderRect.height() / 2f, sliderRect.height() / 2f, sliderBorder);

        // Бегунок на слайдере яркости
        float sliderThumbX = sliderRect.left + hsv[2] * sliderRect.width();
        float sliderThumbY = sliderRect.centerY();
        canvas.drawCircle(sliderThumbX, sliderThumbY, AndroidUtilities.dp(12), thumbPaint);
        canvas.drawCircle(sliderThumbX, sliderThumbY, AndroidUtilities.dp(12), strokePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                float distToWheelCenter = (float) Math.hypot(x - wheelCenterX, y - wheelCenterY);
                if (distToWheelCenter <= wheelRadius + AndroidUtilities.dp(14)) {
                    isTouchingWheel = true;
                    isTouchingSlider = false;
                    updateWheelTouch(x, y);
                    return true;
                } else if (sliderRect.contains(x, y) || (x >= sliderRect.left - AndroidUtilities.dp(14) && x <= sliderRect.right + AndroidUtilities.dp(14) && Math.abs(y - sliderRect.centerY()) <= AndroidUtilities.dp(24))) {
                    isTouchingSlider = true;
                    isTouchingWheel = false;
                    updateSliderTouch(x);
                    return true;
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (isTouchingWheel) {
                    updateWheelTouch(x, y);
                    return true;
                } else if (isTouchingSlider) {
                    updateSliderTouch(x);
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isTouchingWheel = false;
                isTouchingSlider = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                break;
        }
        return super.onTouchEvent(event);
    }

    private void updateWheelTouch(float x, float y) {
        float dx = x - wheelCenterX;
        float dy = y - wheelCenterY;
        float dist = (float) Math.hypot(dx, dy);

        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) {
            angle += 360f;
        }
        hsv[0] = angle;
        hsv[1] = MathUtils.clamp(dist / wheelRadius, 0f, 1f);
        currentColor = Color.HSVToColor(hsv);
        invalidate();

        if (listener != null) {
            listener.onColorChanged(currentColor);
        }
    }

    private void updateSliderTouch(float x) {
        float progress = MathUtils.clamp((x - sliderRect.left) / sliderRect.width(), 0f, 1f);
        hsv[2] = progress;
        currentColor = Color.HSVToColor(hsv);
        invalidate();

        if (listener != null) {
            listener.onColorChanged(currentColor);
        }
    }
}
