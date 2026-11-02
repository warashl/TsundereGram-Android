package com.exteragram.messenger.badges;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import com.exteragram.messenger.api.dto.BadgeDTO;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Switch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.UUID;

public class BadgeManageActivity extends BaseFragment {

    private static final int REQ_PICK_BADGE_IMAGE = 1005;
    private static final int MENU_ADMINS = 1;

    private long targetUserId;
    private TLRPC.User targetUser;

    private EditTextBoldCursor idEditText;
    private ImageView badgePreviewView;
    private TextView badgePreviewHint;
    private EditTextBoldCursor textEditText;
    private TextView saveButton;
    private TextView deleteButton;

    private LinearLayout adminRow;
    private TextView adminStatusTextView;
    private TextView adminActionButton;

    private LinearLayout removeBgRow;
    private Switch removeBgSwitch;
    private boolean autoRemoveBg = true;

    private File pickedImageFile;
    private Bitmap pickedBitmap;
    private Bitmap originalPickedBitmap;
    private Bitmap noBgPickedBitmap;

    private String currentBadgeUrl;
    private String currentBadgeType;
    private boolean isEditMode;

    public BadgeManageActivity() {
        this(0, null);
    }

    public BadgeManageActivity(long targetUserId, TLRPC.User targetUser) {
        this.targetUserId = targetUserId;
        this.targetUser = targetUser;
    }

