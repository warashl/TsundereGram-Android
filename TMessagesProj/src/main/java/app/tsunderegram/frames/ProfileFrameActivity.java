package app.tsunderegram.frames;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Экран выбора и настройки рамок для аватара в стиле TsundereGram.
 */
public class ProfileFrameActivity extends BaseFragment {

    private static final int REQ_PICK_FRAME = 2001;

    private String selectedType = ProfileFrameController.PRESET_NONE;
    private String selectedUrl = "";
    private File pickedFile;
    private Bitmap customBitmap;

    private BackupImageView avatarView;
    private ImageView frameOverlayView;
    private TextView statusTextView;

    private FrameLayout cardNone;
    private RadioButton rbNone;

    private FrameLayout cardNewspaper;
    private RadioButton rbNewspaper;

    // Рамки-иллюстрации: тип -> карточка / радиокнопка
    private final java.util.LinkedHashMap<String, FrameLayout> artCards = new java.util.LinkedHashMap<>();
    private final java.util.HashMap<String, RadioButton> artRadios = new java.util.HashMap<>();

    private FrameLayout cardCustom;
    private RadioButton rbCustom;
    private ImageView customRowMiniFrame;
    private TextView customRowSubtitle;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Рамка аватара");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ProfileFrameController controller = ProfileFrameController.getInstance();
        selectedType = controller.getLocalFrameType();
        selectedUrl = controller.getLocalFrameUrl();

        fragmentView = new ScrollView(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(32));
        ((ScrollView) fragmentView).addView(container, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TLRPC.User currentUser = getUserConfig().getCurrentUser();

        // 1. Hero предпросмотр аватара с рамкой
        LinearLayout heroContainer = new LinearLayout(context);
        heroContainer.setOrientation(LinearLayout.VERTICAL);
        heroContainer.setGravity(Gravity.CENTER_HORIZONTAL);
        heroContainer.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        heroContainer.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(22), AndroidUtilities.dp(16), AndroidUtilities.dp(20));

        FrameLayout avatarBox = new FrameLayout(context);
        avatarView = new BackupImageView(context);
        avatarView.setRoundRadius(AndroidUtilities.dp(48));

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (currentUser != null) {
            avatarDrawable.setInfo(currentAccount, currentUser);
            avatarView.setForUserOrChat(currentUser, avatarDrawable);
        } else {
            avatarDrawable.setInfo(0, "User", null);
            avatarView.setImageDrawable(avatarDrawable);
        }
        avatarBox.addView(avatarView, LayoutHelper.createFrame(96, 96, Gravity.CENTER));

        frameOverlayView = new ImageView(context);
        frameOverlayView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        // Размер зависит от рамки: 96dp * коэффициент (газета ≈ 148dp, иллюстрации ≈ 163dp)
        avatarBox.addView(frameOverlayView, LayoutHelper.createFrame(148, 148, Gravity.CENTER));

        heroContainer.addView(avatarBox, LayoutHelper.createLinear(168, 168, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 10));

