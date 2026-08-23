package com.pubgm;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatDelegate;
import com.pubgm.utils.FLog;
import com.pubgm.utils.FPrefs;
import com.google.android.material.color.DynamicColors;
import java.io.File;
import java.io.IOException;
import top.niunaijun.blackbox.core.system.api.MetaActivationManager;
import org.lsposed.lsparanoid.Obfuscate;

// ONLY BlackBoxCore IMPORTS
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback;
import top.niunaijun.blackbox.core.env.BEnvironment;

@Obfuscate
public class BoxApplication extends Application {

    public static BoxApplication gApp;
    private native String BoxApp();
    public static BoxApplication get() {
        return gApp;
    }
    
    private final String[] process_names = {
            "com.pubg.krmobile",   // KOREA - 1
            "com.tencent.ig",      // GLOBAL - 2
            "com.rekoo.pubgm",     // TAIWAN - 3
            "com.vng.pubgmobile",  // VIETNAM - 4
            "com.pubg.imobile"     // BGMI - 5
    };
    
    static {
        try {
            System.loadLibrary("client");
        } catch(UnsatisfiedLinkError w) {
            FLog.error(w.getMessage());
        }
    }
    
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        try {
            BlackBoxCore.get().doAttachBaseContext(base, new ClientConfiguration() {
                @Override
                public String getHostPackageName() {
                    return base.getPackageName();
                }

                @Override
                public boolean isEnableDaemonService() {
                    return false;
                }

                @Override
                public boolean requestInstallPackage(File file) {
                    if (file != null && file.exists()) {
                        try {
                            base.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
                        } catch (Exception ignored) {}
                    }
                    return false;
                }
            });
        } catch (Exception e) {
            FLog.error("BlackBoxCore attachBaseContext error: " + e.getMessage());
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        gApp = this;
        
        // Global Crash Handler for Stability
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e("ZX_CRASH", "Uncaught exception in thread " + thread.getName(), throwable);
            FLog.error("CRASH [" + thread.getName() + "]: " + throwable.getMessage());
        });

        // BlackBoxCore onCreate
        try {
            BlackBoxCore.get().doCreate();
        } catch (Exception e) {
            FLog.error("BlackBoxCore doCreate error: " + e.getMessage());
        }
        
        MetaActivationManager.activateSdk("ZORO-6069927A-318");
        
        try {
            BlackBoxCore.get().addAppLifecycleCallback(new AppLifecycleCallback() {
                @Override
                public void beforeApplicationOnCreate(String packageName, String processName, Application application, int userId) {
                    try {
                        for (String pkg : process_names) {
                            if (pkg.equals(packageName) && pkg.equals(processName)) {
                                // BGMI loader
                                if (pkg.equals("com.pubg.imobile")) {
                                    File p1 = new File(getFilesDir(), "loader/libbgmi.so");
                                    if (p1.exists()) {
                                        System.load(p1.getAbsolutePath());
                                        Log.d("App", "Loaded libbgmi.so for BGMI");
                                    } else {
                                        Log.e("App", "libbgmi.so not found!");
                                    }
                                }
                            }
                        }
                    } catch (UnsatisfiedLinkError e) {
                        Log.e("App", "Native lib load failed: " + e.getMessage());
                        e.printStackTrace();
                        System.exit(0);
                    } catch (Exception e) {
                        Log.e("App", "Error loading game libs: " + e.getMessage());
                        e.printStackTrace();
                        System.exit(0);
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(0);
        }
        DynamicColors.applyToActivitiesIfAvailable(this);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
    }
    
    public void toast(CharSequence msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}