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
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Locale;

/**
 * Independent production signing root for OneCore MyThos.
 *
 * This class deliberately does not trust only BuildConfig. The production certificate digest is
 * pinned here and independently inside libclient.so. Runtime signer data must agree across
 * PackageManager, apksig, the native APK-v2 parser and the native hard-coded SHA-256 pin.
 */
@Obfuscate
public final class ProductionSignerGuard {
    public static final String PRODUCTION_CERT_SHA256 =
            "B343111BBFA30ED565F9B61A52CF5B34CB2EAF0485EB8B396AB373B2ECCA53D4";

    public enum Status {
        VALID,
        NATIVE_LIBRARY,
        BUILD_PIN_CHANGED,
        PACKAGE_IDENTITY,
        BASE_APK_PATH,
        PACKAGE_MANAGER_SIGNER,
        PACKAGE_MANAGER_PIN_API,
        NATIVE_BASE_SIGNER,
        APKSIG_SIGNER,
        NATIVE_APKSIG_CERT,
        NATIVE_ON_DISK_CERT,
        ARCHIVE_SIGNER,
        ARCHIVE_RUNTIME_BINDING,
        ERROR
    }

    public static final class Verification {
        private final Status status;
        private final String detail;

        private Verification(Status status, String detail) {
            this.status = status;
            this.detail = detail == null ? "" : detail;
        }

        public boolean isValid() { return status == Status.VALID; }
        public Status status() { return status; }
        public String detail() { return detail; }
    }

    private static final boolean NATIVE_AVAILABLE;

    static {
        boolean loaded;
        try {
            System.loadLibrary("client");
            loaded = true;
        } catch (Throwable ignored) {
            loaded = false;
        }
        NATIVE_AVAILABLE = loaded;
    }

    private ProductionSignerGuard() { }

    /** Lightweight check used from attachBaseContext/resume/watchdog. */
    public static Verification verifyEarly(Context context) {
        if (context == null) return fail(Status.ERROR, "NULL_CONTEXT");
        if (!NATIVE_AVAILABLE) return fail(Status.NATIVE_LIBRARY, "JNI_ONLOAD_OR_LOAD_FAILED");
        try {
            if (!productionBuildPinIntact()) {
                return fail(Status.BUILD_PIN_CHANGED, "JAVA_OR_BUILD_CONFIG_PIN");
            }

            Context app = context.getApplicationContext();
            if (app == null) app = context;
            String packageName = app.getPackageName();
            if (!"OneCore.Vip".equals(packageName)
                    || !BuildConfig.APPLICATION_ID.equals(packageName)) {
                return fail(Status.PACKAGE_IDENTITY, "PACKAGE");
            }

            PackageManager pm = app.getPackageManager();
            PackageInfo info = installedPackageInfo(pm, packageName);
            if (info == null || info.applicationInfo == null
                    || !packageName.equals(info.packageName)
                    || !uidOwnsPackage(pm, info.applicationInfo.uid, packageName)) {
                return fail(Status.PACKAGE_IDENTITY, "PACKAGE_MANAGER");
            }

            File baseApk = canonicalBaseApk(info.applicationInfo, app.getApplicationInfo());
            byte[][] pmCertificates = activeSignerCertificates(info);
            if (!exactlyProductionSigner(pmCertificates)
                    || !nativeVerifyCertificatesPinned(pmCertificates, packageName)) {
                return fail(Status.PACKAGE_MANAGER_SIGNER, "ACTIVE_SIGNER");
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    && !pm.hasSigningCertificate(
                    packageName,
                    decodeHex(PRODUCTION_CERT_SHA256),
                    PackageManager.CERT_INPUT_SHA256)) {
                return fail(Status.PACKAGE_MANAGER_PIN_API, "HAS_SIGNING_CERTIFICATE");
            }

            if (!nativeExpectedDigestMatches(PRODUCTION_CERT_SHA256)
                    || !nativeVerifyInstalledBasePinned(baseApk.getAbsolutePath(), packageName)) {
                return fail(Status.NATIVE_BASE_SIGNER, "PINNED_BASE_APK");
            }
            return ok();
        } catch (Throwable ignored) {
            return fail(Status.ERROR, "EARLY_EXCEPTION");
        }
    }

