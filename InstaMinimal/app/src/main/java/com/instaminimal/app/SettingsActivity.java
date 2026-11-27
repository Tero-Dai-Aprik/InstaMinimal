package com.instaminimal.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SettingsActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final String BG = "#0F172A";
    private static final String CARD = "#1E293B";
    private static final String ACCENT = "#0F766E";
    private static final String TEAL = "#2DD4BF";
    private static final String MUTED = "#94A3B8";

    private static final int[] PRESETS = {30, 60, 120, 180};
    private static final String[] PRESET_LABELS = {"30m", "1h", "2h", "3h"};

    private FocusPrefs prefs;
    private boolean locked;
    private int startMin, endMin, mode;
    private int timerMinutes = 60;
    private boolean customChosen = false;

    private TextView startTime, endTime;
    private Button startTimerBtn;
    private Switch scheduleSwitch;
    private final LinearLayout[] modeRows = new LinearLayout[3];
    private final TextView[] chips = new TextView[5];
    private static final int[] RATES = {0, 5, 10, 15};
    private final TextView[] rateChips = new TextView[4];
    private TextView rateInfo;

    // ---------- small helpers ----------

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(String fill, int radiusDp, String stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(fill));
        g.setCornerRadius(dp(radiusDp));
        if (stroke != null) g.setStroke(dp(2), Color.parseColor(stroke));
        return g;
    }

    private TextView text(String s, int sp, boolean bold, String color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private TextView leftText(String s, int sp, boolean bold, String color) {
        TextView t = text(s, sp, bold, color);
        t.setGravity(Gravity.START);
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = dp(topDp);
        return p;
    }

    private Button button(String label, String color) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackground(rounded(color, 14, null));
        return b;
    }

    private LinearLayout card(LinearLayout parent, String heading) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(CARD, 18, null));
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        if (locked) c.setAlpha(0.5f);
        c.addView(leftText(heading, 17, true, "#FFFFFF"));
        parent.addView(c, lp(MATCH, WRAP, 14));
        return c;
    }

    private LinearLayout pill(String caption, TextView timeView, View.OnClickListener click) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setGravity(Gravity.CENTER);
        p.setBackground(rounded("#162033", 14, "#334155"));
        p.setPadding(dp(10), dp(12), dp(10), dp(12));
        p.addView(text(caption, 12, false, MUTED));
        p.addView(timeView);
        p.setOnClickListener(click);
        return p;
    }

    private static String durText(int min) {
        if (min < 60) return min + " min";
        if (min % 60 == 0) return (min / 60) + " h";
        return (min / 60) + " h " + (min % 60) + " min";
    }

    private static String clock(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ms));
    }

    private void confirm(String title, String msg, String yes, final Runnable ok) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton(yes, (dlg, w) -> ok.run())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- refresh the look of selectable things ----------

    private void refreshModes() {
        for (int i = 0; i < 3; i++) {
            boolean sel = (i == mode);
            modeRows[i].setBackground(rounded(sel ? "#123F3B" : "#162033", 14, sel ? TEAL : "#334155"));
        }
    }

    private void refreshTimes() {
        startTime.setText(FocusPrefs.fmt(startMin));
        endTime.setText(FocusPrefs.fmt(endMin));
    }

    private void refreshChips() {
        for (int i = 0; i < 5; i++) {
            boolean sel = (i < 4) ? (!customChosen && timerMinutes == PRESETS[i]) : customChosen;
            chips[i].setBackground(rounded(sel ? ACCENT : "#162033", 12, sel ? TEAL : "#334155"));
            if (i < 4) chips[i].setText(PRESET_LABELS[i]);
            else chips[i].setText(customChosen ? durText(timerMinutes) : "Custom");
        }
        startTimerBtn.setText("Start focus timer - " + durText(timerMinutes));
    }

    private void askCustom() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Minutes (1 to 720)");
        new AlertDialog.Builder(this)
                .setTitle("Custom timer")
                .setView(input)
                .setPositiveButton("Set", (dlg, w) -> {
                    try {
                        int m = Integer.parseInt(input.getText().toString().trim());
                        timerMinutes = Math.max(1, Math.min(720, m));
                        customChosen = true;
                        refreshChips();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Enter a number of minutes", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- screen ----------
        private void refreshRates() {
        int cur = prefs.rewardRate();
        String[] labels = {"Off", "5 min", "10 min", "15 min"};
        for (int i = 0; i < 4; i++) {
            boolean sel = (RATES[i] == cur);
            rateChips[i].setText(labels[i]);
            rateChips[i].setBackground(rounded(sel ? ACCENT : "#162033", 12, sel ? TEAL : "#334155"));
        }
        rateInfo.setText(cur == 0
                ? "Reel rewards are off."
                : "Study for 1 hour with the focus timer and earn " + cur + " min of Reels.");
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = new FocusPrefs(this);
        locked = prefs.lockEndMs() > 0 && prefs.mode() != FocusPrefs.EASY;
        startMin = prefs.startMin();
        endMin = prefs.endMin();
        mode = prefs.mode();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor(BG));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(18), dp(22), dp(18), dp(28));
        scroll.addView(col);

        col.addView(leftText("Focus", 30, true, "#FFFFFF"));
        col.addView(leftText("Lock Instagram when you need to concentrate.", 14, false, MUTED), lp(MATCH, WRAP, 4));

        if (locked) {
            TextView banner = leftText("Settings are locked until " + clock(prefs.lockEndMs()) + ".", 14, true, "#FBBF24");
            banner.setBackground(rounded("#3B2A0B", 12, null));
            banner.setPadding(dp(14), dp(12), dp(14), dp(12));
            col.addView(banner, lp(MATCH, WRAP, 14));
        }

        // ----- Mode card -----
        LinearLayout modeCard = card(col, "Mode");
        String[] titles = {"Easy", "Medium", "Impossible"};
        String[] descs = {
                "Unlock any time with one tap",
                "Confirm, then wait 1 minute. You can cancel",
                "No unlocking until the timer ends"
        };
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.addView(leftText(titles[i], 16, true, "#FFFFFF"));
            row.addView(leftText(descs[i], 13, false, MUTED));
            row.setOnClickListener(view -> {
                if (locked) return;
                mode = idx;
                refreshModes();
            });
            modeRows[i] = row;
            modeCard.addView(row, lp(MATCH, WRAP, 10));
        }

        // ----- Schedule card -----
        LinearLayout schedCard = card(col, "Daily schedule");
        schedCard.addView(leftText("Instagram locks between these times every day.", 13, false, MUTED), lp(MATCH, WRAP, 4));

        scheduleSwitch = new Switch(this);
        scheduleSwitch.setText("Repeat every day");
        scheduleSwitch.setTextColor(Color.WHITE);
        scheduleSwitch.setTextSize(16);
        scheduleSwitch.setChecked(prefs.enabled());
        if (locked) scheduleSwitch.setEnabled(false);
        schedCard.addView(scheduleSwitch, lp(MATCH, WRAP, 12));

        startTime = text("", 24, true, "#FFFFFF");
        endTime = text("", 24, true, "#FFFFFF");

        LinearLayout timesRow = new LinearLayout(this);
        timesRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout startPill = pill("Start", startTime, view -> {
            if (locked) return;
            new TimePickerDialog(this, (tp, h, m) -> {
                startMin = h * 60 + m;
                refreshTimes();
            }, startMin / 60, startMin % 60, true).show();
        });
        LinearLayout endPill = pill("End", endTime, view -> {
            if (locked) return;
            new TimePickerDialog(this, (tp, h, m) -> {
                endMin = h * 60 + m;
                refreshTimes();
            }, endMin / 60, endMin % 60, true).show();
        });
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, WRAP, 1f);
        left.rightMargin = dp(6);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, WRAP, 1f);
        right.leftMargin = dp(6);
        timesRow.addView(startPill, left);
        timesRow.addView(endPill, right);
        schedCard.addView(timesRow, lp(MATCH, WRAP, 12));

        // ----- Focus timer card -----
        LinearLayout timerCard = card(col, "Focus timer");
        timerCard.addView(leftText("Studying? Lock Instagram for a fixed time.", 13, false, MUTED), lp(MATCH, WRAP, 4));

        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            TextView chip = text("", 14, true, "#FFFFFF");
            chip.setPadding(0, dp(10), 0, dp(10));
            chip.setOnClickListener(view -> {
                if (locked) return;
                if (idx < 4) {
                    timerMinutes = PRESETS[idx];
                    customChosen = false;
                    refreshChips();
                } else {
                    askCustom();
                }
            });
            chips[i] = chip;
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            cp.rightMargin = (i < 4) ? dp(6) : 0;
            chipRow.addView(chip, cp);
        }
        timerCard.addView(chipRow, lp(MATCH, WRAP, 12));

        startTimerBtn = button("Start focus timer", ACCENT);
        startTimerBtn.setOnClickListener(view -> {
            if (locked) return;
            final Runnable go = () -> {
                prefs.save(scheduleSwitch.isChecked(), startMin, endMin, mode);
                prefs.startSession(timerMinutes);
                finish();
            };
            if (mode == FocusPrefs.HARD) {
                confirm("Impossible mode",
                        "Instagram will be locked for " + durText(timerMinutes) + " and you will not be able to unlock it. Continue?",
                        "Start", go);
            } else {
                go.run();
            }
        });
        timerCard.addView(startTimerBtn, lp(MATCH, WRAP, 14));

                // ----- Reel rewards card -----
        LinearLayout rewardCard = card(col, "Reel rewards");
        rateInfo = leftText("", 13, false, MUTED);
        rewardCard.addView(rateInfo, lp(MATCH, WRAP, 4));
        LinearLayout rateRow = new LinearLayout(this);
        rateRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            TextView rateChip = text("", 14, true, "#FFFFFF");
            rateChip.setPadding(0, dp(10), 0, dp(10));
            rateChip.setOnClickListener(view -> {
                if (locked) return;
                prefs.setRewardRate(RATES[idx]);
                refreshRates();
            });
            rateChips[i] = rateChip;
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, WRAP, 1f);
            rp.rightMargin = (i < 3) ? dp(6) : 0;
            rateRow.addView(rateChip, rp);
        }
        rewardCard.addView(rateRow, lp(MATCH, WRAP, 12));

        // ----- Save and close -----
        if (!locked) {
            Button save = button("Save focus hours", "#334155");
            save.setOnClickListener(view -> {
                final boolean on = scheduleSwitch.isChecked();
                final Runnable doSave = () -> {
                    prefs.save(on, startMin, endMin, mode);
                    Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                    finish();
                };
                if (on && mode == FocusPrefs.HARD) {
                    confirm("Impossible mode",
                            "During focus hours you will not be able to unlock Instagram or change these settings. Continue?",
                            "Save", doSave);
                } else {
                    doSave.run();
                }
            });
            col.addView(save, lp(MATCH, WRAP, 18));
        }

        TextView close = text("Close", 15, true, TEAL);
        close.setPadding(0, dp(14), 0, dp(14));
        close.setOnClickListener(view -> finish());
        col.addView(close, lp(MATCH, WRAP, 6));

        refreshModes();
        refreshTimes();
        refreshChips();
        setContentView(scroll);
    }
}
