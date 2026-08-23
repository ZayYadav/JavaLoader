package com.pubgm.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public final class FileUtils {

    private static final String TAG = "FileUtils";
    private static final int BUFFER_SIZE = 1024 * 1024; // 1MB
    private FileUtils() { }
    
    public static boolean copy(String sourcePath, String destPath) {
        return copy(sourcePath, destPath, true);
    }

    public static boolean copy(String sourcePath, String destPath, boolean overwrite) {

        File source = new File(sourcePath);
        File dest = new File(destPath);

        if (!source.exists() || !source.isFile()) {
            return false;
        }

        if (dest.exists()) {
            if (!overwrite) {
                return false;
            }
        } else {
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    return false;
                }
            }
        }

        try 
            (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(source), BUFFER_SIZE);
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(dest, false), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                bos.write(buffer, 0, bytesRead);
            }

            bos.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean delete(String path) {
        File file = new File(path);
        if (file.exists()) {
            return file.delete();
        }
        return false;
    }

    public static boolean exists(String path) {
        return new File(path).exists();
    }

    public static long size(String path) {
        File file = new File(path);
        return file.exists() ? file.length() : 0;
    }
}