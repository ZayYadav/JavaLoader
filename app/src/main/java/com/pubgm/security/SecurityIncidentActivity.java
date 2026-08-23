package com.pubgm.security;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import com.pubgm.ui.SecurityCinematicDialog;

import org.lsposed.lsparanoid.Obfuscate;

/** Separate task so a compromised/finishing caller cannot dismiss the integrity response early. */
@Obfuscate
public final class SecurityIncidentActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String reasonValue = getIntent() == null ? null
                : getIntent().getStringExtra(SecurityIncidentDispatcher.EXTRA_REASON);
        String detail = getIntent() == null ? ""
                : getIntent().getStringExtra(SecurityIncidentDispatcher.EXTRA_DETAIL);
        SecurityIncidentDispatcher.Reason reason = SecurityIncidentDispatcher.parseReason(reasonValue);

        main.post(() -> SecurityCinematicDialog.show(
                this,
                reason,
                detail == null ? "" : detail,
                () -> emergencyTerminate(this)));
    }

    @Override
    public void onBackPressed() {
        // Non-cancelable security response.
    }

    public static void emergencyTerminate(Context context) {
        if (context instanceof Activity) {
            try {
                ((Activity) context).finishAndRemoveTask();
            } catch (Throwable ignored) {
            }
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try {
                Process.killProcess(Process.myPid());
            } finally {
                System.exit(0);
            }
        }, 120L);
    }
}
