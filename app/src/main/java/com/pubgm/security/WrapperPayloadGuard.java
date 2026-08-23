package com.pubgm.security;

import org.lsposed.lsparanoid.Obfuscate;

/** Independent native wrapper/runtime policy layered on top of the signer verifier. */
@Obfuscate
final class WrapperPayloadGuard {
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

    private WrapperPayloadGuard() {
    }

    static boolean verify(String apkPath, String packageName) {
        if (!AVAILABLE || apkPath == null || apkPath.isEmpty()
                || packageName == null || packageName.isEmpty()) return false;
        try {
            return verifyArchiveAndRuntimeNative(apkPath, packageName);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static native boolean verifyArchiveAndRuntimeNative(String apkPath, String packageName);
}
