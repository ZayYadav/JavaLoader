package com.pubgm.floating;

import android.annotation.SuppressLint;
import android.os.Build;
import android.text.TextUtils;
import android.view.WindowManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

public class HideRecorder {

    // Supported ROM types
    public static final String ROM_MIUI = "MIUI";
    public static final String ROM_EMUI = "EMUI";
    public static final String ROM_FLYME = "FLYME";
    public static final String ROM_OPPO = "OPPO";
    public static final String ROM_SMARTISAN = "SMARTISAN";
    public static final String ROM_VIVO = "VIVO";
    public static final String ROM_QIKU = "QIKU";
    public static final String ROM_NUBIAUI = "NUBIAUI";
    public static final String ROM_ONEPLUS = "HYDROGEN";
    public static final String ROM_SAMSUNG = "ONEUI";
    public static final String ROM_BLACKSHARK = "JOYUI";
    public static final String ROM_ROG = "REPLIBLIC";
    public static final String ROM_REALME = "REALMEUI";
    public static final String ROM_MOTOROLA = "MOTOROLA";
    public static final String ROM_PIXEL = "PIXEL";
    public static final String ROM_LENOVO = "LENOVO";
    public static final String ROM_SONY = "SONY";
    public static final String ROM_ASUS = "ASUS";
    public static final String ROM_INFINIX = "INFINIX";  // Added Infinix
    public static final String ROM_TECNO = "TECNO";      // Added Tecno (related to Infinix/Transsion)
    public static final String ROM_ITEL = "ITEL";        // Added Itel (related to Infinix/Transsion)
    public static final String ROM_GENERIC = "GENERIC";

    // System property keys for detection
    private static final String KEY_VERSION_MIUI = "ro.miui.ui.version.name";
    private static final String KEY_VERSION_EMUI = "ro.build.version.emui";
    private static final String KEY_VERSION_OPPO = "ro.build.version.opporom";
    private static final String KEY_VERSION_SMARTISAN = "ro.smartisan.version";
    private static final String KEY_VERSION_VIVO = "ro.vivo.os.version";
    private static final String KEY_VERSION_NUBIA = "ro.build.nubia.rom.name";
    private static final String KEY_VERSION_ONEPLUS = "ro.build.ota.versionname";
    private static final String KEY_VERSION_SAMSUNG = "ro.channel.officehubrow";
    private static final String KEY_VERSION_BLACKSHARK = "ro.blackshark.rom";
    private static final String KEY_VERSION_ROG = "ro.build.fota.version";
    private static final String KEY_VERSION_REALME = "ro.build.version.oplusrom";
    // Added Infinix/Transsion property keys
    private static final String KEY_VERSION_INFINIX = "ro.infinix.os.version";
    private static final String KEY_VERSION_TECNO = "ro.transsion.os.version";
    private static final String KEY_VERSION_ITEL = "ro.itel.os.version";
    
    private static String sName;
    private static final Map<String, String> ROM_WINDOW_TITLES = new HashMap<>();

