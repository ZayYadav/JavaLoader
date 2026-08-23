package com.pubgm.activity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.ActivityManager;
import android.widget.ImageView;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.annotation.NonNull;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.pubgm.Login;
import com.pubgm.R;
import com.pubgm.adapter.RecyclerViewAdapter;
import com.pubgm.libhelper.DownloadZipAdapter;
import com.pubgm.libhelper.ApkEnv;
import com.pubgm.libhelper.FileHelper;
import com.pubgm.utils.ActivityCompat;
import com.pubgm.utils.FLog;
import com.pubgm.utils.FPrefs;
import com.pubgm.utils.Shell;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.lsposed.lsparanoid.Obfuscate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static com.pubgm.Config.GAME_LIST_ICON;

import com.google.android.material.button.MaterialButton;

import android.os.Vibrator;

// BlackBoxCore Imports
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.entity.pm.InstallResult;
import top.niunaijun.blackbox.core.env.BEnvironment;

@Obfuscate
public class MainActivity extends ActivityCompat {
    
    public static MainActivity instance;
    private Vibrator vibrator;
    private String daemonPath;
    public static String socket;
    public String CURRENT_PACKAGE = "";
    private LinearProgressIndicator progress;
    private RecyclerView recyclerView;
    private RecyclerViewAdapter adapter;
    String sFixCrash = Login.FixCrash();

    private Dialog verifyDialog;
    private View progressBarView;
    private TextView tvStatusText, tvStatusDetail, tvKeyText, tvErrorMessage;
    private LinearLayout progressContainer, statusContainer, deviceInfoContainer, keyContainer, successScreen, errorScreen;
    private TextView tvDeviceInfo, tvAndroidInfo, tvSDKInfo;
    private boolean verified = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    // ===== NEW XML VIEWS =====
    private TextView days, hours, minutes, seconds;
    private TextView deviceInfoTextView;
    private SwitchMaterial hideEsp;
    private TextView hideEspText;
    private MaterialButton startGame;
    private Button exitApp, clearData;
    private MaterialButton btnSwitchMode;
    private boolean isAutoMode = false;
    private boolean modeSelected = false;
    private ImageView mainGameIcon;
    private TextView mainGameTitle, mainGameStatus;
    private View uninstallGame, switchVersionBottom;
    
    // ===== GAME DATA LIST =====
    private ArrayList<String> gameTitles = new ArrayList<>();
    private ArrayList<String> gamePackages = new ArrayList<>();
    private ArrayList<Integer> gameIcons = new ArrayList<>();
    private ArrayList<String> gameStatuses = new ArrayList<>();
    private int selectedGameIndex = 0;
    
    // ===== TIMER =====
    private long expiryTimeMillis = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable;
    private boolean isServiceRunning = false;
    
    // ===== GAME PACKAGE FROM JSON =====
    private String gamePackage = "com.pubg.imobile"; // Default
    
    // ORIGINAL BGMI METHOD - 100% UNCHANGED
    public void doInitRecycler() {
        if (progress != null) progress.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> {

            ArrayList<Integer> imageValues = new ArrayList<>();
            ArrayList<String> titleValues = new ArrayList<>();
            ArrayList<String> versionValues = new ArrayList<>();
            ArrayList<String> statusValues = new ArrayList<>();
            ArrayList<String> packageValues = new ArrayList<>();
            ArrayList<String> genreValues = new ArrayList<>();
            ArrayList<String> regionValues = new ArrayList<>();

            String jsonString = loadJson("games.json");

            try {
                JSONArray jsonArray = new JSONArray(jsonString);
                gameTitles.clear();
                gamePackages.clear();
                gameIcons.clear();
                gameStatuses.clear();

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject game = jsonArray.getJSONObject(i);
                    int icon = GAME_LIST_ICON[game.getInt("imageIndex")];
                    String title = game.getString("title");
                    String pkg = game.getString("package");
                    String status = game.getString("status");

                    imageValues.add(icon);
                    titleValues.add(title);
                    versionValues.add(game.getString("version"));
                    statusValues.add(status);
                    packageValues.add(pkg);
                    genreValues.add(game.getString("genre"));
                    regionValues.add(game.getString("region"));
                    
                    // Populate local lists for switching
                    gameTitles.add(title);
                    gamePackages.add(pkg);
                    gameIcons.add(icon);
                    gameStatuses.add(status);
                }
                
                // Initialize with first game
                if (!gamePackages.isEmpty()) {
                    selectVersion(0);
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
            adapter = new RecyclerViewAdapter(this, imageValues, titleValues, versionValues, statusValues, packageValues, genreValues, regionValues);
            if (recyclerView != null) {
                recyclerView.setAdapter(adapter);
            }
            if (progress != null) progress.setVisibility(View.GONE);
            
            updateMainGameUI();

        }, 300);
    }

