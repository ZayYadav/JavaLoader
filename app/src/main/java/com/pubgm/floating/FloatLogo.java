package com.pubgm.floating;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.pubgm.R;
import com.pubgm.utils.FLog;

public class FloatLogo extends Service {

    private WindowManager mWindowManager;
    private View mFloatingView;
    private View logoView, menuView;
    private GestureDetector gestureDetector;
    private SharedPreferences mprefs;
    private boolean isOverlayRunning = false;

    // ===== VIEWS =====
    private ImageView closeBtn;
    private CheckBox espCheck, boxCheck, lineCheck, skeletonCheck, nameCheck, distanceCheck, vehiclesCheck, lootBoxCheck;
    private CheckBox healthCheck, headCheck, alert360Check, grenadeCheck, weaponCheck;
    private CheckBox aimbotCheck;
    private android.widget.RadioGroup aimbotModeGroup;
    private SeekBar skFov, skRange, skRecoil, skIpad;
    private TextView txtFov, txtRange, txtRecoil, txtIpad;

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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String CHANNEL_ID = "FloatLogoServiceChannel";
            android.app.NotificationChannel channel = new android.app.NotificationChannel(CHANNEL_ID,
                    "ZX Menu Service",
                    android.app.NotificationManager.IMPORTANCE_LOW);
            android.app.NotificationManager manager = getSystemService(android.app.NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);

