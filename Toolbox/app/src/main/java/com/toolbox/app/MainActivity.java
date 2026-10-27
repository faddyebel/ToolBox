package com.toolbox.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private LinearLayout r;
    private Button bugBtn;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        r = new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL);
        int pad = Store.dp(this, 16); r.setPadding(pad, pad, pad, pad);
        sv.addView(r); setContentView(sv);

        head("1. Автокликер");
        bar("Интервал между касаниями, мс", 20, 3000, "interval", 200, null);
        Button panel = Store.btn(this, "Показать панель автокликера");
        panel.setOnClickListener(v -> {
            if (HelperService.inst == null) {
                Toast.makeText(this, "Включите службу «Toolbox» в спец. возможностях", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } else HelperService.inst.showPanel();
        });
        r.addView(panel);
        TextView hint = new TextView(this);
        hint.setText("● запись (тапайте по экрану), ▶ старт/стоп, ⌫ очистить, ✕ закрыть, ≡ перетащить панель");
        r.addView(hint);

        head("2. Насекомые");
        bar("Скорость", 30, 500, "bspeed", 140, null);
        Runnable restart = () -> { if (BugsService.running) startBugs(); };
        bar("Количество", 1, 25, "bcount", 8, restart);
        bar("Размер", 24, 120, "bsize", 56, restart);
        bugBtn = Store.btn(this, "");
        bugBtn.setOnClickListener(v -> {
            if (BugsService.running) { stopService(new Intent(this, BugsService.class)); bugBtn.setText("Запустить насекомых"); }
            else startBugs();
        });
        r.addView(bugBtn);

        head("3. Тайник приложений");
        Button vault = Store.btn(this, "Открыть тайник и замки");
        vault.setOnClickListener(v -> startActivity(new Intent(this, VaultActivity.class)));
        Button acc = Store.btn(this, "Включить службу блокировки (спец. возможности)");
        acc.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        Button home = Store.btn(this, "Назначить лаунчером (чтобы скрытые не были видны)");
        home.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_HOME_SETTINGS)));
        r.addView(vault); r.addView(acc); r.addView(home);
    }

    @Override protected void onResume() {
        super.onResume();
        bugBtn.setText(BugsService.running ? "Остановить насекомых" : "Запустить насекомых");
    }

    private void startBugs() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Разрешите показ поверх других окон", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            return;
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        startForegroundService(new Intent(this, BugsService.class));
        bugBtn.setText("Остановить насекомых");
    }

    private void head(String t) {
        TextView v = new TextView(this); v.setText(t); v.setTextSize(18); v.setPadding(0, Store.dp(this, 20), 0, Store.dp(this, 4));
        r.addView(v);
    }

    private void bar(String label, int min, int max, String key, int def, Runnable done) {
        int val = Store.p(this).getInt(key, def);
        TextView t = new TextView(this); t.setText(label + ": " + val); r.addView(t);
        SeekBar s = new SeekBar(this); s.setMax(max - min); s.setProgress(val - min);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean u) {
                t.setText(label + ": " + (p + min));
                Store.p(MainActivity.this).edit().putInt(key, p + min).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) { if (done != null) done.run(); }
        });
        r.addView(s);
    }
}
