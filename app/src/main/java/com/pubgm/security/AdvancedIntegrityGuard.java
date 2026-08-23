package com.pubgm.security;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import com.pubgm.BuildConfig;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Extra anti-wrapper layer around the existing OneCore signer verifier. */
@Obfuscate
public final class AdvancedIntegrityGuard {
    private static final long MIN_APK_BYTES = 4L * 1024L;

    public enum Status {
        VALID,
        PACKAGE_OR_SOURCE,
        DEBUGGABLE_RELEASE,
        NESTED_EXECUTABLE_PAYLOAD,
        NATIVE_RUNTIME_POLICY,
        BASE_SIGNER_CHAIN,
        PACKAGE_MANAGER_SIGNER_API,
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

    private AdvancedIntegrityGuard() {
    }

    public static Verification verifyDetailed(Context context) {
        if (context == null) return result(Status.ERROR, "NULL_CONTEXT");
        try {
            Context app = context.getApplicationContext();
            if (app == null) app = context;
            String packageName = app.getPackageName();
            if (!BuildConfig.APPLICATION_ID.equals(packageName)) {
                return result(Status.PACKAGE_OR_SOURCE, "PACKAGE_ID");
            }

            PackageManager pm = app.getPackageManager();
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo installed = pm.getPackageInfo(packageName, flags);
            ApplicationInfo runtime = app.getApplicationInfo();
            if (installed.applicationInfo == null || runtime == null) {
                return result(Status.PACKAGE_OR_SOURCE, "APP_INFO");
            }
            if (!packageName.equals(installed.packageName)) {
                return result(Status.PACKAGE_OR_SOURCE, "PACKAGE_INFO");
            }
            String[] uidPackages = pm.getPackagesForUid(installed.applicationInfo.uid);
            boolean ownsUid = false;
            if (uidPackages != null) {
                for (String candidate : uidPackages) {
                    if (packageName.equals(candidate)) ownsUid = true;
                }
            }
            if (!ownsUid) return result(Status.PACKAGE_OR_SOURCE, "UID_BINDING");

            if (!BuildConfig.DEBUG && (runtime.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
                return result(Status.DEBUGGABLE_RELEASE, "DEBUGGABLE");
            }

            File apk = canonicalInstalledApk(installed.applicationInfo, runtime);
            Status zipPolicy = inspectArchive(apk);
            if (zipPolicy != Status.VALID) return result(zipPolicy, "ARCHIVE_POLICY");

            if (!WrapperPayloadGuard.verify(apk.getAbsolutePath(), packageName)) {
                return result(Status.NATIVE_RUNTIME_POLICY, "NATIVE_PRECHECK");
            }

            ParallaxKaBhaiJanguHaii.Verification signer =
                    ParallaxKaBhaiJanguHaii.verifyDetailed(app);
            if (!signer.isValid()) {
                return result(Status.BASE_SIGNER_CHAIN, signer.status().name());
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                String[] digests = ParallaxKaBhaiJanguHaii.currentSigningCertificateSha256List(app);
                if (digests.length == 0) {
                    return result(Status.PACKAGE_MANAGER_SIGNER_API, "NO_SIGNER_DIGEST");
                }
                for (String digest : digests) {
                    if (!pm.hasSigningCertificate(
                            packageName,
                            decodeHex(digest),
                            PackageManager.CERT_INPUT_SHA256)) {
                        return result(Status.PACKAGE_MANAGER_SIGNER_API, "HAS_SIGNING_CERTIFICATE");
                    }
                }
            }

            if (inspectArchive(apk) != Status.VALID
                    || !WrapperPayloadGuard.verify(apk.getAbsolutePath(), packageName)) {
                return result(Status.NATIVE_RUNTIME_POLICY, "NATIVE_POSTCHECK");
            }
            return result(Status.VALID, "");
        } catch (Throwable ignored) {
            return result(Status.ERROR, "EXCEPTION");
        }
    }

    public static boolean verifyRuntimeBinding(Context context) {
        if (context == null) return false;
        try {
            Context app = context.getApplicationContext();
            if (app == null) app = context;
            if (!BuildConfig.APPLICATION_ID.equals(app.getPackageName())) return false;
            ApplicationInfo info = app.getApplicationInfo();
            if (info == null || info.sourceDir == null) return false;
            File apk = new File(info.sourceDir).getCanonicalFile();
            return WrapperPayloadGuard.verify(apk.getAbsolutePath(), app.getPackageName());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static File canonicalInstalledApk(ApplicationInfo installed, ApplicationInfo runtime)
            throws Exception {
        if (installed.sourceDir == null || runtime.sourceDir == null) throw new IllegalStateException();
        File a = new File(installed.sourceDir).getCanonicalFile();
        File b = new File(runtime.sourceDir).getCanonicalFile();
        if (!a.equals(b)) throw new IllegalStateException();
        if (installed.publicSourceDir != null
                && !a.equals(new File(installed.publicSourceDir).getCanonicalFile())) {
            throw new IllegalStateException();
        }
        if (runtime.publicSourceDir != null
                && !a.equals(new File(runtime.publicSourceDir).getCanonicalFile())) {
            throw new IllegalStateException();
        }
        return a;
    }

    private static Status inspectArchive(File apk) {
        if (!apk.isFile() || !apk.canRead() || apk.length() < MIN_APK_BYTES) {
            return Status.PACKAGE_OR_SOURCE;
        }
        int manifests = 0;
        int primaryDex = 0;
        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String raw = entry.getName();
                if (raw == null || raw.isEmpty() || raw.length() > 4096) {
                    return Status.PACKAGE_OR_SOURCE;
                }
                String name = raw.replace('\\', '/');
                String lower = name.toLowerCase(Locale.US);
                if (name.startsWith("/") || lower.contains("../")) {
                    return Status.PACKAGE_OR_SOURCE;
                }
                if ("AndroidManifest.xml".equals(name)) manifests++;
                if ("classes.dex".equals(name)) primaryDex++;
                if (forbiddenNestedPayload(lower)) return Status.NESTED_EXECUTABLE_PAYLOAD;
            }
            ZipEntry manifest = zip.getEntry("AndroidManifest.xml");
            ZipEntry dex = zip.getEntry("classes.dex");
            if (manifests != 1 || primaryDex != 1 || manifest == null || dex == null
                    || manifest.getSize() <= 0L || dex.getSize() <= 0L) {
                return Status.PACKAGE_OR_SOURCE;
            }
            return Status.VALID;
        } catch (Throwable ignored) {
            return Status.PACKAGE_OR_SOURCE;
        }
    }

    private static boolean forbiddenNestedPayload(String lower) {
        boolean executable = lower.endsWith(".apk") || lower.endsWith(".dex")
                || lower.endsWith(".jar") || lower.endsWith(".odex") || lower.endsWith(".vdex");
        boolean payloadArea = lower.startsWith("assets/") || lower.startsWith("res/raw/");
        boolean obviousWrapper = lower.equals("origin.apk") || lower.endsWith("/origin.apk")
                || lower.equals("original.apk") || lower.endsWith("/original.apk")
                || lower.contains("original_app")
                || lower.equals("backup.apk") || lower.endsWith("/backup.apk");
        return obviousWrapper || (payloadArea && executable);
    }

    private static byte[] decodeHex(String value) {
        String normalized = value == null ? "" : value.replace(":", "").trim();
        if (normalized.length() != 64) throw new IllegalArgumentException();
        byte[] out = new byte[32];
        for (int i = 0; i < 64; i += 2) {
            int hi = Character.digit(normalized.charAt(i), 16);
            int lo = Character.digit(normalized.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException();
            out[i / 2] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    private static Verification result(Status status, String detail) {
        return new Verification(status, detail);
    }
}
