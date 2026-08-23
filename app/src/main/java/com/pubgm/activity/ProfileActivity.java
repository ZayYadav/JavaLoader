package com.pubgm.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.pubgm.R;
import com.pubgm.floating.ESPView;
import com.pubgm.utils.ActivityCompat;
import com.pubgm.utils.FPrefs;
import com.pubgm.floating.ItmeManager;

import org.lsposed.lsparanoid.Obfuscate;

// BlackBoxCore Import
import top.niunaijun.blackbox.BlackBoxCore;

@Obfuscate
public class ProfileActivity extends ActivityCompat {

    private static final String PREFS_NAME = "ElitePrefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_HIDE_ROOT = "hide_root";
    private static final String KEY_HIDE_XPOSED = "hide_xposed";
    private static final String KEY_DAEMON_SERVICE = "daemon_service";
    private static final String KEY_ANTI_RECORDER = "anti_recorder";
    private static final String KEY_FPS = "fps";
    private static final int DEFAULT_FPS = 60;

    private FPrefs prefs;
    private ItmeManager itemManager;
    private LinearLayout mainLayout;
    private LinearLayout topBar;
    private LinearLayout settingsCard;

    private RadioButton fps60;
    private RadioButton fps90;
    private RadioButton fps120;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        initializeComponents();
        setupToolbar();
        setupDarkModeSwitch();
        setupHideRecordingSwitch();
        boolean isDark = prefs.readBoolean(KEY_DARK_MODE, true);
        applyTheme(isDark);
    }

    private void initializeComponents() {
        prefs = FPrefs.with(this, PREFS_NAME);
        itemManager = ItmeManager.getInstance(this);
        mainLayout = findViewById(R.id.DrakProfile);
        topBar = findViewById(R.id.topBar);
        settingsCard = findViewById(R.id.settingsCard);
        fps60 = findViewById(R.id.fps60);
        fps90 = findViewById(R.id.fps90);
        fps120 = findViewById(R.id.fps120);
    }

    private void setupToolbar() {
        ImageView backBtn = findViewById(R.id.backBtn);
        if (backBtn != null) {
            backBtn.setOnClickListener(v -> finish());
        }
    }

    private void setupDarkModeSwitch() {
        Switch darkSwitch = findViewById(R.id.DarkModeSwitch);
        if (darkSwitch == null) return;
        darkSwitch.setChecked(prefs.readBoolean(KEY_DARK_MODE, true));
        darkSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.writeBoolean(KEY_DARK_MODE, isChecked);
            applyTheme(isChecked);
            showToast(isChecked ? "Dark Mode ON" : "Light Mode ON");
        });
    }

    private void applyTheme(boolean isDark) {
        if (mainLayout == null || topBar == null || settingsCard == null) return;
        
        if (isDark) {
            mainLayout.setBackgroundColor(0xFF0A0E15);
            topBar.setBackgroundColor(0xFF05090F);
            settingsCard.setBackgroundColor(0xFF131B29);
        } else {
            mainLayout.setBackgroundColor(0xFFF5F5F5);
            topBar.setBackgroundColor(0xFFE0E0E0);
            settingsCard.setBackgroundColor(0xFFFFFFFF);
        }
    }
    
    private void setupHideRecordingSwitch() {
        Switch switchHideRecording = findViewById(R.id.HideRecording);
        if (switchHideRecording == null) return;
        boolean antiRecorder = prefs.readBoolean(KEY_ANTI_RECORDER, false);
        switchHideRecording.setChecked(antiRecorder);
        switchHideRecording.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.writeBoolean(KEY_ANTI_RECORDER, isChecked);
            showToast("Anti Recorder: " + (isChecked ? "ON" : "OFF"));
        });
    }

    private void showToast(String message) {
        toast(message);
    }
    
    @Override
    public void onBackPressed() {
        if (isLogin) {
            super.onBackPressed();
        } else {
            finish();
        }
    }
}