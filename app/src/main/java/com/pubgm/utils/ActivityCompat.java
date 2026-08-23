package com.pubgm.utils;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import com.pubgm.Config;
import com.pubgm.BoxApplication;
import com.pubgm.activity.MainActivity;
import com.pubgm.libhelper.FileHelper;
import com.pubgm.libhelper.Loader;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import java.io.IOException;
import android.app.ProgressDialog;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.pubgm.R;
import java.io.File;
import java.io.InputStream;
import java.io.FileOutputStream;
import android.content.Context;
import com.pubgm.libhelper.ApkEnv;
import static com.pubgm.Config.GAME_LIST_PKG;
import static com.pubgm.Config.GAME_LIST_ICON;

public class ActivityCompat extends AppCompatActivity {
    private static ActivityCompat activityCompat;
    public static int REQUEST_OVERLAY_PERMISSION = 5469;
    public static int PERMISSION_REQUEST_STORAGE = 100;
    public static int REQUEST_MANAGE_UNKNOWN_APP_SOURCES = 200;
    public boolean isLogin = false;
    public FPrefs prefs;
    private BottomSheetDialog bottomSheetDialog;

    public static String gamename;
    public static String name;
    public static int version;
    public static String url;
    private static ExecutorService executorService = Executors.newSingleThreadExecutor();

    public static ActivityCompat getActivityCompat() {
        return activityCompat;
    }

    public FPrefs getPref() {
        return FPrefs.with(this);
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        activityCompat = this;
        super.onCreate(savedInstanceState);
        setNavBar(R.color.background);
        prefs = getPref();
        ManageFiles();

    }

    public void setNavBar(int color){
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().setStatusBarColor(ContextCompat.getColor(this,color));
    }

    public void restartApp(String clazz) {
        Intent lauchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        lauchIntent.addFlags(335577088);
        lauchIntent.putExtra("restartApp", clazz);
        startActivity(lauchIntent);
        Runtime.getRuntime().exit(0);
    }
    
