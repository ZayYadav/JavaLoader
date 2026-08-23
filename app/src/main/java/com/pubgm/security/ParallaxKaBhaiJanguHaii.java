package com.pubgm.security;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import com.pubgm.BuildConfig;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/** Build-certificate and installed-APK integrity verifier. */
@Obfuscate
public final class ParallaxKaBhaiJanguHaii {
    private ParallaxKaBhaiJanguHaii() {
    }

    public static boolean verify(Context context) {
        if (context == null) return false;
        if (!BuildConfig.APPLICATION_ID.equals(context.getPackageName())) return false;

        try {
            ApplicationInfo appInfo = context.getApplicationInfo();
            if (!BuildConfig.DEBUG && (appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                return false;
            }
            if (appInfo.sourceDir == null || !new File(appInfo.sourceDir).isFile()) {
                return false;
            }
        } catch (Exception ignored) {
            return false;
        }

        String expected = normalizeList(BuildConfig.EXPECTED_SIGNATURE_SHA256);
        if (expected.isEmpty()) return BuildConfig.DEBUG;

        String[] current = currentSigningCertificateSha256List(context);
        if (current.length == 0) return false;
        String[] allowed = expected.split(",");
        for (String signer : current) {
            for (String candidate : allowed) {
                if (constantTimeEquals(signer, candidate)) return true;
            }
        }
        return false;
    }

    public static String currentSigningCertificateSha256(Context context) {
        String[] values = currentSigningCertificateSha256List(context);
        return values.length == 0 ? "" : values[0];
    }

    public static String[] currentSigningCertificateSha256List(Context context) {
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo info;
            Signature[] signatures;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (info.signingInfo == null) return new String[0];
                signatures = info.signingInfo.getApkContentsSigners();
            } else {
                info = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
                signatures = info.signatures;
            }
            if (signatures == null || signatures.length == 0) return new String[0];

            String[] output = new String[signatures.length];
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int i = 0; i < signatures.length; i++) {
                output[i] = toHex(digest.digest(signatures[i].toByteArray()));
            }
            return output;
        } catch (Exception ignored) {
            return new String[0];
        }
    }

    private static String normalizeList(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (String part : value.split(",")) {
            String normalized = part.replace(":", "").trim().toUpperCase(Locale.US);
            if (!normalized.matches("^[0-9A-F]{64}$")) continue;
            if (out.length() > 0) out.append(',');
            out.append(normalized);
        }
        return out.toString();
    }

    private static boolean constantTimeEquals(String first, String second) {
        if (first == null || second == null) return false;
        return MessageDigest.isEqual(
                first.getBytes(StandardCharsets.US_ASCII),
                second.getBytes(StandardCharsets.US_ASCII));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.US, "%02X", value));
        return out.toString();
    }
}
