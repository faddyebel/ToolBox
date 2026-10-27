package com.toolbox.app;

import android.app.Activity;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Простой лаунчер: показывает все приложения, кроме спрятанных в тайник. */
public class HomeActivity extends Activity {
    @Override protected void onResume() { super.onResume(); build(); }
    @Override public void onBackPressed() {}

    private void build() {
        final HomeActivity cx = this;
        Set<String> hid = Store.set(this, "hidden");
        List<Apps.App> shown = new ArrayList<>();
        for (Apps.App a : Apps.load(this)) if (!hid.contains(a.pkg)) shown.add(a);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(this);
        android.widget.Button v1 = Store.btn(this, "Тайник"), v2 = Store.btn(this, "Toolbox");
        v1.setOnClickListener(v -> startActivity(new Intent(this, VaultActivity.class)));
        v2.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        bar.addView(v1, new LinearLayout.LayoutParams(0, -2, 1));
        bar.addView(v2, new LinearLayout.LayoutParams(0, -2, 1));
        GridView g = new GridView(this); g.setNumColumns(4);
        g.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return shown.size(); }
            @Override public Object getItem(int i) { return shown.get(i); }
            @Override public long getItemId(int i) { return i; }
            @Override public View getView(int i, View cv, ViewGroup p) {
                Apps.App a = shown.get(i);
                LinearLayout c = new LinearLayout(cx); c.setOrientation(LinearLayout.VERTICAL); c.setGravity(Gravity.CENTER_HORIZONTAL);
                c.setPadding(4, Store.dp(cx, 10), 4, Store.dp(cx, 10));
                ImageView iv = new ImageView(cx); iv.setImageDrawable(a.icon);
                c.addView(iv, new LinearLayout.LayoutParams(Store.dp(cx, 48), Store.dp(cx, 48)));
                TextView t = new TextView(cx); t.setText(a.label); t.setTextSize(11); t.setMaxLines(1); t.setGravity(Gravity.CENTER);
                c.addView(t);
                return c;
            }
        });
        g.setOnItemClickListener((p, v, pos, id) -> {
            Intent i = getPackageManager().getLaunchIntentForPackage(shown.get(pos).pkg);
            if (i != null) startActivity(i);
        });
        root.addView(bar); root.addView(g, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }
}