    /** Full cryptographic/archive check used at startup, Activity start and periodic watchdog pass. */
    public static Verification verifyFull(Context context) {
        Verification early = verifyEarly(context);
        if (!early.isValid()) return early;
        try {
            Context app = context.getApplicationContext();
            if (app == null) app = context;
            String packageName = app.getPackageName();
            PackageManager pm = app.getPackageManager();
            PackageInfo installed = installedPackageInfo(pm, packageName);
            File baseApk = canonicalBaseApk(installed.applicationInfo, app.getApplicationInfo());

            ApkVerifier.Result apkResult = new ApkVerifier.Builder(baseApk)
                    .setMinCheckedPlatformVersion(24)
                    .build()
                    .verify();
            if (!apkResult.isVerified() || apkResult.getSignerCertificates().size() != 1) {
                return fail(Status.APKSIG_SIGNER, "APKVERIFIER");
            }
            byte[][] apksigCertificates = x509Bytes(apkResult.getSignerCertificates());
            if (!exactlyProductionSigner(apksigCertificates)
                    || !nativeVerifyCertificatesPinned(apksigCertificates, packageName)) {
                return fail(Status.NATIVE_APKSIG_CERT, "APKVERIFIER_CERT");
            }

            byte[][] nativeDiskCertificates = NativeSigningVerifier.readOnDiskSignerCertificates(
                    baseApk.getAbsolutePath(), packageName);
            if (!exactlyProductionSigner(nativeDiskCertificates)
                    || !nativeVerifyCertificatesPinned(nativeDiskCertificates, packageName)
                    || !sameCertificateDigestSet(nativeDiskCertificates, apksigCertificates)) {
                return fail(Status.NATIVE_ON_DISK_CERT, "APK_V2_BLOCK");
            }

            PackageInfo archive = pm.getPackageArchiveInfo(
                    baseApk.getAbsolutePath(),
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                            ? PackageManager.GET_SIGNING_CERTIFICATES
                            : PackageManager.GET_SIGNATURES);
            if (archive == null || !packageName.equals(archive.packageName)) {
                return fail(Status.ARCHIVE_SIGNER, "ARCHIVE_PACKAGE");
            }
            byte[][] archiveCertificates = activeSignerCertificates(archive);
            if (!exactlyProductionSigner(archiveCertificates)
                    || !nativeVerifyCertificatesPinned(archiveCertificates, packageName)) {
                return fail(Status.ARCHIVE_SIGNER, "ARCHIVE_CERT");
            }

            if (!WrapperPayloadGuard.verify(baseApk.getAbsolutePath(), packageName)) {
                return fail(Status.ARCHIVE_RUNTIME_BINDING, "CRC_MAP_OR_RUNTIME_BINDING");
            }

            // Final native re-bind narrows a simple path-swap/TOCTOU window.
            if (!nativeVerifyInstalledBasePinned(baseApk.getAbsolutePath(), packageName)) {
                return fail(Status.NATIVE_BASE_SIGNER, "FINAL_BASE_REBIND");
            }
            return ok();
        } catch (Throwable ignored) {
            return fail(Status.ERROR, "FULL_EXCEPTION");
        }
    }

    public static boolean verify(Context context) {
        return verifyFull(context).isValid();
    }

    private static boolean productionBuildPinIntact() {
        String configured = normalizeDigest(BuildConfig.EXPECTED_SIGNATURE_SHA256);
        return PRODUCTION_CERT_SHA256.equals(configured)
                && nativeExpectedDigestMatches(configured);
    }

    private static PackageInfo installedPackageInfo(PackageManager pm, String packageName)
            throws PackageManager.NameNotFoundException {
        return pm.getPackageInfo(
                packageName,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                        ? PackageManager.GET_SIGNING_CERTIFICATES
                        : PackageManager.GET_SIGNATURES);
    }

