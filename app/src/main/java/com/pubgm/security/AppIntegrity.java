package com.pubgm.security;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import com.pubgm.BuildConfig;

import java.security.MessageDigest;
import java.util.Locale;

/** Verifies that the installed APK is signed by a certificate selected at build time. */
public final class AppIntegrity {
    private AppIntegrity() {}

    public static boolean verify(Context context) {
        String expected = normalize(BuildConfig.EXPECTED_SIGNATURE_SHA256);
        if (expected.isEmpty()) {
            // Debug/local builds may omit binding. Release builds are prevented from doing so by Gradle.
            return BuildConfig.DEBUG;
        }
        String current = currentSigningCertificateSha256(context);
        if (current.isEmpty()) {
            return false;
        }
        for (String allowed : expected.split(",")) {
            if (constantTimeEquals(current, allowed)) {
                return true;
            }
        }
        return false;
    }

    public static String currentSigningCertificateSha256(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info;
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) return "";
                signatures = info.signingInfo.hasMultipleSigners()
                        ? info.signingInfo.getApkContentsSigners()
                        : info.signingInfo.getSigningCertificateHistory();
            } else {
                info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }
            if (signatures == null || signatures.length == 0) return "";
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return toHex(md.digest(signatures[0].toByteArray()));
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String[] parts = value.split(",");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            String normalized = part.replace(":", "").trim().toUpperCase(Locale.US);
            if (normalized.isEmpty()) continue;
            if (result.length() > 0) result.append(',');
            result.append(normalized);
        }
        return result.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        try {
            return MessageDigest.isEqual(a.getBytes("US-ASCII"), b.getBytes("US-ASCII"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            out.append(String.format(Locale.US, "%02X", value));
        }
        return out.toString();
    }
}