            android.app.Notification notification = new android.app.Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle("ZX Cheat Menu Active")
                    .setSmallIcon(com.pubgm.R.drawable.ic_logo)
                    .setCategory(android.app.Notification.CATEGORY_SERVICE)
                    .setPriority(android.app.Notification.PRIORITY_MAX)
                    .setOngoing(true)
                    .build();
            startForeground(2, notification);
        }
        return START_STICKY;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level == TRIM_MEMORY_RUNNING_CRITICAL || level == TRIM_MEMORY_COMPLETE) {
            FLog.error("FloatLogo: Critical memory pressure detected!");
            // Perform emergency cleanup if needed
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate() {
        super.onCreate();
        try {
            mprefs = getSharedPreferences("espValue", Context.MODE_PRIVATE);
            createOver();
            Init();
        } catch (Exception e) {
            FLog.error("FloatLogo onCreate: " + e.getMessage());
        }
    }

    private void DrawESP() {
        if (!isOverlayRunning) {
            startService(new Intent(this, Overlay.class));
            isOverlayRunning = true;
        }
    }

    private void StopESP() {
        try {
            if (isOverlayRunning) {
                Intent intent = new Intent(this, Overlay.class);
                stopService(intent);
                isOverlayRunning = false;
            }
        } catch (Exception e) {
            FLog.error("StopESP error: " + e.getMessage());
        }
    }

    @SuppressLint("InflateParams")
    void createOver() {
        try {
            mFloatingView = LayoutInflater.from(this).inflate(R.layout.float_logo, null);
            if (mFloatingView == null) {
                FLog.error("FloatLogo: Failed to inflate layout!");
                stopSelf();
                return;
            }

            int LAYOUT_FLAG = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 
                    WindowManager.LayoutParams.TYPE_PHONE;

            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    LAYOUT_FLAG,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.RGBA_8888
            );

            mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (mWindowManager != null) {
                mWindowManager.addView(mFloatingView, params);
            }

            gestureDetector = new GestureDetector(this, new SingleTapConfirm());

            logoView = mFloatingView.findViewById(R.id.logo_view);
            menuView = mFloatingView.findViewById(R.id.menu_view);
            closeBtn = mFloatingView.findViewById(R.id.close);

            // Safety check for critical views
            if (logoView == null || menuView == null) {
                FLog.error("FloatLogo: Critical views not found in layout!");
                return;
            }

            // ESP
            espCheck = mFloatingView.findViewById(R.id.esp);
            boxCheck = mFloatingView.findViewById(R.id.box);
            lineCheck = mFloatingView.findViewById(R.id.line);
            skeletonCheck = mFloatingView.findViewById(R.id.skeleton);
            nameCheck = mFloatingView.findViewById(R.id.name);
            distanceCheck = mFloatingView.findViewById(R.id.distance);
            vehiclesCheck = mFloatingView.findViewById(R.id.vehicles);
            lootBoxCheck = mFloatingView.findViewById(R.id.loot_box);
            healthCheck = mFloatingView.findViewById(R.id.health);
            headCheck = mFloatingView.findViewById(R.id.head);
            alert360Check = mFloatingView.findViewById(R.id.alert360);
            grenadeCheck = mFloatingView.findViewById(R.id.grenade);
            weaponCheck = mFloatingView.findViewById(R.id.weapon);

            // Aimbot
            aimbotCheck = mFloatingView.findViewById(R.id.aimbot);
            aimbotModeGroup = mFloatingView.findViewById(R.id.aimbotMode);
            
            // Sliders
            skFov = mFloatingView.findViewById(R.id.sk_fov);
            skRange = mFloatingView.findViewById(R.id.sk_range);
            skRecoil = mFloatingView.findViewById(R.id.sk_recoil);
            skIpad = mFloatingView.findViewById(R.id.sk_ipad);

            txtFov = mFloatingView.findViewById(R.id.txt_fov);
            txtRange = mFloatingView.findViewById(R.id.txt_range);
            txtRecoil = mFloatingView.findViewById(R.id.txt_recoil);
            txtIpad = mFloatingView.findViewById(R.id.txt_ipad);

            if (closeBtn != null) {
                closeBtn.setOnClickListener(v -> {
                    menuView.setVisibility(View.GONE);
                    logoView.setVisibility(View.VISIBLE);
                });
            }

            logoView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (gestureDetector.onTouchEvent(event)) {
                        menuView.setVisibility(View.VISIBLE);
                        logoView.setVisibility(View.GONE);
                        return true;
                    }
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = params.x;
                            initialY = params.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            params.x = initialX + (int) (event.getRawX() - initialTouchX);
                            params.y = initialY + (int) (event.getRawY() - initialTouchY);
                            if (mWindowManager != null) {
                                mWindowManager.updateViewLayout(mFloatingView, params);
                            }
                            return true;
                    }
                    return false;
                }
            });
        } catch (Exception e) {
            FLog.error("FloatLogo createOver error: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mFloatingView != null) mWindowManager.removeView(mFloatingView);
        StopESP();
        ToggleAim(false);
    }

    void setConfig(String key, boolean value) { mprefs.edit().putBoolean(key, value).apply(); }
    boolean getConfig(String key) { return mprefs.getBoolean(key, false); }
    void setInt(String key, int value) { mprefs.edit().putInt(key, value).apply(); }
    int getInt(String key, int def) { return mprefs.getInt(key, def); }

    @SuppressLint("SetTextI18n")
    void Init() {
        // ESP
        espCheck.setChecked(getConfig("EnableEsp"));
        if (espCheck.isChecked()) DrawESP();
        espCheck.setOnCheckedChangeListener((b, isChecked) -> {
            setConfig("EnableEsp", isChecked);
            if (isChecked) DrawESP(); else StopESP();
        });

        setupEsp(boxCheck, "Box", 1);
        setupEsp(lineCheck, "Line", 2);
        setupEsp(skeletonCheck, "Skeleton", 8);
        setupEsp(nameCheck, "Name", 5);
        setupEsp(distanceCheck, "Distance", 3);
        setupEsp(vehiclesCheck, "Vehicles", 15);
        setupEsp(lootBoxCheck, "LootItems", 11);
        setupEsp(healthCheck, "Health", 4);
        setupEsp(headCheck, "HeadCircle", 6);
        setupEsp(alert360Check, "Alert360", 7);
        setupEsp(grenadeCheck, "Grenade", 9);
        setupEsp(weaponCheck, "Weapon", 10);

        // Explicitly disable ground items to prevent native crashes/rendering if UI is missing
        SettingValue(17, false); // Ground Items

        // Aimbot
        aimbotCheck.setChecked(getConfig("Aimbot"));
        ToggleAim(aimbotCheck.isChecked());
        aimbotCheck.setOnCheckedChangeListener((b, isChecked) -> {
            setConfig("Aimbot", isChecked);
            ToggleAim(isChecked);
        });

        aimbotModeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.aim_head) Target(1);
            else if (checkedId == R.id.aim_chest) Target(2);
            else if (checkedId == R.id.aim_body) Target(3);
        });

        // Sliders
        setupSeekBar(skFov, txtFov, "FOV Size: ", "fov", 0, val -> Range(val));
        setupSeekBar(skRange, txtRange, "Aimbot Range: ", "range", 1, val -> distances(val));
        setupSeekBar(skRecoil, txtRecoil, "Recoil: ", "recoil", 0, val -> recoil(val));
        setupSeekBar(skIpad, txtIpad, "iPad View: ", "ipad", 1, val -> WideView(val));
    }

    private void setupEsp(CheckBox cb, String key, int code) {
        cb.setChecked(getConfig(key));
        SettingValue(code, cb.isChecked());
        cb.setOnCheckedChangeListener((b, isChecked) -> {
            setConfig(key, isChecked);
            SettingValue(code, isChecked);
        });
    }

    private void setupSeekBar(SeekBar sk, TextView tv, String label, String key, int def, SliderListener listener) {
        int saved = getInt(key, def);
        sk.setProgress(saved);
        tv.setText(label + saved + (key.equals("recoil") ? "%" : (key.equals("ipad") ? "x" : "")));
        listener.onChanged(saved);
        
        sk.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tv.setText(label + progress + (key.equals("recoil") ? "%" : (key.equals("ipad") ? "x" : "")));
                listener.onChanged(progress);
                setInt(key, progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    interface SliderListener { void onChanged(int value); }

    public native void SettingValue(int code, boolean value);
    public native void Range(int range);
    public native void distances(int distances);
    public native void recoil(int recoil);
    public native void WideView(int wideview);
    public native void Target(int target);
    public native void ToggleAim(boolean value);
}

class SingleTapConfirm extends GestureDetector.SimpleOnGestureListener {
    @Override public boolean onSingleTapUp(MotionEvent event) { return true; }
}
