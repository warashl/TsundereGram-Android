package app.exteraless.morph;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import android.text.TextUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

import tw.nekomimi.nekogram.NekoConfig;

public class FontSettingsActivity extends BaseFragment {

    private TextView previewTextView;
    private TextCheckCell enableMorphCell;
    private TextCheckCell vaporwaveCell;
    private TextCheckCell zalgoCell;
    private TextCheckCell antiCapsCell;
    private TextCheckCell systemFontCell;
    private TextCheckCell forceBoldCell;
    private TextSettingsCell fontStyleCell;
    private TextSettingsCell prefixSuffixCell;
    private TextCheckCell boldFormatCell;
    private TextCheckCell italicFormatCell;
    private TextCheckCell boldItalicCell;
    private TextCheckCell monoFormatCell;
    private TextCheckCell codeBlockCell;
    private TextCheckCell quoteCell;
    private TextCheckCell strikeCell;
    private TextCheckCell spoilerCell;
    private TextCheckCell underlineCell;
    private TextCheckCell randomStyleCell;

    private static final String DEFAULT_PREVIEW_TEXT = "Привет! Это пример оформления сообщения в Text Morph.";
    private String currentPreviewText = DEFAULT_PREVIEW_TEXT;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Настройки шрифта и текста");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ScrollView scrollView = new ScrollView(context);
        scrollView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(32));

                LinearLayout previewCard = new LinearLayout(context);
        previewCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable previewCardBg = new GradientDrawable();
        previewCardBg.setColor(0xFF131D28);
        previewCardBg.setCornerRadius(AndroidUtilities.dp(14));
        previewCardBg.setStroke(AndroidUtilities.dp(1), 0xFF1B3B52);
        previewCard.setBackground(previewCardBg);
        previewCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));

        // Header: ПРЕДПРОСМОТР + кнопка Изменить текст (плагин 1.mp4)
        LinearLayout previewHeader = new LinearLayout(context);
        previewHeader.setOrientation(LinearLayout.HORIZONTAL);
        previewHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView previewLabel = new TextView(context);
        previewLabel.setText("ПРЕДПРОСМОТР");
        previewLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        previewLabel.setTextColor(0xFF00C8DF);
        previewLabel.setTypeface(AndroidUtilities.bold());
        previewHeader.addView(previewLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        TextView changeTextBtn = new TextView(context);
        changeTextBtn.setText("Изменить текст");
        changeTextBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        changeTextBtn.setTypeface(AndroidUtilities.bold());
        changeTextBtn.setTextColor(0xFF00C8DF);
        changeTextBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), 0xFF1E2D3D, 0xFF2A3E54));
        changeTextBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        changeTextBtn.setOnClickListener(v -> showChangePreviewTextDialog());
        previewHeader.addView(changeTextBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        previewCard.addView(previewHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // Контейнер с текстом предпросмотра
        previewTextView = new TextView(context);
        previewTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        previewTextView.setTextColor(0xFFFFFFFF);
        previewTextView.setText(currentPreviewText);
        previewTextView.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));

        GradientDrawable bubbleBg = new GradientDrawable();
        bubbleBg.setColor(0xFF0D141C);
        bubbleBg.setCornerRadius(AndroidUtilities.dp(10));
        previewTextView.setBackground(bubbleBg);
        previewCard.addView(previewTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        TextView previewHintView = new TextView(context);
        previewHintView.setText("Так ваше сообщение будет выглядеть в чате.");
        previewHintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        previewHintView.setTextColor(0xFF8E9BA8);
        previewCard.addView(previewHintView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        root.addView(previewCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // 2. Section: Системный шрифт интерфейса
        addSectionHeader(root, "ШРИФТ ИНТЕРФЕЙСА");
        LinearLayout systemFontCard = createCardLayout(context);

        systemFontCell = new TextCheckCell(context);
        systemFontCell.setTextAndCheck("Системный шрифт устройства", NekoConfig.typeface.Bool(), true);
        systemFontCell.setOnClickListener(v -> {
            boolean newVal = NekoConfig.typeface.toggleConfigBool();
            systemFontCell.setChecked(newVal);
            AndroidUtilities.clearTypefaceCache();
            BulletinFactory.of(FontSettingsActivity.this).createSimpleBulletin(R.raw.info, "Перезапустите приложение для полного применения").show();
        });
        systemFontCard.addView(systemFontCell);

        forceBoldCell = new TextCheckCell(context);
        forceBoldCell.setTextAndCheck("Усиленный полужирный шрифт", NekoConfig.forceFontWeightFallback.Bool(), false);
        forceBoldCell.setOnClickListener(v -> {
            boolean newVal = NekoConfig.forceFontWeightFallback.toggleConfigBool();
            forceBoldCell.setChecked(newVal);
            AndroidUtilities.clearTypefaceCache();
        });
        systemFontCard.addView(forceBoldCell);
        root.addView(systemFontCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // 3. Section: Преобразование текста (Text Morph)
        addSectionHeader(root, "СТИЛИЗАЦИЯ ИСХОДЯЩИХ СООБЩЕНИЙ");
        LinearLayout morphCard = createCardLayout(context);

        final TextMorphController controller = TextMorphController.getInstance();

        enableMorphCell = new TextCheckCell(context);
        enableMorphCell.setTextAndCheck("Включить Text Morph", controller.isEnabled(), true);
        enableMorphCell.setOnClickListener(v -> {
            boolean newVal = !controller.isEnabled();
            controller.setEnabled(newVal);
            enableMorphCell.setChecked(newVal);
            updatePreview();
        });
        morphCard.addView(enableMorphCell);

        fontStyleCell = new TextSettingsCell(context);
        updateFontStyleCellText();
        fontStyleCell.setOnClickListener(v -> showFontStyleDialog());
        morphCard.addView(fontStyleCell);

        root.addView(morphCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // 4. Section: Модификаторы форматирования (HTML/Entities)
        addSectionHeader(root, "МОДИФИКАТОРЫ СТИЛЯ TELEGRAM");
        LinearLayout modifiersCard = createCardLayout(context);

        boldFormatCell = new TextCheckCell(context);
        boldFormatCell.setTextAndCheck("Жирный (Bold)", controller.isForceBold(), true);
        boldFormatCell.setOnClickListener(v -> {
            boolean newVal = !controller.isForceBold();
            controller.setForceBold(newVal);
            boldFormatCell.setChecked(newVal);
            if (newVal) {
                controller.setRandomStyle(false);
                if (randomStyleCell != null) randomStyleCell.setChecked(false);
                controller.setForceCode(false);
                if (codeBlockCell != null) codeBlockCell.setChecked(false);
                controller.setForceBoldItalic(false);
                if (boldItalicCell != null) boldItalicCell.setChecked(false);
                controller.setForceMono(false);
                if (monoFormatCell != null) monoFormatCell.setChecked(false);
            }
            updatePreview();
        });
        modifiersCard.addView(boldFormatCell);

        italicFormatCell = new TextCheckCell(context);
        italicFormatCell.setTextAndCheck("Курсив (Italic)", controller.isForceItalic(), true);
        italicFormatCell.setOnClickListener(v -> {
            boolean newVal = !controller.isForceItalic();
            controller.setForceItalic(newVal);
            italicFormatCell.setChecked(newVal);
            if (newVal) {
                controller.setRandomStyle(false);
                if (randomStyleCell != null) randomStyleCell.setChecked(false);
                controller.setForceCode(false);
                if (codeBlockCell != null) codeBlockCell.setChecked(false);
                controller.setForceBoldItalic(false);
                if (boldItalicCell != null) boldItalicCell.setChecked(false);
                controller.setForceMono(false);
                if (monoFormatCell != null) monoFormatCell.setChecked(false);
            }
            updatePreview();
        });
        modifiersCard.addView(italicFormatCell);

        boldItalicCell = new TextCheckCell(context);
        boldItalicCell.setTextAndCheck("Жирный + Курсив (Bold Italic)", controller.isForceBoldItalic(), true);
        boldItalicCell.setOnClickListener(v -> {
            boolean newVal = !controller.isForceBoldItalic();
            controller.setForceBoldItalic(newVal);
            boldItalicCell.setChecked(newVal);
            if (newVal) {
                controller.setRandomStyle(false);
                if (randomStyleCell != null) randomStyleCell.setChecked(false);
                controller.setForceCode(false);
                if (codeBlockCell != null) codeBlockCell.setChecked(false);
                controller.setForceBold(false);
                if (boldFormatCell != null) boldFormatCell.setChecked(false);
                controller.setForceItalic(false);
                if (italicFormatCell != null) italicFormatCell.setChecked(false);
                controller.setForceMono(false);
                if (monoFormatCell != null) monoFormatCell.setChecked(false);
            }
            updatePreview();
        });
        modifiersCard.addView(boldItalicCell);

        monoFormatCell = new TextCheckCell(context);
        monoFormatCell.setTextAndCheck("Моноширинный (Code)", controller.isForceMono(), true);
        monoFormatCell.setOnClickListener(v -> {
            boolean newVal = !controller.isForceMono();
            controller.setForceMono(newVal);
            monoFormatCell.setChecked(newVal);
            if (newVal) {
                controller.setRandomStyle(false);
                if (randomStyleCell != null) randomStyleCell.setChecked(false);
                controller.setForceCode(false);
                if (codeBlockCell != null) codeBlockCell.setChecked(false);
                controller.setForceBold(false);
                if (boldFormatCell != null) boldFormatCell.setChecked(false);
                controller.setForceItalic(false);
                if (italicFormatCell != null) italicFormatCell.setChecked(false);
                controller.setForceBoldItalic(false);
                if (boldItalicCell != null) boldItalicCell.setChecked(false);
                controller.setForceSpoiler(false);
                if (spoilerCell != null) spoilerCell.setChecked(false);
            }
            updatePreview();
        });
        modifiersCard.addView(monoFormatCell);

        codeBlockCell = new TextCheckCell(context);
        String lang = controller.getCodeLang();
        codeBlockCell.setTextAndValueAndCheck("Блок кода (Pre)", lang.isEmpty() ? "Без языка" : lang, controller.isForceCode(), true, true);
        codeBlockCell.setOnClickListener(v -> {
            boolean newVal = !controller.isForceCode();
            controller.setForceCode(newVal);
            codeBlockCell.setChecked(newVal);
            if (newVal) {
                controller.setRandomStyle(false);
                if (randomStyleCell != null) randomStyleCell.setChecked(false);
                controller.setForceMono(false);
                controller.setForceBold(false);
                controller.setForceItalic(false);
                controller.setForceBoldItalic(false);
                controller.setForceQuote(false);
                controller.setForceStrike(false);
                controller.setForceSpoiler(false);
                controller.setForceUnderline(false);
                if (monoFormatCell != null) monoFormatCell.setChecked(false);
                if (boldFormatCell != null) boldFormatCell.setChecked(false);
                if (italicFormatCell != null) italicFormatCell.setChecked(false);
                if (boldItalicCell != null) boldItalicCell.setChecked(false);
                if (quoteCell != null) quoteCell.setChecked(false);
                if (strikeCell != null) strikeCell.setChecked(false);
                if (spoilerCell != null) spoilerCell.setChecked(false);
                if (underlineCell != null) underlineCell.setChecked(false);
            }
            updatePreview();
        });
        codeBlockCell.setOnLongClickListener(v -> {
            showCodeLangDialog(codeBlockCell);
            return true;
        });
        modifiersCard.addView(codeBlockCell);

        quoteCell = new TextCheckCell(context);
        quoteCell.setTextAndCheck("Цитата (Blockquote)", controller.isForceQuote(), true);
        quoteCell.setOnClickListener(v -> {
            boolean newVal = !controller.isForceQuote();
            controller.setForceQuote(newVal);
            quoteCell.setChecked(newVal);
            if (newVal) {
                controller.setRandomStyle(false);
                if (randomStyleCell != null) randomStyleCell.setChecked(false);
                controller.setForceCode(false);
                if (codeBlockCell != null) codeBlockCell.setChecked(false);
            }
            updatePreview();
        });
        modifiersCard.addView(quoteCell);

        strikeCell = new TextCheckCell(context);
        strikeCell.setTextAndCheck("Зачёркнутый (Strikethrough)", controller.isForceStrike(), true);
        strikeCell.setOnClickListener(v -> {
            boolean newVal = !controller.isForceStrike();
            controller.setForceStrike(newVal);
            strikeCell.setChecked(newVal);
            if (newVal) {
                controller.setRandomStyle(false);
                if (randomStyleCell != null) randomStyleCell.setChecked(false);
                controller.setForceCode(false);
                if (codeBlockCell != null) codeBlockCell.setChecked(false);
            }
            updatePreview();
        });
        modifiersCard.addView(strikeCell);

        spoilerCell = new TextCheckCell(context);
        spoilerCell.setTextAndCheck("Спойлер (Spoiler)", controller.isForceSpoiler(), true);
        spoilerCell.setOnClickListener(v -> {
            boolean newVal = !controller.isForceSpoiler();
            controller.setForceSpoiler(newVal);
            spoilerCell.setChecked(newVal);
            if (newVal) {
                controller.setRandomStyle(false);
                if (randomStyleCell != null) randomStyleCell.setChecked(false);
                controller.setForceCode(false);
                if (codeBlockCell != null) codeBlockCell.setChecked(false);
                controller.setForceMono(false);
                if (monoFormatCell != null) monoFormatCell.setChecked(false);
            }
            updatePreview();
        });
        modifiersCard.addView(spoilerCell);

        underlineCell = new TextCheckCell(context);
        underlineCell.setTextAndCheck("Подчёркнутый (Underline)", controller.isForceUnderline(), true);
        underlineCell.setOnClickListener(v -> {
            boolean newVal = !controller.isForceUnderline();
            controller.setForceUnderline(newVal);
            underlineCell.setChecked(newVal);
            if (newVal) {
                controller.setRandomStyle(false);
                if (randomStyleCell != null) randomStyleCell.setChecked(false);
                controller.setForceCode(false);
                if (codeBlockCell != null) codeBlockCell.setChecked(false);
            }
            updatePreview();
        });
        modifiersCard.addView(underlineCell);

        randomStyleCell = new TextCheckCell(context);
        randomStyleCell.setTextAndCheck("Случайный стиль на каждое сообщение (Random Chaos)", controller.isRandomStyle(), false);
        randomStyleCell.setOnClickListener(v -> {
            boolean newVal = !controller.isRandomStyle();
            controller.setRandomStyle(newVal);
            randomStyleCell.setChecked(newVal);
            if (newVal) {
                controller.setForceBold(false);
                controller.setForceItalic(false);
                controller.setForceBoldItalic(false);
                controller.setForceMono(false);
                controller.setForceCode(false);
                controller.setForceQuote(false);
                controller.setForceStrike(false);
                controller.setForceSpoiler(false);
                controller.setForceUnderline(false);
                controller.setFontStyle(TextMorphController.FONT_NORMAL);
                updateFontStyleCellText();
                if (boldFormatCell != null) boldFormatCell.setChecked(false);
                if (italicFormatCell != null) italicFormatCell.setChecked(false);
                if (boldItalicCell != null) boldItalicCell.setChecked(false);
                if (monoFormatCell != null) monoFormatCell.setChecked(false);
                if (codeBlockCell != null) codeBlockCell.setChecked(false);
                if (quoteCell != null) quoteCell.setChecked(false);
                if (strikeCell != null) strikeCell.setChecked(false);
                if (spoilerCell != null) spoilerCell.setChecked(false);
                if (underlineCell != null) underlineCell.setChecked(false);
            }
            updatePreview();
        });
        modifiersCard.addView(randomStyleCell);

        root.addView(modifiersCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // 5. Section: Режимы трансформации
        addSectionHeader(root, "РЕЖИМЫ ОБРАБОТКИ ТЕКСТА");
        LinearLayout modesCard = createCardLayout(context);

        TextCheckCell leetCell = new TextCheckCell(context);
        leetCell.setTextAndCheck("Leet Speak (1337 замена букв)", controller.isLeetSpeak(), true);
        leetCell.setOnClickListener(v -> {
            boolean newVal = !controller.isLeetSpeak();
            controller.setLeetSpeak(newVal);
            leetCell.setChecked(newVal);
            updatePreview();
        });
        modesCard.addView(leetCell);

        vaporwaveCell = new TextCheckCell(context);
        vaporwaveCell.setTextAndCheck("Vaporwave ( Ａ Ｅ Ｓ )", controller.isVaporwave(), true);
        vaporwaveCell.setOnClickListener(v -> {
            boolean newVal = !controller.isVaporwave();
            controller.setVaporwave(newVal);
            vaporwaveCell.setChecked(newVal);
            if (newVal) {
                controller.setFontStyle(TextMorphController.FONT_NORMAL);
                updateFontStyleCellText();
                controller.setRandomStyle(false);
                if (randomStyleCell != null) randomStyleCell.setChecked(false);
            }
            updatePreview();
        });
        modesCard.addView(vaporwaveCell);

        zalgoCell = new TextCheckCell(context);
        zalgoCell.setTextAndValueAndCheck("Zalgo эффект (глич-текст)", "Сила: " + controller.getZalgoIntensity(), controller.isZalgo(), true, true);
        zalgoCell.setOnClickListener(v -> {
            boolean newVal = !controller.isZalgo();
            controller.setZalgo(newVal);
            zalgoCell.setChecked(newVal);
            updatePreview();
        });
        zalgoCell.setOnLongClickListener(v -> {
            showZalgoIntensityDialog(zalgoCell);
            return true;
        });
        modesCard.addView(zalgoCell);

        antiCapsCell = new TextCheckCell(context);
        antiCapsCell.setTextAndValueAndCheck("Умный анти-капс", controller.getAntiCapsPct() + "% капса", controller.isAntiCaps(), true, true);
        antiCapsCell.setOnClickListener(v -> {
            boolean newVal = !controller.isAntiCaps();
            controller.setAntiCaps(newVal);
            antiCapsCell.setChecked(newVal);
            updatePreview();
        });
        antiCapsCell.setOnLongClickListener(v -> {
            showAntiCapsDialog(antiCapsCell);
            return true;
        });
        modesCard.addView(antiCapsCell);

        prefixSuffixCell = new TextSettingsCell(context);
        updatePrefixSuffixCellText();
        prefixSuffixCell.setOnClickListener(v -> showPrefixSuffixDialog());
        modesCard.addView(prefixSuffixCell);

        root.addView(modesCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // 5. Section: Исключения чатов (плагин и плагин 1)
        addExcludedChatsSection(root, context);

        scrollView.addView(root);
        fragmentView = scrollView;

        updatePreview();

        return fragmentView;
    }

    private void addSectionHeader(LinearLayout root, String text) {
        TextView header = new TextView(getParentActivity());
        header.setText(text);
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        header.setTypeface(AndroidUtilities.bold());
        header.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 4));
    }

    private LinearLayout createCardLayout(Context context) {
        LinearLayout card = new LinearLayout(context) {
            @Override
            protected void dispatchDraw(android.graphics.Canvas canvas) {
                super.dispatchDraw(canvas);
                if (app.exteraless.appearance.LiquidGlassRenderer.isEnabled()) {
                    android.graphics.RectF r = new android.graphics.RectF(0, 0, getWidth(), getHeight());
                    float rad = AndroidUtilities.dp(16);
                    app.exteraless.appearance.LiquidGlassRenderer.drawCardGlass(canvas, r, rad, rad);
                }
            }
        };
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        bg.setCornerRadius(AndroidUtilities.dp(16));
        card.setBackground(bg);
        return card;
    }

    private void updateFontStyleCellText() {
        int style = TextMorphController.getInstance().getFontStyle();
        String name;
        switch (style) {
            case TextMorphController.FONT_MATH_BOLD:
                name = "Math Bold (𝗔𝗕𝗖)";
                break;
            case TextMorphController.FONT_GOTHIC:
                name = "Gothic (𝕬𝕭𝕮)";
                break;
            case TextMorphController.FONT_CURSIVE:
                name = "Cursive (𝓐𝓑𝓒)";
                break;
            case TextMorphController.FONT_DOUBLE_STRUCK:
                name = "Double Struck (𝔸𝔹ℂ)";
                break;
            case TextMorphController.FONT_BUBBLES:
                name = "Bubbles (ⒶⒷⒸ)";
                break;
            case TextMorphController.FONT_SQUARES:
                name = "Squares (🄰🄱🄲)";
                break;
            case TextMorphController.FONT_MONOSPACE:
                name = "Monospace (𝙼𝚘𝚗𝚘)";
                break;
            case TextMorphController.FONT_SANS_SERIF:
                name = "Sans-Serif (𝖲𝖺𝗇𝗌)";
                break;
            case TextMorphController.FONT_SMALL_CAPS:
                name = "Small Caps (ꜱᴍᴀʟʟ)";
                break;
            case TextMorphController.FONT_INVERTED:
                name = "Upside Down (uʍop)";
                break;
            default:
                name = "Обычный";
                break;
        }
        fontStyleCell.setTextAndValue("Шрифт символов", name, true);
    }

    private void updatePrefixSuffixCellText() {
        String p = TextMorphController.getInstance().getPrefix();
        String s = TextMorphController.getInstance().getSuffix();
        String val = "Не заданы";
        if (!p.isEmpty() || !s.isEmpty()) {
            val = "\"" + p + "..." + s + "\"";
        }
        prefixSuffixCell.setTextAndValue("Префикс и суффикс", val, false);
    }

    private void showFontStyleDialog() {
        if (getParentActivity() == null) return;

        CharSequence[] items = new CharSequence[]{
                "Обычный (Normal)",
                "Math Bold (𝗔𝗕𝗖 𝟭𝟮𝟯)",
                "Gothic (𝕬𝕭𝕮)",
                "Cursive (𝓐𝓑𝓒)",
                "Double Struck (𝔸𝔹ℂ)",
                "Bubbles (ⒶⒷⒸ)",
                "Squares (🄰🄱🄲)",
                "Monospace (𝙼𝚘𝚗𝚘)",
                "Sans-Serif (𝖲𝖺𝗇𝗌)",
                "Small Caps (ꜱᴍᴀʟʟ)",
                "Upside Down (uʍop)"
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle("Выберите стиль символов");
        builder.setItems(items, (dialog, which) -> {
            TextMorphController.getInstance().setFontStyle(which);
            TextMorphController.getInstance().setEnabled(true);
            if (enableMorphCell != null) {
                enableMorphCell.setChecked(true);
            }
            if (which > 0) {
                TextMorphController.getInstance().setRandomStyle(false);
                if (randomStyleCell != null) randomStyleCell.setChecked(false);
                TextMorphController.getInstance().setVaporwave(false);
                if (vaporwaveCell != null) vaporwaveCell.setChecked(false);
            }
            updateFontStyleCellText();
            updatePreview();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showPrefixSuffixDialog() {
        Context context = getParentActivity();
        if (context == null) return;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(12), AndroidUtilities.dp(24), AndroidUtilities.dp(12));

        TextView pLabel = new TextView(context);
        pLabel.setText("Префикс:");
        pLabel.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        layout.addView(pLabel);

        EditTextBoldCursor prefixEdit = new EditTextBoldCursor(context);
        prefixEdit.setText(TextMorphController.getInstance().getPrefix());
        prefixEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        layout.addView(prefixEdit);

        TextView sLabel = new TextView(context);
        sLabel.setText("Суффикс:");
        sLabel.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        layout.addView(sLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        EditTextBoldCursor suffixEdit = new EditTextBoldCursor(context);
        suffixEdit.setText(TextMorphController.getInstance().getSuffix());
        suffixEdit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        layout.addView(suffixEdit);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Префикс и суффикс");
        builder.setView(layout);
        builder.setPositiveButton(LocaleController.getString(R.string.Done), (dialog, which) -> {
            TextMorphController.getInstance().setPrefix(prefixEdit.getText().toString());
            TextMorphController.getInstance().setSuffix(suffixEdit.getText().toString());
            updatePrefixSuffixCellText();
            updatePreview();
        });
        builder.setNeutralButton("Очистить", (dialog, which) -> {
            TextMorphController.getInstance().setPrefix("");
            TextMorphController.getInstance().setSuffix("");
            updatePrefixSuffixCellText();
            updatePreview();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showCodeLangDialog(TextCheckCell cell) {
        Context context = getParentActivity();
        if (context == null) return;

        EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setText(TextMorphController.getInstance().getCodeLang());
        edit.setHint("Например: python, java, json, cpp");
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        edit.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(16), AndroidUtilities.dp(24), AndroidUtilities.dp(16));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Язык подсветки блока кода");
        builder.setView(edit);
        builder.setPositiveButton(LocaleController.getString(R.string.Done), (dialog, which) -> {
            String val = edit.getText().toString().trim();
            TextMorphController.getInstance().setCodeLang(val);
            cell.setTextAndValueAndCheck("Блок кода (Pre)", val.isEmpty() ? "Без языка" : val, TextMorphController.getInstance().isForceCode(), true, true);
        });
        builder.setNeutralButton("Сбросить", (dialog, which) -> {
            TextMorphController.getInstance().setCodeLang("");
            cell.setTextAndValueAndCheck("Блок кода (Pre)", "Без языка", TextMorphController.getInstance().isForceCode(), true, true);
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showZalgoIntensityDialog(TextCheckCell cell) {
        Context context = getParentActivity();
        if (context == null) return;

        CharSequence[] items = new CharSequence[]{"Слабый (1)", "Средний (2)", "Высокий (4)", "Безумный (8)"};
        final int[] values = new int[]{1, 2, 4, 8};

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Интенсивность Zalgo");
        builder.setItems(items, (dialog, which) -> {
            TextMorphController.getInstance().setZalgoIntensity(values[which]);
            cell.setTextAndValueAndCheck("Zalgo эффект (глич-текст)", "Сила: " + values[which], TextMorphController.getInstance().isZalgo(), true, true);
            updatePreview();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showAntiCapsDialog(TextCheckCell cell) {
        Context context = getParentActivity();
        if (context == null) return;

        CharSequence[] items = new CharSequence[]{"Порог 30% капса", "Порог 50% капса", "Порог 70% капса (стандарт)", "Порог 90% капса", "Порог 100% (только полный капс)"};
        final int[] values = new int[]{30, 50, 70, 90, 100};

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Настройки анти-капса");
        builder.setItems(items, (dialog, which) -> {
            TextMorphController.getInstance().setAntiCapsPct(values[which]);
            cell.setTextAndValueAndCheck("Умный анти-капс", values[which] + "% капса", TextMorphController.getInstance().isAntiCaps(), true, true);
            updatePreview();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void updatePreview() {
        if (previewTextView == null) return;
        TextMorphController ctrl = TextMorphController.getInstance();
        String transformed = ctrl.transformTextPreview(currentPreviewText);
        android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(transformed);
        if (ctrl.isForceCode()) {
            ssb.setSpan(new android.text.style.TypefaceSpan("monospace"), 0, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            ssb.setSpan(new android.text.style.BackgroundColorSpan(0x33888888), 0, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else if (ctrl.isForceMono()) {
            ssb.setSpan(new android.text.style.TypefaceSpan("monospace"), 0, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } else {
            if (ctrl.isForceBold() || ctrl.isForceBoldItalic()) {
                ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (ctrl.isForceItalic() || ctrl.isForceBoldItalic()) {
                ssb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.ITALIC), 0, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (ctrl.isForceStrike()) {
                ssb.setSpan(new android.text.style.StrikethroughSpan(), 0, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (ctrl.isForceUnderline()) {
                ssb.setSpan(new android.text.style.UnderlineSpan(), 0, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (ctrl.isForceSpoiler()) {
                ssb.setSpan(new android.text.style.BackgroundColorSpan(0x55888888), 0, ssb.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        previewTextView.setText(ssb);
    }

    private void showChangePreviewTextDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        org.telegram.ui.ActionBar.AlertDialog.Builder builder = new org.telegram.ui.ActionBar.AlertDialog.Builder(context);
        builder.setTitle("Текст предпросмотра");

        final org.telegram.ui.Components.EditTextBoldCursor editText = new org.telegram.ui.Components.EditTextBoldCursor(context);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setText(currentPreviewText);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(12));

        FrameLayout frame = new FrameLayout(context);
        frame.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 16, 8, 16, 8));
        builder.setView(frame);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String text = editText.getText().toString().trim();
            if (!text.isEmpty()) {
                currentPreviewText = text;
            } else {
                currentPreviewText = DEFAULT_PREVIEW_TEXT;
            }
            updatePreview();
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }


    private LinearLayout excludedChatsContainer;

    private void addExcludedChatsSection(LinearLayout root, Context context) {
        addSectionHeader(root, "ИСКЛЮЧЕНИЯ ЧАТОВ");
        LinearLayout card = createCardLayout(context);

        TextView infoText = new TextView(context);
        infoText.setText("Сообщения в исключённых чатах отправляются в обычном виде, без стилей и трансформаций Text Morph.");
        infoText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        infoText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        infoText.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        card.addView(infoText);

        excludedChatsContainer = new LinearLayout(context);
        excludedChatsContainer.setOrientation(LinearLayout.VERTICAL);
        card.addView(excludedChatsContainer);

        TextView addExcludedBtn = new TextView(context);
        addExcludedBtn.setText("+ Добавить чат в исключения");
        addExcludedBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        addExcludedBtn.setTypeface(AndroidUtilities.bold());
        addExcludedBtn.setGravity(Gravity.CENTER);
        addExcludedBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText));
        addExcludedBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10),
                Theme.getColor(Theme.key_featuredStickers_addButton),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed)));
        addExcludedBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
        LinearLayout.LayoutParams btnLp = LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 6, 14, 14);
        card.addView(addExcludedBtn, btnLp);

        addExcludedBtn.setOnClickListener(v -> {
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putBoolean("checkCanWrite", false);
            args.putInt("dialogsType", 3);
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
                if (!dids.isEmpty()) {
                    long did = dids.get(0).dialogId;
                    TextMorphController.getInstance().setChatExcluded(did, true);
                    updateExcludedChatsList(context);
                }
                fragment1.finishFragment();
                return true;
            });
            presentFragment(fragment);
        });

        updateExcludedChatsList(context);
        root.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 24));
    }

    private void updateExcludedChatsList(Context context) {
        if (excludedChatsContainer == null) return;
        excludedChatsContainer.removeAllViews();
        java.util.Set<Long> set = TextMorphController.getInstance().getExcludedChats();
        if (set == null || set.isEmpty()) {
            TextView emptyView = new TextView(context);
            emptyView.setText("Нет исключённых чатов");
            emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            emptyView.setGravity(Gravity.CENTER);
            emptyView.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(6), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
            excludedChatsContainer.addView(emptyView);
            return;
        }

        for (Long did : set) {
            final long dialogId = did;
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));

            BackupImageView avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(AndroidUtilities.dp(18));
            AvatarDrawable avatarDrawable = new AvatarDrawable();

            String name = "Чат";
            if (dialogId > 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(dialogId);
                if (user != null) {
                    name = UserObject.getUserName(user);
                    avatarDrawable.setInfo(currentAccount, user);
                    avatarView.setForUserOrChat(user, avatarDrawable);
                } else {
                    avatarDrawable.setInfo(dialogId, "User", null);
                    avatarView.setImageDrawable(avatarDrawable);
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
                if (chat != null) {
                    name = chat.title;
                    avatarDrawable.setInfo(currentAccount, chat);
                    avatarView.setForUserOrChat(chat, avatarDrawable);
                } else {
                    avatarDrawable.setInfo(dialogId, "Chat", null);
                    avatarView.setImageDrawable(avatarDrawable);
                }
            }
            row.addView(avatarView, LayoutHelper.createLinear(36, 36));

            TextView titleView = new TextView(context);
            titleView.setText(name);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            row.addView(titleView, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

            ImageView deleteBtn = new ImageView(context);
            deleteBtn.setImageResource(R.drawable.msg_clear);
            deleteBtn.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            deleteBtn.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6), AndroidUtilities.dp(6));
            deleteBtn.setBackground(Theme.createSelectorDrawable(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText), 0.1f)));
            deleteBtn.setOnClickListener(v -> {
                TextMorphController.getInstance().setChatExcluded(dialogId, false);
                updateExcludedChatsList(context);
            });
            row.addView(deleteBtn, LayoutHelper.createLinear(32, 32));

            excludedChatsContainer.addView(row);
        }
    }

}
