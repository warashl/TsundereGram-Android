package com.tsunderegram.messenger.badges;

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

import com.google.android.gms.common.api.OptionalModuleApi;
import com.google.android.gms.common.moduleinstall.InstallStatusListener;
import com.google.android.gms.common.moduleinstall.ModuleInstall;
import com.google.android.gms.common.moduleinstall.ModuleInstallClient;
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest;
import com.google.android.gms.common.moduleinstall.ModuleInstallStatusUpdate;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter;
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions;

import com.tsunderegram.messenger.api.dto.BadgeDTO;

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
    private static final int MENU_SERVER = 2;

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
            menu.addItem(MENU_SERVER, R.drawable.msg_link2);
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_ADMINS) {
                    showAdminsDialog();
                } else if (id == MENU_SERVER) {
                    showServerDialog();
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
            // В тёмных темах windowBackgroundWhiteHintText почти сливается с карточкой
            final int idTextColor = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider);
            idEditText.setHintColor(Theme.multAlpha(idTextColor, 0.45f));
            idEditText.setHintTextColor(Theme.multAlpha(idTextColor, 0.45f));
            idEditText.setTextColor(idTextColor);
            idEditText.setCursorColor(idTextColor);
            idEditText.setCursorSize(AndroidUtilities.dp(20));
            idEditText.setCursorWidth(1.5f);
            idEditText.setSingleLine(true);
            idEditText.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(10), Theme.multAlpha(idTextColor, 0.06f)));
            idEditText.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
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
        subHint.setText("PNG, JPG или WEBP — фон можно убрать автоматически");
        subHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        subHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        previewMeta.addView(subHint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));

        previewRow.addView(previewMeta, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        badgeCard.addView(previewRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        TextView pickButton = new TextView(context);
        pickButton.setText("Выбрать изображение");
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
        // Капсула тумблера рисуется на 3dp шире своих границ — с clipToPadding
        // её правый край срезался о внутренний отступ строки.
        removeBgRow.setClipToPadding(false);
        removeBgRow.setClipChildren(false);
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
        removeBgLabel.setText("Вырезать объект");
        removeBgLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        removeBgLabel.setTypeface(AndroidUtilities.bold());
        removeBgLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        textLayout.addView(removeBgLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView removeBgSub = new TextView(context);
        removeBgSub.setText("Убирает фон и оставляет только персонажа");
        removeBgSub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11.5f);
        removeBgSub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        textLayout.addView(removeBgSub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        removeBgRow.addView(textLayout, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, Gravity.CENTER_VERTICAL, 0, 0, 8, 0));

        removeBgSwitch = new Switch(context);
        removeBgSwitch.setColors(Theme.key_switchTrack, Theme.key_switchTrackChecked, Theme.key_windowBackgroundWhite, Theme.key_windowBackgroundWhite);
        removeBgSwitch.setChecked(autoRemoveBg, false);
        removeBgRow.addView(removeBgSwitch, LayoutHelper.createLinear(37, 20, Gravity.CENTER_VERTICAL, 2, 0, 4, 0));

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
        textTitle.setText("Текст подсказки");
        textTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textTitle.setTypeface(AndroidUtilities.bold());
        textTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        textCard.addView(textTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        textEditText = new EditTextBoldCursor(context);
        textEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        textEditText.setHint("Например: Значок разработчика");
        textEditText.setHintColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider), 0.45f));
        textEditText.setHintTextColor(Theme.multAlpha(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider), 0.45f));
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
        saveButton.setOnClickListener(v -> requireServer(this::saveBadge));
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
                requireServer(() -> deleteBadge(checkUid));
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
            adminActionButton.setOnClickListener(v -> requireServer(() -> toggleAdminRole(uid, false)));
        } else {
            adminStatusTextView.setText("Роль: Обычный пользователь (без прав)");
            adminStatusTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            adminActionButton.setText("Сделать администратором значков");
            adminActionButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
            adminActionButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10),
                    Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                    Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
            adminActionButton.setOnClickListener(v -> requireServer(() -> toggleAdminRole(uid, true)));
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
            showServerError(err);
        });
    }

    /** TsundereGram: адрес сервера (Cloudflare Worker), через который идут значки/баннеры — токен бота только там. */
    private void showServerDialog() {
        Context context = getParentActivity();
        if (context == null) return;
        final EditTextBoldCursor edit = new EditTextBoldCursor(context);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        edit.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        edit.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        edit.setSingleLine(true);
        edit.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        edit.setHint("https://….workers.dev");
        edit.setText(app.tsunderegram.security.TsundereApi.getCustomBase());
        edit.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(12), AndroidUtilities.dp(14), AndroidUtilities.dp(12));
        edit.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack), 0.06f)));
        TextView info = new TextView(context);
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        info.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        String current = app.tsunderegram.security.TsundereApi.base();
        info.setText(TextUtils.isEmpty(current)
                ? "Сервер не задан: значки, баннеры и рамки не загружаются. Укажите адрес своего Worker. Пустое поле — адрес из сборки."
                : "Сейчас: " + current + "\nПустое поле — адрес, вшитый при сборке.");
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(22), AndroidUtilities.dp(8), AndroidUtilities.dp(22), 0);
        layout.addView(edit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        layout.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Сервер TsundereGram");
        builder.setView(layout);
        builder.setPositiveButton("Проверить и сохранить", (d, w) -> checkAndSaveServer(edit.getText().toString()));
        builder.setNeutralButton("Как настроить", (d, w) -> showServerHowTo());
        builder.setNegativeButton("Отмена", null);
        showDialog(builder.create());
    }

    /** TsundereGram: всё, что пишет в общий конфиг, работает только через сервер. Без него — понятное объяснение. */
    private void requireServer(Runnable action) {
        if (app.tsunderegram.security.TsundereApi.isConfigured()) {
            action.run();
            return;
        }
        Context context = getParentActivity();
        if (context == null) return;
        boolean creator = BadgesController.isCreator(UserConfig.getInstance(currentAccount).getClientUserId());
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Нужен сервер TsundereGram");
        builder.setMessage("Значки, админы, баннеры и рамки теперь сохраняются через ваш сервер — токена бота в приложении больше нет, поэтому его нельзя украсть из APK.\n\n"
                + (creator ? "Адрес сервера ещё не указан. Разверните его один раз (бесплатно, ~5 минут) и вставьте адрес сюда."
                           : "Адрес сервера ещё не указан. Его задаёт создатель TsundereGram — обновите приложение до сборки с сервером."));
        if (creator) {
            builder.setPositiveButton("Указать адрес", (d, w) -> showServerDialog());
            builder.setNeutralButton("Как настроить", (d, w) -> showServerHowTo());
        } else {
            builder.setPositiveButton("ОК", null);
        }
        if (creator) builder.setNegativeButton("Закрыть", null);
        showDialog(builder.create());
    }

    private void showServerError(String err) {
        String text = app.tsunderegram.security.TsundereApi.explain(err);
        if (!app.tsunderegram.security.TsundereApi.isConfigured()) {
            requireServer(() -> {});
            return;
        }
        Toast.makeText(getParentActivity(), "Ошибка: " + text, Toast.LENGTH_LONG).show();
    }

    private void showServerHowTo() {
        Context context = getParentActivity();
        if (context == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Как настроить сервер");
        builder.setMessage("1. @BotFather → /mybots → бот канала @TsundereGram_Banners → API Token → Revoke (старый токен был в старых APK). Скопируйте новый.\n\n"
                + "2. dash.cloudflare.com → Workers & Pages → Create → Create Worker → имя tsundere-api → Deploy → Edit code → вставьте worker.js из папки server/tsundere-api репозитория → Deploy.\n\n"
                + "3. Worker → Settings → Variables and Secrets → Add → Secret, имя BOT_TOKEN, значение — новый токен → Deploy.\n\n"
                + "4. Откройте в браузере адрес Worker + /info (должно быть \"ok\":true), затем один раз + /setup.\n\n"
                + "5. Здесь: 🔗 → вставьте адрес (https://tsundere-api.…workers.dev) → «Проверить и сохранить».\n\n"
                + "Чтобы адрес был у всех пользователей: GitHub → Settings → Secrets and variables → Actions → Variables → TSUNDERE_API_URL, и пересоберите APK.");
        builder.setPositiveButton("Указать адрес", (d, w) -> showServerDialog());
        builder.setNegativeButton("Закрыть", null);
        showDialog(builder.create());
    }

    private void checkAndSaveServer(String url) {
        Context context = getParentActivity();
        if (context == null) return;
        if (TextUtils.isEmpty(url == null ? null : url.trim())) {
            app.tsunderegram.security.TsundereApi.setCustomBase("");
            BadgesController.INSTANCE.checkRefreshRemoteBadges(true);
            Toast.makeText(context, app.tsunderegram.security.TsundereApi.isConfigured() ? "Используется адрес из сборки" : "Адрес сервера очищен", Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog progress = new AlertDialog(context, 3);
        progress.setCanCancel(false);
        progress.show();
        Utilities.globalQueue.postRunnable(() -> {
            String[] info = new String[1];
            String error = app.tsunderegram.security.TsundereApi.check(url, info);
            AndroidUtilities.runOnUIThread(() -> {
                try { progress.dismiss(); } catch (Exception ignored) {}
                if (getParentActivity() == null) return;
                if (error == null) {
                    app.tsunderegram.security.TsundereApi.setCustomBase(url);
                    BadgesController.INSTANCE.checkRefreshRemoteBadges(true);
                    BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check, "Сервер работает",
                            TextUtils.isEmpty(info[0]) ? "Адрес сохранён" : "Бот @" + info[0] + " • адрес сохранён").show();
                } else {
                    AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                    b.setTitle("Сервер не отвечает");
                    b.setMessage(error);
                    b.setPositiveButton("Исправить", (d, w) -> showServerDialog());
                    b.setNeutralButton("Сохранить всё равно", (d, w) -> {
                        app.tsunderegram.security.TsundereApi.setCustomBase(url);
                        BadgesController.INSTANCE.checkRefreshRemoteBadges(true);
                    });
                    b.setNegativeButton("Отмена", null);
                    showDialog(b.create());
                }
            });
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

        // Текущие админы: нажатие на строку отзывает права (в т.ч. у записей, которые не удалось
        // сопоставить ни с кем из знакомых пользователей — видно только хэш).
        TextView listTitle = new TextView(context);
        listTitle.setText("Текущие администраторы (нажмите, чтобы отозвать):");
        listTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        listTitle.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourceProvider));
        layout.addView(listTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 6));
        final LinearLayout adminsList = new LinearLayout(context);
        adminsList.setOrientation(LinearLayout.VERTICAL);
        ScrollView adminsScroll = new ScrollView(context);
        adminsScroll.addView(adminsList);
        layout.addView(adminsScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        builder.setView(layout);
        final AlertDialog[] dialogRef = new AlertDialog[1];
        fillAdminsList(context, adminsList, dialogRef);
        builder.setPositiveButton("Сделать админом", (dialog, which) -> {
            try {
                long uid = Long.parseLong(input.getText().toString().trim());
                requireServer(() -> toggleAdminRole(uid, true));
            } catch (Exception e) {
                Toast.makeText(context, "Некорректный Telegram ID", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Отозвать права", (dialog, which) -> {
            try {
                long uid = Long.parseLong(input.getText().toString().trim());
                requireServer(() -> toggleAdminRole(uid, false));
            } catch (Exception e) {
                Toast.makeText(context, "Некорректный Telegram ID", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNeutralButton("Закрыть", null);
        dialogRef[0] = builder.create();
        showDialog(dialogRef[0]);
    }

    private void fillAdminsList(Context context, LinearLayout list, AlertDialog[] dialogRef) {
        final java.util.ArrayList<String> entries = new java.util.ArrayList<>(BadgesController.INSTANCE.getAdminHashes());
        if (entries.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Пока никого нет");
            empty.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            empty.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourceProvider));
            list.addView(empty, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 6));
            return;
        }
        TextView loading = new TextView(context);
        loading.setText("Загрузка…");
        loading.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        loading.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourceProvider));
        list.addView(loading);
        Utilities.globalQueue.postRunnable(() -> {
            final java.util.HashMap<String, TLRPC.User> resolved = new java.util.HashMap<>();
            try {
                final java.util.HashSet<String> wanted = new java.util.HashSet<>(entries);
                for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT && resolved.size() < wanted.size(); a++) {
                    if (!UserConfig.getInstance(a).isClientActivated()) continue;
                    for (TLRPC.User u : new java.util.ArrayList<>(org.telegram.messenger.MessagesController.getInstance(a).getUsers().values())) {
                        if (u == null || u.id == 0) continue;
                        String raw = String.valueOf(u.id);
                        if (wanted.contains(raw) && !resolved.containsKey(raw)) {
                            resolved.put(raw, u);
                            continue;
                        }
                        String h = app.tsunderegram.security.TsundereCrypto.hashUserId(u.id);
                        if (wanted.contains(h) && !resolved.containsKey(h)) {
                            resolved.put(h, u);
                        }
                    }
                }
            } catch (Throwable e) {
                FileLog.e(e);
            }
            AndroidUtilities.runOnUIThread(() -> {
                list.removeAllViews();
                for (String entry : entries) {
                    final TLRPC.User u = resolved.get(entry);
                    TextView row = new TextView(context);
                    row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                    row.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
                    row.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(9), AndroidUtilities.dp(10), AndroidUtilities.dp(9));
                    row.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10),
                            Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider), 0.05f),
                            Theme.multAlpha(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider), 0.12f)));
                    final String title;
                    if (u != null) {
                        title = UserObject.getUserName(u) + "  •  ID " + u.id;
                    } else {
                        title = "Неизвестный пользователь  •  " + (entry.length() > 12 ? entry.substring(0, 12) + "…" : entry);
                    }
                    row.setText(title + "\nОтозвать права");
                    row.setOnClickListener(v -> {
                        if (dialogRef[0] != null) {
                            try { dialogRef[0].dismiss(); } catch (Exception ignored) {}
                        }
                        if (u != null) {
                            requireServer(() -> toggleAdminRole(u.id, false));
                        } else {
                            requireServer(() -> revokeAdminEntry(entry));
                        }
                    });
                    list.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 6));
                }
            });
        });
    }

    private void revokeAdminEntry(String entry) {
        if (getParentActivity() == null) return;
        AlertDialog progress = new AlertDialog(getParentActivity(), 3);
        progress.setMessage("Отзыв прав администратора…");
        progress.setCanCancel(false);
        progress.show();
        BadgesController.INSTANCE.setBadgeAdminEntry(entry, entry, false, () -> {
            try { progress.dismiss(); } catch (Exception ignored) {}
            long uid = getTargetUid();
            if (uid != 0) updateAdminRowForUser(uid);
            BulletinFactory.of(this).createSimpleBulletin(
                    ContextCompat.getDrawable(getParentActivity(), R.drawable.msg_delete),
                    "Администраторы значков", "Права администратора отозваны!").show();
        }, err -> {
            try { progress.dismiss(); } catch (Exception ignored) {}
            showServerError(err);
        });
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
                    Bitmap decoded = BitmapFactory.decodeStream(in);
                    if (decoded == null) return;
                    // Для значка хватает 1024px, а огромные фото тормозят вырезание
                    final Bitmap bmp = scaleToMax(decoded, 1024);

                    originalPickedBitmap = bmp;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (badgePreviewHint != null) {
                            badgePreviewHint.setText("Вырезаю персонажа…");
                        }
                    });
                    trySegmentSubject(bmp, segmentedBmp -> Utilities.globalQueue.postRunnable(() -> {
                        // Запасной алгоритм тяжёлый — только в фоне, не на UI-потоке
                        final Bitmap noBg = segmentedBmp != null ? segmentedBmp : removeBackground(bmp);
                        AndroidUtilities.runOnUIThread(() -> {
                            noBgPickedBitmap = noBg;
                            currentBadgeType = null;
                            applyBackgroundRemovalToggle();
                        });
                    }));
                } catch (Exception e) {
                    FileLog.e(e);
                }
            });
        }
    }

    private void trySegmentSubject(Bitmap bmp, java.util.function.Consumer<Bitmap> onDone) {
        trySegmentSubject(bmp, onDone, 0);
    }

    private void trySegmentSubject(Bitmap bmp, java.util.function.Consumer<Bitmap> onDone, int attempt) {
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
                        // Модуль сегментации Google Play services качается по требованию:
                        // при первой попытке его обычно ещё нет — просим установить и повторяем.
                        if (attempt == 0 && segmenter instanceof OptionalModuleApi) {
                            requestSegmentationModule((OptionalModuleApi) segmenter, installed -> {
                                if (installed) {
                                    trySegmentSubject(bmp, onDone, attempt + 1);
                                } else {
                                    onDone.accept(null);
                                }
                            });
                        } else {
                            onDone.accept(null);
                        }
                    });
        } catch (Throwable t) {
            FileLog.e("MLKit SubjectSegmentation exception: " + t);
            onDone.accept(null);
        }
    }

    private void requestSegmentationModule(OptionalModuleApi api, java.util.function.Consumer<Boolean> done) {
        try {
            final ModuleInstallClient client = ModuleInstall.getClient(ApplicationLoader.applicationContext);
            final boolean[] finished = new boolean[1];
            final InstallStatusListener listener = new InstallStatusListener() {
                @Override
                public void onInstallStatusUpdated(ModuleInstallStatusUpdate update) {
                    final int state = update.getInstallState();
                    if (state == ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED
                            || state == ModuleInstallStatusUpdate.InstallState.STATE_FAILED
                            || state == ModuleInstallStatusUpdate.InstallState.STATE_CANCELED) {
                        try {
                            client.unregisterListener(this);
                        } catch (Throwable ignore) {
                        }
                        if (!finished[0]) {
                            finished[0] = true;
                            done.accept(state == ModuleInstallStatusUpdate.InstallState.STATE_COMPLETED);
                        }
                    }
                }
            };
            AndroidUtilities.runOnUIThread(() -> {
                if (badgePreviewHint != null) {
                    badgePreviewHint.setText("Загружаю модуль вырезания…");
                }
            });
            ModuleInstallRequest request = ModuleInstallRequest.newBuilder()
                    .addApi(api)
                    .setListener(listener)
                    .build();
            client.installModules(request)
                    .addOnSuccessListener(response -> {
                        if (response.areModulesAlreadyInstalled() && !finished[0]) {
                            finished[0] = true;
                            try {
                                client.unregisterListener(listener);
                            } catch (Throwable ignore) {
                            }
                            done.accept(true);
                        }
                    })
                    .addOnFailureListener(e -> {
                        FileLog.e("MLKit module install failed: " + e);
                        if (!finished[0]) {
                            finished[0] = true;
                            done.accept(false);
                        }
                    });
        } catch (Throwable t) {
            FileLog.e("MLKit module install exception: " + t);
            done.accept(false);
        }
    }

    private static Bitmap scaleToMax(Bitmap src, int max) {
        final int w = src.getWidth();
        final int h = src.getHeight();
        final int m = Math.max(w, h);
        if (m <= max) {
            if (src.getConfig() == Bitmap.Config.ARGB_8888) {
                return src;
            }
            Bitmap copy = src.copy(Bitmap.Config.ARGB_8888, false);
            return copy != null ? copy : src;
        }
        final float scale = max / (float) m;
        return Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * scale)), Math.max(1, Math.round(h * scale)), true);
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
     * Удаление фона без нейросети (запасной путь, если модуль ML Kit недоступен).
     * 1) по рамке изображения оцениваем палитру фона (k-means, до 4 цветов);
     * 2) заливаем фон от краёв, но не перешагиваем резкие перепады цвета (контур персонажа);
     * 3) край делаем мягким (альфа по расстоянию до цвета фона + сглаживание);
     * 4) убираем ореол цвета фона с полупрозрачных пикселей и мелкий мусор.
     */
    public static Bitmap removeBackground(Bitmap src) {
        if (src == null) return null;
        final Bitmap work = scaleToMax(src, 1024);
        final int width = work.getWidth();
        final int height = work.getHeight();
        if (width < 4 || height < 4) {
            return work;
        }
        final int n = width * height;
        final int[] pixels = new int[n];
        work.getPixels(pixels, 0, width, 0, 0, width, height);

        int transparentCount = 0;
        for (int p : pixels) {
            if ((p >>> 24) < 20) {
                transparentCount++;
            }
        }
        if (transparentCount > n * 0.01) {
            // Уже есть прозрачность — просто подрезаем пустые поля
            return cropVisibleContent(work, pixels, width, height);
        }

        // 1. Палитра фона по рамке шириной 2px
        final int ring = Math.min(2, Math.min(width, height) / 2);
        int borderCount = 0;
        final int[] border = new int[(width + height) * 2 * ring];
        for (int r = 0; r < ring; r++) {
            for (int x = 0; x < width; x++) {
                border[borderCount++] = pixels[r * width + x];
                border[borderCount++] = pixels[(height - 1 - r) * width + x];
            }
            for (int y = 0; y < height; y++) {
                border[borderCount++] = pixels[y * width + r];
                border[borderCount++] = pixels[y * width + (width - 1 - r)];
            }
        }
        final float[][] centers = kMeansColors(border, borderCount, 4);

        final float[] spread = new float[borderCount];
        for (int i = 0; i < borderCount; i++) {
            spread[i] = distanceToCenters(border[i], centers);
        }
        java.util.Arrays.sort(spread);
        final float noise = spread[Math.min(borderCount - 1, (int) (borderCount * 0.85f))];
        final float tHi = Math.max(26f, Math.min(80f, noise * 2.2f + 22f));
        final float tLo = tHi * 0.45f;
        final float edgeLimit = Math.max(28f, tHi * 0.9f);

        // 2. Расстояние каждого пикселя до ближайшего цвета фона
        final float[] dist = new float[n];
        for (int i = 0; i < n; i++) {
            dist[i] = distanceToCenters(pixels[i], centers);
        }

        // 3. Заливка фона от краёв с остановкой на контурах
        final byte[] background = new byte[n];
        final int[] queue = new int[n];
        int head = 0, tail = 0;
        for (int x = 0; x < width; x++) {
            tail = seedBackground(x, dist, tHi, background, queue, tail);
            tail = seedBackground((height - 1) * width + x, dist, tHi, background, queue, tail);
        }
        for (int y = 1; y < height - 1; y++) {
            tail = seedBackground(y * width, dist, tHi, background, queue, tail);
            tail = seedBackground(y * width + width - 1, dist, tHi, background, queue, tail);
        }
        while (head < tail) {
            final int idx = queue[head++];
            final int cx = idx % width;
            final int cy = idx / width;
            final int cur = pixels[idx];
            if (cx > 0) tail = growBackground(cur, idx - 1, pixels, dist, tHi, edgeLimit, background, queue, tail);
            if (cx < width - 1) tail = growBackground(cur, idx + 1, pixels, dist, tHi, edgeLimit, background, queue, tail);
            if (cy > 0) tail = growBackground(cur, idx - width, pixels, dist, tHi, edgeLimit, background, queue, tail);
            if (cy < height - 1) tail = growBackground(cur, idx + width, pixels, dist, tHi, edgeLimit, background, queue, tail);
        }
        final int removed = tail;
        if (removed < n * 0.01f || removed > n * 0.985f) {
            // Фон не нашёлся или «съелось» всё — честно оставляем оригинал
            return work;
        }

        // 4. Альфа: фон прозрачный, пограничная полоса — мягкий переход по цвету
        final int[] alpha = new int[n];
        for (int i = 0; i < n; i++) {
            alpha[i] = background[i] != 0 ? 0 : 255;
        }
        final float softRange = Math.max(1f, tHi * 1.5f - tLo);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                final int i = y * width + x;
                if (background[i] != 0 || !touchesBackground(background, x, y, width, height)) {
                    continue;
                }
                float t = (dist[i] - tLo) / softRange;
                t = t < 0f ? 0f : (t > 1f ? 1f : t);
                alpha[i] = (int) (255f * t * t * (3f - 2f * t));
            }
        }

        // 5. Мелкие островки-мусор
        removeSmallIslands(alpha, width, height);

        // 6. Сглаживание лесенки по краю (только уменьшаем альфу, фон не «наползает»)
        final int[] smooth = alpha.clone();
        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                final int i = y * width + x;
                final int a = alpha[i];
                if (a == 0) continue;
                if (alpha[i - 1] == a && alpha[i + 1] == a && alpha[i - width] == a && alpha[i + width] == a && a == 255) {
                    continue;
                }
                int sum = 0;
                for (int dy = -1; dy <= 1; dy++) {
                    final int row = i + dy * width;
                    sum += alpha[row - 1] + alpha[row] + alpha[row + 1];
                }
                smooth[i] = Math.min(a, (sum + a) / 10);
            }
        }

        // 7. Сборка: убираем подмешанный цвет фона с полупрозрачных пикселей
        final int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            final int a = smooth[i];
            if (a <= 0) {
                out[i] = 0;
                continue;
            }
            final int c = pixels[i];
            int r = (c >> 16) & 0xFF, g = (c >> 8) & 0xFF, b = c & 0xFF;
            if (a < 250) {
                final float[] bg = nearestCenter(c, centers);
                final float af = a / 255f;
                r = clampColor((r - (1f - af) * bg[0]) / af);
                g = clampColor((g - (1f - af) * bg[1]) / af);
                b = clampColor((b - (1f - af) * bg[2]) / af);
            }
            out[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        final Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        result.setPixels(out, 0, width, 0, 0, width, height);
        return cropVisibleContent(result, out, width, height);
    }

    private static int seedBackground(int idx, float[] dist, float limit, byte[] background, int[] queue, int tail) {
        if (background[idx] == 0 && dist[idx] < limit) {
            background[idx] = 1;
            queue[tail++] = idx;
        }
        return tail;
    }

    private static int growBackground(int fromColor, int idx, int[] pixels, float[] dist, float limit, float edgeLimit, byte[] background, int[] queue, int tail) {
        if (background[idx] != 0 || dist[idx] >= limit) {
            return tail;
        }
        if (colorDistance(fromColor, pixels[idx]) >= edgeLimit) {
            return tail;
        }
        background[idx] = 1;
        queue[tail++] = idx;
        return tail;
    }

    private static boolean touchesBackground(byte[] background, int x, int y, int width, int height) {
        for (int dy = -1; dy <= 1; dy++) {
            final int ny = y + dy;
            if (ny < 0 || ny >= height) continue;
            for (int dx = -1; dx <= 1; dx++) {
                final int nx = x + dx;
                if (nx < 0 || nx >= width || (dx == 0 && dy == 0)) continue;
                if (background[ny * width + nx] != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void removeSmallIslands(int[] alpha, int width, int height) {
        final int n = width * height;
        final int[] label = new int[n];
        final int[] queue = new int[n];
        int[] areas = new int[64];
        int count = 0;
        int maxArea = 0;
        for (int start = 0; start < n; start++) {
            if (alpha[start] == 0 || label[start] != 0) continue;
            if (count == areas.length) {
                areas = java.util.Arrays.copyOf(areas, areas.length * 2);
            }
            final int id = ++count;
            int head = 0, tail = 0;
            queue[tail++] = start;
            label[start] = id;
            while (head < tail) {
                final int idx = queue[head++];
                final int x = idx % width;
                final int y = idx / width;
                if (x > 0 && alpha[idx - 1] != 0 && label[idx - 1] == 0) { label[idx - 1] = id; queue[tail++] = idx - 1; }
                if (x < width - 1 && alpha[idx + 1] != 0 && label[idx + 1] == 0) { label[idx + 1] = id; queue[tail++] = idx + 1; }
                if (y > 0 && alpha[idx - width] != 0 && label[idx - width] == 0) { label[idx - width] = id; queue[tail++] = idx - width; }
                if (y < height - 1 && alpha[idx + width] != 0 && label[idx + width] == 0) { label[idx + width] = id; queue[tail++] = idx + width; }
            }
            areas[id - 1] = tail;
            if (tail > maxArea) maxArea = tail;
        }
        final int minKeep = Math.max(16, maxArea / 40);
        for (int i = 0; i < n; i++) {
            if (label[i] != 0 && areas[label[i] - 1] < minKeep) {
                alpha[i] = 0;
            }
        }
    }

    private static float[][] kMeansColors(int[] colors, int count, int k) {
        final float[][] centers = new float[k][3];
        // старт: цвета углов/сторон рамки, равномерно по массиву
        for (int c = 0; c < k; c++) {
            final int col = colors[Math.min(count - 1, (int) ((long) count * c / k))];
            centers[c][0] = (col >> 16) & 0xFF;
            centers[c][1] = (col >> 8) & 0xFF;
            centers[c][2] = col & 0xFF;
        }
        final float[][] sums = new float[k][3];
        final int[] sizes = new int[k];
        for (int iter = 0; iter < 8; iter++) {
            for (int c = 0; c < k; c++) {
                sums[c][0] = sums[c][1] = sums[c][2] = 0;
                sizes[c] = 0;
            }
            for (int i = 0; i < count; i++) {
                final int col = colors[i];
                final int best = nearestCenterIndex(col, centers);
                sums[best][0] += (col >> 16) & 0xFF;
                sums[best][1] += (col >> 8) & 0xFF;
                sums[best][2] += col & 0xFF;
                sizes[best]++;
            }
            for (int c = 0; c < k; c++) {
                if (sizes[c] > 0) {
                    centers[c][0] = sums[c][0] / sizes[c];
                    centers[c][1] = sums[c][1] / sizes[c];
                    centers[c][2] = sums[c][2] / sizes[c];
                }
            }
        }
        // Редкие цвета рамки (кусочек персонажа у края) фоном не считаем
        int kept = 0;
        final float[][] result = new float[k][];
        for (int c = 0; c < k; c++) {
            if (sizes[c] >= Math.max(1, count / 12)) {
                result[kept++] = centers[c];
            }
        }
        if (kept == 0) {
            int best = 0;
            for (int c = 1; c < k; c++) {
                if (sizes[c] > sizes[best]) best = c;
            }
            result[kept++] = centers[best];
        }
        return java.util.Arrays.copyOf(result, kept);
    }

    private static int nearestCenterIndex(int col, float[][] centers) {
        int best = 0;
        float bestD = Float.MAX_VALUE;
        for (int c = 0; c < centers.length; c++) {
            final float d = colorDistance(col, centers[c]);
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    private static float[] nearestCenter(int col, float[][] centers) {
        return centers[nearestCenterIndex(col, centers)];
    }

    private static float distanceToCenters(int col, float[][] centers) {
        float best = Float.MAX_VALUE;
        for (float[] center : centers) {
            final float d = colorDistance(col, center);
            if (d < best) best = d;
        }
        return best;
    }

    /** Взвешенное RGB-расстояние («redmean»), нормированное к шкале обычного RGB. */
    private static float colorDistance(int c, float[] center) {
        final float r1 = (c >> 16) & 0xFF, g1 = (c >> 8) & 0xFF, b1 = c & 0xFF;
        final float rm = (r1 + center[0]) * 0.5f;
        final float dr = r1 - center[0], dg = g1 - center[1], db = b1 - center[2];
        return (float) Math.sqrt((2f + rm / 256f) * dr * dr + 4f * dg * dg + (2f + (255f - rm) / 256f) * db * db) / 3f;
    }

    private static float colorDistance(int c1, int c2) {
        final float rm = (((c1 >> 16) & 0xFF) + ((c2 >> 16) & 0xFF)) * 0.5f;
        final float dr = ((c1 >> 16) & 0xFF) - ((c2 >> 16) & 0xFF);
        final float dg = ((c1 >> 8) & 0xFF) - ((c2 >> 8) & 0xFF);
        final float db = (c1 & 0xFF) - (c2 & 0xFF);
        return (float) Math.sqrt((2f + rm / 256f) * dr * dr + 4f * dg * dg + (2f + (255f - rm) / 256f) * db * db) / 3f;
    }

    private static int clampColor(float v) {
        return v < 0f ? 0 : (v > 255f ? 255 : (int) v);
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
                    showServerError(errorMsg);
                });
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    try { progress.dismiss(); } catch (Exception ignored) {}
                    showServerError(app.tsunderegram.security.TsundereApi.explain(e));
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
            showServerError(errorMsg);
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