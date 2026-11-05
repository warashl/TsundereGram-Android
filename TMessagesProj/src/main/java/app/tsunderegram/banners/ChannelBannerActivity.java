package app.tsunderegram.banners;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

/** Баннер для канала (ТГК): виден в профиле канала у всех пользователей TsundereGram. */
public class ChannelBannerActivity extends BaseFragment {

    private static final int ID_UPLOAD = 1;
    private static final int ID_DELETE = 2;
    private static final int ID_HIDE_AVATAR = 3;
    private static final int REQ_PICK = 4711;

    private final long chatId;
    private final ProfileBannerController controller = ProfileBannerController.getInstance();
    private UniversalRecyclerView listView;
    private boolean hideAvatar;

    public ChannelBannerActivity(long chatId) {
        this.chatId = chatId;
    }

    private long dialogId() {
        return -chatId;
    }

    @Override
    public boolean onFragmentCreate() {
        final ProfileBannerController.BannerMeta meta = controller.getBannerMeta(dialogId());
        hideAvatar = meta != null && meta.hideAvatar;
        return super.onFragmentCreate();
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Баннер канала");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        FrameLayout contentView = new FrameLayout(context);
        listView = new UniversalRecyclerView(this, this::fillItems, this::onItemClick, null);
        listView.setSections();
        listView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray, resourceProvider));
        contentView.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        actionBar.setAdaptiveBackground(listView);
        return fragmentView = contentView;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        final TLRPC.Chat chat = getMessagesController().getChat(chatId);
        items.add(UItem.asHeader(chat != null ? chat.title : "Канал"));
        final boolean has = controller.hasChannelBanner(dialogId());
        items.add(UItem.asButton(ID_UPLOAD, R.drawable.msg_addphoto, has ? "Заменить баннер" : "Установить баннер").accent());
        if (has) {
            items.add(UItem.asButton(ID_DELETE, R.drawable.msg_delete, "Удалить баннер").red());
        }
        items.add(UItem.asCheck(ID_HIDE_AVATAR, "Скрывать аватарку канала").setChecked(hideAvatar));
        items.add(UItem.asShadow("Фото, GIF или видео (до 15 МБ). Баннер увидят все, кто открывает профиль канала в TsundereGram. Менять его могут создатель и админы с правом изменения информации."));
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_UPLOAD) {
            openPicker();
        } else if (item.id == ID_DELETE) {
            final AlertDialog progress = showProgress("Удаляю баннер…");
            controller.deleteChannelBanner(dialogId(), () -> {
                dismiss(progress);
                BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "Баннер канала удалён").show();
                listView.adapter.update(true);
            }, error -> {
                dismiss(progress);
                BulletinFactory.of(this).createErrorBulletin(error).show();
            });
        } else if (item.id == ID_HIDE_AVATAR) {
            hideAvatar = !hideAvatar;
            listView.adapter.update(true);
            if (controller.hasChannelBanner(dialogId())) {
                controller.setChannelBannerHideAvatar(dialogId(), hideAvatar, null, error ->
                        BulletinFactory.of(this).createErrorBulletin(error).show());
            }
        }
    }

    private AlertDialog showProgress(String text) {
        final AlertDialog progress = new AlertDialog(getParentActivity(), AlertDialog.ALERT_TYPE_SPINNER);
        progress.setMessage(text);
        progress.setCancelDialog(false);
        progress.setCanceledOnTouchOutside(false);
        progress.show();
        return progress;
    }

    private static void dismiss(AlertDialog dialog) {
        try {
            dialog.dismiss();
        } catch (Exception ignore) {
        }
    }

    private void openPicker() {
        final Activity activity = getParentActivity();
        if (activity == null) {
            return;
        }
        try {
            final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
            activity.startActivityForResult(intent, REQ_PICK);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ_PICK || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        String mime = null;
        try {
            mime = ApplicationLoader.applicationContext.getContentResolver().getType(uri);
        } catch (Exception ignore) {
        }
        final String name = MediaController.getFileName(uri);
        String ext = ".jpg";
        final String lowerMime = mime != null ? mime.toLowerCase(Locale.ROOT) : "";
        final String lowerName = name != null ? name.toLowerCase(Locale.ROOT) : "";
        if (lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mov") || lowerName.endsWith(".webm") || lowerName.endsWith(".mkv")) {
            ext = ".mp4";
        } else if (lowerMime.contains("gif") || lowerName.endsWith(".gif")) {
            ext = ".gif";
        } else if (lowerMime.contains("png") || lowerName.endsWith(".png")) {
            ext = ".png";
        }
        final File tempFile = new File(AndroidUtilities.getCacheDir(), UUID.randomUUID() + ext);
        try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tempFile)) {
            if (in == null) {
                return;
            }
            final byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }
        final long maxBytes = (ext.equals(".mp4") || ext.equals(".gif")) ? 15L * 1024 * 1024 : 8L * 1024 * 1024;
        if (tempFile.length() > maxBytes) {
            tempFile.delete();
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.ProfileBannerFileTooBig)).show();
            return;
        }
        final AlertDialog progress = showProgress(getString(R.string.ProfileBannerSending));
        controller.submitChannelBanner(tempFile, dialogId(), hideAvatar, () -> {
            tempFile.delete();
            dismiss(progress);
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, "Баннер канала установлен").show();
            listView.adapter.update(true);
        }, error -> {
            tempFile.delete();
            dismiss(progress);
            BulletinFactory.of(this).createErrorBulletin(error).show();
        });
    }

    private static String getString(int res) {
        return org.telegram.messenger.LocaleController.getString(res);
    }
}
