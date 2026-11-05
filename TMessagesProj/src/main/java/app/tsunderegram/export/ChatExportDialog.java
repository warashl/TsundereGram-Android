package app.tsunderegram.export;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RadialProgressView;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/** Интерфейс встроенного экспорта чата: выбор медиа, прогресс, готовые экспорты. */
public class ChatExportDialog {

    private static final String PREFS = "tsundere_chat_export";
    private static final String KEY_MASK = "mask";
    private static final int DEFAULT_MASK = ChatExporter.M_PHOTO | ChatExporter.M_VOICE | ChatExporter.M_ROUND | ChatExporter.M_STICKER | ChatExporter.M_GIF;

    private static final int[] TYPES = {
            ChatExporter.M_PHOTO, ChatExporter.M_VIDEO, ChatExporter.M_VOICE, ChatExporter.M_ROUND,
            ChatExporter.M_STICKER, ChatExporter.M_GIF, ChatExporter.M_MUSIC, ChatExporter.M_FILE
    };
    private static final String[] TYPE_NAMES = {
            "Фото", "Видео", "Голосовые сообщения", "Видеосообщения (кружки)",
            "Стикеры", "GIF", "Музыка", "Файлы"
    };

    private static class Session {
        ChatExporter exporter;
        String title;
        String status = "Подготовка…";
        AlertDialog progressDialog;
        TextView statusView;
        boolean stopping;
    }

    private static Session current;

