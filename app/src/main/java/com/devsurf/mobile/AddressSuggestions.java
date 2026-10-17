package com.devsurf.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/** Local suggestions: no typed text is sent to a remote service. */
final class AddressSuggestions extends BaseAdapter implements Filterable {
    static final class Suggestion {
        final String title, subtitle, target, icon;
        Suggestion(String title, String subtitle, String target, String icon) { this.title = title; this.subtitle = subtitle; this.target = target; this.icon = icon; }
    }
    private static final String[][] SITES = {
        {"Google", "https://www.google.com"}, {"YouTube", "https://m.youtube.com"},
        {"Wikipedia", "https://www.wikipedia.org"}, {"Facebook", "https://www.facebook.com"},
        {"Instagram", "https://www.instagram.com"}, {"GitHub", "https://github.com"},
        {"Reddit", "https://www.reddit.com"}, {"ChatGPT", "https://chatgpt.com"},
        {"Gmail", "https://mail.google.com"}, {"Google Maps", "https://maps.google.com"},
        {"Canva", "https://www.canva.com"}, {"TikTok", "https://www.tiktok.com"},
        {"Netflix", "https://www.netflix.com"}, {"Amazon", "https://www.amazon.com"},
        {"Shopee", "https://shopee.ph"}, {"Bing", "https://www.bing.com"},
        {"DuckDuckGo", "https://duckduckgo.com"}
    };
    private final Context context;
    private final SharedPreferences prefs;
    private final int ink, muted;
    private ArrayList<Suggestion> suggestions = new ArrayList<>();
    AddressSuggestions(Context context, SharedPreferences prefs, int ink, int muted) { this.context = context; this.prefs = prefs; this.ink = ink; this.muted = muted; }
    @Override public int getCount() { return suggestions.size(); }
    @Override public Suggestion getItem(int position) { return suggestions.get(position); }
    @Override public long getItemId(int position) { return position; }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    @Override public View getView(int position, View convertView, ViewGroup parent) {
        Suggestion item = getItem(position);
        LinearLayout row = new LinearLayout(context); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(16), dp(12), dp(16), dp(12)); row.setMinimumHeight(dp(62)); row.setBackgroundResource(R.drawable.tap_feedback);
        row.addView(new MenuIcon(context, item.icon, muted), new LinearLayout.LayoutParams(dp(22), dp(22)));
        LinearLayout labels = new LinearLayout(context); labels.setOrientation(LinearLayout.VERTICAL); labels.setPadding(dp(14), 0, 0, 0);
        TextView title = new TextView(context); title.setText(item.title); title.setTextSize(15); title.setTextColor(ink); title.setSingleLine(); title.setEllipsize(android.text.TextUtils.TruncateAt.END); labels.addView(title);
        TextView subtitle = new TextView(context); subtitle.setText(item.subtitle); subtitle.setTextSize(12); subtitle.setTextColor(muted); subtitle.setSingleLine(); subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END); subtitle.setPadding(0, dp(4), 0, 0); labels.addView(subtitle);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1)); return row;
    }
    @Override public Filter getFilter() { return filter; }
    private final Filter filter = new Filter() {
        @Override protected FilterResults performFiltering(CharSequence constraint) {
            String raw = constraint == null ? "" : constraint.toString().trim();
            String query = raw.toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
            ArrayList<Suggestion> result = new ArrayList<>(); HashSet<String> urls = new HashSet<>();
            if (!raw.isEmpty()) {
                if (raw.matches("(?i)^(https?://)?([a-z0-9-]+\\.)+[a-z]{2,}(:[0-9]+)?([/?#][^\\s]*)?$")) {
                    String target = raw.matches("(?i)^https?://.*") ? raw : "https://" + raw;
                    result.add(new Suggestion("Go to " + raw, "Website", target, "desktop")); urls.add(canonical(target));
                }
                String engine = prefs.getString("engine", "Google");
                String endpoint = engine.equals("DuckDuckGo") ? "https://duckduckgo.com/?q=" : engine.equals("Bing") ? "https://www.bing.com/search?q=" : "https://www.google.com/search?q=";
                result.add(new Suggestion(raw, "Search with " + engine, endpoint + Uri.encode(raw), "find"));
            }
            addSaved(result, urls, "bookmarks", query, "star");
            for (String[] site : SITES) {
                if (result.size() >= 8) break;
                if (matches(site[0], site[1], query) && urls.add(canonical(site[1]))) result.add(new Suggestion(site[0], domain(site[1]), site[1], "desktop"));
            }
            addSaved(result, urls, "history", query, "history");
            FilterResults response = new FilterResults(); response.values = result; response.count = result.size(); return response;
        }
        @SuppressWarnings("unchecked")
        @Override protected void publishResults(CharSequence constraint, FilterResults results) { suggestions = (ArrayList<Suggestion>)results.values; notifyDataSetChanged(); }
        @Override public CharSequence convertResultToString(Object value) { return value instanceof Suggestion ? ((Suggestion)value).target : ""; }
    };
    private void addSaved(ArrayList<Suggestion> result, HashSet<String> urls, String key, String query, String icon) {
        try {
            JSONArray items = new JSONArray(prefs.getString(key, "[]"));
            for (int offset = 0; offset < items.length() && result.size() < 8; offset++) {
                int index = key.equals("bookmarks") ? items.length() - 1 - offset : offset;
                JSONObject item = items.optJSONObject(index); if (item == null) continue;
                String url = item.optString("url"), title = item.optString("title");
                if (!(url.startsWith("https://") || url.startsWith("http://"))) continue;
                if (matches(title, url, query) && urls.add(canonical(url))) result.add(new Suggestion(title.isEmpty() ? domain(url) : title, domain(url) + (key.equals("bookmarks") ? " · Bookmark" : " · History"), url, icon));
            }
        } catch (org.json.JSONException ignored) {}
    }
    private static boolean matches(String title, String url, String query) { return query.isEmpty() || title.toLowerCase(Locale.ROOT).contains(query) || url.toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceFirst("^www\\.", "").contains(query); }
    private static String domain(String url) { String host = Uri.parse(url).getHost(); return host == null ? url : host.replaceFirst("^www\\.", ""); }
    private static String canonical(String url) { return url.replaceFirst("^https?://", "").replaceFirst("^www\\.", "").replaceFirst("/$", "").toLowerCase(Locale.ROOT); }
}
