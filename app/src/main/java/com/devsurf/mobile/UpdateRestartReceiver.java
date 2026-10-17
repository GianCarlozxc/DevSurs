package com.devsurf.mobile;

import android.app.*;
import android.content.*;

/** Reopen only after an update explicitly requested in DevSurf replaces this package. */
public final class UpdateRestartReceiver extends BroadcastReceiver {
    private static final int NOTICE = 4101;
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences("devsurf_install", 0); }
    static boolean prepare(Context context, String version, int session, String nonce) {
        return prefs(context).edit().putString("version", version).putInt("session", session).putString("nonce", nonce).putLong("requested", System.currentTimeMillis()).commit();
    }
    static boolean matches(Context context, Intent intent) {
        SharedPreferences p = prefs(context); String nonce = p.getString("nonce", "");
        return !nonce.isEmpty() && nonce.equals(intent.getStringExtra("install_nonce"))
            && p.getInt("session", -1) == intent.getIntExtra(android.content.pm.PackageInstaller.EXTRA_SESSION_ID, -2);
    }
    static void cancel(Context context) { prefs(context).edit().clear().commit(); }
    static void opened(Context context) { context.getSystemService(NotificationManager.class).cancel(NOTICE); }
    static synchronized boolean reopen(Context context) {
        SharedPreferences saved = prefs(context); long age = System.currentTimeMillis() - saved.getLong("requested", 0);
        if (!BuildConfig.VERSION_NAME.equals(saved.getString("version", "")) || age < 0 || age > 2 * 60 * 60 * 1000L) return false;
        cancel(context);
        Intent open = Intent.makeRestartActivityTask(new ComponentName(context, MainActivity.class));
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager.areNotificationsEnabled()) {
            manager.createNotificationChannel(new NotificationChannel("app_updates", "App updates", NotificationManager.IMPORTANCE_LOW));
            PendingIntent tap = PendingIntent.getActivity(context, NOTICE, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(context, "app_updates").setSmallIcon(R.drawable.update_notification)
                .setContentTitle("DevSurf updated").setContentText("Tap to open DevSurf " + BuildConfig.VERSION_NAME + ".")
                .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true).build();
            try { manager.notify(NOTICE, notification); } catch (SecurityException ignored) {}
        }
        // Android may decline a background launch. The notification and app icon remain available.
        try { context.startActivity(open); } catch (Exception ignored) {}
        return true;
    }
    @Override public void onReceive(Context context, Intent intent) { if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) reopen(context); }
}
