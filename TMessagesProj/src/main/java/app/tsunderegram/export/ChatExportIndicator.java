package app.tsunderegram.export;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.LaunchActivity;

/**
 * TsundereGram: где видно свёрнутый экспорт чата.
 *  - уведомление с прогрессом и кнопкой «Остановить» (видно и вне приложения);
 *  - плашка «Экспорт чата · 37%» поверх приложения: нажатие открывает прогресс, её можно перетащить.
 */
final class ChatExportIndicator {

    private static final String CHANNEL_ID = "tsundere_chat_export";
    private static final int NOTIFY_PROGRESS_ID = 0x7E5D01;
    private static final int NOTIFY_DONE_ID = 0x7E5D02;
    private static final String ACTION_STOP = "app.tsunderegram.export.STOP";

    private static BroadcastReceiver stopReceiver;
    private static Runnable stopAction;
    private static Runnable openAction;
    private static long lastNotifyTime;
    private static boolean running;

    private static String title = "";
    private static String status = "";
    private static int done;
    private static int total;

    private static boolean pillWanted;
    private static LinearLayout pill;
    private static TextView pillText;
    private static float pillTx, pillTy;

    private ChatExportIndicator() {
    }

    static void start(String chatTitle, Runnable onStop, Runnable onOpen) {
        running = true;
        title = chatTitle != null ? chatTitle : "Чат";
        status = "Подготовка…";
        done = 0;
        total = 0;
        stopAction = onStop;
        openAction = onOpen;
        lastNotifyTime = 0;
        try {
            NotificationManagerCompat.from(ApplicationLoader.applicationContext).cancel(NOTIFY_DONE_ID);
        } catch (Throwable ignore) {
        }
        if (stopReceiver == null) {
            stopReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    Runnable r = stopAction;
                    if (r != null) {
                        AndroidUtilities.runOnUIThread(r);
                    }
                }
            };
            try {
                ApplicationLoader.registerReceiverNotExported(stopReceiver, new IntentFilter(ACTION_STOP));
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        notifyProgress(true);
    }

    static void progress(String text, int messagesDone, int messagesTotal) {
        if (!running) return;
        if (text != null) status = text;
        done = messagesDone;
        total = messagesTotal;
        notifyProgress(false);
        updatePill();
    }

    static void setPillVisible(boolean visible) {
        pillWanted = visible && running;
        updatePill();
    }

    /** Экспорт закончился: убираем прогресс; если приложение свёрнуто — сообщаем итог уведомлением. */
    static void finish(String resultTitle, String resultText, boolean notifyResult) {
        running = false;
        pillWanted = false;
        stopAction = null;
        openAction = null;
        removePill();
        Context ctx = ApplicationLoader.applicationContext;
        try {
            NotificationManagerCompat.from(ctx).cancel(NOTIFY_PROGRESS_ID);
        } catch (Throwable ignore) {
        }
        if (stopReceiver != null) {
            try {
                ctx.unregisterReceiver(stopReceiver);
            } catch (Throwable ignore) {
            }
            stopReceiver = null;
        }
        if (notifyResult && canNotify(ctx)) {
            try {
                NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, channel(ctx))
                        .setSmallIcon(R.drawable.tsunderegram_notification)
                        .setContentTitle(resultTitle)
                        .setContentText(resultText)
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(resultText))
                        .setAutoCancel(true)
                        .setContentIntent(openAppIntent(ctx));
                notifySafe(ctx, NOTIFY_DONE_ID, b);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
    }

    private static int percent() {
        if (total <= 0) return -1;
        return Math.max(0, Math.min(100, (int) (done * 100L / total)));
    }

    private static String firstLine(String s) {
        if (s == null) return "";
        int i = s.indexOf('\n');
        return i >= 0 ? s.substring(0, i) : s;
    }

