package org.telegram.othergram.settings;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;

public class OtherGramBannerHeader extends View {

    private static final float ASPECT = 168f / 400f;
    private static final int SIDE_PADDING_DP = 12;
    private static final int RADIUS_DP = 16;

    private final Bitmap bitmap;
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF rect = new RectF();
    private final Rect src = new Rect();
    private final Path clip = new Path();

    public OtherGramBannerHeader(Context context) {
        super(context);
        Bitmap b = null;
        try {
            b = BitmapFactory.decodeResource(getResources(), R.drawable.tsundere_settings_banner);
        } catch (Throwable ignore) {
        }
        bitmap = b;
        if (bitmap != null) {
            src.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int bannerHeight = Math.round((width - dp(SIDE_PADDING_DP) * 2) * ASPECT);
        // ActionBar на этом экране прозрачный и лежит поверх списка, поэтому баннер
        // сдвигаем под него — иначе стрелка «назад» и облако налезают на картинку.
        setMeasuredDimension(width, getTopOffset() + bannerHeight + dp(8) + dp(4));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        rect.set(dp(SIDE_PADDING_DP), getTopOffset() + dp(8), w - dp(SIDE_PADDING_DP), h - dp(4));
        clip.reset();
        clip.addRoundRect(rect, dp(RADIUS_DP), dp(RADIUS_DP), Path.Direction.CW);
    }

    private static int getTopOffset() {
        return ActionBar.getCurrentActionBarHeight();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (bitmap == null) {
            return;
        }
        canvas.save();
        canvas.clipPath(clip);
        canvas.drawBitmap(bitmap, src, rect, bitmapPaint);
        canvas.restore();
    }
}
