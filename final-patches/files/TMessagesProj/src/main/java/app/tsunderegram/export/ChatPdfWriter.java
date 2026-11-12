package app.tsunderegram.export;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.StyleSpan;

import org.telegram.messenger.FileLog;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * TsundereGram: вёрстка экспорта чата в PDF (A4, светлая тема) поверх android.graphics.pdf.PdfDocument.
 * Экспортёр отдаёт сюда сообщения по одному; страницы закрываются сразу, как заполнятся.
 */
final class ChatPdfWriter {

    static final int PAGE_W = 595, PAGE_H = 842;
    private static final float MX = 32f, TOP = 34f, BOTTOM = PAGE_H - 46f, FOOT_Y = PAGE_H - 22f;
    private static final float CONTENT_W = PAGE_W - 2 * MX;
    private static final float BUBBLE_MAX = 384f, PAD_X = 9f, PAD_Y = 6f, PAD_MEDIA = 3f, ROW_GAP = 4f;
    private static final float MEDIA_OUT = PAD_X - PAD_MEDIA;
    private static final float AVATAR = 24f, AVATAR_INDENT = AVATAR + 6f;
    private static final float MAX_IMAGE_H = 300f, STICKER_BOX = 128f, ROUND_SIZE = 150f;
    private static final float RADIUS = 11f, RADIUS_SMALL = 4f, IMAGE_RADIUS = 8f;
    /** PdfDocument держит все страницы (и картинки в них) в памяти до writeTo — общий бюджет на растр. */
    private static final long IMAGE_BUDGET = 96L * 1024 * 1024;

    static final int TEXT = 0xff1d2125, MUTED = 0xff8a9199, LINK = 0xff2481cc;
    private static final int IN_BG = 0xfff0f2f5, OUT_BG = 0xffe1f4d0;
    private static final int IN_ACCENT = 0xff2f86d1, OUT_ACCENT = 0xff4b9d40;
    private static final int IN_META = 0xff9aa1a9, OUT_META = 0xff69a35e;
    private static final int IN_CHIP = 0xffdde9f6, OUT_CHIP = 0xffc9e7b4;
    private static final int PILL_BG = 0xffedf0f3, PILL_TEXT = 0xff5f6771;
    private static final int COVER_BG = 0xfff4f6f8, RULE = 0xffe2e5e9, PLACEHOLDER_BG = 0xffe4e7eb;
    /** Те же оттенки, что PEER_COLORS у аватарок, но темнее — для имён на светлом фоне. */
    private static final int[] NAME_COLORS = {0xffcc5049, 0xffd67722, 0xff955cdb, 0xff40a920, 0xff309eba, 0xff368ad1, 0xffc7508b};

    static int nameColor(long id) {
        return NAME_COLORS[(int) (Math.abs(id) % NAME_COLORS.length)];
    }

    // ---------------- модель ----------------

    static final class Media {
        static final int IMAGE = 0, CARD = 1, POLL = 2;
        static final int ICON_EMOJI = 0, ICON_PLAY = 1, ICON_EXT = 2;

        int type;
        File file;
        boolean alpha, round, play;
        String badge;
        int iconKind;
        String icon, title, subtitle;
        byte[] waveform;
        String question, pollKind, pollFooter;
        String[] options;
        int[] percents;
        int imgW, imgH;

        static Media image(File file, boolean alpha, boolean round, boolean play, String badge) {
            Media m = new Media();
            m.type = IMAGE;
            m.file = file;
            m.alpha = alpha;
            m.round = round;
            m.play = play;
            m.badge = badge;
            return m;
        }

        static Media card(int iconKind, String icon, String title, String subtitle) {
            Media m = new Media();
            m.type = CARD;
            m.iconKind = iconKind;
            m.icon = icon;
            m.title = title;
            m.subtitle = subtitle;
            return m;
        }

        static Media poll(String question, String kind, String[] options, int[] percents, String footer) {
            Media m = new Media();
            m.type = POLL;
            m.question = question;
            m.pollKind = kind;
            m.options = options;
            m.percents = percents;
            m.pollFooter = footer;
            return m;
        }
    }

    static final class Bubble {
        boolean out, firstInRun, bare, album;
        /** Входящее в группе: место под аватарку слева; showAvatar — рисовать её у этого пузыря. */
        boolean avatarSlot, showAvatar;
        Bitmap avatar;
        String initials;
        int avatarColor;
        String name;
        int nameColor;
        String forward;
        String replyWho, replyWhat;
        final ArrayList<Media> media = new ArrayList<>();
        CharSequence text;
        String webSite, webTitle, webDescription, webUrl;
        final ArrayList<String> reactions = new ArrayList<>();
        String meta;
    }

    // ---------------- состояние ----------------

