package org.telegram.messenger;

import android.os.SystemClock;
import android.text.TextUtils;

import org.unifiedpush.android.connector.UnifiedPush;


public class UnifiedPushListenerServiceProvider implements PushListenerController.IPushListenerServiceProvider {
    public UnifiedPushListenerServiceProvider(){}

    @Override
    public boolean hasServices() {
        // TsundereGram: встроенный «Google FCM» считается только при наличии сервисов Google
        return TsundereFcmDistributor.hasAnyDistributor(ApplicationLoader.applicationContext);
    }

    @Override
    public String getLogTitle() {
        return "UnifiedPush";
    }

    @Override
    public void onRequestPushToken() {
        String currentPushString = SharedConfig.pushString;
        if (!TextUtils.isEmpty(currentPushString)) {
            FileLog.d("UnifiedPush endpoint = " + currentPushString);
        } else {
            FileLog.d("No UnifiedPush string found");
        }
        Utilities.globalQueue.postRunnable(() -> {
            try {
                SharedConfig.pushStringGetTimeStart = SystemClock.elapsedRealtime();
                SharedConfig.saveConfig();
                if (UnifiedPush.getAckDistributor(ApplicationLoader.applicationContext) == null) {
                    String distributor = TsundereFcmDistributor.defaultDistributor(ApplicationLoader.applicationContext);
                    if (distributor != null) {
                        UnifiedPush.saveDistributor(ApplicationLoader.applicationContext, distributor);
                    }
                }
                TsundereFcmDistributor.syncComponents(ApplicationLoader.applicationContext,
                        TsundereFcmDistributor.currentDistributor(ApplicationLoader.applicationContext));
                UnifiedPush.register(
                        ApplicationLoader.applicationContext,
                        "default",
                        "Telegram Simple Push",
                        null
                );
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    @Override
    public int getPushType() {
        return PushListenerController.PUSH_TYPE_WEB;
    }
}