    private void selectVersion(int index) {
        if (index < 0 || index >= gamePackages.size()) return;
        
        selectedGameIndex = index;
        gamePackage = gamePackages.get(index);
        
        final String title = gameTitles.get(index);
        final int iconRes = gameIcons.get(index);
        final String status = gameStatuses.get(index);

        runOnUiThread(() -> {
            // Force update UI elements
            if (mainGameTitle == null) mainGameTitle = findViewById(R.id.main_game_title);
            if (mainGameIcon == null) mainGameIcon = findViewById(R.id.main_game_icon);
            if (mainGameStatus == null) mainGameStatus = findViewById(R.id.main_game_status);

            if (mainGameTitle != null) {
                mainGameTitle.setText(title);
            }
            if (mainGameIcon != null) {
                mainGameIcon.setImageResource(iconRes);
            }
            if (mainGameStatus != null) {
                mainGameStatus.setText("Status: " + status);
                if (status != null && status.equalsIgnoreCase("Safe")) {
                    mainGameStatus.setTextColor(Color.parseColor("#10B981"));
                } else {
                    mainGameStatus.setTextColor(Color.parseColor("#EF4444"));
                }
            }
            updateMainGameUI();
        });
    }
    
    public String loadJson(String fileName) {
        String json = null;

        try {
            File file = new File(getFilesDir(), fileName);
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                int size = fis.available();
                byte[] buffer = new byte[size];
                fis.read(buffer);
                fis.close();
                return new String(buffer, "UTF-8");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            File file = new File(getExternalFilesDir(null), fileName);
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                int size = fis.available();
                byte[] buffer = new byte[size];
                fis.read(buffer);
                fis.close();
                return new String(buffer, "UTF-8");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            InputStream is = getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }

        return json;
    }
    
    // ORIGINAL LOAD ASSETS METHOD - 100% UNCHANGED
    public void loadAssets() {
        String filepath = Environment.getExternalStorageDirectory() + "/Android/data/.tyb";
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(filepath);
            byte[] buffer = "DO NOT DELETE".getBytes();
            fos.write(buffer, 0, buffer.length);
            fos.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        daemonPath = getFilesDir().toString() + "/sock64";
        File f = new File(daemonPath);
        if (f.exists()) {
            f.setExecutable(true, false);
            f.setReadable(true, false);
            f.setWritable(true, false);
        }
        
        if (Shell.rootAccess()) {
            socket = "su -c " + daemonPath;
        } else {
            socket = daemonPath;
        }
    }

    public static MainActivity get() {
        return instance;
    }
    