        TextView nameView = new TextView(context);
        nameView.setText(currentUser != null ? UserObject.getUserName(currentUser) : "Tsundere");
        nameView.setTypeface(AndroidUtilities.bold());
        nameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        nameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        nameView.setGravity(Gravity.CENTER);
        heroContainer.addView(nameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL, 0, 0, 0, 8));

        statusTextView = new TextView(context);
        statusTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        statusTextView.setTypeface(AndroidUtilities.bold());
        statusTextView.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider));
        statusTextView.setGravity(Gravity.CENTER);
        statusTextView.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(5), AndroidUtilities.dp(12), AndroidUtilities.dp(5));
        heroContainer.addView(statusTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL));

        container.addView(heroContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // 2. Карточка «Доступные рамки»
        LinearLayout presetsCard = new LinearLayout(context);
        presetsCard.setOrientation(LinearLayout.VERTICAL);
        presetsCard.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        presetsCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        TextView presetsTitle = new TextView(context);
        presetsTitle.setText("Доступные рамки");
        presetsTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        presetsTitle.setTypeface(AndroidUtilities.bold());
        presetsTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        presetsCard.addView(presetsTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView presetsSub = new TextView(context);
        presetsSub.setText("Рамка отображается вокруг твоего аватара во всём TsundereGram");
        presetsSub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        presetsSub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        presetsCard.addView(presetsSub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // Вариант 1: Без рамки
        cardNone = createOptionRow(context, currentUser, "Без рамки", "Стандартный вид аватара", 0, true);
        rbNone = (RadioButton) cardNone.findViewWithTag("rb");
        cardNone.setOnClickListener(v -> selectPreset(ProfileFrameController.PRESET_NONE, ""));
        presetsCard.addView(cardNone, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        // Вариант 2: Газетная вырезка
        cardNewspaper = createOptionRow(context, currentUser, "Газетная вырезка", "«Срочно: Цундере в сети!» эксклюзив", R.drawable.frame_newspaper, false);
        rbNewspaper = (RadioButton) cardNewspaper.findViewWithTag("rb");
        cardNewspaper.setOnClickListener(v -> selectPreset(ProfileFrameController.PRESET_NEWSPAPER, ""));
        presetsCard.addView(cardNewspaper, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Варианты 3–6: рамки-иллюстрации
        for (String type : ProfileFrameController.ART_PRESETS) {
            FrameLayout card = createOptionRow(context, currentUser, ProfileFrameController.getPresetTitle(type),
                    ProfileFrameController.getPresetSubtitle(type), 0, false);
            Bitmap art = controller.getArtBitmap(type);
            if (art != null) {
                FrameLayout previewBox = (FrameLayout) card.findViewWithTag("preview");
                if (previewBox != null) {
                    ImageView miniFrame = new ImageView(context);
                    miniFrame.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    miniFrame.setImageBitmap(art);
                    int size = Math.round(34 * ProfileFrameController.ART_SCALE_RATIO);
                    previewBox.addView(miniFrame, LayoutHelper.createFrame(size, size, Gravity.CENTER));
                }
            }
            artRadios.put(type, (RadioButton) card.findViewWithTag("rb"));
            card.setOnClickListener(v -> selectPreset(type, ""));
            artCards.put(type, card);
            presetsCard.addView(card, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));
        }

        container.addView(presetsCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 20));

        // 3. Кнопка сохранения
        TextView saveButton = new TextView(context);
        saveButton.setText("Сохранить рамку для всех");
        saveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        saveButton.setTypeface(AndroidUtilities.bold());
        saveButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        saveButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        saveButton.setOnClickListener(v -> saveFrame());
        container.addView(saveButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        updateSelection(false);
        return fragmentView;
    }

    private FrameLayout createOptionRow(Context context, TLRPC.User currentUser, String title, String subtitle, int frameDrawableRes, boolean isNone) {
        FrameLayout card = new FrameLayout(context);
        card.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));

        // Мини-превью (58x58dp: вмещает и газету, и рамки-иллюстрации)
        FrameLayout previewBox = new FrameLayout(context);
        previewBox.setTag("preview");

        BackupImageView miniAvatar = new BackupImageView(context);
        miniAvatar.setRoundRadius(AndroidUtilities.dp(17));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (currentUser != null) {
            avatarDrawable.setInfo(currentAccount, currentUser);
            miniAvatar.setForUserOrChat(currentUser, avatarDrawable);
        } else {
            avatarDrawable.setInfo(0, "User", null);
            miniAvatar.setImageDrawable(avatarDrawable);
        }
        previewBox.addView(miniAvatar, LayoutHelper.createFrame(34, 34, Gravity.CENTER));

        if (isNone) {
            ImageView noneIcon = new ImageView(context);
            noneIcon.setImageResource(R.drawable.baseline_close_18);
            noneIcon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            previewBox.addView(noneIcon, LayoutHelper.createFrame(16, 16, Gravity.BOTTOM | Gravity.END));
        } else if (frameDrawableRes != 0) {
            ImageView miniFrame = new ImageView(context);
            miniFrame.setScaleType(ImageView.ScaleType.FIT_CENTER);
            miniFrame.setImageResource(frameDrawableRes);
            previewBox.addView(miniFrame, LayoutHelper.createFrame(52, 52, Gravity.CENTER));
        }
        card.addView(previewBox, LayoutHelper.createFrame(58, 58, Gravity.START | Gravity.CENTER_VERTICAL));

        // Тексты (по центру)
        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView subView = new TextView(context);
        subView.setText(subtitle);
        subView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        subView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        textLayout.addView(subView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        card.addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 70, 0, 36, 0));

        // RadioButton (справа)
        RadioButton rb = new RadioButton(context);
        rb.setTag("rb");
        rb.setSize(AndroidUtilities.dp(18));
        rb.setColor(Theme.getColor(Theme.key_radioBackground, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider));
        card.addView(rb, LayoutHelper.createFrame(22, 22, Gravity.END | Gravity.CENTER_VERTICAL));

        return card;
    }

    private FrameLayout createCustomOptionRow(Context context, TLRPC.User currentUser) {
        FrameLayout card = new FrameLayout(context);
        card.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));

        // Мини-превью (52x52dp)
        FrameLayout previewBox = new FrameLayout(context);

        BackupImageView miniAvatar = new BackupImageView(context);
        miniAvatar.setRoundRadius(AndroidUtilities.dp(17));
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (currentUser != null) {
            avatarDrawable.setInfo(currentAccount, currentUser);
            miniAvatar.setForUserOrChat(currentUser, avatarDrawable);
        } else {
            avatarDrawable.setInfo(0, "User", null);
            miniAvatar.setImageDrawable(avatarDrawable);
        }
        previewBox.addView(miniAvatar, LayoutHelper.createFrame(34, 34, Gravity.CENTER));

        customRowMiniFrame = new ImageView(context);
        customRowMiniFrame.setScaleType(ImageView.ScaleType.FIT_CENTER);
        customRowMiniFrame.setVisibility(View.GONE);
        previewBox.addView(customRowMiniFrame, LayoutHelper.createFrame(52, 52, Gravity.CENTER));

        ImageView plusIcon = new ImageView(context);
        plusIcon.setImageResource(R.drawable.baseline_add_24);
        plusIcon.setColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText, resourceProvider));
        previewBox.addView(plusIcon, LayoutHelper.createFrame(16, 16, Gravity.BOTTOM | Gravity.END));

        card.addView(previewBox, LayoutHelper.createFrame(52, 52, Gravity.START | Gravity.CENTER_VERTICAL));

        // Тексты
        LinearLayout textLayout = new LinearLayout(context);
        textLayout.setOrientation(LinearLayout.VERTICAL);

        TextView titleView = new TextView(context);
        titleView.setText("Своя рамка (PNG)");
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        titleView.setTypeface(AndroidUtilities.bold());
        titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        textLayout.addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        customRowSubtitle = new TextView(context);
        customRowSubtitle.setText("Загрузить файл из галереи...");
        customRowSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12.5f);
        customRowSubtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        textLayout.addView(customRowSubtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        card.addView(textLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 64, 0, 36, 0));

        // RadioButton
        RadioButton rb = new RadioButton(context);
        rb.setTag("rb");
        rb.setSize(AndroidUtilities.dp(18));
        rb.setColor(Theme.getColor(Theme.key_radioBackground, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider));
        card.addView(rb, LayoutHelper.createFrame(22, 22, Gravity.END | Gravity.CENTER_VERTICAL));

        return card;
    }

    private void updateCardBackground(FrameLayout card, boolean selected) {
        int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider);
        if (selected) {
            int bgColor = ColorUtils.setAlphaComponent(accentColor, 28);
            card.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), bgColor));
        } else {
            card.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                    Color.TRANSPARENT,
                    Theme.getColor(Theme.key_listSelector, resourceProvider)));
        }
    }

    private void selectPreset(String type, String url) {
        selectedType = type;
        selectedUrl = url;
        updateSelection(true);
    }

    private void setOverlaySize(float ratio) {
        if (frameOverlayView == null) return;
        ViewGroup.LayoutParams lp = frameOverlayView.getLayoutParams();
        int size = AndroidUtilities.dp(Math.min(168, Math.round(96 * ratio)));
        if (lp != null && (lp.width != size || lp.height != size)) {
            lp.width = size;
            lp.height = size;
            frameOverlayView.setLayoutParams(lp);
        }
    }

    private void updateSelection(boolean animated) {
        boolean isNone = ProfileFrameController.PRESET_NONE.equals(selectedType);
        boolean isNewspaper = ProfileFrameController.PRESET_NEWSPAPER.equals(selectedType);
        boolean isArt = ProfileFrameController.isArtPreset(selectedType);
        boolean isCustom = !isNone && !isNewspaper && !isArt;

        if (rbNone != null) rbNone.setChecked(isNone, animated);
        if (rbNewspaper != null) rbNewspaper.setChecked(isNewspaper, animated);
        if (rbCustom != null) rbCustom.setChecked(isCustom, animated);
        for (java.util.Map.Entry<String, FrameLayout> e : artCards.entrySet()) {
            boolean checked = e.getKey().equals(selectedType);
            RadioButton rb = artRadios.get(e.getKey());
            if (rb != null) rb.setChecked(checked, animated);
            updateCardBackground(e.getValue(), checked);
        }

        if (cardNone != null) updateCardBackground(cardNone, isNone);
        if (cardNewspaper != null) updateCardBackground(cardNewspaper, isNewspaper);
        if (cardCustom != null) updateCardBackground(cardCustom, isCustom);

        int accentColor = Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider);
        int pillBg = ColorUtils.setAlphaComponent(accentColor, 26);
        statusTextView.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(12), pillBg));

        if (isNewspaper) {
            setOverlaySize(658f / 428f);
            frameOverlayView.setImageDrawable(ContextCompat.getDrawable(ApplicationLoader.applicationContext, R.drawable.frame_newspaper));
            frameOverlayView.setVisibility(View.VISIBLE);
            statusTextView.setText("Газетная вырезка «Цундере в сети!»");
        } else if (isArt) {
            Bitmap art = ProfileFrameController.getInstance().getArtBitmap(selectedType);
            setOverlaySize(ProfileFrameController.ART_SCALE_RATIO);
            frameOverlayView.setImageBitmap(art);
            frameOverlayView.setVisibility(art != null ? View.VISIBLE : View.GONE);
            statusTextView.setText(ProfileFrameController.getPresetTitle(selectedType));
        } else if (isCustom) {
            setOverlaySize(658f / 428f);
            if (customBitmap != null) {
                frameOverlayView.setImageBitmap(customBitmap);
                frameOverlayView.setVisibility(View.VISIBLE);
            }
            statusTextView.setText("Своя кастомная рамка");
        } else {
            frameOverlayView.setImageDrawable(null);
            frameOverlayView.setVisibility(View.GONE);
            statusTextView.setText("Без рамки");
        }
    }

    private void pickCustomFrame() {
        Activity activity = getParentActivity();
        if (activity == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            startActivityForResult(intent, REQ_PICK_FRAME);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_PICK_FRAME && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                Context context = ApplicationLoader.applicationContext;
                File temp = new File(context.getCacheDir(), "frame_upload_" + System.currentTimeMillis() + ".png");
                try (InputStream is = context.getContentResolver().openInputStream(uri);
                     FileOutputStream fos = new FileOutputStream(temp)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = is.read(buf)) > 0) {
                        fos.write(buf, 0, len);
                    }
                }
                Bitmap bmp = BitmapFactory.decodeFile(temp.getAbsolutePath());
                if (bmp != null) {
                    pickedFile = temp;
                    customBitmap = bmp;
                    selectedType = "custom";
                    selectedUrl = "";
                    if (customRowMiniFrame != null) {
                        customRowMiniFrame.setImageBitmap(customBitmap);
                        customRowMiniFrame.setVisibility(View.VISIBLE);
                    }
                    if (customRowSubtitle != null) {
                        customRowSubtitle.setText("Изображение выбрано (" + bmp.getWidth() + "x" + bmp.getHeight() + ")");
                    }
                    updateSelection(true);
                } else {
                    Toast.makeText(getParentActivity(), "Не удалось открыть изображение", Toast.LENGTH_SHORT).show();
                }
            } catch (Throwable e) {
                FileLog.e(e);
                Toast.makeText(getParentActivity(), "Ошибка: " + app.tsunderegram.security.TsundereApi.explain(e), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveFrame() {
        AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setMessage("Сохранение рамки в облаке...");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        long myId = getUserConfig().getClientUserId();

        Utilities.globalQueue.postRunnable(() -> {
            try {
                String uploadUrl = selectedUrl;
                if (pickedFile != null && pickedFile.exists()) {
                    uploadUrl = com.tsunderegram.messenger.badges.BadgesController.uploadMedia(pickedFile);
                }

                final String finalUploadUrl = uploadUrl;
                ProfileFrameController.getInstance().setLocalFrame(selectedType, finalUploadUrl);

                ProfileFrameController.getInstance().setRemoteFrame(myId, selectedType, finalUploadUrl, () -> {
                    progressDialog.dismiss();
                    BulletinFactory.of(this).createSimpleBulletin(
                            R.raw.done,
                            "Рамка обновлена!",
                            "Теперь все пользователи TsundereGram видят твою рамку"
                    ).show();
                    AndroidUtilities.runOnUIThread(() -> finishFragment(), 1500);
                }, err -> {
                    progressDialog.dismiss();
                    BulletinFactory.of(this).createSimpleBulletin(
                            R.raw.error,
                            "Ошибка облака",
                            "Локально рамка установлена, но облако ответило: " + err
                    ).show();
                });
            } catch (Throwable t) {
                FileLog.e(t);
                AndroidUtilities.runOnUIThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(getParentActivity(), "Ошибка: " + app.tsunderegram.security.TsundereApi.explain(t), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
}
