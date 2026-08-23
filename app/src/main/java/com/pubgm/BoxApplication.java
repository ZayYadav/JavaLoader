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
import com.pubgm.security.ParallaxBhaiServerSeAaya;
import com.pubgm.security.ParallaxKaBhaiJanguHaii;
import com.pubgm.utils.FLog;

import org.lsposed.lsparanoid.Obfuscate;

import java.util.concurrent.atomic.AtomicBoolean;

/** Application shell with a central fail-closed license/session gate. */
@Obfuscate
public class BoxApplication extends Application {
    public static BoxApplication gApp;
    private final AtomicBoolean redirectingToLogin = new AtomicBoolean(false);

    public static BoxApplication get() {
        return gApp;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
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
            @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) { }

            @Override
            public void onActivityStarted(Activity activity) {
                if (activity instanceof SplashActivity || activity instanceof LoginActivity) {
                    redirectingToLogin.set(false);
                    return;
                }

                boolean allowed = false;
                try {
                    allowed = ParallaxKaBhaiJanguHaii.verify(activity)
                            && new ParallaxBhaiServerSeAaya(activity).hasActiveLicense();
                } catch (Exception error) {
                    FLog.error("License gate unavailable: " + error.getClass().getSimpleName());
                }

                if (!allowed) {
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

            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });

        DynamicColors.applyToActivitiesIfAvailable(this);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }

    public void toast(CharSequence msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
