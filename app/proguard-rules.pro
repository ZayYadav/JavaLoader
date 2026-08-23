# ===== PUBGM GAME LOADER - OPTIMIZED SIZE REDUCTION PROGUARD =====

# Disable features that increase size or break code
-dontobfuscate
-dontoptimize
-dontpreverify
-ignorewarnings

# Keep critical attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable

# ===== PROJECT PACKAGES PROTECTION (Do not touch package name) =====
-keep class com.pubgm.** { *; }
-keep interface com.pubgm.** { *; }

# ===== NATIVE / JNI PROTECTION (Critical for ESP and Hacks) =====
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclasseswithmembers class * {
    native <methods>;
}

# Keep all ESPView drawing methods called from JNI
-keep class com.pubgm.floating.ESPView {
    public <methods>;
    protected <methods>;
    <fields>;
}

# Keep Toggle and Service classes JNI methods
-keep class com.pubgm.floating.ToggleAim { native <methods>; public <methods>; }
-keep class com.pubgm.floating.ToggleBullet { native <methods>; public <methods>; }
-keep class com.pubgm.floating.ToggleSimulation { native <methods>; public <methods>; }
-keep class com.pubgm.floating.FloatLogo { native <methods>; public <methods>; }
-keep class com.pubgm.floating.Overlay { native <methods>; public <methods>; }

# ===== HCORE / BLACKBOX PROTECTION (Do not touch hcore) =====
-keep class com.hcore.** { *; }
-keep interface com.hcore.** { *; }
-keep class top.niunaijun.blackbox.** { *; }
-keep class android.MetaCore.** { *; }
-keep class com.virtualx.** { *; }
-keep class top.niunaijun.RIYAZ.** { *; }

# ===== ANDROID COMPONENTS =====
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ===== THIRD PARTY LIBS (Shrink but keep API) =====
-keep class net.lingala.zip4j.** { *; }
-keep class org.jdeferred.** { *; }
-keep class com.github.tiann.** { *; }
-keep class me.weishu.reflection.** { *; }

# ===== PARCELABLE / SERIALIZABLE =====
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Force Single DEX by allowing R8 to remove unused library code
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
