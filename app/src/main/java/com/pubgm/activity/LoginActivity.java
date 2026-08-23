package com.pubgm.activity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipboardManager;
import android.view.animation.DecelerateInterpolator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pubgm.Login;
import com.pubgm.R;
import com.pubgm.utils.ActivityCompat;
import com.pubgm.utils.FPrefs;
import com.pubgm.utils.SecurityUtils;

import android.speech.tts.TextToSpeech;
import java.util.Locale;

import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class LoginActivity extends ActivityCompat {

    public static String USERKEY;
    private TextToSpeech tts;
    private static final int REQ_OVERLAY = 1001;
    private static final int REQ_BATTERY = 1002;
    private static final int REQ_STORAGE = 1003;
    private static final int REQ_ALL_FILES = 1005;
    private static final int REQ_INSTALL_APPS = 1006;
    
    // ===== XML KE HISAB SE VIEWS =====
    private EditText usernameInput;      // R.id.username
    private Button loginButton;          // R.id.login
    private ImageView logoImage;         // R.id.ic_logo (optional)
    
    private AlertDialog customProgressDialog;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public static void goLogin(Context context) {
        Intent intent = new Intent(context, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        initTts();
        initViews();
        loadSavedKey();
        setupListeners();

        // Start security check
        if (!SecurityUtils.checkIntegrity(this)) {
            Toast.makeText(this, "Security Violation: Tampering Detected!", Toast.LENGTH_LONG).show();
            mainHandler.postDelayed(this::finishAffinity, 2000);
            return;
        }

        startAnimations();
        
        // Start permission sequence
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAndRequestPermissions, 1500);
    }

    private void checkAndRequestPermissions() {
        // 1. Basic Storage (WRITE/READ)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE, 
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                }, REQ_STORAGE);
                return;
            }
        }

        // 2. All Files Access (Android 11+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showPermissionDialog("All Files Access", 
                    "Allow 'All Files Access' to manage OBB and Game data. Click ALLOW to go to settings.",
                    () -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, REQ_ALL_FILES);
                        } catch (Exception e) {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                            startActivityForResult(intent, REQ_ALL_FILES);
                        }
                    });
                return;
            }
        }

        // 3. Install Unknown Apps
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                showPermissionDialog("Install Unknown Apps", 
                    "Allow this app to install game packages. Click ALLOW to enable.",
                    () -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQ_INSTALL_APPS);
                    });
                return;
            }
        }

        // 4. Overlay Permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                showPermissionDialog("Overlay Permission", 
                    "Overlay permission is needed for the cheat menu. Click ALLOW to enable.",
                    () -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQ_OVERLAY);
                    });
                return;
            }
        }

        // 5. Battery Optimization
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                showPermissionDialog("Battery Optimization", 
                    "Disable battery optimization to avoid ESP flickering. Click ALLOW to disable.",
                    () -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, REQ_BATTERY);
                        } catch (Exception e) {
                            checkAndRequestPermissions(); 
                        }
                    });
                return;
            }
        }
        
        // 6. Notification (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1004);
            }
        }
    }

    private void showPermissionDialog(String title, String msg, Runnable onAllow) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("ALLOW", (d, w) -> onAllow.run())
                .setNegativeButton("EXIT", (d, w) -> finishAffinity())
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Continue to next permission in chain
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAndRequestPermissions, 500);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // Continue to next permission in chain
        checkAndRequestPermissions();
    }

    private void startAnimations() {
        // Background Blobs floating animation
        animateBlob(findViewById(R.id.blob_purple), 10000, 80f, -80f);
        animateBlob(findViewById(R.id.blob_pink), 15000, -100f, 100f);
        animateBlob(findViewById(R.id.blob_blue), 12000, 60f, 120f);

        // Entrance animation for the card
        View card = findViewById(R.id.login_card);
        card.setAlpha(0f);
        card.setTranslationY(200f);
        card.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(1200)
                .setInterpolator(new DecelerateInterpolator())
                .start();
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

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status != TextToSpeech.ERROR) {
                tts.setLanguage(Locale.US);
                tts.setPitch(0.7f); // Lower pitch for robotic feel
                tts.setSpeechRate(0.9f); // Slightly slower
            }
        });
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "login_success");
        }
    }

    private void initViews() {
        usernameInput = findViewById(R.id.username);
        loginButton = findViewById(R.id.login);
        
        // Password input type
        usernameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | 
                                   android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    private void loadSavedKey() {
        String savedKey = FPrefs.with(this).read("USER", "");
        if (!savedKey.isEmpty()) {
            usernameInput.setText(savedKey);
        }
    }

    private void setupListeners() {
        loginButton.setOnClickListener(v -> handleLogin());
        
        findViewById(R.id.btn_paste).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip() && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).getText();
                if (text != null) {
                    usernameInput.setText(text.toString());
                    Toast.makeText(this, "Key Pasted!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleLogin() {
        String key = usernameInput.getText().toString().trim();
        
        if (key.isEmpty()) {
            Toast.makeText(this, "Enter license key", Toast.LENGTH_SHORT).show();
            usernameInput.setError("License key required");
            return;
        }
        
        if (key.length() < 6) {
            Toast.makeText(this, "Invalid license key", Toast.LENGTH_SHORT).show();
            usernameInput.setError("Invalid key");
            return;
        }
        
        performLogin(key);
    }

    private void performLogin(String userKey) {
        showProgress();
        
        new Thread(() -> {
            String result = Login.check(LoginActivity.this, userKey);
            
            runOnUiThread(() -> {
                dismissProgress();
                
                if ("OK".equals(result)) {
                    // Save authentication
                    SharedPreferences prefs = getSharedPreferences("auth_pref", MODE_PRIVATE);
                    prefs.edit().putBoolean("verified", true)
                         .putLong("expiry", Login.getExpiryTimestamp())
                         .apply();
                    
                    FPrefs.with(LoginActivity.this).write("USER", userKey);
                    USERKEY = userKey;
                    
                    // Sync Auth Token to Native for immediate ESP activation
                    try {
                        Login.setAuthToken(userKey);
                    } catch (UnsatisfiedLinkError | Exception e) {
                        e.printStackTrace();
                    }

                    speak("Access Granted. Welcome Commander");
                    Toast.makeText(LoginActivity.this, "Login Success!", Toast.LENGTH_SHORT).show();
                    
                    mainHandler.postDelayed(() -> {
                        MainActivity.goMain(LoginActivity.this);
                        finish();
                    }, 300);
                    
                } else {
                    showErrorDialog("Login Failed", result);
                }
            });
        }).start();
    }

    private void showProgress() {
        View progressView = getLayoutInflater().inflate(R.layout.dialog_progress, null);
        
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this)
                .setView(progressView)
                .setCancelable(false);
        
        customProgressDialog = builder.create();
        if (customProgressDialog.getWindow() != null) {
            customProgressDialog.getWindow().setBackgroundDrawable(
                new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            );
        }
        customProgressDialog.show();
    }

    private void dismissProgress() {
        if (customProgressDialog != null && customProgressDialog.isShowing()) {
            customProgressDialog.dismiss();
            customProgressDialog = null;
        }
    }

    private void showErrorDialog(String title, String msg) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    @Override
    public void onBackPressed() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Exit")
                .setMessage("Are you sure you want to exit?")
                .setPositiveButton("Yes", (d, w) -> finishAffinity())
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
        dismissProgress();
        mainHandler.removeCallbacksAndMessages(null);
    }
}