package com.pubgm.security;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import com.android.apksig.ApkVerifier;
import com.pubgm.BuildConfig;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * OneCore-style fail-closed APK/package/signing identity verification.
 * The installed base APK is cryptographically checked with apksig and cross-checked against
 * PackageManager plus the build-pinned certificate SHA-256.
 */
@Obfuscate
public final class ParallaxKaBhaiJanguHaii {
    private static final long MIN_APK_BYTES = 4L * 1024L;

    private ParallaxKaBhaiJanguHaii() {
    }

    public static boolean verify(Context context) {
        if (context == null || !BuildConfig.APPLICATION_ID.equals(context.getPackageName())) return false;
        try {
            byte[][] allowed = configuredSignerDigests();
            if (allowed.length == 0) return BuildConfig.DEBUG;

            Context app = context.getApplicationContext() == null
                    ? context : context.getApplicationContext();
            PackageManager pm = app.getPackageManager();
            PackageInfo installed = getInstalledPackageInfo(pm, app.getPackageName());
            if (installed.applicationInfo == null
                    || !app.getPackageName().equals(installed.packageName)
                    || !uidOwnsPackage(pm, installed.applicationInfo.uid, app.getPackageName())) {
                return false;
            }

            ApplicationInfo appInfo = app.getApplicationInfo();
            if (!BuildConfig.DEBUG && (appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                return false;
            }
            File apk = canonicalApk(installed.applicationInfo, appInfo);
            if (!isStructurallyValidApk(apk)) return false;

            byte[][] archiveCertificates = verifyApkAndGetCertificates(apk);
            if (archiveCertificates.length == 0) return false;
            byte[][] archiveDigests = sha256Digests(archiveCertificates);
            if (!matchesAllowedSignerDigests(allowed, archiveDigests)) return false;

            Signature[] packageSigners = getActiveSigners(installed);
            if (packageSigners.length == 0) return false;
            byte[][] packageDigests = sha256Digests(certificateBytes(packageSigners));
            return matchesAllowedSignerDigests(allowed, packageDigests)
                    && sameSignerSets(packageDigests, archiveDigests);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String currentSigningCertificateSha256(Context context) {
        if (!verify(context)) return "";
        try {
            File apk = new File(context.getApplicationInfo().sourceDir).getCanonicalFile();
            byte[][] certificates = verifyApkAndGetCertificates(apk);
            if (certificates.length == 0) return "";
            return toHex(sha256(certificates[0]));
        } catch (Exception ignored) {
            return "";
        }
    }

    public static String[] currentSigningCertificateSha256List(Context context) {
        if (!verify(context)) return new String[0];
        try {
            File apk = new File(context.getApplicationInfo().sourceDir).getCanonicalFile();
            byte[][] certificates = verifyApkAndGetCertificates(apk);
            byte[][] digests = sha256Digests(certificates);
            String[] result = new String[digests.length];
            for (int i = 0; i < digests.length; i++) result[i] = toHex(digests[i]);
            return result;
        } catch (Exception ignored) {
            return new String[0];
        }
    }

    private static byte[][] configuredSignerDigests() {
        String configured = normalizeList(BuildConfig.EXPECTED_SIGNATURE_SHA256);
        if (configured.isEmpty()) return new byte[0][];
        String[] values = configured.split(",");
        byte[][] out = new byte[values.length][];
        for (int i = 0; i < values.length; i++) out[i] = decodeHex(values[i]);
        return out;
    }

    private static PackageInfo getInstalledPackageInfo(PackageManager pm, String packageName)
            throws PackageManager.NameNotFoundException {
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        return pm.getPackageInfo(packageName, flags);
    }

    private static Signature[] getActiveSigners(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (info.signingInfo == null) return new Signature[0];
            Signature[] signers = info.signingInfo.getApkContentsSigners();
            return signers == null ? new Signature[0] : signers;
        }
        @SuppressWarnings("deprecation")
        Signature[] signers = info.signatures;
        return signers == null ? new Signature[0] : signers;
    }

    private static boolean uidOwnsPackage(PackageManager pm, int uid, String packageName) {
        String[] packages = pm.getPackagesForUid(uid);
        if (packages == null) return false;
        for (String candidate : packages) if (packageName.equals(candidate)) return true;
        return false;
    }

    private static File canonicalApk(ApplicationInfo installed, ApplicationInfo runtime) throws Exception {
        if (installed.sourceDir == null || runtime.sourceDir == null) {
            throw new IllegalStateException("APK source unavailable");
        }
        File left = new File(installed.sourceDir).getCanonicalFile();
        File right = new File(runtime.sourceDir).getCanonicalFile();
        if (!left.equals(right)) throw new IllegalStateException("APK source mismatch");
        return left;
    }

    private static boolean isStructurallyValidApk(File apk) {
        if (!apk.isFile() || !apk.canRead() || apk.length() < MIN_APK_BYTES) return false;
        try (ZipFile zip = new ZipFile(apk)) {
            ZipEntry manifest = zip.getEntry("AndroidManifest.xml");
            ZipEntry dex = zip.getEntry("classes.dex");
            return manifest != null && dex != null;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static byte[][] verifyApkAndGetCertificates(File apk) throws Exception {
        ApkVerifier.Result result = new ApkVerifier.Builder(apk)
                .setMinCheckedPlatformVersion(24)
                .build()
                .verify();
        if (!result.isVerified() || result.getSignerCertificates().isEmpty()) return new byte[0][];
        List<X509Certificate> certificates = result.getSignerCertificates();
        byte[][] encoded = new byte[certificates.size()][];
        for (int i = 0; i < certificates.size(); i++) encoded[i] = certificates.get(i).getEncoded();
        return encoded;
    }

    private static byte[][] certificateBytes(Signature[] signatures) {
        byte[][] values = new byte[signatures.length][];
        for (int i = 0; i < signatures.length; i++) values[i] = signatures[i].toByteArray();
        return values;
    }

    private static byte[][] sha256Digests(byte[][] values) throws Exception {
        byte[][] out = new byte[values.length][];
        for (int i = 0; i < values.length; i++) out[i] = sha256(values[i]);
        return out;
    }

    private static byte[] sha256(byte[] value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value);
    }

    private static boolean matchesAllowedSignerDigests(byte[][] allowed, byte[][] actual) {
        if (allowed.length == 0 || actual.length == 0) return false;
        for (byte[] actualDigest : actual) {
            boolean found = false;
            for (byte[] allowedDigest : allowed) {
                if (MessageDigest.isEqual(allowedDigest, actualDigest)) found = true;
            }
            if (!found) return false;
        }
        return true;
    }

    private static boolean sameSignerSets(byte[][] first, byte[][] second) {
        if (first.length == 0 || first.length != second.length) return false;
        List<byte[]> left = sortedDigests(first);
        List<byte[]> right = sortedDigests(second);
        for (int i = 0; i < left.size(); i++) {
            if (!MessageDigest.isEqual(left.get(i), right.get(i))) return false;
        }
        return true;
    }

    private static List<byte[]> sortedDigests(byte[][] values) {
        List<byte[]> out = new ArrayList<>();
        for (byte[] value : values) out.add(Arrays.copyOf(value, value.length));
        Collections.sort(out, new Comparator<byte[]>() {
            @Override
            public int compare(byte[] first, byte[] second) {
                int length = Math.min(first.length, second.length);
                for (int i = 0; i < length; i++) {
                    int comparison = Integer.compare(first[i] & 0xff, second[i] & 0xff);
                    if (comparison != 0) return comparison;
                }
                return Integer.compare(first.length, second.length);
            }
        });
        return out;
    }

    private static String normalizeList(String value) {
        if (value == null) return "";
        StringBuilder out = new StringBuilder();
        for (String part : value.split("[,;]")) {
            String normalized = part.replace(":", "").trim().toUpperCase(Locale.US);
            if (!normalized.matches("^[0-9A-F]{64}$")) continue;
            if (out.length() > 0) out.append(',');
            out.append(normalized);
        }
        return out.toString();
    }

    private static byte[] decodeHex(String value) {
        byte[] result = new byte[32];
        for (int i = 0; i < 64; i += 2) {
            int high = Character.digit(value.charAt(i), 16);
            int low = Character.digit(value.charAt(i + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid SHA-256");
            result[i / 2] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(Locale.US, "%02X", value & 0xff));
        return out.toString();
    }
}
