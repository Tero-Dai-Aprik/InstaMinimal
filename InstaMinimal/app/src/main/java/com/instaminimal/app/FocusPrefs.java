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

    // ---- Daily schedule ----

    // True if the phone's clock is inside the focus window right now
    public boolean activeNow() {
        if (!enabled()) return false;
        Calendar c = Calendar.getInstance();
        int m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
        int s = startMin(), e = endMin();
        if (s == e) return false;
        return s < e ? (m >= s && m < e) : (m >= s || m < e);   // handles 22:00 to 07:00
    }

    // When the current window ends (milliseconds), or 0 if not inside a window
    public long windowEndMs() {
        if (!activeNow()) return 0;
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, endMin() / 60);
        c.set(Calendar.MINUTE, endMin() % 60);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis()) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    private long windowUnlockedUntil() { return p.getLong("winUnlocked", 0); }

    // ---- Focus timer (one-off study session) ----

    public long sessionEnd() { return p.getLong("sessionEnd", 0); }

    public boolean sessionActive() { return sessionEnd() > System.currentTimeMillis(); }

    public void startSession(int minutes) {
        p.edit().putLong("sessionEnd", System.currentTimeMillis() + minutes * 60000L).apply();
    }

    // ---- Lock state ----

    // When the current lock ends (milliseconds), or 0 if Instagram is not locked
    public long lockEndMs() {
        long now = System.currentTimeMillis();
        long end = 0;
        if (sessionEnd() > now) end = sessionEnd();
        long w = windowEndMs();
        if (w > 0 && windowUnlockedUntil() < w) end = Math.max(end, w);
        return end;
    }

    // Unlock: ends the study timer and unlocks the rest of the current window
    public void unlock() {
        long w = windowEndMs();
        p.edit().putLong("sessionEnd", 0)
                .putLong("winUnlocked", w > 0 ? w : windowUnlockedUntil())
                .apply();
    }

    public static String fmt(int minutes) {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
