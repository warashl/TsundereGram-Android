package app.tsunderegram.morph;

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

    private MorphPreviewChat previewChat;
    private TextCheckCell enableMorphCell;
    private TextCheckCell vaporwaveCell;
    private TextCheckCell zalgoCell;
    private TextCheckCell antiCapsCell;
    private TextCheckCell systemFontCell;
    private TextCheckCell forceBoldCell;

    private static final String DEFAULT_PREVIEW_TEXT = "Привет! Это пример твоего сообщения в TsundereGram";
    private String currentPreviewText = initialPreviewText();

    private static String initialPreviewText() {
        String saved = TextMorphController.getInstance().getPreviewSampleText();
        return saved == null || saved.trim().isEmpty() ? DEFAULT_PREVIEW_TEXT : saved;
    }

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

        // Предпросмотр рисуется настоящей ячейкой сообщения Telegram (ChatMessageCell) поверх обоев чата,
        // поэтому цитаты, блоки кода с языком, спойлеры и случайный стиль выглядят ровно как в чате.
        root.setClipToPadding(false);
        LinearLayout previewCard = new LinearLayout(context);
        previewCard.setOrientation(LinearLayout.VERTICAL);

        LinearLayout previewHeader = new LinearLayout(context);
        previewHeader.setOrientation(LinearLayout.HORIZONTAL);
        previewHeader.setGravity(Gravity.CENTER_VERTICAL);

        TextView previewLabel = new TextView(context);
        previewLabel.setText("ПРЕДПРОСМОТР");
        previewLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        previewLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        previewLabel.setTypeface(AndroidUtilities.bold());
        previewHeader.addView(previewLabel, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

        TextView changeTextBtn = new TextView(context);
        changeTextBtn.setText("Изменить текст");
        changeTextBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        changeTextBtn.setTypeface(AndroidUtilities.bold());
        final int accent = Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader);
        changeTextBtn.setTextColor(accent);
        changeTextBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), Theme.multAlpha(accent, 0.12f), Theme.multAlpha(accent, 0.25f)));
        changeTextBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        changeTextBtn.setOnClickListener(v -> showChangePreviewTextDialog());
        previewHeader.addView(changeTextBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        previewCard.addView(previewHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 28, 0, 28, 8));

        previewChat = new MorphPreviewChat(context);
        previewCard.addView(previewChat, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView previewHintView = new TextView(context);
        previewHintView.setText("Так ваше сообщение будет выглядеть в чате. Случайный стиль выбирается заново при каждом изменении.");
        previewHintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12f);
        previewHintView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        previewCard.addView(previewHintView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 28, 8, 28, 0));

        // Карточка на всю ширину экрана: ячейка сообщения считает ширину от экрана, как в чате.
        root.addView(previewCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, -16, 0, -16, 16));

        // Шрифты как в TsundereStyle: заголовки/имена, текст сообщений, интерфейс.
        addSectionHeader(root, "ШРИФТЫ");
        LinearLayout typographyCard = createCardLayout(context);
        TextSettingsCell typographyCell = new TextSettingsCell(context);
        typographyCell.setTextAndValue("Шрифт сообщений, заголовков и интерфейса", org.telegram.tsunderestyle.theme.TsundereStyleFonts.title(context, org.telegram.tsunderestyle.theme.TsundereStyleFonts.get(org.telegram.tsunderestyle.theme.TsundereStyleFonts.TARGET_MESSAGES)), false);
        typographyCell.setOnClickListener(v -> presentFragment(new org.telegram.tsunderestyle.settings.TsundereStyleTypographyActivity()));
        typographyCard.addView(typographyCell);
        root.addView(typographyCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // 2. Section: Системный шрифт интерфейса
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
        // Раздел «Шрифт интерфейса» убран: шрифты выбираются на экране «Шрифт».

        // 3. Стилизация исходящих сообщений (порт Text Morph 1.4.1)
        final TextMorphController controller = TextMorphController.getInstance();
        // «Шрифт символов» убран: шрифты настраиваются в TsundereGram отдельно
        if (controller.getFontStyle() != TextMorphController.FONT_NORMAL) {
            controller.setFontStyle(TextMorphController.FONT_NORMAL);
        }
        addSectionHeader(root, "СТИЛИЗАЦИЯ ИСХОДЯЩИХ СООБЩЕНИЙ");
        LinearLayout morphCard = createCardLayout(context);

        enableMorphCell = new TextCheckCell(context);
        enableMorphCell.setTextAndCheck("Включить", controller.isEnabled(), true);
        enableMorphCell.setIcon(R.drawable.msg_settings);
        enableMorphCell.setOnClickListener(v -> {
            boolean newVal = !controller.isEnabled();
            controller.setEnabled(newVal);
            enableMorphCell.setChecked(newVal);
            updatePreview();
        });
        morphCard.addView(enableMorphCell);

        accountsRow = new TextMorphPages.MorphRow(context);
        accountsRow.icon(R.drawable.msg_contacts);
        accountsRow.setOnClickListener(v -> presentFragment(new TextMorphPages.AccountsPage()));
        morphCard.addView(accountsRow);
        root.addView(morphCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // 4. Модификаторы (сущности форматирования)
        addSectionHeader(root, "МОДИФИКАТОРЫ СТИЛЯ TSUNDEREGRAM");
        LinearLayout modifiersCard = createCardLayout(context);
        styleCells = new TextCheckCell[STYLE_ROW_KEYS.length];
        for (int i = 0; i < STYLE_ROW_KEYS.length; i++) {
            final String key = STYLE_ROW_KEYS[i];
            TextCheckCell cell = new TextCheckCell(context);
            cell.setTextAndCheck(STYLE_ROW_TITLES[i], controller.getStyle(key), true);
            // Иконки как в плагине Text Morph: у каждого модификатора — карандаш.
            cell.setIcon(R.drawable.msg_edit);
            cell.setOnClickListener(v -> {
                controller.applyStyleSwitch(key, !controller.getStyle(key));
                refreshStyleCells();
                updatePreview();
            });
            styleCells[i] = cell;
            modifiersCard.addView(cell);
            if (TextMorphController.S_CODE.equals(key)) {
                codeLangRow = new TextMorphPages.MorphRow(context);
                codeLangRow.icon(R.drawable.msg_edit);
                codeLangRow.setOnClickListener(v -> showCodeLangDialog());
                modifiersCard.addView(codeLangRow);
            }
        }
        TextMorphPages.MorphRow poolRow = new TextMorphPages.MorphRow(context);
        poolRow.icon(R.drawable.msg_settings);
        poolRow.set("Настройка пула случайных стилей", "Какие стили может выбрать «Случайный стиль»", false);
        poolRow.setOnClickListener(v -> showRandomPoolDialog());
        modifiersCard.addView(poolRow);
        root.addView(modifiersCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
        TextMorphPages.hint(root, "Язык блока кода, например python, json или bash. Если пусто — будет стандартный блок кода без заголовка.");

        // 5. Режимы обработки текста
        addSectionHeader(root, "РЕЖИМЫ ОБРАБОТКИ ТЕКСТА");
        LinearLayout modesCard = createCardLayout(context);

        leetCell = new TextCheckCell(context);
        leetCell.setTextAndCheck("Leet Speak", controller.isLeetSpeak(), true);
        leetCell.setIcon(R.drawable.msg_edit);
        leetCell.setOnClickListener(v -> {
            boolean newVal = !controller.isLeetSpeak();
            controller.setLeetSpeak(newVal);
            leetCell.setChecked(newVal);
            updatePreview();
        });
        modesCard.addView(leetCell);

        leetRow = new TextMorphPages.MorphRow(context);
        leetRow.icon(R.drawable.msg_settings);
        leetRow.setOnClickListener(v -> presentFragment(new TextMorphPages.LeetPage()));
        modesCard.addView(leetRow);

        zalgoCell = new TextCheckCell(context);
        zalgoCell.setTextAndCheck("Zalgo текст", controller.isZalgo(), true);
        zalgoCell.setIcon(R.drawable.msg_edit);
        zalgoCell.setOnClickListener(v -> {
            if (controller.isZalgo()) {
                controller.setZalgo(false);
                zalgoCell.setChecked(false);
                updatePreview();
            } else {
                showZalgoWarning();
            }
        });
        modesCard.addView(zalgoCell);

        zalgoLevelRow = new TextMorphPages.MorphRow(context);
        zalgoLevelRow.icon(R.drawable.msg_edit);
        zalgoLevelRow.setOnClickListener(v -> showZalgoLevelDialog());
        modesCard.addView(zalgoLevelRow);

        vaporwaveCell = new TextCheckCell(context);
        vaporwaveCell.setTextAndValueAndCheck("Vaporwave", "Работает только для английских букв, цифр и знаков препинания", controller.isVaporwave(), true, false);
        vaporwaveCell.setIcon(R.drawable.msg_edit);
        vaporwaveCell.setOnClickListener(v -> {
            boolean newVal = !controller.isVaporwave();
            if (newVal) {
                controller.setFontStyle(TextMorphController.FONT_NORMAL);
            }
            controller.setVaporwave(newVal);
            vaporwaveCell.setChecked(newVal);
            updatePreview();
        });
        modesCard.addView(vaporwaveCell);
        root.addView(modesCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // 6. Анти-капс
        addSectionHeader(root, "НАСТРОЙКИ АНТИ-КАПСА");
        LinearLayout capsCard = createCardLayout(context);
        antiCapsCell = new TextCheckCell(context);
        antiCapsCell.setTextAndCheck("Анти-капс", controller.isAntiCaps(), true);
        antiCapsCell.setIcon(R.drawable.msg_edit);
        antiCapsCell.setOnClickListener(v -> {
            boolean newVal = !controller.isAntiCaps();
            controller.setAntiCaps(newVal);
            antiCapsCell.setChecked(newVal);
            updatePreview();
        });
        capsCard.addView(antiCapsCell);

        antiCapsPctRow = new TextMorphPages.MorphRow(context);
        antiCapsPctRow.icon(R.drawable.msg_edit);
        antiCapsPctRow.setOnClickListener(v -> showAntiCapsDialog());
        capsCard.addView(antiCapsPctRow);

        capitalizeCell = new TextCheckCell(context);
        capitalizeCell.setTextAndCheck("Заглавная первая буква", controller.isAntiCapsCapitalize(), false);
        capitalizeCell.setIcon(R.drawable.msg_edit);
        capitalizeCell.setOnClickListener(v -> {
            boolean newVal = !controller.isAntiCapsCapitalize();
            controller.setAntiCapsCapitalize(newVal);
            capitalizeCell.setChecked(newVal);
            updatePreview();
        });
        capsCard.addView(capitalizeCell);
        root.addView(capsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // 7. Подпись к сообщениям: отдельные окна префикса и суффикса
        addSectionHeader(root, "ПОДПИСЬ К СООБЩЕНИЯМ");
        LinearLayout affixCard = createCardLayout(context);
        prefixRow = new TextMorphPages.MorphRow(context);
        prefixRow.icon(R.drawable.msg_edit);
        prefixRow.setOnClickListener(v -> showAffixDialog(true));
        affixCard.addView(prefixRow);
        suffixRow = new TextMorphPages.MorphRow(context);
        suffixRow.icon(R.drawable.msg_edit);
        suffixRow.setOnClickListener(v -> showAffixDialog(false));
        affixCard.addView(suffixRow);
        root.addView(affixCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
        TextMorphPages.hint(root, "Пробелы нужно ставить вручную, даже если они в начале — когда нужен отступ от основного сообщения.");

        // 8. Исключения и фильтры
        addSectionHeader(root, "ИСКЛЮЧЕНИЯ И ФИЛЬТРЫ");
        LinearLayout excCard = createCardLayout(context);
        exclusionsRow = new TextMorphPages.MorphRow(context);
        exclusionsRow.icon(R.drawable.msg_contacts);
        exclusionsRow.setOnClickListener(v -> presentFragment(new TextMorphPages.ExclusionsPage()));
        excCard.addView(exclusionsRow);
        root.addView(excCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
        TextMorphPages.hint(root, "Начните сообщение с «!», чтобы отправить его без стилей.");

        updateRows();
        scrollView.addView(root);
        fragmentView = scrollView;

        updatePreview();

        return fragmentView;
    }

    private static final String[] STYLE_ROW_KEYS = {
            TextMorphController.S_BOLD, TextMorphController.S_ITALIC, TextMorphController.S_BOLD_ITALIC,
            TextMorphController.S_MONO, TextMorphController.S_CODE, TextMorphController.S_QUOTE,
            TextMorphController.S_STRIKE, TextMorphController.S_SPOILER, TextMorphController.S_UNDERLINE,
            TextMorphController.S_RANDOM
    };
    private static final String[] STYLE_ROW_TITLES = {
            "Жирный", "Курсив", "Жирный + Курсив", "Моноширинный", "Блок кода", "Цитата",
            "Зачёркнутый", "Спойлер", "Подчёркнутый", "Случайный стиль"
    };
    private static final String[] ZALGO_LEVELS = {"Слабый", "Средний", "Жесткий", "Максимум"};

    private TextCheckCell[] styleCells;
    private TextCheckCell leetCell;
    private TextCheckCell capitalizeCell;
    private TextMorphPages.MorphRow accountsRow;
    private TextMorphPages.MorphRow codeLangRow;
    private TextMorphPages.MorphRow leetRow;
    private TextMorphPages.MorphRow zalgoLevelRow;
    private TextMorphPages.MorphRow antiCapsPctRow;
    private TextMorphPages.MorphRow prefixRow;
    private TextMorphPages.MorphRow suffixRow;
    private TextMorphPages.MorphRow exclusionsRow;

    @Override
    public void onResume() {
        super.onResume();
        updateRows();
        if (leetCell != null) {
            leetCell.setChecked(TextMorphController.getInstance().isLeetSpeak());
        }
        updatePreview();
    }

    private void refreshStyleCells() {
        if (styleCells == null) return;
        TextMorphController c = TextMorphController.getInstance();
        for (int i = 0; i < styleCells.length; i++) {
            if (styleCells[i] != null) {
                styleCells[i].setChecked(c.getStyle(STYLE_ROW_KEYS[i]));
            }
        }
    }

    private static String quoted(String s) {
        return TextUtils.isEmpty(s) ? "Не задан" : "\"" + s + "\"";
    }

    private void updateRows() {
        TextMorphController c = TextMorphController.getInstance();
        if (accountsRow != null) {
            accountsRow.set("Активные аккаунты", c.getBool(TextMorphController.KEY_WORK_ALL_ACCOUNTS)
                    ? "Все аккаунты" : "Активно профилей: " + c.getAllowedAccounts().size() + " шт.", false);
        }
        if (codeLangRow != null) {
            String lang = c.getCodeLang() != null ? c.getCodeLang().trim() : "";
            codeLangRow.set("Язык кода", lang.isEmpty() ? "Без языка — стандартный блок" : lang, true);
        }
        if (leetRow != null) {
            leetRow.set("Замены Leet Speak", "Замен: " + c.getLeetMappings().size() + " • добавить, изменить или удалить", true);
        }
        if (zalgoLevelRow != null) {
            zalgoLevelRow.set("Мощность Zalgo", ZALGO_LEVELS[c.getZalgoLevel()], true);
        }
        if (antiCapsPctRow != null) {
            antiCapsPctRow.set("Порог срабатывания", c.getAntiCapsPct() + "%", true);
        }
        if (prefixRow != null) {
            prefixRow.set("Префикс", quoted(c.getPrefix()), true);
        }
        if (suffixRow != null) {
            suffixRow.set("Суффикс", quoted(c.getSuffix()), false);
        }
        if (exclusionsRow != null) {
            exclusionsRow.set("Центр исключений", "Исключений чатов: " + c.getExcludedChats().size() + " • Умные фильтры", false);
        }
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
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        bg.setCornerRadius(AndroidUtilities.dp(16));
        card.setBackground(bg);
        card.setClipToOutline(true);
        return card;
    }

    /** Новое окно префикса/суффикса как в Text Morph 1.4.1: Сохранить / Очистить / Отмена. */
    private void showAffixDialog(boolean prefix) {
        Context context = getParentActivity();
        if (context == null) return;
        final TextMorphController c = TextMorphController.getInstance();
        final String current = prefix ? c.getPrefix() : c.getSuffix();
        final EditTextBoldCursor edit = TextMorphPages.styledEdit(context,
                prefix ? "Например: [TM]" : "Например: ~",
                current, android.text.InputType.TYPE_CLASS_TEXT);
        edit.setSingleLine(true);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(prefix ? "Префикс текста" : "Суффикс текста");
        builder.setView(TextMorphPages.dialogLayout(context, edit,
                TextMorphPages.helperText(context, prefix
                        ? "Добавляется в начало каждого сообщения."
                        : "Добавляется в конец каждого сообщения.")));
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String val = edit.getText().toString();
            if (prefix) {
                c.setPrefix(val);
            } else {
                c.setSuffix(val);
            }
            updateRows();
            updatePreview();
            TextMorphPages.toast(this, prefix ? "Префикс сохранен!" : "Суффикс сохранен!");
        });
        if (!TextUtils.isEmpty(current)) {
            builder.setNeutralButton("Очистить", (dialog, which) -> {
                if (prefix) {
                    c.setPrefix("");
                } else {
                    c.setSuffix("");
                }
                updateRows();
                updatePreview();
                TextMorphPages.toast(this, prefix ? "Префикс сохранен!" : "Суффикс сохранен!");
            });
        }
        builder.setNegativeButton("Отмена", null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        edit.requestFocus();
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(edit), 200);
    }

    private void showCodeLangDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        final TextMorphController c = TextMorphController.getInstance();
        final String current = c.getCodeLang();
        final EditTextBoldCursor edit = TextMorphPages.styledEdit(context, "Например: python", current, android.text.InputType.TYPE_CLASS_TEXT);
        edit.setSingleLine(true);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Язык блока кода");
        builder.setView(TextMorphPages.dialogLayout(context, edit,
                TextMorphPages.helperText(context, "Подсветка синтаксиса для блоков кода. Быстрые варианты:"),
                TextMorphPages.exampleChips(context, edit, "python", "bash", "json", "kotlin", "java", "js", "cpp")));
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            c.setCodeLang(edit.getText().toString().trim());
            updateRows();
            updatePreview();
            TextMorphPages.toast(this, "Язык кода сохранен!");
        });
        if (!TextUtils.isEmpty(current)) {
            builder.setNeutralButton("Очистить", (dialog, which) -> {
                c.setCodeLang("");
                updateRows();
                updatePreview();
                TextMorphPages.toast(this, "Язык кода сохранен!");
            });
        }
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void showRandomPoolDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        final TextMorphController c = TextMorphController.getInstance();
        final String[] titles = {"Жирный + Курсив", "Жирный", "Курсив", "Моноширинный", "Блок кода", "Цитата", "Зачёркнутый", "Спойлер", "Подчёркнутый"};
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < TextMorphController.POOL_KEYS.length; i++) {
            final String key = TextMorphController.POOL_PREFIX + TextMorphController.POOL_KEYS[i];
            final TextCheckCell cell = new TextCheckCell(context, 23, true);
            cell.setTextAndCheck(titles[i], c.getBool(key), i < TextMorphController.POOL_KEYS.length - 1);
            cell.setBackground(Theme.getSelectorDrawable(false));
            cell.setOnClickListener(v -> {
                boolean nv = !c.getBool(key);
                c.setBool(key, nv);
                cell.setChecked(nv);
            });
            layout.addView(cell);
        }
        ScrollView scroll = new ScrollView(context);
        scroll.addView(layout);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Настройка пула случайных стилей");
        builder.setView(scroll);
        builder.setPositiveButton(LocaleController.getString(R.string.Done), (dialog, which) -> updatePreview());
        showDialog(builder.create());
    }

    private void showZalgoWarning() {
        Context context = getParentActivity();
        if (context == null) return;
        final TextMorphController c = TextMorphController.getInstance();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Предупреждение");
        builder.setMessage("Внимание! Использование Zalgo-текста может негативно восприниматься модерационными ботами, например Ирисом, и приводить к мутам или кикам в беседах.\n\nИспользуйте с осторожностью.");
        builder.setPositiveButton("Включить модификатор", (dialog, which) -> {
            c.setZalgo(true);
            if (zalgoCell != null) zalgoCell.setChecked(true);
            updatePreview();
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    private void showZalgoLevelDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Мощность Zalgo");
        builder.setItems(ZALGO_LEVELS, (dialog, which) -> {
            TextMorphController.getInstance().setZalgoLevel(which);
            updateRows();
            updatePreview();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void showAntiCapsDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        final int[] values = TextMorphController.ANTI_CAPS_THRESHOLDS;
        CharSequence[] items = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) items[i] = values[i] + "%";
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Порог срабатывания");
        builder.setItems(items, (dialog, which) -> {
            TextMorphController.getInstance().setAntiCapsPct(values[which]);
            updateRows();
            updatePreview();
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void updatePreview() {
        if (previewChat == null) return;
        TextMorphController.Result r = TextMorphController.getInstance().preview(currentPreviewText);
        String text = r != null ? r.text : currentPreviewText;
        java.util.ArrayList<TLRPC.MessageEntity> entities = r != null ? r.buildEntities() : new java.util.ArrayList<>();
        previewChat.setMessage(text, entities);
    }

    /** Обои текущего чата + настоящая исходящая ячейка сообщения. */
    private class MorphPreviewChat extends FrameLayout {
        private final org.telegram.ui.Cells.ChatMessageCell cell;

        MorphPreviewChat(Context context) {
            super(context);
            setWillNotDraw(false);
            setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
            Theme.createChatResources(context, false);
            cell = new org.telegram.ui.Cells.ChatMessageCell(context, currentAccount);
            cell.setDelegate(new org.telegram.ui.Cells.ChatMessageCell.ChatMessageCellDelegate() {
                @Override
                public boolean canPerformActions() {
                    return false;
                }
            });
            cell.isChat = false;
            cell.setFullyDraw(true);
            addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        void setMessage(String text, java.util.ArrayList<TLRPC.MessageEntity> entities) {
            TLRPC.TL_message message = new TLRPC.TL_message();
            message.message = TextUtils.isEmpty(text) ? " " : text;
            message.entities = entities;
            message.date = (int) (System.currentTimeMillis() / 1000);
            message.dialog_id = 1;
            message.flags = 259;
            message.from_id = new TLRPC.TL_peerUser();
            message.from_id.user_id = org.telegram.messenger.UserConfig.getInstance(currentAccount).getClientUserId();
            message.id = 1;
            message.media = new TLRPC.TL_messageMediaEmpty();
            message.out = true;
            message.peer_id = new TLRPC.TL_peerUser();
            message.peer_id.user_id = 0;
            org.telegram.messenger.MessageObject messageObject = new org.telegram.messenger.MessageObject(currentAccount, message, true, false);
            messageObject.resetLayout();
            messageObject.eventId = 1;
            cell.setMessageObject(messageObject, null, false, false, false);
            cell.requestLayout();
            requestLayout();
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            android.graphics.drawable.Drawable drawable = Theme.getCachedWallpaperNonBlocking();
            if (Theme.wallpaperLoadTask != null) {
                invalidate();
            }
            final int w = getMeasuredWidth(), h = getMeasuredHeight();
            if (drawable == null) {
                canvas.drawColor(Theme.getColor(Theme.key_chat_wallpaper));
                return;
            }
            canvas.save();
            canvas.clipRect(0, 0, w, h);
            if (drawable instanceof android.graphics.drawable.BitmapDrawable && ((android.graphics.drawable.BitmapDrawable) drawable).getTileModeX() != android.graphics.Shader.TileMode.REPEAT
                    && drawable.getIntrinsicWidth() > 0 && drawable.getIntrinsicHeight() > 0) {
                float scale = Math.max((float) w / drawable.getIntrinsicWidth(), (float) h / drawable.getIntrinsicHeight());
                int dw = (int) Math.ceil(drawable.getIntrinsicWidth() * scale);
                int dh = (int) Math.ceil(drawable.getIntrinsicHeight() * scale);
                int x = (w - dw) / 2, y = (h - dh) / 2;
                drawable.setBounds(x, y, x + dw, y + dh);
                drawable.draw(canvas);
            } else if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                float scale = 2.0f / AndroidUtilities.density;
                canvas.scale(scale, scale);
                drawable.setBounds(0, 0, (int) Math.ceil(w / scale), (int) Math.ceil(h / scale));
                drawable.draw(canvas);
            } else {
                drawable.setBounds(0, 0, w, h);
                drawable.draw(canvas);
            }
            canvas.restore();
        }
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
            TextMorphController.getInstance().setPreviewSampleText(text);
            updatePreview();
        });
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }
}
