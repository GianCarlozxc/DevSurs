package com.devsurf.mobile;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.*;
import android.webkit.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Native mobile shell. Website WebViews have no JavaScript bridge into app storage. */
public class MainActivity extends Activity {
    private SharedPreferences prefs;
    private AdBlocker adBlocker;
    private LinearLayout root, toolbar, bottom;
    private FrameLayout content, fullScreen;
    private AutoCompleteTextView address;
    private TextView tabButton, reloadButton, backButton, forwardButton;
    private ProgressBar progress;
    private final ArrayList<Tab> tabs = new ArrayList<>();
    private int selected = -1;
    private int bg, panel, ink, muted, border;
    private final boolean light = false;
    private boolean settingsOpen;
    private ValueCallback<Uri[]> upload;
    private WebChromeClient.CustomViewCallback fullScreenCallback;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static class Tab {
        WebView web;
        String url = "devsurf://home", title = "New tab";
        boolean home = true, desktop;
        String mobileAgent;
        Bitmap preview;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        UpdateRestartReceiver.opened(this);
        prefs = getSharedPreferences("devsurf", MODE_PRIVATE);
        prefs.edit().remove("javascript").remove("light").apply();
        adBlocker = new AdBlocker(getApplicationContext(), prefs);
        adBlocker.installServiceWorkerFilter();
        buildChrome();
        JSONArray saved = readArray("tabs");
        for (int i = 0; i < saved.length() && i < 20; i++) newTab(saved.optString(i, "devsurf://home"), false);
        if (tabs.isEmpty()) newTab("devsurf://home", false);
        selectTab(Math.min(prefs.getInt("selected", 0), tabs.size() - 1));
        handleIntent(getIntent());
    }

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), border); return d;
    }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private TextView button(String label, String description, Runnable action) {
        TextView b = text(label, 21, ink); b.setGravity(Gravity.CENTER);
        b.setMinHeight(dp(48)); b.setMinWidth(dp(48)); b.setContentDescription(description);
        b.setBackgroundResource(R.drawable.tap_feedback);
        b.setOnClickListener(v -> action.run()); return b;
    }
    private void buildChrome() {
        bg = Color.parseColor("#080808");
        panel = Color.parseColor("#111111");
        ink = Color.parseColor("#f5f5f5");
        muted = Color.parseColor("#8d8d8d");
        border = Color.parseColor("#292929");
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(panel);
        getWindow().getDecorView().setSystemUiVisibility(light ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(bg);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        toolbar = new LinearLayout(this); toolbar.setId(View.generateViewId()); toolbar.setGravity(Gravity.CENTER_VERTICAL); toolbar.setPadding(dp(6), dp(5), dp(6), dp(5));
        toolbar.addView(button("⌂", "Home", this::goHome), new LinearLayout.LayoutParams(dp(44), dp(48)));
        address = new AutoCompleteTextView(this); address.setSingleLine(); address.setTextSize(14); address.setTextColor(ink); address.setHintTextColor(muted);
        address.setHint("Search or enter address"); address.setSelectAllOnFocus(true);
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO); address.setPadding(dp(12), 0, dp(12), 0); address.setBackground(shape(panel, 12));
        AddressSuggestions suggestions = new AddressSuggestions(this, prefs, ink, muted);
        address.setAdapter(suggestions); address.setThreshold(1); address.setDropDownBackgroundDrawable(shape(panel, 12));
        address.setDropDownAnchor(toolbar.getId()); address.setDropDownHorizontalOffset(dp(12)); address.setDropDownVerticalOffset(dp(4));
        toolbar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> address.setDropDownWidth(Math.max(dp(160), r - l - dp(24))));
        address.setOnFocusChangeListener((v, focused) -> {
            if (!focused) { address.dismissDropDown(); return; }
            String query = address.getText().toString();
            suggestions.getFilter().filter(query, count -> {
                if (address.hasFocus() && address.getText().toString().equals(query) && count > 0) address.showDropDown();
            });
        });
        address.setOnItemClickListener((parent, view, position, id) -> {
            AddressSuggestions.Suggestion choice = (AddressSuggestions.Suggestion)parent.getItemAtPosition(position);
            address.dismissDropDown(); address.clearFocus();
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0);
            navigate(choice.target);
        });
        address.setOnEditorActionListener((v, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                navigate(address.getText().toString()); address.clearFocus();
                ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0); return true;
            } return false;
        });
        toolbar.addView(address, new LinearLayout.LayoutParams(0, dp(46), 1));
        reloadButton = button("↻", "Reload or stop", () -> { Tab t = current(); if (!t.home) { if (t.web.getProgress() < 100) t.web.stopLoading(); else t.web.reload(); } });
        toolbar.addView(reloadButton, new LinearLayout.LayoutParams(dp(44), dp(48)));
        root.addView(toolbar);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(muted)); progress.setVisibility(View.INVISIBLE);
        root.addView(progress, new LinearLayout.LayoutParams(-1, dp(2)));
        content = new FrameLayout(this); root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        bottom = new LinearLayout(this); bottom.setBackgroundColor(panel); bottom.setGravity(Gravity.CENTER);
        backButton = button("‹", "Back", this::back);
        forwardButton = button("›", "Forward", () -> { if (current().web.canGoForward()) { current().home = false; current().web.goForward(); showWeb(current()); } });
        tabButton = button("1", "Tabs", this::showTabs); tabButton.setTextSize(17);
        TextView[] controls = { backButton, forwardButton, button("+", "New tab", () -> newTab("devsurf://home", true)), tabButton, button("⋮", "Menu", this::showMenu) };
        for (TextView b : controls) bottom.addView(b, new LinearLayout.LayoutParams(0, dp(54), 1));
        root.addView(bottom); setContentView(root);
    }

    private Tab current() { return tabs.get(selected); }
    @SuppressWarnings("SetJavaScriptEnabled")
    private void newTab(String url, boolean activate) {
        if (tabs.size() >= 20) { toast("Close a tab before opening another (20 tabs maximum)."); return; }
        Tab t = new Tab(); t.web = new WebView(this); t.web.setBackgroundColor(bg);
        WebSettings s = t.web.getSettings(); s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true); s.setBuiltInZoomControls(true); s.setDisplayZoomControls(false);
        s.setUseWideViewPort(true); s.setLoadWithOverviewMode(true); s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false); s.setAllowFileAccess(false); s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW); s.setSafeBrowsingEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        applyWebTheme(t.web);
        t.mobileAgent = s.getUserAgentString();
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(t.web, false);
        t.web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return adBlocker.intercept(request);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                String link = request.getUrl().toString();
                if (isWeb(link)) return false;
                if (link.equals("about:blank")) return false;
                if (link.equals("devsurf://home")) { if (t == current()) goHome(); return true; }
                // External applications require a deliberate user gesture and confirmation.
                if (request.hasGesture()) external(link);
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, Bitmap icon) {
                t.url = url; t.home = false; if (t == current()) updateChrome();
            }
            @Override public void onPageFinished(WebView view, String url) {
                adBlocker.applyCosmetics(view);
                t.url = url; t.title = view.getTitle() == null ? url : view.getTitle();
                if (isWeb(url)) addHistory(t.title, url);
                if (t == current()) updateChrome(); saveTabs();
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler h, SslError error) {
                h.cancel(); if (t == current()) toast("The website's secure connection could not be verified.");
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError error) {
                if (req.isForMainFrame() && t == current()) toast("Page could not load. Check the address or your connection.");
            }
        });
        t.web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) { if (!settingsOpen && selected >= 0 && t == current()) { progress.setProgress(value); progress.setVisibility(value < 100 ? View.VISIBLE : View.INVISIBLE); reloadButton.setText(value < 100 ? "×" : "↻"); } }
            @Override public void onReceivedTitle(WebView view, String title) { t.title = title; }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (upload != null) upload.onReceiveValue(null); upload = callback;
                try { startActivityForResult(params.createIntent(), 100); }
                catch (ActivityNotFoundException e) { upload.onReceiveValue(null); upload = null; toast("No file picker available."); }
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) { request.deny(); handler.post(() -> toast("Camera and microphone access are unavailable in this version.")); }
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean gesture, Message message) {
                if (!gesture || tabs.size() >= 20) return false;
                newTab("devsurf://home", true); Tab created = current(); created.home = false; showWeb(created);
                ((WebView.WebViewTransport)message.obj).setWebView(created.web); message.sendToTarget(); return true;
            }
            @Override public void onCloseWindow(WebView view) { closeTab(tabs.indexOf(t)); }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullScreen != null) { callback.onCustomViewHidden(); return; }
                fullScreenCallback = callback; fullScreen = new FrameLayout(MainActivity.this); fullScreen.setBackgroundColor(Color.BLACK);
                fullScreen.addView(view, new FrameLayout.LayoutParams(-1, -1));
                ((FrameLayout)getWindow().getDecorView()).addView(fullScreen, new FrameLayout.LayoutParams(-1, -1));
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
            @Override public void onHideCustomView() { exitFullscreen(); }
        });
        t.web.setDownloadListener((link, agent, disposition, mime, length) -> confirmDownload(link, agent, disposition, mime));
        t.web.setOnLongClickListener(v -> {
            WebView.HitTestResult hit = t.web.getHitTestResult(); String link = hit.getExtra();
            if (link == null || !isWeb(link)) return false;
            Dialogs.builder(this, light).setTitle(link).setItems(new String[]{"Open in new tab", "Copy link", "Share link", "Download"}, (d, which) -> {
                if (which == 0) newTab(link, true);
                else if (which == 1) copy(link);
                else if (which == 2) share(link);
                else confirmDownload(link, t.web.getSettings().getUserAgentString(), "", null);
            }).show(); return true;
        });
        tabs.add(t);
        if (isWeb(url)) { t.url = url; t.home = false; t.web.loadUrl(url); }
        if (activate) selectTab(tabs.size() - 1);
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        boolean switching = selected >= 0 && selected != index;
        int direction = index >= selected ? 1 : -1;
        if (switching) capturePreview();
        if (selected >= 0 && selected < tabs.size()) tabs.get(selected).web.onPause();
        // A new tab starts with a fresh address field, even if the previous one was being edited.
        address.clearFocus();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0);
        content.animate().cancel(); content.setAlpha(1f); content.setTranslationX(0f);
        selected = index; Tab t = current(); t.web.onResume();
        if (t.home) showHome(); else showWeb(t);
        updateChrome(); saveTabs();
        if (switching && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            content.setAlpha(0f); content.setTranslationX(dp(32) * direction);
            content.animate().alpha(1f).translationX(0f).setDuration(240)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            tabButton.animate().cancel(); tabButton.setScaleX(.85f); tabButton.setScaleY(.85f);
            tabButton.animate().scaleX(1f).scaleY(1f).setDuration(240)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
    }
    private void showWeb(Tab t) {
        restoreBrowserChrome();
        content.removeAllViews(); if (t.web.getParent() != null) ((android.view.ViewGroup)t.web.getParent()).removeView(t.web);
        content.addView(t.web, new FrameLayout.LayoutParams(-1, -1));
    }
    private void updateChrome() {
        if (settingsOpen) return;
        if (selected < 0) return;
        Tab t = current(); if (!address.hasFocus()) address.setText(t.home ? "" : t.url);
        tabButton.setText(String.valueOf(tabs.size()));
        backButton.setAlpha((!t.home || t.web.canGoBack()) ? 1f : .35f);
        forwardButton.setAlpha(t.web.canGoForward() ? 1f : .35f);
        progress.setVisibility(!t.home && t.web.getProgress() < 100 ? View.VISIBLE : View.INVISIBLE);
        reloadButton.setText(!t.home && t.web.getProgress() < 100 ? "×" : "↻");
    }
    private boolean isWeb(String url) { return url != null && (url.startsWith("https://") || url.startsWith("http://")); }
    private void navigate(String input) {
        String value = input.trim(); if (value.isEmpty()) return;
        if (value.equals("devsurf://home")) { goHome(); return; }
        if (!isWeb(value)) {
            if (!value.contains(" ") && (value.contains(".") || value.startsWith("localhost")) && !value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*")) value = "https://" + value;
            else value = searchUrl() + Uri.encode(value);
        }
        Tab t = current(); t.home = false; t.url = value; showWeb(t); t.web.loadUrl(value); updateChrome(); saveTabs();
    }
    private String searchUrl() {
        String engine = prefs.getString("engine", "Google");
        return engine.equals("DuckDuckGo") ? "https://duckduckgo.com/?q=" : engine.equals("Bing") ? "https://www.bing.com/search?q=" : "https://www.google.com/search?q=";
    }
    private void goHome() { current().home = true; showHome(); updateChrome(); saveTabs(); }
    private void showHome() {
        restoreBrowserChrome();
        content.removeAllViews(); ScrollView scroller = new ScrollView(this); scroller.setFillViewport(true);
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setGravity(Gravity.CENTER_HORIZONTAL); page.setPadding(dp(24), dp(40), dp(24), dp(28));
        ImageView logo = new ImageView(this); logo.setImageResource(com.devsurf.mobile.R.drawable.devsurf); logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        page.addView(logo, new LinearLayout.LayoutParams(dp(76), dp(76)));
        TextView title = text("DevSurf", 34, ink); title.setTypeface(null, Typeface.BOLD); title.setGravity(Gravity.CENTER); title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleSize = new LinearLayout.LayoutParams(-1, -2); titleSize.topMargin = dp(16); page.addView(title, titleSize);
        RotatingQuoteView subtitle = new RotatingQuoteView(this, muted);
        LinearLayout.LayoutParams subtitleSize = new LinearLayout.LayoutParams(-1, -2); subtitleSize.topMargin = dp(8); page.addView(subtitle, subtitleSize);
        TextView search = text("⌕    Search or enter address", 15, muted); search.setGravity(Gravity.CENTER_VERTICAL); search.setPadding(dp(18), 0, dp(18), 0); search.setBackground(shape(panel, 14));
        LinearLayout.LayoutParams searchSize = new LinearLayout.LayoutParams(-1, dp(58)); searchSize.topMargin = dp(32); searchSize.bottomMargin = dp(24); page.addView(search, searchSize);
        search.setOnClickListener(v -> { address.requestFocus(); ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(address, InputMethodManager.SHOW_IMPLICIT); });
        LinearLayout links = new LinearLayout(this); links.setGravity(Gravity.CENTER_VERTICAL); links.setBaselineAligned(false);
        String[] names = {"Google", "YouTube", "Wikipedia"}; String[] urls = {"https://www.google.com", "https://m.youtube.com", "https://www.wikipedia.org"};
        int[] icons = {R.drawable.shortcut_google, R.drawable.shortcut_youtube, R.drawable.shortcut_wikipedia};
        for (int i = 0; i < names.length; i++) {
            final String url = urls[i];
            LinearLayout shortcut = new LinearLayout(this); shortcut.setOrientation(LinearLayout.VERTICAL); shortcut.setGravity(Gravity.CENTER);
            shortcut.setPadding(dp(4), dp(12), dp(4), dp(12)); shortcut.setBackgroundResource(R.drawable.tap_feedback);
            shortcut.setContentDescription(names[i]); shortcut.setFocusable(true); shortcut.setOnClickListener(v -> navigate(url));
            ImageView icon = new ImageView(this); icon.setImageResource(icons[i]); icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            shortcut.addView(icon, new LinearLayout.LayoutParams(dp(32), dp(32)));
            TextView label = text(names[i], 13, ink); label.setGravity(Gravity.CENTER); label.setIncludeFontPadding(false);
            LinearLayout.LayoutParams labelSize = new LinearLayout.LayoutParams(-1, -2); labelSize.topMargin = dp(10); shortcut.addView(label, labelSize);
            links.addView(shortcut, new LinearLayout.LayoutParams(0, dp(90), 1));
        }
        page.addView(links, new LinearLayout.LayoutParams(-1, -2));
        TextView saved = text("YOUR WORKSPACE", 11, muted); saved.setGravity(Gravity.CENTER); saved.setLetterSpacing(.16f); saved.setPadding(0, dp(30), 0, dp(12)); page.addView(saved);
        addCard(page, "☆   Bookmarks", "Keep your favorite places close", this::showBookmarks);
        addCard(page, "◷   History", "Pick up where you left off", this::showHistory);
        addCard(page, "↓   Downloads", "Files saved to your phone", this::openDownloads);
        scroller.addView(page); content.addView(scroller);
    }
    private void addCard(LinearLayout parent, String title, String caption, Runnable action) {
        LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(16), dp(16), dp(16), dp(16)); card.setBackground(shape(panel, 12));
        card.addView(text(title, 16, ink)); TextView sub = text(caption, 12, muted); sub.setPadding(0, dp(6), 0, 0); card.addView(sub);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.bottomMargin = dp(10); parent.addView(card, p); card.setOnClickListener(v -> action.run());
    }
    private void showMenu() {
        Dialog dialog = new Dialog(this, light ? R.style.DevSurfDialogLight : R.style.DevSurfDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout sheet = new LinearLayout(this); sheet.setOrientation(LinearLayout.VERTICAL); sheet.setPadding(dp(16), dp(12), dp(16), dp(16)); sheet.setBackground(shape(panel, 22));
        View handle = new View(this); handle.setBackground(shape(border, 3));
        LinearLayout.LayoutParams handleSize = new LinearLayout.LayoutParams(dp(32), dp(4)); handleSize.gravity = Gravity.CENTER_HORIZONTAL; handleSize.bottomMargin = dp(10); sheet.addView(handle, handleSize);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this); logo.setImageResource(R.drawable.devsurf); logo.setScaleType(ImageView.ScaleType.FIT_CENTER); if (light) logo.setColorFilter(ink);
        LinearLayout.LayoutParams logoSize = new LinearLayout.LayoutParams(dp(28), dp(28)); logoSize.setMarginEnd(dp(10)); header.addView(logo, logoSize);
        TextView title = text("DevSurf", 20, ink); title.setTypeface(null, Typeface.BOLD); header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(button("×", "Close menu", dialog::dismiss), new LinearLayout.LayoutParams(dp(48), dp(48))); sheet.addView(header);
        LinearLayout actions = new LinearLayout(this); actions.setBaselineAligned(false);
        String[] labels = {"Bookmark", "Share", "Find", current().desktop ? "Mobile site" : "Desktop site"};
        String[] icons = {"star", "share", "find", "desktop"};
        Runnable[] callbacks = {this::bookmark, () -> { if (!current().home) share(current().url); }, this::find, this::desktop};
        for (int i = 0; i < labels.length; i++) {
            final Runnable action = callbacks[i];
            LinearLayout tile = new LinearLayout(this); tile.setOrientation(LinearLayout.VERTICAL); tile.setGravity(Gravity.CENTER); tile.setBackgroundResource(R.drawable.tap_feedback);
            tile.setContentDescription(labels[i]); tile.setFocusable(true); tile.setOnClickListener(v -> { dialog.dismiss(); action.run(); });
            tile.addView(new MenuIcon(this, icons[i], ink), new LinearLayout.LayoutParams(dp(23), dp(23)));
            TextView label = text(labels[i], 11, ink); label.setGravity(Gravity.CENTER); label.setMaxLines(2);
            LinearLayout.LayoutParams labelSize = new LinearLayout.LayoutParams(-1, -2); labelSize.topMargin = dp(8); tile.addView(label, labelSize);
            if (i < 3 && current().home) { tile.setAlpha(.35f); tile.setEnabled(false); }
            actions.addView(tile, new LinearLayout.LayoutParams(0, dp(84), 1));
        }
        LinearLayout.LayoutParams actionSize = new LinearLayout.LayoutParams(-1, -2); actionSize.topMargin = dp(8); actionSize.bottomMargin = dp(8); sheet.addView(actions, actionSize);
        menuDivider(sheet);
        menuRow(sheet, dialog, "star", "Bookmarks", this::showBookmarks);
        menuRow(sheet, dialog, "history", "History", this::showHistory);
        menuRow(sheet, dialog, "download", "Downloads", this::openDownloads);
        menuDivider(sheet);
        menuRow(sheet, dialog, "chat", "AI Chat", this::showAiChat);
        menuRow(sheet, dialog, "settings", "Settings", this::settings);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.addView(sheet); scroll.setVerticalScrollBarEnabled(false);
        dialog.setContentView(scroll); dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(getResources().getDisplayMetrics().widthPixels - dp(24), dp(440));
            window.setBackgroundDrawableResource(android.R.color.transparent); window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            window.setLayout(width, -2);
            scroll.post(() -> { if (dialog.isShowing()) { int maxHeight = (int)(getResources().getDisplayMetrics().heightPixels * .85f); window.setLayout(width, Math.min(sheet.getMeasuredHeight(), maxHeight)); } });
            WindowManager.LayoutParams params = window.getAttributes(); params.y = dp(12); window.setAttributes(params);
        }
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) { sheet.setTranslationY(dp(32)); sheet.setAlpha(0f); sheet.animate().translationY(0f).alpha(1f).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start(); }
    }
    private void showAiChat() {
        if (!settingsOpen) capturePreview();
        settingsOpen = true; address.clearFocus();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0);
        toolbar.setVisibility(View.GONE); bottom.setVisibility(View.GONE); progress.setVisibility(View.GONE);
        content.animate().cancel(); content.setAlpha(1f); content.setTranslationX(0f); content.removeAllViews();
        AiChatPage page = new AiChatPage(this, bg, panel, ink, muted, border, this::closeSettings, url -> newTab(url, true));
        content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) { page.setAlpha(0f); page.setTranslationX(dp(24)); page.animate().alpha(1f).translationX(0f).setDuration(220).start(); }
    }
    private void menuDivider(LinearLayout parent) {
        View divider = new View(this); divider.setBackgroundColor(border);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, dp(1)); size.topMargin = dp(4); size.bottomMargin = dp(4); parent.addView(divider, size);
    }
    private void menuRow(LinearLayout parent, Dialog dialog, String icon, String label, Runnable action) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12), 0, dp(12), 0); row.setBackgroundResource(R.drawable.tap_feedback);
        row.addView(new MenuIcon(this, icon, muted), new LinearLayout.LayoutParams(dp(21), dp(21)));
        TextView caption = text(label, 15, ink); caption.setPadding(dp(16), 0, 0, 0); row.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
        TextView arrow = text("›", 22, muted); row.addView(arrow);
        row.setFocusable(true); row.setContentDescription(label); row.setOnClickListener(v -> { dialog.dismiss(); action.run(); }); parent.addView(row, new LinearLayout.LayoutParams(-1, dp(52)));
    }
    private void showTabs() {
        capturePreview();
        Dialog dialog = new Dialog(this, light ? R.style.DevSurfDialogLight : R.style.DevSurfDialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout overview = new LinearLayout(this); overview.setOrientation(LinearLayout.VERTICAL); overview.setBackgroundColor(bg);
        overview.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        LinearLayout heading = new LinearLayout(this); heading.setGravity(Gravity.CENTER_VERTICAL); heading.setPadding(dp(12), dp(8), dp(12), dp(8));
        heading.addView(button("‹", "Return to browser", dialog::dismiss), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("Tabs · " + tabs.size(), 20, ink); title.setTypeface(null, Typeface.BOLD);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView add = button("+ New tab", "New tab", () -> { dialog.dismiss(); newTab("devsurf://home", true); }); add.setTextSize(14); add.setPadding(dp(12), 0, dp(12), 0);
        heading.addView(add); overview.addView(heading);
        ScrollView scroll = new ScrollView(this); scroll.setClipToPadding(false); scroll.setPadding(dp(10), dp(4), dp(10), dp(16));
        GridLayout grid = new GridLayout(this); grid.setColumnCount(2); grid.setUseDefaultMargins(false); scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));
        int cardHeight = Math.max(dp(210), Math.min(dp(340), (int)((getResources().getDisplayMetrics().widthPixels - dp(48)) / 2f * 1.5f)));
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i; Tab t = tabs.get(i);
            LinearLayout card = new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(3), dp(3), dp(3), dp(3));
            GradientDrawable surface = shape(panel, 18); surface.setStroke(dp(i == selected ? 2 : 1), i == selected ? muted : border);
            card.setBackground(surface); card.setClipToOutline(true); card.setContentDescription((t.home ? "New tab" : t.title) + (i == selected ? ", selected" : ""));
            card.setFocusable(true); card.setOnClickListener(v -> { dialog.dismiss(); selectTab(index); });
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(10), 0, 0, 0);
            ImageView favicon = new ImageView(this); favicon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            Bitmap siteIcon = t.home ? null : t.web.getFavicon();
            if (siteIcon != null) favicon.setImageBitmap(siteIcon); else favicon.setImageResource(R.drawable.devsurf);
            row.addView(favicon, new LinearLayout.LayoutParams(dp(20), dp(20)));
            TextView label = text(t.home ? "New tab" : t.title, 13, ink); label.setSingleLine(); label.setEllipsize(android.text.TextUtils.TruncateAt.END); label.setPadding(dp(8), 0, 0, 0);
            row.addView(label, new LinearLayout.LayoutParams(0, dp(44), 1)); label.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(button("×", "Close " + (t.home ? "new tab" : t.title), () -> { dialog.dismiss(); closeTab(index); showTabs(); }), new LinearLayout.LayoutParams(dp(40), dp(44)));
            card.addView(row, new LinearLayout.LayoutParams(-1, dp(44)));
            FrameLayout thumbnail = new FrameLayout(this); thumbnail.setBackground(shape(bg, 12)); thumbnail.setClipToOutline(true);
            if (t.preview != null) {
                ImageView image = new ImageView(this); image.setImageBitmap(t.preview); image.setScaleType(ImageView.ScaleType.MATRIX);
                image.addOnLayoutChangeListener((v, left, top, right, bottom, ol, ot, or, ob) -> {
                    android.graphics.Matrix matrix = new android.graphics.Matrix(); float scale = (right - left) / (float)t.preview.getWidth(); matrix.setScale(scale, scale); image.setImageMatrix(matrix);
                });
                thumbnail.addView(image, new FrameLayout.LayoutParams(-1, -1));
            } else {
                LinearLayout placeholder = new LinearLayout(this); placeholder.setOrientation(LinearLayout.VERTICAL); placeholder.setGravity(Gravity.CENTER); placeholder.setPadding(dp(12), dp(12), dp(12), dp(12));
                ImageView icon = new ImageView(this); icon.setImageResource(R.drawable.devsurf); placeholder.addView(icon, new LinearLayout.LayoutParams(dp(40), dp(40)));
                TextView caption = text(t.home ? "DevSurf" : Uri.parse(t.url).getHost(), 15, ink); caption.setGravity(Gravity.CENTER); caption.setPadding(0, dp(12), 0, 0); placeholder.addView(caption);
                TextView hint = text(t.home ? "Your space to explore." : "Open tab to preview", 11, muted); hint.setGravity(Gravity.CENTER); hint.setPadding(0, dp(6), 0, 0); placeholder.addView(hint);
                thumbnail.addView(placeholder, new FrameLayout.LayoutParams(-1, -1));
            }
            card.addView(thumbnail, new LinearLayout.LayoutParams(-1, 0, 1));
            GridLayout.LayoutParams size = new GridLayout.LayoutParams(GridLayout.spec(i / 2), GridLayout.spec(i % 2, 1f));
            size.width = 0; size.height = cardHeight; size.setMargins(dp(6), dp(6), dp(6), dp(6)); grid.addView(card, size);
        }
        if (tabs.size() % 2 != 0) {
            View spacer = new View(this);
            GridLayout.LayoutParams space = new GridLayout.LayoutParams(GridLayout.spec(tabs.size() / 2), GridLayout.spec(1, 1f));
            space.width = 0; space.height = cardHeight; space.setMargins(dp(6), dp(6), dp(6), dp(6)); grid.addView(spacer, space);
        }
        overview.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); dialog.setContentView(overview);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) { window.setLayout(-1, -1); window.setBackgroundDrawableResource(android.R.color.transparent); window.setStatusBarColor(bg); window.setNavigationBarColor(bg); window.getDecorView().setSystemUiVisibility(light ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0); }
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) { overview.setAlpha(0f); overview.setTranslationY(dp(20)); overview.animate().alpha(1f).translationY(0f).setDuration(220).start(); }
    }
    private void capturePreview() {
        if (settingsOpen) return;
        if (selected < 0 || selected >= tabs.size() || content.getChildCount() == 0) return;
        View page = content.getChildAt(0); int width = page.getWidth(), height = page.getHeight();
        if (width <= 0 || height <= 0) return;
        int previewWidth = Math.min(300, width); float scale = previewWidth / (float)width;
        Bitmap bitmap = Bitmap.createBitmap(previewWidth, Math.max(1, Math.min(600, Math.round(height * scale))), Bitmap.Config.RGB_565);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bitmap); canvas.drawColor(bg); canvas.scale(scale, scale); page.draw(canvas);
        current().preview = bitmap;
    }
    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        Tab removed = tabs.remove(index); if (removed.web.getParent() != null) ((ViewGroup)removed.web.getParent()).removeView(removed.web); removed.web.destroy();
        int next = selected > index ? selected - 1 : Math.min(selected, tabs.size() - 1); selected = -1;
        if (tabs.isEmpty()) { newTab("devsurf://home", true); } else selectTab(next);
    }
    private JSONArray readArray(String key) { try { return new JSONArray(prefs.getString(key, "[]")); } catch (JSONException e) { return new JSONArray(); } }
    private void saveTabs() {
        JSONArray arr = new JSONArray(); for (Tab t : tabs) arr.put(t.home ? "devsurf://home" : t.url);
        prefs.edit().putString("tabs", arr.toString()).putInt("selected", Math.max(0, selected)).apply();
    }
    private JSONObject entry(String title, String url) { JSONObject o = new JSONObject(); try { o.put("title", title); o.put("url", url); o.put("time", System.currentTimeMillis()); } catch (JSONException ignored) {} return o; }
    private void addHistory(String title, String url) {
        JSONArray old = readArray("history");
        if (old.length() > 0 && old.optJSONObject(0) != null && old.optJSONObject(0).optString("url").equals(url)) return;
        JSONArray arr = new JSONArray(); arr.put(entry(title, url)); for (int i = 0; i < old.length() && i < 499; i++) arr.put(old.opt(i)); prefs.edit().putString("history", arr.toString()).apply();
    }
    private void bookmark() {
        if (current().home) { toast("Open a website to bookmark it."); return; }
        JSONArray arr = readArray("bookmarks"); String url = current().url;
        for (int i = 0; i < arr.length(); i++) if (arr.optJSONObject(i) != null && arr.optJSONObject(i).optString("url").equals(url)) { toast("Already bookmarked."); return; }
        arr.put(entry(current().title, url)); prefs.edit().putString("bookmarks", arr.toString()).apply(); toast("Bookmark saved.");
    }
    private void showBookmarks() { showSavedPages("bookmarks"); }
    private void showHistory() { showSavedPages("history"); }
    private void showSavedPages(String key) {
        if (!settingsOpen) capturePreview();
        settingsOpen = true; address.clearFocus();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0);
        toolbar.setVisibility(View.GONE); bottom.setVisibility(View.GONE); progress.setVisibility(View.GONE);
        content.animate().cancel(); content.setAlpha(1f); content.setTranslationX(0f); content.removeAllViews();
        SavedPagesPage page = new SavedPagesPage(this, prefs, key, light, bg, panel, ink, muted, this::closeSettings, this::navigate);
        content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) { page.setAlpha(0f); page.setTranslationX(dp(24)); page.animate().alpha(1f).translationX(0f).setDuration(220).start(); }
    }
    private void settings() {
        boolean entering = !settingsOpen;
        if (entering) capturePreview();
        settingsOpen = true; address.clearFocus();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0);
        toolbar.setVisibility(View.GONE); bottom.setVisibility(View.GONE); progress.setVisibility(View.GONE);
        content.animate().cancel(); content.setAlpha(1f); content.setTranslationX(0f); content.removeAllViews();
        LinearLayout page = new LinearLayout(this); page.setOrientation(LinearLayout.VERTICAL); page.setBackgroundColor(bg);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(8), dp(8), dp(20), dp(8));
        header.addView(button("‹", "Back to browser", this::closeSettings), new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("Settings", 24, ink); title.setTypeface(null, Typeface.BOLD); header.addView(title); page.addView(header);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(20), dp(8), dp(20), dp(32));
        TextView intro = text("Make DevSurf yours.", 14, muted); intro.setPadding(dp(4), 0, 0, dp(8)); body.addView(intro);
        settingsSection(body, "BROWSING");
        settingCard(body, "find", "Search engine", "Used when you search the address bar", prefs.getString("engine", "Google"), () -> {
            String[] engines = {"Google", "DuckDuckGo", "Bing"};
            Dialogs.builder(this, light).setTitle("Search engine").setItems(engines, (d, which) -> { prefs.edit().putString("engine", engines[which]).apply(); settings(); }).show();
        });
        settingsSection(body, "PRIVACY");
        settingCard(body, "shield", "Ad blocker", "Blocks known ad and tracking servers", adBlocker.isAvailable() ? (adBlocker.isEnabled() ? "On" : "Off") : "Unavailable", () -> {
            if (!adBlocker.isAvailable()) { toast("The bundled ad filter could not be loaded."); return; }
            adBlocker.setEnabled(!adBlocker.isEnabled());
            for (Tab tab : tabs) adBlocker.applyCosmetics(tab.web);
            settings(); toast("Ad blocker " + (adBlocker.isEnabled() ? "enabled" : "disabled") + ". Reload open pages to apply.");
        });
        settingCard(body, "delete", "Clear browsing data", "Manage history, cookies and site storage", "", this::clearData);
        settingsSection(body, "GENERAL");
        settingCard(body, "download", "Check for updates", "Installed version " + BuildConfig.VERSION_NAME, "", () -> startActivity(new Intent(this, UpdatesActivity.class)));
        settingCard(body, "sound", "Download sounds", "Play sounds when downloads start and finish", DownloadFeedback.enabled(this) ? "On" : "Off", () -> {
            DownloadFeedback.setEnabled(this, !DownloadFeedback.enabled(this)); settings();
        });
        settingCard(body, "desktop", "Default browser", "Open links from other apps in DevSurf", "", this::chooseDefaultBrowser);
        settingCard(body, "info", "About DevSurf", "Version " + BuildConfig.VERSION_NAME, "", () -> Dialogs.builder(this, light).setTitle("DevSurf Mobile · " + BuildConfig.VERSION_NAME).setMessage("DevSurf's dark workspace, adapted for Android.\n\nBrowsing uses Android System WebView. Tabs, bookmarks, history and preferences stay on this phone. Downloads use Android Download Manager.\n\nAd blocking uses Peter Lowe's bundled ad and tracking server list (pgl.yoyo.org/adservers/), with limited ad-slot hiding. It cannot remove every ad or guarantee video-ad blocking.\n\nAI Chat opens ChatGPT or Claude in a browser tab. Desktop accounts, built-in AI tools and VPN are not included in this version.").setPositiveButton("Done", null).show());
        scroll.addView(body); page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1)); content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        if (entering && android.animation.ValueAnimator.areAnimatorsEnabled()) { page.setTranslationX(dp(24)); page.setAlpha(0f); page.animate().translationX(0f).alpha(1f).setDuration(220).start(); }
    }
    private void settingsSection(LinearLayout parent, String label) {
        TextView heading = text(label, 11, muted); heading.setLetterSpacing(.14f); heading.setPadding(dp(4), dp(24), 0, dp(12)); parent.addView(heading);
    }
    private void settingCard(LinearLayout parent, String icon, String title, String summary, String value, Runnable action) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16), dp(18), dp(14), dp(18));
        row.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x33808080), shape(panel, 12), null));
        row.addView(new MenuIcon(this, icon, muted), new LinearLayout.LayoutParams(dp(22), dp(22)));
        LinearLayout labels = new LinearLayout(this); labels.setOrientation(LinearLayout.VERTICAL); labels.setPadding(dp(14), 0, dp(8), 0);
        labels.addView(text(title, 15, ink)); TextView sub = text(summary, 12, muted); sub.setPadding(0, dp(5), 0, 0); labels.addView(sub); row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        if (!value.isEmpty()) { TextView currentValue = text(value, 12, muted); currentValue.setMaxWidth(dp(88)); currentValue.setGravity(Gravity.END); row.addView(currentValue); }
        TextView arrow = text("›", 22, muted); arrow.setPadding(dp(10), 0, 0, 0); row.addView(arrow);
        row.setFocusable(true); row.setContentDescription(title + (value.isEmpty() ? "" : ", " + value)); row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(-1, -2); size.bottomMargin = dp(10); parent.addView(row, size);
    }
    private void chooseDefaultBrowser() {
        try {
            android.app.role.RoleManager roles = getSystemService(android.app.role.RoleManager.class);
            if (roles.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) { startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER), 200); return; }
            startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
        } catch (Exception e) { toast("Open Android Settings to change your default browser."); }
    }
    private void restoreBrowserChrome() {
        settingsOpen = false; toolbar.setVisibility(View.VISIBLE); bottom.setVisibility(View.VISIBLE); progress.setVisibility(View.INVISIBLE);
    }
    private void closeSettings() {
        if (current().home) showHome(); else showWeb(current()); updateChrome();
    }
    private void clearData() {
        Dialogs.builder(this, light).setTitle("Clear browsing data?").setMessage("This closes all tabs and clears history, cookies, website storage and cache. You will be signed out of websites. Bookmarks stay saved.").setNegativeButton("Cancel", null).setPositiveButton("Clear", (d, w) -> {
            content.removeAllViews(); for (Tab t : tabs) { t.web.stopLoading(); t.web.clearHistory(); t.web.clearCache(true); t.web.destroy(); } tabs.clear(); selected = -1;
            prefs.edit().remove("history").remove("tabs").apply(); WebStorage.getInstance().deleteAllData();
            CookieManager.getInstance().removeAllCookies(success -> { CookieManager.getInstance().flush(); newTab("devsurf://home", true); toast("Browsing data cleared."); });
        }).show();
    }
    private void desktop() {
        Tab t = current(); t.desktop = !t.desktop;
        t.web.getSettings().setUserAgentString(t.desktop ? t.mobileAgent.replace("; wv", "").replace("Android", "X11; Linux x86_64").replace("Mobile", "") : t.mobileAgent);
        t.web.getSettings().setUseWideViewPort(true); if (!t.home) t.web.reload();
    }
    @SuppressWarnings("deprecation")
    private void applyWebTheme(WebView web) {
        if (Build.VERSION.SDK_INT >= 33) web.getSettings().setAlgorithmicDarkeningAllowed(!light);
        else web.getSettings().setForceDark(light ? WebSettings.FORCE_DARK_OFF : WebSettings.FORCE_DARK_ON);
    }
    private void find() {
        if (current().home) return;
        EditText query = new EditText(this); query.setSingleLine(); query.setHint("Find on this page");
        AlertDialog dialog = Dialogs.builder(this, light).setTitle("Find in page").setView(query).setPositiveButton("Next", null).setNeutralButton("Previous", null).setNegativeButton("Done", (d, w) -> current().web.clearMatches()).create();
        query.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int c, int f) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) { current().web.findAllAsync(s.toString()); }
            public void afterTextChanged(android.text.Editable e) {}
        });
        dialog.setOnShowListener(d -> { dialog.getButton(-1).setOnClickListener(v -> current().web.findNext(true)); dialog.getButton(-3).setOnClickListener(v -> current().web.findNext(false)); });
        dialog.setOnDismissListener(d -> current().web.clearMatches()); dialog.show();
    }
    private void confirmDownload(String url, String agent, String disposition, String mime) {
        if (!isWeb(url)) { toast("This download type is unavailable."); return; }
        String guessed = URLUtil.guessFileName(url, disposition, mime).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        String file = guessed.length() > 180 ? guessed.substring(0, 180) : guessed;
        DownloadPrompt.show(this, file, url, mime, () -> {
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setTitle(file); request.setDescription("DevSurf download");
                if (mime != null && !mime.isEmpty()) request.setMimeType(mime);
                request.addRequestHeader("User-Agent", agent);
                String cookies = CookieManager.getInstance().getCookie(url); if (cookies != null) request.addRequestHeader("Cookie", cookies);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, file);
                long id = ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                DownloadFeedback.started(this, id); toast("Download started.");
            } catch (Exception e) { toast("Unable to start this download."); }
        });
    }
    private void openDownloads() {
        if (!settingsOpen) capturePreview();
        settingsOpen = true; address.clearFocus();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(), 0);
        toolbar.setVisibility(View.GONE); bottom.setVisibility(View.GONE); progress.setVisibility(View.GONE);
        content.animate().cancel(); content.setAlpha(1f); content.setTranslationX(0f); content.removeAllViews();
        DownloadsPage page = new DownloadsPage(this, light, bg, panel, ink, muted, border, this::closeSettings);
        content.addView(page, new FrameLayout.LayoutParams(-1, -1));
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) { page.setAlpha(0f); page.setTranslationX(dp(24)); page.animate().alpha(1f).translationX(0f).setDuration(220).start(); }
    }
    private void share(String value) { startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, value), "Share with")); }
    private void copy(String value) { ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Link", value)); toast("Link copied."); }
    private void external(String link) {
        if (!(link.startsWith("mailto:") || link.startsWith("tel:") || link.startsWith("geo:") || link.startsWith("intent:"))) return;
        Dialogs.builder(this, light).setTitle("Open another app?").setMessage("This website wants to open a link outside DevSurf.").setNegativeButton("Cancel", null).setPositiveButton("Open", (d, w) -> {
            try { Intent i = link.startsWith("intent:") ? Intent.parseUri(link, Intent.URI_INTENT_SCHEME) : new Intent(Intent.ACTION_VIEW, Uri.parse(link)); i.addCategory(Intent.CATEGORY_BROWSABLE); i.setComponent(null); i.setSelector(null); i.setFlags(0); startActivity(i); } catch (Exception e) { toast("No app can open this link."); }
        }).show();
    }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private void exitFullscreen() {
        if (fullScreen == null) return;
        ((ViewGroup)fullScreen.getParent()).removeView(fullScreen); fullScreen = null;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (fullScreenCallback != null) { fullScreenCallback.onCustomViewHidden(); fullScreenCallback = null; }
    }
    private void back() {
        if (settingsOpen) { closeSettings(); return; }
        if (fullScreen != null) { exitFullscreen(); return; }
        Tab t = current(); if (!t.home && t.web.canGoBack()) t.web.goBack(); else if (!t.home) goHome(); else if (tabs.size() > 1) closeTab(selected); else super.onBackPressed();
    }
    @Override public void onBackPressed() { back(); }
    private void handleIntent(Intent intent) {
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null && isWeb(intent.getDataString())) newTab(intent.getDataString(), true);
        else if (Intent.ACTION_SEND.equals(intent.getAction())) { String value = intent.getStringExtra(Intent.EXTRA_TEXT); if (value != null) navigate(value); }
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 100 && upload != null) { upload.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data)); upload = null; }
    }
    @Override protected void onPause() { super.onPause(); if (selected >= 0) { current().web.onPause(); saveTabs(); } CookieManager.getInstance().flush(); }
    @Override protected void onResume() { super.onResume(); if (selected >= 0) current().web.onResume(); }
    @Override protected void onDestroy() { if (upload != null) upload.onReceiveValue(null); for (Tab t : tabs) { if (t.web.getParent() != null) ((ViewGroup)t.web.getParent()).removeView(t.web); t.web.destroy(); } super.onDestroy(); }
}
