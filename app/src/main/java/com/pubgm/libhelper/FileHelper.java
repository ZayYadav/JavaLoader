package com.pubgm.libhelper;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.pubgm.activity.MainActivity;
import com.pubgm.utils.ActivityCompat;
import com.pubgm.utils.FLog;
import com.pubgm.utils.FileUtils;
import java.io.File;
import com.pubgm.R;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class FileHelper {
    static boolean dn = false;
    static boolean is64Bit = true;
    
    
    public static void tryInstallWithCopyObb(MainActivity activity, LinearProgressIndicator prog, String packageName) {
        new Thread(() -> {
            PackageInfo info = null;
            try {
                info = activity.getPackageManager().getPackageInfo(packageName, 0);
            } catch (PackageManager.NameNotFoundException err) {
                FLog.error(err.getMessage());
            }

            if (info == null) {
                handleInstallationError(activity, prog, "Please Install Game first.");
                return;
            }

            String gameObb = "main." + info.versionCode + "." + info.packageName + ".obb";
            File obbDest = new File("storage/emulated/0/Android/obb/" + packageName, gameObb);

            if (!obbDest.exists()) {
                handleInstallationError(activity, prog, "Obb File not found");
                return;
            }

            File virObbDir = ApkEnv.getInstance().getObbContainerPath(packageName);
            if (!virObbDir.exists()) virObbDir.mkdirs();

            File virObbDest = new File(virObbDir, gameObb);

            activity.runOnUiThread(() -> {
                activity.doHideProgress();
                activity.doShowProgress(true);
            });

            try {
                FileUtils.copy(obbDest.toString(), virObbDest.toString());
            } catch (Exception err) {
                FLog.error(err.getMessage());
                return;
            }

            if (!ApkEnv.getInstance().isInstalled(packageName)) {
                boolean installResult = ApkEnv.getInstance().installByPackage(packageName);
                if (!installResult) {
                    handleInstallationError(activity, prog, "Failed Add Games");
                    return;
                }
            }

            ApplicationInfo applicationInfo = ApkEnv.getInstance().getApplicationInfoContainer(packageName);
            if (applicationInfo == null) {
                handleInstallationError(activity, prog, "Error, Application Info");
                return;
            }

            activity.runOnUiThread(() -> {
                MainActivity.get().doInitRecycler();
                prog.setIndeterminate(false);
                activity.doHideProgress();
                ActivityCompat.getActivityCompat().toast("Installation is complete.");
            });
            
        }).start();
    }

    private static void handleInstallationError(MainActivity activity, LinearProgressIndicator prog, String errorMessage) {
        activity.runOnUiThread(() -> {
            activity.doHideProgress();
            ActivityCompat.getActivityCompat().toast(errorMessage);
        });
    }
}