package app.tsunderegram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;


import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.RecyclerListView;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

/**
 * Корневой экран раздела tsunderegram.
 * Структура повторяет настройки TsundereGram: About-шапка + категории с иконками.
 * Открывается из главных настроек (SettingsActivity, id 102).
 */
public class OpenExteraSettingsActivity extends BaseNekoSettingsActivity {

    private static final int MENU_CLOUD = 1;
    private static final int MENU_SEARCH = 2;

    private static final int TYPE_ABOUT = 100;

    private int aboutRow;

    private int categoriesHeaderRow;
    private int generalRow;
    private int appearanceRow;
    private int chatsRow;
    private int pluginsRow;
    private int ghostRow;
    private int appIconRow;
    private int otherRow;
    private int adminBadgesRow;
    private int categoriesDividerRow;

    private int linksHeaderRow;
    private int channelRow;
    private int linksDividerRow;

    @Override
    protected void updateRows() {
        super.updateRows();

        aboutRow = addRow("about");

        categoriesHeaderRow = addRow("categoriesHeader");
        generalRow = addRow("general");
        ghostRow = addRow("ghost");
        appearanceRow = addRow("appearance");
        chatsRow = addRow("chats");
        pluginsRow = addRow("plugins");
        appIconRow = addRow("appIcon");
        otherRow = addRow("other");
        if (com.tsunderegram.messenger.badges.BadgesController.INSTANCE.canManageBadges(UserConfig.getInstance(currentAccount).getClientUserId())) {
            adminBadgesRow = addRow("adminBadges");
        } else {
            adminBadgesRow = -1;
        }
        categoriesDividerRow = addRow();

        linksHeaderRow = addRow("linksHeader");
        channelRow = addRow("channel");
        linksDividerRow = addRow();
    }

