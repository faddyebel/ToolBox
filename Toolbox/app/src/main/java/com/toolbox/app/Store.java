package com.toolbox.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Button;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class Store {
    static SharedPreferences p(Context c) { return c.getSharedPreferences("toolbox", Context.MODE_PRIVATE); }
    static int dp(Context c, float v) { return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f); }
    static boolean hasPin(Context c) { return p(c).contains("pin"); }

    private static String hash(Context c, String pin) {
        SharedPreferences sp = p(c);
        String salt = sp.getString("salt", null);
        if (salt == null) { salt = UUID.randomUUID().toString(); sp.edit().putString("salt", salt).apply(); }
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest((salt + pin).getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : d) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    static void setPin(Context c, String pin) { p(c).edit().putString("pin", hash(c, pin)).apply(); }
    static boolean checkPin(Context c, String pin) { return hash(c, pin).equals(p(c).getString("pin", "")); }

    static Set<String> set(Context c, String k) { return new HashSet<>(p(c).getStringSet(k, new HashSet<>())); }
    static void toggle(Context c, String k, String pkg, boolean on) {
        Set<String> s = set(c, k);
        if (on) s.add(pkg); else s.remove(pkg);
        p(c).edit().putStringSet(k, s).apply();
    }
    static Set<String> guarded(Context c) { Set<String> s = set(c, "locked"); s.addAll(set(c, "hidden")); return s; }

    static Button btn(Context c, String t) {
        Button b = new Button(c);
        b.setText(t); b.setAllCaps(false); b.setTextColor(0xFFFFFFFF); b.setBackgroundColor(0xFF2A3139);
        b.setMinWidth(0); b.setMinimumWidth(0);
        return b;
    }
}
