package app.tsunderegram.export;

import android.text.TextUtils;
import android.util.Base64;
import android.util.Base64OutputStream;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Экспорт истории чата в ОДИН html-файл, который открывается в любом браузере и выглядит как переписка
 * в Telegram: пузыри, аватарки, ответы, пересылки, альбомы, реакции, поиск. Медиа встраиваются прямо
 * в файл (base64), поэтому ничего распаковывать не нужно: скачал — открыл — читаешь чат.
 */
public class ChatExporter {

    public static final int M_PHOTO = 1, M_VIDEO = 2, M_VOICE = 4, M_ROUND = 8, M_STICKER = 16, M_GIF = 32, M_MUSIC = 64, M_FILE = 128;
    public static final int M_ALL = 255;

    /** Больше этого в один файл не кладём: браузер на телефоне должен такой файл открыть. */
    public static final long MAX_EMBED_FILE = 30L * 1024 * 1024;
    public static final long MAX_EMBED_TOTAL = 300L * 1024 * 1024;

    public interface Callback {
        void onProgress(String text);

        void onDone(File file, int messages, int files, int failed, String error, boolean cancelled);
    }

    private static final String[] PEER_COLORS = {"#e17076", "#faa774", "#a695e7", "#7bc862", "#6ec9cb", "#65aadd", "#ee7aae"};

    private final int account;
    private final long dialogId;
    private final String title;
    private final int mask;
    private final Callback callback;
    private volatile boolean cancelled;

    private final LongSparseArray<TLRPC.User> users = new LongSparseArray<>();
    private final LongSparseArray<TLRPC.Chat> chats = new LongSparseArray<>();
    private final Locale ru = new Locale("ru");
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("d MMMM yyyy", ru);
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", ru);
    private final SimpleDateFormat fullFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss", ru);
    private OutputStream out;
    private int messagesCount, filesCount, failedCount, skippedCount, total;
    private long embeddedBytes;
    private int blobCounter;
    private int lastDayKey = -1;
    private long lastProgress;
    private final boolean isGroup;
    private final boolean channel;
    private static final int MAX_AVATARS = 80;
    private final HashSet<Long> avatarTried = new HashSet<>();
    private long avatarWaitLeft = 60_000L;
    private boolean lastObtainSkipped;
    private final ArrayList<Object[]> pendingBlobs = new ArrayList<>();

    /** Кто писал предыдущее сообщение — для группировки пузырей как в Telegram. */
    private long lastFromId = Long.MIN_VALUE;
    private int lastFromDate;
    /** Короткие подписи уже выгруженных сообщений — для цитаты в ответах. */
    private final HashMap<Integer, String[]> replyPreview = new HashMap<>();
    private final HashSet<Long> avatarStyles = new HashSet<>();
    private final ArrayList<TLRPC.Message> album = new ArrayList<>();

    public ChatExporter(int account, long dialogId, String title, int mask, Callback callback) {
        this.account = account;
        this.dialogId = dialogId;
        this.title = title;
        this.mask = mask;
        this.callback = callback;
        boolean isChannel = false;
        if (dialogId < 0) {
            TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
            // в канале все посты от имени канала — аватарки и «свои» пузыри не нужны
            isChannel = chat != null && ChatObject.isChannelAndNotMegaGroup(chat);
        }
        this.channel = isChannel;
        this.isGroup = dialogId < 0 && !isChannel;
    }

    public int getMessagesCount() {
        return messagesCount;
    }

    public int getTotal() {
        return total;
    }

    public void start() {
        Thread t = new Thread(this::run, "TsundereChatExport");
        t.setPriority(Thread.MIN_PRIORITY + 1);
        t.start();
    }

    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public static File getExportsDir() {
        File base = ApplicationLoader.applicationContext.getExternalFilesDir(null);
        if (base == null) base = ApplicationLoader.applicationContext.getFilesDir();
        return new File(base, "exports");
    }

