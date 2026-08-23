package com.pubgm.security;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import org.lsposed.lsparanoid.Obfuscate;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicBoolean;

/** Routes confirmed signer/APK incidents into an isolated OneCore security-response task. */
@Obfuscate
public final class SecurityIncidentDispatcher {
    public enum Reason {
        SIGNATURE,
        INTEGRITY
    }

    static final String EXTRA_REASON = "onecore_reason";
    static final String EXTRA_DETAIL = "onecore_detail";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean ACTIVE = new AtomicBoolean(false);
    private static volatile WeakReference<Activity> foreground = new WeakReference<>(null);
    private static volatile Reason pendingReason;
    private static volatile String pendingDetail;

    private SecurityIncidentDispatcher() {
    }

    public static void attach(Activity activity) {
        if (!usable(activity) || activity instanceof SecurityIncidentActivity) return;
        foreground = new WeakReference<>(activity);
        Reason reason = pendingReason;
        if (reason != null && !ACTIVE.get()) {
            String detail = pendingDetail;
            MAIN.post(() -> launch(activity, reason, detail));
        }
    }

    public static void detach(Activity activity) {
        WeakReference<Activity> reference = foreground;
        if (reference != null && reference.get() == activity) {
            foreground = new WeakReference<>(null);
        }
    }

    public static void raise(Reason reason, String detail) {
        Reason safeReason = reason == null ? Reason.INTEGRITY : reason;
        pendingReason = safeReason;
        pendingDetail = detail == null ? "" : detail;
        Activity target = currentActivity();
        if (usable(target) && !(target instanceof SecurityIncidentActivity)) {
            String safeDetail = pendingDetail;
            MAIN.post(() -> launch(target, safeReason, safeDetail));
        }
    }

    public static void raise(Activity preferredActivity, Reason reason, String detail) {
        Reason safeReason = reason == null ? Reason.INTEGRITY : reason;
        pendingReason = safeReason;
        pendingDetail = detail == null ? "" : detail;
        Activity target = usable(preferredActivity) ? preferredActivity : currentActivity();
        if (usable(target) && !(target instanceof SecurityIncidentActivity)) {
            String safeDetail = pendingDetail;
            MAIN.post(() -> launch(target, safeReason, safeDetail));
        }
    }

    /** Used by Application startup before any Activity is guaranteed to exist. */
    public static void raiseFromContext(Context context, Reason reason, String detail) {
        Reason safeReason = reason == null ? Reason.INTEGRITY : reason;
        pendingReason = safeReason;
        pendingDetail = detail == null ? "" : detail;
        Activity target = currentActivity();
        if (usable(target)) {
            String safeDetail = pendingDetail;
            MAIN.post(() -> launch(target, safeReason, safeDetail));
            return;
        }
        Context app = context == null ? null : context.getApplicationContext();
        if (app == null) app = context;
        final Context launchContext = app;
        final String safeDetail = pendingDetail;
        if (launchContext != null) {
            MAIN.post(() -> launch(launchContext, safeReason, safeDetail));
        }
    }

    private static Activity currentActivity() {
        WeakReference<Activity> reference = foreground;
        return reference == null ? null : reference.get();
    }

    private static void launch(Context context, Reason reason, String detail) {
        if (context == null || !ACTIVE.compareAndSet(false, true)) return;
        pendingReason = null;
        pendingDetail = null;

        Intent intent = new Intent(context, SecurityIncidentActivity.class);
        intent.putExtra(EXTRA_REASON, reason == null ? Reason.INTEGRITY.name() : reason.name());
        intent.putExtra(EXTRA_DETAIL, detail == null ? "" : detail);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            context.startActivity(intent);
        } catch (Throwable ignored) {
            SecurityIncidentActivity.emergencyTerminate(context);
        }
    }

    static Reason parseReason(String value) {
        try {
            return Reason.valueOf(value);
        } catch (Throwable ignored) {
            return Reason.INTEGRITY;
        }
    }

    private static boolean usable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }
}
