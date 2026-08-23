package com.pubgm.security;

import org.lsposed.lsparanoid.Obfuscate;

/** Native cross-check for the installed APK path and signer certificate identity. */
@Obfuscate
final class NativeSigningVerifier {
    private static final boolean AVAILABLE;

    static {
        boolean loaded;
        try {
            System.loadLibrary("client");
            loaded = true;
        } catch (Throwable ignored) {
            loaded = false;
        }
        AVAILABLE = loaded;
    }

    private NativeSigningVerifier() {
    }

    static boolean verify(
            byte[][] allowedDigests,
            byte[][] certificates,
            String actualPackage,
            String expectedPackage) {
        if (!AVAILABLE) return false;
        try {
            return verifySigningIdentity(
                    allowedDigests, certificates, actualPackage, expectedPackage);
        } catch (Throwable ignored) {
            return false;
        }
    }

    static boolean verifyInstalledApk(String apkPath, String actualPackage) {
        if (!AVAILABLE || apkPath == null || apkPath.isEmpty()
                || actualPackage == null || actualPackage.isEmpty()) {
            return false;
        }
        try {
            return verifyProcessBoundApkNative(apkPath, actualPackage);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Reads leaf signer certificates directly from the installed APK v2 signing block in C++. */
    static byte[][] readOnDiskSignerCertificates(String apkPath, String actualPackage) {
        if (!AVAILABLE || apkPath == null || apkPath.isEmpty()
                || actualPackage == null || actualPackage.isEmpty()) {
            return new byte[0][];
        }
        try {
            byte[][] certificates = readApkV2SignerCertificatesNative(apkPath, actualPackage);
            return certificates == null ? new byte[0][] : certificates;
        } catch (Throwable ignored) {
            return new byte[0][];
        }
    }

    private static native boolean verifySigningIdentity(
            byte[][] allowedDigests,
            byte[][] certificates,
            String actualPackage,
            String expectedPackage);

    private static native boolean verifyProcessBoundApkNative(
            String apkPath,
            String actualPackage);

    private static native byte[][] readApkV2SignerCertificatesNative(
            String apkPath,
            String actualPackage);
}
