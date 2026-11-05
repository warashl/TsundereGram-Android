package org.telegram.tsunderestyle.settings;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.tsunderestyle.theme.TsundereStyleFonts;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.ui.ThemeActivity;

import java.util.ArrayList;

/**
 * Typography: separate fonts for headings and names, messages and interface text, with a preview.
 * Telegram caches its text paints, so changes apply after restarting the app.
 */
public class TsundereStyleTypographyActivity extends BaseFragment {

    private static final int ID_HEADINGS = 1;
    private static final int ID_MESSAGES = 2;
    private static final int ID_INTERFACE = 3;
    private static final int ID_TEXT_SIZE = 4;
    private static final int ID_RESTART = 5;
    private static final int ID_IMPORT = 6;
    private static final int ID_DELETE_CUSTOM = 7;
    private static final int REQUEST_IMPORT_FONT = 47102;

    private static final org.telegram.messenger.DispatchQueue fontQueue = new org.telegram.messenger.DispatchQueue("tsunderestyleFonts");

    private UniversalRecyclerView listView;
    private PreviewView preview;
    private boolean changed;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        actionBar.setTitle(getString(R.string.OG_Typography));

        preview = new PreviewView(context);

        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(preview));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.OG_Fonts)));
        final Context ctx = ApplicationLoader.applicationContext;
        items.add(UItem.asButton(ID_HEADINGS, R.drawable.msg_text_outlined, getString(R.string.OG_FontHeadings), TsundereStyleFonts.title(ctx, TsundereStyleFonts.get(TsundereStyleFonts.TARGET_HEADINGS))));
        items.add(UItem.asButton(ID_MESSAGES, R.drawable.msg_msgbubble3, getString(R.string.OG_FontMessages), TsundereStyleFonts.title(ctx, TsundereStyleFonts.get(TsundereStyleFonts.TARGET_MESSAGES))));
        items.add(UItem.asButton(ID_INTERFACE, R.drawable.msg_list, getString(R.string.OG_FontInterface), TsundereStyleFonts.title(ctx, TsundereStyleFonts.get(TsundereStyleFonts.TARGET_INTERFACE))));
        items.add(UItem.asShadow(getString(R.string.OG_FontsInfo)));

        items.add(UItem.asButton(ID_TEXT_SIZE, R.drawable.msg_photo_text_framed, getString(R.string.OG_TextSize)));
        items.add(UItem.asShadow(getString(R.string.OG_TextSizeInfo)));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case ID_HEADINGS:
                pickFont(TsundereStyleFonts.TARGET_HEADINGS, R.string.OG_FontHeadings);
                break;
            case ID_MESSAGES:
                pickFont(TsundereStyleFonts.TARGET_MESSAGES, R.string.OG_FontMessages);
                break;
            case ID_INTERFACE:
                pickFont(TsundereStyleFonts.TARGET_INTERFACE, R.string.OG_FontInterface);
                break;
            case ID_TEXT_SIZE:
                presentFragment(new ThemeActivity(ThemeActivity.THEME_TYPE_BASIC));
                break;
            case ID_RESTART:
                restartApp();
                break;
            case ID_IMPORT:
                importFont();
                break;
            case ID_DELETE_CUSTOM:
                deleteFont();
                break;
        }
    }

    private void pickFont(String target, int titleRes) {
        // Только встроенные шрифты: загрузка своих файлов убрана
        final java.util.List<String> fonts = new java.util.ArrayList<>(TsundereStyleFonts.allFonts());
        fonts.removeAll(TsundereStyleFonts.customFonts());
        final CharSequence[] names = new CharSequence[fonts.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = TsundereStyleFonts.title(getParentActivity(), fonts.get(i));
        }
        new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(titleRes))
                .setItems(names, (dialog, which) -> selectFont(target, fonts.get(which)))
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** Выбор шрифта; шрифты Google Fonts сначала скачиваются (один раз). */
    private void selectFont(String target, String font) {
        if (!TsundereStyleFonts.needsDownload(font)) {
            TsundereStyleFonts.set(target, font);
            changed = true;
            applyFontsNow();
            return;
        }
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        final AlertDialog progress = new AlertDialog(activity, AlertDialog.ALERT_TYPE_SPINNER);
        progress.setCanCancel(false);
        progress.show();
        fontQueue.postRunnable(() -> {
            final boolean ok = TsundereStyleFonts.download(font);
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    progress.dismiss();
                } catch (Exception ignore) {
                }
                if (ok) {
                    TsundereStyleFonts.set(target, font);
                    changed = true;
                    applyFontsNow();
                } else {
                    org.telegram.ui.Components.BulletinFactory.of(this).createErrorBulletin(getString(R.string.OG_FontDownloadFailed)).show();
                }
            });
        });
    }

    private void importFont() {
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"font/ttf", "font/otf", "font/sfnt", "application/x-font-ttf", "application/x-font-otf", "application/font-sfnt", "application/octet-stream"});
        try {
            activity.startActivityForResult(intent, REQUEST_IMPORT_FONT);
        } catch (Exception e) {
            org.telegram.messenger.FileLog.e(e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_IMPORT_FONT || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final android.net.Uri uri = data.getData();
        final Context context = getParentActivity();
        if (context == null) {
            return;
        }
        fontQueue.postRunnable(() -> {
            final String font = TsundereStyleFonts.importFont(context, uri);
            AndroidUtilities.runOnUIThread(() -> {
                if (font == null) {
                    org.telegram.ui.Components.BulletinFactory.of(this).createErrorBulletin(getString(R.string.OG_ImportFontFailed)).show();
                    return;
                }
                org.telegram.ui.Components.BulletinFactory.of(this).createSimpleBulletin(R.raw.contact_check,
                        org.telegram.messenger.LocaleController.formatString(R.string.OG_ImportFontDone, TsundereStyleFonts.title(context, font))).show();
                listView.adapter.update(true);
            });
        });
    }

    private void deleteFont() {
        final java.util.List<String> fonts = TsundereStyleFonts.customFonts();
        final CharSequence[] names = new CharSequence[fonts.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = TsundereStyleFonts.title(getParentActivity(), fonts.get(i));
        }
        new AlertDialog.Builder(getParentActivity(), resourceProvider)
                .setTitle(getString(R.string.OG_DeleteFont))
                .setItems(names, (dialog, which) -> {
                    TsundereStyleFonts.deleteCustom(fonts.get(which));
                    changed = true;
                    preview.invalidate();
                    listView.adapter.update(true);
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .show();
    }

    /** Применяет шрифты сразу, без перезапуска: обновляет кэш шрифтов и пересобирает экраны. */
    private void applyFontsNow() {
        TsundereStyleFonts.applyNow();
        try {
            if (getParentLayout() != null) {
                for (BaseFragment fragment : new ArrayList<>(getParentLayout().getFragmentStack())) {
                    if (fragment instanceof org.telegram.ui.ChatActivity) {
                        for (org.telegram.messenger.MessageObject m : ((org.telegram.ui.ChatActivity) fragment).messages) {
                            if (m != null) {
                                m.resetLayout();
                            }
                        }
                    }
                }
                getParentLayout().rebuildAllFragmentViews(false, false);
            }
        } catch (Throwable e) {
            org.telegram.messenger.FileLog.e(e);
        }
        if (preview != null) {
            preview.refresh();
        }
        if (listView != null && listView.adapter != null) {
            listView.adapter.update(true);
        }
    }

    private void restartApp() {
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        final Intent launch = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        if (launch == null || launch.getComponent() == null) {
            return;
        }
        activity.startActivity(Intent.makeRestartActivityTask(launch.getComponent()));
        AndroidUtilities.runOnUIThread(() -> Runtime.getRuntime().exit(0), 200);
    }

    /** Предпросмотр как в Text Morph: обои чата и настоящая исходящая ячейка сообщения, сверху заголовок. */
    private class PreviewView extends android.widget.LinearLayout {
        private final android.widget.TextView titleView;
        private final ChatPreview chat;

        PreviewView(Context context) {
            super(context);
            setOrientation(VERTICAL);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
            titleView = new android.widget.TextView(context);
            titleView.setText(getString(R.string.OG_FontPreviewHeading));
            titleView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 20);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
            titleView.setGravity(Gravity.CENTER_VERTICAL);
            titleView.setPadding(dp(21), dp(12), dp(21), dp(10));
            addView(titleView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            chat = new ChatPreview(context);
            addView(chat, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            refresh();
        }

        void refresh() {
            final Typeface h = TsundereStyleFonts.preview(TsundereStyleFonts.get(TsundereStyleFonts.TARGET_HEADINGS), true);
            titleView.setTypeface(h != null ? h : AndroidUtilities.bold());
            chat.setMessage(getString(R.string.OG_FontPreviewMessage));
        }

        @Override
        public void invalidate() {
            super.invalidate();
            if (chat != null) {
                chat.invalidate();
            }
        }
    }

    private class ChatPreview extends FrameLayout {
        private final org.telegram.ui.Cells.ChatMessageCell cell;

        ChatPreview(Context context) {
            super(context);
            setWillNotDraw(false);
            setPadding(0, dp(10), 0, dp(10));
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

        void setMessage(String text) {
            final org.telegram.tgnet.TLRPC.TL_message message = new org.telegram.tgnet.TLRPC.TL_message();
            message.message = text;
            message.date = (int) (System.currentTimeMillis() / 1000);
            message.dialog_id = 1;
            message.flags = 259;
            message.from_id = new org.telegram.tgnet.TLRPC.TL_peerUser();
            message.from_id.user_id = org.telegram.messenger.UserConfig.getInstance(currentAccount).getClientUserId();
            message.id = 1;
            message.media = new org.telegram.tgnet.TLRPC.TL_messageMediaEmpty();
            message.out = true;
            message.peer_id = new org.telegram.tgnet.TLRPC.TL_peerUser();
            message.peer_id.user_id = 0;
            final org.telegram.messenger.MessageObject messageObject = new org.telegram.messenger.MessageObject(currentAccount, message, true, false);
            messageObject.resetLayout();
            messageObject.eventId = 1;
            cell.setMessageObject(messageObject, null, false, false, false);
            cell.requestLayout();
            requestLayout();
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final android.graphics.drawable.Drawable drawable = Theme.getCachedWallpaperNonBlocking();
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
                final float scale = Math.max((float) w / drawable.getIntrinsicWidth(), (float) h / drawable.getIntrinsicHeight());
                final int dw = (int) Math.ceil(drawable.getIntrinsicWidth() * scale);
                final int dh = (int) Math.ceil(drawable.getIntrinsicHeight() * scale);
                final int x = (w - dw) / 2, y = (h - dh) / 2;
                drawable.setBounds(x, y, x + dw, y + dh);
                drawable.draw(canvas);
            } else if (drawable instanceof android.graphics.drawable.BitmapDrawable) {
                final float scale = 2.0f / AndroidUtilities.density;
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
}
