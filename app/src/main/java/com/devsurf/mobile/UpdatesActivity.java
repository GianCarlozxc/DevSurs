package com.devsurf.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.format.Formatter;
import android.view.*;
import android.widget.*;

/** App-owned update screen; only Android's installation approval leaves this screen. */
public final class UpdatesActivity extends Activity {
    private static final int INSTALL_PERMISSION = 901, NOTIFICATIONS = 902;
    private static final int BG = 0xff080808, PANEL = 0xff111111, INK = 0xfff5f5f5, MUTED = 0xff8d8d8d;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AppUpdater updates;
    private TextView status, detail, primary, secondary, notes, notice;
    private ProgressBar progress;
    private boolean resumed, autoInstall, pendingInstall;
    private final Runnable ticker = new Runnable() { public void run() { refresh(); if (resumed) handler.postDelayed(this, 500); } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        updates = AppUpdater.get(this); autoInstall = state != null && state.getBoolean("autoInstall"); pendingInstall = state != null && state.getBoolean("pendingInstall");
        render();
        if (!updates.checked && !updates.checking && !updates.busy() && !updates.ready()) updates.check(); refresh();
    }
    @Override protected void onResume() { super.onResume(); resumed = true; handler.removeCallbacks(ticker); handler.post(ticker); }
    @Override protected void onPause() { resumed = false; handler.removeCallbacks(ticker); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putBoolean("autoInstall", autoInstall); state.putBoolean("pendingInstall", pendingInstall); }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    private GradientDrawable shape(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), 0xff292929); return d; }
    private TextView action(String value, boolean filled, Runnable click) {
        TextView t = text(value, 14, filled ? BG : INK); t.setGravity(Gravity.CENTER); t.setMinHeight(dp(48)); t.setPadding(dp(16), dp(14), dp(16), dp(14));
        t.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080), shape(filled ? INK : PANEL, 12), null)); t.setFocusable(true); t.setOnClickListener(v -> click.run()); return t;
    }
    private void space(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void render() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> { v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom()); return insets.consumeSystemWindowInsets(); });
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL); heading.setPadding(dp(8), dp(8), dp(20), dp(8));
        TextView back = text("‹", 28, INK); back.setGravity(Gravity.CENTER); back.setBackgroundResource(R.drawable.tap_feedback); back.setContentDescription("Back to Settings"); back.setOnClickListener(v -> finish()); heading.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("App updates", 24, INK); title.setTypeface(null, Typeface.BOLD); heading.addView(title); root.addView(heading);
        ScrollView scroll = new ScrollView(this); LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setPadding(dp(20), dp(24), dp(20), dp(32));
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.devsurf); logo.setScaleType(ImageView.ScaleType.FIT_CENTER); page.addView(logo, new LinearLayout.LayoutParams(dp(64), dp(64))); space(page, 18);
        TextView name = text("DevSurf", 28, INK); name.setTypeface(null, Typeface.BOLD); page.addView(name); space(page, 6); page.addView(text("Installed version " + BuildConfig.VERSION_NAME, 14, MUTED)); space(page, 28);
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(18), dp(20), dp(18), dp(20)); card.setBackground(shape(PANEL, 14));
        status = text("Checking for updates…", 19, INK); status.setTypeface(null, Typeface.BOLD); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); card.addView(status); space(card, 10);
        detail = text("", 14, MUTED); detail.setLineSpacing(dp(3), 1f); card.addView(detail);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100); progress.setProgressTintList(ColorStateList.valueOf(MUTED)); progress.setIndeterminateTintList(ColorStateList.valueOf(MUTED));
        LinearLayout.LayoutParams bar = new LinearLayout.LayoutParams(-1, dp(4)); bar.topMargin = dp(18); card.addView(progress, bar); page.addView(card); space(page, 20);
        primary = action("Check for updates", true, () -> {
            notice.setText("");
            if (updates.ready()) requestInstall();
            else if (updates.available()) { autoInstall = true; updates.download(); }
            else updates.check(); refresh();
        }); page.addView(primary, new LinearLayout.LayoutParams(-1, -2)); space(page, 10);
        secondary = action("Cancel download", false, () -> { autoInstall = false; pendingInstall = false; if (updates.busy() || updates.ready()) updates.cancel(); else updates.check(); refresh(); }); page.addView(secondary, new LinearLayout.LayoutParams(-1, -2)); space(page, 16);
        notice = text("", 13, MUTED); notice.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE); page.addView(notice);
        TextView hint = text("Updates download inside DevSurf. Android will ask you to approve installation. DevSurf will try to reopen afterward; if it stays closed, use the update notification or app icon.", 12, MUTED); hint.setLineSpacing(dp(3), 1f); page.addView(hint); space(page, 24);
        notes = text("", 14, MUTED); notes.setLineSpacing(dp(4), 1f); page.addView(notes);
        scroll.addView(page); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
    }
    private void refresh() {
        boolean ready = updates.ready(), busy = updates.busy(), available = updates.available();
        GitHubUpdates.Release release = updates.active != null && (busy || ready) ? updates.active : updates.latest;
        String title, caption;
        if (updates.installing) { title = "Installing update…"; caption = "Approve the update in Android's installation prompt."; }
        else if (updates.checking) { title = "Checking for updates…"; caption = "Looking for the latest DevSurf release."; }
        else if (updates.verifying) { title = "Verifying update…"; caption = "Checking the APK before installation."; }
        else if (busy) { title = "Downloading update" + (updates.percent >= 0 ? " · " + updates.percent + "%" : "…"); caption = "The download continues if you leave this page. Return here to finish installation."; }
        else if (!updates.error.isEmpty()) { title = "Update could not be completed"; caption = updates.error; }
        else if (ready) { title = "Ready to install"; caption = "DevSurf " + release.version + " is downloaded and verified."; }
        else if (available) { title = "DevSurf " + release.version + " is available"; caption = "Download size: " + Formatter.formatShortFileSize(this, release.size); }
        else if (updates.checked) { title = "You're up to date"; caption = "You have the latest published version of DevSurf."; }
        else { title = "Check for updates"; caption = "Get the latest version without opening a browser."; }
        set(status, title); set(detail, caption);
        progress.setVisibility(updates.checking || busy ? View.VISIBLE : View.GONE); progress.setIndeterminate(updates.checking || updates.verifying || updates.installing || updates.percent < 0); if (updates.percent >= 0) progress.setProgress(updates.percent);
        primary.setEnabled(!updates.checking && !busy); primary.setAlpha(primary.isEnabled() ? 1f : .5f);
        set(primary, updates.installing ? "Installing…" : updates.checking ? "Checking…" : busy ? "Downloading…" : ready ? "Install update" : available ? "Download & update" : "Check for updates");
        secondary.setVisibility(!updates.installing && (busy || ready || available) ? View.VISIBLE : View.GONE); set(secondary, busy ? "Cancel download" : ready ? "Remove download" : "Check again");
        String changes = release != null && available ? release.notes.replaceAll("(?m)^#{1,6}\\s*", "").replace("**", "").replace("`", "").trim() : "";
        if (changes.length() > 2200) changes = changes.substring(0, 2200) + "…";
        set(notes, changes.isEmpty() ? "" : "What's new\n\n" + changes);
        if (autoInstall && ready && !busy && updates.error.isEmpty() && resumed) { autoInstall = false; requestInstall(); }
        if (!updates.error.isEmpty()) autoInstall = false;
    }
    private static void set(TextView view, String value) { if (!value.contentEquals(view.getText())) view.setText(value); }
    private void requestInstall() {
        if (!updates.ready() || updates.busy()) return;
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !getPreferences(0).getBoolean("asked_notifications", false)) {
            getPreferences(0).edit().putBoolean("asked_notifications", true).apply(); pendingInstall = true;
            notice.setText("Notifications provide a way to reopen DevSurf after the update."); requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATIONS); return;
        }
        ensureInstallPermission();
    }
    private void ensureInstallPermission() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            Dialogs.builder(this, false).setTitle("Allow DevSurf updates").setMessage("Allow DevSurf to install app updates in Android Settings. Android will still ask you to approve installation.")
                .setNegativeButton("Not now", (d, w) -> pendingInstall = false).setPositiveButton("Open settings", (d, w) -> {
                    pendingInstall = true;
                    try { startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())), INSTALL_PERMISSION); }
                    catch (Exception e) { pendingInstall = false; notice.setText("Open Android Settings and allow DevSurf to install unknown apps, then try again."); }
                }).show(); return;
        }
        pendingInstall = false; updates.install(); refresh();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) { super.onRequestPermissionsResult(request, permissions, results); if (request == NOTIFICATIONS && pendingInstall) { pendingInstall = false; ensureInstallPermission(); } }
    @Override protected void onActivityResult(int request, int result, Intent data) { super.onActivityResult(request, result, data); if (request == INSTALL_PERMISSION && pendingInstall) { pendingInstall = false; if (getPackageManager().canRequestPackageInstalls()) { updates.install(); refresh(); } else notice.setText("Installation permission was not enabled. Tap Install update to try again."); } }
}