    private final PdfDocument doc = new PdfDocument();
    private final String footerTitle;
    private PdfDocument.Page page;
    private Canvas canvas;
    private int pageNo;
    private float y;
    private long imageBytes;
    private int skippedImages;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Typeface medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private final TextPaint bodyPaint = tp(10.5f, TEXT, null);
    private final TextPaint namePaint = tp(10f, TEXT, medium);
    private final TextPaint forwardPaint = tp(9f, TEXT, null);
    private final TextPaint replyNamePaint = tp(9f, TEXT, medium);
    private final TextPaint replyTextPaint = tp(9f, 0xff50575f, null);
    private final TextPaint metaInPaint = tp(8f, IN_META, null);
    private final TextPaint metaOutPaint = tp(8f, OUT_META, null);
    private final TextPaint chipTextPaint = tp(7.5f, Color.WHITE, medium);
    private final TextPaint dayPaint = tp(8.5f, PILL_TEXT, medium);
    private final TextPaint servicePaint = tp(9f, PILL_TEXT, null);
    private final TextPaint footerPaint = tp(7.5f, MUTED, null);
    private final TextPaint summaryPaint = tp(8.5f, MUTED, null);
    private final TextPaint coverTitlePaint = tp(17f, TEXT, medium);
    private final TextPaint coverSubPaint = tp(10f, 0xff5f6771, null);
    private final TextPaint coverMetaPaint = tp(9f, MUTED, null);
    private final TextPaint cardTitlePaint = tp(10f, TEXT, medium);
    private final TextPaint cardSubPaint = tp(9f, MUTED, null);
    private final TextPaint iconTextPaint = tp(8f, Color.WHITE, Typeface.DEFAULT_BOLD);
    private final TextPaint emojiPaint = tp(15f, TEXT, null);
    private final TextPaint initialsPaint = tp(10f, Color.WHITE, medium);
    private final TextPaint pollQuestionPaint = tp(10.5f, TEXT, medium);
    private final TextPaint pollPercentPaint = tp(9f, TEXT, Typeface.DEFAULT_BOLD);
    private final TextPaint webSitePaint = tp(9f, TEXT, medium);
    private final TextPaint webTitlePaint = tp(10f, TEXT, medium);
    private final TextPaint webDescPaint = tp(9.5f, TEXT, null);
    private final TextPaint webUrlPaint = tp(8.5f, LINK, null);
    private final TextPaint reactionPaint = tp(9f, TEXT, null);
    private final TextPaint placeholderPaint = tp(8.5f, MUTED, null);

    ChatPdfWriter(String footerTitle) {
        this.footerTitle = footerTitle != null ? footerTitle : "";
    }

