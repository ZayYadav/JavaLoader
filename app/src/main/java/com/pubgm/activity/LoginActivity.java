package com.pubgm.activity;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pubgm.Login;
import com.pubgm.R;
import com.pubgm.security.ParallaxBhaiServerSeAaya;
import com.pubgm.security.ParallaxKaBhaiJanguHaii;
import com.pubgm.utils.ActivityCompat;
import com.pubgm.utils.FPrefs;

import java.util.Locale;

public class LoginActivity extends ActivityCompat {

    public static String USERKEY;
    private TextToSpeech tts;
    private static final int REQ_OVERLAY = 1001;
    private static final int REQ_BATTERY = 1002;
    private static final int REQ_STORAGE = 1003;
    private static final int REQ_ALL_FILES = 1005;
    private static final int REQ_INSTALL_APPS = 1006;

    private EditText usernameInput;
    private Button loginButton;
    private AlertDialog customProgressDialog;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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

        if (!ParallaxKaBhaiJanguHaii.verify(this)) {
            Toast.makeText(this, "Security Violation: APK signature mismatch", Toast.LENGTH_LONG).show();
            mainHandler.postDelayed(this::finishAffinity, 1500);
            return;
        }

        startAnimations();
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAndRequestPermissions, 1500);
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE
                }, REQ_STORAGE);
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            showPermissionDialog("All Files Access",
                    "Allow All Files Access for app-managed game files. Click ALLOW to continue.",
                    () -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivityForResult(intent, REQ_ALL_FILES);
                        } catch (Exception error) {
                            startActivityForResult(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION), REQ_ALL_FILES);
                        }
                    });
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            showPermissionDialog("Install Unknown Apps",
                    "Allow this app to open package installation when required.",
                    () -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQ_INSTALL_APPS);
                    });
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            showPermissionDialog("Overlay Permission",
                    "Overlay permission is required by the existing floating UI.",
                    () -> {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivityForResult(intent, REQ_OVERLAY);
                    });
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                showPermissionDialog("Battery Optimization",
                        "Disable battery optimization if you want the existing foreground UI to stay active.",
                        () -> {
                            try {
                                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivityForResult(intent, REQ_BATTERY);
                            } catch (Exception ignored) {
                                checkAndRequestPermissions();
                            }
                        });
                return;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1004);
        }
    }

    private void showPermissionDialog(String title, String msg, Runnable onAllow) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(msg)
                .setCancelable(false)
                .setPositiveButton("ALLOW", (dialog, which) -> onAllow.run())
                .setNegativeButton("EXIT", (dialog, which) -> finishAffinity())
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        new Handler(Looper.getMainLooper()).postDelayed(this::checkAndRequestPermissions, 500);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        checkAndRequestPermissions();
    }

    private void startAnimations() {
        animateBlob(findViewById(R.id.blob_purple), 10000, 80f, -80f);
        animateBlob(findViewById(R.id.blob_pink), 15000, -100f, 100f);
        animateBlob(findViewById(R.id.blob_blue), 12000, 60f, 120f);

        View card = findViewById(R.id.login_card);
        card.setAlpha(0f);
        card.setTranslationY(200f);
        card.animate().alpha(1f).translationY(0f).setDuration(1200)
                .setInterpolator(new DecelerateInterpolator()).start();
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
                tts.setPitch(0.7f);
                tts.setSpeechRate(0.9f);
            }
        });
    }

    private void speak(String text) {
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "login_success");
    }

    private void initViews() {
        usernameInput = findViewById(R.id.username);
        loginButton = findViewById(R.id.login);
        usernameInput.setFilterTouchesWhenObscured(true);
        usernameInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
    }

    private void loadSavedKey() {
        String savedKey = FPrefs.with(this).read("USER", "");
        if (!savedKey.isEmpty()) usernameInput.setText(savedKey);
    }

    private void setupListeners() {
        loginButton.setOnClickListener(v -> handleLogin());
        findViewById(R.id.btn_paste).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null && clipboard.hasPrimaryClip()
                    && clipboard.getPrimaryClip() != null
                    && clipboard.getPrimaryClip().getItemCount() > 0) {
                CharSequence text = clipboard.getPrimaryClip().getItemAt(0).coerceToText(this);
                if (text != null) {
                    usernameInput.setText(text.toString().trim());
                    Toast.makeText(this, "Key Pasted!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleLogin() {
        String key = ParallaxBhaiServerSeAaya.normalizeActivationKey(usernameInput.getText().toString());
        if (!ParallaxBhaiServerSeAaya.isSupportedActivationKey(key)) {
            Toast.makeText(this, "Invalid license key", Toast.LENGTH_SHORT).show();
            usernameInput.setError("Use a valid key");
            return;
        }
        usernameInput.setText(key);
        performLogin(key);
    }

    private void performLogin(String userKey) {
        showProgress();
        new Thread(() -> {
            String result = Login.check(LoginActivity.this, userKey);
            runOnUiThread(() -> {
                dismissProgress();
                if ("OK".equals(result)) {
                    FPrefs.with(LoginActivity.this).write("USER", userKey);
                    USERKEY = userKey;
                    speak("Access Granted");
                    Toast.makeText(LoginActivity.this, "Login Success!", Toast.LENGTH_SHORT).show();
                    mainHandler.postDelayed(() -> {
                        MainActivity.goMain(LoginActivity.this);
                        finish();
                    }, 300);
                } else {
                    showErrorDialog("Login Failed", result);
                }
            });
        }, "Parallax-License").start();
    }

    private void showProgress() {
        View progressView = getLayoutInflater().inflate(R.layout.dialog_progress, null);
        customProgressDialog = new MaterialAlertDialogBuilder(this)
                .setView(progressView)
                .setCancelable(false)
                .create();
        customProgressDialog.show();
        if (customProgressDialog.getWindow() != null) {
            customProgressDialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }

    private void dismissProgress() {
        if (customProgressDialog != null && customProgressDialog.isShowing()) customProgressDialog.dismiss();
        customProgressDialog = null;
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
                .setPositiveButton("Yes", (dialog, which) -> finishAffinity())
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        dismissProgress();
        mainHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
