package com.instaminimal.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class LockOverlayView extends LinearLayout {

    public interface OnUnlockListener {
        void onUnlocked();
    }

    private final FocusPrefs prefs;
    private final OnUnlockListener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView statusText;
    private TextView timerText;
    private Button primaryBtn;
    private Button cancelBtn;

    private int secondsLeft = 60;
    private boolean countingDown = false;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            if (!countingDown) return;
            secondsLeft--;
            if (secondsLeft <= 0) {
                countingDown = false;
                if (listener != null) listener.onUnlocked();
                return;
            }
            timerText.setText("Unlocking in " + secondsLeft + "s...");
            handler.postDelayed(this, 1000L);
        }
    };

    public LockOverlayView(Context context, FocusPrefs prefs, OnUnlockListener listener) {
        super(context);
        this.prefs = prefs;
        this.listener = listener;
        init(context);
    }

    private void init(Context context) {
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setBackgroundColor(Color.parseColor("#0F172A"));
        setClickable(true);
        setFocusable(true);

        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (24 * d);
        setPadding(pad, pad, pad, pad);

        TextView title = new TextView(context);
        title.setText("Focus Mode Active");
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        addView(title);

        addSpacer(context, 10);

        statusText = new TextView(context);
        statusText.setTextColor(Color.parseColor("#94A3B8"));
        statusText.setTextSize(15);
        statusText.setGravity(Gravity.CENTER);
        addView(statusText);

        addSpacer(context, 14);

        timerText = new TextView(context);
        timerText.setTextColor(Color.parseColor("#38BDF8"));
        timerText.setTextSize(18);
        timerText.setTypeface(null, Typeface.BOLD);
        timerText.setGravity(Gravity.CENTER);
        timerText.setVisibility(GONE);
        addView(timerText);

        addSpacer(context, 24);

        primaryBtn = new Button(context);
        primaryBtn.setVisibility(GONE);
        addView(primaryBtn);

        addSpacer(context, 10);

        cancelBtn = new Button(context);
        cancelBtn.setText("Cancel timer");
        cancelBtn.setVisibility(GONE);
        cancelBtn.setOnClickListener(v -> resetMediumTimer());
        addView(cancelBtn);

        configureForMode();
    }

    private void addSpacer(Context context, int dp) {
        View v = new View(context);
        float d = getResources().getDisplayMetrics().density;
        addView(v, new LayoutParams(1, (int) (dp * d)));
    }

    private void resetMediumTimer() {
        countingDown = false;
        handler.removeCallbacks(tickRunnable);
        secondsLeft = 60;
        timerText.setVisibility(GONE);
        cancelBtn.setVisibility(GONE);
        primaryBtn.setEnabled(true);
        primaryBtn.setText("Start 1-minute unlock");
    }

    public void configureForMode() {
        stopTimers();
        int mode = prefs.mode();
        String window = FocusPrefs.fmt(prefs.startMin()) + " – " + FocusPrefs.fmt(prefs.endMin());

        if (mode == FocusPrefs.HARD) {
            statusText.setText("Impossible mode is on (" + window + ").\nLocked until focus hours end.");
            timerText.setVisibility(GONE);
            primaryBtn.setVisibility(GONE);
            cancelBtn.setVisibility(GONE);
        } else if (mode == FocusPrefs.MEDIUM) {
            statusText.setText("Medium mode (" + window + ").\nConfirm to start a 60-second wait.");
            timerText.setVisibility(GONE);
            cancelBtn.setVisibility(GONE);
            primaryBtn.setVisibility(VISIBLE);
            primaryBtn.setEnabled(true);
            primaryBtn.setText("Start 1-minute unlock");
            primaryBtn.setOnClickListener(v -> {
                secondsLeft = 60;
                countingDown = true;
                primaryBtn.setEnabled(false);
                timerText.setText("Unlocking in " + secondsLeft + "s...");
                timerText.setVisibility(VISIBLE);
                cancelBtn.setVisibility(VISIBLE);
                handler.removeCallbacks(tickRunnable);
                handler.postDelayed(tickRunnable, 1000L);
            });
        } else {
            statusText.setText("Easy mode (" + window + ").");
            timerText.setVisibility(GONE);
            cancelBtn.setVisibility(GONE);
            primaryBtn.setVisibility(VISIBLE);
            primaryBtn.setEnabled(true);
            primaryBtn.setText("Unlock now");
            primaryBtn.setOnClickListener(v -> {
                if (listener != null) listener.onUnlocked();
            });
        }
    }

    public void stopTimers() {
        countingDown = false;
        handler.removeCallbacks(tickRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopTimers();
    }
}
