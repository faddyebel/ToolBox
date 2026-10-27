package com.toolbox.app;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class Apps {
    static final class App {
        final String pkg; final CharSequence label; final Drawable icon;
        App(String p, CharSequence l, Drawable i) { pkg = p; label = l; icon = i; }
    }

    static List<App> load(Context c) {
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> l = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        Map<String, App> m = new LinkedHashMap<>();
        for (ResolveInfo r : l) {
            String p = r.activityInfo.packageName;
            if (!p.equals(c.getPackageName()) && !m.containsKey(p)) m.put(p, new App(p, r.loadLabel(pm), r.loadIcon(pm)));
        }
        List<App> out = new ArrayList<>(m.values());
        Collections.sort(out, (a, b) -> a.label.toString().compareToIgnoreCase(b.label.toString()));
        return out;
    }
}