    private static File canonicalBaseApk(ApplicationInfo installed, ApplicationInfo runtime)
            throws Exception {
        if (installed == null || runtime == null
                || installed.sourceDir == null || runtime.sourceDir == null) {
            throw new IllegalStateException("APK source unavailable");
        }
        File first = new File(installed.sourceDir).getCanonicalFile();
        File second = new File(runtime.sourceDir).getCanonicalFile();
        if (!first.equals(second) || !"base.apk".equals(first.getName())) {
            throw new IllegalStateException("Installed base.apk mismatch");
        }
        if (installed.publicSourceDir != null
                && !first.equals(new File(installed.publicSourceDir).getCanonicalFile())) {
            throw new IllegalStateException("Installed public source mismatch");
        }
        if (runtime.publicSourceDir != null
                && !first.equals(new File(runtime.publicSourceDir).getCanonicalFile())) {
            throw new IllegalStateException("Runtime public source mismatch");
        }
        String path = first.getAbsolutePath();
        if (!(path.startsWith("/data/app/") || path.startsWith("/mnt/expand/"))) {
            throw new IllegalStateException("Unexpected APK install root");
        }
        return first;
    }

    private static boolean uidOwnsPackage(PackageManager pm, int uid, String packageName) {
        String[] packages = pm.getPackagesForUid(uid);
        if (packages == null) return false;
        for (String candidate : packages) {
            if (packageName.equals(candidate)) return true;
        }
        return false;
    }

    private static byte[][] activeSignerCertificates(PackageInfo info) {
        if (info == null) return new byte[0][];
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (info.signingInfo == null) return new byte[0][];
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            @SuppressWarnings("deprecation")
            Signature[] legacy = info.signatures;
            signatures = legacy;
        }
        if (signatures == null) return new byte[0][];
        byte[][] result = new byte[signatures.length][];
        for (int i = 0; i < signatures.length; i++) result[i] = signatures[i].toByteArray();
        return result;
    }

    private static byte[][] x509Bytes(List<X509Certificate> certificates) throws Exception {
        byte[][] result = new byte[certificates.size()][];
        for (int i = 0; i < certificates.size(); i++) result[i] = certificates.get(i).getEncoded();
        return result;
    }

    private static boolean exactlyProductionSigner(byte[][] certificates) throws Exception {
        return certificates != null
                && certificates.length == 1
                && certificates[0] != null
                && MessageDigest.isEqual(
                MessageDigest.getInstance("SHA-256").digest(certificates[0]),
                decodeHex(PRODUCTION_CERT_SHA256));
    }

    private static boolean sameCertificateDigestSet(byte[][] first, byte[][] second) throws Exception {
        if (first == null || second == null || first.length != 1 || second.length != 1) return false;
        byte[] a = MessageDigest.getInstance("SHA-256").digest(first[0]);
        byte[] b = MessageDigest.getInstance("SHA-256").digest(second[0]);
        return MessageDigest.isEqual(a, b);
    }

    private static String normalizeDigest(String value) {
        return value == null ? "" : value.replace(":", "").trim().toUpperCase(Locale.US);
    }

    private static byte[] decodeHex(String value) {
        String normalized = normalizeDigest(value);
        if (normalized.length() != 64) throw new IllegalArgumentException("Invalid SHA-256 digest");
        byte[] result = new byte[32];
        for (int i = 0; i < normalized.length(); i += 2) {
            int high = Character.digit(normalized.charAt(i), 16);
            int low = Character.digit(normalized.charAt(i + 1), 16);
            if (high < 0 || low < 0) throw new IllegalArgumentException("Invalid SHA-256 digest");
            result[i / 2] = (byte) ((high << 4) | low);
        }
        return result;
    }

    private static Verification ok() { return new Verification(Status.VALID, ""); }
    private static Verification fail(Status status, String detail) {
        return new Verification(status, detail);
    }

    private static native boolean nativeExpectedDigestMatches(String expectedSha256);
    private static native boolean nativeVerifyInstalledBasePinned(String apkPath, String packageName);
    private static native boolean nativeVerifyCertificatesPinned(byte[][] certificates, String packageName);
}
