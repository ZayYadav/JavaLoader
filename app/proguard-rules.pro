# OneCore MyThos release shrinking/obfuscation.
# R8 is allowed to optimize and rename ordinary Java code; only framework/JNI entry points stay fixed.

-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile
-allowaccessmodification

# JNI symbols are name-sensitive.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Native signing verifier is resolved through exact conventional JNI names.
-keep class com.pubgm.security.NativeSigningVerifier {
    private static native boolean verifySigningIdentity(
        byte[][],
        byte[][],
        java.lang.String,
        java.lang.String
    );
    private static native boolean verifyProcessBoundApkNative(
        java.lang.String,
        java.lang.String
    );
}
-keepnames class com.pubgm.security.NativeSigningVerifier

# The shipped native client resolves this compatibility facade by its existing JNI name.
-keep class com.pubgm.Login { *; }

# Existing native/floating/game runtime entry points are left untouched by this defensive hardening pass.
-keep class com.pubgm.floating.** { *; }
-keep class com.pubgm.libhelper.** { *; }

# Legacy reflection/compatibility namespaces retained only where the existing app still references them.
-keep class com.hcore.** { *; }
-keep interface com.hcore.** { *; }
-keep class top.niunaijun.blackbox.** { *; }
-keep class android.MetaCore.** { *; }
-keep class com.virtualx.** { *; }
-keep class top.niunaijun.RIYAZ.** { *; }

# Manifest/framework components need stable class names, but their code can still be optimized.
-keep,allowoptimization class * extends android.app.Activity
-keep,allowoptimization class * extends android.app.Application
-keep,allowoptimization class * extends android.app.Service
-keep,allowoptimization class * extends android.content.BroadcastReceiver
-keep,allowoptimization class * extends android.content.ContentProvider
-keep,allowoptimization public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

-dontwarn org.jetbrains.annotations.**

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
