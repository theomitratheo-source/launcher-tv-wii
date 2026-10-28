package com.wii.launcher;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Menu façon Wii : grille 4x3 de "chaînes", barre blanche en bas avec l'heure. Une seule vue, zéro dépendance. */
public class WiiView extends View {
    private static final int COLS = 4, ROWS = 3, PER = COLS * ROWS;
    private static final int BLUE = 0xFF35B5F0, GRAY = 0xFFC4C9D1;

    private static class Item {
        String label; ComponentName cn; Drawable icon; boolean banner;
    }

    private final List<Item> apps = new ArrayList<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF(), tmp = new RectF();
    private final Path path = new Path();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.getDefault());
    private final SimpleDateFormat dateFmt = new SimpleDateFormat("EEEE d MMMM", Locale.getDefault());
    private Shader bg;
    private int page = 0, sel = 0; // sel < PER : tuile ; PER / PER+1 : boutons du bas

    private final Runnable tick = new Runnable() {
        @Override public void run() { invalidate(); ui.postDelayed(this, 15000); }
    };

    public WiiView(Context c) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        tp.setTextAlign(Paint.Align.CENTER);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestFocus();
        ui.post(tick);
    }

    @Override protected void onDetachedFromWindow() {
        ui.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        bg = new LinearGradient(0, 0, 0, h, 0xFFFFFFFF, 0xFFD9DDE3, Shader.TileMode.CLAMP);
    }

    public void reload() {
        final Context ctx = getContext();
        new Thread(new Runnable() {
            @Override public void run() {
                PackageManager pm = ctx.getPackageManager();
                final List<Item> list = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                String[] cats = { Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER };
                for (String cat : cats) {
                    Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                    for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
                        ActivityInfo ai = ri.activityInfo;
                        if (ai.packageName.equals(ctx.getPackageName()) || !seen.add(ai.packageName)) continue;
                        Item it = new Item();
                        it.label = String.valueOf(ri.loadLabel(pm));
                        it.cn = new ComponentName(ai.packageName, ai.name);
                        Drawable d = null;
                        try { d = ai.loadBanner(pm); } catch (Exception ignored) { }
                        it.banner = d != null;
                        if (d == null) d = ri.loadIcon(pm);
                        it.icon = d;
                        list.add(it);
                    }
                }
                Collections.sort(list, (a, b) -> a.label.compareToIgnoreCase(b.label));
                ui.post(new Runnable() {
                    @Override public void run() {
                        boolean first = apps.isEmpty();
                        apps.clear();
                        apps.addAll(list);
                        if (first) { page = 0; sel = 0; }
                        clamp();
                        invalidate();
                    }
                });
            }
        }).start();
    }

    private int pages() { return Math.max(1, (apps.size() + PER - 1) / PER); }
    private boolean exists(int i) { return page * PER + i < apps.size(); }

    private void clamp() {
        if (page >= pages()) page = pages() - 1;
        if (apps.isEmpty()) { sel = PER; return; }
        if (sel < PER && !exists(sel)) sel = Math.max(0, apps.size() - page * PER - 1);
    }

    // ---------- dessin ----------

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        p.setStyle(Paint.Style.FILL);
        p.setShader(bg);
        c.drawRect(0, 0, w, h, p);
        p.setShader(null);

        float mx = w * 0.07f, gap = w * 0.015f;
        float gridTop = h * 0.06f, gridBottom = h * 0.80f;
        float tw = (w - 2 * mx - (COLS - 1) * gap) / COLS;
        float th = (gridBottom - gridTop - (ROWS - 1) * gap) / ROWS;

        for (int i = 0; i < PER; i++) {
            if (i == sel) continue;
            tile(c, i, mx + (i % COLS) * (tw + gap), gridTop + (i / COLS) * (th + gap), tw, th, false);
        }
        if (sel < PER) {
            tile(c, sel, mx + (sel % COLS) * (tw + gap), gridTop + (sel / COLS) * (th + gap), tw, th, true);
        }

        // flèches de pagination
        p.setColor(BLUE);
        float cy = (gridTop + gridBottom) / 2, a = mx * 0.22f;
        if (page > 0) triangle(c, mx * 0.5f, cy, a, -1);
        if (page < pages() - 1) triangle(c, w - mx * 0.5f, cy, a, 1);

        // barre du bas
        float barTop = h * 0.85f, barH = h - barTop;
        r.set(-60, barTop, w + 60, h + 90);
        p.setColor(0xFFFFFFFF);
        c.drawRoundRect(r, 90, 90, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(GRAY);
        c.drawRoundRect(r, 90, 90, p);
        p.setStyle(Paint.Style.FILL);

        button(c, w * 0.12f, barTop + barH * 0.5f, barH * 0.36f, "Réglages", sel == PER);
        button(c, w * 0.88f, barTop + barH * 0.5f, barH * 0.36f, "Wi-Fi", sel == PER + 1);

        Date now = new Date();
        tp.setColor(0xFF8A8F98);
        tp.setFakeBoldText(false);
        tp.setTextSize(barH * 0.42f);
        c.drawText(timeFmt.format(now), w / 2f, barTop + barH * 0.52f, tp);
        tp.setTextSize(barH * 0.16f);
        String d = dateFmt.format(now);
        if (!d.isEmpty()) d = Character.toUpperCase(d.charAt(0)) + d.substring(1);
        c.drawText(d, w / 2f, barTop + barH * 0.80f, tp);
    }

    private void tile(Canvas c, int i, float x, float y, float tw, float th, boolean selected) {
        r.set(x, y, x + tw, y + th);
        if (selected) r.inset(-tw * 0.03f, -th * 0.03f);
        float rad = th * 0.12f;

        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        c.drawRoundRect(r, rad, rad, p);

        int idx = page * PER + i;
        if (idx < apps.size()) {
            Item it = apps.get(idx);
            c.save();
            path.rewind();
            path.addRoundRect(r, rad, rad, Path.Direction.CW);
            c.clipPath(path);
            if (it.banner) {
                it.icon.setBounds((int) r.left, (int) r.top, (int) r.right, (int) r.bottom);
                it.icon.draw(c);
            } else {
                int s = (int) (r.height() * 0.52f);
                int cx = (int) r.centerX(), cy = (int) (r.top + r.height() * 0.40f);
                it.icon.setBounds(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2);
                it.icon.draw(c);
                tp.setColor(0xFF666B73);
                tp.setTextSize(r.height() * 0.12f);
                CharSequence t = TextUtils.ellipsize(it.label, tp, r.width() * 0.9f, TextUtils.TruncateAt.END);
                c.drawText(t, 0, t.length(), r.centerX(), r.bottom - r.height() * 0.10f, tp);
            }
            c.restore();
        }

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(selected ? 6 : 2);
        p.setColor(selected ? BLUE : GRAY);
        c.drawRoundRect(r, rad, rad, p);
        p.setStyle(Paint.Style.FILL);
    }

    private void button(Canvas c, float cx, float cy, float rad, String label, boolean selected) {
        if (selected) rad *= 1.1f;
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFFFFFFFF);
        c.drawCircle(cx, cy, rad, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(selected ? 6 : 3);
        p.setColor(selected ? BLUE : GRAY);
        c.drawCircle(cx, cy, rad, p);
        p.setStyle(Paint.Style.FILL);
        tp.setColor(0xFF666B73);
        tp.setTextSize(rad * 0.30f);
        c.drawText(label, cx, cy + rad * 0.10f, tp);
    }

    private void triangle(Canvas c, float cx, float cy, float a, int dir) {
        path.rewind();
        path.moveTo(cx + dir * a, cy);
        path.lineTo(cx - dir * a, cy - a * 1.4f);
        path.lineTo(cx - dir * a, cy + a * 1.4f);
        path.close();
        p.setStyle(Paint.Style.FILL);
        c.drawPath(path, p);
    }

    // ---------- télécommande ----------

    @Override public boolean onKeyDown(int k, KeyEvent e) {
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:  moveH(-1); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: moveH(1);  return true;
            case KeyEvent.KEYCODE_DPAD_UP:    moveV(-1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:  moveV(1);  return true;
            case KeyEvent.KEYCODE_PAGE_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:   changePage(-1); return true;
            case KeyEvent.KEYCODE_PAGE_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN: changePage(1);  return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                activate();
                return true;
        }
        return super.onKeyDown(k, e);
    }

    private void moveH(int d) {
        if (sel >= PER) {
            int n = sel + d;
            if (n >= PER && n <= PER + 1) sel = n;
        } else {
            int col = sel % COLS;
            if (d < 0) {
                if (col > 0) sel--;
                else if (page > 0) { page--; sel += COLS - 1; }
            } else {
                if (col < COLS - 1) { if (exists(sel + 1)) sel++; }
                else if ((page + 1) * PER < apps.size()) { page++; sel -= COLS - 1; clamp(); }
            }
        }
        invalidate();
    }

    private void moveV(int d) {
        if (sel >= PER) {
            if (d < 0 && !apps.isEmpty()) {
                int t = (ROWS - 1) * COLS + (sel == PER ? 0 : COLS - 1);
                while (t > 0 && !exists(t)) t--;
                sel = t;
            }
        } else {
            int row = sel / COLS;
            if (d < 0) { if (row > 0) sel -= COLS; }
            else if (row < ROWS - 1 && exists(sel + COLS)) sel += COLS;
            else sel = (sel % COLS < COLS / 2) ? PER : PER + 1;
        }
        invalidate();
    }

    private void changePage(int d) {
        int np = page + d;
        if (np < 0 || np >= pages()) return;
        page = np;
        if (sel >= PER) sel = 0;
        clamp();
        invalidate();
    }

    private void activate() {
        Context ctx = getContext();
        Intent i = null;
        if (sel < PER) {
            int idx = page * PER + sel;
            if (idx < apps.size()) {
                i = new Intent(Intent.ACTION_MAIN).setComponent(apps.get(idx).cn)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            }
        } else {
            i = new Intent(sel == PER ? Settings.ACTION_SETTINGS : Settings.ACTION_WIFI_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if (i != null) {
            try { ctx.startActivity(i); } catch (ActivityNotFoundException | SecurityException ignored) { }
        }
    }
}
