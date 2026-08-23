package com.pubgm.security;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Cross-implementation integrity map for the exact installed base.apk.
 *
 * Java reads the ZIP through ZipFile while native code parses the central directory directly.
 * Both sides must agree on every classes*.dex entry, every packaged asset, the manifest,
 * resources table and libclient.so. Periodically both implementations also recalculate the
 * uncompressed entry CRC32 from bytes instead of trusting only central-directory metadata.
 */
@Obfuscate
final class ApkArchiveIntegrity {
    private static final AtomicInteger CHECK_COUNTER = new AtomicInteger(0);
    private static final int FULL_CRC_INTERVAL = 8;
    private static final int MAX_CRITICAL_ENTRIES = 4096;
    private static final long MAX_ENTRY_BYTES = 256L * 1024L * 1024L;
    private static final int BUFFER_BYTES = 32 * 1024;

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

    private ApkArchiveIntegrity() {
    }

    static boolean verify(String apkPath, String packageName) {
        if (!AVAILABLE || apkPath == null || apkPath.isEmpty()
                || packageName == null || packageName.isEmpty()) {
            return false;
        }
        try {
            File apk = new File(apkPath).getCanonicalFile();
            if (!apk.isFile() || !apk.canRead() || !"base.apk".equals(apk.getName())) {
                return false;
            }

            int count = CHECK_COUNTER.incrementAndGet();
            boolean verifyContentCrc = count == 1 || Math.floorMod(count, FULL_CRC_INTERVAL) == 0;

            String[] javaMap = javaEntryMap(apk, verifyContentCrc);
            String[] nativeMap = readCriticalArchiveMapNative(
                    apk.getAbsolutePath(), packageName, verifyContentCrc);
            if (javaMap.length == 0 || nativeMap == null || nativeMap.length != javaMap.length) {
                return false;
            }
            for (int i = 0; i < javaMap.length; i++) {
                if (!javaMap[i].equals(nativeMap[i])) return false;
            }
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String[] javaEntryMap(File apk, boolean verifyContentCrc) throws Exception {
        List<String> rows = new ArrayList<>();
        Set<String> allNames = new HashSet<>();
        int manifestCount = 0;
        int primaryDexCount = 0;
        int resourcesCount = 0;
        int clientCount = 0;

        try (ZipFile zip = new ZipFile(apk)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!safeName(name) || !allNames.add(name)) {
                    throw new IllegalStateException("APK entry name policy failed");
                }
                if (entry.isDirectory()) continue;

                if ("AndroidManifest.xml".equals(name)) manifestCount++;
                if ("classes.dex".equals(name)) primaryDexCount++;
                if ("resources.arsc".equals(name)) resourcesCount++;
                if ("lib/arm64-v8a/libclient.so".equals(name)) clientCount++;

                if (!isCritical(name)) continue;
                if (rows.size() >= MAX_CRITICAL_ENTRIES) {
                    throw new IllegalStateException("Too many critical APK entries");
                }

                long crc = entry.getCrc();
                long size = entry.getSize();
                long compressed = entry.getCompressedSize();
                int method = entry.getMethod();
                if (crc < 0L || size < 0L || compressed < 0L
                        || size > MAX_ENTRY_BYTES
                        || (method != ZipEntry.STORED && method != ZipEntry.DEFLATED)) {
                    throw new IllegalStateException("Invalid APK entry metadata");
                }

                if (verifyContentCrc && !contentMatchesStoredCrc(zip, entry, crc, size)) {
                    throw new IllegalStateException("APK entry CRC mismatch");
                }

                rows.add(row(name, crc, size, compressed, method));
            }
        }

        if (manifestCount != 1 || primaryDexCount != 1 || resourcesCount != 1 || clientCount != 1) {
            throw new IllegalStateException("Required APK entries are missing or duplicated");
        }
        Collections.sort(rows);
        return rows.toArray(new String[0]);
    }

    private static boolean contentMatchesStoredCrc(
            ZipFile zip,
            ZipEntry entry,
            long expectedCrc,
            long expectedSize) {
        CRC32 crc = new CRC32();
        byte[] buffer = new byte[BUFFER_BYTES];
        long total = 0L;
        try (InputStream input = zip.getInputStream(entry)) {
            while (true) {
                int read = input.read(buffer);
                if (read < 0) break;
                if (read == 0) continue;
                total += read;
                if (total > expectedSize || total > MAX_ENTRY_BYTES) return false;
                crc.update(buffer, 0, read);
            }
        } catch (Throwable ignored) {
            return false;
        }
        return total == expectedSize && crc.getValue() == expectedCrc;
    }

    private static boolean isCritical(String name) {
        return "AndroidManifest.xml".equals(name)
                || "resources.arsc".equals(name)
                || "lib/arm64-v8a/libclient.so".equals(name)
                || isDex(name)
                || name.startsWith("assets/");
    }

    private static boolean isDex(String name) {
        if ("classes.dex".equals(name)) return true;
        if (!name.startsWith("classes") || !name.endsWith(".dex")) return false;
        String middle = name.substring("classes".length(), name.length() - ".dex".length());
        if (middle.isEmpty()) return false;
        for (int i = 0; i < middle.length(); i++) {
            if (!Character.isDigit(middle.charAt(i))) return false;
        }
        return true;
    }

    private static boolean safeName(String name) {
        if (name == null || name.isEmpty() || name.length() > 4096
                || name.startsWith("/") || name.indexOf('\\') >= 0 || name.indexOf('|') >= 0
                || name.contains("../") || name.equals("..") || name.startsWith("../")) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (ch == 0 || ch == '\n' || ch == '\r') return false;
        }
        return true;
    }

    private static String row(String name, long crc, long size, long compressed, int method) {
        return name + '|' + crc + '|' + size + '|' + compressed + '|' + method;
    }

    private static native String[] readCriticalArchiveMapNative(
            String apkPath,
            String packageName,
            boolean verifyContentCrc);
}
