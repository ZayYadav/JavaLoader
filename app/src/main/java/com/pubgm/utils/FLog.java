package com.pubgm.utils;

import android.util.Log;

public class FLog {
    
    public static final String TAG = FLog.class.getSimpleName();
    public static boolean DEBUG = true;
    
    public static void debug(String msg) {
        if (!DEBUG) {
            return;
        }
        Log.d(TAG, msg);
    }
    
    public static void info(String msg) {
        if (!DEBUG) {
            return;
        }
        Log.i(TAG, msg);
    }
    
    public static void warning(String msg) {
        if (!DEBUG) {
            return;
        }
        Log.w(TAG, msg);
    }
    
    public static void error(String msg) {
        if (!DEBUG) {
            return;
        }
        Log.e(TAG, msg);
    }
}
