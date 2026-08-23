package com.pubgm.floating;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Surface;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ESPView extends View implements Runnable {
    Paint mStrokePaint, mFilledPaint, mFillPaint, mNamePaint, mMDText, mTextPaint, mItemsPaint, mTextPainti, mLootBoxPaint, mVehiclesPaint, mFPSText;
    Thread mThread;
    public static long sleepTime = 16;
    private float mFPS = 0.0f;
    private float mFPSCounter = 0.0f;
    private long mFPSTime = 0;
    
    private boolean isRunning = true;

    public ESPView(Context context) {
        super(context, null, 0);
        InitializePaints();
        setFocusableInTouchMode(false);
        setBackgroundColor(Color.TRANSPARENT);
        mThread = new Thread(this);
        mThread.setPriority(Thread.MAX_PRIORITY);
        mThread.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (canvas == null) return;
        try {
            // Stability Fix: Removed rotation check that caused disappearing issues
            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            Overlay.DrawOn(this, canvas);
        } catch (Exception e) {
            // Silent catch to prevent crash during state changes
        }
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY);
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                long startTime = SystemClock.uptimeMillis();
                postInvalidate();
                long timeTaken = SystemClock.uptimeMillis() - startTime;
                long sleep = sleepTime - timeTaken;
                if (sleep > 0) {
                    Thread.sleep(sleep);
                } else {
                    Thread.sleep(5); // Minimum sleep to prevent CPU hogging
                }
            } catch (Exception e) {
                try { Thread.sleep(100); } catch (InterruptedException ex) {}
            }
        }
    }

    public void stopDrawing() {
        isRunning = false;
        if (mThread != null) {
            mThread.interrupt();
            try {
                mThread.join(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            mThread = null;
        }
    }

    public void InitializePaints() {
        mStrokePaint = new Paint(); mStrokePaint.setStyle(Paint.Style.STROKE); mStrokePaint.setAntiAlias(true);
        mFilledPaint = new Paint(); mFilledPaint.setStyle(Paint.Style.FILL); mFilledPaint.setAntiAlias(true);
        mFillPaint = new Paint(); mFillPaint.setStyle(Paint.Style.FILL); mFillPaint.setAntiAlias(true);
        mTextPaint = new Paint(); mTextPaint.setStyle(Paint.Style.FILL_AND_STROKE); mTextPaint.setAntiAlias(true); mTextPaint.setTextAlign(Paint.Align.CENTER);
        mNamePaint = new Paint(); mNamePaint.setStyle(Paint.Style.FILL); mNamePaint.setAntiAlias(true); mNamePaint.setTextAlign(Paint.Align.CENTER);
        mMDText = new Paint(); mMDText.setStyle(Paint.Style.FILL_AND_STROKE); mMDText.setAntiAlias(true); mMDText.setTextAlign(Paint.Align.CENTER);
        mItemsPaint = new Paint(); mItemsPaint.setAntiAlias(true); mItemsPaint.setTextAlign(Paint.Align.CENTER);
        mTextPainti = new Paint(); mTextPainti.setStyle(Paint.Style.FILL); mTextPainti.setAntiAlias(true); mTextPainti.setTextAlign(Paint.Align.CENTER);
        mLootBoxPaint = new Paint(); mLootBoxPaint.setAntiAlias(true); mLootBoxPaint.setTextAlign(Paint.Align.LEFT);
        mVehiclesPaint = new Paint(); mVehiclesPaint.setAntiAlias(true); mVehiclesPaint.setTextAlign(Paint.Align.CENTER);
        mFPSText = new Paint(); mFPSText.setStyle(Paint.Style.FILL); mFPSText.setAntiAlias(true); mFPSText.setTextAlign(Paint.Align.CENTER);
    }

    public void ClearCanvas(Canvas cvs) {
        cvs.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }

    public void DrawLine(Canvas cvs, int a, int r, int g, int b, float t, float fx, float fy, float tx, float ty) {
        mStrokePaint.setARGB(a, r, g, b); mStrokePaint.setStrokeWidth(t);
        cvs.drawLine(fx, fy, tx, ty, mStrokePaint);
    }

    public void DrawRect(Canvas cvs, int a, int r, int g, int b, float t, float x1, float y1, float x2, float y2) {
        mStrokePaint.setARGB(a, r, g, b); mStrokePaint.setStrokeWidth(t);
        cvs.drawRect(x1, y1, x2, y2, mStrokePaint);
    }

    public void DrawFilledRect(Canvas cvs, int a, int r, int g, int b, float x1, float y1, float x2, float y2) {
        mFillPaint.setARGB(a, r, g, b);
        cvs.drawRect(x1, y1, x2, y2, mFillPaint);
    }

    public void DrawCircle(Canvas cvs, int a, int r, int g, int b, float x, float y, float rd, float t) {
        mStrokePaint.setARGB(a, r, g, b); mStrokePaint.setStrokeWidth(t);
        cvs.drawCircle(x, y, rd, mStrokePaint);
    }

    public void DrawFilledCircle(Canvas cvs, int a, int r, int g, int b, float x, float y, float rd) {
        mFilledPaint.setARGB(a, r, g, b);
        cvs.drawCircle(x, y, rd, mFilledPaint);
    }

    public void DrawTriangle(Canvas cvs, int a, int r, int g, int b, float cx, float cy, float sz, float an) {
        mFilledPaint.setARGB(a, r, g, b);
        Path p = new Path();
        float tx = cx + (float) (Math.cos(Math.toRadians(an)) * sz);
        float ty = cy + (float) (Math.sin(Math.toRadians(an)) * sz);
        float lx = cx + (float) (Math.cos(Math.toRadians(an + 120)) * sz/2);
        float ly = cy + (float) (Math.sin(Math.toRadians(an + 120)) * sz/2);
        float rx = cx + (float) (Math.cos(Math.toRadians(an - 120)) * sz/2);
        float ry = cy + (float) (Math.sin(Math.toRadians(an - 120)) * sz/2);
        p.moveTo(tx, ty); p.lineTo(lx, ly); p.lineTo(rx, ry); p.close();
        cvs.drawPath(p, mFilledPaint);
    }

    public void DrawText(Canvas cvs, int a, int r, int g, int b, String txt, float x, float y, float sz) {
        if (txt == null) return;
        mTextPaint.setARGB(a, r, g, b); mTextPaint.setTextSize(sz);
        cvs.drawText(txt, x, y, mTextPaint);
    }

    public void DrawName(Canvas cvs, int a, int r, int g, int b, String txt, float x, float y, float sz) {
        if (txt == null) return;
        mNamePaint.setARGB(a, r, g, b); mNamePaint.setTextSize(sz);
        cvs.drawText(txt, x, y, mNamePaint);
    }

    public void DrawUserID(Canvas cvs, int a, int r, int g, int b, String txt, float x, float y, float sz) {
        if (txt == null) return;
        mTextPaint.setARGB(a, r, g, b); mTextPaint.setTextSize(sz);
        cvs.drawText("ID: " + txt, x, y, mTextPaint);
    }

    public void DrawTextName(Canvas cvs, int a, int r, int g, int b, String txt, float x, float y, float sz) {
        if (txt == null) return;
        mFPSText.setARGB(a, r, g, b); mFPSText.setTextSize(sz);
        if (SystemClock.uptimeMillis() - mFPSTime > 1000) {
            mFPSTime = SystemClock.uptimeMillis(); mFPS = mFPSCounter; mFPSCounter = 0f;
        } else { mFPSCounter++; }
        cvs.drawText(txt + " | FPS: " + (int)mFPS, x, y, mFPSText);
    }

    public void DrawTransRoundRect(Canvas cvs, int a, int r, int g, int b, float x1, float y1, float x2, float y2) {
        mFillPaint.setARGB(a, r, g, b);
        cvs.drawRoundRect(x1, y1, x2, y2, 20, 20, mFillPaint);
    }

    public void DrawFilledRoundRect(Canvas cvs, int a, int r, int g, int b, float x1, float y1, float x2, float y2, float rd) {
        mFillPaint.setARGB(a, r, g, b);
        cvs.drawRoundRect(x1, y1, x2, y2, rd, rd, mFillPaint);
    }

    public void DrawRoundRect(Canvas cvs, int a, int r, int g, int b, float t, float x1, float y1, float x2, float y2, float rd) {
        mStrokePaint.setARGB(a, r, g, b);
        mStrokePaint.setStrokeWidth(t);
        cvs.drawRoundRect(x1, y1, x2, y2, rd, rd, mStrokePaint);
    }

    public void DrawItems(Canvas cvs, String txt, float d, float x, float y, float sz) {
        if (txt == null) return;
        mTextPainti.setARGB(255, 255, 255, 255); mTextPainti.setTextSize(sz);
        cvs.drawText(txt + " [" + (int)d + "m]", x, y, mTextPainti);
    }

    public void DrawVehicles(Canvas cvs, String txt, float d, float h, float f, float x, float y, float sz) {
        if (txt == null) return;
        mVehiclesPaint.setARGB(255, 255, 255, 255); mVehiclesPaint.setTextSize(sz);
        cvs.drawText(txt + " [" + (int)d + "m]", x, y, mVehiclesPaint);
    }

    public void DrawDeadBoxItems(Canvas cvs, int a, int r, int g, int b, String txt, float x, float y, float sz) {
        if (txt == null) return;
        mLootBoxPaint.setARGB(a, r, g, b); mLootBoxPaint.setTextSize(sz);
        cvs.drawText(txt, x, y, mLootBoxPaint);
    }

    public void DrawWeapon(Canvas cvs, int a, int r, int g, int b, int wid, int a1, int a2, float x, float y, float sz) {
        mNamePaint.setARGB(a, r, g, b); mNamePaint.setTextSize(sz);
        cvs.drawText("Gun(" + wid + ") [" + a1 + "]", x, y, mNamePaint);
    }
}
