package com.pubgm.libhelper;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.RemoteException;
import androidx.core.app.ActivityCompat;
import com.pubgm.BoxApplication;
import com.pubgm.R;
import com.pubgm.utils.FLog;
import static com.pubgm.Config.GAME_LIST_PKG;
import com.pubgm.utils.FileUtils;
import java.io.File;
import org.lsposed.lsparanoid.Obfuscate;

// BlackBoxCore Imports
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.env.BEnvironment;
import top.niunaijun.blackbox.entity.pm.InstallResult;

@Obfuscate
public class ApkEnv {
    File obbContaine;
    
    private static ApkEnv singleton;
    
    public static ApkEnv getInstance() {
        if (singleton == null) {
            singleton = new ApkEnv();
        }
        return singleton;
    }
    
    public ApplicationInfo getApplicationInfo(String packageName) {
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = BoxApplication.get().getPackageManager().getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException err) {
            FLog.error(err.getMessage());
            BoxApplication.get().toast(err.getMessage());
            return null;
        }
        return applicationInfo;
    }
    
    public ApplicationInfo getApplicationInfoContainer(String packageName) {
        if (!isInstalled(packageName)) {
            BoxApplication.get().toast("App not install, install first");
            return null;
        }

        try {
            // FIXED: Using BlackBoxCore instead of EliteInstaller
            ApplicationInfo applicationInfo =
                    BlackBoxCore.getBPackageManager()
                                .getApplicationInfo(packageName, 0, 0);

            return applicationInfo;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public void launchApk(String packageName) {
        if (!isInstalled(packageName)) {
            BoxApplication.get().toast("Client not installed");
            return;
        }
        try {
            // FIXED: Using BlackBoxCore instead of EliteInstaller
            BlackBoxCore.get().launchApk(packageName, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public boolean isInstalled(String packageName) {
        try {
            // FIXED: Using BlackBoxCore instead of EliteInstaller
            return BlackBoxCore.get().isInstalled(packageName, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public boolean isInstalled2(String packageName) {
        try {
            // FIXED: Using BlackBoxCore instead of EliteInstaller
            return BlackBoxCore.get() != null && BlackBoxCore.get().isInstalled(packageName, 0);
        } catch (Throwable e) {
            return false;
        }
    }

    public boolean isRunning(String packageName) {
        return false;
    }

    public boolean installByFile(String packageName) {
        ApplicationInfo applicationInfo = getApplicationInfo(packageName);
        if (applicationInfo == null) {
            return false;
        }
        try {
            // FIXED: Using BlackBoxCore instead of EliteInstaller
            InstallResult result = BlackBoxCore.get().installPackageAsUser(applicationInfo.sourceDir, 0);
            return result.success;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public boolean installByPackage(String packageName) {
        try {
            // FIXED: Using BlackBoxCore instead of EliteInstaller
            InstallResult result = BlackBoxCore.get().installPackageAsUser(packageName, 0);
            return result.success;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void unInstallApp(String packageName) {
        try {
            // FIXED: Using BlackBoxCore instead of EliteInstaller
            BlackBoxCore.get().uninstallPackageAsUser(packageName, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void stopRunningApp(String packageName) {
        try {
            // FIXED: Using BlackBoxCore instead of EliteInstaller
            BlackBoxCore.get().stopPackage(packageName, 0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public File getObbContainerPath(String packageName) {
        return new File(
            "/storage/emulated/0/SdCard/Android/obb",
            packageName
        );
    }
    
    public boolean removeLoader(String packageName) {
        // FIXED: Using BlackBoxCore BEnvironment instead of EliteInstaller
        File destDir = BEnvironment.getAppLibDir(packageName);
        if (destDir == null || !destDir.exists()) return true;

        String[] targetNames = {"libAkAudioVisiual.so", "libfarlight.so"};
        boolean allRemoved = true;

        for (String name : targetNames) {
            File loader = new File(destDir, name);
            if (loader.exists()) {
                if (!loader.delete()) {
                    allRemoved = false;
                }
            }
        }
        return allRemoved;
    }

    public boolean tryAddLoader(String packageName) {
        ApplicationInfo applicationInfo = getApplicationInfoContainer(packageName);
        if (applicationInfo == null) {
            FLog.error("Injection Error: Application Info Null");
            return false;
        }

        // Source is libbgmi.so from Saved.zip
        String targetName = "libbgmi.so"; 
        
        File loaderDir = new File(BoxApplication.get().getFilesDir(), "loader");
        if (!loaderDir.exists()) loaderDir.mkdirs();
        
        File sourceLib = new File(loaderDir, targetName);
        
        // Fallback checks if libbgmi.so is not found
        if (!sourceLib.exists()) {
             String[] fallbacks = {"libbgmi.so", "libpubgm.so", "libkorea.so", "libvietnam.so", "libtaiwan.so"};
             for (String f : fallbacks) {
                 File test = new File(loaderDir, f);
                 if (test.exists()) {
                     sourceLib = test;
                     targetName = f;
                     break;
                 }
             }
        }

        if (!sourceLib.exists()) {
            FLog.error("Injection Error: Source lib not found in " + loaderDir.getAbsolutePath());
            BoxApplication.get().toast("Source Lib Missing!");
            return false;
        }

        // The name the game expects to load (hijacked name)
        String destName = packageName.equals("com.miraclegames.farlight84") ? "libfarlight.so" : "libAkAudioVisiual.so";
        
        // FIXED: Using BlackBoxCore BEnvironment to get the CORRECT writable lib path for virtualized app
        File destDir = BEnvironment.getAppLibDir(packageName);
        if (destDir == null) {
            FLog.error("Injection Error: Could not resolve virtual lib path");
            return false;
        }
        
        if (!destDir.exists()) destDir.mkdirs();
        
        File loaderDest = new File(destDir, destName);
        
        try {
            // Delete old one if exists
            if (loaderDest.exists()) loaderDest.delete();
            
            FLog.info("Attempting Injection: " + sourceLib.getAbsolutePath() + " -> " + loaderDest.getAbsolutePath());
            
            if (FileUtils.copy(sourceLib.getAbsolutePath(), loaderDest.getAbsolutePath())) {
                // Ensure system recognizes the file and permissions
                loaderDest.setReadable(true, false);
                loaderDest.setExecutable(true, false);
                
                FLog.info("Injection Success: " + targetName + " successfully injected as " + destName);
                return true;
            } else {
                FLog.error("Injection Error: File copy failed");
            }
        } catch (Exception err) {
            FLog.error("Injection Exception: " + err.getMessage());
            err.printStackTrace();
        }

        return false;
    }
}