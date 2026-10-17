package com.devsurf.mobile;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Private target of the explicit installation-session PendingIntent. */
public final class UpdateInstallActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView message = new TextView(this); message.setText("Confirm the DevSurf update in Android."); message.setTextColor(0xfff5f5f5); message.setTextSize(16); message.setGravity(Gravity.CENTER); message.setBackgroundColor(0xff080808); setContentView(message);
        if (state == null) handle(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handle(intent); }
    private void handle(Intent intent) {
        if (!UpdateRestartReceiver.matches(this, intent)) { finish(); return; }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm == null) { fail("Android did not provide an installation prompt. Try again."); return; }
            try { startActivity(confirm); } catch (Exception e) { fail("Android could not open the installation prompt. Try again."); }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            UpdateRestartReceiver.reopen(this); finish();
        } else fail(status == PackageInstaller.STATUS_FAILURE_ABORTED ? "Installation was cancelled. The download is ready if you want to try again." : "Android could not install the update. Check free storage and try again.");
    }
    private void fail(String message) {
        AppUpdater.get(this).installFailed(message);
        startActivity(new Intent(this, UpdatesActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish();
    }
}
