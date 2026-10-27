package com.toolbox.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Насекомые бегают поверх всех приложений; тап — зелёная клякса. */
public class BugsService extends Service {
    static volatile boolean running;
    static final String STOP = "stop";
    private WindowManager wm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<Bug> bugs = new ArrayList<>();
    private final Random rnd = new Random();
    private int sw, sh;
    private long last;
    private final Runnable frame = this::frame;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int f, int id) {
        if (i != null && STOP.equals(i.getAction())) { stopSelf(); return START_NOT_STICKY; }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        h.removeCallbacks(frame); clear();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("bugs", "Насекомые", NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop = PendingIntent.getService(this, 0, new Intent(this, BugsService.class).setAction(STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(this, "bugs").setSmallIcon(android.R.drawable.star_on)
                .setContentTitle("Насекомые бегают по экрану").setContentText("Нажмите «Выключить», чтобы остановить")
                .setOngoing(true).addAction(android.R.drawable.ic_delete, "Выключить", stop).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(1, n);
        try { spawn(); } catch (RuntimeException e) { stopSelf(); return START_NOT_STICKY; }
        running = true; last = SystemClock.uptimeMillis();
        h.post(frame);
        return START_NOT_STICKY;
    }

    private void metrics() {
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        sw = dm.widthPixels; sh = dm.heightPixels;
    }

    private void spawn() {
        SharedPreferences sp = Store.p(this);
        int n = sp.getInt("bcount", 8), size = Store.dp(this, sp.getInt("bsize", 56)), off = rnd.nextInt(8);
        metrics();
        for (int i = 0; i < n; i++) {
            Bug b = new Bug(this, (i + off) % 8, rnd.nextLong());
            b.x = rnd.nextInt(Math.max(1, sw - size)); b.y = rnd.nextInt(Math.max(1, sh - size));
            b.angle = rnd.nextFloat() * 6.2832f;
            b.lp = new WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
            b.lp.gravity = Gravity.TOP | Gravity.START; b.lp.x = (int) b.x; b.lp.y = (int) b.y;
            wm.addView(b, b.lp);
            bugs.add(b);
        }
    }

    private void frame() {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(0.05f, (now - last) / 1000f); last = now;
        metrics();
        float spd = Store.p(this).getInt("bspeed", 140) * getResources().getDisplayMetrics().density;
        Iterator<Bug> it = bugs.iterator();
        while (it.hasNext()) {
            Bug b = it.next();
            if (b.state == 1) {
                long a = now - b.splatT;
                if (a > 1200) { try { wm.removeView(b); } catch (Exception ignored) {} it.remove(); }
                else { if (a < 200) b.invalidate(); if (a > 700) b.setAlpha(1f - (a - 700) / 500f); }
                continue;
            }
            b.angle += (rnd.nextFloat() - 0.5f) * b.turn * dt * 2;
            float v = spd * b.mul;
            b.x += Math.cos(b.angle) * v * dt; b.y += Math.sin(b.angle) * v * dt;
            float mx = Math.max(0, sw - b.lp.width), my = Math.max(0, sh - b.lp.height);
            if (b.x < 0) { b.x = 0; b.angle = (float) Math.PI - b.angle; } else if (b.x > mx) { b.x = mx; b.angle = (float) Math.PI - b.angle; }
            if (b.y < 0) { b.y = 0; b.angle = -b.angle; } else if (b.y > my) { b.y = my; b.angle = -b.angle; }
            b.phase += dt * 20;
            b.lp.x = (int) b.x; b.lp.y = (int) b.y;
            try { wm.updateViewLayout(b, b.lp); } catch (Exception ignored) {}
            b.invalidate();
        }
        if (bugs.isEmpty()) { stopSelf(); return; }
        h.postDelayed(frame, 25);
    }

    private void clear() {
        for (Bug b : bugs) { try { wm.removeView(b); } catch (Exception ignored) {} }
        bugs.clear();
    }

    @Override public void onDestroy() {
        h.removeCallbacks(frame);
        if (wm != null) clear();
        running = false;
        super.onDestroy();
    }

    static class Bug extends View {
        static final float[] MUL = {1f, 1.5f, 0.8f, 1.3f, 0.7f, 0.5f, 0.45f, 0.9f};
        static final float[] TURN = {3, 8, 2, 5, 2, 1, 1, 3};
        final int type; final long seed; final float mul, turn;
        float x, y, angle, phase;
        volatile int state; long splatT;
        WindowManager.LayoutParams lp;
        final Paint P = new Paint(Paint.ANTI_ALIAS_FLAG);
        final RectF R = new RectF();

        Bug(Context c, int type, long seed) { super(c); this.type = type; this.seed = seed; mul = MUL[type]; turn = TURN[type]; }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN && state == 0) { state = 1; splatT = SystemClock.uptimeMillis(); invalidate(); }
            return true;
        }

        private void oval(Canvas c, float cx, float cy, float rx, float ry, int col) {
            P.setStyle(Paint.Style.FILL); P.setColor(col); R.set(cx - rx, cy - ry, cx + rx, cy + ry); c.drawOval(R, P);
        }
        private void line(Canvas c, float a, float b, float x2, float y2, int col, float w) {
            P.setStyle(Paint.Style.STROKE); P.setStrokeWidth(w); P.setColor(col); c.drawLine(a, b, x2, y2, P);
        }

        @Override protected void onDraw(Canvas c) {
            float s = getWidth() / 100f;
            c.save(); c.translate(getWidth() / 2f, getHeight() / 2f); c.scale(s, s);
            if (state == 1) splat(c); else bug(c);
            c.restore();
        }

        private void splat(Canvas c) {
            float k = Math.min(1f, (SystemClock.uptimeMillis() - splatT) / 120f);
            c.scale(k, k);
            Random r = new Random(seed);
            oval(c, 0, 0, 24, 21, 0xFF5FA82A);
            for (int i = 0; i < 10; i++) {
                double a = r.nextDouble() * 6.283; float d = 22 + r.nextFloat() * 22, rad = 3 + r.nextFloat() * 8;
                oval(c, (float) (Math.cos(a) * d), (float) (Math.sin(a) * d), rad, rad, i % 2 == 0 ? 0xFF7CC336 : 0xFF4E8F23);
            }
            oval(c, -6, -5, 9, 7, 0xFF8FD64A);
        }

        private void bug(Canvas c) {
            c.rotate((float) Math.toDegrees(angle) + 90);
            float w = (float) Math.sin(phase);
            int dk = 0xFF222222;
            if (type == 7) {
                for (int i = 0; i < 4; i++) {
                    float a = -26 + i * 18 + w * (i % 2 == 0 ? 4 : -4);
                    line(c, 0, 4, -36, a, dk, 2.5f); line(c, 0, 4, 36, a, dk, 2.5f);
                }
            } else {
                float len = type == 0 ? 42 : 30, th = type == 0 ? 1.5f : 2.5f;
                for (int i = -1; i <= 1; i++) {
                    float yy = i * 10 + 2, q = w * 4 * (i == 0 ? -1 : 1);
                    line(c, -6, yy, -len, yy + 10 + q, dk, th); line(c, 6, yy, len, yy + 10 - q, dk, th);
                }
            }
            if (type <= 3) {
                float f = 0.75f + 0.25f * Math.abs((float) Math.sin(phase * 3));
                oval(c, -20, 6, 9, 24 * f, 0x99DCE8F5); oval(c, 20, 6, 9, 24 * f, 0x99DCE8F5);
            }
            switch (type) {
                case 0: // комар
                    oval(c, 0, 16, 5, 18, 0xFF6D5B4B); oval(c, 0, -4, 7, 9, 0xFF4A3F36); oval(c, 0, -16, 5, 5, 0xFF3A322C);
                    line(c, 0, -20, 0, -34, dk, 1.5f); break;
                case 1: // муха
                    oval(c, 0, 14, 10, 16, 0xFF2B2B2B); oval(c, 0, -4, 10, 10, 0xFF3B3B3B); oval(c, 0, -18, 8, 7, 0xFF1E1E1E);
                    oval(c, -5, -19, 4, 5, 0xFFD32F2F); oval(c, 5, -19, 4, 5, 0xFFD32F2F); break;
                case 2: // шмель
                    oval(c, 0, 14, 14, 18, 0xFFFFC107); oval(c, 0, 8, 13, 3.5f, 0xFF111111); oval(c, 0, 19, 12, 3.5f, 0xFF111111);
                    oval(c, 0, -4, 12, 11, 0xFF4A3B00); oval(c, 0, -18, 7, 6, 0xFF111111); break;
                case 3: // оса
                    oval(c, 0, 18, 8, 18, 0xFFFFD600); oval(c, 0, 12, 8, 3, 0xFF111111); oval(c, 0, 22, 7, 3, 0xFF111111);
                    oval(c, 0, 29, 5, 3, 0xFF111111); oval(c, 0, 2, 3, 4, 0xFF111111); oval(c, 0, -6, 8, 9, 0xFF222222);
                    oval(c, 0, -19, 7, 6, 0xFFFFB300); line(c, 0, 35, 0, 42, dk, 2f); break;
                case 4: // муравей
                    oval(c, 0, 16, 9, 13, 0xFF5D3A1A); oval(c, 0, 0, 5, 7, 0xFF6D4420); oval(c, 0, -14, 6, 6, 0xFF4E2F14);
                    line(c, -2, -18, -10, -32, dk, 1.5f); line(c, 2, -18, 10, -32, dk, 1.5f); break;
                case 5: // жук
                    oval(c, 0, 6, 18, 28, 0xFF1B3A2A); oval(c, -6, 0, 4, 14, 0x3388CC99); line(c, 0, -18, 0, 32, 0xFF000000, 1.5f);
                    oval(c, 0, -24, 8, 6, 0xFF111111); break;
                case 6: // божья коровка
                    oval(c, 0, 4, 22, 24, 0xFFD32F2F); line(c, 0, -20, 0, 28, 0xFF000000, 1.5f);
                    oval(c, -10, -6, 4, 4, 0xFF000000); oval(c, 10, -6, 4, 4, 0xFF000000); oval(c, -12, 10, 4, 4, 0xFF000000);
                    oval(c, 12, 10, 4, 4, 0xFF000000); oval(c, -5, 20, 3, 3, 0xFF000000); oval(c, 5, 20, 3, 3, 0xFF000000);
                    oval(c, 0, -24, 9, 6, 0xFF000000); break;
                default: // паук
                    oval(c, 0, 12, 14, 17, 0xFF3E2723); oval(c, 0, -8, 9, 9, 0xFF4E342E);
                    oval(c, -3, -12, 2, 2, 0xFFFF5252); oval(c, 3, -12, 2, 2, 0xFFFF5252);
            }
        }
    }
}
