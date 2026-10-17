package com.devsurf.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Local hostname blocking for WebView and service-worker requests. No browsing data is uploaded. */
final class AdBlocker {
    private final Set<String> hosts;
    private final SharedPreferences prefs;
    private volatile boolean enabled;
    private static final String REMOVE_STYLE = "document.getElementById('devsurf-ad-filter')?.remove();";
    private static final String HIDE_ADS = "(()=>{if(document.getElementById('devsurf-ad-filter'))return;"
        + "const s=document.createElement('style');s.id='devsurf-ad-filter';"
        + "s.textContent='.adsbygoogle,ins[data-ad-client],[data-ad-slot],iframe[src*=\"doubleclick.net\"],iframe[src*=\"googlesyndication.com\"]{display:none!important}';"
        + "(document.head||document.documentElement).appendChild(s);})()";

    AdBlocker(Context context, SharedPreferences prefs) {
        this.prefs = prefs; enabled = prefs.getBoolean("adblock", true);
        HashSet<String> loaded = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(context.getAssets().open("adblock/peter-lowe.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase(Locale.ROOT);
                // This bundled subscription contains unconditional domain rules only.
                // Reject other filter syntax rather than silently weakening its conditions.
                if (line.startsWith("||") && line.endsWith("^")) {
                    String host = line.substring(2, line.length() - 1);
                    if (host.matches("[a-z0-9.-]+") && host.contains(".")) loaded.add(host);
                }
            }
        } catch (java.io.IOException e) { loaded.clear(); }
        hosts = Collections.unmodifiableSet(loaded);
    }
    boolean isEnabled() { return enabled; }
    boolean isAvailable() { return !hosts.isEmpty(); }
    void setEnabled(boolean value) { enabled = value; prefs.edit().putBoolean("adblock", value).apply(); }

    void installServiceWorkerFilter() {
        ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) { return intercept(request); }
        });
    }
    WebResourceResponse intercept(WebResourceRequest request) {
        // Keep deliberate top-level visits working; only embedded website requests are filtered.
        if (!enabled || request.isForMainFrame()) return null;
        Uri uri = request.getUrl();
        if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))) return null;
        String host = uri.getHost(); if (host == null) return null;
        host = host.toLowerCase(Locale.ROOT);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        while (!host.isEmpty()) {
            if (hosts.contains(host)) return new WebResourceResponse("text/plain", "UTF-8", 204, "No Content",
                Collections.singletonMap("Cache-Control", "no-store"), new ByteArrayInputStream(new byte[0]));
            int dot = host.indexOf('.'); if (dot < 0) break; host = host.substring(dot + 1);
        }
        return null;
    }
    void applyCosmetics(WebView web) {
        String url = web.getUrl();
        if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) web.evaluateJavascript(enabled && isAvailable() ? HIDE_ADS : REMOVE_STYLE, null);
    }
}
