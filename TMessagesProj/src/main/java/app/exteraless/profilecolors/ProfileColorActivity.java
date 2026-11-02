package app.exteraless.profilecolors;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
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

import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColorPicker;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import app.exteraless.banners.ProfileBannerController;
import app.exteraless.frames.ProfileFrameController;

/**
 * Экран настройки цветов профиля под баннер (Profile Colors) в стиле TsundereGram.
 * Полноценный визуальный выбор цветов на палитре без ручного ввода кодов.
 */
public class ProfileColorActivity extends BaseFragment {
    private final java.util.List<LinearLayout> heroPillButtons = new java.util.ArrayList<>();
    private final java.util.List<ImageView> heroPillIcons = new java.util.ArrayList<>();
    private final java.util.List<TextView> heroPillLabels = new java.util.ArrayList<>();

    private void addHeroPillButton(LinearLayout parent, Context context, int iconRes, String title) {
        LinearLayout btn = new LinearLayout(context);
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));
        btn.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(20), 0x26FFFFFF));
        heroPillButtons.add(btn);

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setColorFilter(Color.WHITE);
        heroPillIcons.add(icon);
        btn.addView(icon, LayoutHelper.createLinear(18, 18, Gravity.CENTER_VERTICAL, 0, 0, 6, 0));

        TextView label = new TextView(context);
        label.setText(title);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        label.setTypeface(AndroidUtilities.bold());
        label.setTextColor(Color.WHITE);
        heroPillLabels.add(label);
        btn.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        parent.addView(btn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 4, 0, 4, 0));
    }


    public static final int TYPE_PAGE_BG = 0;
    public static final int TYPE_CARD_BG = 1;
    public static final int TYPE_ACCENT = 2;

    private int currentPageBg = 0;
    private int currentCardBg = 0;
    private int currentAccent = 0;
    private String currentPreset = ProfileColorController.PRESET_DEFAULT;

    // View компоненты живого предпросмотра
    private FrameLayout heroCard;
    private ImageView heroBannerView;
    private BackupImageView heroAvatarView;
    private ImageView heroFrameView;

    // Карточка музыки в превью
    private FrameLayout heroMusicCard;
    private FrameLayout heroMusicCover;
    private ImageView heroMusicIcon;
    private TextView heroMusicTitle;
    private TextView heroMusicArtist;
    private ImageView heroMusicEqualizer;

    // Карточка профиля в превью
    private LinearLayout heroInfoCard;
    private TextView heroBioValue;
    private TextView heroBioLabel;
    private TextView heroUsernameValue;
    private TextView heroUsernameLabel;
    private TextView heroBdayValue;
    private TextView heroBdayLabel;
    private TextView heroIdValue;
    private TextView heroIdLabel;
    private List<View> infoDividers = new ArrayList<>();

    // Вкладки
    private LinearLayout heroTabsLayout;
    private View heroTabIndicator;
    private TextView tab1;
    private TextView tab2;
    private TextView tab3;

    // Шапка и кнопки действий профиля
    private TextView heroNameView;
    private TextView heroOnlineView;
    private List<View> heroActionCircles = new ArrayList<>();
    private List<ImageView> heroActionIcons = new ArrayList<>();
    private List<TextView> heroActionLabels = new ArrayList<>();

    // Витрина подарков в профиле
    private List<FrameLayout> heroGiftCards = new ArrayList<>();
    private List<TextView> heroGiftStars = new ArrayList<>();
    private List<TextView> heroGiftTitles = new ArrayList<>();

    // Визуальные контролы выбора цвета
    private View pageBgDot;
    private View cardBgDot;
    private View accentDot;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Оформление профиля");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ProfileColorController controller = ProfileColorController.getInstance();
        ProfileColorController.ProfileColorMeta meta = controller.getLocalProfileColor();
        currentPageBg = meta.pageBgColor;
        currentCardBg = meta.cardBgColor;
        currentAccent = meta.accentColor;
        currentPreset = meta.preset;

        fragmentView = new ScrollView(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(32));
        ((ScrollView) fragmentView).addView(container, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // ==========================================
        // 1. HERO ПРЕДПРОСМОТР ПРОФИЛЯ
        // ==========================================
        heroCard = new FrameLayout(context);
        heroCard.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), 0xFF18181A));
        heroCard.setPadding(0, 0, 0, AndroidUtilities.dp(14));

        LinearLayout heroContent = new LinearLayout(context);
        heroContent.setOrientation(LinearLayout.VERTICAL);

        // 1.0 Баннер профиля
        FrameLayout bannerBox = new FrameLayout(context);
        heroBannerView = new ImageView(context);
        heroBannerView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroBannerView.setBackgroundColor(0xFF2C2C2E);
        bannerBox.addView(heroBannerView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        View bannerDim = new View(context);
        GradientDrawable dimGrad = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x25000000, 0x88000000});
        bannerDim.setBackground(dimGrad);
        bannerBox.addView(bannerDim, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Контент внутри шапки (аватарка, имя, статус и кнопки быстрого действия как на 1 фото)
        LinearLayout bannerContent = new LinearLayout(context);
        bannerContent.setOrientation(LinearLayout.VERTICAL);
        bannerContent.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout avatarWrap = new FrameLayout(context);
        heroAvatarView = new BackupImageView(context);
        heroAvatarView.setRoundRadius(AndroidUtilities.dp(34));

        TLRPC.User currentUser = getUserConfig().getCurrentUser();
        AvatarDrawable avatarDrawable = new AvatarDrawable();
        if (currentUser != null) {
            avatarDrawable.setInfo(currentAccount, currentUser);
            heroAvatarView.setForUserOrChat(currentUser, avatarDrawable);
        } else {
            avatarDrawable.setInfo(0, "User", null);
            heroAvatarView.setImageDrawable(avatarDrawable);
        }
        avatarWrap.addView(heroAvatarView, LayoutHelper.createFrame(68, 68, Gravity.CENTER));

        heroFrameView = new ImageView(context);
        heroFrameView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        avatarWrap.addView(heroFrameView, LayoutHelper.createFrame(102, 102, Gravity.CENTER));

        bannerContent.addView(avatarWrap, LayoutHelper.createLinear(102, 102, Gravity.CENTER_HORIZONTAL, 0, 16, 0, 4));

        heroNameView = new TextView(context);
        heroNameView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 19);
        heroNameView.setTypeface(AndroidUtilities.bold());
        heroNameView.setGravity(Gravity.CENTER);
        heroNameView.setTextColor(Color.WHITE);
        if (currentUser != null) {
            heroNameView.setText(org.telegram.messenger.UserObject.getUserName(currentUser));
        } else {
            heroNameView.setText("Пользователь");
        }
        bannerContent.addView(heroNameView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 16, 2, 16, 0));

        heroOnlineView = new TextView(context);
        heroOnlineView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        heroOnlineView.setGravity(Gravity.CENTER);
        heroOnlineView.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 170));
        heroOnlineView.setText("в сети");
        bannerContent.addView(heroOnlineView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 16, 2, 16, 8));

        // Полупрозрачные pill-кнопки быстрого действия поверх баннера (Выбрать фото / Изменить)
        LinearLayout heroActionButtonsRow = new LinearLayout(context);
        heroActionButtonsRow.setOrientation(LinearLayout.HORIZONTAL);
        heroActionButtonsRow.setGravity(Gravity.CENTER);

        heroPillButtons.clear();
        heroPillIcons.clear();
        heroPillLabels.clear();

        addHeroPillButton(heroActionButtonsRow, context, R.drawable.msg_camera, "Выбрать фото");
        addHeroPillButton(heroActionButtonsRow, context, R.drawable.msg_edit, "Изменить");

        bannerContent.addView(heroActionButtonsRow, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        bannerBox.addView(bannerContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
        heroContent.addView(bannerBox, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 260));

        // Загрузка баннера и рамки
        loadBannerImage();
        loadFrameImage();

        // ------------------------------------------
        // 1.1 Музыкальная карточка (Красивый плеер)
        // ------------------------------------------
        heroMusicCard = new FrameLayout(context);
        heroMusicCard.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));

        LinearLayout musicRow = new LinearLayout(context);
        musicRow.setOrientation(LinearLayout.HORIZONTAL);
        musicRow.setGravity(Gravity.CENTER_VERTICAL);

        // Обложка трека (стильный скругленный блок с градиентом)
        heroMusicCover = new FrameLayout(context);
        GradientDrawable coverGrad = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{0xFF2F3242, 0xFF1E202C});
        coverGrad.setCornerRadius(AndroidUtilities.dp(10));
        heroMusicCover.setBackground(coverGrad);

        heroMusicIcon = new ImageView(context);
        heroMusicIcon.setImageResource(R.drawable.msg_played);
        heroMusicIcon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        heroMusicIcon.setColorFilter(Color.WHITE);
        heroMusicCover.addView(heroMusicIcon, LayoutHelper.createFrame(22, 22, Gravity.CENTER));
        musicRow.addView(heroMusicCover, LayoutHelper.createLinear(42, 42, 0, 0, 12, 0));

        // Название и исполнитель
        LinearLayout musicTexts = new LinearLayout(context);
        musicTexts.setOrientation(LinearLayout.VERTICAL);
        heroMusicTitle = new TextView(context);
        heroMusicTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        heroMusicTitle.setTypeface(AndroidUtilities.bold());
        heroMusicTitle.setTextColor(Color.WHITE);
        heroMusicTitle.setSingleLine(true);
        heroMusicTitle.setEllipsize(TextUtils.TruncateAt.END);
        musicTexts.addView(heroMusicTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        heroMusicArtist = new TextView(context);
        heroMusicArtist.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        heroMusicArtist.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 140));
        heroMusicArtist.setSingleLine(true);
        heroMusicArtist.setEllipsize(TextUtils.TruncateAt.END);
        musicTexts.addView(heroMusicArtist, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        musicRow.addView(musicTexts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL));

        heroMusicEqualizer = new ImageView(context);
        heroMusicEqualizer.setImageResource(R.drawable.msg_played);
        heroMusicEqualizer.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        heroMusicEqualizer.setColorFilter(Color.WHITE);
        musicRow.addView(heroMusicEqualizer, LayoutHelper.createLinear(20, 20, Gravity.CENTER_VERTICAL, 8, 0, 4, 0));

        heroMusicCard.addView(musicRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        heroContent.addView(heroMusicCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 8));

        long myId = getUserConfig().getClientUserId();
        TLRPC.UserFull userInfo = getMessagesController().getUserFull(myId);

        // Музыка (если есть у пользователя)
        if (userInfo != null && userInfo.saved_music != null) {
            heroMusicCard.setVisibility(View.VISIBLE);
            CharSequence musicAuthor = org.telegram.ui.Components.ProfileMusicView.getAuthor(userInfo.saved_music);
            CharSequence musicTitle = org.telegram.ui.Components.ProfileMusicView.getTitle(userInfo.saved_music);
            String a = !TextUtils.isEmpty(musicAuthor) ? app.exteraless.nowplaying.ZeroWidthCodec.strip(musicAuthor).toString() : "";
            String t = !TextUtils.isEmpty(musicTitle) ? app.exteraless.nowplaying.ZeroWidthCodec.strip(musicTitle).toString() : "";
            if (!TextUtils.isEmpty(a) && !TextUtils.isEmpty(t)) {
                heroMusicTitle.setText(a + " • " + t);
            } else if (!TextUtils.isEmpty(t)) {
                heroMusicTitle.setText(t);
            } else {
                heroMusicTitle.setText(!TextUtils.isEmpty(a) ? a : "Музыка");
            }
            heroMusicArtist.setText("Слушает в профиле");
        } else {
            heroMusicCard.setVisibility(View.GONE);
        }

        // ------------------------------------------
        // 1.2 Карточка профиля (Аутентичные строки как в оригинальном профиле)
        // ------------------------------------------
        heroInfoCard = new LinearLayout(context);
        heroInfoCard.setOrientation(LinearLayout.VERTICAL);
        heroInfoCard.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        infoDividers.clear();

        // 1) О себе (только если заполнено у пользователя)
        heroBioValue = new TextView(context);
        heroBioValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        heroBioValue.setTextColor(Color.WHITE);

        heroBioLabel = new TextView(context);
        heroBioLabel.setText("О себе");
        heroBioLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        heroBioLabel.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 140));

        if (userInfo != null && !TextUtils.isEmpty(userInfo.about)) {
            heroBioValue.setText(userInfo.about);
            heroInfoCard.addView(createProfileInfoItem(context, heroBioValue, heroBioLabel));
            heroInfoCard.addView(addProfileDivider(context));
        }

        // 2) Имя пользователя / телефон
        heroUsernameValue = new TextView(context);
        heroUsernameValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        heroUsernameValue.setTypeface(AndroidUtilities.bold());

        heroUsernameLabel = new TextView(context);
        heroUsernameLabel.setText("Имя пользователя");
        heroUsernameLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        heroUsernameLabel.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 140));

        String username = currentUser != null ? org.telegram.messenger.UserObject.getPublicUsername(currentUser) : null;
        if (!TextUtils.isEmpty(username)) {
            heroUsernameValue.setText("@" + username);
            heroInfoCard.addView(createProfileInfoItem(context, heroUsernameValue, heroUsernameLabel));
            heroInfoCard.addView(addProfileDivider(context));
        } else if (currentUser != null && !TextUtils.isEmpty(currentUser.phone)) {
            heroUsernameValue.setText(PhoneFormat.getInstance().format("+" + currentUser.phone));
            heroUsernameLabel.setText("Номер телефона");
            heroInfoCard.addView(createProfileInfoItem(context, heroUsernameValue, heroUsernameLabel));
            heroInfoCard.addView(addProfileDivider(context));
        }

        // 3) ID аккаунта и дата-центр (как в оригинале: "5133421262 \n DC2 Venus, Amsterdam")
        heroIdValue = new TextView(context);
        heroIdValue.setText(String.valueOf(myId > 0 ? myId : 5133421262L));
        heroIdValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        heroIdValue.setTextColor(Color.WHITE);

        heroIdLabel = new TextView(context);
        heroIdLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        heroIdLabel.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 140));

        int dc = getConnectionsManager().getCurrentDatacenterId();
        if (dc != 0) {
            String name = tw.nekomimi.nekogram.DatacenterActivity.getDCName(dc);
            String loc = tw.nekomimi.nekogram.DatacenterActivity.getDCLocation(dc);
            if (!TextUtils.isEmpty(name) && !TextUtils.isEmpty(loc)) {
                heroIdLabel.setText(String.format(java.util.Locale.US, "DC%d %s, %s", dc, name, loc));
            } else {
                heroIdLabel.setText("DC " + dc);
            }
        } else {
            heroIdLabel.setText("ID пользователя");
        }
        heroInfoCard.addView(createProfileInfoItem(context, heroIdValue, heroIdLabel));

        // 4) День рождения (если указан у пользователя)
        heroBdayValue = new TextView(context);
        heroBdayValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        heroBdayValue.setTextColor(Color.WHITE);

        heroBdayLabel = new TextView(context);
        heroBdayLabel.setText("День рождения");
        heroBdayLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        heroBdayLabel.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 140));

        if (userInfo != null && userInfo.birthday != null) {
            heroInfoCard.addView(addProfileDivider(context));
            try {
                boolean today = org.telegram.messenger.BirthdayController.isToday(userInfo);
                boolean withYear = (userInfo.birthday.flags & 1) != 0;
                int age = withYear ? java.time.Period.between(java.time.LocalDate.of(userInfo.birthday.year, userInfo.birthday.month, userInfo.birthday.day), java.time.LocalDate.now()).getYears() : -1;
                String bdayStr = org.telegram.ui.UserInfoActivity.birthdayString(userInfo.birthday);
                if (withYear) {
                    bdayStr = org.telegram.messenger.LocaleController.formatPluralString(today ? "ProfileBirthdayTodayValueYear" : "ProfileBirthdayValueYear", age, bdayStr);
                } else {
                    bdayStr = org.telegram.messenger.LocaleController.formatString(today ? org.telegram.messenger.R.string.ProfileBirthdayTodayValue : org.telegram.messenger.R.string.ProfileBirthdayValue, bdayStr);
                }
                heroBdayValue.setText(bdayStr);
                heroBdayLabel.setText(org.telegram.messenger.LocaleController.getString(today ? org.telegram.messenger.R.string.ProfileBirthdayToday : org.telegram.messenger.R.string.ProfileBirthday));
            } catch (Throwable t) {
                heroBdayValue.setText(org.telegram.ui.UserInfoActivity.birthdayString(userInfo.birthday));
            }
            heroInfoCard.addView(createProfileInfoItem(context, heroBdayValue, heroBdayLabel));
        }

        heroContent.addView(heroInfoCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 0, 12, 10));

        // ------------------------------------------
        // 1.3 Вкладки (Публикации / Медиа / Подарки)
        // ------------------------------------------
        heroTabsLayout = new LinearLayout(context);
        heroTabsLayout.setOrientation(LinearLayout.HORIZONTAL);
        heroTabsLayout.setPadding(AndroidUtilities.dp(16), 0, AndroidUtilities.dp(16), 0);

        tab1 = new TextView(context);
        tab1.setText("Публикации");
        tab1.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tab1.setTypeface(AndroidUtilities.bold());
        tab1.setTextColor(Color.WHITE);
        heroTabsLayout.addView(tab1, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 16, 0));

        tab2 = new TextView(context);
        tab2.setText("Медиа");
        tab2.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tab2.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 140));
        heroTabsLayout.addView(tab2, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 16, 0));

        tab3 = new TextView(context);
        tab3.setText("Подарки");
        tab3.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        tab3.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 140));
        heroTabsLayout.addView(tab3, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        heroContent.addView(heroTabsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 4));

        heroTabIndicator = new View(context);
        heroContent.addView(heroTabIndicator, LayoutHelper.createLinear(74, 3, Gravity.LEFT, 16, 4, 0, 0));

        // 1.4 Мини-витрина подарков под вкладками
        LinearLayout giftsRow = new LinearLayout(context);
        giftsRow.setOrientation(LinearLayout.HORIZONTAL);
        giftsRow.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(2));

        heroGiftCards.clear();
        heroGiftStars.clear();
        heroGiftTitles.clear();

        LinearLayout emptyPostsLayout = new LinearLayout(context);
        emptyPostsLayout.setOrientation(LinearLayout.VERTICAL);
        emptyPostsLayout.setGravity(Gravity.CENTER);
        emptyPostsLayout.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(18), AndroidUtilities.dp(16), AndroidUtilities.dp(18));

        TextView emptyTitle = new TextView(context);
        emptyTitle.setText("Публикаций пока нет...");
        emptyTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        emptyTitle.setTypeface(AndroidUtilities.bold());
        emptyTitle.setGravity(Gravity.CENTER);
        emptyTitle.setTextColor(Color.WHITE);
        emptyPostsLayout.addView(emptyTitle, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 4));

        TextView emptySub = new TextView(context);
        emptySub.setText("Публикуйте фотографии и видео в своём профиле");
        emptySub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        emptySub.setGravity(Gravity.CENTER);
        emptySub.setTextColor(ColorUtils.setAlphaComponent(Color.WHITE, 140));
        emptyPostsLayout.addView(emptySub, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        heroContent.addView(emptyPostsLayout, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        heroCard.addView(heroContent, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        container.addView(heroCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

        // ==========================================
        // 2. КНОПКА «ПОДОБРАТЬ ПОД БАННЕР»
        // ==========================================
        LinearLayout autoBannerButton = new LinearLayout(context);
        autoBannerButton.setOrientation(LinearLayout.HORIZONTAL);
        autoBannerButton.setGravity(Gravity.CENTER);
        autoBannerButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                0xFF7266ED, 0xFF5D50DC));
        autoBannerButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        autoBannerButton.setOnClickListener(v -> autoExtractFromBanner());

        ImageView magicIcon = new ImageView(context);
        magicIcon.setImageResource(R.drawable.msg_theme);
        magicIcon.setColorFilter(Color.WHITE);
        autoBannerButton.addView(magicIcon, LayoutHelper.createLinear(20, 20, 0, 0, 8, 0));

        TextView magicText = new TextView(context);
        magicText.setText("Подобрать цвета под баннер");
        magicText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        magicText.setTypeface(AndroidUtilities.bold());
        magicText.setTextColor(Color.WHITE);
        autoBannerButton.addView(magicText);

        container.addView(autoBannerButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // ==========================================
        // 3. БЛОК ВИЗУАЛЬНОЙ НАСТРОЙКИ СВОИХ ЦВЕТОВ
        // (Убран список пресетов, теперь только визуальная палитра)
        // ==========================================
        LinearLayout paletteSection = new LinearLayout(context);
        paletteSection.setOrientation(LinearLayout.VERTICAL);
        paletteSection.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(14), Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider)));
        paletteSection.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16), AndroidUtilities.dp(16));

        TextView paletteTitle = new TextView(context);
        paletteTitle.setText("Выбор своих цветов");
        paletteTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        paletteTitle.setTypeface(AndroidUtilities.bold());
        paletteTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        paletteSection.addView(paletteTitle);

        TextView paletteSub = new TextView(context);
        paletteSub.setText("Нажми на нужный параметр, чтобы открыть цветовой круг и выбрать любой оттенок пальцем");
        paletteSub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        paletteSub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        paletteSection.addView(paletteSub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 14));

        // 3.1 Строка выбора фона страницы профиля
        View pageBgRow = createColorPickerRow(context, "Фон страницы профиля", "Цвет холста экрана под карточками", TYPE_PAGE_BG);
        pageBgRow.setOnClickListener(v -> showVisualColorPicker(TYPE_PAGE_BG));
        paletteSection.addView(pageBgRow);

        paletteSection.addView(createRowDivider(context));

        // 3.2 Строка выбора цвета карточек
        View cardBgRow = createColorPickerRow(context, "Цвет карточек профиля", "Фон блоков с инфой и музыкой", TYPE_CARD_BG);
        cardBgRow.setOnClickListener(v -> showVisualColorPicker(TYPE_CARD_BG));
        paletteSection.addView(cardBgRow);

        paletteSection.addView(createRowDivider(context));

        // 3.3 Строка выбора цвета акцента
        View accentRow = createColorPickerRow(context, "Акцентный цвет профиля", "Ник, ссылки, подсветка и вкладки", TYPE_ACCENT);
        accentRow.setOnClickListener(v -> showVisualColorPicker(TYPE_ACCENT));
        paletteSection.addView(accentRow);

        paletteSection.addView(createRowDivider(context));

        // Quick palette swatches removed per user request

        container.addView(paletteSection, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // ==========================================
        // 4. КНОПКА СБРОСА К ТЕМЕ ПО УМОЛЧАНИЮ
        // ==========================================
        TextView resetBtn = new TextView(context);
        resetBtn.setText("Сбросить цвета (по умолчанию из темы)");
        resetBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        resetBtn.setTypeface(AndroidUtilities.bold());
        resetBtn.setGravity(Gravity.CENTER);
        resetBtn.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
        resetBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10),
                0x15FF3B30, 0x30FF3B30));
        resetBtn.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(10), AndroidUtilities.dp(12), AndroidUtilities.dp(10));
        resetBtn.setOnClickListener(v -> resetToDefault());
        container.addView(resetBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

        // ==========================================
        // 5. КНОПКА СОХРАНЕНИЯ
        // ==========================================
        TextView saveButton = new TextView(context);
        saveButton.setText("Сохранить цвета для всех");
        saveButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        saveButton.setTypeface(AndroidUtilities.bold());
        saveButton.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
        saveButton.setGravity(Gravity.CENTER);
        saveButton.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        saveButton.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        saveButton.setOnClickListener(v -> saveColors());
        container.addView(saveButton, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        updateLivePreview();
        updateSelectorRows();

        return fragmentView;
    }

    private void addHeroActionButton(LinearLayout parent, Context context, int iconRes, String title) {
        LinearLayout btn = new LinearLayout(context);
        btn.setOrientation(LinearLayout.VERTICAL);
        btn.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout circle = new FrameLayout(context);
        circle.setBackground(Theme.createCircleDrawable(AndroidUtilities.dp(44), 0xFF242426));
        heroActionCircles.add(circle);

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconRes);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        icon.setColorFilter(0xFF7266ED);
        heroActionIcons.add(icon);
        circle.addView(icon, LayoutHelper.createFrame(22, 22, Gravity.CENTER));

        btn.addView(circle, LayoutHelper.createLinear(44, 44, Gravity.CENTER_HORIZONTAL));

        TextView label = new TextView(context);
        label.setText(title);
        label.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        label.setTypeface(AndroidUtilities.bold());
        label.setGravity(Gravity.CENTER);
        label.setTextColor(Color.WHITE);
        heroActionLabels.add(label);
        btn.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));

        parent.addView(btn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
    }

    private void addHeroGiftCard(LinearLayout parent, Context context, String emoji, String stars, String name) {
        FrameLayout card = new FrameLayout(context);
        card.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8), AndroidUtilities.dp(8));
        heroGiftCards.add(card);

        LinearLayout inner = new LinearLayout(context);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setGravity(Gravity.CENTER);

        TextView iconTv = new TextView(context);
        iconTv.setText(emoji);
        iconTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 24);
        iconTv.setGravity(Gravity.CENTER);
        inner.addView(iconTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView starTv = new TextView(context);
        starTv.setText(stars);
        starTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
        starTv.setTypeface(AndroidUtilities.bold());
        starTv.setGravity(Gravity.CENTER);
        heroGiftStars.add(starTv);
        inner.addView(starTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        TextView nameTv = new TextView(context);
        nameTv.setText(name);
        nameTv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
        nameTv.setGravity(Gravity.CENTER);
        heroGiftTitles.add(nameTv);
        inner.addView(nameTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 1, 0, 0));

        card.addView(inner, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        parent.addView(card, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 6, 0));
    }

    private View createProfileInfoItem(Context context, TextView valueTv, TextView labelTv) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.addView(valueTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        row.addView(labelTv, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
        return row;
    }

    private View addProfileDivider(Context context) {
        View div = new View(context);
        div.setBackgroundColor(0x18FFFFFF);
        infoDividers.add(div);
        return div;
    }

    private View createRowDivider(Context context) {
        View v = new View(context);
        v.setBackgroundColor(Theme.getColor(Theme.key_divider, resourceProvider));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(1));
        lp.topMargin = AndroidUtilities.dp(10);
        lp.bottomMargin = AndroidUtilities.dp(10);
        v.setLayoutParams(lp);
        return v;
    }

    private View createColorPickerRow(Context context, String title, String subtitle, int type) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(6), AndroidUtilities.dp(4), AndroidUtilities.dp(6));
        row.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(8), 0, Color.argb(20, 0, 0, 0)));
        row.setClickable(true);

        // Круг предпросмотра выбранного цвета
        View dot = new View(context);
        int initialColor;
        if (type == TYPE_PAGE_BG) {
            initialColor = currentPageBg != 0 ? currentPageBg : (currentCardBg != 0 ? currentCardBg : 0xFF121214);
            pageBgDot = dot;
        } else if (type == TYPE_CARD_BG) {
            initialColor = currentCardBg != 0 ? currentCardBg : 0xFF18181A;
            cardBgDot = dot;
        } else {
            initialColor = currentAccent != 0 ? currentAccent : 0xFF7266ED;
            accentDot = dot;
        }
        GradientDrawable dotDrawable = new GradientDrawable();
        dotDrawable.setShape(GradientDrawable.OVAL);
        dotDrawable.setColor(initialColor);
        dotDrawable.setStroke(AndroidUtilities.dp(2), 0x33FFFFFF);
        dot.setBackground(dotDrawable);

        row.addView(dot, LayoutHelper.createLinear(32, 32, 0, 0, 12, 0));

        // Заголовки
        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);

        TextView tvTitle = new TextView(context);
        tvTitle.setText(title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        tvTitle.setTypeface(AndroidUtilities.bold());
        tvTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        texts.addView(tvTitle);

        TextView tvSub = new TextView(context);
        tvSub.setText(subtitle);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        tvSub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        texts.addView(tvSub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

        row.addView(texts, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));

        // Кнопка «Выбрать»
        TextView pickBtn = new TextView(context);
        pickBtn.setText("Выбрать ›");
        pickBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        pickBtn.setTypeface(AndroidUtilities.bold());
        pickBtn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourceProvider));
        row.addView(pickBtn, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 6, 0, 4, 0));

        return row;
    }

    private void addQuickSwatchChip(LinearLayout parent, Context context, String title, int pageBg, int cardBg, int accent) {
        TextView chip = new TextView(context);
        chip.setText(title);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        chip.setTypeface(AndroidUtilities.bold());
        chip.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        chip.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(7), AndroidUtilities.dp(12), AndroidUtilities.dp(7));
        chip.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(14),
                Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider),
                Color.argb(30, 0, 0, 0)));
        chip.setClickable(true);
        chip.setOnClickListener(v -> {
            currentPageBg = pageBg;
            currentCardBg = cardBg;
            currentAccent = accent;
            currentPreset = ProfileColorController.PRESET_CUSTOM;
            updateLivePreview();
            updateSelectorRows();
        });

        parent.addView(chip, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
    }

    /**
     * Открывает круговой HSV ColorPicker (CircularColorPickerView) в BottomSheet.
     * Выбор цвета пальцем по идеальному цветовому кругу с живым HEX превью!
     */
    private void showVisualColorPicker(int type) {
        Context context = getParentActivity();
        if (context == null) return;

        BottomSheet.Builder builder = new BottomSheet.Builder(context, false, resourceProvider);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(16), AndroidUtilities.dp(20), AndroidUtilities.dp(20));

        String titleText;
        int initialColor;
        if (type == TYPE_PAGE_BG) {
            titleText = "Фон страницы профиля";
            initialColor = currentPageBg != 0 ? currentPageBg : (currentCardBg != 0 ? currentCardBg : 0xFF121214);
        } else if (type == TYPE_CARD_BG) {
            titleText = "Цвет карточек профиля";
            initialColor = currentCardBg != 0 ? currentCardBg : 0xFF18181A;
        } else {
            titleText = "Акцентный цвет профиля";
            initialColor = currentAccent != 0 ? currentAccent : 0xFF7266ED;
        }

        TextView title = new TextView(context);
        title.setText(titleText);
        title.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        title.setTypeface(AndroidUtilities.bold());
        title.setGravity(Gravity.CENTER);
        title.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        layout.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 10));

        // Контейнер с круглым превью цвета и HEX-кодом
        LinearLayout previewRow = new LinearLayout(context);
        previewRow.setOrientation(LinearLayout.HORIZONTAL);
        previewRow.setGravity(Gravity.CENTER);
        previewRow.setPadding(0, 0, 0, AndroidUtilities.dp(8));

        View swatchDot = new View(context);
        GradientDrawable swatchDrawable = new GradientDrawable();
        swatchDrawable.setShape(GradientDrawable.OVAL);
        swatchDrawable.setColor(initialColor);
        swatchDrawable.setStroke(AndroidUtilities.dp(2.5f), 0x33FFFFFF);
        swatchDot.setBackground(swatchDrawable);
        previewRow.addView(swatchDot, LayoutHelper.createLinear(36, 36, 0, 0, 10, 0));

        TextView hexLabel = new TextView(context);
        hexLabel.setText(String.format("#%06X", (0xFFFFFF & initialColor)));
        hexLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        hexLabel.setTypeface(AndroidUtilities.bold());
        hexLabel.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
        hexLabel.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(6), 0x15000000, 0x30000000));
        hexLabel.setPadding(AndroidUtilities.dp(8), AndroidUtilities.dp(4), AndroidUtilities.dp(8), AndroidUtilities.dp(4));
        previewRow.addView(hexLabel, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        layout.addView(previewRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        final BottomSheet[] sheetRef = new BottomSheet[1];

        // КРУГЛЫЙ COLOR PICKER (CircularColorPickerView)
        CircularColorPickerView circularPicker = new CircularColorPickerView(context);
        circularPicker.setColor(initialColor);

                hexLabel.setOnClickListener(v -> {
            final BottomSheet[] hexSheet = new BottomSheet[1];
            BottomSheet.Builder sheetBuilder = new BottomSheet.Builder(context, false);

            LinearLayout root = new LinearLayout(context);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(18), AndroidUtilities.dp(20), AndroidUtilities.dp(20));

            TextView sheetTitle = new TextView(context);
            sheetTitle.setText("Код цвета (HEX)");
            sheetTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            sheetTitle.setTypeface(AndroidUtilities.bold());
            sheetTitle.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
            sheetTitle.setGravity(Gravity.CENTER);
            root.addView(sheetTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 16));

            // Input card with preview and edit text
            LinearLayout inputCard = new LinearLayout(context);
            inputCard.setOrientation(LinearLayout.HORIZONTAL);
            inputCard.setGravity(Gravity.CENTER_VERTICAL);
            inputCard.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                    Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider),
                    Theme.getColor(Theme.key_listSelector, resourceProvider)));
            inputCard.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(14), AndroidUtilities.dp(8));

            // Live preview dot
            final View liveDot = new View(context);
            final GradientDrawable liveDotDrawable = new GradientDrawable();
            liveDotDrawable.setShape(GradientDrawable.OVAL);
            int startCol = 0xFFFFFF & (type == TYPE_PAGE_BG ? (currentPageBg != 0 ? currentPageBg : 0xFF121214) : (type == TYPE_CARD_BG ? (currentCardBg != 0 ? currentCardBg : 0xFF18181A) : (currentAccent != 0 ? currentAccent : 0xFF7266ED)));
            liveDotDrawable.setColor(0xFF000000 | startCol);
            liveDotDrawable.setStroke(AndroidUtilities.dp(2), 0x33FFFFFF);
            liveDot.setBackground(liveDotDrawable);
            inputCard.addView(liveDot, LayoutHelper.createLinear(34, 34, 0, 0, 10, 0));

            TextView hashPrefix = new TextView(context);
            hashPrefix.setText("#");
            hashPrefix.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
            hashPrefix.setTypeface(AndroidUtilities.bold());
            hashPrefix.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourceProvider));
            inputCard.addView(hashPrefix, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 4, 0));

            final org.telegram.ui.Components.EditTextBoldCursor input = new org.telegram.ui.Components.EditTextBoldCursor(context);
            input.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            input.setTypeface(AndroidUtilities.bold());
            String curHex = hexLabel.getText().toString();
            if (curHex.startsWith("#")) curHex = curHex.substring(1);
            input.setText(curHex.toUpperCase());
            input.setSelection(input.getText().length());
            input.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
            input.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack, resourceProvider));
            input.setBackground(null);
            input.setPadding(0, 0, 0, 0);
            input.setSingleLine(true);
            inputCard.addView(input, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

            root.addView(inputCard, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 14));

            // TextWatcher for live preview
            input.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String h = s.toString().trim();
                    if (!h.startsWith("#")) h = "#" + h;
                    try {
                        int pc = Color.parseColor(h);
                        liveDotDrawable.setColor(pc);
                        liveDot.invalidate();
                    } catch (Throwable ignore) {}
                }
                @Override
                public void afterTextChanged(android.text.Editable s) {}
            });

            // Quick color suggestions row
            TextView presetsTitle = new TextView(context);
            presetsTitle.setText("Популярные цвета");
            presetsTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            presetsTitle.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourceProvider));
            root.addView(presetsTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 0, 6));

            LinearLayout chipsRow = new LinearLayout(context);
            chipsRow.setOrientation(LinearLayout.HORIZONTAL);
            int[] chipColors = new int[]{0xFFFF7597, 0xFF7266ED, 0xFF64B5F6, 0xFF81C784, 0xFFFFD54F, 0xFFFF8A65, 0xFFFFFFFF, 0xFF1E1F24};
            for (int col : chipColors) {
                View chip = new View(context);
                GradientDrawable cd = new GradientDrawable();
                cd.setShape(GradientDrawable.OVAL);
                cd.setColor(col);
                cd.setStroke(AndroidUtilities.dp(1.5f), 0x33000000);
                chip.setBackground(cd);
                chip.setOnClickListener(cv -> {
                    String ch = String.format("%06X", 0xFFFFFF & col);
                    input.setText(ch);
                    input.setSelection(ch.length());
                    liveDotDrawable.setColor(col);
                    liveDot.invalidate();
                });
                chipsRow.addView(chip, LayoutHelper.createLinear(28, 28, 0, 0, 10, 0));
            }
            root.addView(chipsRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 4, 0, 0, 18));

            // Buttons row: Cancel and Apply
            LinearLayout btnRow = new LinearLayout(context);
            btnRow.setOrientation(LinearLayout.HORIZONTAL);

            TextView cancelBtn = new TextView(context);
            cancelBtn.setText(LocaleController.getString(R.string.Cancel));
            cancelBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            cancelBtn.setTypeface(AndroidUtilities.bold());
            cancelBtn.setGravity(Gravity.CENTER);
            cancelBtn.setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourceProvider));
            cancelBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10), 0x15000000, 0x30000000));
            cancelBtn.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10));
            cancelBtn.setOnClickListener(bv -> {
                if (hexSheet[0] != null) hexSheet[0].dismiss();
            });
            btnRow.addView(cancelBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f, 0, 0, 8, 0));

            TextView applyBtn = new TextView(context);
            applyBtn.setText("Применить");
            applyBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            applyBtn.setTypeface(AndroidUtilities.bold());
            applyBtn.setGravity(Gravity.CENTER);
            applyBtn.setTextColor(Theme.getColor(Theme.key_featuredStickers_buttonText, resourceProvider));
            applyBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(10),
                    Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                    Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
            applyBtn.setPadding(AndroidUtilities.dp(14), AndroidUtilities.dp(10), AndroidUtilities.dp(14), AndroidUtilities.dp(10));
            applyBtn.setOnClickListener(bv -> {
                String hex = input.getText().toString().trim();
                if (!hex.startsWith("#")) hex = "#" + hex;
                try {
                    int parsedColor = Color.parseColor(hex);
                    swatchDrawable.setColor(parsedColor);
                    circularPicker.setColor(parsedColor);
                    hexLabel.setText(String.format("#%06X", (0xFFFFFF & parsedColor)));
                    if (type == TYPE_PAGE_BG) {
                        currentPageBg = parsedColor;
                    } else if (type == TYPE_CARD_BG) {
                        currentCardBg = parsedColor;
                    } else {
                        currentAccent = parsedColor;
                    }
                    currentPreset = ProfileColorController.PRESET_CUSTOM;
                    updateLivePreview();
                    updateSelectorRows();
                    if (hexSheet[0] != null) hexSheet[0].dismiss();
                } catch (Exception e) {
                    BulletinFactory.of(ProfileColorActivity.this).createSimpleBulletin(R.raw.error, "Неверный формат HEX (например #FFAA00)").show();
                }
            });
            btnRow.addView(applyBtn, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1.0f));

            root.addView(btnRow, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            sheetBuilder.setCustomView(root);
            hexSheet[0] = sheetBuilder.show();
        });
        circularPicker.setOnColorChangedListener(color -> {
            swatchDrawable.setColor(color);
            hexLabel.setText(String.format("#%06X", (0xFFFFFF & color)));

            if (type == TYPE_PAGE_BG) {
                currentPageBg = color;
            } else if (type == TYPE_CARD_BG) {
                currentCardBg = color;
            } else {
                currentAccent = color;
            }
            currentPreset = ProfileColorController.PRESET_CUSTOM;
            updateLivePreview();
            updateSelectorRows();
        });
        layout.addView(circularPicker, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 6, 0, 10));

        TextView doneBtn = new TextView(context);
        doneBtn.setText("Готово");
        doneBtn.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        doneBtn.setTypeface(AndroidUtilities.bold());
        doneBtn.setGravity(Gravity.CENTER);
        doneBtn.setTextColor(Color.WHITE);
        doneBtn.setBackground(Theme.createSimpleSelectorRoundRectDrawable(AndroidUtilities.dp(12),
                Theme.getColor(Theme.key_featuredStickers_addButton, resourceProvider),
                Theme.getColor(Theme.key_featuredStickers_addButtonPressed, resourceProvider)));
        doneBtn.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));
        doneBtn.setOnClickListener(v -> {
            if (sheetRef[0] != null) {
                sheetRef[0].dismiss();
            }
        });
        layout.addView(doneBtn, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 10, 0, 0));

        builder.setCustomView(layout);
        sheetRef[0] = builder.create();
        sheetRef[0].show();
    }

    private void updateSelectorRows() {
        if (pageBgDot != null) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(currentPageBg != 0 ? currentPageBg : (currentCardBg != 0 ? currentCardBg : 0xFF121214));
            d.setStroke(AndroidUtilities.dp(2), 0x33888888);
            pageBgDot.setBackground(d);
        }
        if (cardBgDot != null) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(currentCardBg != 0 ? currentCardBg : 0xFF18181A);
            d.setStroke(AndroidUtilities.dp(2), 0x33888888);
            cardBgDot.setBackground(d);
        }
        if (accentDot != null) {
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(currentAccent != 0 ? currentAccent : 0xFF7266ED);
            d.setStroke(AndroidUtilities.dp(2), 0x33888888);
            accentDot.setBackground(d);
        }
    }

    private void resetToDefault() {
        currentPreset = ProfileColorController.PRESET_DEFAULT;
        currentPageBg = 0;
        currentCardBg = 0;
        currentAccent = 0;
        updateLivePreview();
        updateSelectorRows();
        BulletinFactory.of(this).createSimpleBulletin(
                R.raw.done,
                "Цвета сброшены",
                "Возвращено стандартное оформление темы"
        ).show();
    }

    private void updateLivePreview() {
        ProfileColorController.ProfileColorMeta meta = new ProfileColorController.ProfileColorMeta(currentCardBg, currentPageBg, currentAccent, currentPreset);
        int cardBg = meta.getCardBgColor();
        int pageBg = meta.getPageBgColor();
        int accent = currentAccent != 0 ? currentAccent : 0xFF7266ED;
        int strokeColor = meta.getCardStrokeColor(1.0f);

        // Фон всей карточки превью
        heroCard.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(16), pageBg != 0 ? pageBg : 0xFF141416));

        boolean isLight = meta.isLight();
        int textColor = meta.getTextColor();
        int subtextColor = meta.getSubtextColor();

        // Имя и онлайн
        if (heroNameView != null) heroNameView.setTextColor(textColor);
        if (heroOnlineView != null) heroOnlineView.setTextColor(subtextColor);

        // Кнопки действий
        for (View circle : heroActionCircles) {
            GradientDrawable cd = new GradientDrawable();
            cd.setShape(GradientDrawable.OVAL);
            cd.setColor(cardBg);
            cd.setStroke(AndroidUtilities.dp(1), strokeColor);
            circle.setBackground(cd);
        }
        for (ImageView icon : heroActionIcons) {
            icon.setColorFilter(accent);
        }
        for (TextView lbl : heroActionLabels) {
            lbl.setTextColor(textColor);
        }

        // Фон карточки музыки с тонким аккуратным бордером
        GradientDrawable musicBg = new GradientDrawable();
        musicBg.setColor(cardBg);
        musicBg.setCornerRadius(AndroidUtilities.dp(12));
        musicBg.setStroke(AndroidUtilities.dp(1), strokeColor);
        heroMusicCard.setBackground(musicBg);

        // Фон карточки инфо с тонким аккуратным бордером
        GradientDrawable infoBg = new GradientDrawable();
        infoBg.setColor(cardBg);
        infoBg.setCornerRadius(AndroidUtilities.dp(12));
        infoBg.setStroke(AndroidUtilities.dp(1), strokeColor);
        heroInfoCard.setBackground(infoBg);

        // Цвета музыки
        if (heroMusicTitle != null) heroMusicTitle.setTextColor(textColor);
        if (heroMusicArtist != null) heroMusicArtist.setTextColor(subtextColor);
        if (heroMusicEqualizer != null) heroMusicEqualizer.setColorFilter(accent);

        // Цвета инфо карточки
        heroBioValue.setTextColor(textColor);
        heroBioLabel.setTextColor(subtextColor);

        heroUsernameValue.setTextColor(accent);
        heroUsernameLabel.setTextColor(subtextColor);

        heroBdayValue.setTextColor(textColor);
        heroBdayLabel.setTextColor(subtextColor);

        heroIdValue.setTextColor(textColor);
        heroIdLabel.setTextColor(subtextColor);

        // Разделители внутри инфо карточки
        int divColor = isLight ? 0x14000000 : 0x18FFFFFF;
        for (View div : infoDividers) {
            div.setBackgroundColor(divColor);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(0.8f));
            lp.topMargin = AndroidUtilities.dp(6);
            lp.bottomMargin = AndroidUtilities.dp(6);
            div.setLayoutParams(lp);
        }

        // Вкладки
        tab1.setTextColor(textColor);
        tab2.setTextColor(subtextColor);
        tab3.setTextColor(subtextColor);
        heroTabIndicator.setBackgroundColor(accent);

        // Витрина подарков
        for (FrameLayout gc : heroGiftCards) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(cardBg);
            gd.setCornerRadius(AndroidUtilities.dp(12));
            gd.setStroke(AndroidUtilities.dp(1), strokeColor);
            gc.setBackground(gd);
        }
        for (TextView st : heroGiftStars) {
            st.setTextColor(accent);
        }
        for (TextView tt : heroGiftTitles) {
            tt.setTextColor(subtextColor);
        }
    }

    private void loadBannerImage() {
        try {
            long myId = getUserConfig().getClientUserId();
            String path = ProfileBannerController.getInstance().resolveBannerPath(myId);
            if (TextUtils.isEmpty(path) && ProfileBannerController.getInstance().hasLocalBanner()) {
                path = ProfileBannerController.getInstance().getLocalBannerPath();
            }
            if (!TextUtils.isEmpty(path)) {
                Bitmap bmp = null;
                if (ProfileBannerController.getInstance().isVideo(path)) {
                    bmp = ProfileBannerController.getInstance().getVideoThumbnail(path);
                } else {
                    bmp = BitmapFactory.decodeFile(path);
                }
                if (bmp != null) {
                    heroBannerView.setImageBitmap(bmp);
                }
            }
        } catch (Throwable ignore) {}
    }

    private void loadFrameImage() {
        try {
            long myId = getUserConfig().getClientUserId();
            ProfileFrameController.FrameMeta meta = ProfileFrameController.getInstance().getFrameFor(myId);
            if (meta != null && meta.hasFrame()) {
                Bitmap bmp = ProfileFrameController.getInstance().getFrameBitmap(meta);
                if (bmp != null) {
                    heroFrameView.setImageBitmap(bmp);
                    heroFrameView.setVisibility(View.VISIBLE);
                    return;
                }
            }
            heroFrameView.setVisibility(View.GONE);
        } catch (Throwable ignore) {}
    }

    private void autoExtractFromBanner() {
        try {
            long myId = getUserConfig().getClientUserId();
            Bitmap bannerBmp = null;
            String path = ProfileBannerController.getInstance().resolveBannerPath(myId);
            if (TextUtils.isEmpty(path) && ProfileBannerController.getInstance().hasLocalBanner()) {
                path = ProfileBannerController.getInstance().getLocalBannerPath();
            }
            if (!TextUtils.isEmpty(path)) {
                if (ProfileBannerController.getInstance().isVideo(path)) {
                    bannerBmp = ProfileBannerController.getInstance().getVideoThumbnail(path);
                } else {
                    bannerBmp = BitmapFactory.decodeFile(path);
                }
            }
            if (bannerBmp != null) {
                ProfileColorController.ProfileColorMeta extracted = ProfileColorController.extractFromBanner(bannerBmp);
                currentPageBg = extracted.pageBgColor;
                currentCardBg = extracted.cardBgColor;
                currentAccent = extracted.accentColor;
                currentPreset = ProfileColorController.PRESET_BANNER;
                updateLivePreview();
                updateSelectorRows();
                BulletinFactory.of(this).createSimpleBulletin(
                        R.raw.done,
                        "Цвета подобраны!",
                        "Палитра гармонично извлечена из твоего баннера"
                ).show();
                return;
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        BulletinFactory.of(this).createSimpleBulletin(
                R.raw.info,
                "Баннер не найден",
                "Установи баннер в настройках, чтобы подобрать цвета автоматически!"
        ).show();
    }

    private void saveColors() {
        AlertDialog progressDialog = new AlertDialog(getParentActivity(), 3);
        progressDialog.setMessage("Сохранение цветов профиля...");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.show();

        long myId = getUserConfig().getClientUserId();

        Utilities.globalQueue.postRunnable(() -> {
            try {
                ProfileColorController.getInstance().setLocalProfileColor(currentCardBg, currentPageBg, currentAccent, currentPreset);
                AndroidUtilities.runOnUIThread(() -> {
                    for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                        NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.didSetNewTheme);
                        NotificationCenter.getInstance(a).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                    }
                });

                ProfileColorController.getInstance().setRemoteColor(myId, currentCardBg, currentPageBg, currentAccent, currentPreset, () -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            progressDialog.dismiss();
                        } catch (Throwable ignore) {}
                        BulletinFactory.of(this).createSimpleBulletin(
                                R.raw.done,
                                "Цвета профиля сохранены!",
                                "Теперь твой профиль оформлен стильно для всех пользователей"
                        ).show();
                        AndroidUtilities.runOnUIThread(this::finishFragment, 1500);
                    });
                }, err -> {
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            progressDialog.dismiss();
                        } catch (Throwable ignore) {}
                        BulletinFactory.of(this).createSimpleBulletin(
                                R.raw.done,
                                "Цвета применены!",
                                "Локально цвета установлены"
                        ).show();
                        AndroidUtilities.runOnUIThread(this::finishFragment, 1500);
                    });
                });
            } catch (Throwable t) {
                FileLog.e(t);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        progressDialog.dismiss();
                    } catch (Throwable ignore) {}
                });
            }
        });
    }
}
