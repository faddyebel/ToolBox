package com.toolbox.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class VaultActivity extends Activity {
    private PinView pv;
    private String first;
    private boolean vault;
    private List<Apps.App> apps;
    private ListView lv;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        pv = new PinView(this, Store.hasPin(this) ? "Введите PIN" : "Придумайте PIN (4–8 цифр)", new PinView.Listener() {
            @Override public boolean onPin(String p) {
                if (Store.hasPin(VaultActivity.this)) {
                    if (!Store.checkPin(VaultActivity.this, p)) return false;
                    show(); return true;
                }
                if (first == null) { first = p; pv.setTitle("Повторите PIN"); return true; }
                if (!first.equals(p)) { first = null; pv.setTitle("PIN не совпал. Придумайте заново"); return true; }
                Store.setPin(VaultActivity.this, p); show(); return true;
            }
            @Override public void onCancel() { finish(); }
        });
        setContentView(pv);
    }

    private void show() {
        apps = Apps.load(this);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout tabs = new LinearLayout(this);
        Button a = Store.btn(this, "Приложения"), t = Store.btn(this, "Тайник");
        a.setOnClickListener(v -> { vault = false; fill(); });
        t.setOnClickListener(v -> { vault = true; fill(); });
        tabs.addView(a, new LinearLayout.LayoutParams(0, -2, 1));
        tabs.addView(t, new LinearLayout.LayoutParams(0, -2, 1));
        lv = new ListView(this);
        root.addView(tabs); root.addView(lv, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        fill();
    }

    private void fill() {
        final VaultActivity cx = this;
        Set<String> hid = Store.set(this, "hidden"), lk = Store.set(this, "locked");
        List<Apps.App> shown = new ArrayList<>();
        for (Apps.App a : apps) if (hid.contains(a.pkg) == vault) shown.add(a);
        lv.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup parent) {
                Apps.App a = shown.get(i);
                LinearLayout row = new LinearLayout(cx);
                row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(Store.dp(cx, 12), Store.dp(cx, 6), Store.dp(cx, 12), Store.dp(cx, 6));
                ImageView iv = new ImageView(cx); iv.setImageDrawable(a.icon);
                row.addView(iv, new LinearLayout.LayoutParams(Store.dp(cx, 40), Store.dp(cx, 40)));
                TextView tv = new TextView(cx); tv.setText(a.label); tv.setTextSize(16); tv.setPadding(Store.dp(cx, 12), 0, 0, 0);
                row.addView(tv, new LinearLayout.LayoutParams(0, -2, 1));
                if (!vault) {
                    CheckBox cb = new CheckBox(cx); cb.setText("Замок"); cb.setChecked(lk.contains(a.pkg));
                    cb.setOnCheckedChangeListener((v, on) -> Store.toggle(cx, "locked", a.pkg, on));
                    Button hb = Store.btn(cx, "Скрыть");
                    hb.setOnClickListener(v -> { Store.toggle(cx, "hidden", a.pkg, true); fill(); });
                    row.addView(cb); row.addView(hb);
                } else {
                    row.setOnClickListener(v -> launch(a.pkg));
                    Button ub = Store.btn(cx, "Вернуть");
                    ub.setOnClickListener(v -> { Store.toggle(cx, "hidden", a.pkg, false); fill(); });
                    row.addView(ub);
                }
                return row;
            }
        });
    }

    private void launch(String pkg) {
        Store.p(this).edit().putString("skip", pkg).putLong("skipT", System.currentTimeMillis()).apply();
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) startActivity(i);
    }
}
