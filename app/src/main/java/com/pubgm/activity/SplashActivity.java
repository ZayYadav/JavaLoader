package com.pubgm.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.TextView;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.WindowManager;
import android.graphics.Color;

import com.pubgm.R;
import com.pubgm.utils.ActivityCompat;
import com.pubgm.utils.FLog;
import com.pubgm.utils.FPrefs;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import org.lsposed.lsparanoid.Obfuscate;

@Obfuscate
public class SplashActivity extends ActivityCompat {

    private static final long LOGO_ANIMATION_DURATION = 1500;
    private static final long DOT_ANIMATION_INTERVAL = 300;
    private static final long SPLASH_DELAY_MS = 2000;
    private static final float ALPHA_HIGH = 1.0f;
    private static final float ALPHA_LOW = 0.3f;

    private CircularProgressIndicator progressIndicator;
    private TextView descTitle;
    private TextView bottomStatus;
    private View dot1, dot2, dot3;
    
    private Handler animationHandler;
    private Handler loadingHandler;
    private Runnable dotAnimationRunnable;
    private Runnable loadingRunnable;
    
    private boolean isDestroyed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Transparent Status Bar
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        
        setContentView(R.layout.activity_splash);
        initializeViews();
        hideSystemUI();
        startAnimations();
        startLogoAnimation();
        startDotAnimation();
        startLoadingProcess();
    }

    private void startAnimations() {
        View blobPurple = findViewById(R.id.blob_purple);
        View blobPink = findViewById(R.id.blob_pink);
        View blobBlue = findViewById(R.id.blob_blue);

        if (blobPurple != null) animateBlob(blobPurple, 4000, 20f, -30f);
        if (blobPink != null) animateBlob(blobPink, 5000, -25f, 40f);
        if (blobBlue != null) animateBlob(blobBlue, 4500, 30f, 25f);
    }

    private void animateBlob(View view, int duration, float tx, float ty) {
        ObjectAnimator animator = ObjectAnimator.ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, tx, 0f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, ty, 0f)
        );
        animator.setDuration(duration);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.start();
    }

    private void initializeViews() {
        progressIndicator = findViewById(R.id.animationView);
        descTitle = findViewById(R.id.descTitle);
        bottomStatus = findViewById(R.id.bottomStatus);
        dot1 = findViewById(R.id.dot1);
        dot2 = findViewById(R.id.dot2);
        dot3 = findViewById(R.id.dot3);
    }

    private void startLogoAnimation() {
        try {
            ScaleAnimation scaleAnimation = new ScaleAnimation(0.8f, 1.0f, 0.8f, 1.0f,Animation.RELATIVE_TO_SELF, 0.5f,Animation.RELATIVE_TO_SELF, 0.5f);
            scaleAnimation.setDuration(LOGO_ANIMATION_DURATION);
            scaleAnimation.setRepeatCount(Animation.INFINITE);
            scaleAnimation.setRepeatMode(Animation.REVERSE);
            progressIndicator.startAnimation(scaleAnimation);
        } catch (Exception e) {
            FLog.error("Animation error: " + e.getMessage());
        }
    }

    private void startDotAnimation() {
        animationHandler = new Handler(Looper.getMainLooper());
        dotAnimationRunnable = new Runnable() {
            private int dotState = 0;

            @Override
            public void run() {
                if (isDestroyed) return;
                dot1.setAlpha(ALPHA_LOW);
                dot2.setAlpha(ALPHA_LOW);
                dot3.setAlpha(ALPHA_LOW);
                
                switch (dotState % 3) {
                    case 0:
                        dot1.setAlpha(ALPHA_HIGH);
                        break;
                    case 1:
                        dot2.setAlpha(ALPHA_HIGH);
                        break;
                    case 2:
                        dot3.setAlpha(ALPHA_HIGH);
                        break;
                }
                dotState++;
                if (!isDestroyed) {
                    animationHandler.postDelayed(this, DOT_ANIMATION_INTERVAL);
                }
            }
        };
        
        animationHandler.post(dotAnimationRunnable);
    }

    private void startLoadingProcess() {
        descTitle.setText("Initializing Application...");
        
        loadingHandler = new Handler(Looper.getMainLooper());
        loadingRunnable = () -> {
            if (!isDestroyed) {
                LoginActivity.goLogin(SplashActivity.this);
                finish();
            }
        };
        
        loadingHandler.postDelayed(loadingRunnable, SPLASH_DELAY_MS);
    }

    @Override
    protected void onDestroy() {
        isDestroyed = true;
        cleanupHandlers();
        super.onDestroy();
    }

    private void cleanupHandlers() {
        if (animationHandler != null && dotAnimationRunnable != null) {
            animationHandler.removeCallbacks(dotAnimationRunnable);
            animationHandler = null;
            dotAnimationRunnable = null;
        }

        if (loadingHandler != null && loadingRunnable != null) {
            loadingHandler.removeCallbacks(loadingRunnable);
            loadingHandler = null;
            loadingRunnable = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (!isLogin) {
            super.onBackPressed();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !isDestroyed) {
            hideSystemUI();
        }
    }

    private void hideSystemUI() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    @Override
    public void toast(CharSequence msg) {
        super.toast(msg);
    }
}