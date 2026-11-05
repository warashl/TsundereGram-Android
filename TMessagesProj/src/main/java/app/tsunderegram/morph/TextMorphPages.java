package app.tsunderegram.morph;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;
import org.telegram.ui.DialogsActivity;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;

/** Подстраницы стилизации сообщений (порт Text Morph 1.4.1): аккаунты, центр исключений, Leet Speak. */
public final class TextMorphPages {

    private TextMorphPages() {
    }

    // ---------------- общие элементы ----------------

    static LinearLayout card(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        bg.setCornerRadius(AndroidUtilities.dp(16));
        card.setBackground(bg);
        card.setClipToOutline(true);
        return card;
    }

    static void header(LinearLayout root, String text) {
        TextView header = new TextView(root.getContext());
        header.setText(text);
        header.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        header.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueHeader));
        header.setTypeface(AndroidUtilities.bold());
        header.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(6), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
        root.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 4));
    }

    static void addCard(LinearLayout root, View card, int bottomMargin) {
        root.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, bottomMargin));
    }

    static void hint(LinearLayout root, String text) {
        TextView hint = new TextView(root.getContext());
        hint.setText(text);
        hint.setTypeface(AndroidUtilities.regular());
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint.setLineSpacing(AndroidUtilities.dp(2), 1f);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText4));
        hint.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
        root.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));
    }

    static TextCheckCell check(Context context, String text, String sub, boolean checked, boolean divider) {
        TextCheckCell cell = new TextCheckCell(context);
        if (TextUtils.isEmpty(sub)) {
            cell.setTextAndCheck(text, checked, divider);
        } else {
            cell.setTextAndValueAndCheck(text, sub, checked, true, divider);
        }
        cell.setBackground(Theme.getSelectorDrawable(false));
        return cell;
    }

    /** Поле ввода в стиле плагина: скруглённая подложка с обводкой акцентного цвета. */
    static EditTextBoldCursor styledEdit(Context context, String hint, String value, int inputType) {
        EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        edit.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setCursorSize(AndroidUtilities.dp(20));
        edit.setCursorWidth(1.5f);
        if (inputType != 0) {
            edit.setInputType(inputType);
        }
        if (!TextUtils.isEmpty(hint)) {
            edit.setHint(hint);
        }
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack), 0.06f));
        bg.setCornerRadius(AndroidUtilities.dp(12));
        bg.setStroke(AndroidUtilities.dp(1), Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlue2), 0.45f));
        edit.setBackground(bg);
        edit.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        if (!TextUtils.isEmpty(value)) {
            edit.setText(value);
            edit.setSelection(edit.length());
        }
        return edit;
    }

    /** Поясняющий текст под полем ввода (переносится по строкам, не обрезается). */
    static TextView helperText(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tv.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        tv.setLineSpacing(AndroidUtilities.dp(2), 1f);
        tv.setText(text);
        tv.setPadding(AndroidUtilities.dp(4), 0, AndroidUtilities.dp(4), 0);
        return tv;
    }

    /** Ряд «чипсов» с примерами: нажатие подставляет пример в поле. */
    static View exampleChips(Context context, EditTextBoldCursor edit, String... examples) {
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int accent = Theme.getColor(Theme.key_dialogTextBlue2);
        for (int i = 0; i < examples.length; i++) {
            final String ex = examples[i];
            TextView chip = new TextView(context);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            chip.setTextColor(accent);
            chip.setSingleLine(true);
            chip.setText(ex.trim().isEmpty() ? ex : ex.trim());
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Theme.multAlpha(accent, 0.12f));
            bg.setCornerRadius(AndroidUtilities.dp(16));
            chip.setBackground(bg);
            chip.setOnClickListener(v -> {
                edit.setText(ex);
                edit.setSelection(edit.length());
                edit.requestFocus();
            });
            row.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, 32, 0, 0, i == examples.length - 1 ? 0 : 8, 0));
        }
        scroll.addView(row, new android.widget.FrameLayout.LayoutParams(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        return scroll;
    }

    static LinearLayout dialogLayout(Context context, View... edits) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(12), AndroidUtilities.dp(22), AndroidUtilities.dp(6));
        for (int i = 0; i < edits.length; i++) {
            layout.addView(edits[i], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, i == 0 ? 0 : 10, 0, 0));
        }
        return layout;
    }

    static void toast(BaseFragment fragment, String text) {
        if (fragment != null && BulletinFactory.canShowBulletin(fragment)) {
            BulletinFactory.of(fragment).createSimpleBulletin(R.raw.done, text).show();
        }
    }

    static void error(BaseFragment fragment, String text) {
        if (fragment != null && BulletinFactory.canShowBulletin(fragment)) {
            BulletinFactory.of(fragment).createErrorBulletin(text).show();
        }
    }

    /** Ошибка поверх открытого диалога (диалог остаётся открытым). */
    static void dialogError(View field, String text) {
        if (field != null) {
            AndroidUtilities.shakeViewSpring(field);
            Toast.makeText(field.getContext(), text, Toast.LENGTH_SHORT).show();
        }
    }

    /** Строка «заголовок + подпись» без фиксированных отступов — не налезает при любых шрифтах. */
    public static class MorphRow extends LinearLayout {
        final TextView titleView;
        final TextView valueView;
        private boolean divider;

        public MorphRow(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(AndroidUtilities.dp(52));
            setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(10), AndroidUtilities.dp(21), AndroidUtilities.dp(10));
            setBackground(Theme.getSelectorDrawable(false));
            titleView = new TextView(context);
            titleView.setTypeface(AndroidUtilities.regular());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            valueView = new TextView(context);
            valueView.setTypeface(AndroidUtilities.regular());
            valueView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            valueView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            addView(valueView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            setWillNotDraw(false);
        }

        public MorphRow set(CharSequence title, CharSequence value, boolean divider) {
            titleView.setText(title);
            valueView.setText(value);
            valueView.setVisibility(TextUtils.isEmpty(value) ? GONE : VISIBLE);
            this.divider = divider;
            invalidate();
            return this;
        }

        public MorphRow red() {
            titleView.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
            return this;
        }

        private android.graphics.drawable.Drawable iconDrawable;

        /** Серая иконка слева, как у строк настроек плагина Text Morph. */
        public MorphRow icon(int resId) {
            if (resId == 0) {
                iconDrawable = null;
                setPadding(AndroidUtilities.dp(21), getPaddingTop(), AndroidUtilities.dp(21), getPaddingBottom());
            } else {
                android.graphics.drawable.Drawable d = androidx.core.content.ContextCompat.getDrawable(getContext(), resId);
                if (d != null) {
                    d = d.mutate();
                    d.setColorFilter(new android.graphics.PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), android.graphics.PorterDuff.Mode.MULTIPLY));
                }
                iconDrawable = d;
                setPadding(AndroidUtilities.dp(71), getPaddingTop(), AndroidUtilities.dp(21), getPaddingBottom());
            }
            invalidate();
            return this;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (iconDrawable != null) {
                int size = AndroidUtilities.dp(24);
                int left = AndroidUtilities.dp(21);
                int top = (getHeight() - size) / 2;
                iconDrawable.setBounds(left, top, left + size, top + size);
                iconDrawable.draw(canvas);
            }
            if (divider && Theme.dividerPaint != null) {
                canvas.drawLine(AndroidUtilities.dp(iconDrawable != null ? 71 : 21), getHeight() - 1, getWidth(), getHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    /** Строка с аватаркой пользователя/чата. */
    static class PeerRow extends LinearLayout {
        final BackupImageView avatar;
        final TextView titleView;
        final TextView subView;
        private boolean divider;

        PeerRow(Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setMinimumHeight(AndroidUtilities.dp(60));
            setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
            setBackground(Theme.getSelectorDrawable(false));
            avatar = new BackupImageView(context);
            avatar.setRoundRadius(AndroidUtilities.dp(20));
            addView(avatar, LayoutHelper.createLinear(40, 40));
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(VERTICAL);
            titleView = new TextView(context);
            titleView.setTypeface(AndroidUtilities.regular());
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            subView = new TextView(context);
            subView.setTypeface(AndroidUtilities.regular());
            subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            subView.setSingleLine(true);
            subView.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 14, 0, 8, 0));
            setWillNotDraw(false);
        }

        void setPeer(int account, TLRPC.User user, TLRPC.Chat chat, long id) {
            AvatarDrawable drawable = new AvatarDrawable();
            if (user != null) {
                drawable.setInfo(account, user);
                avatar.setForUserOrChat(user, drawable);
            } else if (chat != null) {
                drawable.setInfo(account, chat);
                avatar.setForUserOrChat(chat, drawable);
            } else {
                drawable.setInfo(id, String.valueOf(Math.abs(id)), null);
                avatar.setImageDrawable(drawable);
            }
        }

        void set(CharSequence title, CharSequence sub, boolean divider) {
            titleView.setText(title);
            subView.setText(sub);
            this.divider = divider;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (divider && Theme.dividerPaint != null) {
                canvas.drawLine(AndroidUtilities.dp(70), getHeight() - 1, getWidth(), getHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    /** Базовая страница: шапка + прокручиваемый список карточек, перестраивается целиком. */
    abstract static class Page extends BaseFragment {
        protected LinearLayout root;

        protected abstract String title();

        protected abstract void fill(Context context, LinearLayout root);

        @Override
        public View createView(Context context) {
            actionBar.setBackButtonImage(R.drawable.ic_ab_back);
            actionBar.setAllowOverlayTitle(true);
            actionBar.setTitle(title());
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
            root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(32));
            scrollView.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
            fragmentView = scrollView;
            rebuild();
            return fragmentView;
        }

        protected void rebuild() {
            if (root == null) return;
            root.removeAllViews();
            fill(root.getContext(), root);
        }
    }

    // ---------------- Активные аккаунты ----------------

    public static class AccountsPage extends Page {

        @Override
        protected String title() {
            return "Активные аккаунты";
        }

        @Override
        protected void fill(Context context, LinearLayout root) {
            final TextMorphController c = TextMorphController.getInstance();
            final boolean all = c.getBool(TextMorphController.KEY_WORK_ALL_ACCOUNTS);

            header(root, "Управление профилями");
            LinearLayout top = card(context);
            TextCheckCell allCell = check(context, "Работать на всех аккаунтах", null, all, false);
            allCell.setOnClickListener(v -> {
                c.setBool(TextMorphController.KEY_WORK_ALL_ACCOUNTS, !all);
                rebuild();
            });
            top.addView(allCell);
            addCard(root, top, 6);
            if (all) {
                hint(root, "Стилизация применяется ко всем аккаунтам.");
                return;
            }
            hint(root, "Добавьте аккаунты, где будет работать стилизация.");

            final LinkedHashSet<Long> allowed = c.getAllowedAccounts();
            final ArrayList<Long> deviceIds = new ArrayList<>();
            ArrayList<int[]> slots = new ArrayList<>();
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (!UserConfig.isValidAccount(a)) continue;
                UserConfig uc = UserConfig.getInstance(a);
                if (!uc.isClientActivated() || uc.getClientUserId() == 0) continue;
                slots.add(new int[]{a});
            }
            if (!slots.isEmpty()) {
                header(root, "Аккаунты на этом устройстве");
                LinearLayout devCard = card(context);
                for (int i = 0; i < slots.size(); i++) {
                    final int a = slots.get(i)[0];
                    UserConfig uc = UserConfig.getInstance(a);
                    final long uid = uc.getClientUserId();
                    deviceIds.add(uid);
                    TLRPC.User user = uc.getCurrentUser();
                    String name = user != null ? UserObject.getUserName(user) : "";
                    if (TextUtils.isEmpty(name)) name = "Аккаунт #" + (a + 1);
                    if (a == UserConfig.selectedAccount) name += " • текущий";
                    String sub = (user != null && !TextUtils.isEmpty(user.username) ? "@" + user.username + " • " : "") + "ID: " + uid;
                    final boolean on = allowed.contains(uid);
                    PeerRow row = new PeerRow(context);
                    row.setPeer(a, user, null, uid);
                    row.set(name, (on ? "Включен" : "Подключить") + " • " + sub, i < slots.size() - 1);
                    Switch sw = new Switch(context);
                    sw.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
                    sw.setChecked(on, false);
                    sw.setFocusable(false);
                    sw.setClickable(false);
                    row.addView(sw, LayoutHelper.createLinear(37, 40));
                    row.setOnClickListener(v -> {
                        LinkedHashSet<Long> set = c.getAllowedAccounts();
                        if (on) {
                            set.remove(uid);
                        } else {
                            set.add(uid);
                        }
                        c.setAllowedAccounts(set);
                        toast(this, on ? "Аккаунт отключен от стилизации." : "Аккаунт успешно включен!");
                        rebuild();
                    });
                    devCard.addView(row);
                }
                addCard(root, devCard, 6);
                hint(root, "Нажмите на карточку аккаунта, чтобы включить или отключить стилизацию.");
            }

            ArrayList<Long> custom = new ArrayList<>();
            for (Long id : allowed) {
                if (!deviceIds.contains(id)) custom.add(id);
            }
            if (!custom.isEmpty()) {
                header(root, "Другие сохраненные ID");
                LinearLayout cc = card(context);
                for (int i = 0; i < custom.size(); i++) {
                    final long uid = custom.get(i);
                    TLRPC.User u = getMessagesController().getUser(uid);
                    String name = u != null ? UserObject.getUserName(u) : null;
                    PeerRow row = new PeerRow(context);
                    row.setPeer(currentAccount, u, null, uid);
                    row.set(!TextUtils.isEmpty(name) ? name : "ID аккаунта: " + uid,
                            (u != null && !TextUtils.isEmpty(u.username) ? "@" + u.username + " • " : "") + "ID: " + uid, i < custom.size() - 1);
                    row.setOnClickListener(v -> {
                        LinkedHashSet<Long> set = c.getAllowedAccounts();
                        set.remove(uid);
                        c.setAllowedAccounts(set);
                        toast(this, "Аккаунт отключен от стилизации.");
                        rebuild();
                    });
                    cc.addView(row);
                }
                addCard(root, cc, 6);
                hint(root, "Нажмите на аккаунт в списке, чтобы отключить его.");
            }

            header(root, "Действия");
            LinearLayout actions = card(context);
            MorphRow add = new MorphRow(context).set("Добавить по ID", "Ввести числовой ID другого аккаунта вручную", !allowed.isEmpty());
            add.setOnClickListener(v -> showAddIdDialog());
            actions.addView(add);
            if (!allowed.isEmpty()) {
                MorphRow clear = new MorphRow(context).set("Очистить список", null, false).red();
                clear.setOnClickListener(v -> {
                    c.setAllowedAccounts(new ArrayList<>());
                    toast(this, "Список аккаунтов очищен.");
                    rebuild();
                });
                actions.addView(clear);
            }
            addCard(root, actions, 24);
        }

        private void showAddIdDialog() {
            Context ctx = getParentActivity();
            if (ctx == null) return;
            final EditTextBoldCursor edit = styledEdit(ctx, "123456789", null, InputType.TYPE_CLASS_NUMBER);
            AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
            builder.setTitle("Добавить ID аккаунта");
            builder.setView(dialogLayout(ctx, edit));
            builder.setPositiveButton("Добавить", null);
            builder.setNegativeButton("Отмена", null);
            AlertDialog dialog = builder.create();
            dialog.setDismissDialogByButtons(false);
            showDialog(dialog);
            View btn = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (btn != null) btn.setOnClickListener(v -> dialog.dismiss());
            View pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) pos.setOnClickListener(v -> {
                long uid;
                try {
                    uid = Long.parseLong(edit.getText().toString().trim());
                } catch (Exception e) {
                    dialogError(edit, "Введен некорректный ID.");
                    return;
                }
                if (uid <= 0) {
                    dialogError(edit, "Введен некорректный ID.");
                    return;
                }
                TextMorphController c = TextMorphController.getInstance();
                LinkedHashSet<Long> set = c.getAllowedAccounts();
                if (!set.add(uid)) {
                    dialogError(edit, "Этот аккаунт уже включен.");
                    return;
                }
                c.setAllowedAccounts(set);
                dialog.dismiss();
                toast(this, "Аккаунт успешно включен!");
                rebuild();
            });
        }
    }

    // ---------------- Центр исключений ----------------

    public static class ExclusionsPage extends Page {

        private static final String[][] CHAT_FILTERS = {
                {TextMorphController.KEY_FILTER_PM, "Личные сообщения", "Применять оформление в диалогах один на один"},
                {TextMorphController.KEY_FILTER_GROUPS, "Группы и беседы", "Применять в обычных беседах и супергруппах"},
                {TextMorphController.KEY_FILTER_CHANNELS, "Каналы", "Применять в каналах — по умолчанию выключено для безопасности"},
                {TextMorphController.KEY_FILTER_SAVED, "Избранное", "Применять в ваших сохранённых сообщениях"},
        };
        private static final String[][] CONTENT_FILTERS = {
                {TextMorphController.KEY_SKIP_LINKS, "Игнорировать ссылки", "Пропускать сообщения со ссылками http, https, t.me, сохраняя кликабельность"},
                {TextMorphController.KEY_SKIP_MENTIONS, "Игнорировать юзернеймы", "Не форматировать сообщения с тегами пользователей и каналов"},
                {TextMorphController.KEY_SKIP_COMMANDS, "Игнорировать команды ботов", "Пропускать служебные команды со слэшем: /start, /help…"},
                {TextMorphController.KEY_SKIP_HASHTAGS, "Игнорировать хэштеги", "Пропускать сообщения с хэштегами для сохранения поиска"},
                {TextMorphController.KEY_QUICK_BYPASS, "Быстрый обход", "Символ в начале сообщения отключает стили"},
                {TextMorphController.KEY_SKIP_EMOJIS, "Игнорировать только эмодзи", "Не форматировать сообщения, состоящие только из смайликов"},
        };

        @Override
        protected String title() {
            return "Центр исключений";
        }

        private void addToggles(Context context, LinearLayout root, String title, String[][] rows) {
            final TextMorphController c = TextMorphController.getInstance();
            header(root, title);
            LinearLayout card = card(context);
            for (int i = 0; i < rows.length; i++) {
                final String key = rows[i][0];
                final TextCheckCell cell = check(context, rows[i][1], rows[i][2], c.getBool(key), i < rows.length - 1);
                cell.setOnClickListener(v -> {
                    boolean nv = !c.getBool(key);
                    c.setBool(key, nv);
                    cell.setChecked(nv);
                });
                card.addView(cell);
            }
            addCard(root, card, 16);
        }

        @Override
        protected void fill(Context context, LinearLayout root) {
            final TextMorphController c = TextMorphController.getInstance();
            addToggles(context, root, "Где применять стилизацию", CHAT_FILTERS);
            addToggles(context, root, "Умное игнорирование контента", CONTENT_FILTERS);
            LinearLayout bypassCard = card(context);
            MorphRow bypassRow = new MorphRow(context).set("Символ обхода", "«" + c.getBypassPrefix() + "»", false);
            bypassRow.setOnClickListener(v -> showBypassDialog());
            bypassCard.addView(bypassRow);
            addCard(root, bypassCard, 16);

            header(root, "Конкретные чаты-исключения");
            LinearLayout addCard = card(context);
            MorphRow pick = new MorphRow(context).set("Выбрать чат", "Найти чат в списке диалогов", true);
            pick.setOnClickListener(v -> openChatPicker());
            addCard.addView(pick);
            MorphRow byId = new MorphRow(context).set("Добавить по ID", "Ввести ID чата вручную", false);
            byId.setOnClickListener(v -> showAddChatDialog());
            addCard.addView(byId);
            addCard(root, addCard, 16);

            header(root, "Список исключений");
            LinearLayout list = card(context);
            ArrayList<Long> ids = new ArrayList<>(c.getExcludedChats());
            if (ids.isEmpty()) {
                list.addView(new MorphRow(context).set("Список исключений пока пуст", "Сообщения в исключённых чатах отправляются без стилей и трансформаций", false));
                addCard(root, list, 24);
                return;
            }
            for (int i = 0; i < ids.size(); i++) {
                final long did = ids.get(i);
                PeerRow row = new PeerRow(context);
                TLRPC.User user = did > 0 ? getMessagesController().getUser(did) : null;
                TLRPC.Chat chat = did < 0 ? getMessagesController().getChat(-did) : null;
                String name = user != null ? UserObject.getUserName(user) : chat != null ? chat.title : null;
                String username = user != null ? user.username : chat != null ? chat.username : null;
                row.setPeer(currentAccount, user, chat, did);
                row.set(!TextUtils.isEmpty(name) ? name : "ID: " + did,
                        "ID: " + did + (!TextUtils.isEmpty(username) ? " • @" + username : ""), i < ids.size() - 1);
                row.setOnClickListener(v -> {
                    c.setChatExcluded(did, false);
                    toast(this, "Удалено из исключений.");
                    rebuild();
                });
                list.addView(row);
            }
            addCard(root, list, 6);
            hint(root, "Нажмите на чат в списке, чтобы удалить его из списка исключений.");
            LinearLayout clearCard = card(context);
            MorphRow clear = new MorphRow(context).set("Очистить весь список", null, false).red();
            clear.setOnClickListener(v -> {
                for (Long id : c.getExcludedChats()) {
                    c.setChatExcluded(id, false);
                }
                toast(this, "Список полностью очищен.");
                rebuild();
            });
            clearCard.addView(clear);
            addCard(root, clearCard, 24);
        }

        private void openChatPicker() {
            Bundle args = new Bundle();
            args.putBoolean("onlySelect", true);
            args.putBoolean("checkCanWrite", false);
            args.putInt("dialogsType", 3);
            DialogsActivity fragment = new DialogsActivity(args);
            fragment.setDelegate((fragment1, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
                if (!dids.isEmpty()) {
                    TextMorphController.getInstance().setChatExcluded(dids.get(0).dialogId, true);
                    rebuild();
                }
                fragment1.finishFragment();
                return true;
            });
            presentFragment(fragment);
        }

        private void showBypassDialog() {
            Context ctx = getParentActivity();
            if (ctx == null) return;
            final TextMorphController c = TextMorphController.getInstance();
            final EditTextBoldCursor edit = styledEdit(ctx, "Например: !! или ~", c.getBypassPrefix(), InputType.TYPE_CLASS_TEXT);
            edit.setSingleLine(true);
            AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
            builder.setTitle("Символ обхода");
            builder.setView(dialogLayout(ctx, edit));
            builder.setPositiveButton("Сохранить", (d, w) -> {
                String val = edit.getText().toString().trim();
                c.setBypassPrefix(val);
                toast(this, "Символ обхода сохранён!");
                rebuild();
            });
            builder.setNeutralButton("Сбросить", (d, w) -> {
                c.setBypassPrefix(null);
                rebuild();
            });
            builder.setNegativeButton("Отмена", null);
            showDialog(builder.create());
        }

        private void showAddChatDialog() {
            Context ctx = getParentActivity();
            if (ctx == null) return;
            final EditTextBoldCursor edit = styledEdit(ctx, "-100123456789", null, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
            builder.setTitle("Добавить чат-исключение");
            builder.setView(dialogLayout(ctx, edit));
            builder.setPositiveButton("Добавить", null);
            builder.setNegativeButton("Отмена", null);
            AlertDialog dialog = builder.create();
            dialog.setDismissDialogByButtons(false);
            showDialog(dialog);
            View neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (neg != null) neg.setOnClickListener(v -> dialog.dismiss());
            View pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) pos.setOnClickListener(v -> {
                String text = edit.getText().toString().trim();
                long id;
                try {
                    id = Long.parseLong(text);
                } catch (Exception e) {
                    dialogError(edit, "Введен некорректный ID.");
                    return;
                }
                if (id == 0) {
                    dialogError(edit, "Введен некорректный ID.");
                    return;
                }
                // формат Bot API (-100…) → внутренний id диалога
                if (id < -1000000000000L) {
                    id = -(-id - 1000000000000L);
                }
                TextMorphController.getInstance().setChatExcluded(id, true);
                dialog.dismiss();
                toast(this, "Чат добавлен в исключения.");
                rebuild();
            });
        }
    }

    // ---------------- Leet Speak ----------------

    public static class LeetPage extends Page {

        @Override
        protected String title() {
            return "Leet Speak";
        }

        @Override
        protected void fill(Context context, LinearLayout root) {
            final TextMorphController c = TextMorphController.getInstance();
            header(root, "Leet Speak");
            LinearLayout top = card(context);
            final TextCheckCell enable = check(context, "Включить модификатор", null, c.isLeetSpeak(), false);
            enable.setOnClickListener(v -> {
                boolean nv = !c.isLeetSpeak();
                c.setLeetSpeak(nv);
                enable.setChecked(nv);
            });
            top.addView(enable);
            addCard(root, top, 16);

            header(root, "Добавление замены");
            LinearLayout addCard = card(context);
            MorphRow en = new MorphRow(context).set("Добавить английскую букву", "Введите новую букву и символ", true);
            en.setOnClickListener(v -> showAddDialog(false));
            addCard.addView(en);
            MorphRow ru = new MorphRow(context).set("Добавить русскую букву", "Введите новую букву и символ", false);
            ru.setOnClickListener(v -> showAddDialog(true));
            addCard.addView(ru);
            addCard(root, addCard, 16);

            header(root, "Замены");
            LinearLayout list = card(context);
            final TreeMap<String, String> map = c.getLeetMappings();
            if (map.isEmpty()) {
                list.addView(new MorphRow(context).set("Замен пока нет", null, false));
            } else {
                int i = 0;
                for (Map.Entry<String, String> e : map.entrySet()) {
                    final String key = e.getKey();
                    final String rep = e.getValue();
                    MorphRow row = new MorphRow(context).set(key + "  ➔  " + rep, null, ++i < map.size());
                    row.setOnClickListener(v -> {
                        TreeMap<String, String> m = c.getLeetMappings();
                        m.remove(key);
                        c.setLeetMappings(m);
                        toast(this, "Замена удалена.");
                        rebuild();
                    });
                    row.setOnLongClickListener(v -> {
                        showEditDialog(key, rep);
                        return true;
                    });
                    list.addView(row);
                }
            }
            addCard(root, list, 6);
            hint(root, "Нажмите, чтобы удалить. Зажмите, чтобы изменить символ.");

            LinearLayout resetCard = card(context);
            MorphRow reset = new MorphRow(context).set("Сбросить по умолчанию", null, false);
            reset.setOnClickListener(v -> {
                c.resetLeetMappings();
                toast(this, "Настройки Leet Speak сброшены.");
                rebuild();
            });
            resetCard.addView(reset);
            addCard(root, resetCard, 24);
        }

        private void showEditDialog(String key, String rep) {
            Context ctx = getParentActivity();
            if (ctx == null) return;
            final EditTextBoldCursor edit = styledEdit(ctx, "Символ, например 4 или @", rep, InputType.TYPE_CLASS_TEXT);
            AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
            builder.setTitle("Редактирование " + key);
            builder.setView(dialogLayout(ctx, edit));
            builder.setPositiveButton("Сохранить", (d, w) -> {
                String nv = edit.getText().toString().trim();
                if (!nv.isEmpty()) {
                    TextMorphController c = TextMorphController.getInstance();
                    TreeMap<String, String> m = c.getLeetMappings();
                    m.put(key, nv);
                    c.setLeetMappings(m);
                    toast(this, "Замена успешно изменена!");
                    rebuild();
                }
            });
            builder.setNegativeButton("Отмена", null);
            showDialog(builder.create());
        }

        private void showAddDialog(boolean russian) {
            Context ctx = getParentActivity();
            if (ctx == null) return;
            final EditTextBoldCursor charEdit = styledEdit(ctx, russian ? "Буква, например А" : "Буква, например A", null, InputType.TYPE_CLASS_TEXT);
            final EditTextBoldCursor repEdit = styledEdit(ctx, "Символ, например 4 или @", null, InputType.TYPE_CLASS_TEXT);
            AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
            builder.setTitle("Добавление замены");
            builder.setView(dialogLayout(ctx, charEdit, repEdit));
            builder.setPositiveButton("Добавить", null);
            builder.setNegativeButton("Отмена", null);
            AlertDialog dialog = builder.create();
            dialog.setDismissDialogByButtons(false);
            showDialog(dialog);
            View neg = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (neg != null) neg.setOnClickListener(v -> dialog.dismiss());
            View pos = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) pos.setOnClickListener(v -> {
                String ch = charEdit.getText().toString().trim().toUpperCase();
                String rep = repEdit.getText().toString().trim();
                if (ch.length() != 1) {
                    dialogError(charEdit, "Ошибка: буква должна состоять ровно из 1 символа.");
                    return;
                }
                char x = ch.charAt(0);
                if (!Character.isLetter(x)) {
                    dialogError(charEdit, "Ошибка: разрешены только буквы.");
                    return;
                }
                if (!russian && !(x >= 'A' && x <= 'Z')) {
                    dialogError(charEdit, "Ошибка: ожидается английская буква.");
                    return;
                }
                if (russian && !((x >= 'А' && x <= 'Я') || x == 'Ё')) {
                    dialogError(charEdit, "Ошибка: ожидается русская буква.");
                    return;
                }
                if (rep.isEmpty()) {
                    dialogError(repEdit, "Введите символ замены.");
                    return;
                }
                TextMorphController c = TextMorphController.getInstance();
                TreeMap<String, String> m = c.getLeetMappings();
                if (m.containsKey(ch)) {
                    dialogError(charEdit, "Ошибка: эта буква уже есть в списке.");
                    return;
                }
                m.put(ch, rep);
                c.setLeetMappings(m);
                dialog.dismiss();
                toast(this, "Замена успешно добавлена!");
                rebuild();
            });
        }
    }
}
