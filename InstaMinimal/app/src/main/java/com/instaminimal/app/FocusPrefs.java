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

    // The reward (in seconds of Reels) is fixed when the timer starts:
    // rate = minutes of Reels per hour of study, so each study minute earns "rate" seconds
    public void startSession(int minutes) {
        p.edit().putLong("sessionEnd", System.currentTimeMillis() + minutes * 60000L)
                .putLong("sessionReward", minutes * (long) rewardRate())
                .apply();
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

    // Unlock: ends the study timer (no reward) and unlocks the rest of the current window
    public void unlock() {
        long w = windowEndMs();
        p.edit().putLong("sessionEnd", 0)
                .putLong("sessionReward", 0)
                .putLong("winUnlocked", w > 0 ? w : windowUnlockedUntil())
                .apply();
    }

    // ---- Reel rewards ----

    // Minutes of Reels earned per hour of study (0 = rewards off)
    public int rewardRate() { return p.getInt("rewardRate", 5); }
    public void setRewardRate(int v) { p.edit().putInt("rewardRate", v).apply(); }

    // Saved-up Reel time that has not been spent yet (seconds)
    public long bankSec() { return p.getLong("bankSec", 0); }

    // Reel time that is currently being spent
    public boolean reelOn() { return p.getBoolean("reelOn", false); }
    public long reelLeftMs() { return p.getLong("reelLeftMs", 0); }
    public void setReelLeftMs(long ms) { p.edit().putLong("reelLeftMs", ms).apply(); }

    // If a study timer has finished, move its reward into the bank. Returns seconds earned.
    public long collectReward() {
        long end = sessionEnd();
        if (end > 0 && System.currentTimeMillis() >= end) {
            long earned = p.getLong("sessionReward", 0);
            p.edit().putLong("sessionEnd", 0)
                    .putLong("sessionReward", 0)
                    .putLong("bankSec", bankSec() + earned)
                    .apply();
            return earned;
        }
        return 0;
    }

    // Spend the whole bank as one Reel session
    public boolean startReels() {
        long b = bankSec();
        if (b <= 0 || reelOn()) return false;
        p.edit().putBoolean("reelOn", true)
                .putLong("reelLeftMs", b * 1000)
                .putLong("bankSec", 0)
                .apply();
        return true;
    }

    // Stop early and save what is left back into the bank
    public void stopReels(long leftMs) {
        long sec = Math.max(0, leftMs / 1000);
        p.edit().putBoolean("reelOn", false)
                .putLong("reelLeftMs", 0)
                .putLong("bankSec", bankSec() + sec)
                .apply();
    }

    // Time is up
    public void endReels() {
        p.edit().putBoolean("reelOn", false).putLong("reelLeftMs", 0).apply();
    }

    public static String fmt(int minutes) {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