    static {
        // Initialize ROM to window title mappings
        ROM_WINDOW_TITLES.put(ROM_MIUI, "com.miui.screenrecorder");
        ROM_WINDOW_TITLES.put(ROM_EMUI, "ScreenRecoderTimer");
        ROM_WINDOW_TITLES.put(ROM_OPPO, "com.coloros.screenrecorder");
        ROM_WINDOW_TITLES.put(ROM_VIVO, "screen_record_menu");
        ROM_WINDOW_TITLES.put(ROM_ONEPLUS, "op_screenrecord");
        ROM_WINDOW_TITLES.put(ROM_FLYME, "SysScreenRecorder");
        ROM_WINDOW_TITLES.put(ROM_NUBIAUI, "NubiaScreenDecorOverlay");
        ROM_WINDOW_TITLES.put(ROM_BLACKSHARK, "com.blackshark.screenrecorder");
        ROM_WINDOW_TITLES.put(ROM_ROG, "com.asus.screenrecorder");
        ROM_WINDOW_TITLES.put(ROM_REALME, "com.oplus.screenrecorder");
        ROM_WINDOW_TITLES.put(ROM_PIXEL, "com.google.android.apps.screenrecorder");
        ROM_WINDOW_TITLES.put(ROM_MOTOROLA, "com.motorola.screenrecorder");
        ROM_WINDOW_TITLES.put(ROM_LENOVO, "com.lenovo.screenrecorder");
        ROM_WINDOW_TITLES.put(ROM_SONY, "com.sony.screenrecorder");
        ROM_WINDOW_TITLES.put(ROM_ASUS, "com.asus.screenrecorder");
        // Added Infinix/Transsion window titles
        ROM_WINDOW_TITLES.put(ROM_INFINIX, "com.infinix.screenrecorder");
        ROM_WINDOW_TITLES.put(ROM_TECNO, "com.tecno.screenrecorder");
        ROM_WINDOW_TITLES.put(ROM_ITEL, "com.itel.screenrecorder");
        ROM_WINDOW_TITLES.put(ROM_GENERIC, "ScreenRecorder");
    }

    public static boolean isEmui() { return check(ROM_EMUI); }
    public static boolean isMiui() { return check(ROM_MIUI); }
    public static boolean isVivo() { return check(ROM_VIVO); }
    public static boolean isOppo() { return check(ROM_OPPO); }
    public static boolean isFlyme() { return check(ROM_FLYME); }
    public static boolean isNubia() { return check(ROM_NUBIAUI); }
    public static boolean isOnePlus() { return check(ROM_ONEPLUS); }
    public static boolean isSanSung() { return check(ROM_SAMSUNG); }
    public static boolean isBLACKSHARK() { return check(ROM_BLACKSHARK); }
    public static boolean isRog() { return check(ROM_ROG); }
    public static boolean isRealme() { return check(ROM_REALME); }
    public static boolean isMotorola() { return check(ROM_MOTOROLA); }
    public static boolean isPixel() { return check(ROM_PIXEL); }
    public static boolean isLenovo() { return check(ROM_LENOVO); }
    public static boolean isSony() { return check(ROM_SONY); }
    public static boolean isAsus() { return check(ROM_ASUS); }
    // Added Infinix/Transsion check methods
    public static boolean isInfinix() { return check(ROM_INFINIX); }
    public static boolean isTecno() { return check(ROM_TECNO); }
    public static boolean isItel() { return check(ROM_ITEL); }

    public static boolean isActivice() {
        // You can implement your activation logic here
        // For now returning false as placeholder
        return false;
    }

