package com.pubgm;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.color.DynamicColors;
import com.pubgm.activity.LoginActivity;
import com.pubgm.activity.SplashActivity;
import com.pubgm.security.AdvancedIntegrityGuard;
import com.pubgm.security.IntegrityWatchdog;
import com.pubgm.security.ParallaxBhaiServerSeAaya;
import com.pubgm.security.ProductionSignerGuard;
import com.pubgm.security.SecurityIncidentActivity;
import com.pubgm.security.SecurityIncidentDispatcher;
import com.pubgm.utils.FLog;

import org.lsposed.lsparanoid.Obfuscate;

import java.util.concurrent.atomic.AtomicBoolean;

/** Application shell with fail-closed signer/integrity and license/session gates. */
@Obfuscate
public class BoxApplication extends Application {
    public static BoxApplication gApp;
    private static volatile String earlyProductionSignerFailure;
    private final AtomicBoolean redirectingToLogin = new AtomicBoolean(false);

    public static BoxApplication get() {
        return gApp;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        ProductionSignerGuard.Verification early = ProductionSignerGuard.verifyEarly(base);
        if (!early.isValid()) {
            earlyProductionSignerFailure =
                    "PRODUCTION_SIGNER:" + early.status().name() + ":" + early.detail();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        gApp = this;

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("PARALLAX_CRASH", "Uncaught exception in " + thread.getName(), throwable);
            FLog.error("CRASH [" + thread.getName() + "]: " + throwable.getClass().getSimpleName());
        });

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                if (!(activity instanceof SecurityIncidentActivity)) {
                    SecurityIncidentDispatcher.attach(activity);
                }
            }

            @Override
            public void onActivityStarted(Activity activity) {
                if (activity instanceof SecurityIncidentActivity) return;
                SecurityIncidentDispatcher.attach(activity);

                ProductionSignerGuard.Verification production = ProductionSignerGuard.verifyFull(activity);
                if (!production.isValid()) {
                    SecurityIncidentDispatcher.raise(
                            activity,
                            SecurityIncidentDispatcher.Reason.SIGNATURE,
                            "PRODUCTION_SIGNER:" + production.status().name() + ":" + production.detail());
                    return;
                }

                AdvancedIntegrityGuard.Verification integrity =
                        AdvancedIntegrityGuard.verifyDetailed(activity);
                if (!integrity.isValid()) {
                    SecurityIncidentDispatcher.raise(
                            activity,
                            SecurityIncidentDispatcher.Reason.SIGNATURE,
                            integrity.status().name() + ":" + integrity.detail());
                    return;
                }

                if (activity instanceof SplashActivity || activity instanceof LoginActivity) {
                    redirectingToLogin.set(false);
                    return;
                }

                boolean licensed = false;
                try {
                    licensed = new ParallaxBhaiServerSeAaya(activity).hasActiveLicense();
                } catch (Exception error) {
                    FLog.error("License gate unavailable: " + error.getClass().getSimpleName());
                }

                if (!licensed) {
                    if (redirectingToLogin.compareAndSet(false, true)) {
                        Intent intent = new Intent(activity, LoginActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        activity.startActivity(intent);
                    }
                    activity.finish();
                } else {
                    redirectingToLogin.set(false);
                }
            }

            @Override
            public void onActivityResumed(Activity activity) {
                if (activity instanceof SecurityIncidentActivity) return;
                SecurityIncidentDispatcher.attach(activity);

                ProductionSignerGuard.Verification production = ProductionSignerGuard.verifyEarly(activity);
                if (!production.isValid()) {
                    SecurityIncidentDispatcher.raise(
                            activity,
                            SecurityIncidentDispatcher.Reason.SIGNATURE,
                            "PRODUCTION_SIGNER_RUNTIME:" + production.status().name()
                                    + ":" + production.detail());
                    return;
                }

                if (!AdvancedIntegrityGuard.verifyRuntimeBinding(activity)) {
                    SecurityIncidentDispatcher.raise(
                            activity,
                            SecurityIncidentDispatcher.Reason.SIGNATURE,
                            "RUNTIME_APK_BINDING");
                }
            }

            @Override public void onActivityPaused(Activity activity) { }

            @Override
            public void onActivityStopped(Activity activity) {
                SecurityIncidentDispatcher.detach(activity);
            }

            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }

            @Override
            public void onActivityDestroyed(Activity activity) {
                SecurityIncidentDispatcher.detach(activity);
            }
        });

        String earlyFailure = earlyProductionSignerFailure;
        if (earlyFailure != null && !earlyFailure.isEmpty()) {
            SecurityIncidentDispatcher.raiseFromContext(
                    this,
                    SecurityIncidentDispatcher.Reason.SIGNATURE,
                    earlyFailure);
        } else {
            ProductionSignerGuard.Verification productionStartup = ProductionSignerGuard.verifyFull(this);
            if (!productionStartup.isValid()) {
                SecurityIncidentDispatcher.raiseFromContext(
                        this,
                        SecurityIncidentDispatcher.Reason.SIGNATURE,
                        "PRODUCTION_SIGNER_STARTUP:" + productionStartup.status().name()
                                + ":" + productionStartup.detail());
            } else {
                AdvancedIntegrityGuard.Verification startup = AdvancedIntegrityGuard.verifyDetailed(this);
                if (!startup.isValid()) {
                    SecurityIncidentDispatcher.raiseFromContext(
                            this,
                            SecurityIncidentDispatcher.Reason.SIGNATURE,
                            startup.status().name() + ":" + startup.detail());
                }
            }
        }
        IntegrityWatchdog.start(this);

        DynamicColors.applyToActivitiesIfAvailable(this);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }

    public void toast(CharSequence msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