    private void run() {
        String error = null;
        File result = null;
        File part = null;
        try {
            File base = getExportsDir();
            if (!base.isDirectory() && !base.mkdirs()) {
                throw new IOException("Не удалось создать папку экспорта");
            }
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
            String baseName = safeName(title) + "_" + stamp;
            File candidate = new File(base, baseName + ".html");
            for (int i = 2; candidate.exists(); i++) {
                // никогда не трогаем уже существующие экспорты — берём свободное имя
                candidate = new File(base, baseName + "_" + i + ".html");
            }
            part = new File(base, candidate.getName() + ".part");
            out = new BufferedOutputStream(new FileOutputStream(part), 256 * 1024);
            writeHeader();
            try {
                loadHistory();
            } catch (InterruptedException e) {
                cancelled = true;
            } catch (Throwable t) {
                // сетевые ошибки посреди истории: сохраняем то, что уже выгружено
                FileLog.e(t);
                error = describe(t);
            }
            flushAlbum();
            writeFooter(error != null);
            out.close();
            out = null;
            if (!cancelled && (error == null || messagesCount > 0)) {
                if (!part.renameTo(candidate)) {
                    throw new IOException("Не удалось сохранить файл экспорта");
                }
                result = candidate;
            }
        } catch (Throwable t) {
            FileLog.e(t);
            error = describe(t);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignore) {
                }
                out = null;
            }
            if (part != null && part.exists()) {
                //noinspection ResultOfMethodCallIgnored
                part.delete();
            }
        }
        final File fResult = result;
        final String fError = error;
        final boolean fCancelled = cancelled;
        AndroidUtilities.runOnUIThread(() -> callback.onDone(fResult, messagesCount, filesCount, failedCount, fError, fCancelled));
    }

    private static String describe(Throwable t) {
        return t.getMessage() != null ? t.getMessage() : t.toString();
    }

    private void loadHistory() throws Exception {
        TLRPC.InputPeer peer = MessagesController.getInstance(account).getInputPeer(dialogId);
        if (peer == null || peer instanceof TLRPC.TL_inputPeerEmpty) {
            throw new IOException("Чат недоступен");
        }
        int cursor = 1;
        int maxSeen = 0;
        progress("Загрузка истории…", true);
        while (!cancelled) {
            TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
            req.peer = peer;
            req.offset_id = cursor;
            req.add_offset = -100;
            req.limit = 100;
            TLObject resp = request(req);
            if (!(resp instanceof TLRPC.messages_Messages)) break;
            TLRPC.messages_Messages res = (TLRPC.messages_Messages) resp;
            if (total == 0) total = Math.max(res.count, res.messages.size());
            for (TLRPC.User u : res.users) users.put(u.id, u);
            for (TLRPC.Chat c : res.chats) chats.put(c.id, c);
            ArrayList<TLRPC.Message> page = new ArrayList<>();
            for (TLRPC.Message m : res.messages) {
                if (m != null && m.id > maxSeen) page.add(m);
            }
            if (page.isEmpty()) break;
            Collections.sort(page, (a, b) -> Integer.compare(a.id, b.id));
            for (TLRPC.Message m : page) {
                if (cancelled) break;
                maxSeen = Math.max(maxSeen, m.id);
                if (m instanceof TLRPC.TL_messageEmpty) continue;
                try {
                    handleMessage(m);
                } catch (IOException e) {
                    throw e;
                } catch (Throwable t) {
                    FileLog.e(t);
                }
                messagesCount++;
                progress(null, false);
            }
            cursor = maxSeen + 1;
            Thread.sleep(200);
        }
    }

    private void progress(String text, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastProgress < 300) return;
        lastProgress = now;
        final String t = text != null ? text : ("Сообщений: " + messagesCount + (total > 0 ? " из " + total : "") + "\nМедиафайлов: " + filesCount);
        AndroidUtilities.runOnUIThread(() -> callback.onProgress(t));
    }

    private TLObject request(TLObject req) throws Exception {
        for (int attempt = 0; attempt < 6 && !cancelled; attempt++) {
            final CountDownLatch latch = new CountDownLatch(1);
            final TLObject[] res = new TLObject[1];
            final TLRPC.TL_error[] err = new TLRPC.TL_error[1];
            ConnectionsManager.getInstance(account).sendRequest(req, (response, e) -> {
                res[0] = response;
                err[0] = e;
                latch.countDown();
            });
            if (!latch.await(60, TimeUnit.SECONDS)) continue;
            if (err[0] == null) return res[0];
            String text = err[0].text != null ? err[0].text : "";
            if (text.startsWith("FLOOD_WAIT_")) {
                int sec = 5;
                try {
                    sec = Integer.parseInt(text.substring("FLOOD_WAIT_".length()));
                } catch (Exception ignore) {
                }
                progress("Telegram просит подождать " + sec + " с…", true);
                sleepCancellable((sec + 1) * 1000L);
                continue;
            }
            throw new IOException(text);
        }
        if (cancelled) return null;
        throw new IOException("Нет ответа от сервера");
    }

    private void sleepCancellable(long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (!cancelled && System.currentTimeMillis() < end) {
            Thread.sleep(200);
        }
    }

    // ---------------- оформление ----------------

    private static final String CSS = """
            :root{--bg:#0f1424;--wall1:#1a2347;--wall2:#2b1d45;--wall3:#0f2a3d;--pat:rgba(255,255,255,.045);--panel:rgba(23,28,44,.82);--panel2:#1b2133;--in:#1f2538;--out1:#4a66d9;--out2:#8b55d4;--text:#f3f5fa;--muted:#8d97ad;--outtext:#ffffff;--outmuted:rgba(255,255,255,.72);--link:#8fb4ff;--outlink:#ffffff;--acc:#7c93ff;--acc2:#b07cff;--svc:rgba(20,24,40,.55);--quote:rgba(255,255,255,.07);--outquote:rgba(255,255,255,.16);--line:rgba(255,255,255,.08);--shadow:0 1px 2px rgba(0,0,0,.28)}
            html.light{--bg:#dfe7d4;--wall1:#cfe2c0;--wall2:#e9e2c3;--wall3:#c3d8e6;--pat:rgba(40,70,40,.07);--panel:rgba(255,255,255,.86);--panel2:#ffffff;--in:#ffffff;--out1:#e3fbd0;--out2:#d6f5c2;--text:#10151c;--muted:#7d8a96;--outtext:#10151c;--outmuted:#5c9c4f;--link:#2a7bd6;--outlink:#2a7bd6;--acc:#3390ec;--acc2:#5aa7f5;--svc:rgba(60,90,55,.38);--quote:rgba(0,0,0,.045);--outquote:rgba(70,140,60,.12);--line:rgba(0,0,0,.08);--shadow:0 1px 2px rgba(0,0,0,.14)}
            *{box-sizing:border-box;-webkit-tap-highlight-color:transparent}
            html,body{margin:0;padding:0}
            body{min-height:100vh;color:var(--text);font:15px/1.4 -apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;-webkit-font-smoothing:antialiased;background:var(--bg)}
            body:before{content:"";position:fixed;inset:0;z-index:-1;background:radial-gradient(1200px 700px at 0% 0%,var(--wall1),transparent 70%),radial-gradient(900px 700px at 100% 40%,var(--wall2),transparent 70%),radial-gradient(1000px 800px at 30% 100%,var(--wall3),transparent 70%),var(--bg)}
            body:after{content:"";position:fixed;inset:0;z-index:-1;pointer-events:none;background-image:radial-gradient(var(--pat) 1.2px,transparent 1.6px),radial-gradient(var(--pat) 1.2px,transparent 1.6px);background-size:28px 28px;background-position:0 0,14px 14px}
            button{font:inherit}
            svg{display:block}
            .top{position:sticky;top:0;z-index:6;display:flex;align-items:center;gap:12px;padding:8px 10px 8px 14px;background:var(--panel);-webkit-backdrop-filter:saturate(1.6) blur(18px);backdrop-filter:saturate(1.6) blur(18px);box-shadow:0 1px 0 var(--line)}
            .top .ttl{flex:1;min-width:0}
            .top .name{font-weight:650;font-size:16.5px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;letter-spacing:.1px}
            .top .sub{font-size:13px;color:var(--muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
            .ib{background:none;border:0;color:var(--muted);width:40px;height:40px;border-radius:50%;cursor:pointer;display:flex;align-items:center;justify-content:center;flex:0 0 40px;transition:background .15s,color .15s}
            .ib:hover,.ib.on{background:var(--quote);color:var(--text)}
            .search{display:none;position:sticky;top:56px;z-index:5;gap:6px;align-items:center;padding:6px 10px 8px 14px;background:var(--panel);-webkit-backdrop-filter:blur(18px);backdrop-filter:blur(18px);box-shadow:0 1px 0 var(--line)}
            .search.on{display:flex}
            .search .sw{flex:1;min-width:0;display:flex;align-items:center;gap:8px;background:var(--quote);border-radius:20px;padding:0 12px;color:var(--muted)}
            .search input{flex:1;min-width:0;background:none;border:0;padding:9px 0;color:var(--text);font:inherit;outline:none}
            .search input::-webkit-search-cancel-button{display:none}
            .search #qc{color:var(--muted);font-size:13px;min-width:48px;text-align:center;white-space:nowrap}
            .av{width:40px;height:40px;border-radius:50%;flex:0 0 40px;display:flex;align-items:center;justify-content:center;color:#fff;font-weight:600;font-size:15px;background-size:cover;background-position:center;user-select:none;background-image:linear-gradient(160deg,rgba(255,255,255,.22),rgba(0,0,0,.12))}
            .av[style*="background-color"]{text-shadow:0 1px 1px rgba(0,0,0,.18)}
            #chat{max-width:780px;margin:0 auto;padding:12px 12px 90px}
            .intro{display:flex;flex-direction:column;align-items:center;text-align:center;margin:18px auto 22px;padding:22px 18px 18px;max-width:420px;background:var(--panel);-webkit-backdrop-filter:blur(14px);backdrop-filter:blur(14px);border-radius:24px;box-shadow:var(--shadow)}
            .intro .av{width:92px;height:92px;flex-basis:92px;font-size:34px;box-shadow:0 6px 22px rgba(0,0,0,.25)}
            .intro h1{font-size:21px;margin:12px 0 2px;font-weight:700;overflow-wrap:anywhere}
            .intro p{margin:0;color:var(--muted);font-size:14px}
            .chips{display:flex;flex-wrap:wrap;justify-content:center;gap:6px;margin-top:14px}
            .chips span{background:var(--quote);border-radius:12px;padding:4px 10px;font-size:13px;color:var(--text)}
            .chips span b{font-weight:650}
            .day{position:sticky;top:64px;z-index:3;text-align:center;margin:14px 0 10px;pointer-events:none}
            .search.on~#chat .day{top:116px}
            .day span{display:inline-block;background:var(--svc);color:#fff;border-radius:14px;padding:4px 12px;font-size:13px;font-weight:550;-webkit-backdrop-filter:blur(10px);backdrop-filter:blur(10px);pointer-events:auto;cursor:pointer}
            .service{text-align:center;margin:10px 0}
            .service span{display:inline-block;background:var(--svc);color:#fff;border-radius:14px;padding:5px 12px;font-size:13.5px;max-width:85%;overflow-wrap:anywhere;-webkit-backdrop-filter:blur(10px);backdrop-filter:blur(10px)}
            .row{display:flex;align-items:flex-end;gap:8px;margin-top:2px;scroll-margin:120px}
            .row.first{margin-top:10px}
            .row.out{justify-content:flex-end}
            .row .gap{width:38px;flex:0 0 38px}
            .row .av{width:38px;height:38px;flex-basis:38px;font-size:14px;position:sticky;bottom:10px}
            .bubble{position:relative;max-width:min(540px,80%);min-width:76px;background:var(--in);border-radius:16px;padding:6px 10px 7px;box-shadow:var(--shadow);overflow-wrap:anywhere}
            .row.in:not(.first) .bubble{border-top-left-radius:6px}
            .row.in:not(.last) .bubble{border-bottom-left-radius:6px}
            .row.out:not(.first) .bubble{border-top-right-radius:6px}
            .row.out:not(.last) .bubble{border-bottom-right-radius:6px}
            .row.out .bubble{background:linear-gradient(160deg,var(--out1),var(--out2));color:var(--outtext)}
            .row.last .bubble:not(.bare):before{content:"";position:absolute;bottom:0;width:11px;height:18px;background:var(--in)}
            .row.out.last .bubble:not(.bare):before{background:var(--out2)}
            .row.in.last .bubble:not(.bare){border-bottom-left-radius:0}
            .row.out.last .bubble:not(.bare){border-bottom-right-radius:0}
            .row.in.last .bubble:not(.bare):before{left:-8px;-webkit-mask:url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 11 18"><path d="M11 0v18H1.5c-1.2 0-1.8-1.4-1-2.3C5.2 10.5 8.6 6.6 11 0z"/></svg>') no-repeat;mask:url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 11 18"><path d="M11 0v18H1.5c-1.2 0-1.8-1.4-1-2.3C5.2 10.5 8.6 6.6 11 0z"/></svg>') no-repeat}
            .row.out.last .bubble:not(.bare):before{right:-8px;-webkit-mask:url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 11 18"><path d="M0 0v18h9.5c1.2 0 1.8-1.4 1-2.3C5.8 10.5 2.4 6.6 0 0z"/></svg>') no-repeat;mask:url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 11 18"><path d="M0 0v18h9.5c1.2 0 1.8-1.4 1-2.3C5.8 10.5 2.4 6.6 0 0z"/></svg>') no-repeat}
            .bubble.bare{background:none!important;box-shadow:none;padding:0;min-width:0}
            .bubble.media{padding:3px}
            .bubble.media .hd{padding:4px 8px 2px}
            .bubble.media .body{padding:2px 8px 4px}
            .bubble.onmedia .body{padding:0}
            .bubble.media .reacts{margin:4px 7px 3px}
            .from{font-weight:650;font-size:14px;margin-bottom:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
            .fwd{font-size:13.5px;color:var(--link);margin-bottom:3px;display:flex;align-items:center;gap:5px}
            .fwd:before{content:"";width:14px;height:14px;flex:0 0 14px;background:currentColor;-webkit-mask:url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M14 5l7 7-7 7v-4.1C9 14.9 5.6 16.5 3 20c1-5 4-10 11-11V5z"/></svg>') center/contain no-repeat;mask:url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M14 5l7 7-7 7v-4.1C9 14.9 5.6 16.5 3 20c1-5 4-10 11-11V5z"/></svg>') center/contain no-repeat}
            .fwd b{font-weight:650}
            .row.out .fwd,.row.out a{color:var(--outlink)}
            .reply{display:block;position:relative;border-radius:8px;padding:4px 9px 4px 12px;margin:2px 0 6px;text-decoration:none!important;color:inherit;font-size:14px;overflow:hidden;background:var(--quote)}
            .reply:before{content:"";position:absolute;left:0;top:0;bottom:0;width:3px;background:var(--acc)}
            .row.out .reply{background:var(--outquote)}
            .row.out .reply:before{background:var(--outtext);opacity:.85}
            .reply b{display:block;font-weight:650;font-size:13.5px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;color:var(--acc)}
            .row.out .reply b{color:inherit}
            .reply span{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;opacity:.85}
            a.reply:hover{filter:brightness(1.12)}
            .text{display:inline}
            .stk{font-size:64px;line-height:1.15;padding:2px 4px}
            .meta{float:right;display:inline-flex;align-items:center;gap:3px;margin:7px 0 -5px 12px;font-size:12px;color:var(--muted);white-space:nowrap;user-select:none;position:relative;top:1px}
            .row.out .meta{color:var(--outmuted)}
            .tick{width:16px;height:11px;margin-left:1px}
            .bubble.bare .meta,.bubble.onmedia .meta{position:absolute;right:8px;bottom:8px;top:auto;margin:0;float:none;background:rgba(0,0,0,.42);color:#fff;border-radius:10px;padding:2px 7px;-webkit-backdrop-filter:blur(6px);backdrop-filter:blur(6px)}
            .clear{clear:both}
            img.photo,video.video{display:block;max-width:100%;max-height:480px;border-radius:13px;cursor:zoom-in;background:rgba(0,0,0,.2)}
            .bubble.media img.photo,.bubble.media video.video{width:100%;object-fit:cover}
            .row:not(.first) .bubble.media img.photo{border-top-left-radius:inherit}
            video.video{min-height:160px;background:#000;cursor:auto}
            .album{display:grid;grid-template-columns:repeat(2,1fr);gap:2px;border-radius:13px;overflow:hidden}
            .album>*{width:100%;height:100%;aspect-ratio:1;object-fit:cover;display:block;border-radius:0!important;max-height:none!important}
            .album.n1{grid-template-columns:1fr}
            .album.n1>*{aspect-ratio:auto}
            .album.n3>:first-child,.album.n5>:first-child{grid-column:span 2;aspect-ratio:2}
            .album .ph{aspect-ratio:auto;padding:16px 8px}
            .roundw{position:relative;width:240px;height:240px;cursor:pointer}
            video.round{display:block;width:240px;height:240px;border-radius:50%;object-fit:cover;background:#000;box-shadow:0 2px 10px rgba(0,0,0,.3)}
            .roundw .rp{position:absolute;inset:0;margin:auto;width:56px;height:56px;border-radius:50%;background:rgba(0,0,0,.45);-webkit-backdrop-filter:blur(6px);backdrop-filter:blur(6px);display:flex;align-items:center;justify-content:center;color:#fff;transition:opacity .2s;pointer-events:none}
            .roundw.playing .rp{opacity:0}
            .roundw .ring{position:absolute;inset:-4px;width:248px;height:248px;transform:rotate(-90deg);pointer-events:none}
            .roundw .ring circle{fill:none;stroke:#fff;stroke-width:3;stroke-linecap:round;opacity:.9;transition:stroke-dashoffset .2s linear}
            .roundw .rd{position:absolute;left:12px;bottom:8px;background:rgba(0,0,0,.42);color:#fff;font-size:12px;border-radius:10px;padding:2px 7px}
            .sticker{display:block;width:180px;height:180px;object-fit:contain}
            video.gif{display:block;max-width:100%;max-height:360px;border-radius:13px}
            audio{display:none}
            .pl{width:44px;height:44px;flex:0 0 44px;border-radius:50%;border:0;cursor:pointer;color:#fff;background:linear-gradient(150deg,var(--acc),var(--acc2));display:flex;align-items:center;justify-content:center;box-shadow:0 2px 6px rgba(0,0,0,.2);transition:transform .12s}
            .pl:active{transform:scale(.92)}
            .pl svg{width:22px;height:22px}
            .pl .ip{display:none}
            .pl.on .ip{display:block}
            .pl.on .ia{display:none}
            .row.out .pl{background:#fff;color:var(--out1)}
            html.light .row.out .pl{background:linear-gradient(150deg,#58b04a,#7ccc6a);color:#fff}
            .voice{display:flex;align-items:center;gap:10px;min-width:220px;padding:2px 0}
            .voice .vw{flex:1;min-width:0}
            .wave{display:flex;align-items:center;gap:2px;height:28px;cursor:pointer}
            .wave i{flex:1;max-width:3px;min-width:2px;border-radius:2px;background:var(--muted);opacity:.55;min-height:3px}
            .wave i.p{background:var(--acc);opacity:1}
            .row.out .wave i{background:var(--outtext);opacity:.4}
            .row.out .wave i.p{opacity:1}
            .dur{font-size:12.5px;color:var(--muted)}
            .row.out .dur{color:var(--outmuted)}
            .music{display:flex;align-items:center;gap:10px;min-width:240px;padding:3px 0}
            .music .mi{flex:1;min-width:0}
            .music .fn{font-weight:600;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
            .music .fs{font-size:13px;color:var(--muted);white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
            .row.out .music .fs{color:var(--outmuted)}
            .prog{height:4px;border-radius:2px;background:var(--quote);margin-top:6px;overflow:hidden;cursor:pointer}
            .row.out .prog{background:rgba(255,255,255,.25)}
            .prog i{display:block;height:100%;width:0;background:var(--acc);border-radius:2px}
            .row.out .prog i{background:var(--outtext)}
            .file{display:flex;align-items:center;gap:11px;color:inherit!important;text-decoration:none!important;padding:3px 2px;cursor:pointer;min-width:200px}
            .file .ic{width:46px;height:46px;border-radius:12px;background:linear-gradient(150deg,var(--acc),var(--acc2));display:flex;align-items:center;justify-content:center;font-size:20px;flex:0 0 46px;color:#fff;font-weight:700;letter-spacing:.3px}
            .file .ic.ext{font-size:12px}
            .row.out .file .ic{background:rgba(255,255,255,.22)}
            html.light .row.out .file .ic{background:linear-gradient(150deg,#58b04a,#7ccc6a)}
            .file .fn{font-weight:550;word-break:break-all;line-height:1.3}
            .file .fs{font-size:13px;color:var(--muted);margin-top:2px}
            .row.out .file .fs{color:var(--outmuted)}
            a.file[data-b] .fs:after{content:" • скачать";color:var(--acc)}
            .row.out a.file[data-b] .fs:after{color:inherit}
            .ph{color:var(--muted);font-style:italic;padding:6px 4px}
            .row.out .ph{color:var(--outmuted)}
            .ph small{display:block;font-size:12px;font-style:normal;opacity:.8}
            .wp{display:block;position:relative;background:var(--quote);border-radius:8px;padding:6px 10px 6px 13px;margin:6px 0 2px;text-decoration:none!important;color:inherit!important;font-size:14px;overflow:hidden}
            .wp:before{content:"";position:absolute;left:0;top:0;bottom:0;width:3px;background:var(--acc)}
            .row.out .wp{background:var(--outquote)}
            .row.out .wp:before{background:var(--outtext)}
            .wp b{color:var(--acc);display:block;font-weight:650}
            .row.out .wp b{color:inherit}
            .wp .wt{font-weight:650}
            .wp .wd{opacity:.85;display:-webkit-box;-webkit-line-clamp:4;-webkit-box-orient:vertical;overflow:hidden}
            .poll{min-width:240px}
            .poll b{display:block;margin-bottom:2px;font-weight:650}
            .poll .opt{margin:9px 0}
            .poll .bar{height:5px;border-radius:3px;background:linear-gradient(90deg,var(--acc),var(--acc2));margin-top:4px;min-width:5px}
            .row.out .poll .bar{background:var(--outtext)}
            .poll .pc{font-weight:650;margin-right:7px}
            .reacts{display:flex;flex-wrap:wrap;gap:5px;margin-top:6px}
            .reacts span{background:var(--quote);border-radius:14px;padding:2px 9px;font-size:13.5px;font-weight:550}
            .row.out .reacts span{background:var(--outquote)}
            a{color:var(--link);text-decoration:none}
            a:hover{text-decoration:underline}
            code,pre{font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace;font-size:13.5px}
            code{background:var(--quote);border-radius:5px;padding:1px 5px}
            pre{background:var(--quote);border-radius:9px;padding:8px 11px;white-space:pre-wrap;margin:5px 0;overflow-x:auto}
            blockquote{position:relative;background:var(--quote);border-radius:8px;margin:5px 0;padding:4px 10px 4px 13px}
            blockquote:before{content:"";position:absolute;left:0;top:0;bottom:0;width:3px;background:var(--acc)}
            .row.out code,.row.out pre,.row.out blockquote{background:var(--outquote)}
            .spoiler{background:var(--muted);color:transparent;border-radius:4px;cursor:pointer;transition:.2s}
            .spoiler.shown{background:none;color:inherit}
            .row:target .bubble,.row.hit .bubble{animation:flash 1.6s ease}
            @keyframes flash{0%,40%{box-shadow:0 0 0 3px var(--acc)}100%{box-shadow:var(--shadow)}}
            mark{background:#ffd54a;color:#000;border-radius:3px;padding:0 1px}
            #down{position:fixed;right:18px;bottom:20px;width:48px;height:48px;border-radius:50%;border:0;background:var(--panel2);color:var(--muted);box-shadow:0 3px 12px rgba(0,0,0,.3);cursor:pointer;display:flex;align-items:center;justify-content:center;z-index:6;opacity:0;transform:translateY(12px) scale(.9);pointer-events:none;transition:opacity .2s,transform .2s}
            #down.on{opacity:1;transform:none;pointer-events:auto}
            .foot{text-align:center;margin:26px 0 8px}
            .foot span{display:inline-block;background:var(--svc);color:#fff;border-radius:16px;padding:8px 14px;font-size:12.5px;line-height:1.5;-webkit-backdrop-filter:blur(10px);backdrop-filter:blur(10px)}
            .foot b{font-weight:650}
            #viewer{position:fixed;inset:0;background:rgba(6,8,14,.94);display:none;align-items:center;justify-content:center;z-index:9;-webkit-backdrop-filter:blur(8px);backdrop-filter:blur(8px)}
            #viewer.on{display:flex}
            #viewer img{max-width:94vw;max-height:88vh;border-radius:6px;box-shadow:0 10px 40px rgba(0,0,0,.5);user-select:none}
            #viewer .ib{position:absolute;color:#fff;background:rgba(255,255,255,.1);width:46px;height:46px}
            #viewer .ib:hover{background:rgba(255,255,255,.2)}
            #viewer .vx{top:14px;right:14px}
            #viewer .vd{top:14px;right:68px}
            #viewer .vp{left:14px;top:50%;margin-top:-23px}
            #viewer .vn{right:14px;top:50%;margin-top:-23px}
            #viewer .vc{position:absolute;top:24px;left:0;right:0;text-align:center;color:rgba(255,255,255,.8);font-size:14px;pointer-events:none}
            .sheet{position:fixed;inset:0;z-index:8;display:none;align-items:flex-end;justify-content:center;background:rgba(0,0,0,.45)}
            .sheet.on{display:flex}
            .sheet .sh{width:100%;max-width:520px;max-height:78vh;display:flex;flex-direction:column;background:var(--panel2);border-radius:20px 20px 0 0;box-shadow:0 -6px 30px rgba(0,0,0,.35);overflow:hidden;animation:up .22s ease}
            @keyframes up{from{transform:translateY(40px);opacity:.3}to{transform:none;opacity:1}}
            .sheet .shh{display:flex;align-items:center;justify-content:space-between;padding:14px 10px 10px 18px;font-weight:650;font-size:17px}
            .sheet .shl{overflow:auto;padding:0 12px 18px}
            .sheet .mo{font-size:13px;color:var(--muted);font-weight:650;text-transform:uppercase;letter-spacing:.5px;margin:12px 6px 6px}
            .sheet .ds{display:flex;flex-wrap:wrap;gap:6px}
            .sheet .ds button{border:0;background:var(--quote);color:var(--text);border-radius:12px;padding:7px 11px;cursor:pointer;font-size:14px;min-width:44px}
            .sheet .ds button:hover{background:var(--acc);color:#fff}
            @media (min-width:760px){.sheet{align-items:center}.sheet .sh{border-radius:20px}}
            @media (max-width:520px){.bubble{max-width:84%}.row .av{width:34px;height:34px;flex-basis:34px;font-size:13px}.row .gap{width:34px;flex-basis:34px}#chat{padding:8px 7px 90px}.top{gap:8px;padding-left:10px}.ib{width:38px;height:38px;flex-basis:38px}.roundw,video.round{width:200px;height:200px}.roundw .ring{width:208px;height:208px}#viewer .vp,#viewer .vn{display:none}}
            html.light .row.out .wave i.p,html.light .row.out .prog i{background:#4fae4e}
            html.light .row.out .wave i{background:#4fae4e;opacity:.35}
            html.light .row.out .wave i.p{opacity:1}
            """;

    private static final String JS = """
            (function(){
            var d=document,root=d.documentElement;
            function $(id){return d.getElementById(id)}
            function all(sel,el){return [].slice.call((el||d).querySelectorAll(sel))}
            function pl(n,a,b,c){var m=n%10,h=n%100;return n.toLocaleString('ru-RU')+' '+(m==1&&h!=11?a:m>=2&&m<=4&&(h<12||h>14)?b:c)}
            function fmt(s){s=Math.max(0,Math.floor(s||0));return Math.floor(s/60)+':'+('0'+s%60).slice(-2)}
            function setTheme(t){root.classList.toggle('light',t==='light');try{localStorage.setItem('tgexp-theme',t)}catch(e){}}
            var saved=null;try{saved=localStorage.getItem('tgexp-theme')}catch(e){}
            if(saved){setTheme(saved)}else if(window.matchMedia&&matchMedia('(prefers-color-scheme: light)').matches){setTheme('light')}
            $('tbtn').onclick=function(){setTheme(root.classList.contains('light')?'dark':'light')};
            d.addEventListener('click',function(e){var t=e.target;while(t&&t.classList){if(t.classList.contains('spoiler')){t.classList.add('shown');return}t=t.parentNode}});
            function nextRow(r){var n=r.nextElementSibling;while(n&&n.tagName==='SCRIPT')n=n.nextElementSibling;return n}
            var rows=all('.row');
            rows.forEach(function(r){var n=nextRow(r);if(!n||!n.classList.contains('row')||n.classList.contains('first')||n.classList.contains('out')!==r.classList.contains('out'))r.classList.add('last')});
            all('.row.first').forEach(function(r){var av=r.querySelector(':scope>.av');if(!av||r.classList.contains('last'))return;var e=r,n;
            while(!e.classList.contains('last')){n=nextRow(e);if(!n||!n.classList.contains('row'))break;e=n}
            var g=e.querySelector(':scope>.gap');if(e!==r&&g){var g2=d.createElement('div');g2.className='gap';r.replaceChild(g2,av);e.replaceChild(av,g)}});
            var foot=d.querySelector('.foot'),days=all('.day');
            var nm=foot?+(foot.getAttribute('data-m')||0):rows.length,nf=foot?+(foot.getAttribute('data-f')||0):0;
            function chip(box,text,bold,first){var s=d.createElement('span');if(bold){var b=d.createElement('b');b.textContent=text;s.appendChild(b)}else{s.textContent=text}if(first&&box.firstChild){box.insertBefore(s,box.firstChild)}else{box.appendChild(s)}return s}
            var st=$('stats');
            if(st){var ref=st.firstChild;var tmp=d.createElement('div');chip(tmp,pl(nm,'сообщение','сообщения','сообщений'),true);if(nf)chip(tmp,pl(nf,'медиафайл','медиафайла','медиафайлов'));
            if(days.length){var da=days[0].textContent.trim(),db=days[days.length-1].textContent.trim();chip(tmp,da===db?da:da+' — '+db)}
            while(tmp.firstChild)st.insertBefore(tmp.firstChild,ref)}
            var hs=$('hsub');if(hs&&hs.getAttribute('data-k'))hs.textContent=hs.getAttribute('data-k')+' • '+pl(nm,'сообщение','сообщения','сообщений');
            var cache={};
            function blob(id){if(cache[id])return Promise.resolve(cache[id]);var s=$(id);if(!s)return Promise.reject('no data');
            var mime=s.getAttribute('data-mime')||'application/octet-stream';var b64=s.textContent.trim();
            return fetch('data:'+mime+';base64,'+b64).then(function(r){return r.blob()}).catch(function(){var bin=atob(b64),u=new Uint8Array(bin.length);for(var i=0;i<bin.length;i++)u[i]=bin.charCodeAt(i);return new Blob([u],{type:mime})}).then(function(b){cache[id]=URL.createObjectURL(b);return cache[id]})}
            function load(el){if(el._l)return Promise.resolve();el._l=1;return blob(el.getAttribute('data-b')).then(function(u){if(el.tagName==='A'){el.href=u}else{if(el.tagName==='VIDEO')el.preload='metadata';el.src=u;if(el.autoplay&&el.play){var p=el.play();if(p&&p.catch)p.catch(function(){})}}})}
            var lazy=all('video[data-b],img[data-b]');
            if('IntersectionObserver' in window){var io=new IntersectionObserver(function(es){es.forEach(function(en){if(en.isIntersecting){io.unobserve(en.target);load(en.target)}})},{rootMargin:'800px 0px'});lazy.forEach(function(el){io.observe(el)})}else{lazy.forEach(load)}
            all('a[data-b]').forEach(function(a){a.addEventListener('click',function(e){if(a._l&&a.href&&a.href.indexOf('blob:')===0)return;e.preventDefault();blob(a.getAttribute('data-b')).then(function(u){a._l=1;a.href=u;a.click()})})});
            var cur=null;
            function playEl(m){if(cur&&cur!==m){try{cur.pause()}catch(e){}}cur=m;var go=function(){var p=m.play();if(p&&p.catch)p.catch(function(){})};if(m._l&&m.src){go()}else{load(m).then(go)}}
            function waveOf(box){var w=box.querySelector('.wave');if(!w)return;var s=box.getAttribute('data-wf'),v=[];
            if(s){try{var bin=atob(s),n=Math.floor(bin.length*8/5);for(var i=0;i<n;i++){var by=Math.floor(i*5/8),bit=i*5%8;v.push(((bin.charCodeAt(by)|((by+1<bin.length?bin.charCodeAt(by+1):0)<<8))>>bit)&31)}}catch(e){v=[]}}
            if(!v.length||Math.max.apply(null,v)===0){v=[];var au=box.querySelector('audio'),id=au?au.getAttribute('data-b'):'x',seed=7;for(var j=0;j<id.length;j++)seed=(seed*31+id.charCodeAt(j))|0;for(var k=0;k<60;k++){seed=(seed*1103515245+12345)|0;v.push(4+((seed>>>16)%26))}}
            var count=Math.max(20,Math.min(56,Math.round((w.clientWidth||180)/5))),out=[];
            for(var c=0;c<count;c++){var a=Math.floor(c*v.length/count),b=Math.max(a+1,Math.floor((c+1)*v.length/count)),mx=0;for(var q=a;q<b&&q<v.length;q++)mx=Math.max(mx,v[q]);out.push(mx)}
            var top=Math.max.apply(null,out)||1;w.innerHTML='';out.forEach(function(x){var i=d.createElement('i');i.style.height=Math.max(14,Math.round(x/top*100))+'%';w.appendChild(i)})}
            all('.voice,.music').forEach(function(box){var au=box.querySelector('audio'),btn=box.querySelector('.pl');if(!au||!btn)return;waveOf(box);
            var dur=box.querySelector('.dur'),total=dur?dur.textContent:'',bars=all('.wave i',box),prog=box.querySelector('.prog i');
            function upd(){var p=au.duration&&isFinite(au.duration)?au.currentTime/au.duration:0;if(bars.length){var k=Math.round(p*bars.length);for(var i=0;i<bars.length;i++)bars[i].classList.toggle('p',i<k)}if(prog)prog.style.width=(p*100)+'%';if(dur&&box.classList.contains('voice'))dur.textContent=au.currentTime>0&&!au.ended?fmt(au.currentTime)+' / '+total:total}
            au.addEventListener('timeupdate',upd);
            au.addEventListener('play',function(){btn.classList.add('on')});au.addEventListener('pause',function(){btn.classList.remove('on')});
            au.addEventListener('ended',function(){btn.classList.remove('on');try{au.currentTime=0}catch(e){}upd()});
            btn.onclick=function(){if(!au.paused){au.pause()}else{btn.classList.add('on');playEl(au)}};
            var sk=box.querySelector('.wave')||box.querySelector('.prog');
            if(sk)sk.addEventListener('click',function(e){var r=sk.getBoundingClientRect(),p=Math.min(1,Math.max(0,(e.clientX-r.left)/r.width));
            var set=function(){if(au.duration&&isFinite(au.duration)){au.currentTime=p*au.duration;upd()}};if(au.readyState>0){set();if(au.paused)playEl(au)}else{au.addEventListener('loadedmetadata',function f(){au.removeEventListener('loadedmetadata',f);set()});playEl(au)}})});
            all('.roundw').forEach(function(r){var v=r.querySelector('video'),c=r.querySelector('.ring circle'),L=c?2*Math.PI*c.r.baseVal.value:0;if(c){c.style.strokeDasharray=L;c.style.strokeDashoffset=L}
            r.onclick=function(){if(!v.paused){v.pause()}else{v.muted=false;playEl(v)}};
            v.addEventListener('play',function(){r.classList.add('playing')});v.addEventListener('pause',function(){r.classList.remove('playing')});
            v.addEventListener('timeupdate',function(){if(c&&v.duration)c.style.strokeDashoffset=L*(1-v.currentTime/v.duration)});v.addEventListener('ended',function(){if(c)c.style.strokeDashoffset=L})});
            all('video.video').forEach(function(v){v.addEventListener('play',function(){if(cur&&cur!==v){try{cur.pause()}catch(e){}}cur=v})});
            var viewer=$('viewer'),vimg=viewer.querySelector('img'),vc=viewer.querySelector('.vc'),vd=viewer.querySelector('.vd'),vi=-1,photos=[],swiped=false;
            function show(i){photos=all('img.photo');if(!photos.length)return;vi=(i+photos.length)%photos.length;vimg.src=photos[vi].src;vc.textContent=(vi+1)+' / '+photos.length;vd.href=photos[vi].src;viewer.classList.add('on')}
            function hide(){viewer.classList.remove('on');vimg.removeAttribute('src')}
            d.addEventListener('click',function(e){var t=e.target;if(t.tagName==='IMG'&&t.classList.contains('photo')&&!viewer.contains(t)){show(all('img.photo').indexOf(t))}});
            viewer.onclick=function(e){if(swiped){swiped=false;return}if(e.target===viewer)hide()};
            viewer.querySelector('.vx').onclick=hide;
            viewer.querySelector('.vp').onclick=function(e){e.stopPropagation();show(vi-1)};
            viewer.querySelector('.vn').onclick=function(e){e.stopPropagation();show(vi+1)};
            vd.onclick=function(e){e.stopPropagation()};
            var sx=0;viewer.addEventListener('touchstart',function(e){sx=e.touches[0].clientX},{passive:true});
            viewer.addEventListener('touchend',function(e){var dx=e.changedTouches[0].clientX-sx;if(Math.abs(dx)>50){swiped=true;show(vi+(dx<0?1:-1));setTimeout(function(){swiped=false},350)}},{passive:true});
            var sheet=$('dates'),shl=sheet.querySelector('.shl'),cbtn=$('cbtn');
            var MON={'января':'Январь','февраля':'Февраль','марта':'Март','апреля':'Апрель','мая':'Май','июня':'Июнь','июля':'Июль','августа':'Август','сентября':'Сентябрь','октября':'Октябрь','ноября':'Ноябрь','декабря':'Декабрь'};
            function jump(el){var y=el.getBoundingClientRect().top+window.pageYOffset-70;window.scrollTo({top:y,behavior:'smooth'})}
            function closeDates(){sheet.classList.remove('on')}
            function openDates(){if(!shl.firstChild){var lastKey='',box=null;days.forEach(function(dy){var t=dy.textContent.trim().split(' ');var key=(MON[t[1]]||t[1]||'')+' '+(t[2]||'');
            if(key!==lastKey){lastKey=key;var h=d.createElement('div');h.className='mo';h.textContent=key;shl.appendChild(h);box=d.createElement('div');box.className='ds';shl.appendChild(box)}
            var b=d.createElement('button');b.textContent=t[0];b.onclick=function(){closeDates();jump(dy)};box.appendChild(b)})}sheet.classList.add('on')}
            if(days.length<2){cbtn.style.display='none'}else{cbtn.onclick=openDates;days.forEach(function(dy){dy.querySelector('span').onclick=openDates})}
            sheet.onclick=function(e){if(e.target===sheet)closeDates()};sheet.querySelector('.ib').onclick=closeDates;
            var down=$('down');
            function onScroll(){var h=d.documentElement;down.classList.toggle('on',h.scrollHeight-h.scrollTop-h.clientHeight>900)}
            window.addEventListener('scroll',onScroll,{passive:true});onScroll();
            down.onclick=function(){window.scrollTo({top:d.documentElement.scrollHeight,behavior:'smooth'})};
            var bar=$('sbar'),q=$('q'),qc=$('qc'),sbtn=$('sbtn'),hits=[],hi=-1,timer=0;
            function closeSearch(){bar.classList.remove('on');sbtn.classList.remove('on');q.value='';run()}
            sbtn.onclick=function(){if(bar.classList.contains('on')){closeSearch()}else{bar.classList.add('on');sbtn.classList.add('on');q.focus()}};
            $('qx').onclick=closeSearch;
            function clearMarks(){all('mark').forEach(function(m){var p=m.parentNode;p.replaceChild(d.createTextNode(m.textContent),m);p.normalize()})}
            function markIn(el,needle){var w=d.createTreeWalker(el,NodeFilter.SHOW_TEXT,null),nodes=[],n;while((n=w.nextNode()))nodes.push(n);
            nodes.forEach(function(node){var s=node.nodeValue,l=s.toLowerCase(),i=l.indexOf(needle);if(i<0)return;var f=d.createDocumentFragment(),pos=0;while(i>=0){f.appendChild(d.createTextNode(s.slice(pos,i)));var m=d.createElement('mark');m.textContent=s.slice(i,i+needle.length);f.appendChild(m);pos=i+needle.length;i=l.indexOf(needle,pos)}f.appendChild(d.createTextNode(s.slice(pos)));node.parentNode.replaceChild(f,node)})}
            function go(i){if(!hits.length){qc.textContent=q.value.trim().length>1?'0':'';return}hi=(i+hits.length)%hits.length;qc.textContent=(hi+1)+' / '+hits.length;var r=hits[hi];all('.row.hit').forEach(function(x){x.classList.remove('hit')});void r.offsetWidth;r.classList.add('hit');r.scrollIntoView({block:'center'})}
            function run(){clearMarks();hits=[];hi=-1;var needle=q.value.trim().toLowerCase();if(needle.length<2){qc.textContent='';return}
            rows.forEach(function(r){var found=false;all('.text,.fn',r).forEach(function(t){if(t.textContent.toLowerCase().indexOf(needle)>=0){found=true;markIn(t,needle)}});if(found)hits.push(r)});go(hits.length-1)}
            q.addEventListener('input',function(){clearTimeout(timer);timer=setTimeout(run,250)});
            q.addEventListener('keydown',function(e){if(e.key==='Enter'){e.preventDefault();go(hi+(e.shiftKey?1:-1))}});
            $('qp').onclick=function(){go(hi-1)};$('qn').onclick=function(){go(hi+1)};
            d.addEventListener('keydown',function(e){if(e.key==='Escape'){if(viewer.classList.contains('on'))hide();else if(sheet.classList.contains('on'))closeDates();else if(bar.classList.contains('on'))closeSearch()}
            else if(viewer.classList.contains('on')&&(e.key==='ArrowLeft'||e.key==='ArrowRight')){show(vi+(e.key==='ArrowLeft'?-1:1))}
            else if((e.ctrlKey||e.metaKey)&&e.key==='f'&&!bar.classList.contains('on')){e.preventDefault();sbtn.onclick()}});
            })();
            """;

    // ---------------- иконки ----------------

    private static String icon(String path, int size, String width) {
        return "<svg viewBox=\"0 0 24 24\" width=\"" + size + "\" height=\"" + size + "\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"" + width
                + "\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><path d=\"" + path + "\"/></svg>";
    }

    private static final String SVG_SEARCH = "<svg viewBox=\"0 0 24 24\" width=\"22\" height=\"22\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\"><circle cx=\"11\" cy=\"11\" r=\"7\"/><path d=\"M20 20l-3.5-3.5\"/></svg>";
    private static final String SVG_SEARCH_SMALL = SVG_SEARCH.replace("width=\"22\" height=\"22\"", "width=\"18\" height=\"18\"");
    private static final String SVG_CALENDAR = "<svg viewBox=\"0 0 24 24\" width=\"22\" height=\"22\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><rect x=\"3.5\" y=\"5\" width=\"17\" height=\"15\" rx=\"3\"/><path d=\"M3.5 10h17M8 3v4M16 3v4\"/></svg>";
    private static final String SVG_MOON = "<svg viewBox=\"0 0 24 24\" width=\"21\" height=\"21\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linejoin=\"round\"><path d=\"M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z\"/></svg>";
    private static final String SVG_UP = icon("M6 15l6-6 6 6", 20, "2.2");
    private static final String SVG_DOWN = icon("M6 9l6 6 6-6", 20, "2.2");
    private static final String SVG_CLOSE = icon("M6 6l12 12M18 6L6 18", 20, "2.2");
    private static final String SVG_CLOSE_BIG = icon("M6 6l12 12M18 6L6 18", 24, "2.2");
    private static final String SVG_DOWNLOAD = icon("M12 4v11M7 10l5 5 5-5M5 20h14", 22, "2.2");
    private static final String SVG_PREV = icon("M15 6l-6 6 6 6", 24, "2.2");
    private static final String SVG_NEXT = icon("M9 6l6 6-6 6", 24, "2.2");
    private static final String SVG_TO_BOTTOM = icon("M6 9l6 6 6-6", 24, "2.2");
    private static final String SVG_PLAY_ONLY = "<svg viewBox=\"0 0 24 24\" width=\"26\" height=\"26\"><path d=\"M8 5.5v13a1 1 0 0 0 1.5.86l10.2-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5z\" fill=\"currentColor\"/></svg>";
    private static final String SVG_PLAY_PAUSE = "<svg class=\"ia\" viewBox=\"0 0 24 24\"><path d=\"M8 5.5v13a1 1 0 0 0 1.5.86l10.2-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5z\" fill=\"currentColor\"/></svg>"
            + "<svg class=\"ip\" viewBox=\"0 0 24 24\"><rect x=\"6.5\" y=\"5\" width=\"4\" height=\"14\" rx=\"1.3\" fill=\"currentColor\"/><rect x=\"13.5\" y=\"5\" width=\"4\" height=\"14\" rx=\"1.3\" fill=\"currentColor\"/></svg>";
    private static final String SVG_TICKS = "<svg class=\"tick\" viewBox=\"0 0 16 11\"><path d=\"M1 6l3 3.2L10.2 2.5M6.6 9.2l.8.8L14.6 2.5\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"1.6\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/></svg>";
    private static final String SVG_RING = "<svg class=\"ring\" viewBox=\"0 0 248 248\"><circle cx=\"124\" cy=\"124\" r=\"120\"/></svg>";

    // ---------------- вывод ----------------

    /** Кусок html, внутри которого могут встречаться встроенные файлы (пишутся потоково, без загрузки в память). */
    private static final class Embed {
        final File file;

        Embed(File file) {
            this.file = file;
        }
    }

    private final class Html {
        private final ArrayList<Object> parts = new ArrayList<>();
        private final StringBuilder sb = new StringBuilder(1024);

        Html a(Object o) {
            sb.append(o);
            return this;
        }

        Html embed(File f) {
            if (sb.length() > 0) {
                parts.add(sb.toString());
                sb.setLength(0);
            }
            parts.add(new Embed(f));
            return this;
        }

        void flush() throws IOException {
            for (Object p : parts) {
                if (p instanceof Embed) {
                    writeBase64(((Embed) p).file);
                } else {
                    w((String) p);
                }
            }
            parts.clear();
            if (sb.length() > 0) {
                w(sb.toString());
                sb.setLength(0);
            }
        }
    }

    private void w(String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private void writeBase64(File f) throws IOException {
        OutputStream keep = new FilterOutputStream(out) {
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                out.write(b, off, len);
            }

            @Override
            public void close() throws IOException {
                flush();
            }
        };
        Base64OutputStream b64 = new Base64OutputStream(keep, Base64.NO_WRAP);
        try (InputStream in = new BufferedInputStream(new FileInputStream(f), 64 * 1024)) {
            byte[] buf = new byte[48 * 1024];
            int r;
            while ((r = in.read(buf)) > 0) {
                b64.write(buf, 0, r);
            }
        }
        b64.close();
    }

    private void writeHeader() throws IOException {
        Html h = new Html();
        h.a("<!DOCTYPE html><html lang=\"ru\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><meta name=\"color-scheme\" content=\"dark light\"><title>")
                .a(esc(title)).a("</title><style>").a(CSS).a("</style></head><body>\n");
        String kind;
        if (dialogId > 0) {
            TLRPC.User u = MessagesController.getInstance(account).getUser(dialogId);
            kind = u != null && u.bot ? "бот" : "личный чат";
        } else {
            kind = channel ? "канал" : "группа";
        }
        String exportedAt = fullFormat.format(new Date());
        h.a("<header class=\"top\">");
        avatar(h, dialogId, title, true);
        h.a("<div class=\"ttl\"><div class=\"name\">").a(esc(title)).a("</div><div class=\"sub\" id=\"hsub\" data-k=\"").a(kind).a("\">").a(kind)
                .a(" • экспорт от ").a(esc(exportedAt)).a("</div></div>")
                .a("<button class=\"ib\" id=\"cbtn\" title=\"Перейти к дате\" aria-label=\"Перейти к дате\">").a(SVG_CALENDAR).a("</button>")
                .a("<button class=\"ib\" id=\"sbtn\" title=\"Поиск\" aria-label=\"Поиск\">").a(SVG_SEARCH).a("</button>")
                .a("<button class=\"ib\" id=\"tbtn\" title=\"Светлая / тёмная тема\" aria-label=\"Тема\">").a(SVG_MOON).a("</button></header>\n")
                .a("<div class=\"search\" id=\"sbar\"><div class=\"sw\">").a(SVG_SEARCH_SMALL).a("<input id=\"q\" type=\"search\" placeholder=\"Поиск по сообщениям\" autocomplete=\"off\"></div>")
                .a("<span id=\"qc\"></span><button class=\"ib\" id=\"qp\" title=\"Раньше\">").a(SVG_UP).a("</button><button class=\"ib\" id=\"qn\" title=\"Позже\">").a(SVG_DOWN)
                .a("</button><button class=\"ib\" id=\"qx\" title=\"Закрыть\">").a(SVG_CLOSE).a("</button></div>\n")
                .a("<main id=\"chat\">\n<section class=\"intro\">");
        avatar(h, dialogId, title, true);
        h.a("<h1>").a(esc(title)).a("</h1><p>").a(kind).a("</p><div class=\"chips\" id=\"stats\"><span>Экспорт от ")
                .a(esc(exportedAt.length() > 10 ? exportedAt.substring(0, 10) : exportedAt)).a("</span></div></section>\n");
        h.flush();
    }

    private void writeFooter(boolean partial) throws IOException {
        StringBuilder f = new StringBuilder();
        f.append("<div class=\"foot\" data-m=\"").append(messagesCount).append("\" data-f=\"").append(filesCount)
                .append("\"><span>Сообщений: ").append(messagesCount).append(" • медиафайлов: ").append(filesCount);
        if (skippedCount > 0) f.append(" • не влезло в файл: ").append(skippedCount);
        if (failedCount > 0) f.append(" • не скачалось: ").append(failedCount);
        if (cancelled || partial) f.append(" • экспорт неполный");
        f.append("<br>Экспортировано в <b>TsundereGram</b></span></div>\n</main>\n");
        f.append("<div id=\"viewer\"><img alt=\"\"><div class=\"vc\"></div><a class=\"ib vd\" download=\"photo.jpg\" title=\"Скачать\">").append(SVG_DOWNLOAD)
                .append("</a><button class=\"ib vx\" title=\"Закрыть\">").append(SVG_CLOSE_BIG).append("</button><button class=\"ib vp\" title=\"Назад\">").append(SVG_PREV)
                .append("</button><button class=\"ib vn\" title=\"Вперёд\">").append(SVG_NEXT).append("</button></div>\n");
        f.append("<div class=\"sheet\" id=\"dates\"><div class=\"sh\"><div class=\"shh\">Перейти к дате<button class=\"ib\" title=\"Закрыть\">").append(SVG_CLOSE)
                .append("</button></div><div class=\"shl\"></div></div></div>\n");
        f.append("<button id=\"down\" title=\"Вниз\">").append(SVG_TO_BOTTOM).append("</button>\n");
        f.append("<script>").append(JS).append("</script>\n</body></html>\n");
        w(f.toString());
    }

    // ---------------- люди и аватарки ----------------

    private TLObject peerObject(long id) {
        if (id > 0) {
            TLRPC.User u = users.get(id);
            if (u == null) u = MessagesController.getInstance(account).getUser(id);
            return u;
        } else if (id < 0) {
            TLRPC.Chat c = chats.get(-id);
            if (c == null) c = MessagesController.getInstance(account).getChat(-id);
            return c;
        }
        return null;
    }

    private String peerName(long id) {
        TLObject o = peerObject(id);
        if (o instanceof TLRPC.User) return UserObject.getUserName((TLRPC.User) o);
        if (o instanceof TLRPC.Chat && ((TLRPC.Chat) o).title != null) return ((TLRPC.Chat) o).title;
        return id != 0 ? "ID " + Math.abs(id) : "";
    }

    private static long peerId(TLRPC.Peer p) {
        if (p == null) return 0;
        if (p.user_id != 0) return p.user_id;
        if (p.chat_id != 0) return -p.chat_id;
        if (p.channel_id != 0) return -p.channel_id;
        return 0;
    }

    private static String peerColor(long id) {
        return PEER_COLORS[(int) (Math.abs(id) % PEER_COLORS.length)];
    }

    private static String cssId(long id) {
        return id < 0 ? "pc" + (-id) : "pu" + id;
    }

    private static String initials(String name) {
        if (TextUtils.isEmpty(name)) return "?";
        String[] words = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            int cp = word.codePointAt(0);
            if (!Character.isLetterOrDigit(cp)) continue;
            sb.appendCodePoint(Character.toUpperCase(cp));
            if (sb.length() >= 2 || words.length == 1) break;
        }
        return sb.length() == 0 ? name.substring(0, name.offsetByCodePoints(0, 1)) : sb.toString();
    }

    private void avatar(Html h, long id, String name, boolean header) {
        String cls = cssId(id);
        boolean hasPhoto = avatarStyles.contains(id);
        if (!hasPhoto && !avatarTried.contains(id) && avatarTried.size() < MAX_AVATARS) {
            avatarTried.add(id);
            File f = loadAvatar(id);
            if (f != null) {
                h.a("<style>.").a(cls).a("{background-image:url(data:image/jpeg;base64,").embed(f).a(")}</style>");
                avatarStyles.add(id);
                hasPhoto = true;
            }
        }
        h.a("<div class=\"av ").a(cls).a("\" style=\"background-color:").a(peerColor(id)).a("\">");
        if (!hasPhoto) h.a(esc(initials(name)));
        h.a("</div>");
    }

    /** Маленькая аватарка: из кеша, а если её там нет — быстро докачиваем (не дольше нескольких секунд). */
    private File loadAvatar(long id) {
        try {
            TLObject peer = peerObject(id);
            TLRPC.FileLocation small = null;
            if (peer instanceof TLRPC.User && ((TLRPC.User) peer).photo != null) {
                small = ((TLRPC.User) peer).photo.photo_small;
            } else if (peer instanceof TLRPC.Chat && ((TLRPC.Chat) peer).photo != null) {
                small = ((TLRPC.Chat) peer).photo.photo_small;
            }
            if (small == null) return null;
            FileLoader fl = FileLoader.getInstance(account);
            File f = fl.getPathToAttach(small, true);
            if (f != null && f.exists() && f.length() > 0) return f;
            if (avatarWaitLeft <= 0) return null;
            ImageLocation loc = ImageLocation.getForUserOrChat(peer, ImageLocation.TYPE_SMALL);
            if (loc == null) return null;
            fl.loadFile(loc, peer, null, FileLoader.PRIORITY_NORMAL, 1);
            long started = System.currentTimeMillis();
            long deadline = started + Math.min(6000, avatarWaitLeft);
            while (!cancelled && System.currentTimeMillis() < deadline) {
                Thread.sleep(150);
                if (f != null && f.exists() && f.length() > 0) break;
                f = fl.getPathToAttach(small, true);
            }
            avatarWaitLeft -= System.currentTimeMillis() - started;
            return f != null && f.exists() && f.length() > 0 ? f : null;
        } catch (InterruptedException e) {
            cancelled = true;
            return null;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    // ---------------- сообщения ----------------

    private void handleMessage(TLRPC.Message m) throws IOException {
        if (m.grouped_id != 0 && !(m instanceof TLRPC.TL_messageService)) {
            if (!album.isEmpty() && album.get(0).grouped_id != m.grouped_id) flushAlbum();
            album.add(m);
            if (album.size() >= 10) flushAlbum();
            return;
        }
        flushAlbum();
        ArrayList<TLRPC.Message> one = new ArrayList<>(1);
        one.add(m);
        writeMessage(one);
    }

    private void flushAlbum() throws IOException {
        if (album.isEmpty()) return;
        ArrayList<TLRPC.Message> list = new ArrayList<>(album);
        album.clear();
        writeMessage(list);
    }

    private MessageObject toObject(TLRPC.Message m) {
        try {
            return new MessageObject(account, m, users, chats, false, false);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    private boolean isVisual(MessageObject mo) {
        if (mo == null) return false;
        TLRPC.MessageMedia media = mo.messageOwner.media;
        if (media instanceof TLRPC.TL_messageMediaPhoto) return true;
        return media instanceof TLRPC.TL_messageMediaDocument && (mo.isVideo() || mo.isGif()) && !mo.isRoundVideo();
    }

    private void writeMessage(ArrayList<TLRPC.Message> group) throws IOException {
        TLRPC.Message first = group.get(0);
        TLRPC.Message last = group.get(group.size() - 1);
        Html h = new Html();
        Date date = new Date(last.date * 1000L);
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(first.date * 1000L);
        int dayKey = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR);
        if (dayKey != lastDayKey) {
            lastDayKey = dayKey;
            lastFromId = Long.MIN_VALUE;
            h.a("<div class=\"day\"><span>").a(esc(dayFormat.format(new Date(first.date * 1000L)))).a("</span></div>\n");
        }
        if (first instanceof TLRPC.TL_messageService) {
            MessageObject mo = toObject(first);
            CharSequence text = mo != null ? mo.messageText : null;
            h.a("<div class=\"service\" id=\"m").a(first.id).a("\"><span>").a(esc(text != null ? text.toString() : "Служебное сообщение")).a("</span></div>\n");
            lastFromId = Long.MIN_VALUE;
            h.flush();
            return;
        }
        ArrayList<MessageObject> objects = new ArrayList<>(group.size());
        boolean allVisual = group.size() > 1;
        for (TLRPC.Message m : group) {
            MessageObject mo = toObject(m);
            objects.add(mo);
            if (!isVisual(mo)) allVisual = false;
        }
        long fromId = peerId(first.from_id);
        if (fromId == 0) fromId = peerId(first.peer_id);
        boolean out = first.out && !channel;
        boolean firstInGroup = fromId != lastFromId || first.date - lastFromDate > 300;
        lastFromId = fromId;
        lastFromDate = last.date;
        boolean withAvatar = isGroup && !out;

        TLRPC.Message captionMsg = null;
        for (TLRPC.Message m : group) {
            if (!TextUtils.isEmpty(m.message)) {
                captionMsg = m;
                break;
            }
        }
        MessageObject single = group.size() == 1 ? objects.get(0) : null;
        boolean sticker = single != null && single.messageOwner.media instanceof TLRPC.TL_messageMediaDocument
                && (single.isSticker() || single.isAnimatedSticker() || single.isRoundVideo()) && captionMsg == null;
        boolean visual = allVisual || isVisual(single);
        boolean onMedia = visual && captionMsg == null;

        String name = peerName(fromId);
        h.a("<div class=\"row ").a(out ? "out" : "in").a(firstInGroup ? " first" : "").a("\" id=\"m").a(first.id).a("\">");
        if (withAvatar) {
            if (firstInGroup) {
                avatar(h, fromId, name, false);
            } else {
                h.a("<div class=\"gap\"></div>");
            }
        }
        h.a("<div class=\"bubble").a(sticker ? " bare" : visual ? " media" : "").a(onMedia ? " onmedia" : "").a("\">");

        StringBuilder head = new StringBuilder();
        if (withAvatar && firstInGroup && !sticker) {
            head.append("<div class=\"from\" style=\"color:").append(peerColor(fromId)).append("\">").append(esc(name)).append("</div>");
        }
        if (first.fwd_from != null) {
            String fwd = !TextUtils.isEmpty(first.fwd_from.from_name) ? first.fwd_from.from_name : peerName(peerId(first.fwd_from.from_id));
            head.append("<div class=\"fwd\">Переслано от <b>").append(esc(TextUtils.isEmpty(fwd) ? "скрытого пользователя" : fwd)).append("</b></div>");
        }
        appendReply(head, first);
        if (head.length() > 0) h.a("<div class=\"hd\">").a(head).a("</div>");

        if (allVisual) {
            h.a("<div class=\"album n").a(Math.min(group.size(), 6)).a("\">");
            for (int i = 0; i < group.size(); i++) {
                appendMedia(h, objects.get(i), group.get(i), true);
            }
            h.a("</div>");
        } else {
            for (int i = 0; i < group.size(); i++) {
                MessageObject mo = objects.get(i);
                TLRPC.Message m = group.get(i);
                if (mo != null) {
                    appendMedia(h, mo, m, false);
                } else if (m.media != null && !(m.media instanceof TLRPC.TL_messageMediaEmpty) && !(m.media instanceof TLRPC.TL_messageMediaWebPage)) {
                    h.a("<div class=\"ph\">📎 Вложение</div>");
                }
            }
        }

        h.a("<div class=\"body\">");
        if (captionMsg != null) {
            h.a("<div class=\"text\">").a(textToHtml(captionMsg.message, captionMsg.entities)).a("</div>");
        }
        if (single != null && first.media instanceof TLRPC.TL_messageMediaWebPage) {
            appendWebPage(h, first.media.webpage);
        }
        h.a("<span class=\"meta\" title=\"").a(esc(fullFormat.format(date))).a("\">");
        if (channel && last.views > 0) h.a("&#128065;&#xFE0E; ").a(esc(shortCount(last.views))).a(" &nbsp;");
        if (last.edit_date != 0 && !last.edit_hide) h.a("изменено ");
        if (channel && !TextUtils.isEmpty(last.post_author)) h.a(esc(last.post_author)).a(", ");
        h.a(esc(timeFormat.format(date)));
        if (out) h.a(" ").a(SVG_TICKS);
        h.a("</span><div class=\"clear\"></div></div>");
        appendReactions(h, last.reactions != null ? last : first);
        h.a("</div></div>\n");
        emitBlobs(h);
        h.flush();

        // для цитат в ответах
        String snippet = captionMsg != null ? captionMsg.message : mediaLabel(objects.get(0), first);
        if (snippet == null) snippet = "";
        snippet = snippet.replace('\n', ' ');
        if (snippet.length() > 90) snippet = snippet.substring(0, 90) + "…";
        String[] preview = new String[]{out && !isGroup ? "Вы" : name, snippet};
        for (TLRPC.Message m : group) {
            replyPreview.put(m.id, preview);
        }
    }

    private void appendReply(StringBuilder sb, TLRPC.Message m) {
        if (!(m.reply_to instanceof TLRPC.TL_messageReplyHeader) || m.reply_to.reply_to_msg_id == 0) return;
        TLRPC.MessageReplyHeader r = m.reply_to;
        long replyPeer = peerId(r.reply_to_peer_id);
        String[] p = replyPeer == 0 || replyPeer == dialogId ? replyPreview.get(r.reply_to_msg_id) : null;
        String who = p != null ? p[0] : replyPeer != 0 && replyPeer != dialogId ? peerName(replyPeer) : "Ответ";
        String what = !TextUtils.isEmpty(r.quote_text) ? r.quote_text : p != null ? p[1] : "Сообщение";
        if (what.length() > 90) what = what.substring(0, 90) + "…";
        if (p != null) {
            sb.append("<a class=\"reply\" href=\"#m").append(r.reply_to_msg_id).append("\">");
        } else {
            sb.append("<div class=\"reply\">");
        }
        sb.append("<b>").append(esc(who)).append("</b><span>").append(esc(what.replace('\n', ' '))).append("</span>");
        sb.append(p != null ? "</a>" : "</div>");
    }

    private void appendWebPage(Html h, TLRPC.WebPage wp) {
        if (wp == null || (TextUtils.isEmpty(wp.title) && TextUtils.isEmpty(wp.description) && TextUtils.isEmpty(wp.site_name))) return;
        h.a("<a class=\"wp\" href=\"").a(attr(wp.url)).a("\" target=\"_blank\" rel=\"noopener\">");
        if (!TextUtils.isEmpty(wp.site_name)) h.a("<b>").a(esc(wp.site_name)).a("</b>");
        if (!TextUtils.isEmpty(wp.title)) h.a("<div class=\"wt\">").a(esc(wp.title)).a("</div>");
        if (!TextUtils.isEmpty(wp.description)) h.a("<div class=\"wd\">").a(esc(wp.description)).a("</div>");
        h.a("</a>");
    }

    private void appendReactions(Html h, TLRPC.Message m) {
        if (m.reactions == null || m.reactions.results == null || m.reactions.results.isEmpty()) return;
        h.a("<div class=\"reacts\">");
        for (TLRPC.ReactionCount rc : m.reactions.results) {
            String e;
            if (rc.reaction instanceof TLRPC.TL_reactionEmoji) {
                e = ((TLRPC.TL_reactionEmoji) rc.reaction).emoticon;
            } else if (rc.reaction instanceof TLRPC.TL_reactionPaid) {
                e = "⭐";
            } else {
                e = "✨";
            }
            h.a("<span>").a(esc(e)).a(" ").a(rc.count).a("</span>");
        }
        h.a("</div>");
    }

    private static String shortCount(int n) {
        if (n >= 1_000_000) return String.format(Locale.US, "%.1fM", n / 1_000_000f).replace(".0M", "M");
        if (n >= 1000) return String.format(Locale.US, "%.1fK", n / 1000f).replace(".0K", "K");
        return String.valueOf(n);
    }

    private static String duration(int sec) {
        return String.format(Locale.US, "%d:%02d", sec / 60, sec % 60);
    }

    private String mediaLabel(MessageObject mo, TLRPC.Message m) {
        TLRPC.MessageMedia media = m.media;
        if (media == null || media instanceof TLRPC.TL_messageMediaEmpty) return "";
        if (media instanceof TLRPC.TL_messageMediaPhoto) return "🖼 Фото";
        if (media instanceof TLRPC.TL_messageMediaWebPage) return media.webpage != null && media.webpage.url != null ? media.webpage.url : "Ссылка";
        if (media instanceof TLRPC.TL_messageMediaPoll) return "📊 Опрос";
        if (media instanceof TLRPC.TL_messageMediaContact) return "👤 Контакт";
        if (media instanceof TLRPC.TL_messageMediaGeo || media instanceof TLRPC.TL_messageMediaVenue || media instanceof TLRPC.TL_messageMediaGeoLive) return "📍 Геопозиция";
        if (mo == null) return "📎 Вложение";
        if (mo.isVoice()) return "🎤 Голосовое сообщение";
        if (mo.isRoundVideo()) return "⭕ Видеосообщение";
        if (mo.isSticker() || mo.isAnimatedSticker()) return (mo.getStickerEmoji() != null ? mo.getStickerEmoji() + " " : "") + "Стикер";
        if (mo.isGif()) return "GIF";
        if (mo.isVideo()) return "🎬 Видео";
        if (mo.isMusic()) return "🎵 " + mo.getMusicTitle();
        TLRPC.Document doc = mo.getDocument();
        return "📄 " + (doc != null ? FileLoader.getDocumentFileName(doc) : "Файл");
    }

    /** Ставит в разметку ссылку на встроенные данные для видео/аудио/файлов (браузер собирает их лениво). */
    private String blobScript(Html h, File f, String mime) {
        String id = "b" + (++blobCounter);
        pendingBlobs.add(new Object[]{id, f, mime});
        return id;
    }

    private void emitBlobs(Html h) {
        for (Object[] b : pendingBlobs) {
            h.a("<script type=\"application/octet-stream\" id=\"").a(b[0]).a("\" data-mime=\"").a(attr((String) b[2])).a("\">").embed((File) b[1]).a("</script>\n");
        }
        pendingBlobs.clear();
    }

    private void appendMedia(Html h, MessageObject mo, TLRPC.Message m, boolean inAlbum) {
        TLRPC.MessageMedia media = m.media;
        if (media == null || media instanceof TLRPC.TL_messageMediaEmpty || media instanceof TLRPC.TL_messageMediaWebPage) {
            return;
        }
        if (media instanceof TLRPC.TL_messageMediaPhoto) {
            File f = null;
            if (media.photo != null && (mask & M_PHOTO) != 0) {
                TLRPC.PhotoSize size = FileLoader.getClosestPhotoSizeWithSize(media.photo.sizes, 1600);
                f = size != null ? obtain(mo, size, media.photo, size.size, "фото") : null;
            }
            if (f != null) {
                h.a("<img class=\"photo\" loading=\"lazy\" decoding=\"async\" alt=\"\" src=\"data:image/jpeg;base64,").embed(f).a("\">");
            } else {
                h.a("<div class=\"ph\">🖼 Фото").a(skipNote()).a("</div>");
            }
            return;
        }
        TLRPC.Document doc = mo.getDocument();
        if (media instanceof TLRPC.TL_messageMediaDocument && doc != null) {
            String mime = doc.mime_type != null ? doc.mime_type.toLowerCase(Locale.ROOT) : "";
            int dur = (int) Math.round(MessageObject.getDocumentDuration(doc));
            if (mo.isVoice()) {
                File f = (mask & M_VOICE) != 0 ? obtain(mo, doc, null, doc.size, "голосовое") : null;
                if (f != null) {
                    String id = blobScript(h, f, TextUtils.isEmpty(mime) ? "audio/ogg" : mime);
                    String wave = voiceWaveform(doc);
                    h.a("<div class=\"voice\"");
                    if (wave != null) h.a(" data-wf=\"").a(wave).a("\"");
                    h.a("><button class=\"pl\" aria-label=\"Слушать\">").a(SVG_PLAY_PAUSE).a("</button><div class=\"vw\"><div class=\"wave\"></div><span class=\"dur\">")
                            .a(duration(dur)).a("</span></div><audio preload=\"none\" data-b=\"").a(id).a("\"></audio></div>");
                } else {
                    h.a("<div class=\"ph\">🎤 Голосовое сообщение • ").a(duration(dur)).a(skipNote()).a("</div>");
                }
            } else if (mo.isRoundVideo()) {
                File f = (mask & M_ROUND) != 0 ? obtain(mo, doc, null, doc.size, "кружок") : null;
                if (f != null) {
                    String id = blobScript(h, f, "video/mp4");
                    h.a("<div class=\"roundw\"><video class=\"round\" playsinline preload=\"none\" data-b=\"").a(id).a("\"></video>").a(SVG_RING)
                            .a("<span class=\"rp\">").a(SVG_PLAY_ONLY).a("</span><span class=\"rd\">").a(duration(dur)).a("</span></div>");
                } else {
                    h.a("<div class=\"ph\">⭕ Видеосообщение • ").a(duration(dur)).a(skipNote()).a("</div>");
                }
            } else if (MessageObject.isStickerDocument(doc) || MessageObject.isAnimatedStickerDocument(doc) || MessageObject.isVideoStickerDocument(doc)) {
                String emoji = "";
                for (TLRPC.DocumentAttribute a : doc.attributes) {
                    if (a instanceof TLRPC.TL_documentAttributeSticker && a.alt != null) emoji = a.alt;
                }
                boolean tgs = mime.contains("tgsticker") || MessageObject.isAnimatedStickerDocument(doc);
                boolean webm = mime.contains("webm");
                File f = null;
                if ((mask & M_STICKER) != 0) {
                    if (tgs) {
                        // анимированные (lottie) браузер не проигрывает — берём превью стикера
                        TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(doc.thumbs, 320);
                        if (thumb != null && !(thumb instanceof TLRPC.TL_photoStrippedSize) && !(thumb instanceof TLRPC.TL_photoPathSize)) {
                            f = findLocal(mo, thumb, null);
                        }
                    } else {
                        f = obtain(mo, doc, null, doc.size, "стикер");
                    }
                }
                if (f != null && webm) {
                    String id = blobScript(h, f, "video/webm");
                    h.a("<video class=\"sticker\" autoplay loop muted playsinline data-b=\"").a(id).a("\"></video>");
                } else if (f != null) {
                    h.a("<img class=\"sticker\" loading=\"lazy\" alt=\"").a(attr(emoji)).a("\" src=\"data:image/webp;base64,").embed(f).a("\">");
                } else {
                    h.a("<div class=\"stk\">").a(esc(TextUtils.isEmpty(emoji) ? "🙂" : emoji)).a("</div>");
                }
            } else if (mo.isGif()) {
                File f = (mask & M_GIF) != 0 ? obtain(mo, doc, null, doc.size, "GIF") : null;
                if (f != null) {
                    String id = blobScript(h, f, "video/mp4");
                    h.a("<video class=\"gif\" autoplay loop muted playsinline data-b=\"").a(id).a("\"></video>");
                } else {
                    h.a("<div class=\"ph\">GIF").a(skipNote()).a("</div>");
                }
            } else if (mo.isVideo()) {
                File f = (mask & M_VIDEO) != 0 ? obtain(mo, doc, null, doc.size, "видео") : null;
                if (f != null) {
                    String id = blobScript(h, f, TextUtils.isEmpty(mime) || !mime.startsWith("video/") ? "video/mp4" : mime);
                    h.a("<video class=\"video\" controls playsinline preload=\"none\" data-b=\"").a(id).a("\"></video>");
                } else {
                    h.a("<div class=\"ph\">🎬 Видео • ").a(duration(dur)).a(skipNote()).a("</div>");
                }
            } else if (mo.isMusic()) {
                File f = (mask & M_MUSIC) != 0 ? obtain(mo, doc, null, doc.size, "музыка") : null;
                String author = mo.getMusicAuthor();
                String songTitle = mo.getMusicTitle();
                String songName = esc(!TextUtils.isEmpty(songTitle) ? songTitle : "Аудио");
                String songInfo = esc(!TextUtils.isEmpty(author) ? author + " • " : "") + duration(dur);
                if (f != null) {
                    String id = blobScript(h, f, TextUtils.isEmpty(mime) ? "audio/mpeg" : mime);
                    h.a("<div class=\"music\"><button class=\"pl\" aria-label=\"Слушать\">").a(SVG_PLAY_PAUSE).a("</button><div class=\"mi\"><div class=\"fn\">").a(songName)
                            .a("</div><div class=\"fs\">").a(songInfo).a("</div><div class=\"prog\"><i></i></div></div><audio preload=\"none\" data-b=\"").a(id).a("\"></audio></div>");
                } else {
                    h.a("<div class=\"file\"><div class=\"ic\">🎵</div><div><div class=\"fn\">").a(songName).a("</div><div class=\"fs\">")
                            .a(songInfo).a(skipNote()).a("</div></div></div>");
                }
            } else {
                String fileName = FileLoader.getDocumentFileName(doc);
                if (TextUtils.isEmpty(fileName)) fileName = "file";
                File f = (mask & M_FILE) != 0 ? obtain(mo, doc, null, doc.size, fileName) : null;
                String ext = fileExtension(fileName);
                String info = (ext != null ? "<div class=\"ic ext\">" + esc(ext) + "</div>" : "<div class=\"ic\">📄</div>")
                        + "<div><div class=\"fn\">" + esc(fileName) + "</div><div class=\"fs\">" + esc(AndroidUtilities.formatFileSize(doc.size));
                if (f != null) {
                    String id = blobScript(h, f, TextUtils.isEmpty(mime) ? "application/octet-stream" : mime);
                    h.a("<a class=\"file\" href=\"#\" data-b=\"").a(id).a("\" download=\"").a(attr(safeName(fileName))).a("\">").a(info).a("</div></div></a>");
                } else {
                    h.a("<div class=\"file\">").a(info).a(skipNote()).a("</div></div></div>");
                }
            }
            return;
        }
        if (media instanceof TLRPC.TL_messageMediaGeo || media instanceof TLRPC.TL_messageMediaVenue || media instanceof TLRPC.TL_messageMediaGeoLive) {
            if (media.geo != null && !(media.geo instanceof TLRPC.TL_geoPointEmpty)) {
                String q = String.format(Locale.US, "%.6f,%.6f", media.geo.lat, media.geo._long);
                h.a("<a class=\"file\" target=\"_blank\" rel=\"noopener\" href=\"https://maps.google.com/?q=").a(q).a("\"><div class=\"ic\">📍</div><div><div class=\"fn\">")
                        .a(esc(!TextUtils.isEmpty(media.title) ? media.title : "Геопозиция")).a("</div><div class=\"fs\">")
                        .a(esc(!TextUtils.isEmpty(media.address) ? media.address : q)).a("</div></div></a>");
            } else {
                h.a("<div class=\"ph\">📍 Геопозиция</div>");
            }
        } else if (media instanceof TLRPC.TL_messageMediaContact) {
            String name = ((media.first_name != null ? media.first_name : "") + " " + (media.last_name != null ? media.last_name : "")).trim();
            h.a("<div class=\"file\"><div class=\"ic\">👤</div><div><div class=\"fn\">").a(esc(name)).a("</div><div class=\"fs\">")
                    .a(esc(media.phone_number != null ? media.phone_number : "")).a("</div></div></div>");
        } else if (media instanceof TLRPC.TL_messageMediaPoll) {
            appendPoll(h, (TLRPC.TL_messageMediaPoll) media);
        } else if (media instanceof TLRPC.TL_messageMediaDice) {
            TLRPC.TL_messageMediaDice dice = (TLRPC.TL_messageMediaDice) media;
            h.a("<div class=\"stk\">").a(esc(dice.emoticon != null ? dice.emoticon : "🎲")).a("</div><div class=\"dur\">Выпало: ").a(dice.value).a("</div>");
        } else {
            h.a("<div class=\"ph\">📎 Вложение</div>");
        }
    }

    private void appendPoll(Html h, TLRPC.TL_messageMediaPoll mp) {
        TLRPC.Poll poll = mp.poll;
        if (poll == null) {
            h.a("<div class=\"ph\">📊 Опрос</div>");
            return;
        }
        int totalVoters = mp.results != null ? mp.results.total_voters : 0;
        h.a("<div class=\"poll\"><b>").a(esc(poll.question != null ? poll.question.text : "Опрос")).a("</b>");
        h.a("<div class=\"dur\">").a(poll.quiz ? "Викторина" : poll.public_voters ? "Опрос" : "Анонимный опрос").a(poll.closed ? " • завершён" : "").a("</div>");
        for (TLRPC.PollAnswer answer : poll.answers) {
            int voters = 0;
            if (mp.results != null && mp.results.results != null) {
                for (TLRPC.PollAnswerVoters v : mp.results.results) {
                    if (v.option != null && answer.option != null && java.util.Arrays.equals(v.option, answer.option)) voters = v.voters;
                }
            }
            int pc = totalVoters > 0 ? Math.round(voters * 100f / totalVoters) : 0;
            h.a("<div class=\"opt\"><span class=\"pc\">").a(pc).a("%</span>").a(esc(answer.text != null ? answer.text.text : ""))
                    .a("<div class=\"bar\" style=\"width:").a(Math.max(1, pc)).a("%\"></div></div>");
        }
        h.a("<div class=\"dur\">Голосов: ").a(totalVoters).a("</div></div>");
    }

    /** Волна голосового (как в Telegram: 5-битные значения), base64 — её рисует скрипт в файле. */
    private static String voiceWaveform(TLRPC.Document doc) {
        if (doc == null || doc.attributes == null) return null;
        for (TLRPC.DocumentAttribute a : doc.attributes) {
            if (a instanceof TLRPC.TL_documentAttributeAudio && a.waveform != null && a.waveform.length > 0) {
                return android.util.Base64.encodeToString(a.waveform, android.util.Base64.NO_WRAP);
            }
        }
        return null;
    }

    /** Расширение файла для значка («PDF», «ZIP»), не длиннее 4 символов. */
    private static String fileExtension(String name) {
        if (TextUtils.isEmpty(name)) return null;
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot >= name.length() - 1) return null;
        String ext = name.substring(dot + 1).toUpperCase(Locale.ROOT);
        if (ext.length() > 4) return null;
        for (int i = 0; i < ext.length(); i++) {
            if (!Character.isLetterOrDigit(ext.charAt(i))) return null;
        }
        return ext;
    }

    private String skipNote() {
        if (lastObtainSkipped) {
            lastObtainSkipped = false;
            return "<small>слишком большой файл — не встроен</small>";
        }
        return "";
    }

    /** Возвращает локальный файл медиа (при необходимости докачивает) с учётом лимитов встраивания. */
    private File obtain(MessageObject mo, TLObject attach, TLRPC.Photo photo, long size, String label) {
        lastObtainSkipped = false;
        if (cancelled || attach == null) return null;
        if (size > MAX_EMBED_FILE || embeddedBytes + Math.max(0, size) > MAX_EMBED_TOTAL) {
            skippedCount++;
            lastObtainSkipped = true;
            return null;
        }
        try {
            File src = findLocal(mo, attach, photo);
            if (src == null) {
                progress("Загрузка медиа: " + label + (size > 0 ? " • " + AndroidUtilities.formatFileSize(size) : ""), true);
                FileLoader fl = FileLoader.getInstance(account);
                if (attach instanceof TLRPC.Document) {
                    fl.loadFile((TLRPC.Document) attach, mo, FileLoader.PRIORITY_NORMAL, 1);
                } else {
                    fl.loadFile(ImageLocation.getForPhoto((TLRPC.PhotoSize) attach, photo), mo, "jpg", FileLoader.PRIORITY_NORMAL, 1);
                }
                long deadline = System.currentTimeMillis() + 90_000L + Math.max(0, size) / 30;
                while (!cancelled && System.currentTimeMillis() < deadline) {
                    Thread.sleep(300);
                    src = findLocal(mo, attach, photo);
                    if (src != null) break;
                }
                if (src == null) {
                    if (attach instanceof TLRPC.Document) {
                        fl.cancelLoadFile((TLRPC.Document) attach);
                    } else {
                        fl.cancelLoadFile((TLRPC.PhotoSize) attach);
                    }
                    if (!cancelled) failedCount++;
                    return null;
                }
            }
            long len = src.length();
            if (len > MAX_EMBED_FILE || embeddedBytes + len > MAX_EMBED_TOTAL) {
                skippedCount++;
                lastObtainSkipped = true;
                return null;
            }
            embeddedBytes += len;
            filesCount++;
            return src;
        } catch (InterruptedException e) {
            cancelled = true;
            return null;
        } catch (Throwable t) {
            FileLog.e(t);
            failedCount++;
            return null;
        }
    }

    private File findLocal(MessageObject mo, TLObject attach, TLRPC.Photo photo) {
        File f;
        if (attach instanceof TLRPC.Document && !TextUtils.isEmpty(mo.messageOwner.attachPath)) {
            f = new File(mo.messageOwner.attachPath);
            if (f.exists() && f.length() > 0) return f;
        }
        FileLoader fl = FileLoader.getInstance(account);
        ImageLocation loc = attach instanceof TLRPC.Document ? ImageLocation.getForDocument((TLRPC.Document) attach)
                : photo != null ? ImageLocation.getForPhoto((TLRPC.PhotoSize) attach, photo) : null;
        f = loc != null ? fl.getLocalFile(loc) : null;
        if (f != null && f.exists() && f.length() > 0) return f;
        f = fl.getPathToAttach(attach, true);
        if (f != null && f.exists() && f.length() > 0) return f;
        f = fl.getPathToAttach(attach, false);
        if (f != null && f.exists() && f.length() > 0) return f;
        return null;
    }

    // ---------------- текст ----------------

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        appendEsc(sb, s, 0, s.length());
        return sb.toString();
    }

    private static void appendEsc(StringBuilder sb, String s, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\n': sb.append("<br>"); break;
                default: sb.append(c);
            }
        }
    }

    private static String attr(String s) {
        return esc(s).replace("<br>", "");
    }

    @SuppressWarnings("unchecked")
    static String textToHtml(String text, ArrayList<TLRPC.MessageEntity> entities) {
        final int n = text.length();
        if (entities == null || entities.isEmpty()) return esc(text);
        ArrayList<TLRPC.MessageEntity> list = new ArrayList<>(entities);
        Collections.sort(list, (a, b) -> a.offset != b.offset ? Integer.compare(a.offset, b.offset) : Integer.compare(b.length, a.length));
        ArrayList<String>[] opens = new ArrayList[n + 1];
        ArrayList<String>[] closes = new ArrayList[n + 1];
        for (TLRPC.MessageEntity e : list) {
            int s = Math.max(0, Math.min(n, e.offset));
            int en = Math.max(0, Math.min(n, e.offset + e.length));
            if (en <= s) continue;
            String part = text.substring(s, en);
            String open, close;
            if (e instanceof TLRPC.TL_messageEntityBold) { open = "<b>"; close = "</b>"; }
            else if (e instanceof TLRPC.TL_messageEntityItalic) { open = "<i>"; close = "</i>"; }
            else if (e instanceof TLRPC.TL_messageEntityUnderline) { open = "<u>"; close = "</u>"; }
            else if (e instanceof TLRPC.TL_messageEntityStrike) { open = "<s>"; close = "</s>"; }
            else if (e instanceof TLRPC.TL_messageEntityCode) { open = "<code>"; close = "</code>"; }
            else if (e instanceof TLRPC.TL_messageEntityPre) { open = "<pre>"; close = "</pre>"; }
            else if (e instanceof TLRPC.TL_messageEntitySpoiler) { open = "<span class=\"spoiler\">"; close = "</span>"; }
            else if (e instanceof TLRPC.TL_messageEntityBlockquote) { open = "<blockquote>"; close = "</blockquote>"; }
            else if (e instanceof TLRPC.TL_messageEntityTextUrl) { open = "<a href=\"" + attr(e.url) + "\">"; close = "</a>"; }
            else if (e instanceof TLRPC.TL_messageEntityUrl) {
                String url = part.contains("://") ? part : "https://" + part;
                open = "<a href=\"" + attr(url) + "\">"; close = "</a>";
            }
            else if (e instanceof TLRPC.TL_messageEntityEmail) { open = "<a href=\"mailto:" + attr(part) + "\">"; close = "</a>"; }
            else if (e instanceof TLRPC.TL_messageEntityPhone) { open = "<a href=\"tel:" + attr(part) + "\">"; close = "</a>"; }
            else if (e instanceof TLRPC.TL_messageEntityMention) { open = "<a href=\"https://t.me/" + attr(part.startsWith("@") ? part.substring(1) : part) + "\">"; close = "</a>"; }
            else continue;
            if (opens[s] == null) opens[s] = new ArrayList<>();
            opens[s].add(open);
            if (closes[en] == null) closes[en] = new ArrayList<>();
            closes[en].add(0, close);
        }
        StringBuilder sb = new StringBuilder(n + 64);
        for (int i = 0; i <= n; i++) {
            if (closes[i] != null) for (String c : closes[i]) sb.append(c);
            if (opens[i] != null && i < n) for (String o : opens[i]) sb.append(o);
            if (i < n) appendEsc(sb, text, i, i + 1);
        }
        return sb.toString();
    }

    // ---------------- файлы ----------------

    static String safeName(String s) {
        if (s == null) return "file";
        String r = s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        if (r.length() > 80) r = r.substring(0, 80);
        return r.isEmpty() ? "file" : r;
    }
}
