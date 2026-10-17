package com.devsurf.mobile;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** App-owned DownloadManager history, queried off the UI thread. */
final class DownloadsPage extends LinearLayout {
    private final Activity activity;
    private final DownloadManager manager;
    private final boolean light;
    private final int ink, muted, panel, border;
    private final LinearLayout list;
    private final ScrollView scroll;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private boolean attached, loading;
    private String lastSnapshot;
    private final Runnable refresh = this::load;

    private static final class Item {
        long id, bytes, total;
        int status;
        String title;
    }
    DownloadsPage(Activity activity, boolean light, int bg, int panel, int ink, int muted, int border, Runnable close) {
        super(activity); this.activity = activity; this.light = light; this.panel = panel; this.ink = ink; this.muted = muted; this.border = border;
        manager = activity.getSystemService(DownloadManager.class);
        setOrientation(VERTICAL); setBackgroundColor(bg);
        LinearLayout header = new LinearLayout(activity); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(8), dp(8), dp(20), dp(8));
        TextView back = label("‹", 28, ink); back.setGravity(Gravity.CENTER); back.setBackgroundResource(R.drawable.tap_feedback); back.setContentDescription("Back to browser"); back.setOnClickListener(v -> close.run());
        header.addView(back, new LayoutParams(dp(48), dp(48)));
        TextView title = label("Downloads", 24, ink); title.setTypeface(null, Typeface.BOLD); header.addView(title); addView(header);
        scroll = new ScrollView(activity); scroll.setFillViewport(true);
        list = new LinearLayout(activity); list.setOrientation(VERTICAL); list.setPadding(dp(20), dp(12), dp(20), dp(28)); scroll.addView(list);
        addView(scroll, new LayoutParams(-1, 0, 1));
        list.addView(label("Loading downloads…", 14, muted));
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView label(String value, int size, int color) { TextView t = new TextView(activity); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t; }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); attached = true; load(); }
    @Override protected void onDetachedFromWindow() { attached = false; handler.removeCallbacks(refresh); worker.shutdownNow(); super.onDetachedFromWindow(); }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (handler == null) return;
        handler.removeCallbacks(refresh);
        if (visibility == VISIBLE && attached) load();
    }
    private void load() {
        if (!attached || loading || getWindowVisibility() != VISIBLE) return;
        handler.removeCallbacks(refresh); loading = true;
        worker.execute(() -> {
            ArrayList<Item> items = new ArrayList<>(); boolean failed = false;
            try (Cursor cursor = manager.query(new DownloadManager.Query())) {
                if (cursor != null) while (cursor.moveToNext()) {
                    Item item = new Item();
                    item.id = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
                    item.title = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE));
                    item.status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    item.bytes = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    item.total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    items.add(item);
                }
            } catch (Exception e) { failed = true; }
            final boolean error = failed;
            handler.post(() -> {
                loading = false;
                if (!attached || getWindowVisibility() != VISIBLE) return;
                StringBuilder snapshot = new StringBuilder(Boolean.toString(error));
                for (Item item : items) snapshot.append('|').append(item.id).append(':').append(item.status).append(':').append(item.bytes).append(':').append(item.total).append(':').append(item.title);
                String nextSnapshot = snapshot.toString();
                if (!nextSnapshot.equals(lastSnapshot)) { lastSnapshot = nextSnapshot; render(items, error); }
                handler.postDelayed(refresh, 1500);
            });
        });
    }
    private void render(ArrayList<Item> items, boolean error) {
        int y = scroll.getScrollY(); list.removeAllViews();
        if (error || items.isEmpty()) {
            LinearLayout empty = new LinearLayout(activity); empty.setOrientation(VERTICAL); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(24), dp(64), dp(24), dp(48));
            empty.addView(new MenuIcon(activity, "download", muted), new LayoutParams(dp(48), dp(48)));
            TextView title = label(error ? "Downloads unavailable" : "No downloads yet", 20, ink); title.setGravity(Gravity.CENTER); title.setPadding(0, dp(20), 0, dp(8)); empty.addView(title);
            TextView hint = label(error ? "Your download history could not be loaded. Try reopening this page." : "Files you download in DevSurf will appear here.", 14, muted); hint.setGravity(Gravity.CENTER); empty.addView(hint); list.addView(empty); return;
        }
        TextView caption = label("Your DevSurf downloads", 13, muted); caption.setPadding(dp(4), 0, 0, dp(16)); list.addView(caption);
        for (Item item : items) addItem(item);
        scroll.post(() -> { if (attached) scroll.scrollTo(0, y); });
    }
    private void addItem(Item item) {
        boolean active = item.status == DownloadManager.STATUS_PENDING || item.status == DownloadManager.STATUS_RUNNING || item.status == DownloadManager.STATUS_PAUSED;
        LinearLayout card = new LinearLayout(activity); card.setOrientation(VERTICAL); card.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable surface = new GradientDrawable(); surface.setColor(panel); surface.setCornerRadius(dp(12)); surface.setStroke(dp(1), border); card.setBackground(surface);
        LinearLayout row = new LinearLayout(activity); row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(new MenuIcon(activity, "download", muted), new LayoutParams(dp(22), dp(22)));
        LinearLayout details = new LinearLayout(activity); details.setOrientation(VERTICAL); details.setPadding(dp(14), 0, dp(8), 0);
        TextView name = label(item.title == null ? "Download" : item.title, 15, ink); name.setMaxLines(2); name.setEllipsize(android.text.TextUtils.TruncateAt.END); details.addView(name);
        String status;
        if (item.status == DownloadManager.STATUS_SUCCESSFUL) status = "Downloaded";
        else if (item.status == DownloadManager.STATUS_FAILED) status = "Download failed";
        else if (item.status == DownloadManager.STATUS_PAUSED) status = "Waiting to resume";
        else if (item.status == DownloadManager.STATUS_PENDING) status = "Queued";
        else status = "Downloading";
        if (item.total > 0) status += " · " + (active ? Formatter.formatShortFileSize(activity, Math.max(0, item.bytes)) + " / " : "") + Formatter.formatShortFileSize(activity, item.total);
        TextView state = label(status, 12, muted); state.setPadding(0, dp(6), 0, 0); details.addView(state); row.addView(details, new LayoutParams(0, -2, 1));
        if (active) {
            TextView cancel = label("×", 24, ink); cancel.setGravity(Gravity.CENTER); cancel.setBackgroundResource(R.drawable.tap_feedback); cancel.setContentDescription("Cancel download");
            cancel.setOnClickListener(v -> Dialogs.builder(activity, light).setTitle("Cancel download?").setMessage(item.title).setNegativeButton("Keep downloading", null).setPositiveButton("Cancel download", (d, w) -> { DownloadFeedback.forget(activity, item.id); manager.remove(item.id); load(); }).show());
            row.addView(cancel, new LayoutParams(dp(44), dp(44)));
        } else if (item.status == DownloadManager.STATUS_SUCCESSFUL) {
            TextView open = label("Open", 13, ink); open.setPadding(dp(8), dp(12), 0, dp(12)); row.addView(open);
            card.setOnClickListener(v -> open(item)); card.setFocusable(true); card.setContentDescription("Open " + item.title);
        }
        card.addView(row);
        if (active) {
            ProgressBar progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal); progress.setMax(100);
            progress.setProgressTintList(ColorStateList.valueOf(muted)); progress.setIndeterminateTintList(ColorStateList.valueOf(muted));
            progress.setIndeterminate(item.total <= 0); if (item.total > 0) progress.setProgress((int)Math.min(100, Math.max(0, item.bytes * 100d / item.total)));
            LayoutParams bar = new LayoutParams(-1, dp(4)); bar.topMargin = dp(14); card.addView(progress, bar);
        }
        LayoutParams size = new LayoutParams(-1, -2); size.bottomMargin = dp(12); list.addView(card, size);
    }
    private void open(Item item) {
        try {
            Uri uri = manager.getUriForDownloadedFile(item.id);
            if (uri == null) { Toast.makeText(activity, "This file is no longer available.", Toast.LENGTH_SHORT).show(); return; }
            String mime = manager.getMimeTypeForDownloadedFile(item.id);
            activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime == null ? "application/octet-stream" : mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
        } catch (android.content.ActivityNotFoundException e) { Toast.makeText(activity, "No installed app can open this file type.", Toast.LENGTH_SHORT).show(); }
        catch (Exception e) { Toast.makeText(activity, "This file could not be opened.", Toast.LENGTH_SHORT).show(); }
    }
}
