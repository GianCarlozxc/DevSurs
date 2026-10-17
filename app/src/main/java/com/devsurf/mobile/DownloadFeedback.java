package com.devsurf.mobile;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;

/** Short, queued original sounds using the device's notification audio settings. */
final class DownloadFeedback {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ArrayDeque<Cue> QUEUE = new ArrayDeque<>();
    private static Cue active;
    private static MediaPlayer player;
    private static Runnable watchdog;
    private static final String PENDING = "downloads_waiting_for_sound";

    private static final class Cue {
        final Context context;
        final int resource;
        final Runnable done;
        final long expires = SystemClock.elapsedRealtime() + 5000;
        Cue(Context context, int resource, Runnable done) { this.context = context.getApplicationContext(); this.resource = resource; this.done = done; }
    }
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences("devsurf", Context.MODE_PRIVATE); }
    static boolean enabled(Context context) { return prefs(context).getBoolean("download_sounds", true); }
    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("download_sounds", enabled).apply();
        if (!enabled) MAIN.post(() -> {
            while (!QUEUE.isEmpty()) QUEUE.remove().done.run();
            if (active != null) finish(active);
        });
    }
    static void started(Context context, long id) {
        synchronized (DownloadFeedback.class) {
            SharedPreferences p = prefs(context);
            HashSet<String> pending = new HashSet<>(p.getStringSet(PENDING, Collections.emptySet())); pending.add(Long.toString(id));
            p.edit().putStringSet(PENDING, pending).apply();
        }
        enqueue(context, R.raw.download_start, () -> {});
    }
    static synchronized boolean tracked(Context context, long id) { return prefs(context).getStringSet(PENDING, Collections.emptySet()).contains(Long.toString(id)); }
    static synchronized boolean forget(Context context, long id) {
        SharedPreferences p = prefs(context); HashSet<String> pending = new HashSet<>(p.getStringSet(PENDING, Collections.emptySet()));
        if (!pending.remove(Long.toString(id))) return false;
        p.edit().putStringSet(PENDING, pending).apply(); return true;
    }
    static void completed(Context context, Runnable done) { enqueue(context, R.raw.download_complete, done); }
    private static void enqueue(Context context, int resource, Runnable done) {
        MAIN.post(() -> {
            if (!enabled(context) || QUEUE.size() >= 24) { done.run(); return; }
            QUEUE.add(new Cue(context, resource, done)); playNext();
        });
    }
    private static void playNext() {
        if (active != null || QUEUE.isEmpty()) return;
        Cue cue = QUEUE.remove();
        AudioManager audio = cue.context.getSystemService(AudioManager.class);
        if (!enabled(cue.context) || SystemClock.elapsedRealtime() > cue.expires || audio.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
            cue.done.run(); playNext(); return;
        }
        active = cue;
        try {
            AudioAttributes attributes = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            player = MediaPlayer.create(cue.context, cue.resource, attributes, audio.generateAudioSessionId());
            if (player == null) { finish(cue); return; }
            player.setVolume(.65f, .65f);
            player.setOnCompletionListener(p -> finish(cue)); player.setOnErrorListener((p, what, extra) -> { finish(cue); return true; });
            watchdog = () -> finish(cue); MAIN.postDelayed(watchdog, 1800); player.start();
        } catch (Exception e) { finish(cue); }
    }
    private static void finish(Cue cue) {
        if (active != cue) return;
        if (watchdog != null) { MAIN.removeCallbacks(watchdog); watchdog = null; }
        if (player != null) { player.release(); player = null; }
        active = null; cue.done.run(); playNext();
    }
}
