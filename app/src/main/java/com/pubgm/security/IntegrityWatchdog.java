package com.pubgm.security;

import android.content.Context;

import org.lsposed.lsparanoid.Obfuscate;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** Periodic runtime binding check plus periodic complete signer-chain revalidation. */
@Obfuscate
public final class IntegrityWatchdog {
    private static final AtomicBoolean STARTED = new AtomicBoolean(false);
    private static final AtomicBoolean INCIDENT = new AtomicBoolean(false);
    private static final AtomicInteger TICKS = new AtomicInteger(0);
    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "OneCore-IntegrityWatch");
                thread.setDaemon(true);
                return thread;
            });

    private IntegrityWatchdog() {
    }

    public static void start(Context context) {
        if (context == null || !STARTED.compareAndSet(false, true)) return;
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        final Context target = app;
        EXECUTOR.scheduleWithFixedDelay(() -> runCheck(target), 6L, 6L, TimeUnit.SECONDS);
    }

    private static void runCheck(Context context) {
        if (INCIDENT.get()) return;
        try {
            int tick = TICKS.incrementAndGet();

            ProductionSignerGuard.Verification pinned = ProductionSignerGuard.verifyEarly(context);
            if (!pinned.isValid()) {
                trigger(context, "PRODUCTION_SIGNER_RUNTIME:" + pinned.status().name()
                        + ":" + pinned.detail());
                return;
            }

            if (!AdvancedIntegrityGuard.verifyRuntimeBinding(context)) {
                trigger(context, "RUNTIME_APK_BINDING");
                return;
            }

            if ((tick & 7) == 0) {
                ProductionSignerGuard.Verification productionFull =
                        ProductionSignerGuard.verifyFull(context);
                if (!productionFull.isValid()) {
                    trigger(context, "PRODUCTION_SIGNER_FULL:" + productionFull.status().name()
                            + ":" + productionFull.detail());
                    return;
                }

                AdvancedIntegrityGuard.Verification full = AdvancedIntegrityGuard.verifyDetailed(context);
                if (!full.isValid()) {
                    trigger(context, full.status().name() + ":" + full.detail());
                }
            }
        } catch (Throwable ignored) {
            trigger(context, "WATCHDOG_VERIFICATION_ERROR");
        }
    }

    private static void trigger(Context context, String detail) {
        if (!INCIDENT.compareAndSet(false, true)) return;
        SecurityIncidentDispatcher.raiseFromContext(
                context,
                SecurityIncidentDispatcher.Reason.SIGNATURE,
                detail == null ? "INTEGRITY" : detail);
    }
}