    public static void goMain(Context context) {
        Intent i = new Intent(context, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(i);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;
        setContentView(R.layout.activity_main);
        
        // ===== ORIGINAL CODE =====
        progress = findViewById(R.id.progress);
        recyclerView = findViewById(R.id.recyclerview);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            recyclerView.setVisibility(View.GONE);
        }
        
        hideSystemUI();
        
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        
        // ===== NEW VIEWS =====
        days = findViewById(R.id.days);
        hours = findViewById(R.id.hours);
        minutes = findViewById(R.id.minutes);
        seconds = findViewById(R.id.seconds);
        deviceInfoTextView = findViewById(R.id.device_info);
        hideEsp = findViewById(R.id.hide_esp);
        hideEspText = findViewById(R.id.hide_esp_text);
        startGame = findViewById(R.id.start_game);
        exitApp = findViewById(R.id.exit_app);
        clearData = findViewById(R.id.clear_data);
        btnSwitchMode = findViewById(R.id.btn_switch_mode);

        updateModeSelectionUI();
        
        mainGameIcon = findViewById(R.id.main_game_icon);
        mainGameTitle = findViewById(R.id.main_game_title);
        mainGameStatus = findViewById(R.id.main_game_status);
        uninstallGame = findViewById(R.id.uninstall_game);
        switchVersionBottom = findViewById(R.id.switch_version_bottom);
        
        loadDeviceInfo();
        
        startAnimations();
        
        // CHECK VERIFICATION
        SharedPreferences prefs = getSharedPreferences("auth_pref", MODE_PRIVATE);
        boolean isVerified = prefs.getBoolean("verified", false);
        long expiry = prefs.getLong("expiry", 0);
        
        // If we have EXP string but no timestamp, try parsing it now
        if (expiry == 0 && Login.EXP != null && !Login.EXP.isEmpty()) {
            expiry = Login.getExpiryTimestamp();
        }
        
        if (isVerified && expiry > (System.currentTimeMillis() / 1000) - 60) {
            verified = true;
            expiryTimeMillis = expiry * 1000;
            runOnUiThread(() -> {
                updateExpiryDisplay();
                startTimer();
            });
            doInitRecycler();
        } else {
            createVerifyDialog();
        }
        
        setupNewListeners();
        applyColorfulText();
    }

