package com.devsurf.mobile;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Persistent system download, private verified APK, and Android installation session. */
final class AppUpdater {
    private static AppUpdater instance;
    static synchronized AppUpdater get(Context context) { if (instance == null) instance = new AppUpdater(context.getApplicationContext()); return instance; }
    private final Context context;
    private final DownloadManager downloads;
    private final SharedPreferences prefs;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    GitHubUpdates.Release latest, active;
    boolean checking, checked, verifying, installing;
    String error = "";
    int percent = -1;
    private long downloadId = -1;
    private File ready;
    private volatile int generation;
    private boolean polling;
    private final Runnable ticker = () -> poll();
    private AppUpdater(Context context) {
        this.context = context; downloads = context.getSystemService(DownloadManager.class); prefs = context.getSharedPreferences("devsurf_updates", 0);
        error = prefs.getString("lastInstallError", "");
        try {
            String saved = prefs.getString("release", "");
            if (!saved.isEmpty()) { active = new GitHubUpdates.Release(new JSONObject(saved)); latest = active; }
            downloadId = prefs.getLong("downloadId", -1);
            if (active != null && prefs.getBoolean("ready", false) && readyFile(active).isFile()) ready = readyFile(active);
            if (active != null && !GitHubUpdates.newer(active.version, BuildConfig.VERSION_NAME)) { cancel(); latest = null; }
            else if (downloadId >= 0) main.post(ticker);
        } catch (Exception ignored) { cancel(); latest = null; }
    }
    boolean available() { return latest != null && GitHubUpdates.newer(latest.version, BuildConfig.VERSION_NAME); }
    boolean busy() { return downloadId >= 0 || verifying || installing; }
    boolean ready() { if (ready != null && !ready.isFile()) { ready = null; prefs.edit().remove("ready").apply(); } return ready != null; }
    private File directory() { return new File(context.getFilesDir(), "updates"); }
    private File readyFile(GitHubUpdates.Release release) { return new File(directory(), "devsurf-" + release.assetId + ".apk"); }
    void check() {
        if (checking || busy() || ready()) return;
        checking = true; error = ""; prefs.edit().remove("lastInstallError").apply(); final int token = generation;
        worker.execute(() -> {
            try { GitHubUpdates.Release release = GitHubUpdates.latest(); main.post(() -> { if (token != generation) return; latest = release; checked = true; checking = false; }); }
            catch (Exception e) { main.post(() -> { if (token != generation) return; checking = false; error = message(e, "Could not check for updates. Check your connection and try again."); }); }
        });
    }
    void download() {
        if (checking || busy() || !available()) return;
        cancel(); active = latest; error = "";
        try {
            File root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (root == null) throw new IOException("Download storage is unavailable.");
            File folder = new File(root, "updates"); if (!folder.isDirectory() && !folder.mkdirs()) throw new IOException("Could not prepare update storage.");
            File destination = new File(folder, "devsurf-" + active.assetId + ".apk");
            if (destination.exists() && !destination.delete()) throw new IOException("Could not replace the previous update download.");
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(active.url)).setTitle("DevSurf " + active.version + " update").setDescription("Downloading app update")
                .setMimeType("application/vnd.android.package-archive").setAllowedOverRoaming(false).setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(destination)).addRequestHeader("User-Agent", "DevSurf/" + BuildConfig.VERSION_NAME);
            downloadId = downloads.enqueue(request);
            prefs.edit().putString("release", active.json().toString()).putLong("downloadId", downloadId).remove("ready").apply(); main.post(ticker);
        } catch (Exception e) { if (downloadId >= 0) downloads.remove(downloadId); downloadId = -1; error = message(e, "Could not start the update download."); }
    }
    private void poll() {
        main.removeCallbacks(ticker);
        if (downloadId < 0 || verifying || polling) return;
        polling = true; final int token = generation; final long id = downloadId; final GitHubUpdates.Release release = active;
        worker.execute(() -> {
            try (Cursor row = downloads.query(new DownloadManager.Query().setFilterById(id))) {
                if (row == null || !row.moveToFirst()) throw new IOException("The update download was removed. Download it again.");
                int status = row.getInt(row.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                long bytes = row.getLong(row.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)), total = row.getLong(row.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                int progress = total > 0 ? (int)Math.min(99, bytes * 100d / total) : -1;
                if (bytes > release.size || (total > 0 && total != release.size)) throw new IOException("The update has an unexpected download size.");
                if (status == DownloadManager.STATUS_FAILED) throw new IOException("The update download failed. Check your connection and free storage, then try again.");
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    main.post(() -> { if (token == generation) { verifying = true; percent = 100; } });
                    File file = copyAndVerify(id, release, token);
                    main.post(() -> {
                        if (token != generation) { file.delete(); return; }
                        polling = false; verifying = false; downloadId = -1; downloads.remove(id); ready = file; percent = 100;
                        prefs.edit().remove("downloadId").putBoolean("ready", true).apply();
                    });
                } else main.post(() -> { if (token != generation) return; polling = false; percent = progress; main.postDelayed(ticker, 800); });
            } catch (Exception e) { main.post(() -> { if (token != generation) return; polling = false; verifying = false; downloadId = -1; downloads.remove(id); prefs.edit().remove("downloadId").remove("ready").apply(); error = message(e, "The update could not be verified. Try downloading it again."); }); }
        });
    }
    private File copyAndVerify(long id, GitHubUpdates.Release release, int token) throws Exception {
        if (!directory().isDirectory() && !directory().mkdirs()) throw new IOException("Could not save the update.");
        File part = new File(directory(), "download-" + token + ".part.apk"), destination = readyFile(release);
        try {
            Uri uri = downloads.getUriForDownloadedFile(id); if (uri == null) throw new IOException("The downloaded update is unavailable.");
            try (InputStream input = context.getContentResolver().openInputStream(uri); FileOutputStream output = new FileOutputStream(part)) {
                if (input == null) throw new IOException("The downloaded update is unavailable.");
                long size = 0; byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) { if (token != generation) throw new InterruptedIOException(); size += count; if (size > release.size) throw new IOException("The downloaded update has an unexpected size."); output.write(buffer, 0, count); }
                output.getFD().sync();
            }
            validate(part, release);
            if (token != generation) throw new InterruptedIOException();
            if (destination.exists() && !destination.delete()) throw new IOException("Could not replace the saved update.");
            if (!part.renameTo(destination)) throw new IOException("Could not save the verified update.");
            return destination;
        } finally { part.delete(); }
    }
    private void validate(File file, GitHubUpdates.Release release) throws Exception {
        if (file.length() != release.size || file.length() > GitHubUpdates.MAX_APK_BYTES) throw new IOException("The update has an unexpected file size.");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = new FileInputStream(file)) { byte[] buffer = new byte[65536]; int n; while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n); }
        if (!hex(digest.digest()).equalsIgnoreCase(release.sha256)) throw new IOException("The APK checksum does not match. Download the update again.");
        PackageManager manager = context.getPackageManager();
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo candidate = manager.getPackageArchiveInfo(file.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
        if (candidate == null || !context.getPackageName().equals(candidate.packageName)) throw new IOException("This APK is not a DevSurf update.");
        if (candidate.getLongVersionCode() <= installed.getLongVersionCode() || !release.version.equals(candidate.versionName)) throw new IOException("The APK is not a newer matching version of DevSurf.");
        if (candidate.applicationInfo == null || candidate.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT) throw new IOException("This update requires a newer Android version.");
        if (installed.signingInfo == null || candidate.signingInfo == null || !certificates(installed.signingInfo).equals(certificates(candidate.signingInfo))) throw new IOException("This update uses a different signing key and cannot update this installation.");
    }
    private static Set<String> certificates(SigningInfo info) throws Exception {
        Set<String> signatures = new HashSet<>(); for (android.content.pm.Signature signature : info.getApkContentsSigners()) signatures.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
        if (signatures.isEmpty()) throw new IOException("The APK has no signing certificate."); return signatures;
    }
    private static String hex(byte[] bytes) { StringBuilder value = new StringBuilder(); for (byte b : bytes) value.append(String.format(Locale.ROOT, "%02x", b & 255)); return value.toString(); }
    void install() {
        if (!ready() || busy()) return;
        installing = true; error = ""; prefs.edit().remove("lastInstallError").apply(); final File apk = ready; final GitHubUpdates.Release release = active; final int token = generation;
        worker.execute(() -> {
            int sessionId = -1;
            try {
                validate(apk, release);
                PackageInstaller installer = context.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(context.getPackageName()); params.setSize(apk.length());
                if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
                sessionId = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(sessionId)) {
                    try (InputStream input = new FileInputStream(apk); OutputStream output = session.openWrite("base.apk", 0, apk.length())) {
                        byte[] buffer = new byte[65536]; int n; while ((n = input.read(buffer)) != -1) { if (token != generation) throw new InterruptedIOException(); output.write(buffer, 0, n); } session.fsync(output);
                    }
                    String nonce = UUID.randomUUID().toString();
                    if (!UpdateRestartReceiver.prepare(context, release.version, sessionId, nonce)) throw new IOException("Could not prepare installation. Try again.");
                    Intent result = new Intent(context, UpdateInstallActivity.class).setAction(context.getPackageName() + ".INSTALL_RESULT").putExtra("install_nonce", nonce).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
                    Bundle options = null;
                    if (Build.VERSION.SDK_INT >= 35) { ActivityOptions launch = ActivityOptions.makeBasic(); launch.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED); options = launch.toBundle(); }
                    PendingIntent callback = PendingIntent.getActivity(context, sessionId, result, flags, options);
                    if (token != generation) throw new InterruptedIOException();
                    session.commit(callback.getIntentSender());
                }
            } catch (Exception e) {
                if (sessionId >= 0) try { context.getPackageManager().getPackageInstaller().abandonSession(sessionId); } catch (Exception ignored) {}
                UpdateRestartReceiver.cancel(context);
                main.post(() -> { installing = false; error = message(e, "Android could not start installation. Try again."); });
            }
        });
    }
    void installFailed(String description) { installing = false; error = description; prefs.edit().putString("lastInstallError", description).apply(); UpdateRestartReceiver.cancel(context); }
    void cancel() {
        generation++; main.removeCallbacks(ticker); checking = false; polling = false;
        if (downloadId >= 0) downloads.remove(downloadId); downloadId = -1; verifying = false; percent = -1;
        if (ready != null) ready.delete(); else if (active != null) readyFile(active).delete(); ready = null;
        prefs.edit().remove("downloadId").remove("ready").remove("release").remove("lastInstallError").apply(); error = "";
    }
    private static String message(Exception e, String fallback) { return e instanceof IOException && e.getMessage() != null ? e.getMessage() : fallback; }
}
