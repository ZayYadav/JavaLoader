package com.pubgm.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.pubgm.security.ParallaxLoversssHu;

import java.util.Set;

public class FPrefs {
    private static final String LENGTH = "_length";
    private static final String DEFAULT_STRING_VALUE = "";
    private static final int DEFAULT_INT_VALUE = -1;
    private static final double DEFAULT_DOUBLE_VALUE = -1d;
    private static final float DEFAULT_FLOAT_VALUE = -1f;
    private static final long DEFAULT_LONG_VALUE = -1L;
    private static final boolean DEFAULT_BOOLEAN_VALUE = false;
    private static final String PROTECTED_LICENSE_KEY = "USER";

    private static FPrefs prefsInstance;
    private final SharedPreferences sharedPreferences;
    private final ParallaxLoversssHu securePreferences;

    private FPrefs(Context context) {
        Context appContext = context.getApplicationContext();
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
        securePreferences = new ParallaxLoversssHu(appContext);
        migrateLegacyLicenseKey();
    }

    private FPrefs(Context context, String preferencesName) {
        Context appContext = context.getApplicationContext();
        sharedPreferences = appContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE);
        securePreferences = new ParallaxLoversssHu(appContext);
        migrateLegacyLicenseKey();
    }

    public static FPrefs with(Context context) {
        if (prefsInstance == null) prefsInstance = new FPrefs(context);
        return prefsInstance;
    }

    public static FPrefs with(Context context, boolean forceInstantiation) {
        if (forceInstantiation) prefsInstance = new FPrefs(context);
        return prefsInstance;
    }

    public static FPrefs with(Context context, String preferencesName) {
        if (prefsInstance == null) prefsInstance = new FPrefs(context, preferencesName);
        return prefsInstance;
    }

    public static FPrefs with(Context context, String preferencesName, boolean forceInstantiation) {
        if (forceInstantiation) prefsInstance = new FPrefs(context, preferencesName);
        return prefsInstance;
    }

    public String read(String what) {
        return read(what, DEFAULT_STRING_VALUE);
    }

    public String read(String what, String defaultString) {
        if (PROTECTED_LICENSE_KEY.equals(what)) {
            return securePreferences.getString(PROTECTED_LICENSE_KEY, defaultString);
        }
        return sharedPreferences.getString(what, defaultString);
    }

    public void write(String where, String what) {
        if (PROTECTED_LICENSE_KEY.equals(where)) {
            securePreferences.putString(PROTECTED_LICENSE_KEY, what);
            sharedPreferences.edit().remove(PROTECTED_LICENSE_KEY).apply();
            return;
        }
        sharedPreferences.edit().putString(where, what).apply();
    }

    public int readInt(String what) { return sharedPreferences.getInt(what, DEFAULT_INT_VALUE); }
    public int readInt(String what, int defaultInt) { return sharedPreferences.getInt(what, defaultInt); }
    public void writeInt(String where, int what) { sharedPreferences.edit().putInt(where, what).apply(); }

    public double readDouble(String what) {
        if (!contains(what)) return DEFAULT_DOUBLE_VALUE;
        return Double.longBitsToDouble(readLong(what));
    }

    public double readDouble(String what, double defaultDouble) {
        if (!contains(what)) return defaultDouble;
        return Double.longBitsToDouble(readLong(what));
    }

    public void writeDouble(String where, double what) { writeLong(where, Double.doubleToRawLongBits(what)); }
    public float readFloat(String what) { return sharedPreferences.getFloat(what, DEFAULT_FLOAT_VALUE); }
    public float readFloat(String what, float defaultFloat) { return sharedPreferences.getFloat(what, defaultFloat); }
    public void writeFloat(String where, float what) { sharedPreferences.edit().putFloat(where, what).apply(); }
    public long readLong(String what) { return sharedPreferences.getLong(what, DEFAULT_LONG_VALUE); }
    public long readLong(String what, long defaultLong) { return sharedPreferences.getLong(what, defaultLong); }
    public void writeLong(String where, long what) { sharedPreferences.edit().putLong(where, what).apply(); }
    public boolean readBoolean(String what) { return readBoolean(what, DEFAULT_BOOLEAN_VALUE); }
    public boolean readBoolean(String what, boolean defaultBoolean) { return sharedPreferences.getBoolean(what, defaultBoolean); }
    public void writeBoolean(String where, boolean what) { sharedPreferences.edit().putBoolean(where, what).apply(); }
    public void putStringSet(final String key, final Set<String> value) { sharedPreferences.edit().putStringSet(key, value).apply(); }
    public Set<String> getStringSet(final String key, final Set<String> defValue) { return sharedPreferences.getStringSet(key, defValue); }

    public void remove(final String key) {
        if (PROTECTED_LICENSE_KEY.equals(key)) {
            securePreferences.remove(PROTECTED_LICENSE_KEY);
            sharedPreferences.edit().remove(PROTECTED_LICENSE_KEY).apply();
            return;
        }
        if (contains(key + LENGTH)) {
            int stringSetLength = readInt(key + LENGTH);
            if (stringSetLength >= 0) {
                sharedPreferences.edit().remove(key + LENGTH).apply();
                for (int i = 0; i < stringSetLength; i++) {
                    sharedPreferences.edit().remove(key + "[" + i + "]").apply();
                }
            }
        }
        sharedPreferences.edit().remove(key).apply();
    }

    public boolean contains(final String key) {
        if (PROTECTED_LICENSE_KEY.equals(key)) {
            return !securePreferences.getString(PROTECTED_LICENSE_KEY, "").isEmpty();
        }
        return sharedPreferences.contains(key);
    }

    public void clear() {
        sharedPreferences.edit().clear().apply();
        securePreferences.remove(PROTECTED_LICENSE_KEY);
    }

    private void migrateLegacyLicenseKey() {
        String legacy = sharedPreferences.getString(PROTECTED_LICENSE_KEY, "");
        if (legacy != null && !legacy.isEmpty()) {
            securePreferences.putString(PROTECTED_LICENSE_KEY, legacy);
            sharedPreferences.edit().remove(PROTECTED_LICENSE_KEY).apply();
        }
    }
}
