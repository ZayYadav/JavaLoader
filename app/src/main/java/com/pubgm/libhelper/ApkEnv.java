package com.pubgm.libhelper;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import com.pubgm.BoxApplication;
import com.pubgm.utils.FLog;

import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.util.List;

/** Standard Android package helper. Virtualization and library injection are intentionally disabled. */
@Obfuscate
public class ApkEnv {
    private static ApkEnv singleton;

    public static synchronized ApkEnv getInstance() {
        if (singleton == null) singleton = new ApkEnv();
        return singleton;
    }

    public ApplicationInfo getApplicationInfo(String packageName) {
        try {
            return BoxApplication.get().getPackageManager().getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException error) {
            return null;
        }
    }

    public ApplicationInfo getApplicationInfoContainer(String packageName) {
        return getApplicationInfo(packageName);
    }

    public void launchApk(String packageName) {
        try {
            Intent launch = BoxApplication.get().getPackageManager().getLaunchIntentForPackage(packageName);
            if (launch == null) {
                BoxApplication.get().toast("Client not installed");
                return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            BoxApplication.get().startActivity(launch);
        } catch (Exception error) {
            FLog.error("Launch failed: " + error.getMessage());
        }
    }

    public boolean isInstalled(String packageName) {
        return getApplicationInfo(packageName) != null;
    }

    public boolean isInstalled2(String packageName) {
        return isInstalled(packageName);
    }

    public boolean isRunning(String packageName) {
        try {
            ActivityManager manager = (ActivityManager) BoxApplication.get().getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> processes = manager == null ? null : manager.getRunningAppProcesses();
            if (processes == null) return false;
            for (ActivityManager.RunningAppProcessInfo process : processes) {
                if (packageName.equals(process.processName)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    public boolean installByFile(String packageName) {
        return isInstalled(packageName);
    }

    public boolean installByPackage(String packageName) {
        return isInstalled(packageName);
    }

    public static void unInstallApp(String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            BoxApplication.get().startActivity(intent);
        } catch (Exception error) {
            FLog.error("Uninstall request failed: " + error.getMessage());
        }
    }

    public void stopRunningApp(String packageName) {
        BoxApplication.get().toast("Use Android App Info to stop the app");
    }

    public File getObbContainerPath(String packageName) {
        return new File("/storage/emulated/0/Android/obb", packageName);
    }

    public boolean removeLoader(String packageName) {
        return true;
    }

    public boolean tryAddLoader(String packageName) {
        FLog.error("Native library injection is disabled in standard package mode");
        return false;
    }
}
