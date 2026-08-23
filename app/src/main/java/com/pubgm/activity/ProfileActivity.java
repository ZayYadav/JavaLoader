package com.pubgm.activity;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.Switch;

import com.pubgm.R;
import com.pubgm.floating.ItmeManager;
import com.pubgm.utils.ActivityCompat;
import com.pubgm.utils.FPrefs;

import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class ProfileActivity extends ActivityCompat {

    private static final String PREFS_NAME = "ElitePrefs";
    private static final String KEY_DARK_MODE = "dark_mode";
    private static final String KEY_ANTI_RECORDER = "anti_recorder";

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
        applyTheme(prefs.readBoolean(KEY_DARK_MODE, true));
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
        if (backBtn != null) backBtn.setOnClickListener(v -> finish());
    }

    private void setupDarkModeSwitch() {
        Switch darkSwitch = findViewById(R.id.DarkModeSwitch);
        if (darkSwitch == null) return;
        darkSwitch.setChecked(prefs.readBoolean(KEY_DARK_MODE, true));
        darkSwitch.setOnCheckedChangeListener((button, isChecked) -> {
            prefs.writeBoolean(KEY_DARK_MODE, isChecked);
            applyTheme(isChecked);
            toast(isChecked ? "Dark Mode ON" : "Light Mode ON");
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
        switchHideRecording.setOnCheckedChangeListener((button, isChecked) -> {
            prefs.writeBoolean(KEY_ANTI_RECORDER, isChecked);
            toast("Anti Recorder: " + (isChecked ? "ON" : "OFF"));
        });
    }

    @Override
    public void onBackPressed() {
        if (isLogin) super.onBackPressed();
        else finish();
    }
}
