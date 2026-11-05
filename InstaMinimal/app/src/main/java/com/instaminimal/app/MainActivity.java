package com.instaminimal.app;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;

public class MainActivity extends Activity {

    private static final String START_URL = "https://www.instagram.com/direct/inbox/";

    // Requests whose URL contains any of these are dropped, so the feed,
    // reels and explore are never downloaded. Edit this list to tune it.
    // (Do NOT add video file types: that would break reels sent in DMs.)
    private static final String[] BLOCKED_PARTS = {
            "feed/timeline",
            "/clips/",
            "/discover/"
    };

    private WebView web;
    private String blockerJs = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        blockerJs = readAsset("blocker.js");

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        WebView.setWebContentsDebuggingEnabled(true); // lets you debug via chrome://inspect

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                for (String part : BLOCKED_PARTS) {
                    if (url.contains(part)) {
                        return new WebResourceResponse(
                                "application/json", "UTF-8", 403, "Blocked",
                                new HashMap<String, String>(),
                                new ByteArrayInputStream(new byte[0]));
                    }
                }
                return super.shouldInterceptRequest(view, request);
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