    private void requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = getPackageName();
            android.os.PowerManager pm = (android.os.PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                try {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(android.net.Uri.parse("package:" + packageName));
                    startActivity(intent);
                } catch (Exception e) {
                    FLog.error("Battery Optimization Request Failed: " + e.getMessage());
                }
            }
        }
    }

    private void applyColorfulText() {
        if (hideEspText != null) {
            hideEspText.post(() -> {
                float width = hideEspText.getPaint().measureText(hideEspText.getText().toString());
                Shader textShader = new LinearGradient(0, 0, width, 0,
                        new int[]{
                                Color.parseColor("#A855F7"),
                                Color.parseColor("#EC4899"),
                                Color.parseColor("#3B82F6")
                        }, null, Shader.TileMode.CLAMP);
                hideEspText.getPaint().setShader(textShader);
                hideEspText.invalidate();
            });
        }
    }

    private void startAnimations() {
        animateBlob(findViewById(R.id.blob_purple), 10000, 80f, -80f);
        animateBlob(findViewById(R.id.blob_pink), 15000, -100f, 100f);
        animateBlob(findViewById(R.id.blob_blue), 12000, 60f, 120f);
    }

    private void animateBlob(View view, int duration, float tx, float ty) {
        if (view == null) return;
        ObjectAnimator animX = ObjectAnimator.ofFloat(view, "translationX", 0f, tx, 0f);
        ObjectAnimator animY = ObjectAnimator.ofFloat(view, "translationY", 0f, ty, 0f);
        animX.setDuration(duration);
        animY.setDuration(duration + 2000);
        animX.setRepeatCount(ValueAnimator.INFINITE);
        animY.setRepeatCount(ValueAnimator.INFINITE);
        animX.setRepeatMode(ValueAnimator.REVERSE);
        animY.setRepeatMode(ValueAnimator.REVERSE);
        animX.start();
        animY.start();
    }

    private void updateMainGameUI() {
        if (gamePackage == null || startGame == null) return;
        
        boolean isInstalled = ApkEnv.getInstance().isInstalled(gamePackage);
        boolean isRunning = ApkEnv.getInstance().isRunning(gamePackage);
        
        runOnUiThread(() -> {
            if (isInstalled) {
                if (uninstallGame != null) uninstallGame.setVisibility(View.VISIBLE);
                if (isRunning) {
                    startGame.setText("STOP GAME");
                } else {
                    startGame.setText("PLAY GAME");
                }
            } else {
                if (uninstallGame != null) uninstallGame.setVisibility(View.GONE);
                startGame.setText("INSTALL GAME");
            }
        });
    }
    
    // ===== NEW LISTENERS =====
    private void setupNewListeners() {
        // Copy HWID on Device Info click (New)
        if (deviceInfoTextView != null) {
            deviceInfoTextView.setOnClickListener(v -> {
                String hwid = Login.getAndroidID(this);
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("HWID", hwid);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                    toast("HWID Copied to Clipboard!");
                }
            });
        }

        // ===== START GAME / MAIN ACTION =====
        if (startGame != null) {
            startGame.setOnClickListener(v -> handleMainAction());
        }

        // Switch Version
        if (switchVersionBottom != null) {
            switchVersionBottom.setOnClickListener(v -> handleVersionSwitch());
        }

        // Uninstall Game
        if (uninstallGame != null) {
            uninstallGame.setOnClickListener(v -> {
                if (gamePackage != null) {
                    new androidx.appcompat.app.AlertDialog.Builder(this)
                            .setTitle("Uninstall Game")
                            .setMessage("Are you sure you want to remove this game from loader?")
                            .setPositiveButton("Yes", (dialog, which) -> {
                                doShowProgress(true);
                                ApkEnv.getInstance().unInstallApp(gamePackage);
                                handler.postDelayed(() -> {
                                    doInitRecycler();
                                    doHideProgress();
                                    toast("Uninstalled Successfully");
                                }, 500);
                            })
                            .setNegativeButton("No", null)
                            .show();
                }
            });
        }

        // Hide ESP
        if (hideEsp != null) {
            hideEsp.setOnCheckedChangeListener((buttonView, isChecked) -> {
                FPrefs.with(this).writeBoolean("hide_esp", isChecked);
                Toast.makeText(this, "Hide ESP: " + (isChecked ? "ON" : "OFF"), Toast.LENGTH_SHORT).show();
            });
        }

        // Exit
        if (exitApp != null) {
            exitApp.setOnClickListener(v -> {
                finishAffinity();
            });
        }

        // Mode Switcher Button
        if (btnSwitchMode != null) {
            btnSwitchMode.setOnClickListener(v -> handleModeSwitchPopup());
        }

        // Clear Data
        if (clearData != null) {
            clearData.setOnClickListener(v -> {
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Clear Data")
                        .setMessage("Are you sure?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            getSharedPreferences("auth_pref", MODE_PRIVATE).edit().clear().apply();
                            FPrefs.with(this).write("USER", "");
                            FPrefs.with(this).writeBoolean("service_running", false);
                            LoginActivity.goLogin(this);
                            finish();
                        })
                        .setNegativeButton("No", null)
                        .show();
            });
        }
    }
    
    // ===== START PATCHER =====
    private void startPatcherAndLaunch() {
        startPatcher();
        
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            launchGame();
        }, 500);
    }

    private void handleVersionSwitch() {
        if (gameTitles.isEmpty()) return;
        
        final com.google.android.material.bottomsheet.BottomSheetDialog dialog = new com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_Translucent);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_version_selector, null);
        dialog.setContentView(dialogView);
        
        RecyclerView rv = dialogView.findViewById(R.id.version_recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        
        rv.setAdapter(new RecyclerView.Adapter<VersionViewHolder>() {
            @NonNull
            @Override
            public VersionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = getLayoutInflater().inflate(R.layout.item_version_selector, parent, false);
                return new VersionViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull VersionViewHolder holder, int position) {
                holder.title.setText(gameTitles.get(position));
                holder.pkg.setText(gamePackages.get(position));
                holder.icon.setImageResource(gameIcons.get(position));
                
                holder.itemView.setOnClickListener(v -> {
                    dialog.dismiss();
                    selectVersion(position);
                    toast("Switched to " + gameTitles.get(position));
                });
                
                // Add simple animation to items
                holder.itemView.setAlpha(0f);
                holder.itemView.setTranslationY(50f);
                holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(300)
                    .setStartDelay(position * 50L)
                    .start();
            }

            @Override
            public int getItemCount() {
                return gameTitles.size();
            }
        });
        
        View btnClose = dialogView.findViewById(R.id.btn_close);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }
        
        dialog.show();
    }
    
    private static class VersionViewHolder extends RecyclerView.ViewHolder {
        TextView title, pkg;
        ImageView icon;
        public VersionViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.version_title);
            pkg = itemView.findViewById(R.id.version_pkg);
            icon = itemView.findViewById(R.id.version_icon);
        }
    }

    private void handleModeSwitchPopup() {
        final BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.Theme_Translucent);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_mode_selector, null);
        dialog.setContentView(dialogView);

        View btnJava = dialogView.findViewById(R.id.btn_select_java);
        View btnAuto = dialogView.findViewById(R.id.btn_select_auto);
        View btnClose = dialogView.findViewById(R.id.btn_close_mode);

        if (btnJava != null) {
            btnJava.setOnClickListener(v -> {
                isAutoMode = false;
                modeSelected = true;
                updateModeSelectionUI();
                dialog.dismiss();
                toast("Switched to JAVA");
            });
        }

        if (btnAuto != null) {
            btnAuto.setOnClickListener(v -> {
                isAutoMode = true;
                modeSelected = true;
                updateModeSelectionUI();
                dialog.dismiss();
                toast("Switched to AUTO MODE");
            });
        }

        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
    }

    private void updateModeSelectionUI() {
        if (btnSwitchMode == null) return;
        
        if (!modeSelected) {
            btnSwitchMode.setText("SELECT GAME MODE");
            btnSwitchMode.setIconResource(R.drawable.animation_play);
            btnSwitchMode.setIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#64748B")));
            btnSwitchMode.setTextColor(Color.parseColor("#64748B"));
            return;
        }

        if (!isAutoMode) {
            btnSwitchMode.setText("MODE: JAVA");
            btnSwitchMode.setIconResource(R.drawable.animation_play);
            btnSwitchMode.setIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#3B82F6")));
            btnSwitchMode.setTextColor(Color.parseColor("#3B82F6"));
        } else {
            btnSwitchMode.setText("MODE: AUTO (ADVANCED)");
            btnSwitchMode.setIconResource(R.drawable.bypass);
            btnSwitchMode.setIconTint(android.content.res.ColorStateList.valueOf(Color.parseColor("#EC4899")));
            btnSwitchMode.setTextColor(Color.parseColor("#EC4899"));
        }
    }

    private void handleMainAction() {
        if (gamePackage == null) return;

        if (ApkEnv.getInstance().isInstalled(gamePackage)) {
            if (ApkEnv.getInstance().isRunning(gamePackage)) {
                ApkEnv.getInstance().stopRunningApp(gamePackage);
                updateMainGameUI();
            } else {
                if (!modeSelected) {
                    toast("Please SELECT GAME MODE first!");
                    handleModeSwitchPopup();
                    return;
                }
                
                if (isAutoMode) {
                    handleAutoModeStart();
                } else {
                    handleJavaModeStart();
                }
            }
        } else {
            doShowProgress(true);
            FileHelper.tryInstallWithCopyObb(this, getProgresBar(), gamePackage);
        }
    }

    private void handleJavaModeStart() {
        // SECURITY: Remove any injected "Auto Mode" loaders to ensure clean Java Mode
        ApkEnv.getInstance().removeLoader(gamePackage);

        // Ensure daemon is downloaded if missing
        File dFile = new File(getFilesDir(), "sock64");
        if (!dFile.exists()) {
            toast("Downloading required files...");
            DownloadZipAdapter downloadZip = new DownloadZipAdapter(this);
            downloadZip.setZipFileName("assets.zip");
            downloadZip.startDownload(Login.FixCrash(), new DownloadZipAdapter.DownloadCallback() {
                @Override
                public void onDownloadComplete(boolean success, String message) {
                    if (success) {
                        startPatcherAndLaunch();
                    } else {
                        toast("Failed to download daemon: " + message);
                    }
                }
                @Override
                public void onProgress(int progress) {}
            });
        } else {
            startPatcherAndLaunch();
        }
    }

    private void handleAutoModeStart() {
        toast("Starting Auto Mode Injection...");
        doShowProgress(true);
        
        DownloadZipAdapter downloadZip = new DownloadZipAdapter(this);
        downloadZip.setZipFileName("Saved.zip");
        
        // Link: https://github.com/aaravmeh2345/Game_loader/releases/download/Bgmi1/Saved.zip
        downloadZip.startDownload("https://github.com/aaravmeh2345/Game_loader/releases/download/Bgmi1/Saved.zip", new DownloadZipAdapter.DownloadCallback() {
            @Override
            public void onDownloadComplete(boolean success, String message) {
                runOnUiThread(() -> {
                    doHideProgress();
                    if (success) {
                        // Inject the library into the game container
                        if (ApkEnv.getInstance().tryAddLoader(gamePackage)) {
                            toast("Injection Successful. Launching...");
                            launchGame();
                        } else {
                            toast("Injection Failed!");
                        }
                    } else {
                        toast("Auto Mode Download Failed: " + message);
                    }
                });
            }
            @Override
            public void onProgress(int progress) {
                runOnUiThread(() -> {
                    if (getProgresBar() != null) {
                        getProgresBar().setIndeterminate(false);
                        getProgresBar().setProgress(progress);
                    }
                });
            }
        });
    }

    public void startPatcher() {
        try {
            loadAssets();
            // Floating overlay disabled: FloatLogo service is not started.
            isServiceRunning = false;
            FPrefs.with(this).writeBoolean("service_running", false);
            updateMainGameUI();
            Toast.makeText(this, "Service Started!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Service Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ===== LAUNCH GAME =====
    private void launchGame() {
        try {
            if (ApkEnv.getInstance().isInstalled(gamePackage)) {
                ApkEnv.getInstance().launchApk(gamePackage);
                Toast.makeText(this, "Launching Game...", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Game not installed in BlackBox!", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Launch Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    // ===== TIMER =====
    private void updateExpiryDisplay() {
        long currentTime = System.currentTimeMillis();
        long diff = expiryTimeMillis - currentTime;

        if (diff <= 0) {
            if (days != null) days.setText("00");
            if (hours != null) hours.setText("00");
            if (minutes != null) minutes.setText("00");
            if (seconds != null) seconds.setText("00");
            return;
        }

        long d = TimeUnit.MILLISECONDS.toDays(diff);
        long h = TimeUnit.MILLISECONDS.toHours(diff) - TimeUnit.DAYS.toHours(d);
        long m = TimeUnit.MILLISECONDS.toMinutes(diff) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(diff));
        long s = TimeUnit.MILLISECONDS.toSeconds(diff) - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(diff));

        if (days != null) days.setText(String.format("%02d", d));
        if (hours != null) hours.setText(String.format("%02d", h));
        if (minutes != null) minutes.setText(String.format("%02d", m));
        if (seconds != null) seconds.setText(String.format("%02d", s));
    }

    private void startTimer() {
        if (timerRunnable != null) timerHandler.removeCallbacks(timerRunnable);
        
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                updateExpiryDisplay();
                timerHandler.postDelayed(this, 1000);
            }
        };
        timerHandler.postDelayed(timerRunnable, 0);
    }

    // ===== VERIFY DIALOG =====
    private void createVerifyDialog() {
        verifyDialog = new Dialog(this, android.R.style.Theme_Translucent_NoTitleBar);
        verifyDialog.setCancelable(false);
        
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_verify, null);
        verifyDialog.setContentView(dialogView);
        
        progressBarView = dialogView.findViewById(R.id.progressBar);
        tvStatusText = dialogView.findViewById(R.id.tvStatusText);
        tvStatusDetail = dialogView.findViewById(R.id.tvStatusDetail);
        tvKeyText = dialogView.findViewById(R.id.tvKeyText);
        tvErrorMessage = dialogView.findViewById(R.id.tvErrorMessage);
        
        progressContainer = dialogView.findViewById(R.id.progressContainer);
        statusContainer = dialogView.findViewById(R.id.statusContainer);
        deviceInfoContainer = dialogView.findViewById(R.id.deviceInfoContainer);
        keyContainer = dialogView.findViewById(R.id.keyContainer);
        successScreen = dialogView.findViewById(R.id.successScreen);
        errorScreen = dialogView.findViewById(R.id.errorScreen);
        
        tvDeviceInfo = dialogView.findViewById(R.id.tvDeviceInfo);
        tvAndroidInfo = dialogView.findViewById(R.id.tvAndroidInfo);
        tvSDKInfo = dialogView.findViewById(R.id.tvSDKInfo);
        
        Window window = verifyDialog.getWindow();
        if (window != null) {
            window.setLayout(dp(180), dp(220));
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        verifyDialog.show();
        startVerification();
    }
    
    private void startVerification() {
        tvStatusText.setText("VERIFYING");
        tvStatusDetail.setText("Please wait...");
        loadDeviceInfo();
        
        String key = getUserKey();
        tvKeyText.setText(key);
        
        if (key == null || key.equals("INVALID") || key.equals("null") || key.trim().isEmpty()) {
            showError("INVALID LICENCE KEY");
            return;
        }
        
        new Thread(() -> {
            try {
                String result = Login.check(MainActivity.this, key);
                runOnUiThread(() -> {
                    if (result.equals("OK")) {
                        SharedPreferences prefs = getSharedPreferences("auth_pref", MODE_PRIVATE);
                        prefs.edit().putBoolean("verified", true).putLong("expiry", Login.getExpiryTimestamp()).apply();
                        tvStatusText.setText("VERIFIED");
                        tvStatusDetail.setText("Licence confirmed");
                        handler.postDelayed(() -> {
                            progressContainer.setVisibility(View.GONE);
                            statusContainer.setVisibility(View.GONE);
                            deviceInfoContainer.setVisibility(View.GONE);
                            keyContainer.setVisibility(View.GONE);
                            successScreen.setVisibility(View.VISIBLE);
                            handler.postDelayed(() -> {
                                verifyDialog.dismiss();
                                onVerified();
                            }, 800);
                        }, 500);
                    } else {
                        showError(result);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError(e.getMessage()));
            }
        }).start();
    }
    
    private void onVerified() {
        if (verified) return;
        verified = true;
        
        // Fix: Update timer immediately after verification
        long expiry = Login.getExpiryTimestamp();
        if (expiry > 0) {
            expiryTimeMillis = expiry * 1000;
            runOnUiThread(() -> {
                updateExpiryDisplay();
                startTimer();
            });
        }

        doInitRecycler();
    }
    
    private void showError(String message) {
        runOnUiThread(() -> {
            progressContainer.setVisibility(View.GONE);
            statusContainer.setVisibility(View.GONE);
            deviceInfoContainer.setVisibility(View.GONE);
            keyContainer.setVisibility(View.GONE);
            tvErrorMessage.setText(message);
            errorScreen.setVisibility(View.VISIBLE);
            handler.postDelayed(() -> {
                verifyDialog.dismiss();
                MainActivity.this.toast("Licence Error: " + message);
                LoginActivity.goLogin(MainActivity.this);
                finish();
            }, 2000);
        });
    }
    
    private void loadDeviceInfo() {
        runOnUiThread(() -> {
            String deviceName = getDeviceName();
            String androidVer = getAndroidVersion();
            
            if (tvDeviceInfo != null) tvDeviceInfo.setText("Device: " + deviceName);
            if (tvAndroidInfo != null) tvAndroidInfo.setText("Android: " + androidVer);
            if (tvSDKInfo != null) tvSDKInfo.setText("SDK: " + getSDKVersion());
            
            // Update the main dashboard text view
            if (deviceInfoTextView != null) {
                deviceInfoTextView.setText(deviceName + " | Android " + androidVer + "\n(Tap to Copy HWID)");
            }
        });
    }
    
    private String getUserKey() {
        if (LoginActivity.USERKEY != null && !LoginActivity.USERKEY.isEmpty()) {
            return LoginActivity.USERKEY;
        }
        return "INVALID";
    }
    
    private String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        } else {
            return capitalize(manufacturer) + " " + model;
        }
    }

    private String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }
    
    private String getAndroidVersion() {
        return Build.VERSION.RELEASE;
    }
    
    private String getSDKVersion() {
        return String.valueOf(Build.VERSION.SDK_INT);
    }
    
    private int dp(int d) {
        return Math.round(d * getResources().getDisplayMetrics().density);
    }

    public LinearProgressIndicator getProgresBar() {
        return progress;
    }
    
    public void doShowProgress(boolean indeterminate) {
        if (progress == null) return;
        runOnUiThread(() -> {
            progress.setVisibility(View.VISIBLE);
            progress.setIndeterminate(indeterminate);
        });
    }
    
    public void doHideProgress() {
        if (progress == null) return;
        runOnUiThread(() -> {
            progress.setVisibility(View.GONE);
        });
    }
    
    public void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
    
    public void launchSplash(String packageName) {
        launchGame();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
        if (verifyDialog != null && verifyDialog.isShowing()) {
            verifyDialog.dismiss();
        }
        instance = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 123) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startPatcherAndLaunch();
            } else {
                Toast.makeText(this, "Overlay permission required!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}