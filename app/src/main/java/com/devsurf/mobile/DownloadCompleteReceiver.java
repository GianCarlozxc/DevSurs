package com.devsurf.mobile;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicBoolean;

/** Handles only verified, app-owned, successful downloads; cancellations/failures stay quiet. */
public final class DownloadCompleteReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
        long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (id < 0 || !DownloadFeedback.tracked(context, id)) return;
        PendingResult pending = goAsync(); AtomicBoolean finished = new AtomicBoolean();
        Handler main = new Handler(Looper.getMainLooper());
        Runnable timeout = () -> { if (finished.compareAndSet(false, true)) pending.finish(); };
        Runnable done = () -> { main.removeCallbacks(timeout); if (finished.compareAndSet(false, true)) pending.finish(); };
        main.postDelayed(timeout, 7500);
        Context app = context.getApplicationContext();
        new Thread(() -> {
            try (Cursor cursor = app.getSystemService(DownloadManager.class).query(new DownloadManager.Query().setFilterById(id))) {
                if (cursor == null || !cursor.moveToFirst()) { DownloadFeedback.forget(app, id); done.run(); return; }
                int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (status != DownloadManager.STATUS_SUCCESSFUL) {
                    if (status == DownloadManager.STATUS_FAILED) DownloadFeedback.forget(app, id);
                    done.run(); return;
                }
                if (!DownloadFeedback.forget(app, id) || finished.get()) { done.run(); return; }
                DownloadFeedback.completed(app, done);
            } catch (Exception e) { done.run(); }
        }, "devsurf-download-completion").start();
    }
}
