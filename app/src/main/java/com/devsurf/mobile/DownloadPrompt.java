package com.devsurf.mobile;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;
import java.util.Locale;

/** Branded file confirmation with separate file, source and destination details. */
final class DownloadPrompt {
    private final Activity activity;
    private final int ink = Color.parseColor("#f5f5f5"), muted = Color.parseColor("#8d8d8d"), border = Color.parseColor("#292929");
    private DownloadPrompt(Activity activity) { this.activity = activity; }
    static void show(Activity activity, String file, String url, String mime, Runnable download) { new DownloadPrompt(activity).show(file, url, mime, download); }
    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    private TextView text(String label, int size, int color) { TextView t = new TextView(activity); t.setText(label); t.setTextSize(size); t.setTextColor(color); return t; }
    private GradientDrawable surface(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); d.setStroke(dp(1), border); return d; }
    private void show(String file, String url, String mime, Runnable download) {
        Dialog dialog = new Dialog(activity, R.style.DevSurfDialog); dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL); body.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout heading = new LinearLayout(activity); heading.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout badge = new FrameLayout(activity); badge.setBackground(surface(Color.parseColor("#202020"), 12));
        badge.addView(new MenuIcon(activity, "download", ink), new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER)); heading.addView(badge, new LinearLayout.LayoutParams(dp(44), dp(44)));
        LinearLayout titles = new LinearLayout(activity); titles.setOrientation(LinearLayout.VERTICAL); titles.setPadding(dp(12), 0, 0, 0);
        TextView title = text("Download file", 20, ink); title.setTypeface(null, Typeface.BOLD); titles.addView(title);
        TextView subtitle = text("Save to your phone", 12, muted); subtitle.setPadding(0, dp(4), 0, 0); titles.addView(subtitle); heading.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(heading);

        LinearLayout fileCard = new LinearLayout(activity); fileCard.setGravity(Gravity.CENTER_VERTICAL); fileCard.setPadding(dp(14), dp(16), dp(14), dp(16)); fileCard.setBackground(surface(Color.parseColor("#080808"), 12));
        fileCard.addView(new MenuIcon(activity, "file", muted), new LinearLayout.LayoutParams(dp(28), dp(28)));
        LinearLayout details = new LinearLayout(activity); details.setOrientation(LinearLayout.VERTICAL); details.setPadding(dp(12), 0, 0, 0);
        TextView filename = text(file, 15, ink); filename.setMaxLines(3); filename.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE); filename.setTypeface(null, Typeface.BOLD); details.addView(filename);
        int dot = file.lastIndexOf('.'); String kind = dot >= 0 && dot < file.length() - 1 ? file.substring(dot + 1).toUpperCase(Locale.ROOT) + " file" : "File";
        if (mime != null && !mime.isEmpty() && !mime.equals("application/octet-stream")) kind = mime;
        TextView type = text(kind, 12, muted); type.setPadding(0, dp(6), 0, 0); details.addView(type); fileCard.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams cardSize = new LinearLayout.LayoutParams(-1, -2); cardSize.topMargin = dp(22); cardSize.bottomMargin = dp(18); body.addView(fileCard, cardSize);
        TextView from = text("FROM", 10, muted); from.setLetterSpacing(.14f); body.addView(from);
        String host = Uri.parse(url).getHost(); TextView source = text(host == null ? url : host, 13, ink); source.setMaxLines(2); source.setEllipsize(android.text.TextUtils.TruncateAt.END); source.setPadding(0, dp(6), 0, dp(18)); body.addView(source);
        LinearLayout destination = new LinearLayout(activity); destination.setGravity(Gravity.CENTER_VERTICAL);
        destination.addView(new MenuIcon(activity, "download", muted), new LinearLayout.LayoutParams(dp(16), dp(16)));
        TextView folder = text("Saves to Downloads", 12, muted); folder.setPadding(dp(8), 0, 0, 0); destination.addView(folder); body.addView(destination);
        LinearLayout buttons = new LinearLayout(activity); buttons.setBaselineAligned(false);
        TextView cancel = action("Cancel", false, dialog::dismiss);
        TextView confirm = action("Download", true, () -> { dialog.dismiss(); download.run(); });
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1); left.setMarginEnd(dp(6)); buttons.addView(cancel, left);
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, -2, 1); right.setMarginStart(dp(6)); buttons.addView(confirm, right);
        LinearLayout.LayoutParams buttonSize = new LinearLayout.LayoutParams(-1, -2); buttonSize.topMargin = dp(24); body.addView(buttons, buttonSize);
        ScrollView scroll = new ScrollView(activity); scroll.setFillViewport(false); scroll.addView(body); dialog.setContentView(scroll); dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = Math.min(activity.getResources().getDisplayMetrics().widthPixels - dp(40), dp(420));
            window.setBackgroundDrawable(surface(Color.parseColor("#111111"), 20)); window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            scroll.post(() -> { if (dialog.isShowing()) window.setLayout(width, Math.min(body.getMeasuredHeight(), (int)(activity.getResources().getDisplayMetrics().heightPixels * .85f))); });
        }
    }
    private TextView action(String label, boolean primary, Runnable callback) {
        TextView button = text(label, 14, primary ? Color.parseColor("#111111") : ink); button.setGravity(Gravity.CENTER); button.setMinHeight(dp(48)); button.setPadding(dp(8), dp(14), dp(8), dp(14));
        button.setTypeface(null, primary ? Typeface.BOLD : Typeface.NORMAL);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33808080), surface(Color.parseColor(primary ? "#f5f5f5" : "#202020"), 10), null));
        button.setFocusable(true); button.setOnClickListener(v -> callback.run()); return button;
    }
}
