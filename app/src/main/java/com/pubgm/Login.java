package com.pubgm;

import android.content.Context;
import android.content.SharedPreferences;

import com.pubgm.security.ParallaxBhaiServerSeAaya;
import com.pubgm.security.ParallaxSoloHu;
import com.pubgm.utils.FLog;

import org.lsposed.lsparanoid.Obfuscate;

/** Compatibility facade. Security decisions are delegated to the OneCore-style client. */
@Obfuscate
public final class Login {
    public static volatile String EXP = "";
    private static volatile long lastExpiry = 0L;

    static {
        try {
            System.loadLibrary("client");
        } catch (UnsatisfiedLinkError error) {
            FLog.error(error.getMessage());
        }
    }

    private Login() {
    }

    /** Kept only because the existing native library exports this asset endpoint symbol. */
    public static native String FixCrash();

    public static String check(Context context, String userKey) {
        try {
            ParallaxBhaiServerSeAaya client = new ParallaxBhaiServerSeAaya(context);
            String result = client.activate(userKey);
            SharedPreferences cache = context.getSharedPreferences("auth_pref", Context.MODE_PRIVATE);
            if ("OK".equals(result)) {
                lastExpiry = client.expiresAtEpochSeconds();
                EXP = Long.toString(lastExpiry);
                cache.edit().putBoolean("verified", true).putLong("expiry", lastExpiry).apply();
            } else {
                lastExpiry = 0L;
                EXP = "";
                cache.edit().clear().apply();
            }
            return result;
        } catch (Exception error) {
            FLog.error("Secure login error: " + error.getMessage());
            return "Secure license verification is temporarily unavailable";
        }
    }

    public static long getExpiryTimestamp() {
        if (lastExpiry > 0L) return lastExpiry;
        try {
            long parsed = Long.parseLong(EXP == null ? "0" : EXP.trim());
            return parsed > 0L ? parsed : 0L;
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public static String getAndroidID(Context context) {
        try {
            return ParallaxSoloHu.deviceId();
        } catch (Exception error) {
            return "";
        }
    }

    /** Legacy call site compatibility; auth secrets are no longer pushed into native state. */
    public static void setAuthToken(String ignored) {
    }
}
