package app.tsunderegram.banners;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.UUID;

import tw.nekomimi.nekogram.settings.BaseNekoSettingsActivity;
import tw.nekomimi.nekogram.ui.cells.HeaderCell;

public class ProfileBannerActivity extends BaseNekoSettingsActivity {

    private static final int TYPE_HERO = 100;
    private static final int REQ_PICK_LOCAL = 1001;
    private static final int REQ_PICK_GLOBAL = 1002;

    private int heroRow;

    private int globalHeaderRow;
    private int statusRow;
    private int uploadGlobalRow;
    private int deleteGlobalRow;
    private int checkStatusRow;
    private int hideAvatarRow;
    private int globalShadowRow;

    private int localHeaderRow;
    private int pickLocalRow;
    private int deleteLocalRow;
    private int localShadowRow;

    private int extraHeaderRow;
    private int useAvatarRow;
    private int profileColorsRow = -1;
    private int extraShadowRow;

    private View heroView;
    private final ProfileBannerController controller = ProfileBannerController.getInstance();

    @Override
    public boolean onFragmentCreate() {
        controller.fetchStatus(controller.getMyId(), status -> {
            if (fragmentView != null) {
                updateRows();
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
            }
        });
        return super.onFragmentCreate();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listView != null && listView.getAdapter() != null) {
            updateRows();
            listView.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    protected void updateRows() {
        super.updateRows();

        heroRow = addRow();

        globalHeaderRow = addRow("profileBannerGlobalHeader");
        statusRow = addRow();
        uploadGlobalRow = addRow();
        if (controller.hasGlobalBanner()) {
            deleteGlobalRow = addRow();
        } else {
            deleteGlobalRow = -1;
        }
        checkStatusRow = addRow();
        hideAvatarRow = addRow();
        globalShadowRow = addRow();

        localHeaderRow = addRow("profileBannerLocalHeader");
        pickLocalRow = addRow();
        if (controller.hasLocalBanner()) {
            deleteLocalRow = addRow();
        } else {
            deleteLocalRow = -1;
        }
        localShadowRow = addRow();

        extraHeaderRow = addRow("profileBannerExtraHeader");
        useAvatarRow = addRow();
        profileColorsRow = addRow();
        extraShadowRow = addRow();
    }

    @Override
    protected String getActionBarTitle() {
        return getString(R.string.ProfileBanner);
    }

    @Override
    public int getSearchGuid() {
        return 29000;
    }

    @Override
    public String getSearchPrefix() {
        return "ProfileBanner";
    }

    @Override
    protected String getKey() {
        return "profile_banner_settings";
    }

    @Override
    protected BaseListAdapter createAdapter(Context context) {
        return new ListAdapter(context);
    }

    @Override
    protected void onItemClick(View view, int position, float x, float y) {
        if (position == uploadGlobalRow) {
            String status = controller.getServerStatus();
            if ("blocked".equals(status)) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.ProfileBannerBlockedError)).show();
                return;
            }
            if ("pending".equals(status)) {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.ProfileBannerPendingError)).show();
                return;
            }
            openFilePicker(REQ_PICK_GLOBAL);
        } else if (position == deleteGlobalRow) {
            AlertDialog progress = new AlertDialog(getParentActivity(), 3);
            progress.setMessage(getString(R.string.ProfileBannerDeleting));
            progress.setCancelDialog(false);
            progress.setCanceledOnTouchOutside(false);
            progress.show();

            controller.deleteGlobalBanner(currentAccount, () -> {
                try {
                    progress.dismiss();
                } catch (Exception ignored) {}
                BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.ProfileBannerGlobalDeleted)).show();
                updateRows();
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
                if (heroView != null) heroView.invalidate();
            }, error -> {
                try {
                    progress.dismiss();
                } catch (Exception ignored) {}
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, error).show();
            });
        } else if (position == checkStatusRow || position == statusRow) {
            BulletinFactory.of(this).createSimpleBulletin(R.raw.info, getString(R.string.ProfileBannerRefreshing)).show();
            controller.fetchStatus(controller.getMyId(), s -> {
                BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.ProfileBannerStatusUpdated)).show();
                updateRows();
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
                if (heroView != null) heroView.invalidate();
            });
        } else if (position == hideAvatarRow) {
            boolean next = !controller.isHideAvatarSelf();
            controller.updateHideAvatar(next, () -> {
                updateRows();
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
                if (heroView != null) heroView.invalidate();
            }, null);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
        } else if (position == pickLocalRow) {
            openFilePicker(REQ_PICK_LOCAL);
        } else if (position == deleteLocalRow) {
            controller.deleteLocalBanner();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.ProfileBannerLocalDeleted)).show();
            updateRows();
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
            if (heroView != null) heroView.invalidate();
        } else if (position == useAvatarRow) {
            boolean next = !controller.isUseAvatarAsBanner();
            controller.setUseAvatarAsBanner(next);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(next);
            }
            if (heroView != null) heroView.invalidate();
        } else if (position == profileColorsRow) {
            presentFragment(new app.tsunderegram.profilecolors.ProfileColorActivity());
        }
    }

    private void openFilePicker(int requestCode) {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
            startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            FileLog.e(e);
            try {
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
                startActivityForResult(intent, requestCode);
            } catch (Exception ex) {
                FileLog.e(ex);
            }
        }
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            super.onActivityResultFragment(requestCode, resultCode, data);
            return;
        }

        Uri uri = data.getData();
        String mime = null;
        try {
            mime = ApplicationLoader.applicationContext.getContentResolver().getType(uri);
        } catch (Exception ignored) {}

        String name = MediaController.getFileName(uri);
        String ext = ".jpg";
        if (mime != null) {
            String lowerMime = mime.toLowerCase(Locale.ROOT);
            if (lowerMime.startsWith("video/") || lowerMime.contains("mp4")) {
                ext = ".mp4";
            } else if (lowerMime.contains("gif")) {
                ext = ".gif";
            } else if (lowerMime.contains("png")) {
                ext = ".png";
            } else if (lowerMime.contains("webp")) {
                ext = ".webp";
            }
        }
        if (ext.equals(".jpg") && name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.endsWith(".mp4") || lower.endsWith(".mov") || lower.endsWith(".webm") || lower.endsWith(".mkv")) {
                ext = ".mp4";
            } else if (lower.endsWith(".gif")) {
                ext = ".gif";
            } else if (lower.endsWith(".png")) {
                ext = ".png";
            } else if (lower.endsWith(".webp")) {
                ext = ".webp";
            }
        }

        File tempFile = new File(AndroidUtilities.getCacheDir(), UUID.randomUUID().toString() + ext);
        try (InputStream in = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(tempFile)) {
            if (in == null) return;
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
        } catch (Exception e) {
            FileLog.e(e);
            return;
        }

        if (requestCode == REQ_PICK_LOCAL) {
            controller.setLocalBanner(tempFile, ext);
            tempFile.delete();
            BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.ProfileBannerLocalSet)).show();
            updateRows();
            if (listView != null && listView.getAdapter() != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
            if (heroView != null) heroView.invalidate();
        } else if (requestCode == REQ_PICK_GLOBAL) {
            long maxBytes = (ext.equals(".mp4") || ext.equals(".gif")) ? 15 * 1024 * 1024 : 8 * 1024 * 1024;
            if (tempFile.length() > maxBytes) {
                tempFile.delete();
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, getString(R.string.ProfileBannerFileTooBig)).show();
                return;
            }

            AlertDialog progress = new AlertDialog(getParentActivity(), 3);
            progress.setMessage(getString(R.string.ProfileBannerSending));
            progress.setCancelDialog(false);
            progress.setCanceledOnTouchOutside(false);
            progress.show();

            controller.submitBanner(tempFile, currentAccount, () -> {
                tempFile.delete();
                try {
                    progress.dismiss();
                } catch (Exception ignored) {}
                BulletinFactory.of(this).createSimpleBulletin(R.raw.done, getString(R.string.ProfileBannerSentOk)).show();
                updateRows();
                if (listView != null && listView.getAdapter() != null) {
                    listView.getAdapter().notifyDataSetChanged();
                }
                if (heroView != null) heroView.invalidate();
            }, error -> {
                tempFile.delete();
                try {
                    progress.dismiss();
                } catch (Exception ignored) {}
                BulletinFactory.of(this).createSimpleBulletin(R.raw.error, error).show();
            });
        }
    }

    private View createHero(Context context) {
        FrameLayout root = new FrameLayout(context);
        root.setPadding(dp(16), dp(12), dp(16), dp(8));

        HeroCardView card = new HeroCardView(context);
        heroView = card;
        root.addView(card, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 160));
        return root;
    }

    private class HeroCardView extends FrameLayout {

        private final Paint roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private final Matrix matrix = new Matrix();
        private final Paint dimPaint = new Paint();
        private final Paint gradPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private BackupImageView avatarView;
        private TextView nameTextView;
        private TextView badgeTextView;

        public HeroCardView(Context context) {
            super(context);
            setWillNotDraw(false);

            TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();

            avatarView = new BackupImageView(context);
            avatarView.setRoundRadius(dp(26));
            AvatarDrawable avatarDrawable = new AvatarDrawable();
            avatarDrawable.setInfo(currentAccount, user);
            avatarView.setForUserOrChat(user, avatarDrawable);

            addView(avatarView, LayoutHelper.createFrame(52, 52, Gravity.BOTTOM | Gravity.START, 16, 0, 0, 16));

            nameTextView = new TextView(context);
            nameTextView.setTextColor(Color.WHITE);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
            nameTextView.setTypeface(AndroidUtilities.bold());
            nameTextView.setSingleLine(true);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            nameTextView.setShadowLayer(dp(3), 0, dp(1), Color.argb(180, 0, 0, 0));
            nameTextView.setText(user != null ? UserObject.getUserName(user) : "TsundereGram");

            addView(nameTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.START, 76, 0, 16, 28));

            badgeTextView = new TextView(context);
            badgeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            badgeTextView.setTypeface(AndroidUtilities.bold());
            badgeTextView.setTextColor(Color.WHITE);
            badgeTextView.setPadding(dp(8), dp(4), dp(8), dp(4));
            badgeTextView.setBackground(Theme.createRoundRectDrawable(dp(8), Color.argb(160, 0, 0, 0)));
            badgeTextView.setText(controller.getBannerStatusDescription());

            addView(badgeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT,
                    Gravity.TOP | Gravity.END, 0, 12, 12, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            bounds.set(0, 0, getWidth(), getHeight());
            float radius = dp(16);

            int save = canvas.save();
            android.graphics.Path clipPath = new android.graphics.Path();
            clipPath.addRoundRect(bounds, radius, radius, android.graphics.Path.Direction.CW);
            canvas.clipPath(clipPath);

            String path = controller.resolveBannerPath(controller.getMyId());
            boolean drawn = false;
            boolean isVideo = path != null && controller.isVideo(path);

            if (path != null) {
                Bitmap bmp = isVideo ? controller.getVideoThumbnail(path) : BitmapFactory.decodeFile(path);
                if (bmp != null) {
                    float scale = Math.max((float) getWidth() / bmp.getWidth(), (float) getHeight() / bmp.getHeight());
                    float dx = (getWidth() - bmp.getWidth() * scale) * 0.5f;
                    float dy = (getHeight() - bmp.getHeight() * scale) * 0.5f;
                    matrix.reset();
                    matrix.postScale(scale, scale);
                    matrix.postTranslate(dx, dy);
                    roundPaint.setAlpha(255);
                    canvas.drawBitmap(bmp, matrix, roundPaint);
                    drawn = true;
                }
            }

            if (!drawn) {
                // Gradient placeholder
                LinearGradient baseGrad = new LinearGradient(0, 0, getWidth(), getHeight(),
                        new int[]{0xFFE040FB, 0xFF7C4DFF, 0xFF00B0FF},
                        null, Shader.TileMode.CLAMP);
                roundPaint.setShader(baseGrad);
                canvas.drawRect(bounds, roundPaint);
                roundPaint.setShader(null);
            }

            // Dark dim
            dimPaint.setColor(Color.argb(80, 0, 0, 0));
            canvas.drawRect(bounds, dimPaint);

            // Bottom gradient
            LinearGradient grad = new LinearGradient(0, getHeight() * 0.4f, 0, getHeight(),
                    Color.TRANSPARENT, Color.argb(160, 0, 0, 0), Shader.TileMode.CLAMP);
            gradPaint.setShader(grad);
            canvas.drawRect(0, getHeight() * 0.4f, getWidth(), getHeight(), gradPaint);

            // Cute "▶ ВИДЕО" badge if banner is a video
            if (isVideo) {
                Paint pillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                pillPaint.setColor(Color.argb(170, 0, 0, 0));
                RectF pillRect = new RectF(dp(12), dp(12), dp(78), dp(32));
                canvas.drawRoundRect(pillRect, dp(6), dp(6), pillPaint);

                Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                textPaint.setColor(Color.WHITE);
                textPaint.setTextSize(dp(10));
                textPaint.setTypeface(AndroidUtilities.bold());
                canvas.drawText("▶ ВИДЕО", dp(18), dp(25), textPaint);
            }

            canvas.restoreToCount(save);

            boolean hideAvatar = controller.isHideAvatarSelf();
            avatarView.setAlpha(hideAvatar ? 0.25f : 1.0f);
            badgeTextView.setText(controller.getBannerStatusDescription());

            super.onDraw(canvas);
        }
    }

    private class ListAdapter extends BaseListAdapter {

        public ListAdapter(Context context) {
            super(context);
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_HERO) {
                View view = createHero(mContext);
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
                    if (position == globalHeaderRow) {
                        cell.setText(getString(R.string.ProfileBannerGlobalHeader));
                    } else if (position == localHeaderRow) {
                        cell.setText(getString(R.string.ProfileBannerLocalHeader));
                    } else if (position == extraHeaderRow) {
                        cell.setText(getString(R.string.ProfileBannerExtra));
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (position == statusRow) {
                        cell.setTextAndValue(getString(R.string.ProfileBannerStatus),
                                controller.getBannerStatusDescription(), false);
                    }
                    break;
                }
                case TYPE_TEXT: {
                    TextCell cell = (TextCell) holder.itemView;
                    if (position == uploadGlobalRow) {
                        String text = controller.hasGlobalBanner()
                                ? getString(R.string.ProfileBannerChangeGlobal)
                                : getString(R.string.ProfileBannerSubmitModeration);
                        cell.setColors(Theme.key_windowBackgroundWhiteBlueIcon, Theme.key_windowBackgroundWhiteBlueText4);
                        cell.setTextAndIcon(text, R.drawable.cloud_sync, true);
                    } else if (position == deleteGlobalRow) {
                        cell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                        cell.setTextAndIcon(getString(R.string.ProfileBannerDeleteGlobal), R.drawable.msg_delete, true);
                    } else if (position == checkStatusRow) {
                        cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString(R.string.ProfileBannerRefreshStatus), R.drawable.msg_retry, true);
                    } else if (position == pickLocalRow) {
                        cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndIcon(getString(R.string.ProfileBannerPickLocal), R.drawable.msg_photos, deleteLocalRow != -1);
                    } else if (position == deleteLocalRow) {
                        cell.setColors(Theme.key_text_RedRegular, Theme.key_text_RedRegular);
                        cell.setTextAndIcon(getString(R.string.ProfileBannerDeleteLocal), R.drawable.msg_delete, false);
                    } else if (position == profileColorsRow) {
                        cell.setColors(Theme.key_windowBackgroundWhiteGrayIcon, Theme.key_windowBackgroundWhiteBlackText);
                        cell.setTextAndValueAndIcon("Цвета профиля", app.tsunderegram.profilecolors.ProfileColorController.getInstance().getColorStatusDescription(), R.drawable.msg_theme, false);
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    if (position == hideAvatarRow) {
                        cell.setTextAndValueAndCheck(getString(R.string.ProfileBannerHideAvatar),
                                getString(R.string.ProfileBannerHideAvatarHint),
                                controller.isHideAvatarSelf(), true, false);
                    } else if (position == useAvatarRow) {
                        cell.setTextAndValueAndCheck(getString(R.string.ProfileBannerAvatarBanner),
                                getString(R.string.ProfileBannerAvatarBannerHint),
                                controller.isUseAvatarAsBanner(), true, false);
                    }
                    break;
                }
                case TYPE_INFO_PRIVACY: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == globalShadowRow) {
                        cell.setText(getString(R.string.ProfileBannerSub));
                        cell.setBackground(Theme.getThemedDrawable(mContext,
                                R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == localShadowRow) {
                        cell.setText(getString(R.string.ProfileBannerLocalHint));
                        cell.setBackground(Theme.getThemedDrawable(mContext,
                                R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == heroRow) {
                return TYPE_HERO;
            } else if (position == globalHeaderRow || position == localHeaderRow || position == extraHeaderRow) {
                return TYPE_HEADER;
            } else if (position == statusRow) {
                return TYPE_SETTINGS;
            } else if (position == uploadGlobalRow || position == deleteGlobalRow || position == checkStatusRow || position == pickLocalRow || position == deleteLocalRow) {
                return TYPE_TEXT;
            } else if (position == hideAvatarRow || position == useAvatarRow) {
                return TYPE_CHECK;
            } else if (position == globalShadowRow || position == localShadowRow) {
                return TYPE_INFO_PRIVACY;
            }
            return TYPE_SHADOW;
        }
    }
}