    public static void setFakeRecorderWindowLayoutParams(WindowManager.LayoutParams layoutParams) {
        if (layoutParams == null) return;
        
        try {
            // Set default title for all devices
            String title = getFakeRecordWindowTitle();
            if (!TextUtils.isEmpty(title)) {
                layoutParams.setTitle(title);
            }
            
            // Handle special cases for each ROM type
            if (isFlyme()) {
                handleFlymeParams(layoutParams);
            } else if (isMiui() || isBLACKSHARK()) {
                handleXiaomiParams(layoutParams);
            } else if (isOnePlus()) {
                handleOnePlusParams(layoutParams);
            } else if (isSanSung()) {
                handleSamsungParams(layoutParams);
            } else if (isRog()) {
                handleRogParams(layoutParams);
            } else if (isRealme() || isOppo()) {
                handleOppoStyleParams(layoutParams);
            } else if (isPixel()) {
                handlePixelParams(layoutParams);
            } else if (isVivo()) {
                handleVivoParams(layoutParams);
            } else if (isInfinix() || isTecno() || isItel()) {
                handleInfinixParams(layoutParams);  // Added Infinix handling
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleXiaomiParams(WindowManager.LayoutParams params) {
        try {
            // Add FLAG_DITHER for Xiaomi devices
            params.flags |= WindowManager.LayoutParams.FLAG_DITHER;
            
            // For MIUI 12 and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    Field field = params.getClass().getField("extraFlags");
                    field.setAccessible(true);
                    int extraFlags = field.getInt(params);
                    field.setInt(params, extraFlags | 0x10000);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleFlymeParams(WindowManager.LayoutParams params) {
        try {
            // Flyme specific handling
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                try {
                    Field meizuFlags = params.getClass().getDeclaredField("meizuFlags");
                    meizuFlags.setAccessible(true);
                    meizuFlags.setInt(params, 1024);
                } catch (Exception ignored) {
                    // Fallback to older method
                    try {
                        Class<?> meizuClass = Class.forName("android.view.MeizuLayoutParams");
                        Field flagField = meizuClass.getDeclaredField("flags");
                        flagField.setAccessible(true);
                        Object meizuObj = meizuClass.newInstance();
                        flagField.setInt(meizuObj, 8192);
                        Field mzField = params.getClass().getField("meizuParams");
                        mzField.set(params, meizuObj);
                    } catch (Exception ignored2) {}
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleOnePlusParams(WindowManager.LayoutParams params) {
        try {
            // OnePlus specific handling
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    Field privateFlags = params.getClass().getDeclaredField("privateFlags");
                    privateFlags.setAccessible(true);
                    int flags = privateFlags.getInt(params);
                    privateFlags.setInt(params, flags | 0x00000040);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleSamsungParams(WindowManager.LayoutParams params) {
        try {
            // Samsung specific handling
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                try {
                    Method method = params.getClass().getMethod("semAddExtensionFlags", int.class);
                    method.invoke(params, 0x80000000);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleRogParams(WindowManager.LayoutParams params) {
        try {
            // ROG Phone specific handling
            Field field = params.getClass().getDeclaredField("memoryType");
            field.setAccessible(true);
            int memoryType = field.getInt(params);
            field.setInt(params, memoryType | 0x10000000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleOppoStyleParams(WindowManager.LayoutParams params) {
        try {
            // OPPO/Realme specific handling
            params.flags |= 0x00000200;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    Field field = params.getClass().getDeclaredField("privacyIndicatorBounds");
                    field.setAccessible(true);
                    field.set(params, null);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handlePixelParams(WindowManager.LayoutParams params) {
        try {
            // Pixel specific handling
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                params.setTitle("com.google.android.apps.screenrecorder");
                params.flags |= 0x00002000;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleVivoParams(WindowManager.LayoutParams params) {
        try {
            // Vivo specific handling
            params.flags |= 0x00000400;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    Field field = params.getClass().getDeclaredField("vivoFlags");
                    field.setAccessible(true);
                    int vivoFlags = field.getInt(params);
                    field.setInt(params, vivoFlags | 0x2000);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Added Infinix specific parameter handling
    private static void handleInfinixParams(WindowManager.LayoutParams params) {
        try {
            // Infinix/Transsion specific handling
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Add flags commonly used by Infinix devices
                params.flags |= 0x00001000;
                
                try {
                    // Try to set XOS specific flags (Infinix uses XOS)
                    Field field = params.getClass().getDeclaredField("xosFlags");
                    field.setAccessible(true);
                    int xosFlags = field.getInt(params);
                    field.setInt(params, xosFlags | 0x4000);
                } catch (Exception ignored) {
                    // Try alternative method for older XOS versions
                    try {
                        Field field = params.getClass().getDeclaredField("transsionFlags");
                        field.setAccessible(true);
                        int transsionFlags = field.getInt(params);
                        field.setInt(params, transsionFlags | 0x2000);
                    } catch (Exception ignored2) {}
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String getFakeRecordWindowTitle() {
        if (sName == null) {
            check("");
        }
        String title = ROM_WINDOW_TITLES.get(sName);
        return title != null ? title : "ScreenRecorder";
    }

    private static boolean check(String rom) {
        if (sName != null) {
            return sName.equals(rom);
        }

        // Check system properties first
        if (!TextUtils.isEmpty(getProp(KEY_VERSION_MIUI))) {
            sName = ROM_MIUI;
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_BLACKSHARK))) {
            sName = ROM_BLACKSHARK;
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_EMUI))) {
            sName = ROM_EMUI;
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_OPPO))) {
            sName = ROM_OPPO;
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_VIVO))) {
            sName = ROM_VIVO;
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_SMARTISAN))) {
            sName = ROM_SMARTISAN;
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_NUBIA))) {
            sName = ROM_NUBIAUI;
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_ONEPLUS))) {
            sName = ROM_ONEPLUS;
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_ROG))) {
            sName = ROM_ROG;
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_SAMSUNG))) {
            sName = ROM_SAMSUNG;
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_REALME))) {
            sName = ROM_REALME;
        } 
        // Added Infinix/Transsion property checks
        else if (!TextUtils.isEmpty(getProp(KEY_VERSION_INFINIX))) {
            sName = ROM_INFINIX;
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_TECNO))) {
            sName = ROM_TECNO;
        } else if (!TextUtils.isEmpty(getProp(KEY_VERSION_ITEL))) {
            sName = ROM_ITEL;
        } else {
            // Fallback to manufacturer detection
            String manufacturer = Build.MANUFACTURER.toUpperCase();
            String brand = Build.BRAND.toUpperCase();
            String display = Build.DISPLAY.toUpperCase();
            
            if (display.contains("FLYME") || manufacturer.contains("MEIZU")) {
                sName = ROM_FLYME;
            } else if (manufacturer.contains("MOTOROLA") || brand.contains("MOTOROLA")) {
                sName = ROM_MOTOROLA;
            } else if (manufacturer.contains("GOOGLE") || brand.contains("GOOGLE") || manufacturer.contains("PIXEL")) {
                sName = ROM_PIXEL;
            } else if (manufacturer.contains("LENOVO")) {
                sName = ROM_LENOVO;
            } else if (manufacturer.contains("SONY")) {
                sName = ROM_SONY;
            } else if (manufacturer.contains("ASUS")) {
                sName = ROM_ASUS;
            } else if (manufacturer.contains("SAMSUNG")) {
                sName = ROM_SAMSUNG;
            } else if (manufacturer.contains("XIAOMI") || manufacturer.contains("REDMI") || brand.contains("XIAOMI")) {
                sName = ROM_MIUI;
            } else if (manufacturer.contains("OPPO")) {
                sName = ROM_OPPO;
            } else if (manufacturer.contains("VIVO")) {
                sName = ROM_VIVO;
            } else if (manufacturer.contains("REALME")) {
                sName = ROM_REALME;
            } else if (manufacturer.contains("ONEPLUS")) {
                sName = ROM_ONEPLUS;
            } else if (manufacturer.contains("NUBIA") || manufacturer.contains("ZTE")) {
                sName = ROM_NUBIAUI;
            } 
            // Added Infinix/Transsion manufacturer checks
            else if (manufacturer.contains("INFINIX")) {
                sName = ROM_INFINIX;
            } else if (manufacturer.contains("TECNO")) {
                sName = ROM_TECNO;
            } else if (manufacturer.contains("ITEL")) {
                sName = ROM_ITEL;
            } else if (manufacturer.contains("TRANSSION")) {
                // Transsion is the parent company of Infinix, Tecno, and Itel
                // Default to Infinix if we can't determine the specific brand
                sName = ROM_INFINIX;
            } else {
                sName = ROM_GENERIC;
            }
        }
        
        return sName.equals(rom);
    }

    private static String getProp(String name) {
        BufferedReader reader = null;
        try {
            Process process = Runtime.getRuntime().exec("getprop " + name);
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()), 1024);
            return reader.readLine();
        } catch (IOException e) {
            return null;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}