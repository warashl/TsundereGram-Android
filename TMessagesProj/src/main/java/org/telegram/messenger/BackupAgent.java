package org.telegram.messenger;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.BackupManager;
import android.app.backup.FullBackupDataOutput;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.ParcelFileDescriptor;

import java.io.IOException;

public class BackupAgent extends BackupAgentHelper {

    /** TsundereGram: настройка «Не включать в резервную копию Android». */
    private static final String PRIVACY_PREFS = "tsundere_privacy";
    private static final String KEY_NO_BACKUP = "no_android_backup";
    private static final String[] BACKED_UP = {"saved_tokens", "saved_tokens_login"};

    private static BackupManager backupManager;

    @Override
    public void onCreate() {
        SharedPreferencesBackupHelper helper = new SharedPreferencesBackupHelper(this, BACKED_UP);
        addHelper("prefs", helper);
    }

    private static SharedPreferences privacyPrefs(Context context) {
        return context.getSharedPreferences(PRIVACY_PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isExcludedFromBackup() {
        Context context = ApplicationLoader.applicationContext;
        return context != null && privacyPrefs(context).getBoolean(KEY_NO_BACKUP, false);
    }

    public static void setExcludedFromBackup(boolean excluded) {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        privacyPrefs(context).edit().putBoolean(KEY_NO_BACKUP, excluded).commit();
        // при включении запрос нужен, чтобы система стёрла уже сохранённые данные
        requestBackup();
    }

    private boolean excluded() {
        try {
            return privacyPrefs(this).getBoolean(KEY_NO_BACKUP, false);
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) throws IOException {
        if (excluded()) {
            // удаляем записи, которые могли попасть в облако раньше
            for (String name : BACKED_UP) {
                try {
                    data.writeEntityHeader("prefs:" + name, -1);
                } catch (Throwable ignore) {
                }
            }
            return;
        }
        super.onBackup(oldState, data, newState);
    }

    @Override
    public void onFullBackup(FullBackupDataOutput data) throws IOException {
        if (excluded()) {
            return; // полная копия (перенос на новое устройство, adb backup) — пустая
        }
        super.onFullBackup(data);
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState) throws IOException {
        if (excluded()) {
            return;
        }
        super.onRestore(data, appVersionCode, newState);
    }

    public static void requestBackup() {
        if (backupManager == null) {
            backupManager = new BackupManager(ApplicationLoader.applicationContext);
        }
        backupManager.dataChanged();
    }
}
