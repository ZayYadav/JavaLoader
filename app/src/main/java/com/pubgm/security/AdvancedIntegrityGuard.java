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
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
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
        SPLIT_APK_UNEXPECTED,
        NESTED_EXECUTABLE_PAYLOAD,
        NATIVE_RUNTIME_POLICY,
        NATIVE_SIGNING_BLOCK,
        BASE_SIGNER_CHAIN,
        PACKAGE_MANAGER_SIGNER_API,
        SIGNING_HISTORY_MISMATCH,
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

            byte[][] allowedDigests = configuredSignerDigests();
            if (allowedDigests.length == 0) {
                return result(Status.BASE_SIGNER_CHAIN, "NO_SIGNER_ALLOWLIST");
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
            if (hasUnexpectedSplits(installed.applicationInfo) || hasUnexpectedSplits(runtime)) {
                return result(Status.SPLIT_APK_UNEXPECTED, "SPLIT_SOURCE_DIRS");
            }

            File apk = canonicalInstalledApk(installed.applicationInfo, runtime);
            Status zipPolicy = inspectArchive(apk);
            if (zipPolicy != Status.VALID) return result(zipPolicy, "ARCHIVE_POLICY");

            if (!WrapperPayloadGuard.verify(apk.getAbsolutePath(), packageName)) {
                return result(Status.NATIVE_RUNTIME_POLICY, "NATIVE_PRECHECK");
            }
            if (!NativeSigningVerifier.verifyOnDiskSigningBlock(
                    apk.getAbsolutePath(), packageName, allowedDigests)) {
                return result(Status.NATIVE_SIGNING_BLOCK, "V2_SIGNING_BLOCK_PRECHECK");
            }

            ParallaxKaBhaiJanguHaii.Verification signer =
                    ParallaxKaBhaiJanguHaii.verifyDetailed(app);
            if (!signer.isValid()) {
                return result(Status.BASE_SIGNER_CHAIN, signer.status().name());
            }

            if (!packageManagerSignersAllowed(pm, installed, packageName, allowedDigests)) {
                return result(Status.SIGNING_HISTORY_MISMATCH, "PM_SIGNER_HISTORY");
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
                        return result(Status.PACKAGE_MANAGER_SIGNER_API, "ACTIVE_SIGNER_API");
                    }
                }

                boolean expectedKnown = false;
                for (byte[] allowed : allowedDigests) {
                    if (allowed != null && allowed.length == 32
                            && pm.hasSigningCertificate(
                            packageName,
                            allowed,
                            PackageManager.CERT_INPUT_SHA256)) {
                        expectedKnown = true;
                    }
                }
                if (!expectedKnown) {
                    return result(Status.PACKAGE_MANAGER_SIGNER_API, "EXPECTED_SIGNER_API");
                }
            }

            if (inspectArchive(apk) != Status.VALID
                    || !WrapperPayloadGuard.verify(apk.getAbsolutePath(), packageName)) {
                return result(Status.NATIVE_RUNTIME_POLICY, "NATIVE_POSTCHECK");
            }
            if (!NativeSigningVerifier.verifyOnDiskSigningBlock(
                    apk.getAbsolutePath(), packageName, allowedDigests)) {
                return result(Status.NATIVE_SIGNING_BLOCK, "V2_SIGNING_BLOCK_POSTCHECK");
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
            String packageName = app.getPackageName();
            if (!BuildConfig.APPLICATION_ID.equals(packageName)) return false;
            ApplicationInfo info = app.getApplicationInfo();
            if (info == null || info.sourceDir == null || hasUnexpectedSplits(info)) return false;
            File apk = new File(info.sourceDir).getCanonicalFile();
            byte[][] allowedDigests = configuredSignerDigests();
            return allowedDigests.length > 0
                    && WrapperPayloadGuard.verify(apk.getAbsolutePath(), packageName)
                    && NativeSigningVerifier.verifyOnDiskSigningBlock(
                    apk.getAbsolutePath(), packageName, allowedDigests);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasUnexpectedSplits(ApplicationInfo info) {
        return info != null && info.splitSourceDirs != null && info.splitSourceDirs.length > 0;
    }

    private static File canonicalInstalledApk(ApplicationInfo installed, ApplicationInfo runtime)
            throws Exception {
        if (installed.sourceDir == null || runtime.sourceDir == null) throw new IllegalStateException();
        File a = new File(installed.sourceDir).getCanonicalFile();
        File b = new File(runtime.sourceDir).getCanonicalFile();
        if (!a.equals(b)) throw new IllegalStateException();
        if (!"base.apk".equals(a.getName())) throw new IllegalStateException();
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
                if (name.startsWith("/") || lower.contains("../") || lower.contains("..\\")) {
                    return Status.PACKAGE_OR_SOURCE;
                }
                if ("AndroidManifest.xml".equals(name)) manifests++;
                if ("classes.dex".equals(name)) primaryDex++;
                if (forbiddenNestedPayload(lower)
                        || hiddenExecutableMagic(zip, entry, lower)) {
                    return Status.NESTED_EXECUTABLE_PAYLOAD;
                }
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
                || lower.endsWith(".jar") || lower.endsWith(".odex")
                || lower.endsWith(".vdex") || lower.endsWith(".so")
                || lower.endsWith(".zip");
        boolean payloadArea = lower.startsWith("assets/") || lower.startsWith("res/raw/");
        boolean obviousWrapper = lower.equals("origin.apk") || lower.endsWith("/origin.apk")
                || lower.equals("original.apk") || lower.endsWith("/original.apk")
                || lower.equals("payload.apk") || lower.endsWith("/payload.apk")
                || lower.equals("shell.apk") || lower.endsWith("/shell.apk")
                || lower.equals("target.apk") || lower.endsWith("/target.apk")
                || lower.equals("backup.apk") || lower.endsWith("/backup.apk")
                || lower.contains("original_app") || lower.contains("origin_app");
        return obviousWrapper || (payloadArea && executable);
    }

    private static boolean hiddenExecutableMagic(ZipFile zip, ZipEntry entry, String lower) {
        if (entry == null || entry.isDirectory()) return false;
        boolean payloadArea = lower.startsWith("assets/") || lower.startsWith("res/raw/");
        if (!payloadArea) return false;
        byte[] header = new byte[8];
        int count = 0;
        try (InputStream input = zip.getInputStream(entry)) {
            while (count < header.length) {
                int read = input.read(header, count, header.length - count);
                if (read < 0) break;
                if (read == 0) continue;
                count += read;
            }
        } catch (Throwable ignored) {
            return true;
        }
        if (count >= 4) {
            boolean zipMagic = header[0] == 'P' && header[1] == 'K'
                    && ((header[2] == 3 && header[3] == 4)
                    || (header[2] == 5 && header[3] == 6)
                    || (header[2] == 7 && header[3] == 8));
            boolean dexMagic = header[0] == 'd' && header[1] == 'e'
                    && header[2] == 'x' && header[3] == '\n';
            boolean elfMagic = (header[0] & 0xff) == 0x7f
                    && header[1] == 'E' && header[2] == 'L' && header[3] == 'F';
            return zipMagic || dexMagic || elfMagic;
        }
        return false;
    }

    private static byte[][] configuredSignerDigests() {
        String configured = BuildConfig.EXPECTED_SIGNATURE_SHA256;
        if (configured == null || configured.trim().isEmpty()) return new byte[0][];
        String[] values = configured.split("[,;]");
        List<byte[]> result = new ArrayList<>();
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) result.add(decodeHex(value));
        }
        return result.toArray(new byte[0][]);
    }

    private static boolean packageManagerSignersAllowed(
            PackageManager pm,
            PackageInfo info,
            String packageName,
            byte[][] allowed) throws Exception {
        if (info == null || allowed == null || allowed.length == 0) return false;
        Signature[] active;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (info.signingInfo == null) return false;
            active = info.signingInfo.getApkContentsSigners();
        } else {
            @SuppressWarnings("deprecation")
            Signature[] legacy = info.signatures;
            active = legacy;
        }
        if (active == null || active.length == 0) return false;
        for (Signature signature : active) {
            if (!digestAllowed(sha256(signature.toByteArray()), allowed)) return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && info.signingInfo != null
                && !info.signingInfo.hasMultipleSigners()) {
            Signature[] history = info.signingInfo.getSigningCertificateHistory();
            if (history == null || history.length == 0) return false;
            for (Signature signature : history) {
                byte[] digest = sha256(signature.toByteArray());
                if (!digestAllowed(digest, allowed)) return false;
                if (!pm.hasSigningCertificate(
                        packageName, digest, PackageManager.CERT_INPUT_SHA256)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean digestAllowed(byte[] digest, byte[][] allowed) {
        if (digest == null || allowed == null) return false;
        for (byte[] candidate : allowed) {
            if (candidate != null && MessageDigest.isEqual(digest, candidate)) return true;
        }
        return false;
    }

    private static byte[] sha256(byte[] value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value);
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
