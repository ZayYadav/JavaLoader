package top.niunaijun.blackbox.core.env;

import android.os.Environment;

import java.io.File;

/** Standard-storage compatibility shim; no virtual-app filesystem is provided. */
@Deprecated
public final class BEnvironment {
    private BEnvironment() { }

    public static File getExternalDataDir(String packageName) {
        return new File(Environment.getExternalStorageDirectory(), "Android/data/" + packageName);
    }
}
