package com.instaminimal.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private static final String START_URL = "https://www.instagram.com/direct/inbox/";
    private static final String OFFLINE_URL = "file:///android_asset/offline.html";

    // Requests containing any of these words are dropped.
    private static final String[] BLOCKED_PARTS = {
            "feed/timeline",
            "/clips/",
            "/discover/"
    };

    // Set by the script: true while a single Reel is open
    private static volatile boolean reelOpenFlag = false;
    private static final Pattern REEL_URL = Pattern.compile("^https?://[^/]+/reels?/[^/?#]+.*");

    private static final int FILE_REQ = 1;
    private static final int MIC_REQ = 2;

    private LockableWebView web;
    private ProgressBar progress;
    private String blockerJs = "";
    private String failedUrl = null;

    // Photos, camera and microphone
    private ValueCallback<Uri[]> filePathCallback;
    private Uri cameraUri;
    private PermissionRequest pendingMicRequest;

    // Focus lock screen
    private FocusPrefs prefs;
    private LinearLayout lockView;
    private TextView lockTitle, lockTime, lockBack, lockNote;
    private Button lockBtn, lockCancelBtn;
    private CountDownTimer unlockTimer;
    private boolean webPaused = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshLock();
            handler.postDelayed(this, 1000);
        }
    };

    class Bridge {
        @JavascriptInterface
        public void setReelOpen(boolean open) {
            reelOpenFlag = open;
        }

        @JavascriptInterface
        public void retry() {
            runOnUiThread(() -> web.loadUrl(failedUrl != null ? failedUrl : START_URL));
        }
    }

    // A WebView that swallows vertical swipes while a Reel is open
    static class LockableWebView extends WebView {
        private float startX, startY;
        private boolean locking = false;
        private final int slop;

        LockableWebView(Context c) {
            super(c);
            slop = ViewConfiguration.get(c).getScaledTouchSlop();
        }

        private boolean reelActive() {
            String u = getUrl();
            return reelOpenFlag || (u != null && REEL_URL.matcher(u).matches());
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent e) {
            if (!reelActive()) {
                locking = false;
                return super.dispatchTouchEvent(e);
            }
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = e.getX();
                    startY = e.getY();
                    locking = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!locking) {
                        float dx = Math.abs(e.getX() - startX);
                        float dy = Math.abs(e.getY() - startY);
                        if (dy > slop && dy > dx) {
                            locking = true;
                            MotionEvent cancel = MotionEvent.obtain(e);
                            cancel.setAction(MotionEvent.ACTION_CANCEL);
                            super.dispatchTouchEvent(cancel);
                            cancel.recycle();
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (locking) {
                        locking = false;
                        return true;
                    }
                    break;
            }
            if (locking) return true;
            return super.dispatchTouchEvent(e);
        }
    }

    // ---------- small helpers ----------

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(String fill, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(fill));
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private TextView label(String s, int sp, boolean bold, String color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        return t;
    }

    private Button lockButton(String s, String color) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(16);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackground(rounded(color, 14));
        return b;
    }

    private void addTo(LinearLayout parent, View child, int topDp, int widthPx) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(widthPx, WRAP);
        p.topMargin = dp(topDp);
        parent.addView(child, p);
    }

    private static String hms(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }

    private static String clock(long ms) {
        return new SimpleDateFormat("HH:mm", Locale.US).format(new Date(ms));
    }

    // ---------- photos and camera ----------

    private boolean acceptsImages(WebChromeClient.FileChooserParams params) {
        String[] types = params.getAcceptTypes();
        if (types == null || types.length == 0) return true;
        for (String t : types) {
            if (t == null || t.trim().isEmpty() || t.contains("image") || t.contains("*/*")) return true;
        }
        return false;
    }

    // An intent for Android's own camera app that saves into Pictures/InstaFlow
    private Intent buildCameraIntent() {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.Images.Media.DISPLAY_NAME, "InstaFlow_" + System.currentTimeMillis() + ".jpg");
            cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= 29) {
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/InstaFlow");
            }
            cameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (cameraUri == null) return null;

            Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            cam.setClipData(ClipData.newRawUri("", cameraUri));
            cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return cam;
        } catch (Exception e) {
            cameraUri = null;   // for example on older Android versions: no camera option
            return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_REQ) {
            boolean hasPicked = data != null && (data.getData() != null || data.getClipData() != null);
            boolean cameraUsed = resultCode == RESULT_OK && !hasPicked && cameraUri != null;

            if (filePathCallback != null) {
                Uri[] result = null;
                if (resultCode == RESULT_OK) {
                    if (hasPicked) {
                        result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                    } else if (cameraUri != null) {
                        result = new Uri[]{cameraUri};
                    }
                }
                filePathCallback.onReceiveValue(result);
                filePathCallback = null;
            }

            // If the camera was not used, remove the empty picture entry we created for it
            if (cameraUri != null && !cameraUsed) {
                try {
                    getContentResolver().delete(cameraUri, null, null);
                } catch (Exception ignored) {
                }
            }
            cameraUri = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MIC_REQ && pendingMicRequest != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingMicRequest.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            } else {
                pendingMicRequest.deny();
            }
            pendingMicRequest = null;
        }
    }

    // ---------- focus lock screen ----------

    private LinearLayout buildLockView() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(Color.parseColor("#0F172A"));
        box.setPadding(dp(28), dp(28), dp(28), dp(28));
        box.setClickable(true);   // swallow touches so nothing underneath can be used

        lockTitle = label("Focus time", 28, true, "#FFFFFF");
        lockTime = label("00:00:00", 54, true, "#2DD4BF");
        lockBack = label("", 15, false, "#CBD5E1");
        lockNote = label("", 14, false, "#94A3B8");
        lockBtn = lockButton("Unlock", "#0F766E");
        lockCancelBtn = lockButton("Cancel", "#334155");

        addTo(box, lockTitle, 0, WRAP);
        addTo(box, lockTime, 18, WRAP);
        addTo(box, lockBack, 6, WRAP);
        addTo(box, lockNote, 26, WRAP);
        addTo(box, lockBtn, 22, dp(240));
        addTo(box, lockCancelBtn, 12, dp(240));

        lockBtn.setOnClickListener(view -> {
            int mode = prefs.mode();
            if (mode == FocusPrefs.EASY) {
                doUnlock();
            } else if (mode == FocusPrefs.MEDIUM) {
                new AlertDialog.Builder(this)
                        .setTitle("Are you sure?")
                        .setMessage("You will have to wait 1 minute before Instagram unlocks.")
                        .setPositiveButton("Yes, unlock", (dlg, w) -> startUnlockCountdown())
                        .setNegativeButton("Keep focus", null)
                        .show();
            }
        });

        lockCancelBtn.setOnClickListener(view -> {
            cancelUnlockTimer();
            applyLockMode();
        });

        return box;
    }

    private void applyLockMode() {
        int mode = prefs.mode();
        lockCancelBtn.setVisibility(View.GONE);
        if (mode == FocusPrefs.EASY) {
            lockBtn.setText("Unlock");
            lockBtn.setVisibility(View.VISIBLE);
            lockNote.setText("Easy mode: you can unlock any time.");
        } else if (mode == FocusPrefs.MEDIUM) {
            lockBtn.setText("Unlock...");
            lockBtn.setVisibility(View.VISIBLE);
            lockNote.setText("Medium mode: confirm, then wait 1 minute.");
        } else {
            lockBtn.setVisibility(View.GONE);
            lockNote.setText("Impossible mode: no unlocking until the timer ends.");
        }
    }

    private void startUnlockCountdown() {
        cancelUnlockTimer();
        lockBtn.setVisibility(View.GONE);
        lockCancelBtn.setVisibility(View.VISIBLE);
        lockNote.setText("Unlocking in 01:00. Stay focused, or cancel.");
        unlockTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long msLeft) {
                lockNote.setText("Unlocking in " + hms(msLeft).substring(3) + ". Stay focused, or cancel.");
            }

            @Override
            public void onFinish() {
                unlockTimer = null;
                doUnlock();
            }
        }.start();
    }

    private void cancelUnlockTimer() {
        if (unlockTimer != null) {
            unlockTimer.cancel();
            unlockTimer = null;
        }
    }

    private void doUnlock() {
        cancelUnlockTimer();
        prefs.unlock();
        refreshLock();
    }

    private void refreshLock() {
        long end = prefs.lockEndMs();
        if (end <= 0) {
            cancelUnlockTimer();
            if (lockView.getVisibility() == View.VISIBLE) lockView.setVisibility(View.GONE);
            if (webPaused) {
                web.onResume();
                webPaused = false;
            }
            return;
        }

        if (lockView.getVisibility() != View.VISIBLE) {
            lockView.setVisibility(View.VISIBLE);
        }
        if (!webPaused) {
            web.onPause();
            webPaused = true;
        }
        if (unlockTimer == null) applyLockMode();

        lockTitle.setText(prefs.sessionActive() ? "Study timer" : "Focus hours");
        lockTime.setText(hms(end - System.currentTimeMillis()));
        lockBack.setText("Instagram is back at " + clock(end));
    }

    // ---------- activity ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = new FocusPrefs(this);
        blockerJs = readAsset("blocker.js");

        // Layout: a slim header, then the web view with a loading bar and the lock screen over it
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(Color.parseColor("#0F172A"));
        header.setPadding(dp(14), 0, dp(6), 0);

        TextView title = new TextView(this);
        title.setText("InstaFlow");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        header.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));

        Button focusBtn = new Button(this);
        focusBtn.setText("Focus");
        focusBtn.setAllCaps(false);
        focusBtn.setTextColor(Color.WHITE);
        focusBtn.setBackgroundColor(Color.TRANSPARENT);
        focusBtn.setOnClickListener(view -> startActivity(new Intent(this, SettingsActivity.class)));
        header.addView(focusBtn, new LinearLayout.LayoutParams(WRAP, MATCH));

        column.addView(header, new LinearLayout.LayoutParams(MATCH, dp(40)));

        FrameLayout root = new FrameLayout(this);
        web = new LockableWebView(this);
        root.addView(web, new FrameLayout.LayoutParams(MATCH, MATCH));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, new FrameLayout.LayoutParams(MATCH, dp(3), Gravity.TOP));

        lockView = buildLockView();
        lockView.setVisibility(View.GONE);
        root.addView(lockView, new FrameLayout.LayoutParams(MATCH, MATCH));

        column.addView(root, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        setContentView(column);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        WebView.setWebContentsDebuggingEnabled(true);
        web.addJavascriptInterface(new Bridge(), "IFNative");

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            // Photo button: gallery or files, plus a Camera option
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = callback;
                cameraUri = null;

                Intent chooser = new Intent(Intent.ACTION_CHOOSER);
                chooser.putExtra(Intent.EXTRA_INTENT, params.createIntent());
                chooser.putExtra(Intent.EXTRA_TITLE, "Choose a photo or take one");

                if (acceptsImages(params)) {
                    Intent cam = buildCameraIntent();
                    if (cam != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cam});
                }

                try {
                    startActivityForResult(chooser, FILE_REQ);
                } catch (Exception e) {
                    filePathCallback = null;
                    cameraUri = null;
                    return false;
                }
                return true;
            }

            // Microphone for voice messages (only the microphone is ever allowed)
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                boolean wantsMic = false;
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsMic = true;
                }
                if (!wantsMic) {
                    request.deny();
                    return;
                }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    pendingMicRequest = request;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQ);
                }
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                for (String part : BLOCKED_PARTS) {
                    if (url.contains(part)) {
                        return new WebResourceResponse(
                                "application/json", "UTF-8", 200, "OK",
                                new HashMap<String, String>(),
                                new ByteArrayInputStream("{}".getBytes()));
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                reelOpenFlag = false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(blockerJs, null);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    failedUrl = request.getUrl().toString();
                    view.loadUrl(OFFLINE_URL);
                }
            }
        });

        web.loadUrl(START_URL);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLock();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public void onBackPressed() {
        if (lockView.getVisibility() == View.VISIBLE) {
            moveTaskToBack(true);
        } else if (web != null && web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
