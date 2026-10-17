package com.devsurf.mobile;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Shared bookmark and history layout, grouped by the device's local calendar date. */
final class SavedPagesPage extends LinearLayout {
    interface Navigate { void open(String url); }
    private final Activity activity;
    private final SharedPreferences prefs;
    private final boolean light;
    private final int ink, muted, panel;
    private final Navigate navigate;
    private final LinearLayout entries;
    private final ScrollView scroll;
    private final TextView clear;
    private final String key;
    private final boolean bookmarks;

    SavedPagesPage(Activity activity, SharedPreferences prefs, String key, boolean light, int bg, int panel, int ink, int muted, Runnable back, Navigate navigate) {
        super(activity); this.activity = activity; this.prefs = prefs; this.light = light;
        this.key = key; bookmarks = key.equals("bookmarks");
        this.ink = ink; this.muted = muted; this.panel = panel; this.navigate = navigate;
        setOrientation(VERTICAL); setBackgroundColor(bg);
        LinearLayout header = new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(8), dp(8), dp(16), dp(8));
        TextView close = action("‹", "Back to browser", back); close.setTextSize(28); header.addView(close, new LayoutParams(dp(48), dp(48)));
        TextView title = label(bookmarks ? "Bookmarks" : "History", 24, ink); title.setTypeface(null, Typeface.BOLD); header.addView(title, new LayoutParams(0, -2, 1));
        clear = action("Clear all", bookmarks ? "Clear bookmarks" : "Clear browsing history", () -> Dialogs.builder(activity, light).setTitle(bookmarks ? "Clear bookmarks?" : "Clear history?").setMessage(bookmarks ? "Remove all saved bookmarks from DevSurf?" : "Remove your browsing history from DevSurf? Your bookmarks and downloaded files will stay saved.").setNegativeButton("Cancel", null).setPositiveButton("Clear", (d, w) -> { prefs.edit().remove(key).apply(); render(false); }).show());
        clear.setTextSize(13); clear.setPadding(dp(12), 0, dp(12), 0); header.addView(clear, new LayoutParams(-2, dp(48))); addView(header);
        scroll = new ScrollView(activity); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        entries = new LinearLayout(activity); entries.setOrientation(VERTICAL); entries.setPadding(dp(12), 0, dp(12), dp(28)); scroll.addView(entries);
        addView(scroll, new LayoutParams(-1, 0, 1)); render(false);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size, int color) { TextView t = new TextView(activity); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    private TextView action(String title, String description, Runnable callback) {
        TextView t = label(title, 22, ink); t.setGravity(Gravity.CENTER); t.setContentDescription(description); t.setFocusable(true); t.setBackgroundResource(R.drawable.tap_feedback); t.setOnClickListener(v -> callback.run()); return t;
    }
    private JSONArray savedItems() { try { return new JSONArray(prefs.getString(key, "[]")); } catch (Exception e) { return new JSONArray(); } }
    private void render(boolean preserveScroll) {
        int y = preserveScroll ? scroll.getScrollY() : 0; entries.removeAllViews(); JSONArray stored = savedItems();
        java.util.ArrayList<JSONObject> items = new java.util.ArrayList<>();
        for (int i = 0; i < stored.length(); i++) { JSONObject item = stored.optJSONObject(i); if (item != null) items.add(item); }
        items.sort((a, b) -> Long.compare(b.optLong("time"), a.optLong("time")));
        clear.setEnabled(!items.isEmpty()); clear.setAlpha(!items.isEmpty() ? 1f : .35f);
        if (items.isEmpty()) {
            LinearLayout empty = new LinearLayout(activity); empty.setOrientation(VERTICAL); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(24), dp(72), dp(24), dp(40));
            empty.addView(new MenuIcon(activity, bookmarks ? "star" : "history", muted), new LayoutParams(dp(48), dp(48)));
            TextView title = label(bookmarks ? "No bookmarks yet" : "No history yet", 20, ink); title.setPadding(0, dp(20), 0, dp(8)); empty.addView(title);
            TextView hint = label(bookmarks ? "Save a page from the browser menu to find it here." : "Pages you visit will appear here.", 14, muted); hint.setGravity(Gravity.CENTER); empty.addView(hint); entries.addView(empty); return;
        }
        String previousGroup = null;
        for (JSONObject item : items) {
            String group = dateLabel(item.optLong("time", 0));
            if (!group.equals(previousGroup)) { TextView heading = label(group, 13, muted); heading.setPadding(dp(6), dp(24), dp(6), dp(12)); entries.addView(heading); previousGroup = group; }
            addEntry(item);
        }
        scroll.post(() -> scroll.scrollTo(0, y));
    }
    private String dateLabel(long time) {
        if (time <= 0) return "Earlier";
        LocalDate date = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).toLocalDate(); LocalDate today = LocalDate.now();
        String formatted = date.format(DateTimeFormatter.ofPattern("MMM d, uuuu", Locale.getDefault()));
        return date.equals(today) ? "Today · " + formatted : date.equals(today.minusDays(1)) ? "Yesterday · " + formatted : formatted;
    }
    private void addEntry(JSONObject item) {
        String url = item.optString("url"); String title = item.optString("title", "");
        String host = Uri.parse(url).getHost(); if (host == null) host = url; if (host.startsWith("www.")) host = host.substring(4);
        if (title.trim().isEmpty()) title = host;
        LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(6), dp(10), 0, dp(10)); row.setBackgroundResource(R.drawable.tap_feedback);
        FrameLayout badge = new FrameLayout(activity); GradientDrawable circle = new GradientDrawable(); circle.setColor(panel); circle.setShape(GradientDrawable.OVAL); badge.setBackground(circle);
        int icon = host.equals("youtube.com") || host.endsWith(".youtube.com") ? R.drawable.shortcut_youtube : host.equals("wikipedia.org") || host.endsWith(".wikipedia.org") ? R.drawable.shortcut_wikipedia : host.equals("google.com") || host.endsWith(".google.com") ? R.drawable.shortcut_google : 0;
        if (icon != 0) { ImageView image = new ImageView(activity); image.setImageResource(icon); image.setScaleType(ImageView.ScaleType.FIT_CENTER); badge.addView(image, new FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER)); }
        else { TextView initial = label(host.isEmpty() ? "•" : host.substring(0, 1).toUpperCase(Locale.ROOT), 16, ink); initial.setGravity(Gravity.CENTER); badge.addView(initial, new FrameLayout.LayoutParams(-1, -1)); }
        row.addView(badge, new LayoutParams(dp(40), dp(40)));
        LinearLayout labels = new LinearLayout(activity); labels.setOrientation(VERTICAL); labels.setPadding(dp(16), 0, dp(8), 0);
        TextView name = label(title, 15, ink); name.setSingleLine(); name.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(name);
        TextView domain = label(host, 13, muted); domain.setSingleLine(); domain.setEllipsize(android.text.TextUtils.TruncateAt.END); domain.setPadding(0, dp(5), 0, 0); labels.addView(domain);
        row.addView(labels, new LayoutParams(0, -2, 1));
        TextView remove = action("⊗", "Remove " + title + (bookmarks ? " from bookmarks" : " from history"), () -> remove(item)); remove.setTextSize(21); remove.setTextColor(muted); row.addView(remove, new LayoutParams(dp(48), dp(48)));
        row.setFocusable(true); row.setOnClickListener(v -> navigate.open(url)); row.setContentDescription(title + ", " + host); entries.addView(row, new LayoutParams(-1, -2));
    }
    private void remove(JSONObject item) {
        // Read again so a page finishing in the background cannot overwrite newer history.
        JSONArray latest = savedItems();
        for (int i = 0; i < latest.length(); i++) {
            JSONObject candidate = latest.optJSONObject(i);
            if (candidate != null && candidate.optString("url").equals(item.optString("url")) && candidate.optLong("time") == item.optLong("time")) { latest.remove(i); break; }
        }
        prefs.edit().putString(key, latest.toString()).apply(); render(true);
    }
}