    public static void show(BaseFragment fragment, long dialogId) {
        if (fragment == null) return;
        Activity activity = fragment.getParentActivity();
        if (activity == null) return;
        if (current != null) {
            showProgress(fragment, current);
            return;
        }
        if (DialogObject.isEncryptedDialog(dialogId)) {
            AlertDialog.Builder b = new AlertDialog.Builder(activity);
            b.setTitle("Экспорт чата");
            b.setMessage("Секретные чаты не экспортируются: их история зашифрована и хранится только на этом устройстве.");
            b.setPositiveButton("ОК", null);
            fragment.showDialog(b.create());
            return;
        }
        final int account = fragment.getCurrentAccount();
        final String title = chatTitle(fragment, dialogId);
        final SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        final int savedMask = prefs.getInt(KEY_MASK, DEFAULT_MASK);

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        TextView info = new TextView(activity);
        info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        info.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        info.setText("Вся история «" + title + "» сохранится в один HTML-файл: откройте его в любом браузере — переписка выглядит как чат в Telegram, а отмеченные медиа лежат прямо внутри файла. Недостающие файлы будут скачаны.");
        list.addView(info, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 4, 24, 8));
        final TextCheckCell[] cells = new TextCheckCell[TYPES.length];
        for (int i = 0; i < TYPES.length; i++) {
            final TextCheckCell cell = new TextCheckCell(activity, 23, true);
            cell.setTextAndCheck(TYPE_NAMES[i], (savedMask & TYPES[i]) != 0, i != TYPES.length - 1);
            cell.setBackground(Theme.getSelectorDrawable(false));
            cell.setOnClickListener(v -> cell.setChecked(!cell.isChecked()));
            cells[i] = cell;
            list.addView(cell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }
        TextView hint = new TextView(activity);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hint.setTextColor(Theme.getColor(Theme.key_dialogTextGray3));
        hint.setText("Текст, ответы, пересылки, реакции и форматирование сохраняются всегда. Файлы больше " + (ChatExporter.MAX_EMBED_FILE / 1024 / 1024) + " МБ в экспорт не встраиваются, чтобы браузер смог его открыть.");
        list.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 8, 24, 4));
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(list, LayoutHelper.createScroll(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Экспорт чата");
        builder.setView(scroll);
        builder.setPositiveButton("Экспортировать", (d, w) -> {
            int mask = 0;
            for (int i = 0; i < TYPES.length; i++) {
                if (cells[i].isChecked()) mask |= TYPES[i];
            }
            prefs.edit().putInt(KEY_MASK, mask).apply();
            start(fragment, account, dialogId, title, mask);
        });
        builder.setNeutralButton("Готовые экспорты", (d, w) -> showArchives(fragment));
        builder.setNegativeButton("Отмена", null);
        fragment.showDialog(builder.create());
    }

    private static String chatTitle(BaseFragment fragment, long dialogId) {
        String title = null;
        if (dialogId > 0) {
            TLRPC.User user = fragment.getMessagesController().getUser(dialogId);
            if (user != null) {
                title = UserObject.isUserSelf(user) ? "Избранное" : UserObject.getUserName(user);
            }
        } else if (dialogId < 0) {
            TLRPC.Chat chat = fragment.getMessagesController().getChat(-dialogId);
            if (chat != null) title = chat.title;
        }
        return TextUtils.isEmpty(title) ? "Чат" : title;
    }

    private static void start(BaseFragment fragment, int account, long dialogId, String title, int mask) {
        final Session session = new Session();
        session.title = title;
        current = session;
        session.exporter = new ChatExporter(account, dialogId, title, mask, new ChatExporter.Callback() {
            @Override
            public void onProgress(String text) {
                session.status = text;
                if (session.statusView != null) {
                    session.statusView.setText(text);
                }
                ChatExportIndicator.progress(text, session.exporter.getMessagesCount(), session.exporter.getTotal());
            }

            @Override
            public void onDone(File file, int messages, int files, int failed, String error, boolean cancelled) {
                if (current == session) current = null;
                if (session.progressDialog != null) {
                    try {
                        session.progressDialog.dismiss();
                    } catch (Exception ignore) {
                    }
                }
                BaseFragment target = LaunchActivity.getSafeLastFragment();
                if (target == null || target.getParentActivity() == null) target = fragment;
                Activity activity = target != null ? target.getParentActivity() : null;
                final boolean background = activity == null || ApplicationLoader.mainInterfacePaused;
                if (cancelled) {
                    ChatExportIndicator.finish(null, null, false);
                    Toast.makeText(ApplicationLoader.applicationContext, "Экспорт отменён", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (file == null) {
                    ChatExportIndicator.finish("Экспорт «" + session.title + "» не удался",
                            TextUtils.isEmpty(error) ? "Неизвестная ошибка" : error, background);
                    if (activity == null) {
                        Toast.makeText(ApplicationLoader.applicationContext, "Экспорт не удался: " + error, Toast.LENGTH_LONG).show();
                        return;
                    }
                    AlertDialog.Builder b = new AlertDialog.Builder(activity);
                    b.setTitle("Экспорт не удался");
                    b.setMessage(TextUtils.isEmpty(error) ? "Неизвестная ошибка" : error);
                    b.setPositiveButton("ОК", null);
                    target.showDialog(b.create());
                    return;
                }
                StringBuilder sb = new StringBuilder();
                sb.append("Чат: ").append(session.title)
                        .append("\nСообщений: ").append(messages)
                        .append("\nМедиафайлов: ").append(files);
                if (failed > 0) sb.append("\nНе удалось скачать: ").append(failed);
                if (!TextUtils.isEmpty(error)) sb.append("\n\nЭкспорт неполный — ошибка сети: ").append(error);
                sb.append("\n\nФайл: ").append(file.getName()).append(" (").append(AndroidUtilities.formatFileSize(file.length())).append(")");
                ChatExportIndicator.finish("Экспорт «" + session.title + "» готов",
                        "Сообщений: " + messages + ", медиафайлов: " + files + " • " + AndroidUtilities.formatFileSize(file.length())
                                + ". Откройте «Экспорт чата» → «Готовые экспорты»", background);
                if (activity == null) {
                    Toast.makeText(ApplicationLoader.applicationContext, "Экспорт чата готов: откройте «Экспорт чата» → «Готовые экспорты»", Toast.LENGTH_LONG).show();
                    return;
                }
                showArchive(target, file, "Экспорт готов", sb.toString(), false);
            }
        });
        ChatExportIndicator.start(title, () -> {
            if (current != session) return;
            session.stopping = true;
            session.status = "Остановка…";
            if (session.statusView != null) session.statusView.setText(session.status);
            ChatExportIndicator.setPillVisible(false);
            session.exporter.cancel();
        }, ChatExportDialog::reopenProgress);
        showProgress(fragment, session);
        session.exporter.start();
    }

    /** Открыть окно прогресса свёрнутого экспорта (из плашки). */
    private static void reopenProgress() {
        Session session = current;
        if (session == null || session.stopping) return;
        BaseFragment fragment = LaunchActivity.getSafeLastFragment();
        if (fragment == null || fragment.getParentActivity() == null) return;
        showProgress(fragment, session);
    }

    private static void showProgress(BaseFragment fragment, Session session) {
        Activity activity = fragment.getParentActivity();
        if (activity == null || session.progressDialog != null) return;
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.HORIZONTAL);
        RadialProgressView spinner = new RadialProgressView(activity);
        spinner.setSize(AndroidUtilities.dp(28));
        box.addView(spinner, LayoutHelper.createLinear(36, 36, Gravity.CENTER_VERTICAL));
        TextView status = new TextView(activity);
        status.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
        status.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        status.setText(session.status);
        box.addView(status, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL, 16, 0, 0, 0));
        LinearLayout wrap = new LinearLayout(activity);
        wrap.addView(box, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP, 24, 8, 24, 8));

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Экспорт: " + session.title);
        builder.setView(wrap);
        builder.setPositiveButton("Свернуть", (d, w) -> Toast.makeText(ApplicationLoader.applicationContext,
                "Экспорт продолжается: прогресс — на плашке сверху и в уведомлении", Toast.LENGTH_SHORT).show());
        builder.setNegativeButton("Остановить", (d, w) -> {
            session.stopping = true;
            session.status = "Остановка…";
            session.exporter.cancel();
        });
        AlertDialog dialog = builder.create();
        session.progressDialog = dialog;
        session.statusView = status;
        // слушатель передаём в showDialog: BaseFragment заменяет собственный OnDismissListener диалога
        if (fragment.showDialog(dialog, d -> {
            if (session.progressDialog == dialog) {
                session.progressDialog = null;
                session.statusView = null;
            }
            // свернули — экспорт продолжает показываться плашкой поверх приложения
            ChatExportIndicator.setPillVisible(current == session && !session.stopping);
        }) == null) {
            session.progressDialog = null;
            session.statusView = null;
            ChatExportIndicator.setPillVisible(current == session && !session.stopping);
            return;
        }
        ChatExportIndicator.setPillVisible(false);
        // после showDialog: он включает закрытие по тапу снаружи
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private static File[] listArchives() {
        File[] files = ChatExporter.getExportsDir().listFiles(f -> f.isFile() && f.getName().endsWith(".html"));
        if (files == null) return new File[0];
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files;
    }

    private static void showArchives(BaseFragment fragment) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) return;
        final File[] files = listArchives();
        if (files.length == 0) {
            Toast.makeText(activity, "Готовых экспортов пока нет", Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] names = new CharSequence[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].getName() + " • " + AndroidUtilities.formatFileSize(files[i].length());
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Готовые экспорты");
        builder.setItems(names, (d, which) -> {
            File f = files[which];
            String info = "Файл: " + f.getName()
                    + "\nРазмер: " + AndroidUtilities.formatFileSize(f.length())
                    + "\nСоздан: " + new SimpleDateFormat("dd.MM.yyyy HH:mm", new Locale("ru")).format(new Date(f.lastModified()));
            showArchive(fragment, f, "Экспорт чата", info, true);
        });
        builder.setNegativeButton("Закрыть", null);
        fragment.showDialog(builder.create());
    }

    private static void showArchive(BaseFragment fragment, File file, String title, String message, boolean allowDelete) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        builder.setMessage(message);
        CharSequence[] items = allowDelete
                ? new CharSequence[]{"Открыть в браузере", "Сохранить в «Загрузки»", "Поделиться", "Удалить"}
                : new CharSequence[]{"Открыть в браузере", "Сохранить в «Загрузки»", "Поделиться"};
        builder.setItems(items, (d, which) -> {
            if (which == 0) {
                open(activity, file);
            } else if (which == 1) {
                saveToDownloads(activity, file);
            } else if (which == 2) {
                share(activity, file);
            } else {
                confirmDelete(fragment, file);
            }
        });
        builder.setNegativeButton("Закрыть", null);
        fragment.showDialog(builder.create());
    }

    private static void open(Activity activity, File file) {
        if (!file.exists()) {
            Toast.makeText(activity, "Файл экспорта не найден", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "text/html");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "Открыть экспорт"));
        } catch (Throwable t) {
            FileLog.e(t);
            Toast.makeText(activity, "Нет приложения для открытия: сохраните файл в «Загрузки» и откройте его в браузере", Toast.LENGTH_LONG).show();
        }
    }

    private static void confirmDelete(BaseFragment fragment, File file) {
        Activity activity = fragment.getParentActivity();
        if (activity == null) return;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Удалить экспорт?");
        builder.setMessage("«" + file.getName() + "» будет удалён из памяти приложения. Копии, сохранённые в «Загрузки» или отправленные, останутся.");
        builder.setPositiveButton("Удалить", (d, w) -> {
            boolean ok = file.delete();
            Toast.makeText(ApplicationLoader.applicationContext, ok ? "Экспорт удалён" : "Не удалось удалить экспорт", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Отмена", null);
        AlertDialog dialog = builder.create();
        fragment.showDialog(dialog);
        android.view.View button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (button instanceof TextView) {
            ((TextView) button).setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private static boolean saveToDownloads(Activity activity, File file) {
        if (!file.exists()) {
            Toast.makeText(activity, "Файл экспорта не найден", Toast.LENGTH_SHORT).show();
            return true;
        }
        if (Build.VERSION.SDK_INT >= 23 && (Build.VERSION.SDK_INT <= 28 || BuildVars.NO_SCOPED_STORAGE)
                && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 4);
            Toast.makeText(activity, "Разрешите доступ к памяти и нажмите «Сохранить в «Загрузки»» ещё раз", Toast.LENGTH_LONG).show();
            return false;
        }
        MediaController.saveFile(file.getAbsolutePath(), activity, 2, file.getName(), "text/html",
                uri -> Toast.makeText(ApplicationLoader.applicationContext, "Экспорт сохранён в «Загрузки» — откройте его в браузере", Toast.LENGTH_LONG).show());
        return true;
    }

    private static void share(Activity activity, File file) {
        try {
            Uri uri = FileProvider.getUriForFile(activity, ApplicationLoader.getApplicationId() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/html");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "Отправить экспорт"));
        } catch (Throwable t) {
            FileLog.e(t);
            Toast.makeText(activity, "Не удалось поделиться экспортом", Toast.LENGTH_SHORT).show();
        }
    }
}
