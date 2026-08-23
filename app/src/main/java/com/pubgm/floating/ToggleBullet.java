package com.pubgm.floating;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.pubgm.R;
import com.pubgm.utils.FLog;
import com.pubgm.utils.FPrefs;

public class ToggleBullet extends Service {
    
    private boolean checkStatus;
    private View mainView;
    private RelativeLayout miniFloatView;
    private WindowManager windowManager;
	private LayoutParams paramsView;
    
    public native void ToggleBullet(boolean value);

    static {
        try {
            System.loadLibrary("client");
        } catch(UnsatisfiedLinkError w) {
            FLog.error(w.getMessage());
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ShowMainView();
    }
    
    public FPrefs getPref() {
        return FPrefs.with(this);
    }

    private void ShowMainView() {
        mainView = LayoutInflater.from(this).inflate(R.layout.toggle_aimbullet, null);
        paramsView = getParaams();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.addView(mainView, paramsView);
        InitShowMainView();
    }

    private LayoutParams getParaams() {
        final LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT, getLayoutType(), LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
        if (getPref().readBoolean("anti_recorder")) {
			HideRecorder.setFakeRecorderWindowLayoutParams(params);
        }
        params.gravity = Gravity.CENTER | Gravity.CENTER;
        params.x = 0;
        params.y = 0;
        return params;
    }

    private static int getLayoutType() {
        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            LAYOUT_FLAG = LayoutParams.TYPE_PHONE;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            LAYOUT_FLAG = LayoutParams.TYPE_TOAST;
        } else {
            LAYOUT_FLAG = LayoutParams.TYPE_SYSTEM_ALERT;
        }
        return LAYOUT_FLAG;
    }

    private void InitShowMainView() {
        miniFloatView = mainView.findViewById(R.id.miniFloatMenu);
        RelativeLayout layoutView = mainView.findViewById(R.id.layout_icon_control_aim);
        final ImageView myImageView = mainView.findViewById(R.id.imageview_aim);

        layoutView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX;
                private int initialY;
                private float initialTouchX;
                private float initialTouchY;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            initialX = paramsView.x;
                            initialY = paramsView.y;
                            initialTouchX = event.getRawX();
                            initialTouchY = event.getRawY();
                            return true;

                        case MotionEvent.ACTION_UP:
                            int Xdiff = (int) (event.getRawX() - initialTouchX);
                            int Ydiff = (int) (event.getRawY() - initialTouchY);
                            if (Xdiff < 5 && Ydiff < 5) {
                                if (miniFloatView.getVisibility() == View.VISIBLE) {
                                    if (!checkStatus) {
                                        checkStatus = true;
                                        ToggleBullet(true);
                                        myImageView.animate().rotationBy(0).rotation(-45);
                                        myImageView.setImageResource(R.drawable.b2);

                                    } else {
                                        checkStatus = false;
                                        ToggleBullet(false);
                                        myImageView.animate().rotationBy(-45).rotation(0);
                                        myImageView.setImageResource(R.drawable.b1);

                                    }
                                }
                            }
                            return true;

                        case MotionEvent.ACTION_MOVE:
                            paramsView.x = initialX + (int) (event.getRawX() - initialTouchX);
                            paramsView.y = initialY + (int) (event.getRawY() - initialTouchY);
                            windowManager.updateViewLayout(mainView, paramsView);
                            return true;
                    }
                    return false;
                }
            });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        checkStatus = false;
        ToggleBullet(false);
        if (mainView != null)
            windowManager.removeView(mainView);
    }
    
}