    public void toast(CharSequence msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    public void RestartAppp() {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
        finish();
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    public void takeFilePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        } else {
            androidx.core.app.ActivityCompat.requestPermissions(this,new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.MANAGE_EXTERNAL_STORAGE},PERMISSION_REQUEST_STORAGE);
        }
    }

    public boolean isPermissionGaranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public void InstllUnknownApp() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_MANAGE_UNKNOWN_APP_SOURCES);
            } else {
                if (!isPermissionGaranted()) {
                    takeFilePermissions();
                }
            }
        }
    }

    public void OverlayPermision() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
            } else {
                InstllUnknownApp();
            }
        }
    }

    public void ManageFiles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},PERMISSION_REQUEST_STORAGE);
            } else {
                OverlayPermision();
            }
        }
    }

    protected AndroidDeferredManager defer() {
        return UiKit.defer();
    }

    private long backPressedTime = 0; 

    @Override
    public void onBackPressed() {
        if (isLogin) {
            long t = System.currentTimeMillis();
            if (t - backPressedTime > 2000) {    // 2 secs
                backPressedTime = t;
                toast("Press back again to exit");
            } else {
                super.onBackPressed();
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUI();
        } else {
            showSystemUI();
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
    
    private void showSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    private void doActionAnimation(CircularProgressIndicator progressIndicator, TextView txt, String pkg) {
        txt.setText("Starting Client " + pkg + " ...");
        progressIndicator.setVisibility(View.VISIBLE);
        progressIndicator.setIndeterminate(true);
    }

    public void launch(AlertDialog dialog, String pkg) {
        UiKit.defer().when(() -> {
            long startTime = System.currentTimeMillis();
            dialog.dismiss();
            long elapsedTime = System.currentTimeMillis() - startTime;
            long delta = 500L - elapsedTime;
            if (delta > 0) {
                UiKit.sleep(delta);
            }
        }).done((ree) -> {
            ApkEnv.getInstance().launchApk(pkg);
        });
    }
    
    public void launchSplash(String pkg) {
        try {
            View view = getLayoutInflater().inflate(R.layout.launcher, null);
            CardView cv = view.findViewById(R.id.cv_lauch);
            ImageView appIcon = view.findViewById(R.id.app_icon);
            TextView gameName = view.findViewById(R.id.game_name);
            TextView packageInfo = view.findViewById(R.id.package_info);
            TextView loadingText = view.findViewById(R.id.loading_text);
            CircularProgressIndicator progressIndicator = view.findViewById(R.id.progress_indicator);
            TextView progressPercent = view.findViewById(R.id.progress_percent);
            TextView statusMessage = view.findViewById(R.id.status_message);
            ImageView glowEffect = view.findViewById(R.id.glow_effect);
            
            try {
                ApplicationInfo appInfo = getPackageManager().getApplicationInfo(pkg, 0);
                Drawable icon = getPackageManager().getApplicationIcon(appInfo);
                String appName = getPackageManager().getApplicationLabel(appInfo).toString();
                
                appIcon.setImageDrawable(icon);
                gameName.setText(appName);
                packageInfo.setText(pkg);
                
            } catch (PackageManager.NameNotFoundException e) {
                appIcon.setImageResource(R.drawable.ic_launcher);
                gameName.setText("Game Client");
                packageInfo.setText(pkg);
            }
            
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setCancelable(false).setView(view).setBackground(getResources().getDrawable(R.drawable.background_trans));
            
            AlertDialog dialog = builder.create();
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setDimAmount(0.8f);
            dialog.show();
            
            startGlowAnimation(glowEffect);
            startProgressAnimation(progressIndicator, progressPercent, statusMessage, loadingText, dialog, pkg);
            
        } catch (Exception err) {
            FLog.error(err.getMessage() != null ? err.getMessage() : "Unknown error");
            ApkEnv.getInstance().launchApk(pkg);
        }
    }

    private void startGlowAnimation(ImageView glowEffect) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(glowEffect, "scaleX", 1f, 1.2f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(glowEffect, "scaleY", 1f, 1.2f, 1f);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(glowEffect, "alpha", 0.3f, 0.7f, 0.3f);
        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleX.setRepeatMode(ValueAnimator.RESTART);
        scaleX.setDuration(2000);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatMode(ValueAnimator.RESTART);
        scaleY.setDuration(2000);
        alpha.setRepeatCount(ValueAnimator.INFINITE);
        alpha.setRepeatMode(ValueAnimator.RESTART);
        alpha.setDuration(2000);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(scaleX, scaleY, alpha);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.start();
    }

    private void startProgressAnimation(CircularProgressIndicator progressIndicator,TextView progressPercent, TextView statusMessage,TextView loadingText,AlertDialog dialog,String pkg) {
        progressIndicator.setIndeterminate(false);
        progressIndicator.setMax(100);
        progressIndicator.setProgress(0);
        ValueAnimator animator = ValueAnimator.ofInt(0, 100);
        animator.setDuration(2000); // 2 seconds
        animator.addUpdateListener(animation -> {
            int progress = (int) animation.getAnimatedValue();
            progressIndicator.setProgress(progress);
            progressPercent.setText(progress + "%");
            if (progress < 25) {
                statusMessage.setText("Initializing environment...");
                loadingText.setText("Preparing launch...");
            } else if (progress < 50) {
                statusMessage.setText("Loading resources...");
                loadingText.setText("Loading...");
            } else if (progress < 75) {
                statusMessage.setText("Optimizing performance...");
                loadingText.setText("Optimizing...");
            } else {
                statusMessage.setText("Launching game...");
                loadingText.setText("Almost ready!");
            }
        });
        
        animator.addListener(new android.animation.Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(android.animation.Animator animation) { }
            
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                launchGame(dialog, pkg);
            }
            
            @Override
            public void onAnimationCancel(android.animation.Animator animation) {
                launchGame(dialog, pkg);
            }
            
            @Override
            public void onAnimationRepeat(android.animation.Animator animation) { }
        });
        
        animator.start();
    }

    private void launchGame(AlertDialog dialog, String pkg) {
        dialog.dismiss();
        toast("Launching game...");
        new Handler().postDelayed(() -> {
            ApkEnv.getInstance().launchApk(pkg);
        }, 300);
    }
    
    public void launch(AlertDialog dialog, String pkg, boolean usePremium) {
        if (usePremium) {
            launchSplash(pkg);
        } else {
            UiKit.defer().when(() -> {
                long startTime = System.currentTimeMillis();
                dialog.dismiss();
                long elapsedTime = System.currentTimeMillis() - startTime;
                long delta = 500L - elapsedTime;
                if (delta > 0) {
                    UiKit.sleep(delta);
                }
            }).done((ree) -> {
                ApkEnv.getInstance().launchApk(pkg);
            });
        }
    }
}