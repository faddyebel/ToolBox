package com.toolbox.app;

import android.content.Context;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class PinView extends LinearLayout {
    interface Listener { boolean onPin(String pin); void onCancel(); }

    private final StringBuilder sb = new StringBuilder();
    private final TextView title, dots;
    private final Listener l;

    PinView(Context c, String t, Listener l) {
        super(c);
        this.l = l;
        setOrientation(VERTICAL); setGravity(Gravity.CENTER); setBackgroundColor(0xFF101418); setClickable(true);
        title = new TextView(c); title.setText(t); title.setTextSize(20); title.setTextColor(0xFFFFFFFF); title.setGravity(Gravity.CENTER);
        addView(title, new LayoutParams(-1, -2));
        dots = new TextView(c); dots.setTextSize(32); dots.setTextColor(0xFFFFFFFF); dots.setGravity(Gravity.CENTER);
        addView(dots, new LayoutParams(-1, Store.dp(c, 56)));
        String[][] k = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"←", "0", "OK"}};
        int m = Store.dp(c, 4);
        for (String[] r : k) {
            LinearLayout row = new LinearLayout(c);
            for (String s : r) {
                Button b = Store.btn(c, s); b.setTextSize(22);
                b.setOnClickListener(v -> press(s));
                LayoutParams lp = new LayoutParams(0, Store.dp(c, 64), 1); lp.setMargins(m, m, m, m);
                row.addView(b, lp);
            }
            addView(row, new LayoutParams(Store.dp(c, 300), -2));
        }
        Button cancel = Store.btn(c, "Отмена");
        cancel.setOnClickListener(v -> this.l.onCancel());
        LayoutParams cl = new LayoutParams(Store.dp(c, 300), -2); cl.topMargin = Store.dp(c, 12);
        addView(cancel, cl);
        refresh();
    }

    void setTitle(String t) { title.setText(t); }

    private void press(String s) {
        if (s.equals("←")) { if (sb.length() > 0) sb.setLength(sb.length() - 1); }
        else if (s.equals("OK")) {
            if (sb.length() >= 4) {
                String p = sb.toString(); sb.setLength(0);
                if (!l.onPin(p)) title.setText("Неверный PIN");
            }
        } else if (sb.length() < 8) sb.append(s);
        refresh();
    }

    private void refresh() { dots.setText(new String(new char[sb.length()]).replace('\0', '•')); }
}