    private static TextPaint tp(float size, int color, Typeface tf) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        if (tf != null) p.setTypeface(tf);
        return p;
    }

    private static TextPaint colored(TextPaint base, int color) {
        TextPaint p = new TextPaint(base);
        p.setColor(color);
        return p;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    int getPageCount() {
        return pageNo;
    }

    int getSkippedImages() {
        return skippedImages;
    }

    // ---------------- страницы ----------------

    private void ensurePage() {
        if (page == null) startPage();
    }

    private void startPage() {
        pageNo++;
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create();
        page = doc.startPage(info);
        canvas = page.getCanvas();
        y = TOP;
        fill.setColor(RULE);
        canvas.drawRect(MX, BOTTOM + 12f, PAGE_W - MX, BOTTOM + 12.5f, fill);
        String num = "Стр. " + pageNo;
        float numW = footerPaint.measureText(num);
        canvas.drawText(num, PAGE_W - MX - numW, FOOT_Y, footerPaint);
        drawLine(canvas, footerTitle, footerPaint, MX, FOOT_Y, CONTENT_W - numW - 24f);
    }

    private void finishPage() {
        if (page != null) {
            doc.finishPage(page);
            page = null;
            canvas = null;
        }
    }

    private void newPage() {
        finishPage();
        startPage();
    }

    /** Нужна высота h: если на текущей странице не помещается — новая страница. */
    private void need(float h) {
        ensurePage();
        if (y + h > BOTTOM && y > TOP + 0.5f) newPage();
    }

    void writeTo(OutputStream os) throws IOException {
        ensurePage();
        finishPage();
        doc.writeTo(os);
    }

    void close() {
        try {
            if (page != null) {
                doc.finishPage(page);
                page = null;
                canvas = null;
            }
        } catch (Throwable ignore) {
        }
        try {
            doc.close();
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    // ---------------- крупные блоки ----------------

    void cover(String title, String kind, String stats, String exported, Bitmap avatar, String initials, int avatarColor) {
        ensurePage();
        float pad = 16f, av = 56f;
        float textX = MX + pad + av + 14f;
        float textW = PAGE_W - MX - pad - textX;
        StaticLayout tl = makeLayout(title, coverTitlePaint, (int) textW, 2, Layout.Alignment.ALIGN_NORMAL);
        String line1 = kind + (TextUtils.isEmpty(stats) ? "" : " • " + stats);
        float textH = tl.getHeight() + 6f + 13f + 12f;
        float h = Math.max(av, textH) + 2 * pad;
        RectF r = new RectF(MX, y, PAGE_W - MX, y + h);
        fill.setColor(COVER_BG);
        canvas.drawRoundRect(r, 14f, 14f, fill);
        drawAvatar(canvas, MX + pad + av / 2f, y + h / 2f, av / 2f, avatar, initials, avatarColor);
        float ty = y + (h - textH) / 2f;
        canvas.save();
        canvas.translate(textX, ty);
        tl.draw(canvas);
        canvas.restore();
        ty += tl.getHeight() + 6f;
        drawLine(canvas, line1, coverSubPaint, textX, ty + 10f, textW);
        drawLine(canvas, exported, coverMetaPaint, textX, ty + 23f, textW);
        y += h + 16f;
    }

    void day(String text) {
        float h = 18f;
        // плашка даты не должна остаться одна внизу страницы
        need(h + 16f + 60f);
        if (y > TOP + 0.5f) y += 8f;
        float w = dayPaint.measureText(text) + 20f;
        RectF r = new RectF((PAGE_W - w) / 2f, y, (PAGE_W + w) / 2f, y + h);
        fill.setColor(PILL_BG);
        canvas.drawRoundRect(r, h / 2f, h / 2f, fill);
        canvas.drawText(text, r.left + 10f, centerBaseline(dayPaint, y, h), dayPaint);
        y += h + 6f;
    }

    void service(String text) {
        if (TextUtils.isEmpty(text)) return;
        int lw = 360;
        StaticLayout l = makeLayout(text, servicePaint, lw, 8, Layout.Alignment.ALIGN_CENTER);
        float w = maxLineWidth(l, 0, l.getLineCount()) + 20f;
        float h = l.getHeight() + 8f;
        need(h + 12f);
        y += 6f;
        RectF r = new RectF((PAGE_W - w) / 2f, y, (PAGE_W + w) / 2f, y + h);
        fill.setColor(PILL_BG);
        canvas.drawRoundRect(r, 9f, 9f, fill);
        canvas.save();
        canvas.translate((PAGE_W - lw) / 2f, y + 4f);
        l.draw(canvas);
        canvas.restore();
        y += h + 6f;
    }

    void summary(String text) {
        StaticLayout l = makeLayout(text, summaryPaint, (int) CONTENT_W, 0, Layout.Alignment.ALIGN_CENTER);
        need(l.getHeight() + 30f);
        y += 16f;
        fill.setColor(RULE);
        canvas.drawRect(PAGE_W / 2f - 60f, y, PAGE_W / 2f + 60f, y + 0.5f, fill);
        y += 8f;
        canvas.save();
        canvas.translate(MX, y);
        l.draw(canvas);
        canvas.restore();
        y += l.getHeight() + 6f;
    }

    // ---------------- пузыри ----------------

    void bubble(Bubble b) {
        ensurePage();
        final int accent = b.out ? OUT_ACCENT : IN_ACCENT;
        final int maxInner = (int) (BUBBLE_MAX - 2 * PAD_X);
        ArrayList<Row> rows = new ArrayList<>();
        if (!TextUtils.isEmpty(b.name) && !b.bare) {
            rows.add(textRow(b.name, colored(namePaint, b.nameColor), maxInner, 1));
        }
        if (!TextUtils.isEmpty(b.forward)) {
            SpannableStringBuilder s = new SpannableStringBuilder("Переслано от ");
            int st = s.length();
            s.append(b.forward);
            s.setSpan(new StyleSpan(Typeface.BOLD), st, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            rows.add(textRow(s, colored(forwardPaint, accent), maxInner, 2));
        }
        if (!TextUtils.isEmpty(b.replyWho)) {
            rows.add(new ReplyRow(b.replyWho, b.replyWhat != null ? b.replyWhat : "", accent, maxInner));
        }
        int albumAt = rows.size();
        ArrayList<Media> albumItems = new ArrayList<>();
        for (Media m : b.media) {
            if (m.type == Media.IMAGE && readBounds(m)) {
                if (b.album) {
                    albumItems.add(m);
                } else {
                    ArrayList<Media> one = new ArrayList<>(1);
                    one.add(m);
                    rows.add(new ImageRow(one, maxInner));
                }
                continue;
            }
            Media c = m.type == Media.IMAGE ? Media.card(Media.ICON_EMOJI, "🖼", "Изображение", "не удалось прочитать файл") : m;
            if (c.type == Media.POLL) {
                rows.add(new PollRow(c, accent, maxInner));
            } else {
                rows.add(new CardRow(c, accent, maxInner));
            }
        }
        if (!albumItems.isEmpty()) rows.add(albumAt, new ImageRow(albumItems, maxInner));
        if (!TextUtils.isEmpty(b.text)) {
            rows.add(textRow(b.text, bodyPaint, maxInner, 0));
        }
        if (!TextUtils.isEmpty(b.webTitle) || !TextUtils.isEmpty(b.webDescription) || !TextUtils.isEmpty(b.webSite)) {
            rows.add(new WebRow(b, accent, maxInner));
        }
        if (!b.reactions.isEmpty()) {
            rows.add(new ReactionsRow(b.reactions, b.out ? OUT_CHIP : IN_CHIP, maxInner));
        }
        if (rows.isEmpty()) {
            rows.add(textRow("Сообщение", colored(bodyPaint, MUTED), maxInner, 1));
        }

        final boolean bare = b.bare && rows.size() == 1 && rows.get(0) instanceof ImageRow && ((ImageRow) rows.get(0)).fixed;
        final String meta = b.meta != null ? b.meta : "";
        final TextPaint mp = b.out ? metaOutPaint : metaInPaint;
        final float metaW = mp.measureText(meta);
        Row lastRow = rows.get(rows.size() - 1);
        float inner = 0;
        for (Row r : rows) inner = Math.max(inner, r.prefW);
        boolean metaOnMedia = false;
        if (bare || lastRow instanceof ImageRow && ((ImageRow) lastRow).media) {
            metaOnMedia = true;
        } else if (lastRow instanceof TextRow && ((TextRow) lastRow).canInline(metaW, maxInner)) {
            TextRow t = (TextRow) lastRow;
            t.meta = meta;
            t.metaPaint = mp;
            inner = Math.max(inner, t.lastLineW() + 6f + metaW);
        } else {
            rows.add(new MetaRow(meta, mp));
            inner = Math.max(inner, metaW);
        }
        if (!bare) inner = Math.min(Math.max(inner, 40f), maxInner);
        for (Row r : rows) r.layout(inner);

        final float bw = bare ? inner : inner + 2 * PAD_X;
        final float left = MX + (b.avatarSlot ? AVATAR_INDENT : 0f);
        final float bx = b.out ? PAGE_W - MX - bw : left;
        final float gapBefore = b.firstInRun ? 6f : 2.5f;
        float total = segmentHeight(rows, bare);
        if (y + gapBefore + total <= BOTTOM) {
            if (y > TOP + 0.5f) y += gapBefore;
            drawSegment(b, rows, bx, bw, inner, bare, true, true, metaOnMedia, meta);
            return;
        }
        if (total <= BOTTOM - TOP) {
            newPage();
            drawSegment(b, rows, bx, bw, inner, bare, true, true, metaOnMedia, meta);
            return;
        }
        // пузырь выше страницы — режем текст по строкам, каждая часть рисуется своим пузырём
        if (BOTTOM - y < 80f) {
            newPage();
        } else if (y > TOP + 0.5f) {
            y += gapBefore;
        }
        ArrayList<Row> rest = new ArrayList<>(rows);
        boolean first = true;
        while (!rest.isEmpty()) {
            float avail = BOTTOM - y - (bare ? 0f : 2 * PAD_Y);
            ArrayList<Row> seg = new ArrayList<>();
            float used = 0;
            while (!rest.isEmpty()) {
                Row r = rest.get(0);
                float gap = seg.isEmpty() ? 0f : ROW_GAP;
                if (used + gap + r.h <= avail) {
                    seg.add(r);
                    rest.remove(0);
                    used += gap + r.h;
                    continue;
                }
                if (r instanceof TextRow) {
                    TextRow t = (TextRow) r;
                    int n = t.linesFitting(avail - used - gap);
                    if (n > 0 && n < t.to - t.from) {
                        seg.add(t.sub(t.from, t.from + n));
                        rest.set(0, t.sub(t.from + n, t.to));
                    }
                }
                if (seg.isEmpty()) {
                    // даже одна строка/картинка не влезает на чистую страницу — рисуем как есть
                    seg.add(r);
                    rest.remove(0);
                }
                break;
            }
            drawSegment(b, seg, bx, bw, inner, bare, first, rest.isEmpty(), metaOnMedia, meta);
            first = false;
            if (!rest.isEmpty()) newPage();
        }
    }

    private static boolean isMediaRow(Row r) {
        return r instanceof ImageRow && ((ImageRow) r).media;
    }

    private static float segmentHeight(ArrayList<Row> seg, boolean bare) {
        float h = 0;
        for (int i = 0; i < seg.size(); i++) {
            if (i > 0) h += ROW_GAP;
            h += seg.get(i).h;
        }
        if (!bare) {
            h += isMediaRow(seg.get(0)) ? PAD_MEDIA : PAD_Y;
            h += isMediaRow(seg.get(seg.size() - 1)) ? PAD_MEDIA : PAD_Y;
        }
        return h;
    }

    private void drawSegment(Bubble b, ArrayList<Row> seg, float bx, float bw, float inner, boolean bare,
                             boolean first, boolean last, boolean metaOnMedia, String meta) {
        Canvas c = canvas;
        float h = segmentHeight(seg, bare);
        // ошибка посреди рисования не должна оставить на странице чужой клип
        int saved = c.save();
        try {
            drawSegmentContent(c, b, seg, bx, bw, inner, bare, first, last, metaOnMedia, meta, h);
        } finally {
            c.restoreToCount(saved);
        }
        y += h;
    }

    private void drawSegmentContent(Canvas c, Bubble b, ArrayList<Row> seg, float bx, float bw, float inner, boolean bare,
                                    boolean first, boolean last, boolean metaOnMedia, String meta, float h) {
        if (!bare) {
            float big = RADIUS;
            float side = first && b.firstInRun ? RADIUS : RADIUS_SMALL;
            float[] radii = b.out
                    ? new float[]{big, big, side, side, RADIUS_SMALL, RADIUS_SMALL, big, big}
                    : new float[]{side, side, big, big, big, big, RADIUS_SMALL, RADIUS_SMALL};
            Path p = new Path();
            p.addRoundRect(new RectF(bx, y, bx + bw, y + h), radii, Path.Direction.CW);
            fill.setColor(b.out ? OUT_BG : IN_BG);
            c.drawPath(p, fill);
        }
        if (first && b.showAvatar) {
            drawAvatar(c, MX + AVATAR / 2f, y + AVATAR / 2f, AVATAR / 2f, b.avatar, b.initials, b.avatarColor);
        }
        float x = bare ? bx : bx + PAD_X;
        float cy = y + (bare ? 0f : isMediaRow(seg.get(0)) ? PAD_MEDIA : PAD_Y);
        Row lastRow = null;
        float lastTop = cy;
        for (int i = 0; i < seg.size(); i++) {
            Row r = seg.get(i);
            if (i > 0) cy += ROW_GAP;
            r.draw(c, x, cy, inner);
            lastRow = r;
            lastTop = cy;
            cy += r.h;
        }
        if (last && metaOnMedia && lastRow instanceof ImageRow && !TextUtils.isEmpty(meta)) {
            ImageRow ir = (ImageRow) lastRow;
            float right = ir.media ? x + inner + MEDIA_OUT : x + ir.prefW;
            drawChip(c, meta, right - 5f, lastTop + ir.h - 5f, false);
        }
    }

    // ---------------- строки пузыря ----------------

    private abstract static class Row {
        float h, prefW;

        void layout(float inner) {
        }

        abstract void draw(Canvas c, float x, float y, float w);
    }

    private TextRow textRow(CharSequence text, TextPaint p, int maxW, int maxLines) {
        StaticLayout l = makeLayout(text, p, maxW, maxLines, Layout.Alignment.ALIGN_NORMAL);
        int tight = (int) Math.ceil(maxLineWidth(l, 0, l.getLineCount())) + 2;
        if (tight < maxW) {
            // узкий макет: иначе RTL-строки уедут вправо за границу пузыря
            l = makeLayout(text, p, tight, maxLines, Layout.Alignment.ALIGN_NORMAL);
        }
        return new TextRow(l, 0, l.getLineCount());
    }

    private final class TextRow extends Row {
        final StaticLayout l;
        final int from, to;
        String meta;
        TextPaint metaPaint;

        TextRow(StaticLayout l, int from, int to) {
            this.l = l;
            this.from = from;
            this.to = to;
            h = l.getLineTop(to) - l.getLineTop(from);
            prefW = maxLineWidth(l, from, to);
        }

        TextRow sub(int a, int b) {
            TextRow r = new TextRow(l, a, b);
            if (b == to) {
                r.meta = meta;
                r.metaPaint = metaPaint;
            }
            return r;
        }

        float lastLineW() {
            return Math.min(l.getWidth(), l.getLineWidth(to - 1));
        }

        boolean canInline(float metaW, float maxW) {
            return l.getParagraphDirection(to - 1) != Layout.DIR_RIGHT_TO_LEFT && lastLineW() + 6f + metaW <= maxW;
        }

        int linesFitting(float maxH) {
            int n = 0;
            float top = l.getLineTop(from);
            for (int i = from; i < to; i++) {
                if (l.getLineBottom(i) - top > maxH) break;
                n++;
            }
            return n;
        }

        @Override
        void draw(Canvas c, float x, float y, float w) {
            float top = l.getLineTop(from);
            c.save();
            // клип ограничивает и сами строки: Layout рисует только видимые, без дублей текста в PDF
            c.clipRect(x - 2f, y, x + Math.max(w, l.getWidth()) + 2f, y + h);
            c.translate(x, y - top);
            l.draw(c);
            c.restore();
            if (meta != null && metaPaint != null) {
                float base = y + l.getLineBaseline(to - 1) - top;
                c.drawText(meta, x + w - metaPaint.measureText(meta), base, metaPaint);
            }
        }
    }

    private final class MetaRow extends Row {
        final String meta;
        final TextPaint p;

        MetaRow(String meta, TextPaint p) {
            this.meta = meta;
            this.p = p;
            h = 10f;
            prefW = p.measureText(meta);
        }

        @Override
        void draw(Canvas c, float x, float y, float w) {
            c.drawText(meta, x + w - p.measureText(meta), y + 8f, p);
        }
    }

    private final class ReplyRow extends Row {
        final String who, what;
        final int accent;

        ReplyRow(String who, String what, int accent, float maxW) {
            this.who = who;
            this.what = what.replace('\n', ' ');
            this.accent = accent;
            h = 30f;
            prefW = Math.min(maxW, Math.max(replyNamePaint.measureText(who), replyTextPaint.measureText(this.what)) + 16f);
        }

        @Override
        void draw(Canvas c, float x, float y, float w) {
            RectF r = new RectF(x, y, x + w, y + h);
            Path clip = new Path();
            clip.addRoundRect(r, 5f, 5f, Path.Direction.CW);
            c.save();
            c.clipPath(clip);
            fill.setColor(withAlpha(accent, 0x1f));
            c.drawRect(r, fill);
            fill.setColor(accent);
            c.drawRect(x, y, x + 2.5f, y + h, fill);
            c.restore();
            replyNamePaint.setColor(accent);
            drawLine(c, who, replyNamePaint, x + 8f, y + 12.5f, w - 12f);
            drawLine(c, what, replyTextPaint, x + 8f, y + 25f, w - 12f);
        }
    }

    private final class ImageRow extends Row {
        final ArrayList<Media> items;
        /** Стикер или кружок: свой размер, без растягивания на ширину пузыря. */
        final boolean fixed;
        /** Фото/видео: выходит за внутренние отступы пузыря почти до края. */
        final boolean media;
        float[] cells;

        ImageRow(ArrayList<Media> items, float maxInner) {
            this.items = items;
            Media m0 = items.get(0);
            fixed = items.size() == 1 && (m0.round || m0.alpha);
            media = !fixed;
            if (fixed) {
                float[] box = fixedBox(m0);
                prefW = box[0];
                h = box[1];
            } else if (items.size() == 1) {
                float a = m0.imgW / (float) m0.imgH;
                float natW = Math.min(maxInner + 2 * MEDIA_OUT, Math.max(160f, MAX_IMAGE_H * a));
                prefW = natW - 2 * MEDIA_OUT;
            } else {
                prefW = maxInner;
            }
        }

        private float[] fixedBox(Media m) {
            if (m.round) return new float[]{ROUND_SIZE, ROUND_SIZE};
            float a = m.imgW / (float) m.imgH;
            return a >= 1f ? new float[]{STICKER_BOX, STICKER_BOX / a} : new float[]{STICKER_BOX * a, STICKER_BOX};
        }

        @Override
        void layout(float inner) {
            int n = items.size();
            cells = new float[n * 4];
            if (fixed) {
                cells[2] = prefW;
                cells[3] = h;
                return;
            }
            float W = inner + 2 * MEDIA_OUT;
            if (n == 1) {
                Media m = items.get(0);
                float a = m.imgW / (float) m.imgH;
                cells[2] = W;
                cells[3] = Math.max(48f, Math.min(MAX_IMAGE_H, W / a));
                h = cells[3];
                return;
            }
            final float gap = 2f;
            int cols = n <= 4 ? 2 : 3;
            int firstRow = n % cols == 0 ? cols : n % cols;
            float cy = 0;
            int idx = 0;
            // первая строка — «остаток» (1–2 шт.), дальше ровная сетка
            float fw = (W - gap * (firstRow - 1)) / firstRow;
            float fh;
            if (firstRow == 1) {
                Media m = items.get(0);
                fh = Math.max(60f, Math.min(220f, W * m.imgH / (float) m.imgW));
            } else {
                fh = fw * 0.8f;
            }
            if (firstRow < cols || n == cols) {
                for (int i = 0; i < firstRow; i++, idx++) {
                    cells[idx * 4] = i * (fw + gap);
                    cells[idx * 4 + 1] = cy;
                    cells[idx * 4 + 2] = fw;
                    cells[idx * 4 + 3] = fh;
                }
                cy += fh + gap;
            }
            float cw = (W - gap * (cols - 1)) / cols;
            float ch = cw * 0.8f;
            for (int col = 0; idx < n; idx++) {
                cells[idx * 4] = col * (cw + gap);
                cells[idx * 4 + 1] = cy;
                cells[idx * 4 + 2] = cw;
                cells[idx * 4 + 3] = ch;
                col++;
                if (col == cols) {
                    col = 0;
                    cy += ch + gap;
                }
            }
            h = cy - gap;
        }

        @Override
        void draw(Canvas c, float x, float y, float w) {
            float ox = media ? x - MEDIA_OUT : x;
            for (int i = 0; i < items.size(); i++) {
                drawImage(c, items.get(i), ox + cells[i * 4], y + cells[i * 4 + 1], cells[i * 4 + 2], cells[i * 4 + 3]);
            }
        }
    }

    private final class CardRow extends Row {
        final Media m;
        final int accent;

        CardRow(Media m, int accent, float maxW) {
            this.m = m;
            this.accent = accent;
            h = 36f;
            float tw = 0;
            if (m.waveform != null) {
                tw = 150f;
            } else if (m.title != null) {
                tw = cardTitlePaint.measureText(m.title);
            }
            if (m.subtitle != null) tw = Math.max(tw, cardSubPaint.measureText(m.subtitle));
            prefW = Math.min(maxW, Math.max(150f, 45f + tw + 2f));
        }

        @Override
        void draw(Canvas c, float x, float y, float w) {
            float cx = x + 18f, cy = y + 18f;
            if (m.iconKind == Media.ICON_EMOJI) {
                fill.setColor(withAlpha(accent, 0x26));
                c.drawCircle(cx, cy, 18f, fill);
                String icon = TextUtils.isEmpty(m.icon) ? "📎" : m.icon;
                c.drawText(icon, cx - emojiPaint.measureText(icon) / 2f, centerBaseline(emojiPaint, cy - 18f, 36f), emojiPaint);
            } else {
                fill.setColor(accent);
                c.drawCircle(cx, cy, 18f, fill);
                if (m.iconKind == Media.ICON_PLAY || TextUtils.isEmpty(m.icon)) {
                    drawPlay(c, cx, cy, 6.5f, Color.WHITE);
                } else {
                    c.drawText(m.icon, cx - iconTextPaint.measureText(m.icon) / 2f, centerBaseline(iconTextPaint, cy - 18f, 36f), iconTextPaint);
                }
            }
            float tx = x + 45f, tw = w - 45f;
            String title = m.title != null ? m.title : "";
            boolean hasSub = !TextUtils.isEmpty(m.subtitle);
            if (m.waveform != null) {
                drawWaveform(c, m.waveform, tx, y + 5f, Math.min(tw, 150f), 15f, accent);
                if (hasSub) drawLine(c, m.subtitle, cardSubPaint, tx, y + 31f, tw);
            } else if (hasSub) {
                drawLine(c, title, cardTitlePaint, tx, y + 15f, tw);
                drawLine(c, m.subtitle, cardSubPaint, tx, y + 29f, tw);
            } else {
                drawLine(c, title, cardTitlePaint, tx, y + 21.5f, tw);
            }
        }
    }

    private final class PollRow extends Row {
        final StaticLayout question;
        final StaticLayout[] options;
        final Media m;
        final int accent;

        PollRow(Media m, int accent, int maxW) {
            this.m = m;
            this.accent = accent;
            prefW = maxW;
            question = makeLayout(m.question != null ? m.question : "Опрос", pollQuestionPaint, maxW, 0, Layout.Alignment.ALIGN_NORMAL);
            int n = m.options != null ? m.options.length : 0;
            options = new StaticLayout[n];
            float hh = question.getHeight() + 14f;
            for (int i = 0; i < n; i++) {
                options[i] = makeLayout(m.options[i] != null ? m.options[i] : "", bodyPaint, maxW - 34, 0, Layout.Alignment.ALIGN_NORMAL);
                hh += options[i].getHeight() + 13f;
            }
            h = hh + 11f;
        }

        @Override
        void draw(Canvas c, float x, float y, float w) {
            c.save();
            c.translate(x, y);
            question.draw(c);
            c.restore();
            float cy = y + question.getHeight();
            drawLine(c, m.pollKind, cardSubPaint, x, cy + 10f, w);
            cy += 14f;
            for (int i = 0; i < options.length; i++) {
                StaticLayout l = options[i];
                int pc = m.percents != null && i < m.percents.length ? m.percents[i] : 0;
                String pcs = pc + "%";
                c.drawText(pcs, x + 28f - pollPercentPaint.measureText(pcs), cy + l.getLineBaseline(0), pollPercentPaint);
                c.save();
                c.translate(x + 34f, cy);
                l.draw(c);
                c.restore();
                cy += l.getHeight() + 4f;
                float bw = w - 34f;
                fill.setColor(withAlpha(accent, 0x30));
                c.drawRoundRect(new RectF(x + 34f, cy, x + 34f + bw, cy + 3f), 1.5f, 1.5f, fill);
                fill.setColor(accent);
                c.drawRoundRect(new RectF(x + 34f, cy, x + 34f + Math.max(3f, bw * Math.min(100, pc) / 100f), cy + 3f), 1.5f, 1.5f, fill);
                cy += 9f;
            }
            drawLine(c, m.pollFooter, cardSubPaint, x, cy + 9f, w);
        }
    }

    private final class WebRow extends Row {
        final StaticLayout site, title, desc;
        final String url;
        final int accent;

        WebRow(Bubble b, int accent, int maxW) {
            this.accent = accent;
            int tw = maxW - 10;
            site = TextUtils.isEmpty(b.webSite) ? null : makeLayout(b.webSite, colored(webSitePaint, accent), tw, 1, Layout.Alignment.ALIGN_NORMAL);
            title = TextUtils.isEmpty(b.webTitle) ? null : makeLayout(b.webTitle, webTitlePaint, tw, 2, Layout.Alignment.ALIGN_NORMAL);
            desc = TextUtils.isEmpty(b.webDescription) ? null : makeLayout(b.webDescription, webDescPaint, tw, 4, Layout.Alignment.ALIGN_NORMAL);
            url = b.webUrl;
            float hh = 4f, pw = 0;
            for (StaticLayout l : new StaticLayout[]{site, title, desc}) {
                if (l == null) continue;
                hh += l.getHeight() + 1f;
                pw = Math.max(pw, maxLineWidth(l, 0, l.getLineCount()));
            }
            if (!TextUtils.isEmpty(url)) {
                hh += 12f;
                pw = Math.max(pw, Math.min(tw, webUrlPaint.measureText(url)));
            }
            h = hh + 3f;
            prefW = Math.min(maxW, pw + 10f);
        }

        @Override
        void draw(Canvas c, float x, float y, float w) {
            fill.setColor(accent);
            c.drawRoundRect(new RectF(x, y, x + 2.5f, y + h), 1.25f, 1.25f, fill);
            float cy = y + 2f;
            for (StaticLayout l : new StaticLayout[]{site, title, desc}) {
                if (l == null) continue;
                c.save();
                c.translate(x + 8f, cy);
                l.draw(c);
                c.restore();
                cy += l.getHeight() + 1f;
            }
            if (!TextUtils.isEmpty(url)) drawLine(c, url, webUrlPaint, x + 8f, cy + 9f, w - 10f);
        }
    }

    private final class ReactionsRow extends Row {
        final ArrayList<String> items;
        final float[] xs, ys, ws;
        final int chip;

        ReactionsRow(ArrayList<String> items, int chip, float maxW) {
            this.items = items;
            this.chip = chip;
            int n = items.size();
            xs = new float[n];
            ys = new float[n];
            ws = new float[n];
            float cx = 0, cy = 0;
            for (int i = 0; i < n; i++) {
                float cw = reactionPaint.measureText(items.get(i)) + 14f;
                if (cx > 0 && cx + cw > maxW) {
                    cx = 0;
                    cy += 20f;
                }
                xs[i] = cx;
                ys[i] = cy;
                ws[i] = cw;
                cx += cw + 4f;
                prefW = Math.max(prefW, cx - 4f);
            }
            h = cy + 17f;
        }

        @Override
        void draw(Canvas c, float x, float y, float w) {
            for (int i = 0; i < items.size(); i++) {
                RectF r = new RectF(x + xs[i], y + ys[i], x + xs[i] + ws[i], y + ys[i] + 17f);
                fill.setColor(chip);
                c.drawRoundRect(r, 8.5f, 8.5f, fill);
                c.drawText(items.get(i), r.left + 7f, centerBaseline(reactionPaint, r.top, 17f), reactionPaint);
            }
        }
    }

    // ---------------- рисование ----------------

    private static StaticLayout makeLayout(CharSequence text, TextPaint p, int width, int maxLines, Layout.Alignment align) {
        if (text == null) text = "";
        StaticLayout.Builder b = StaticLayout.Builder.obtain(text, 0, text.length(), p, Math.max(8, width))
                .setAlignment(align)
                .setLineSpacing(1f, 1f)
                .setIncludePad(false);
        if (maxLines > 0) {
            b.setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END);
        }
        return b.build();
    }

    private static float maxLineWidth(Layout l, int from, int to) {
        float w = 0;
        for (int i = from; i < to; i++) w = Math.max(w, l.getLineWidth(i));
        return Math.min(w, l.getWidth());
    }

    private static float centerBaseline(Paint p, float top, float h) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return top + h / 2f - (fm.ascent + fm.descent) / 2f;
    }

    private static void drawLine(Canvas c, String text, TextPaint p, float x, float baseline, float maxW) {
        if (TextUtils.isEmpty(text) || maxW <= 0) return;
        CharSequence t = TextUtils.ellipsize(text.replace('\n', ' '), p, maxW, TextUtils.TruncateAt.END);
        c.drawText(t, 0, t.length(), x, baseline, p);
    }

    private void drawPlay(Canvas c, float cx, float cy, float r, int color) {
        Path p = new Path();
        p.moveTo(cx - r * 0.55f, cy - r);
        p.lineTo(cx + r, cy);
        p.lineTo(cx - r * 0.55f, cy + r);
        p.close();
        fill.setColor(color);
        c.drawPath(p, fill);
    }

    /** Плашка с текстом (время на фото, бейдж «Видео 0:42»); right/bottom — правый нижний угол, иначе левый верхний. */
    private void drawChip(Canvas c, String text, float ax, float ay, boolean fromTopLeft) {
        float w = chipTextPaint.measureText(text) + 10f, h = 13f;
        float l = fromTopLeft ? ax : ax - w;
        float t = fromTopLeft ? ay : ay - h;
        fill.setColor(0x80000000);
        c.drawRoundRect(new RectF(l, t, l + w, t + h), h / 2f, h / 2f, fill);
        c.drawText(text, l + 5f, centerBaseline(chipTextPaint, t, h), chipTextPaint);
    }

    private void drawWaveform(Canvas c, byte[] data, float x, float top, float w, float h, int color) {
        int count = data.length * 8 / 5;
        if (count <= 0) return;
        int bars = Math.max(1, (int) (w / 3f));
        fill.setColor(withAlpha(color, 0xc0));
        for (int i = 0; i < bars; i++) {
            int idx = (int) ((long) i * count / bars);
            int bit = idx * 5, pos = bit / 8, shift = bit % 8;
            int v = (data[pos] & 0xff) | (pos + 1 < data.length ? (data[pos + 1] & 0xff) << 8 : 0);
            v = (v >> shift) & 31;
            float bh = Math.max(2f, h * v / 31f);
            c.drawRoundRect(new RectF(x + i * 3f, top + h - bh, x + i * 3f + 2f, top + h), 1f, 1f, fill);
        }
    }

    private void drawAvatar(Canvas c, float cx, float cy, float r, Bitmap bmp, String initials, int color) {
        if (bmp != null && !bmp.isRecycled()) {
            Path p = new Path();
            p.addCircle(cx, cy, r, Path.Direction.CW);
            c.save();
            c.clipPath(p);
            c.drawBitmap(bmp, null, new RectF(cx - r, cy - r, cx + r, cy + r), bitmapPaint);
            c.restore();
            return;
        }
        fill.setColor(color);
        c.drawCircle(cx, cy, r, fill);
        if (!TextUtils.isEmpty(initials)) {
            initialsPaint.setTextSize(r * 0.78f);
            c.drawText(initials, cx - initialsPaint.measureText(initials) / 2f, centerBaseline(initialsPaint, cy - r, 2 * r), initialsPaint);
        }
    }

    private void drawImage(Canvas c, Media m, float x, float y, float w, float h) {
        Bitmap bmp = decode(m, w, h);
        RectF dst = new RectF(x, y, x + w, y + h);
        Path clip = null;
        if (m.round) {
            clip = new Path();
            clip.addCircle(x + w / 2f, y + h / 2f, Math.min(w, h) / 2f, Path.Direction.CW);
        } else if (!m.alpha) {
            clip = new Path();
            clip.addRoundRect(dst, IMAGE_RADIUS, IMAGE_RADIUS, Path.Direction.CW);
        }
        c.save();
        if (clip != null) c.clipPath(clip);
        if (bmp != null) {
            int bw = bmp.getWidth(), bh = bmp.getHeight();
            Rect src;
            if (m.alpha) {
                src = new Rect(0, 0, bw, bh);
            } else {
                // обрезка по центру под пропорции ячейки
                float target = w / h;
                if (bw / (float) bh > target) {
                    int cw = Math.max(1, Math.round(bh * target));
                    src = new Rect((bw - cw) / 2, 0, (bw - cw) / 2 + cw, bh);
                } else {
                    int ch = Math.max(1, Math.round(bw / target));
                    src = new Rect(0, (bh - ch) / 2, bw, (bh - ch) / 2 + ch);
                }
            }
            c.drawBitmap(bmp, src, dst, bitmapPaint);
            // запись страницы держит свою ссылку на пиксели — java-объект можно освободить сразу
            bmp.recycle();
        } else {
            fill.setColor(PLACEHOLDER_BG);
            c.drawRect(dst, fill);
            drawLine(c, "изображение не вошло в PDF", placeholderPaint, x + 6f, y + h / 2f + 3f, w - 12f);
        }
        c.restore();
        if (m.play) {
            fill.setColor(0x73000000);
            float r = Math.min(16f, Math.min(w, h) / 3f);
            c.drawCircle(x + w / 2f, y + h / 2f, r, fill);
            drawPlay(c, x + w / 2f + r * 0.08f, y + h / 2f, r * 0.42f, Color.WHITE);
        }
        if (!TextUtils.isEmpty(m.badge)) {
            drawChip(c, m.badge, x + 5f, y + 5f, true);
        }
    }

    private static boolean readBounds(Media m) {
        if (m.file == null) return false;
        if (m.imgW > 0 && m.imgH > 0) return true;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(m.file.getAbsolutePath(), o);
            m.imgW = o.outWidth;
            m.imgH = o.outHeight;
        } catch (Throwable t) {
            FileLog.e(t);
        }
        return m.imgW > 0 && m.imgH > 0;
    }

    /** Декодирует картинку не крупнее нужного (inSampleSize + доуменьшение) с учётом общего бюджета памяти. */
    private Bitmap decode(Media m, float w, float h) {
        if (!readBounds(m)) return null;
        // чем больше уже нарисовано, тем скромнее разрешение — чтобы большие чаты не упёрлись в память
        float scale = imageBytes < IMAGE_BUDGET * 2 / 5 ? 1.5f : imageBytes < IMAGE_BUDGET * 3 / 4 ? 1.0f : 0.6f;
        int tw = Math.max(1, Math.round(w * scale)), th = Math.max(1, Math.round(h * scale));
        int sample = 1;
        while (m.imgW / (sample * 2) >= tw && m.imgH / (sample * 2) >= th) sample *= 2;
        int bpp = m.alpha ? 4 : 2;
        long estimate = (long) (m.imgW / sample) * (m.imgH / sample) * bpp;
        long expected = Math.min(estimate, (long) tw * th * bpp * 2);
        if (imageBytes + expected > IMAGE_BUDGET) {
            skippedImages++;
            return null;
        }
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = m.alpha ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565;
            Bitmap b = BitmapFactory.decodeFile(m.file.getAbsolutePath(), o);
            if (b == null) return null;
            float f = Math.max(tw / (float) b.getWidth(), th / (float) b.getHeight());
            if (f < 0.75f) {
                int nw = Math.max(1, Math.round(b.getWidth() * f)), nh = Math.max(1, Math.round(b.getHeight() * f));
                Bitmap scaled = Bitmap.createScaledBitmap(b, nw, nh, true);
                if (scaled != b) {
                    b.recycle();
                    b = scaled;
                }
            }
            imageBytes += b.getAllocationByteCount();
            return b;
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }

    /** Маленькая аватарка (для кружков): декодируется один раз и переиспользуется на всех страницах. */
    static Bitmap decodeAvatar(File f, int px) {
        if (f == null) return null;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= px && o.outHeight / (sample * 2) >= px) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getAbsolutePath(), d);
        } catch (Throwable t) {
            FileLog.e(t);
            return null;
        }
    }
}
