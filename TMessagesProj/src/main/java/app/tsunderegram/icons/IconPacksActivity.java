package app.tsunderegram.icons;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadioButton;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.DocumentSelectActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import app.tsunderegram.icons.ui.NewIconPackBottomSheet;
import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;
import tw.nekomimi.nekogram.ui.icons.IconsResources;

/**
 * Экран «Наборы иконок» в духе TsundereGram: базовые наборы с превью 2×2, включённые наборы
 * (порядок = приоритет, перетаскивание за ручку), все остальные наборы, кнопка «+»
 * и меню по удержанию (Изменить / Поделиться / Удалить). Общего выключателя больше нет:
 * подмена работает, пока включён хотя бы один набор.
 */
public class IconPacksActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_PACK = 100;
    private static final int TYPE_BASE = 101;

    /** Иконки для превью 2×2 — как у TsundereGram: смайлик, ссылка, скрепка, картинка. */
    private static final String[] PREVIEW_ICONS = {"msg_emoji_cat", "msg_link", "msg_pin", "msg_gallery"};

    private int basePacksHeaderRow;
    private int baseDefaultRow;
    private int baseSolarRow;
    private int baseRemixRow;
    private int baseInfoRow;

    private int activeHeaderRow;
    private int activeStartRow;
    private int activeEndRow;
    private int activeShadowRow;

    private int allHeaderRow;
    private int allStartRow;
    private int allEndRow;
    private int packsInfoRow;

    private int editingRow;
    private int editingDividerRow;

    private final List<IconPack> activeList = new ArrayList<>();
    private final List<IconPack> inactiveList = new ArrayList<>();
    private final Map<String, Drawable[]> previewCache = new HashMap<>();

    private ImageView fab;
    private ItemTouchHelper touchHelper;
    private boolean orderChanged;

    public IconPacksActivity() {
        super();
    }

    @Override
    public boolean onFragmentCreate() {
        IconPacksConfig.init();
        syncEnabledFlag();
        return super.onFragmentCreate();
    }

    /** Общий выключатель убран: подмена включена, пока есть хотя бы один включённый набор. */
    private static boolean syncEnabledFlag() {
        boolean any = false;
        for (String id : IconPacksConfig.getActivePackIds()) {
            if (IconPackStorage.findPackById(id) != null) {
                any = true;
                break;
            }
        }
        if (IconPacksConfig.enabled() != any) {
            IconPacksConfig.enabled.setConfigBool(any);
            IconPackManager.getInstance().reload();
            return true;
        }
        return false;
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        activeList.clear();
        inactiveList.clear();
        List<IconPack> installed = IconPackStorage.getInstalledPacks();
        List<String> activeIds = IconPacksConfig.getActivePackIds();
        for (String id : activeIds) {
            for (IconPack pack : installed) {
                if (pack.getId().equals(id)) {
                    activeList.add(pack);
                    break;
                }
            }
        }
        for (IconPack pack : installed) {
            if (!activeIds.contains(pack.getId())) {
                inactiveList.add(pack);
            }
        }

        basePacksHeaderRow = addRow("iconPacksBase");
        baseDefaultRow = addRow("iconPackBaseDefault");
        baseSolarRow = addRow("iconPackBaseSolar");
        baseRemixRow = addRow("iconPackBaseRemix");
        baseInfoRow = addRow();

        if (!activeList.isEmpty()) {
            activeHeaderRow = addRow("iconPacksActive");
            activeStartRow = rowCount;
            rowCount += activeList.size();
            activeEndRow = rowCount;
            activeShadowRow = inactiveList.isEmpty() ? -1 : addRow();
        } else {
            activeHeaderRow = activeStartRow = activeEndRow = activeShadowRow = -1;
        }

        if (!inactiveList.isEmpty()) {
            allHeaderRow = addRow("iconPacksList");
            allStartRow = rowCount;
            rowCount += inactiveList.size();
            allEndRow = rowCount;
        } else {
            allHeaderRow = allStartRow = allEndRow = -1;
        }
        packsInfoRow = addRow();

        if (IconPacksConfig.isEditing()) {
            editingRow = addRow("iconPacksEditing");
            editingDividerRow = addRow();
        } else {
            editingRow = editingDividerRow = -1;
        }
    }

    @Override
    protected String getActionBarTitle() {
        return "Наборы иконок";
    }

    @Override
    protected String getKey() {
        return "iconpacks";
    }

    private boolean isActiveRow(int position) {
        return activeStartRow != -1 && position >= activeStartRow && position < activeEndRow;
    }

    private boolean isInactiveRow(int position) {
        return allStartRow != -1 && position >= allStartRow && position < allEndRow;
    }

    private IconPack packAt(int position) {
        if (isActiveRow(position)) {
            return activeList.get(position - activeStartRow);
        }
        if (isInactiveRow(position)) {
            return inactiveList.get(position - allStartRow);
        }
        return null;
    }

    @Override
    public View createView(Context context) {
        View view = super.createView(context);

        fab = new ImageView(context);
        fab.setScaleType(ImageView.ScaleType.CENTER);
        fab.setImageResource(R.drawable.msg_add);
        fab.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chats_actionIcon), PorterDuff.Mode.SRC_IN));
        fab.setBackground(Theme.createSimpleSelectorCircleDrawable(dp(56),
                getThemedColor(Theme.key_chats_actionBackground), getThemedColor(Theme.key_chats_actionPressedBackground)));
        if (Build.VERSION.SDK_INT >= 21) {
            fab.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View v, Outline outline) {
                    outline.setOval(0, 0, v.getWidth(), v.getHeight());
                }
            });
            fab.setElevation(dp(4));
        }
        fab.setContentDescription("Добавить набор");
        fab.setOnClickListener(v -> showAddMenu());
        ((FrameLayout) fragmentView).addView(fab, LayoutHelper.createFrame(56, 56,
                (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.BOTTOM, 16, 0, 16, 16));
        listView.setPadding(0, 0, 0, dp(88));

        touchHelper = new ItemTouchHelper(new ReorderCallback());
        touchHelper.attachToRecyclerView(listView);
        return view;
    }

    @Override
    public void onInsets(int left, int top, int right, int bottom) {
        super.onInsets(left, top, right, bottom);
        listView.setPadding(0, 0, 0, bottom + dp(88));
        if (fab != null && fab.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) fab.getLayoutParams();
            lp.bottomMargin = bottom + dp(16);
            fab.setLayoutParams(lp);
        }
    }

    private void showAddMenu() {
        if (fab == null) {
            return;
        }
        ItemOptions.makeOptions(this, fab)
                .add(R.drawable.msg_download, "Установить из файла", this::openArchivePicker)
                .add(R.drawable.msg_photoeditor, "Создать набор", () -> showPackInfoSheet(null))
                .show();
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == baseDefaultRow || position == baseSolarRow || position == baseRemixRow) {
            int type = position == baseSolarRow ? BaseIconPacks.BASE_SOLAR
                    : position == baseRemixRow ? BaseIconPacks.BASE_REMIX
                    : BaseIconPacks.BASE_DEFAULT;
            if (type == BaseIconPacks.getSelected()) {
                return;
            }
            BaseIconPacks.setSelected(type);
            if (listAdapter != null) {
                listAdapter.notifyItemRangeChanged(baseDefaultRow, 3);
            }
            IconPackManager.getInstance().reload();
            showRestartHint();
        } else if (editingRow != -1 && position == editingRow) {
            app.tsunderegram.icons.picker.IconPickerController.finishEditing();
            updateRowsAndNotify();
        } else {
            IconPack pack = packAt(position);
            if (pack == null) {
                return;
            }
            IconPacksConfig.togglePack(pack.getId());
            syncEnabledFlag();
            IconPackManager.getInstance().reload();
            updateRowsAndNotify();
        }
    }

    @Override
    protected boolean onItemLongClick(View view, int position, float x, float y) {
        IconPack pack = packAt(position);
        if (pack != null) {
            showPackMenu(view, pack);
            return true;
        }
        return position == baseDefaultRow || position == baseSolarRow || position == baseRemixRow;
    }

    private void updateRowsAndNotify() {
        updateRows();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    /** Меню по удержанию, как у TsundereGram: Изменить / Поделиться / Удалить. */
    private void showPackMenu(View view, IconPack pack) {
        if (getParentActivity() == null) {
            return;
        }
        ItemOptions.makeOptions(this, view)
                .add(R.drawable.msg_edit, "Изменить", () -> openEditor(pack))
                .add(R.drawable.msg_share, "Поделиться", () -> sharePack(pack))
                .add(R.drawable.msg_delete, "Удалить", true, () -> showDeleteDialog(pack))
                .show();
    }

    /** Редактор пака: список заменённых иконок; там же переименование и замена на экранах. */
    private void openEditor(IconPack pack) {
        if (!IconPacksConfig.isPackActive(pack.getId())) {
            IconPacksConfig.togglePack(pack.getId());
        }
        syncEnabledFlag();
        IconPacksConfig.setEditingPackId(pack.getId());
        IconPackManager.getInstance().reload();
        if (getParentActivity() instanceof org.telegram.ui.LaunchActivity) {
            app.tsunderegram.icons.picker.IconPickerController.setActive(
                    (org.telegram.ui.LaunchActivity) getParentActivity(), true);
        }
        presentFragment(new IconPacksEditorActivity(pack.getId()));
    }

    private void showPackInfoSheet(IconPack pack) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        NewIconPackBottomSheet sheet = new NewIconPackBottomSheet(this, context, pack);
        sheet.setOnDone(() -> {
            previewCache.clear();
            syncEnabledFlag();
            IconPackManager.getInstance().reload();
            updateRowsAndNotify();
        });
        showDialog(sheet);
    }

    @Override
    public void onResume() {
        super.onResume();
        previewCache.clear();
        updateRowsAndNotify();
    }

    // ---- перетаскивание включённых наборов ----

    private class ReorderCallback extends ItemTouchHelper.Callback {

        @Override
        public boolean isLongPressDragEnabled() {
            return false;
        }

        @Override
        public boolean isItemViewSwipeEnabled() {
            return false;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            if (activeList.size() > 1 && isActiveRow(viewHolder.getAdapterPosition())) {
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0);
            }
            return makeMovementFlags(0, 0);
        }

        @Override
        public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
            int from = source.getAdapterPosition();
            int to = target.getAdapterPosition();
            if (!isActiveRow(from) || !isActiveRow(to)) {
                return false;
            }
            IconPack moved = activeList.remove(from - activeStartRow);
            activeList.add(to - activeStartRow, moved);
            if (listAdapter != null) {
                listAdapter.notifyItemMoved(from, to);
            }
            orderChanged = true;
            return true;
        }

        @Override
        public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            if (viewHolder != null && actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
                listView.cancelClickRunnables(false);
                viewHolder.itemView.setPressed(true);
            }
            super.onSelectedChanged(viewHolder, actionState);
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            viewHolder.itemView.setPressed(false);
            if (orderChanged) {
                orderChanged = false;
                List<String> ids = new ArrayList<>();
                for (IconPack pack : activeList) {
                    ids.add(pack.getId());
                }
                IconPacksConfig.setActivePackIds(ids);
                IconPackManager.getInstance().reload();
                AndroidUtilities.runOnUIThread(IconPacksActivity.this::updateRowsAndNotify, 200);
            }
        }
    }

    // ---- превью 2×2 ----

    private Drawable[] basePreview(int type) {
        String key = "base." + type;
        Drawable[] cached = previewCache.get(key);
        if (cached != null) {
            return cached;
        }
        Drawable[] result = new Drawable[4];
        try {
            Resources res = ApplicationLoader.applicationContext.getResources();
            android.util.SparseIntArray map = type == BaseIconPacks.BASE_DEFAULT ? null : BaseIconPacks.getMap(type);
            for (int i = 0; i < 4; i++) {
                int id = IconPackManager.getInstance().getResourceId(PREVIEW_ICONS[i]);
                if (id == 0) {
                    continue;
                }
                int mapped = map != null ? map.get(id, id) : id;
                Drawable d;
                if (res instanceof IconsResources) {
                    d = ((IconsResources) res).getOriginalDrawableForDensity(mapped, 0, null);
                } else {
                    d = ApplicationLoader.applicationContext.getResources().getDrawable(mapped);
                }
                result[i] = d == null ? null : d.mutate();
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        previewCache.put(key, result);
        return result;
    }

    private Drawable[] packPreview(IconPack pack) {
        Drawable[] cached = previewCache.get(pack.getId());
        if (cached != null) {
            return cached;
        }
        Drawable[] result = new Drawable[4];
        try {
            Map<String, String> icons = pack.getIcons();
            List<String> names = new ArrayList<>();
            for (String name : PREVIEW_ICONS) {
                if (icons.containsKey(name)) {
                    names.add(name);
                }
            }
            if (names.size() < 4) {
                for (String name : new TreeMap<>(icons).keySet()) {
                    if (names.size() >= 8) {
                        break;
                    }
                    if (!names.contains(name)) {
                        names.add(name);
                    }
                }
            }
            int filled = 0;
            int size = dp(20);
            for (int i = 0; i < names.size() && filled < 4; i++) {
                File file = IconPackStorage.resolveIconFile(pack, icons.get(names.get(i)));
                Bitmap bitmap = file == null ? null : decodePreview(file, size);
                if (bitmap != null) {
                    result[filled++] = new BitmapDrawable(ApplicationLoader.applicationContext.getResources(), bitmap);
                }
            }
        } catch (Throwable t) {
            FileLog.e(t);
        }
        previewCache.put(pack.getId(), result);
        return result;
    }

    private static Bitmap decodePreview(File file, int size) {
        try {
            if (IconPackManager.isSvg(file.getName())) {
                try (InputStream in = new FileInputStream(file)) {
                    return IconPackManager.renderSvg(in, size, size);
                }
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            int sample = 1;
            while (opts.outWidth / (sample * 2) >= size && opts.outHeight / (sample * 2) >= size) {
                sample *= 2;
            }
            opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Строка набора: превью 2×2, название и автор, справа — радиокнопка или ручка перетаскивания. */
    private class PackCell extends FrameLayout {

        private final Drawable[] icons = new Drawable[4];
        private final TextView titleView;
        private final TextView subtitleView;
        private final RadioButton radioButton;
        private final ImageView handleView;
        private boolean needDivider;

        @SuppressLint("ClickableViewAccessibility")
        PackCell(Context context) {
            super(context);
            setWillNotDraw(false);
            boolean rtl = LocaleController.isRTL;

            titleView = new TextView(context);
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setSingleLine(true);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            titleView.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, rtl ? 60 : 76, 11, rtl ? 76 : 60, 0));

            subtitleView = new TextView(context);
            subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            subtitleView.setSingleLine(true);
            subtitleView.setEllipsize(TextUtils.TruncateAt.END);
            subtitleView.setGravity(rtl ? Gravity.RIGHT : Gravity.LEFT);
            addView(subtitleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    (rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, rtl ? 60 : 76, 35, rtl ? 76 : 60, 0));

            radioButton = new RadioButton(context);
            radioButton.setSize(dp(20));
            addView(radioButton, LayoutHelper.createFrame(22, 22,
                    (rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 22, 0, 22, 0));

            handleView = new ImageView(context);
            handleView.setScaleType(ImageView.ScaleType.CENTER);
            handleView.setImageResource(R.drawable.list_reorder);
            addView(handleView, LayoutHelper.createFrame(48, 48,
                    (rtl ? Gravity.LEFT : Gravity.RIGHT) | Gravity.CENTER_VERTICAL, 8, 0, 8, 0));
            handleView.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN && touchHelper != null) {
                    RecyclerView.ViewHolder holder = listView.findContainingViewHolder(PackCell.this);
                    if (holder != null) {
                        touchHelper.startDrag(holder);
                    }
                }
                return false;
            });
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(dp(64) + (needDivider ? 1 : 0), MeasureSpec.EXACTLY));
        }

        void set(Drawable[] preview, CharSequence title, CharSequence subtitle, int mode, boolean checked, boolean divider) {
            int iconColor = getThemedColor(Theme.key_windowBackgroundWhiteGrayIcon);
            for (int i = 0; i < 4; i++) {
                Drawable d = preview != null ? preview[i] : null;
                if (d != null) {
                    d.setColorFilter(new PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN));
                }
                icons[i] = d;
            }
            titleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteBlackText));
            subtitleView.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText2));
            titleView.setText(title);
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(TextUtils.isEmpty(subtitle) ? GONE : VISIBLE);
            if (TextUtils.isEmpty(subtitle)) {
                ((FrameLayout.LayoutParams) titleView.getLayoutParams()).topMargin = dp(21);
            } else {
                ((FrameLayout.LayoutParams) titleView.getLayoutParams()).topMargin = dp(11);
            }
            // mode: 0 — радиокнопка, 1 — ручка, 2 — ничего
            radioButton.setVisibility(mode == 0 ? VISIBLE : GONE);
            handleView.setVisibility(mode == 1 ? VISIBLE : GONE);
            if (mode == 0) {
                radioButton.setColor(getThemedColor(Theme.key_radioBackground), getThemedColor(Theme.key_radioBackgroundChecked));
                radioButton.setChecked(checked, false);
            }
            handleView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_stickers_menu), PorterDuff.Mode.SRC_IN));
            needDivider = divider;
            requestLayout();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            boolean rtl = LocaleController.isRTL;
            int s = dp(18);
            int gap = dp(4);
            int total = s * 2 + gap;
            int left = rtl ? getMeasuredWidth() - dp(18) - total : dp(18);
            int top = (dp(64) - total) / 2;
            for (int i = 0; i < 4; i++) {
                Drawable d = icons[i];
                if (d == null) {
                    continue;
                }
                int x = left + (i % 2) * (s + gap);
                int y = top + (i / 2) * (s + gap);
                d.setBounds(x, y, x + s, y + s);
                d.draw(canvas);
            }
            if (needDivider) {
                canvas.drawLine(rtl ? 0 : dp(76), getMeasuredHeight() - 1,
                        getMeasuredWidth() - (rtl ? dp(76) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
            }
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            return type == TYPE_PACK || type == TYPE_BASE || super.isEnabled(holder);
        }

        @Override
        protected boolean isSectionContent(int viewType) {
            return viewType == TYPE_PACK || viewType == TYPE_BASE || super.isSectionContent(viewType);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_PACK || viewType == TYPE_BASE) {
                PackCell cell = new PackCell(mContext);
                cell.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundWhite));
                cell.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }
            return super.onCreateViewHolder(parent, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, boolean partial) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == basePacksHeaderRow) {
                        cell.setText("Базовые наборы");
                    } else if (position == activeHeaderRow) {
                        cell.setText("Включённые наборы");
                    } else if (position == allHeaderRow) {
                        cell.setText("Все наборы");
                    }
                    break;
                }
                case TYPE_BASE: {
                    int type = position == baseSolarRow ? BaseIconPacks.BASE_SOLAR
                            : position == baseRemixRow ? BaseIconPacks.BASE_REMIX
                            : BaseIconPacks.BASE_DEFAULT;
                    ((PackCell) holder.itemView).set(basePreview(type), BaseIconPacks.getName(type),
                            BaseIconPacks.getAuthor(type), 0, BaseIconPacks.getSelected() == type, position != baseRemixRow);
                    break;
                }
                case TYPE_PACK: {
                    IconPack pack = packAt(position);
                    if (pack == null) {
                        break;
                    }
                    boolean active = isActiveRow(position);
                    boolean last = active ? position == activeEndRow - 1 : position == allEndRow - 1;
                    ((PackCell) holder.itemView).set(packPreview(pack), pack.getName(), buildPackSubtitle(pack),
                            active && activeList.size() > 1 ? 1 : 2, false, !last);
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (editingRow != -1 && position == editingRow) {
                        cell.setTextAndIcon(getString(R.string.IconPickerFinish), R.drawable.msg_check, false);
                        cell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == baseInfoRow) {
                        cell.setText("Базовый набор иконок используется для значков, которые не заменены другими наборами.");
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == packsInfoRow) {
                        if (activeList.isEmpty() && inactiveList.isEmpty()) {
                            cell.setText("Своих наборов пока нет. Нажмите «+», чтобы установить набор из файла или создать новый.");
                        } else {
                            cell.setText("Нажмите, чтобы включить или отключить набор. Удерживайте, чтобы открыть меню. Наборы выше в списке имеют приоритет.");
                        }
                        cell.setBackground(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == basePacksHeaderRow || position == activeHeaderRow || position == allHeaderRow) {
                return TYPE_HEADER;
            } else if (position == baseDefaultRow || position == baseSolarRow || position == baseRemixRow) {
                return TYPE_BASE;
            } else if (position == baseInfoRow || position == packsInfoRow) {
                return TYPE_INFO_PRIVACY;
            } else if (editingRow != -1 && position == editingRow) {
                return TYPE_TEXT;
            } else if (position == activeShadowRow || (editingDividerRow != -1 && position == editingDividerRow)) {
                return TYPE_SHADOW;
            }
            return TYPE_PACK;
        }
    }

    private void sharePack(IconPack pack) {
        AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            File bundled = IconPackStorage.bundlePack(pack.getId());
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    progress.dismiss();
                } catch (Exception ignore) {
                }
                if (bundled == null || getParentActivity() == null) {
                    return;
                }
                try {
                    android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
                    intent.setType("application/octet-stream");
                    intent.putExtra(android.content.Intent.EXTRA_STREAM,
                            androidx.core.content.FileProvider.getUriForFile(getParentActivity(),
                                    org.telegram.messenger.ApplicationLoader.getApplicationId() + ".provider", bundled));
                    intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    getParentActivity().startActivity(android.content.Intent.createChooser(intent, getString(R.string.ShareFile)));
                } catch (Exception e) {
                    org.telegram.messenger.FileLog.e(e);
                }
            });
        });
    }

    private void showRestartHint() {
        if (getParentActivity() == null) {
            return;
        }
        BulletinFactory.of(this)
                .createSimpleBulletin(R.raw.info, getString(R.string.IconPackRestartHint))
                .show();
    }

    private void showDeleteDialog(IconPack pack) {
        Context context = getParentActivity();
        if (context == null) {
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.IconPackDelete));
        builder.setMessage(LocaleController.formatString(R.string.IconPackDeleteInfo, pack.getName()));
        builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
            IconPackManager.getInstance().deletePack(pack.getId());
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button != null) {
            ((android.widget.TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private void openArchivePicker() {
        Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28
                    && activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 4);
                return;
            }
        } catch (Throwable ignore) {
        }

        DocumentSelectActivity fragment = new DocumentSelectActivity(false);
        fragment.setMaxSelectedFiles(1);
        fragment.setAllowPhoto(false);
        fragment.setDelegate(new DocumentSelectActivity.DocumentSelectActivityDelegate() {
            @Override
            public void didSelectFiles(DocumentSelectActivity activity, ArrayList<String> files, String caption, boolean notify, int scheduleDate) {
                activity.finishFragment();
                if (files.isEmpty()) {
                    return;
                }
                installArchive(new File(files.get(0)));
            }

            @Override
            public void didSelectPhotos(ArrayList<SendMessagesHelper.SendingMediaInfo> photos, boolean notify, int scheduleDate) {
            }

            @Override
            public void startDocumentSelectActivity() {
            }
        });
        presentFragment(fragment);
    }

    private void installArchive(File file) {
        if (getParentActivity() == null) {
            return;
        }
        AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        IconPackManager.getInstance().installPack(file, error -> {
            try {
                progress.dismiss();
            } catch (Exception ignore) {
            }
            if (error != null) {
                if (getParentActivity() != null) {
                    BulletinFactory.of(IconPacksActivity.this)
                            .createErrorBulletin(error.getLocalizedMessage())
                            .show();
                }
                return;
            }
            updateRows();
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
            if (getParentActivity() != null) {
                BulletinFactory.of(IconPacksActivity.this)
                        .createSimpleBulletin(R.raw.done, getString(R.string.IconPackInstalledToast))
                        .show();
            }
        });
    }

    private String buildPackSubtitle(IconPack pack) {
        String count = LocaleController.formatPluralString("IconPackIconCount", pack.getIconCount());
        if (TextUtils.isEmpty(pack.getAuthor())) {
            return count;
        }
        return pack.getAuthor() + ", " + count;
    }
}
