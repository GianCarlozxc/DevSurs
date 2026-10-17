package com.devsurf.mobile;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Dedicated provider launcher using the browser's two-column card layout. */
final class AiChatPage extends LinearLayout {
    interface Open { void provider(String url); }
    private final int panel, ink, muted, border;
    AiChatPage(Context context, int bg, int panel, int ink, int muted, int border, Runnable back, Open open) {
        super(context); this.panel = panel; this.ink = ink; this.muted = muted; this.border = border;
        setOrientation(VERTICAL); setBackgroundColor(bg);
        LinearLayout header = new LinearLayout(context); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(8), dp(8), dp(20), dp(8));
        TextView close = label("‹", 28, ink); close.setGravity(Gravity.CENTER); close.setBackgroundResource(R.drawable.tap_feedback); close.setContentDescription("Back to browser"); close.setOnClickListener(v -> back.run());
        header.addView(close, new LayoutParams(dp(48), dp(48)));
        TextView title = label("AI Chat", 24, ink); title.setTypeface(null, Typeface.BOLD); header.addView(title); addView(header);
        ScrollView scroll = new ScrollView(context); scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(context); body.setOrientation(VERTICAL); body.setPadding(dp(12), dp(8), dp(12), dp(28));
        TextView subtitle = label("Choose an assistant", 14, muted); subtitle.setPadding(dp(6), 0, dp(6), dp(18)); body.addView(subtitle);
        GridLayout grid = new GridLayout(context); grid.setColumnCount(2);
        addProvider(grid, 0, "ChatGPT", "chatgpt.com", "Ask, write and explore.", "chat", "https://chatgpt.com/", open);
        addProvider(grid, 1, "Claude", "claude.ai", "Think, create and learn.", "star", "https://claude.ai/new", open);
        body.addView(grid, new LayoutParams(-1, -2)); scroll.addView(body); addView(scroll, new LayoutParams(-1, 0, 1));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size, int color) { TextView t = new TextView(getContext()); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    private GradientDrawable surface(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), border); return d; }
    private void addProvider(GridLayout grid, int column, String name, String domain, String description, String icon, String url, Open open) {
        LinearLayout card = new LinearLayout(getContext()); card.setOrientation(VERTICAL); card.setPadding(dp(3), dp(3), dp(3), dp(3));
        card.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080), surface(panel, 18), null)); card.setClipToOutline(true);
        card.setFocusable(true); card.setContentDescription("Open " + name); card.setOnClickListener(v -> open.provider(url));
        LinearLayout heading = new LinearLayout(getContext()); heading.setGravity(Gravity.CENTER_VERTICAL); heading.setPadding(dp(10), 0, dp(10), 0);
        heading.addView(new MenuIcon(getContext(), icon, ink), new LayoutParams(dp(20), dp(20)));
        TextView title = label(name, 14, ink); title.setSingleLine(); title.setPadding(dp(8), 0, dp(4), 0); heading.addView(title, new LayoutParams(0, -2, 1));
        heading.addView(label("↗", 16, muted)); card.addView(heading, new LayoutParams(-1, dp(44)));
        LinearLayout preview = new LinearLayout(getContext()); preview.setOrientation(VERTICAL); preview.setGravity(Gravity.CENTER); preview.setPadding(dp(12), dp(20), dp(12), dp(20)); preview.setBackground(surface(panel, 12));
        preview.addView(new MenuIcon(getContext(), icon, ink), new LayoutParams(dp(42), dp(42)));
        TextView provider = label(name, 20, ink); provider.setGravity(Gravity.CENTER); provider.setTypeface(null, Typeface.BOLD); provider.setPadding(0, dp(20), 0, dp(8)); preview.addView(provider, new LayoutParams(-1, -2));
        TextView summary = label(description, 12, muted); summary.setGravity(Gravity.CENTER); preview.addView(summary, new LayoutParams(-1, -2));
        TextView site = label(domain, 11, muted); site.setGravity(Gravity.CENTER); site.setPadding(0, dp(20), 0, 0); preview.addView(site, new LayoutParams(-1, -2));
        card.addView(preview, new LayoutParams(-1, 0, 1));
        GridLayout.LayoutParams size = new GridLayout.LayoutParams(GridLayout.spec(0), GridLayout.spec(column, 1f)); size.width = 0;
        size.height = Math.max(dp(270), Math.round(dp(270) * getResources().getConfiguration().fontScale));
        size.setMargins(dp(6), dp(6), dp(6), dp(6)); grid.addView(card, size);
    }
}