    @Override
    public boolean onFragmentCreate() {
        if (targetUserId != 0 && targetUser == null) {
            targetUser = getMessagesController().getUser(targetUserId);
        }
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Выдать значок");

        long myId = UserConfig.getInstance(currentAccount).getClientUserId();
        boolean isCreator = BadgesController.isCreator(myId);

        if (isCreator) {
            ActionBarMenu menu = actionBar.createMenu();
            menu.addItem(MENU_ADMINS, R.drawable.msg_admins);
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_ADMINS) {
                    showAdminsDialog();
                }
            }
        });

        fragmentView = new ScrollView(context);
        ScrollView scrollView = (ScrollView) fragmentView;
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(32) + AndroidUtilities.navigationBarHeight);
        ((ScrollView) fragmentView).addView(container, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 1. Карточка пользователя
        LinearLayout userCard = new LinearLayout(context);
        userCard.setOrientation(LinearLayout.VERTICAL);
        userCard.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        userCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));

        LinearLayout userHeaderRow = new LinearLayout(context);
        userHeaderRow.setOrientation(LinearLayout.HORIZONTAL);
        userHeaderRow.setGravity(Gravity.CENTER_VERTICAL);

        if (targetUser != null || targetUserId != 0) {
            BackupImageView avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(AndroidUtilities.dp(24));
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            if (targetUser != null) {
                avatarDrawable.setInfo(currentAccount, targetUser);
                avatarView.setForUserOrChat(targetUser, avatarDrawable);
            } else {
                avatarDrawable.setInfo(targetUserId, "User", null);
                avatarView.setImageDrawable(avatarDrawable);
            }
            userHeaderRow.addView(avatarView, LayoutHelper.createLinear(48, 48));

            LinearLayout userInfo = new LinearLayout(context);
            userInfo.setOrientation(LinearLayout.VERTICAL);
            userInfo.setPadding(AndroidUtilities.dp(14), 0, 0, 0);

            TextView nameView = new TextView(context);
            nameView.setText(targetUser != null ? UserObject.getUserName(targetUser) : "Пользователь");
            nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            nameView.setTypeface(AndroidUtilities.bold());
            userInfo.addView(nameView);

            TextView idView = new TextView(context);
            idView.setText("ID: " + (targetUser != null ? targetUser.id : targetUserId));
            idView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            idView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            userInfo.addView(idView);

            userHeaderRow.addView(userInfo, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            userCard.addView(userHeaderRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        } else {
            LinearLayout inputLayout = new LinearLayout(context);
            inputLayout.setOrientation(LinearLayout.VERTICAL);

            TextView label = new TextView(context);
            label.setText("Telegram ID пользователя:");
            label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            label.setTypeface(AndroidUtilities.bold());
            inputLayout.addView(label, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));

            idEditText = new EditTextBoldCursor(context);
            idEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            idEditText.setInputType(InputType.TYPE_CLASS_NUMBER);
            idEditText.setHint("Например: 5133421262");
            idEditText.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourceProvider));
            idEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            idEditText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourceProvider));
            idEditText.setBackground(null);
            idEditText.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    try {
                        long typedId = Long.parseLong(s.toString().trim());
                        loadBadgeForUser(typedId);
                        updateAdminRowForUser(typedId);
                    } catch (Exception ignored) {}
                }
            });
            inputLayout.addView(idEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            userHeaderRow.addView(inputLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            userCard.addView(userHeaderRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        // Кнопка управления правами администратора значков для создателя
        if (isCreator) {
            View divider = new View(context);
            divider.setBackgroundColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider), 0.08f));
            userCard.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 0, 14, 0, 12));

            adminRow = new LinearLayout(context);
            adminRow.setOrientation(LinearLayout.VERTICAL);

            adminStatusTextView = new TextView(context);
            adminStatusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13.5f);
            adminStatusTextView.setTypeface(AndroidUtilities.bold());
            adminRow.addView(adminStatusTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

            adminActionButton = new TextView(context);
            adminActionButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            adminActionButton.setTypeface(AndroidUtilities.bold());
            adminActionButton.setGravity(Gravity.CENTER);
            adminActionButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(10));
            adminRow.addView(adminActionButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            userCard.addView(adminRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            long initUid = targetUserId != 0 ? targetUserId : (targetUser != null ? targetUser.id : 0);
            updateAdminRowForUser(initUid);
        }

        container.addView(userCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // 3. Карточка загрузки кастомного значка
        LinearLayout badgeCard = new LinearLayout(context);
        badgeCard.setOrientation(LinearLayout.VERTICAL);
        badgeCard.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        badgeCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        TextView badgeTitle = new TextView(context);
        badgeTitle.setText("Кастомный значок или персонаж");
        badgeTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        badgeTitle.setTypeface(AndroidUtilities.bold());
        badgeTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        badgeCard.addView(badgeTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        LinearLayout previewRow = new LinearLayout(context);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout previewContainer = new FrameLayout(context);
        previewContainer.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider)));
        badgePreviewView = new ImageView(context);
        badgePreviewView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewContainer.addView(badgePreviewView, LayoutHelper.createFrame(46, 46, Gravity.CENTER));
        previewRow.addView(previewContainer, LayoutHelper.createLinear(64, 64));

        LinearLayout previewMeta = new LinearLayout(context);
        previewMeta.setOrientation(LinearLayout.VERTICAL);
        previewMeta.setPadding(AndroidUtilities.dp(14), 0, 0, 0);

        badgePreviewHint = new TextView(context);
        badgePreviewHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14.5f);
        badgePreviewHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        badgePreviewHint.setTypeface(AndroidUtilities.bold());
        previewMeta.addView(badgePreviewHint);

        TextView subHint = new TextView(context);
        subHint.setText("Автоматически вырезает персонажа без фона");
        subHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        subHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        previewMeta.addView(subHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));

        previewRow.addView(previewMeta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        badgeCard.addView(previewRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        TextView pickButton = new TextView(context);
        pickButton.setText("Выбрать изображение персонажа / значка");
        pickButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        pickButton.setTypeface(AndroidUtilities.bold());
        pickButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        pickButton.setGravity(Gravity.CENTER);
        pickButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        pickButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        pickButton.setOnClickListener(v -> openImagePicker());
        badgeCard.addView(pickButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Переключатель: Автоматическое вырезание персонажа (Убрать фон)
        removeBgRow = new LinearLayout(context);
        removeBgRow.setOrientation(LinearLayout.HORIZONTAL);
        removeBgRow.setGravity(Gravity.CENTER_VERTICAL);
        removeBgRow.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        removeBgRow.setClickable(true);
        removeBgRow.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider), 0.05f),
                Theme.getColor(Theme.key_listSelector, resourceProvider)));

        ImageView cutIcon = new ImageView(context);
        cutIcon.setImageResource(R.drawable.msg_edit);
        cutIcon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourceProvider));
        cutIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        removeBgRow.addView(cutIcon, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL, 0, 0, 12, 0));

        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);

        TextView removeBgLabel = new TextView(context);
        removeBgLabel.setText("Вырезать объект (Убрать фон)");
        removeBgLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        removeBgLabel.setTypeface(AndroidUtilities.bold());
        removeBgLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        textLayout.addView(removeBgLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView removeBgSub = new TextView(context);
        removeBgSub.setText("Умное выделение силуэта персонажа без лишних краёв");
        removeBgSub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        removeBgSub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        textLayout.addView(removeBgSub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        removeBgRow.addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        removeBgSwitch = new Switch(context);
        removeBgSwitch.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        removeBgSwitch.setChecked(autoRemoveBg, false);
        removeBgRow.addView(removeBgSwitch, LayoutHelper.createLinear(37, 20, Gravity.CENTER_VERTICAL));

        removeBgRow.setOnClickListener(v -> {
            autoRemoveBg = !autoRemoveBg;
            removeBgSwitch.setChecked(autoRemoveBg, true);
            applyBackgroundRemovalToggle();
        });
        badgeCard.addView(removeBgRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        container.addView(badgeCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // 4. Карточка текста описания
        LinearLayout textCard = new LinearLayout(context);
        textCard.setOrientation(LinearLayout.VERTICAL);
        textCard.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        textCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        TextView textTitle = new TextView(context);
        textTitle.setText("Текст подсказки (при клике на значок)");
        textTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textTitle.setTypeface(AndroidUtilities.bold());
        textTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        textCard.addView(textTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        textEditText = new EditTextBoldCursor(context);
        textEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textEditText.setHint("Например: Значок разработчика");
        textEditText.setHintColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText, resourceProvider));
        textEditText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        textEditText.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated, resourceProvider));
        textEditText.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10),
                Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider), 0.05f)));
        textEditText.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        textCard.addView(textEditText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        container.addView(textCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

        // 5. Кнопки сохранения и удаления
        saveButton = new TextView(context);
        saveButton.setText("Сохранить изменения");
        saveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        saveButton.setTypeface(AndroidUtilities.bold());
        saveButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        saveButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        saveButton.setOnClickListener(v -> saveBadge());
        container.addView(saveButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        deleteButton = new TextView(context);
        deleteButton.setText("Удалить значок у пользователя");
        deleteButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        deleteButton.setTypeface(AndroidUtilities.bold());
        deleteButton.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
        deleteButton.setGravity(Gravity.CENTER);
        deleteButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Color.argb(25, 255, 0, 0), Color.argb(45, 255, 0, 0)));
        deleteButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        deleteButton.setVisibility(View.GONE);
        deleteButton.setOnClickListener(v -> {
            long checkUid = getTargetUid();
            if (checkUid != 0) {
                deleteBadge(checkUid);
            }
        });
        container.addView(deleteButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        long initUid = getTargetUid();
        loadBadgeForUser(initUid);

        return fragmentView;
    }



    private long getTargetUid() {
        if (targetUserId != 0) return targetUserId;
        if (targetUser != null) return targetUser.id;
        if (idEditText != null) {
            try {
                return Long.parseLong(idEditText.getText().toString().trim());
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private void updateAdminRowForUser(long uid) {
        if (adminRow == null || uid == 0 || uid == BadgesController.DEV_ID_1) {
            if (adminRow != null) adminRow.setVisibility(View.GONE);
            return;
        }
        adminRow.setVisibility(View.VISIBLE);
        boolean isAdmin = BadgesController.INSTANCE.isBadgeAdmin(uid);
        if (isAdmin) {
            adminStatusTextView.setText("Роль: Администратор значков (активен)");
            adminStatusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider));
            adminActionButton.setText("Отозвать права администратора");
            adminActionButton.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
            adminActionButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10),
                    Color.argb(25, 255, 60, 60), Color.argb(45, 255, 60, 60)));
            adminActionButton.setOnClickListener(v -> toggleAdminRole(uid, false));
        } else {
            adminStatusTextView.setText("Роль: Обычный пользователь (без прав)");
            adminStatusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            adminActionButton.setText("Сделать администратором значков");
            adminActionButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
            adminActionButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10),
                    Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                    Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
            adminActionButton.setOnClickListener(v -> toggleAdminRole(uid, true));
        }
    }

    private void toggleAdminRole(long uid, boolean makeAdmin) {
        AlertDialog progress = new AlertDialog(getParentActivity(), 3);
        progress.setMessage(makeAdmin ? "Назначение администратора…" : "Отзыв прав администратора…");
        progress.setCanCancel(false);
        progress.show();

        BadgesController.INSTANCE.setBadgeAdmin(uid, makeAdmin, () -> {
            try { progress.dismiss(); } catch (Exception ignored) {}
            updateAdminRowForUser(uid);
            BulletinFactory.of(this).createSimpleBulletin(
                    ContextCompat.getDrawable(getParentActivity(), makeAdmin ? R.drawable.msg_admins : R.drawable.msg_delete),
                    "Администраторы значков",
                    makeAdmin ? "Пользователь успешно назначен админом!" : "Права администратора отозваны!"
            ).show();
        }, err -> {
            try { progress.dismiss(); } catch (Exception ignored) {}
            Toast.makeText(getParentActivity(), "Ошибка: " + err, Toast.LENGTH_SHORT).show();
        });
    }

    private void showAdminsDialog() {
        Context context = getParentActivity();
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Управление администраторами значков");

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(12), AndroidUtilities.dp(20), AndroidUtilities.dp(8));

        TextView hint = new TextView(context);
        hint.setText("Введите Telegram ID пользователя, чтобы выдать или отозвать права администратора значков в реальном времени:");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        hint.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        layout.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        EditTextBoldCursor input = new EditTextBoldCursor(context);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Telegram ID");
        input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        input.setHintColor(Theme.getColor(Theme.key_dialogTextGray2, resourceProvider));
        layout.addView(input, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        builder.setView(layout);
        builder.setPositiveButton("Сделать админом", (dialog, which) -> {
            try {
                long uid = Long.parseLong(input.getText().toString().trim());
                toggleAdminRole(uid, true);
            } catch (Exception e) {
                Toast.makeText(context, "Некорректный Telegram ID", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отозвать права", (dialog, which) -> {
            try {
                long uid = Long.parseLong(input.getText().toString().trim());
                toggleAdminRole(uid, false);
            } catch (Exception e) {
                Toast.makeText(context, "Некорректный Telegram ID", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNeutralButton("Закрыть", null);
        showDialog(builder.create());
    }

    private void loadBadgeForUser(long uid) {
        BadgeDTO existingBadge = uid != 0 ? BadgesController.INSTANCE.getBadgeFor(uid) : null;
        isEditMode = existingBadge != null;

        if (actionBar != null) {
            actionBar.setTitle(isEditMode ? "Редактировать значок" : "Выдать значок");
        }
        if (saveButton != null) {
            saveButton.setText(isEditMode ? "Сохранить изменения" : "Сохранить изменения");
        }
        if (deleteButton != null) {
            deleteButton.setVisibility(isEditMode ? View.VISIBLE : View.GONE);
        }

        if (existingBadge != null) {
            currentBadgeUrl = existingBadge.getImageUrl();
            if (!TextUtils.isEmpty(existingBadge.getText())) {
                textEditText.setText(existingBadge.getText());
            }
            if (existingBadge.hasImageUrl()) {
                currentBadgeType = null;
                badgePreviewHint.setText("Текущий значок установлен");
                Bitmap bmp = BadgesController.INSTANCE.getOrDownloadBadgeBitmap(existingBadge.getImageUrl());
                if (bmp != null) {
                    badgePreviewView.setImageBitmap(bmp);
                } else {
                    badgePreviewView.setImageDrawable(BadgesController.INSTANCE.getCustomBadgeDrawable(existingBadge, 28));
                }
            } else {
                currentBadgeType = BadgesController.typeForDocId(existingBadge.getDocumentId());
                badgePreviewHint.setText("Текущий значок: " + currentBadgeType);
                badgePreviewView.setImageDrawable(BadgesController.INSTANCE.getCustomBadgeDrawable(existingBadge, 28));
            }
        } else {
            currentBadgeUrl = null;
            currentBadgeType = null;
            if (pickedImageFile == null) {
                badgePreviewHint.setText("Значок не выбран");
                badgePreviewView.setImageResource(R.drawable.msg_theme);
                if (textEditText != null && TextUtils.isEmpty(textEditText.getText())) {
                    textEditText.setText("Значок TsundereGram");
                }
            }
        }
    }

    private void openImagePicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/png", "image/webp", "image/jpeg"});
            startActivityForResult(intent, REQ_PICK_BADGE_IMAGE);
        } catch (Exception e) {
            FileLog.e(e);
            try {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("image/*");
                startActivityForResult(intent, REQ_PICK_BADGE_IMAGE);
            } catch (Exception ex) {
                FileLog.e(ex);
            }
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK_BADGE_IMAGE && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            Utilities.globalQueue.postRunnable(() -> {
                try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri)) {
                    if (in == null) return;
                    Bitmap bmp = BitmapFactory.decodeStream(in);
                    if (bmp == null) return;

                    originalPickedBitmap = bmp;
                    trySegmentSubject(bmp, segmentedBmp -> {
                        noBgPickedBitmap = segmentedBmp != null ? segmentedBmp : removeBackground(bmp);
                        AndroidUtilities.runOnUIThread(() -> {
                            currentBadgeType = null;
                            applyBackgroundRemovalToggle();
                        });
                    });
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }
    }

    private void trySegmentSubject(Bitmap bmp, java.util.function.Consumer<Bitmap> onDone) {
        try {
            SubjectSegmenterOptions options = new SubjectSegmenterOptions.Builder()
                    .enableForegroundBitmap()
                    .build();
            SubjectSegmenter segmenter = SubjectSegmentation.getClient(options);
            InputImage inputImage = InputImage.fromBitmap(bmp, 0);
            segmenter.process(inputImage)
                    .addOnSuccessListener(result -> {
                        Bitmap fg = result.getForegroundBitmap();
                        if (fg != null) {
                            int w = fg.getWidth();
                            int h = fg.getHeight();
                            int[] pixels = new int[w * h];
                            fg.getPixels(pixels, 0, w, 0, 0, w, h);
                            Bitmap cropped = cropVisibleContent(fg, pixels, w, h);
                            onDone.accept(cropped != null ? cropped : fg);
                        } else {
                            onDone.accept(null);
                        }
                    })
                    .addOnFailureListener(e -> {
                        FileLog.e("MLKit SubjectSegmentation fallback: " + e);
                        onDone.accept(null);
                    });
        } catch (Throwable t) {
            FileLog.e("MLKit SubjectSegmentation exception: " + t);
            onDone.accept(null);
        }
    }

    private void applyBackgroundRemovalToggle() {
        if (originalPickedBitmap == null) return;

        pickedBitmap = (autoRemoveBg && noBgPickedBitmap != null) ? noBgPickedBitmap : originalPickedBitmap;

        // Сохраняем во временный PNG файл для последующей загрузки
        try {
            File tempFile = new File(AndroidUtilities.getCacheDir(), "picked_badge_" + UUID.randomUUID() + ".png");
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                pickedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.flush();
            }
            pickedImageFile = tempFile;
        } catch (Exception e) {
            FileLog.e(e);
        }

        if (badgePreviewView != null) {
            badgePreviewView.setImageBitmap(pickedBitmap);
        }
        if (badgePreviewHint != null) {
            badgePreviewHint.setText(autoRemoveBg ? "Персонаж вырезан" : "Оригинал (" + pickedBitmap.getWidth() + "x" + pickedBitmap.getHeight() + ")");
        }
    }

    /**
     * Алгоритм автоматического удаления фона вокруг персонажа (flood-fill прозрачности)
     * с последующей центровкой и кадрированием до границ объекта.
     */
    public static Bitmap removeBackground(Bitmap src) {
        if (src == null) return null;
        int width = src.getWidth();
        int height = src.getHeight();
        Bitmap result = src.copy(Bitmap.Config.ARGB_8888, true);

        int[] pixels = new int[width * height];
        result.getPixels(pixels, 0, width, 0, 0, width, height);

        int transparentCount = 0;
        for (int p : pixels) {
            if (Color.alpha(p) < 20) {
                transparentCount++;
            }
        }
        if (transparentCount > (width * height) * 0.01) {
            return cropVisibleContent(result, pixels, width, height);
        }

        // Цвета по 4 углам изображения
        int cTL = pixels[0];
        int cTR = pixels[width - 1];
        int cBL = pixels[(height - 1) * width];
        int cBR = pixels[(height - 1) * width + width - 1];

        boolean[] visited = new boolean[width * height];
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        final double TOLERANCE = 36.0;

        for (int x = 0; x < width; x++) {
            queue.add(x);
            visited[x] = true;
            int bIdx = (height - 1) * width + x;
            queue.add(bIdx);
            visited[bIdx] = true;
        }
        for (int y = 0; y < height; y++) {
            int lIdx = y * width;
            if (!visited[lIdx]) {
                queue.add(lIdx);
                visited[lIdx] = true;
            }
            int rIdx = y * width + (width - 1);
            if (!visited[rIdx]) {
                queue.add(rIdx);
                visited[rIdx] = true;
            }
        }

        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            int idx = queue.poll();
            int curColor = pixels[idx];

            if (Color.alpha(curColor) == 0) {
                continue;
            }

            boolean match = isSimilarColor(curColor, cTL, TOLERANCE)
                    || isSimilarColor(curColor, cTR, TOLERANCE)
                    || isSimilarColor(curColor, cBL, TOLERANCE)
                    || isSimilarColor(curColor, cBR, TOLERANCE);

            if (match) {
                pixels[idx] = 0; // Полная прозрачность

                int cx = idx % width;
                int cy = idx / width;

                for (int i = 0; i < 4; i++) {
                    int nx = cx + dx[i];
                    int ny = cy + dy[i];
                    if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                        int nIdx = ny * width + nx;
                        if (!visited[nIdx]) {
                            visited[nIdx] = true;
                            queue.add(nIdx);
                        }
                    }
                }
            }
        }

        result.setPixels(pixels, 0, width, 0, 0, width, height);
        return cropVisibleContent(result, pixels, width, height);
    }

    private static Bitmap cropVisibleContent(Bitmap result, int[] pixels, int width, int height) {
        int minX = width, maxX = 0, minY = height, maxY = 0;
        boolean hasVisibleContent = false;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if ((pixels[y * width + x] >>> 24) > 15) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    hasVisibleContent = true;
                }
            }
        }

        if (hasVisibleContent && maxX > minX && maxY > minY) {
            int pad = Math.max(2, (maxX - minX) / 25);
            minX = Math.max(0, minX - pad);
            minY = Math.max(0, minY - pad);
            maxX = Math.min(width - 1, maxX + pad);
            maxY = Math.min(height - 1, maxY + pad);
            int cropW = maxX - minX + 1;
            int cropH = maxY - minY + 1;
            return Bitmap.createBitmap(result, minX, minY, cropW, cropH);
        }

        return result;
    }

    private static boolean isSimilarColor(int c1, int c2, double tolerance) {
        int a1 = Color.alpha(c1);
        int a2 = Color.alpha(c2);
        if (a1 < 20 && a2 < 20) return true;
        if (a1 < 20 || a2 < 20) return false;
        int rDiff = Color.red(c1) - Color.red(c2);
        int gDiff = Color.green(c1) - Color.green(c2);
        int bDiff = Color.blue(c1) - Color.blue(c2);
        return Math.sqrt(rDiff * rDiff + gDiff * gDiff + bDiff * bDiff) <= tolerance;
    }

    private void saveBadge() {
        long finalUserId = getTargetUid();
        if (finalUserId == 0) {
            Toast.makeText(getParentActivity(), "Введите корректный Telegram ID!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (pickedImageFile == null && TextUtils.isEmpty(currentBadgeUrl) && TextUtils.isEmpty(currentBadgeType)) {
            Toast.makeText(getParentActivity(), "Пожалуйста, выберите изображение или пресет значка!", Toast.LENGTH_SHORT).show();
            return;
        }

        final long targetId = finalUserId;
        final String text = textEditText != null && textEditText.getText() != null ? textEditText.getText().toString().trim() : "Значок TsundereGram";

        AlertDialog progress = new AlertDialog(getParentActivity(), 3);
        progress.setMessage(isEditMode ? "Сохранение изменений значка…" : "Загрузка значка в TsundereGram…");
        progress.setCanCancel(false);
        progress.setCanceledOnTouchOutside(false);
        progress.show();

        Utilities.globalQueue.postRunnable(() -> {
            try {
                String badgeUrl = currentBadgeUrl;
                String badgeType = currentBadgeType;

                if (pickedImageFile != null && pickedImageFile.exists()) {
                    badgeUrl = BadgesController.uploadMedia(pickedImageFile);
                    badgeType = null;
                }

                if (TextUtils.isEmpty(badgeUrl) && TextUtils.isEmpty(badgeType)) {
                    AndroidUtilities.runOnUIThread(() -> {
                        try { progress.dismiss(); } catch (Exception ignored) {}
                        Toast.makeText(getParentActivity(), "Не удалось загрузить изображение значка", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                final String finalParam = badgeUrl != null ? badgeUrl : badgeType;
                final boolean wasCustom = badgeUrl != null;

                BadgesController.INSTANCE.setRemoteBadge(targetId, finalParam, text, () -> {
                    if (pickedBitmap != null && wasCustom) {
                        BadgesController.INSTANCE.cacheCustomBadgeDirectly(finalParam, pickedBitmap);
                    }
                    try { progress.dismiss(); } catch (Exception ignored) {}
                    BulletinFactory.of(BadgeManageActivity.this).createSimpleBulletin(
                            pickedBitmap != null ? new BadgesController.TsundereBadgeDrawable(pickedBitmap, 24) : BadgesController.INSTANCE.getTsundereBadgeDrawable(24),
                            "TsundereGram",
                            isEditMode ? "Значок успешно обновлён!" : "Значок успешно выдан!"
                    ).show();
                    AndroidUtilities.runOnUIThread(this::finishFragment, 800);
                }, errorMsg -> {
                    try { progress.dismiss(); } catch (Exception ignored) {}
                    Toast.makeText(getParentActivity(), "Ошибка: " + errorMsg, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    try { progress.dismiss(); } catch (Exception ignored) {}
                    Toast.makeText(getParentActivity(), "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void deleteBadge(long userId) {
        AlertDialog progress = new AlertDialog(getParentActivity(), 3);
        progress.setMessage("Удаление значка…");
        progress.show();

        BadgesController.INSTANCE.setRemoteBadge(userId, null, null, () -> {
            try { progress.dismiss(); } catch (Exception ignored) {}
            BulletinFactory.of(BadgeManageActivity.this).createSimpleBulletin(
                    ContextCompat.getDrawable(getParentActivity(), R.drawable.msg_delete),
                    "TsundereGram",
                    "Значок успешно удалён!"
            ).show();
            AndroidUtilities.runOnUIThread(this::finishFragment, 800);
        }, errorMsg -> {
            try { progress.dismiss(); } catch (Exception ignored) {}
            Toast.makeText(getParentActivity(), "Ошибка: " + errorMsg, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public int getNavigationBarColor() {
        return Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getParentActivity() != null && getParentActivity().getWindow() != null) {
            int navColor = Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider);
            getParentActivity().getWindow().setNavigationBarColor(navColor);
            AndroidUtilities.setLightNavigationBar(getParentActivity(), AndroidUtilities.computePerceivedBrightness(navColor) > 0.721f);
        }
        setNavigationBarColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
    }

}