    /**
     * У экстеры корневой экран устроен так: actionBar не занимает места и прозрачен,
     * пока список не прокручен, поэтому логотип стоит выше (в 12.9.0 верх логотипа
     * y=242 при низе actionBar 283). Заголовок проявляется вместе с фоном шапки.
     */
    @Override
    public View createView(Context context) {
        View view = super.createView(context);
        getMessagesController().getContentSettings(null);
        if (listView != null) {
            // Сразу резервируем место под строку состояния, чтобы первый layout
            // (до прихода insets) не ставил баннер под неё.
            listView.setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
        }
        if (actionBar != null && fragmentView instanceof android.widget.FrameLayout) {
            // 1:1 с MainPreferencesActivity.createView из 12.9.0.
            actionBar.setBackground(null);
            actionBar.setCastShadows(false);
            actionBar.setAddToContainer(false);
            if (actionBar.getTitleTextView() != null) {
                actionBar.getTitleTextView().setAlpha(0f);
            }
            ((android.widget.FrameLayout) fragmentView).addView(actionBar,
                    org.telegram.ui.Components.LayoutHelper.createFrame(
                            org.telegram.ui.Components.LayoutHelper.MATCH_PARENT,
                            org.telegram.ui.Components.LayoutHelper.WRAP_CONTENT,
                            android.view.Gravity.TOP));
        }
        if (actionBar != null) {
            final org.telegram.ui.ActionBar.ActionBarMenu menu = actionBar.createMenu();
            menu.addItem(MENU_SEARCH, R.drawable.outline_header_search, getResourceProvider());
            menu.addItem(MENU_CLOUD, R.drawable.cloud_sync, getResourceProvider());
            actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
                @Override
                public void onItemClick(int id) {
                    if (id == -1) {
                        finishFragment();
                    } else if (id == MENU_CLOUD) {
                        presentFragment(new OpenExteraCloudActivity());
                    } else if (id == MENU_SEARCH) {
                        showSettingsSearchDialog();
                    }
                }
            });
        }
        return view;
    }

    /**
     * Базовый класс в onInsets обнуляет верхний отступ списка. Нам он нужен: actionBar
     * прозрачный и не занимает места, поэтому без отступа контент уезжает под строку
     * состояния и логотип срезает.
     */
    /**
     * У экстеры на корневом экране адаптивный фон НЕ включается вовсе:
     * {@code BasePreferencesActivity.createView} вызывает {@code setAdaptiveBackground}
     * только при {@code !hasHeaderCell()}, а у {@code MainPreferencesActivity}
     * {@code hasHeaderCell()} возвращает true. Шапка остаётся прозрачной всегда,
     * заголовок не проявляется даже при прокрутке.
     */
    @Override
    protected void setupAdaptiveBackground() {
    }

    /** Блюр-подложка NagramX закрывает верх логотипа; у экстеры её нет. */
    @Override
    protected boolean needActionBarBlur() {
        return false;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        if (listView != null) {
            final int oldTop = listView.topPadding;
            listView.setPadding(0, top, 0, bottom);
            listView.setClipToPadding(false);
            if (oldTop != top) {
                reanchorListToTop();
            }
        }
    }

    /**
     * После пересборки экрана (например, «Оформление» вызывает rebuildAllFragmentViews)
     * список создаётся заново и первый layout проходит ещё без верхнего отступа.
     * Когда приходят insets, LinearLayoutManager держит якорь на старой позиции —
     * баннер оставался под строкой состояния, налезая на actionBar, пока список
     * не тронут. Если пользователь ничего не прокручивал, возвращаем список в начало.
     */
    private void reanchorListToTop() {
        if (listView == null || layoutManager == null) {
            return;
        }
        if (listView.getScrollState() != androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_IDLE) {
            return;
        }
        final int first = layoutManager.findFirstVisibleItemPosition();
        if (first <= 0) {
            layoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

    @Override
    public void onTransitionAnimationStart(boolean isOpen, boolean backward) {
        super.onTransitionAnimationStart(isOpen, backward);
        if (isOpen && backward) {
            reanchorListToTop();
        }
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.OpenExtera);
    }

    @Override
    public int getSearchGuid() {
        return 24000;
    }

    @Override
    public int getSearchIcon() {
        return R.drawable.msg_settings_old;
    }

    @Override
    public String getSearchPrefix() {
        return "OpenExtera";
    }

    @Override
    protected String getKey() {
        return "tsunderegram";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == generalRow) {
            presentFragment(new OpenExteraGeneralActivity());
        } else if (position == appearanceRow) {
            presentFragment(new OpenExteraAppearanceActivity());
        } else if (position == chatsRow) {
            presentFragment(new OpenExteraChatsActivity());
        } else if (position == pluginsRow) {
            presentFragment(new app.tsunderegram.plugins.ui.PluginsActivity());
        } else if (position == ghostRow) {
            presentFragment(new OpenExteraAyuMomentsActivity());
        } else if (position == appIconRow) {
            presentFragment(new app.tsunderegram.appicons.AppIconsActivity());
        } else if (position == otherRow) {
            presentFragment(new OpenExteraOtherActivity());
        } else if (position == adminBadgesRow) {
            presentFragment(new com.tsunderegram.messenger.badges.BadgeManageActivity(0, null));
        } else if (position == channelRow) {
            getMessagesController().openByUserName("tsunderegram", this, 1);
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            if (viewType == TYPE_ABOUT) {
                View view = new org.telegram.tsunderestyle.settings.TsundereStyleBannerHeader(mContext);
                view.setTag(RecyclerListView.TAG_NOT_SECTION);
                view.setLayoutParams(new RecyclerView.LayoutParams(
                        RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(view);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == categoriesHeaderRow) {
                        cell.setText(getString(R.string.OpenExteraCategories));
                    } else if (position == linksHeaderRow) {
                        cell.setText(getString(R.string.OpenExteraLinks));
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == generalRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraGeneral), R.drawable.msg_media, true);
                    } else if (position == appearanceRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraAppearance), R.drawable.msg_theme, true);
                    } else if (position == chatsRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraChats), R.drawable.msg_discussion, true);
                    } else if (position == pluginsRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraPlugins), R.drawable.msg_plugins, true);
                    } else if (position == ghostRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraGhost), R.drawable.ayu_ghost, true);
                    } else if (position == appIconRow) {
                        cell.setTextAndIcon(getString(R.string.AppIcon), R.drawable.msg_theme, true);
                        android.widget.ImageView viv = cell.getValueImageView();
                        if (viv != null) {
                            viv.setVisibility(View.VISIBLE);
                            viv.setImageDrawable(getAppIconPreviewDrawable());
                        }
                    } else if (position == otherRow) {
                        cell.setTextAndIcon(getString(R.string.OpenExteraOther), R.drawable.msg_fave, adminBadgesRow != -1);
                    } else if (position == adminBadgesRow) {
                        cell.setTextAndIcon("Управление значками", R.drawable.msg_admins, false);
                    } else if (position == channelRow) {
                        cell.setTextAndValueAndIcon(getString(R.string.ProfileChannel),
                                "@tsunderegram", R.drawable.msg_channel, false);
                    }
                    // ВАЖНО: только после setTextAndIcon* — они сбрасывают imageLeft в 16dp.
                    // Метрики сняты с 12.9.0 (420 dpi): иконка 88px от края экрана, текст 219px,
                    // то есть 21dp и 71dp вместо дефолтных 16dp и 58dp.
                    cell.setImageLeft(21);
                    cell.setOffsetFromImage(71);
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == linksDividerRow) {
                        cell.setText(null);
                        cell.setBackground(Theme.getThemedDrawable(mContext,
                                R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_TEXT;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == aboutRow) {
                return TYPE_ABOUT;
            } else if (position == categoriesHeaderRow || position == linksHeaderRow) {
                return TYPE_HEADER;
            } else if (position == categoriesDividerRow) {
                return TYPE_SHADOW;
            } else if (position == linksDividerRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_TEXT;
        }
    }

    private android.graphics.drawable.Drawable appIconPreviewDrawable;

    @Override
    public void onResume() {
        super.onResume();
        // После выбора другой иконки превью в строке должно обновиться
        appIconPreviewDrawable = null;
        if (listAdapter != null && appIconRow >= 0) {
            try {
                listAdapter.notifyItemChanged(appIconRow);
            } catch (Throwable ignore) {
            }
        }
    }

    private android.graphics.drawable.Drawable getAppIconPreviewDrawable() {
        if (appIconPreviewDrawable == null && getContext() != null) {
            try {
                int size = AndroidUtilities.dp(28);
                // Превью выбранной иконки приложения
                final boolean arrow = app.tsunderegram.appicons.AppIcons.current() == org.telegram.ui.LauncherIconController.LauncherIcon.ARROW;
                android.graphics.Bitmap src = android.graphics.BitmapFactory.decodeResource(getContext().getResources(), arrow ? R.drawable.tsundere_arrow_avatar : R.drawable.tsunderegram_icon);
                if (src != null) {
                    android.graphics.Bitmap scaled = android.graphics.Bitmap.createScaledBitmap(src, size, size, true);
                    androidx.core.graphics.drawable.RoundedBitmapDrawable rounded =
                            androidx.core.graphics.drawable.RoundedBitmapDrawableFactory.create(getContext().getResources(), scaled);
                    // Такое же скругление, как у остальных иконок (скруглённый квадрат, а не круг)
                    rounded.setCornerRadius(size * 0.22f);
                    rounded.setAntiAlias(true);
                    appIconPreviewDrawable = rounded;
                }
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        return appIconPreviewDrawable;
    }
}
