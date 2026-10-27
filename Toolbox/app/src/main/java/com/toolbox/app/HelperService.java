package com.toolbox.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Автокликер (запись касаний + повтор) и блокировка приложений PIN-кодом. */
public class HelperService extends AccessibilityService {
    static HelperService inst;
    private static final int OVL = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    private static final int FULL = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

    private WindowManager wm;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<float[]> pts = new ArrayList<>();
    private final Set<String> unlocked = new HashSet<>();
    private final Set<String> ignore = new HashSet<>();
    private View panel, layer, lockView;
    private WindowManager.LayoutParams panelLp;
    private Button recBtn, playBtn;
    private TextView count;
    private boolean playing, recording;
    private int idx;
    private String lockPkg;
    private final Runnable tickR = this::tick;
    private final BroadcastReceiver off = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { unlocked.clear(); }
    };

    @Override protected void onServiceConnected() {
        inst = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        ignore.add(getPackageName());
        ignore.add("com.android.systemui");
        ignore.add("com.google.android.permissioncontroller");
        ignore.add("com.android.permissioncontroller");
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        for (InputMethodInfo i : im.getInputMethodList()) ignore.add(i.getPackageName());
        registerReceiver(off, new IntentFilter(Intent.ACTION_SCREEN_OFF));
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        inst = null;
        stopPlay(); hideLock(); closePanel();
        try { unregisterReceiver(off); } catch (Exception ignored) {}
        super.onDestroy();
    }

    // ---------- Блокировка приложений ----------
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {
        if (e.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || e.getPackageName() == null) return;
        String pkg = e.getPackageName().toString();
        if (ignore.contains(pkg)) return;
        if (!Store.guarded(this).contains(pkg)) { unlocked.clear(); hideLock(); return; }
        if (unlocked.contains(pkg)) return;
        SharedPreferences sp = Store.p(this);
        if (pkg.equals(sp.getString("skip", "")) && System.currentTimeMillis() - sp.getLong("skipT", 0) < 5000) {
            unlocked.add(pkg); return; // запуск из тайника: PIN уже введён
        }
        showLock(pkg);
    }

    private void showLock(String pkg) {
        if (!Store.hasPin(this)) return;
        if (lockView != null) { if (pkg.equals(lockPkg)) return; hideLock(); }
        lockPkg = pkg;
        lockView = new PinView(this, "Введите PIN", new PinView.Listener() {
            @Override public boolean onPin(String pin) {
                if (!Store.checkPin(HelperService.this, pin)) return false;
                unlocked.add(pkg); hideLock(); return true;
            }
            @Override public void onCancel() { hideLock(); performGlobalAction(GLOBAL_ACTION_HOME); }
        });
        wm.addView(lockView, new WindowManager.LayoutParams(-1, -1, OVL, FULL, PixelFormat.TRANSLUCENT));
    }

    private void hideLock() {
        if (lockView != null) { try { wm.removeView(lockView); } catch (Exception ignored) {} lockView = null; lockPkg = null; }
    }

    // ---------- Автокликер ----------
    void showPanel() {
        if (panel != null) return;
        LinearLayout p = new LinearLayout(this);
        p.setBackgroundColor(0xE6202830); p.setGravity(Gravity.CENTER_VERTICAL);
        TextView handle = new TextView(this); handle.setText(" ≡ "); handle.setTextSize(22); handle.setTextColor(0xFFFFFFFF);
        count = new TextView(this); count.setTextColor(0xFFFFFFFF); count.setPadding(Store.dp(this, 6), 0, Store.dp(this, 6), 0);
        recBtn = Store.btn(this, "●"); playBtn = Store.btn(this, "▶");
        Button clr = Store.btn(this, "⌫"), close = Store.btn(this, "✕");
        recBtn.setOnClickListener(v -> { if (recording) stopRec(); else startRec(); });
        playBtn.setOnClickListener(v -> { if (playing) stopPlay(); else startPlay(); });
        clr.setOnClickListener(v -> { stopPlay(); pts.clear(); upd(); if (layer != null) layer.invalidate(); });
        close.setOnClickListener(v -> closePanel());
        p.addView(handle); p.addView(count); p.addView(recBtn); p.addView(playBtn); p.addView(clr); p.addView(close);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(-2, -2, OVL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START; lp.x = Store.dp(this, 8); lp.y = Store.dp(this, 200);
        handle.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) { dx = lp.x - e.getRawX(); dy = lp.y - e.getRawY(); }
                else if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    lp.x = (int) (e.getRawX() + dx); lp.y = (int) (e.getRawY() + dy); wm.updateViewLayout(panel, lp);
                }
                return true;
            }
        });
        panel = p; panelLp = lp;
        wm.addView(panel, lp);
        upd();
    }

    private void closePanel() {
        stopPlay(); stopRec();
        if (panel != null) { try { wm.removeView(panel); } catch (Exception ignored) {} panel = null; }
    }

    private void upd() {
        if (panel == null) return;
        count.setText(String.valueOf(pts.size()));
        recBtn.setText(recording ? "■" : "●");
        playBtn.setText(playing ? "⏸" : "▶");
    }

    private void startRec() {
        stopPlay(); recording = true;
        layer = new View(this) {
            final Paint pa = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                int[] o = new int[2]; getLocationOnScreen(o);
                pa.setTextAlign(Paint.Align.CENTER); pa.setTextSize(Store.dp(HelperService.this, 14));
                for (int i = 0; i < pts.size(); i++) {
                    float x = pts.get(i)[0] - o[0], y = pts.get(i)[1] - o[1];
                    pa.setColor(0xCCFFC107); c.drawCircle(x, y, Store.dp(HelperService.this, 16), pa);
                    pa.setColor(0xFF000000); c.drawText(String.valueOf(i + 1), x, y + Store.dp(HelperService.this, 5), pa);
                }
            }
            @Override public boolean onTouchEvent(MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_DOWN) { pts.add(new float[]{e.getRawX(), e.getRawY()}); upd(); invalidate(); }
                return true;
            }
        };
        layer.setBackgroundColor(0x33FF0000);
        wm.removeView(panel);
        wm.addView(layer, new WindowManager.LayoutParams(-1, -1, OVL, FULL, PixelFormat.TRANSLUCENT));
        wm.addView(panel, panelLp);
        upd();
    }

    private void stopRec() {
        recording = false;
        if (layer != null) { try { wm.removeView(layer); } catch (Exception ignored) {} layer = null; }
        upd();
    }

    private void startPlay() {
        if (pts.isEmpty()) { Toast.makeText(this, "Сначала запишите касания (●)", Toast.LENGTH_SHORT).show(); return; }
        if (recording) stopRec();
        playing = true; idx = 0; upd(); tick();
    }

    private void stopPlay() { playing = false; h.removeCallbacks(tickR); upd(); }

    private void tick() {
        if (!playing || pts.isEmpty()) return;
        float[] q = pts.get(idx++ % pts.size());
        Path path = new Path(); path.moveTo(q[0], q[1]);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 40)).build();
        boolean ok = dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription d) { next(); }
            @Override public void onCancelled(GestureDescription d) { next(); }
        }, null);
        if (!ok) next();
    }

    private void next() {
        if (playing) h.postDelayed(tickR, Math.max(10, Store.p(this).getInt("interval", 200)));
    }
}