    private static boolean canNotify(Context ctx) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled();
    }

    private static String channel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Экспорт чата", NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                ch.enableVibration(false);
                ch.setSound(null, null);
                nm.createNotificationChannel(ch);
            }
        }
        return CHANNEL_ID;
    }

    private static PendingIntent openAppIntent(Context ctx) {
        Intent intent = new Intent(ctx, LaunchActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, 0x7E5D, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    @SuppressLint("MissingPermission")
    private static void notifySafe(Context ctx, int id, NotificationCompat.Builder b) {
        try {
            NotificationManagerCompat.from(ctx).notify(id, b.build());
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static void notifyProgress(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastNotifyTime < 1000) return;
        lastNotifyTime = now;
        Context ctx = ApplicationLoader.applicationContext;
        if (!canNotify(ctx)) return;
        try {
            Intent stop = new Intent(ACTION_STOP);
            stop.setPackage(ctx.getPackageName());
            PendingIntent stopPi = PendingIntent.getBroadcast(ctx, 0x7E5E, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            int p = percent();
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, channel(ctx))
                    .setSmallIcon(R.drawable.tsunderegram_notification)
                    .setContentTitle("Экспорт чата «" + title + "»")
                    .setContentText(firstLine(status))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSilent(true)
                    .setShowWhen(false)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setContentIntent(openAppIntent(ctx))
                    .addAction(0, "Остановить", stopPi);
            if (p >= 0) {
                b.setProgress(100, p, false);
                b.setSubText(p + "%");
            } else {
                b.setProgress(0, 0, true);
            }
            notifySafe(ctx, NOTIFY_PROGRESS_ID, b);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    private static String pillLabel() {
        int p = percent();
        String s = firstLine(status);
        if (s.startsWith("Упаковка")) {
            return "Экспорт чата · упаковка";
        }
        return p >= 0 ? "Экспорт чата · " + p + "%" : "Экспорт чата…";
    }

    private static void removePill() {
        if (pill != null && pill.getParent() instanceof ViewGroup) {
            ((ViewGroup) pill.getParent()).removeView(pill);
        }
        pill = null;
        pillText = null;
    }

    private static void updatePill() {
        if (!pillWanted) {
            removePill();
            return;
        }
        LaunchActivity activity = LaunchActivity.instance;
        if (activity == null || activity.isFinishing()) {
            removePill();
            return;
        }
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof FrameLayout)) {
            return;
        }
        FrameLayout root = (FrameLayout) content;
        if (pill == null || pill.getParent() != root) {
            removePill();
            createPill(activity, root);
        }
        if (pillText != null) {
            pillText.setText(pillLabel());
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private static void createPill(Context context, FrameLayout root) {
        final LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.HORIZONTAL);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(7), AndroidUtilities.dp(14), AndroidUtilities.dp(7));
        int bg = Theme.getColor(Theme.key_windowBackgroundWhite);
        view.setBackground(Theme.createRoundRectDrawable(AndroidUtilities.dp(18), (bg & 0x00FFFFFF) | 0xF2000000));
        if (Build.VERSION.SDK_INT >= 21) {
            view.setElevation(AndroidUtilities.dp(6));
        }
        RadialProgressView spinner = new RadialProgressView(context);
        spinner.setSize(AndroidUtilities.dp(16));
        spinner.setStrokeWidth(2f);
        spinner.setProgressColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        view.addView(spinner, LayoutHelper.createLinear(20, 20, Gravity.CENTER_VERTICAL));
        TextView text = new TextView(context);
        text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        text.setTypeface(AndroidUtilities.bold());
        text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        text.setSingleLine(true);
        text.setText(pillLabel());
        view.addView(text, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 8, 0, 0, 0));
        view.setContentDescription("Экспорт чата: открыть прогресс");

        final int slop = ViewConfiguration.get(context).getScaledTouchSlop();
        view.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY, startTx, startTy;
            boolean dragging;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX();
                        downY = e.getRawY();
                        startTx = v.getTranslationX();
                        startTy = v.getTranslationY();
                        dragging = false;
                        v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(120).start();
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                        if (!dragging && Math.hypot(dx, dy) > slop) dragging = true;
                        if (dragging) {
                            View parent = (View) v.getParent();
                            float maxX = parent != null ? Math.max(0, (parent.getWidth() - v.getWidth()) / 2f) : 0;
                            float maxY = parent != null ? Math.max(0, parent.getHeight() - v.getBottom() - AndroidUtilities.dp(16)) : 0;
                            float minY = -v.getTop() + AndroidUtilities.statusBarHeight;
                            pillTx = Math.max(-maxX, Math.min(maxX, startTx + dx));
                            pillTy = Math.max(minY, Math.min(maxY, startTy + dy));
                            v.setTranslationX(pillTx);
                            v.setTranslationY(pillTy);
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        if (!dragging) {
                            Runnable r = openAction;
                            if (r != null) r.run();
                        }
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                        return true;
                }
                return false;
            }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = AndroidUtilities.statusBarHeight + ActionBar.getCurrentActionBarHeight() + AndroidUtilities.dp(8);
        root.addView(view, lp);
        view.setTranslationX(pillTx);
        view.setTranslationY(pillTy);
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(180).start();
        pill = view;
        pillText = text;
    }
}
