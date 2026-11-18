package com.instaminimal.app;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String START_URL = "https://www.instagram.com/direct/inbox/";

    // Requests containing any of these words are dropped.
    private static final String[] BLOCKED_PARTS = {
            "feed/timeline",
            "/clips/",
            "/discover/"
    };

    // Set by the script: true while a single Reel is open
    private static volatile boolean reelOpenFlag = false;
    private static volatile boolean onHomeFlag = false;
    private static final Pattern REEL_URL = Pattern.compile("^https?://[^/]+/reels?/[^/?#]+.*");

    private LockableWebView web;
    private String blockerJs = "";

    class Bridge {
        @JavascriptInterface
        public void setReelOpen(boolean open) {
            reelOpenFlag = open;
        }
        @JavascriptInterface
        public void setOnHome(boolean home) {
            onHomeFlag = home;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        blockerJs = readAsset("blocker.js");

        web = new LockableWebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        WebView.setWebContentsDebuggingEnabled(true);
        web.addJavascriptInterface(new Bridge(), "IFNative");

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
                if (onHomeFlag && url.contains(".mp4")) {
                    return new WebResourceResponse(
                            "video/mp4", "UTF-8", 200, "OK",
                            new HashMap<String, String>(),
                            new ByteArrayInputStream(new byte[0]));
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
        });

        web.loadUrl(START_URL);
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
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
