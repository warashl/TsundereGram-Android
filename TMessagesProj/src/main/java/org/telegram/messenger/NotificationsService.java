/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.messenger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import xyz.nextalone.nagram.NaConfig;

public class NotificationsService extends Service {

    private static final String CHANNEL_ID = "push_service_channel";
    private static final String CHANNEL_ID_QUIET = "push_service_channel_min";

    @Override
    public void onCreate() {
        super.onCreate();
        ApplicationLoader.postInitApplication();
        goForeground();
        resumeConnections();
    }

    private void resumeConnections() {
        try {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (UserConfig.getInstance(a).isClientActivated()) {
                    org.telegram.tgnet.ConnectionsManager.getInstance(a).setPushConnectionEnabled(true);
                    org.telegram.tgnet.ConnectionsManager.getInstance(a).resumeNetworkMaybe();
                }
            }
        } catch (Throwable ignored) {}
    }

    public static final int SERVICE_NOTIFICATION_ID = 9999;
    private static final String DEFAULT_TEXT = "Б-бака!(✿^‿^) Ц-цундере следит за сообщениями... ✨";

    private static volatile boolean running;
    /** TsundereGram: текст последнего уведомления о сообщении (null — стандартный текст). */
    private static volatile String messagePreview;

    private static Notification buildNotification(Context context) {
        final boolean visible = NaConfig.INSTANCE.getPushServiceTypeInAppDialog().Bool();
        final String channelId = visible ? CHANNEL_ID : CHANNEL_ID_QUIET;
        final String preview = messagePreview;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setShowWhen(false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setContentTitle("TsundereGram")
                .setPriority(visible ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_MIN)
                .setSmallIcon(preview != null ? R.drawable.tsundere_notification_arrow : R.drawable.tsunderegram_notification)
                .setContentText(preview != null ? preview : DEFAULT_TEXT);
        if (preview != null) {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(preview));
            try {
                Intent intent = new Intent(context, org.telegram.ui.LaunchActivity.class);
                intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                builder.setContentIntent(android.app.PendingIntent.getActivity(context, 9999, intent,
                        android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT));
            } catch (Throwable ignore) {
            }
        }
        return builder.build();
    }

    /**
     * TsundereGram: пока висит уведомление о сообщении, постоянное уведомление сервиса показывает
     * это сообщение (отправитель + текст) со стрелкой; когда уведомление о сообщении убрано — снова
     * стандартный текст. {@code text == null} — вернуть стандартный текст.
     */
    public static void setMessagePreview(String text) {
        if (text != null) {
            text = text.replace('\n', ' ').trim();
            if (text.length() > 300) {
                text = text.substring(0, 300) + "…";
            }
            if (text.isEmpty()) {
                text = null;
            }
        }
        if (android.text.TextUtils.equals(messagePreview, text)) {
            return;
        }
        messagePreview = text;
        if (!running) {
            return;
        }
        try {
            Context context = ApplicationLoader.applicationContext;
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(SERVICE_NOTIFICATION_ID, buildNotification(context));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /**
     * TsundereGram: аккаунт, чьё уведомление о сообщении сейчас показано вместо постоянного
     * уведомления сервиса (-1 — показано стандартное). Так вместо двух плашек — одна: пришло
     * сообщение — плашка сервиса превращается в него, прочитали/смахнули — возвращается обратно.
     */
    private static volatile int messageOwnerAccount = -1;
    private static volatile Notification lastMessageNotification;

    public static boolean canShowMessage(int account) {
        return running && (messageOwnerAccount == -1 || messageOwnerAccount == account);
    }

    public static boolean isShowingMessage(int account) {
        return messageOwnerAccount == account;
    }

    /** Показывает уведомление о сообщении на месте уведомления сервиса. */
    public static boolean showMessage(int account, Notification notification) {
        if (!running || notification == null) {
            return false;
        }
        Context context = ApplicationLoader.applicationContext;
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification);
        messageOwnerAccount = account;
        lastMessageNotification = notification;
        messagePreview = null;
        return true;
    }

    /** Возвращает стандартное уведомление сервиса, если сейчас там сообщение этого аккаунта. */
    public static void releaseMessage(int account) {
        if (messageOwnerAccount != account) {
            return;
        }
        messageOwnerAccount = -1;
        lastMessageNotification = null;
        messagePreview = null;
        if (!running) {
            return;
        }
        try {
            Context context = ApplicationLoader.applicationContext;
            NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(SERVICE_NOTIFICATION_ID, buildNotification(context));
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    /** Уведомления о сообщениях могли смахнуть — если их больше нет, возвращаем стандартный текст. */
    public static void recheckMessagePreview() {
        if (messageOwnerAccount != -1) {
            final int owner = messageOwnerAccount;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    if (messageOwnerAccount != owner) {
                        return;
                    }
                    NotificationManager nm = (NotificationManager) ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
                    for (android.service.notification.StatusBarNotification sbn : nm.getActiveNotifications()) {
                        if (sbn.getId() == SERVICE_NOTIFICATION_ID && sbn.getTag() == null) {
                            return;
                        }
                    }
                    // Сообщение смахнули (Android 14+ это позволяет) — возвращаем плашку сервиса
                    releaseMessage(owner);
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }, 800);
            return;
        }
        if (messagePreview == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            try {
                NotificationManager nm = (NotificationManager) ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
                for (android.service.notification.StatusBarNotification sbn : nm.getActiveNotifications()) {
                    if (sbn.getId() == SERVICE_NOTIFICATION_ID || sbn.getNotification() == null) {
                        continue;
                    }
                    if (Notification.CATEGORY_MESSAGE.equals(sbn.getNotification().category)) {
                        return;
                    }
                }
                setMessagePreview(null);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }, 800);
    }

    private void goForeground() {
        final boolean visible = NaConfig.INSTANCE.getPushServiceTypeInAppDialog().Bool();
        final String channelId = visible ? CHANNEL_ID : CHANNEL_ID_QUIET;
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(channelId,
                LocaleController.getString(R.string.NagramXPushService),
                visible ? NotificationManager.IMPORTANCE_DEFAULT : NotificationManager.IMPORTANCE_MIN);
        channel.setShowBadge(false);
        notificationManager.createNotificationChannel(channel);
        // Сервис перезапускается по будильнику: если сейчас на его месте сообщение — оставляем его
        Notification notification = messageOwnerAccount != -1 && lastMessageNotification != null ? lastMessageNotification : buildNotification(this);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(SERVICE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING);
            } else {
                startForeground(SERVICE_NOTIFICATION_ID, notification);
            }
            running = true;
        } catch (Throwable e) {
            Log.e("TFOSS", "Failed to start push service", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ApplicationLoader.postInitApplication();
        goForeground();
        resumeConnections();
        scheduleNextAlarm();
        return START_STICKY;
    }

    private void scheduleNextAlarm() {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null) {
                Intent i = new Intent(this, NotificationsService.class);
                android.app.PendingIntent pi = android.app.PendingIntent.getForegroundService(
                        this, 0, i, android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 10 * 60 * 1000, pi);
                }
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        requestRestart();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void onDestroy() {
        running = false;
        final int owner = messageOwnerAccount;
        messageOwnerAccount = -1;
        lastMessageNotification = null;
        if (owner != -1) {
            // плашка сервиса пропадёт вместе с сообщением — пусть оно покажется обычным уведомлением
            try {
                app.tsunderegram.notifications.NotificationsHelper.clearPostedSignatures(owner);
                NotificationsController.getInstance(owner).showNotifications();
            } catch (Throwable ignore) {
            }
        }
        super.onDestroy();
        try {
            stopForeground(true);
        } catch (Throwable ignore) {
        }
        requestRestart();
    }

    private void requestRestart() {
        SharedPreferences preferences = MessagesController.getNotificationsSettings(UserConfig.selectedAccount);
        if (!preferences.contains("pushService")) {
            preferences = MessagesController.getGlobalNotificationsSettings();
        }
        if (preferences.getBoolean("pushService", true)) {
            Intent intent = new Intent("org.telegram.start");
            intent.setPackage(getPackageName());
            try {
                sendBroadcast(intent);
            } catch (Exception ex) {
                // 辣鷄miui 就你事最多.jpg
            }
        }
    }
}
