package com.pubgm.floating;

import android.annotation.*;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.Process;
import android.view.WindowManager.LayoutParams;
import androidx.annotation.Keep;
import com.pubgm.activity.MainActivity;
import com.pubgm.floating.HideRecorder;
import com.pubgm.utils.FLog;
import com.pubgm.utils.FPrefs;
import java.io.IOException;

public class Overlay extends Service {

    static {
        try {
            System.loadLibrary("client");
        } catch (UnsatisfiedLinkError w) {
            FLog.error(w.getMessage());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public FPrefs getPref() {
        return FPrefs.with(this);
    }
    
    private native boolean getReady();
    private native void Close();
    private static native boolean isDaemonConnected();
    public static native void DrawOn(ESPView espView, Canvas canvas);

    private WindowManager windowManager;
    private ESPView overlayView;
    private Overlay Instance;
    Process process;

    @SuppressLint("StaticFieldLeak")
    public static Context ctx;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String CHANNEL_ID = "OverlayServiceChannel";
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Overlay Service",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);

            Notification notification = new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("ZX Cheat Active")
                    .setSmallIcon(com.pubgm.R.drawable.ic_logo)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setOngoing(true)
                    .build();
            startForeground(1, notification);
        }
        return START_STICKY;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level == TRIM_MEMORY_RUNNING_CRITICAL || level == TRIM_MEMORY_COMPLETE) {
            FLog.error("Overlay: Critical memory pressure detected!");
        }
    }

    @SuppressLint("InflateParams")
    @Override
    public void onCreate() {
        super.onCreate();
        ctx = this;
        Start();
        windowManager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        overlayView = new ESPView(ctx);
        DrawCanvas();
    }

    @Override
    public void onDestroy() {
        try {
            if (overlayView != null) {
                overlayView.stopDrawing();
                WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                if (wm != null) {
                    try {
                        wm.removeView(overlayView);
                    } catch (Exception e) {}
                }
                overlayView = null;
            }
            Close();
            if (process != null) {
                process.destroy();
                process = null;
            }
        } catch (Exception e) {
            FLog.error("Overlay onDestroy: " + e.getMessage());
        }
        super.onDestroy();
    }

    private Thread daemonThread;
    private Thread readyThread;

    public void Start() {
        if (readyThread == null || !readyThread.isAlive()) {
            readyThread = new Thread(() -> getReady());
            readyThread.start();
        }
        
        if (daemonThread == null || !daemonThread.isAlive()) {
            daemonThread = new Thread(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {}
                StartDaemon();
            });
            daemonThread.start();
        }
    }

    private void DrawCanvas() {
        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        }

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, 0, getNavigationBarHeight(), LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_FULLSCREEN, PixelFormat.RGBA_8888
        );

        if (getPref().readBoolean("anti_recorder")) {
			HideRecorder.setFakeRecorderWindowLayoutParams(params);
        }

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
        //params.alpha = 0.8f;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        windowManager.addView(overlayView, params);
    }

    private int getNavigationBarHeight() {
        boolean hasMenuKey = ViewConfiguration.get(this).hasPermanentMenuKey();
        int resourceId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0 && !hasMenuKey) {
            return getResources().getDimensionPixelSize(resourceId);
        }
        return 0;
    }
    
    public void StartDaemon() {
        if (MainActivity.socket == null || MainActivity.socket.isEmpty()) {
            String path = getFilesDir().toString() + "/sock64";
            MainActivity.socket = path;
            java.io.File f = new java.io.File(path);
            if (f.exists()) f.setExecutable(true, false);
        }
        
        if (MainActivity.socket != null && !MainActivity.socket.isEmpty()) {
            // Check if file exists
            String path = MainActivity.socket.replace("su -c ", "");
            java.io.File f = new java.io.File(path);
            if (!f.exists()) {
                FLog.error("Daemon file not found: " + path);
                return;
            }
            f.setExecutable(true, false);
            
            // Safety: Ensure only one daemon thread/process is active
            if (process == null || !isDaemonConnected()) {
                if (process != null) {
                    process.destroy();
                    process = null;
                }
                Shell(MainActivity.socket);
            }
        }
    }

    public void Shell(String str) {
        if (str == null || str.isEmpty()) return;
        try {
            if (str.startsWith("su -c ")) {
                String cmd = str.replace("su -c ", "");
                process = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
            } else {
                process = Runtime.getRuntime().exec(str);
            }
        } catch (Exception e) {
            FLog.error("Shell error: " + e.getMessage());
            process = null;
        }
    }

    static boolean getConfig(String key) {
        SharedPreferences sp = ctx.getSharedPreferences("espValue", Context.MODE_PRIVATE);
        return sp.getBoolean(key, false);
    }

}
