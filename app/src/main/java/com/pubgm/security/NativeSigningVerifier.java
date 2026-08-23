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

    private static native boolean verifySigningIdentity(
            byte[][] allowedDigests,
            byte[][] certificates,
            String actualPackage,
            String expectedPackage);

    private static native boolean verifyProcessBoundApkNative(
            String apkPath,
            String actualPackage);
}
