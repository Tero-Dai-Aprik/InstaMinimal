package com.instaminimal.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private int startMin, endMin;
    private Button startBtn, endBtn;

    private TextView label(String text, int sizeSp, boolean bold, String color) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(sizeSp);
        t.setTextColor(Color.parseColor(color));
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private void gap(LinearLayout col, int dp) {
        View v = new View(this);
        float d = getResources().getDisplayMetrics().density;
        col.addView(v, new LinearLayout.LayoutParams(1, (int) (dp * d)));
    }

    private void updateTimeButtons() {
        startBtn.setText("Start: " + FocusPrefs.fmt(startMin));
        endBtn.setText("End: " + FocusPrefs.fmt(endMin));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final FocusPrefs prefs = new FocusPrefs(this);
        final float d = getResources().getDisplayMetrics().density;
        final boolean locked = prefs.activeNow() && prefs.mode() != FocusPrefs.EASY;

        startMin = prefs.startMin();
        endMin = prefs.endMin();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#0F172A"));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * d);
        col.setPadding(pad, pad, pad, pad);
        scroll.addView(col);

        col.addView(label("Focus hours", 26, true, "#FFFFFF"));
        gap(col, 6);
        col.addView(label("During focus hours the app shows a lock screen instead of Instagram.", 14, false, "#94A3B8"));
        gap(col, 16);

        if (locked) {
            col.addView(label("Settings are locked while focus hours are active.", 16, true, "#F59E0B"));
            gap(col, 12);
        }

        final CheckBox enabledBox = new CheckBox(this);
        enabledBox.setText("Turn on focus hours");
        enabledBox.setTextColor(Color.WHITE);
        enabledBox.setChecked(prefs.enabled());
        col.addView(enabledBox);
        gap(col, 8);

        startBtn = new Button(this);
        endBtn = new Button(this);
        updateTimeButtons();
        startBtn.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            startMin = h * 60 + m;
            updateTimeButtons();
        }, startMin / 60, startMin % 60, true).show());
        endBtn.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> {
            endMin = h * 60 + m;
            updateTimeButtons();
        }, endMin / 60, endMin % 60, true).show());
        col.addView(startBtn);
        col.addView(endBtn);
        gap(col, 16);

        col.addView(label("Mode", 18, true, "#FFFFFF"));
        gap(col, 6);
        final RadioGroup group = new RadioGroup(this);
        String[] names = {
                "Easy: unlock with one tap",
                "Medium: confirm, then wait 1 minute (you can cancel)",
                "Impossible: locked until focus hours end"
        };
        for (int i = 0; i < 3; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(100 + i);
            rb.setText(names[i]);
            rb.setTextColor(Color.WHITE);
            group.addView(rb);
        }
        group.check(100 + prefs.mode());
        col.addView(group);
        gap(col, 20);

        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(v -> {
            final int mode = group.getCheckedRadioButtonId() - 100;
            final boolean on = enabledBox.isChecked();
            final Runnable doSave = () -> {
                prefs.save(on, startMin, endMin, mode);
                Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
                finish();
            };
            if (on && mode == FocusPrefs.HARD) {
                new AlertDialog.Builder(this)
                        .setTitle("Impossible mode")
                        .setMessage("During focus hours you will not be able to unlock Instagram or change these settings. Continue?")
                        .setPositiveButton("Save", (dlg, w) -> doSave.run())
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                doSave.run();
            }
        });

        Button close = new Button(this);
        close.setText("Close");
        close.setOnClickListener(v -> finish());

        if (locked) {
            enabledBox.setEnabled(false);
            startBtn.setEnabled(false);
            endBtn.setEnabled(false);
            for (int i = 0; i < group.getChildCount(); i++) group.getChildAt(i).setEnabled(false);
        } else {
            col.addView(save);
        }
        col.addView(close);

        setContentView(scroll);
    }
}
