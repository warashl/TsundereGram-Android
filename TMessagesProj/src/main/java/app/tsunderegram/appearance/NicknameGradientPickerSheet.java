package app.tsunderegram.appearance;

import android.content.Context;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import app.tsunderegram.profilecolors.CircularColorPickerView;

/** Свои цвета градиента ника: два кружка цвета и цветовой круг, предпросмотр на имени. */
public class NicknameGradientPickerSheet {

    public static void show(BaseFragment fragment, Runnable onApplied) {
        final Context context = fragment.getParentActivity();
        if (context == null) {
            return;
        }
        final TsundereChatCustomizer customizer = TsundereChatCustomizer.getInstance();
        final int[] colors = {customizer.getNicknameColor1(), customizer.getNicknameColor2()};
        if (colors[0] == 0) colors[0] = 0xFFFF69B4;
        if (colors[1] == 0) colors[1] = 0xFFB224EF;
        final int[] selected = {0};
        final boolean[] animated = {customizer.isNicknameGradientAnimated()};
        final Theme.ResourcesProvider rp = fragment.getResourceProvider();

        final LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        final TextView title = new TextView(context);
        title.setText("Градиент никнейма");
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, rp));
        layout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        String name = "TsundereGram";
        try {
            final TLRPC.User self = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
            if (self != null) {
                name = UserObject.getUserName(self);
            }
        } catch (Throwable ignore) {
        }
        final TextView preview = new TextView(context) {
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                applyShader(this, colors, animated[0]);
            }

            @Override
            protected void onDraw(android.graphics.Canvas canvas) {
                if (animated[0] && getPaint().getShader() != null) {
                    // переливание: сдвигаем шейдер по времени
                    TsundereChatCustomizer.updateShimmer(getPaint().getShader(), shimmerWidth(this));
                    android.graphics.Matrix m = new android.graphics.Matrix();
                    getPaint().getShader().getLocalMatrix(m);
                    m.preTranslate(shimmerLeft(this), 0);
                    getPaint().getShader().setLocalMatrix(m);
                    postInvalidateOnAnimation();
                }
                super.onDraw(canvas);
            }
        };
        preview.setText(name);
        preview.setMaxLines(1);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        preview.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        preview.setTypeface(AndroidUtilities.bold());
        preview.setGravity(Gravity.CENTER);
        layout.addView(preview, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        final LinearLayout dots = new LinearLayout(context);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        dots.setGravity(Gravity.CENTER);
        final FrameLayout[] dotViews = new FrameLayout[2];
        final CircularColorPickerView picker = new CircularColorPickerView(context);
        final Runnable updateDots = () -> {
            for (int i = 0; i < 2; i++) {
                final GradientDrawable d = new GradientDrawable();
                d.setShape(GradientDrawable.OVAL);
                d.setColor(colors[i]);
                d.setStroke(AndroidUtilities.dp(selected[0] == i ? 3 : 1.5f), selected[0] == i ? Theme.getColor(Theme.key_dialogTextBlack, rp) : 0x33888888);
                dotViews[i].setBackground(d);
            }
            applyShader(preview, colors, animated[0]);
        };
        for (int i = 0; i < 2; i++) {
            final int index = i;
            final LinearLayout column = new LinearLayout(context);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.CENTER_HORIZONTAL);
            dotViews[i] = new FrameLayout(context);
            column.addView(dotViews[i], LayoutHelper.createLinear(44, 44, Gravity.CENTER_HORIZONTAL));
            final TextView label = new TextView(context);
            label.setText(i == 0 ? "Начало" : "Конец");
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            label.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, rp));
            column.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 6, 0, 0));
            column.setOnClickListener(v -> {
                selected[0] = index;
                picker.setColor(colors[index]);
                updateDots.run();
            });
            dots.addView(column, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 18, 0, 18, 0));
        }
        layout.addView(dots, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        picker.setColor(colors[0]);
        picker.setOnColorChangedListener(color -> {
            colors[selected[0]] = color;
            updateDots.run();
        });
        layout.addView(picker, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 10));

        // TsundereGram: необязательный «переливающийся» градиент
        final org.telegram.ui.Cells.TextCheckCell shimmerCell = new org.telegram.ui.Cells.TextCheckCell(context, rp);
        shimmerCell.setTextAndValueAndCheck("Переливающийся градиент", "Цвета плавно бегут по имени", animated[0], true, false);
        shimmerCell.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12), 0, Theme.getColor(Theme.key_listSelector, rp)));
        shimmerCell.setOnClickListener(v -> {
            animated[0] = !animated[0];
            shimmerCell.setChecked(animated[0]);
            applyShader(preview, colors, animated[0]);
        });
        layout.addView(shimmerCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -20, 0, -20, 4));
        updateDots.run();

        final BottomSheet[] sheet = new BottomSheet[1];
        final TextView apply = new TextView(context);
        apply.setText("Применить");
        apply.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        apply.setTypeface(AndroidUtilities.bold());
        apply.setGravity(Gravity.CENTER);
        apply.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, rp));
        apply.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_featuredStickers_addButton, rp),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, rp)));
        apply.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        apply.setOnClickListener(v -> {
            customizer.setNicknameGradientEnabled(true);
            customizer.setNicknameGradientAnimated(animated[0]);
            customizer.setNicknameColors(colors[0], colors[1]);
            NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_STATUS);
            if (onApplied != null) {
                onApplied.run();
            }
            if (sheet[0] != null) {
                sheet[0].dismiss();
            }
        });
        layout.addView(apply, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        if (customizer.isNicknameGradientEnabled()) {
            final TextView disable = new TextView(context);
            disable.setText("Выключить градиент");
            disable.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            disable.setGravity(Gravity.CENTER);
            disable.setTextColor(Theme.getColor(Theme.key_text_RedBold, rp));
            disable.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12), 0,
                    Theme.getColor(Theme.key_listSelector, rp)));
            disable.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
            disable.setOnClickListener(v -> {
                customizer.setNicknameGradientEnabled(false);
                NotificationCenter.getInstance(UserConfig.selectedAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_NAME | MessagesController.UPDATE_MASK_STATUS);
                if (onApplied != null) {
                    onApplied.run();
                }
                if (sheet[0] != null) {
                    sheet[0].dismiss();
                }
            });
            layout.addView(disable, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 0));
        }

        final BottomSheet.Builder builder = new BottomSheet.Builder(context, false, rp);
        builder.setCustomView(layout);
        sheet[0] = builder.create();
        fragment.showDialog(sheet[0]);
    }

    private static float shimmerLeft(TextView view) {
        final android.text.Layout layout = view.getLayout();
        return layout != null && layout.getLineCount() > 0 ? layout.getLineLeft(0) : 0;
    }

    private static float shimmerWidth(TextView view) {
        final android.text.Layout layout = view.getLayout();
        if (layout != null && layout.getLineCount() > 0) {
            return Math.max(1, layout.getLineRight(0) - layout.getLineLeft(0));
        }
        return Math.max(1, view.getPaint().measureText(view.getText().toString()));
    }

    private static void applyShader(TextView view, int[] colors, boolean animated) {
        if (animated) {
            view.getPaint().setShader(TsundereChatCustomizer.createShimmerShader(colors[0], colors[1], shimmerWidth(view)));
            view.invalidate();
            return;
        }
        // Шейдер задаётся в координатах разметки текста. Раньше он считался от ширины
        // самого View, а однострочный TextView рисует текст со сдвигом прокрутки —
        // поэтому было видно только конечный цвет.
        float left;
        float right;
        final android.text.Layout layout = view.getLayout();
        if (layout != null && layout.getLineCount() > 0) {
            left = layout.getLineLeft(0);
            right = layout.getLineRight(0);
        } else {
            final float textWidth = view.getPaint().measureText(view.getText().toString());
            final float avail = view.getWidth() - view.getCompoundPaddingLeft() - view.getCompoundPaddingRight();
            left = Math.max(0, (avail - textWidth) / 2f);
            right = left + textWidth;
        }
        if (right - left < 1) {
            right = left + 1;
        }
        view.getPaint().setShader(new LinearGradient(left, 0, right, 0, colors[0], colors[1], Shader.TileMode.CLAMP));
        view.invalidate();
    }
}
