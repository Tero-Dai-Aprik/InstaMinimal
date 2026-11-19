package com.instaminimal.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;
import java.util.Locale;

public class FocusPrefs {
    public static final int EASY = 0, MEDIUM = 1, HARD = 2;

    private final SharedPreferences p;

    public FocusPrefs(Context c) {
        p = c.getSharedPreferences("focus", Context.MODE_PRIVATE);
    }

    public boolean enabled() { return p.getBoolean("enabled", false); }
    public int startMin()    { return p.getInt("start", 22 * 60); }
    public int endMin()      { return p.getInt("end", 7 * 60); }
    public int mode()        { return p.getInt("mode", EASY); }

    public void save(boolean enabled, int start, int end, int mode) {
        p.edit().putBoolean("enabled", enabled)
                .putInt("start", start)
                .putInt("end", end)
                .putInt("mode", mode)
                .apply();
    }

    // True if the phone's clock is inside the focus window right now
    public boolean activeNow() {
        if (!enabled()) return false;
        Calendar c = Calendar.getInstance();
        int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int s = startMin(), e = endMin();
        if (s == e) return false;
        return s < e ? (m >= s && m < e) : (m >= s || m < e);   // handles 22:00 to 07:00
    }

    public static String fmt(int minutes) {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
