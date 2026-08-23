#include <vector>
#include <string>
#include <jni.h>

std::string g_Token;
std::string g_Auth;
std::string g_Expire;

// Hardcoded SHA-256 Signature (Upper case, no colons)
#define APP_SIGNATURE "C6288994EE727D59DD2B3CE0E5DD206E0FF815BAC13AFD7F611420DEE89A2FA7"
#define APP_PACKAGE "Game.Loader"

#include <android/log.h>
#define LOG_SEC(...) __android_log_print(ANDROID_LOG_ERROR, "SECURITY", __VA_ARGS__)

// Simple helper to check signature and package integrity
bool verifySignature(JNIEnv *env, jobject context) {
    if (context == nullptr) return false;
    jclass contextClass = env->GetObjectClass(context);

    // 1. Get Package Name
    jmethodID getPackageName = env->GetMethodID(contextClass, "getPackageName", "()Ljava/lang/String;");
    jstring jPackageName = (jstring)env->CallObjectMethod(context, getPackageName);

    jmethodID getPackageManager = env->GetMethodID(contextClass, "getPackageManager", "()Landroid/content/pm/PackageManager;");
    jobject packageManager = env->CallObjectMethod(context, getPackageManager);

    jclass packageManagerClass = env->GetObjectClass(packageManager);
    jmethodID getPackageInfo = env->GetMethodID(packageManagerClass, "getPackageInfo", "(Ljava/lang/String;I)Landroid/content/pm/PackageInfo;");

    // GET_SIGNATURES = 64
    jobject packageInfo = env->CallObjectMethod(packageManager, getPackageInfo, jPackageName, 64);
    if (packageInfo == nullptr) return false;

    jclass packageInfoClass = env->GetObjectClass(packageInfo);
    jfieldID signaturesField = env->GetFieldID(packageInfoClass, "signatures", "[Landroid/content/pm/Signature;");
    jobjectArray signatures = (jobjectArray)env->GetObjectField(packageInfo, signaturesField);
    if (signatures == nullptr || env->GetArrayLength(signatures) == 0) return false;
    jobject signature = env->GetObjectArrayElement(signatures, 0);

    jclass signatureClass = env->GetObjectClass(signature);
    jmethodID toByteArray = env->GetMethodID(signatureClass, "toByteArray", "()[B");
    jbyteArray signatureBytes = (jbyteArray)env->CallObjectMethod(signature, toByteArray);

    // Compute SHA-256 using Java MessageDigest (Stable in threads)
    jclass messageDigestClass = env->FindClass("java/security/MessageDigest");
    jmethodID getInstance = env->GetStaticMethodID(messageDigestClass, "getInstance", "(Ljava/lang/String;)Ljava/security/MessageDigest;");
    jobject digest = env->CallStaticObjectMethod(messageDigestClass, getInstance, env->NewStringUTF("SHA-256"));

    jmethodID update = env->GetMethodID(messageDigestClass, "update", "([B)V");
    env->CallVoidMethod(digest, update, signatureBytes);

    jmethodID doFinal = env->GetMethodID(messageDigestClass, "digest", "()[B");
    jbyteArray hashBytes = (jbyteArray)env->CallObjectMethod(digest, doFinal);

    jsize len = env->GetArrayLength(hashBytes);
    jbyte* bytes = env->GetByteArrayElements(hashBytes, 0);

    char hex[len * 2 + 1];
    for (int i = 0; i < len; i++) {
        sprintf(hex + (i * 2), "%02X", (unsigned char)bytes[i]);
    }
    hex[len * 2] = '\0';
    env->ReleaseByteArrayElements(hashBytes, bytes, 0);

    return (std::string(hex) == std::string(APP_SIGNATURE));
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pubgm_Login_setAuth(JNIEnv *env, jclass clazz,jstring token,jstring auth) {
    const char* t = env->GetStringUTFChars(token, 0);
    const char* a = env->GetStringUTFChars(auth, 0);
    g_Token = t;
    g_Auth = a;
    env->ReleaseStringUTFChars(token, t);
    env->ReleaseStringUTFChars(auth, a);
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pubgm_Login_setExpire(JNIEnv *env, jclass clazz, jstring exp) {
    const char* Expire = env->GetStringUTFChars(exp, 0);
    g_Expire = Expire;
    env->ReleaseStringUTFChars(exp, Expire);
}

extern "C"
JNIEXPORT jobject JNICALL
Java_com_pubgm_Login_getheaders(JNIEnv *env, jclass clazz, jobject context) {
    // SECURITY: If signature check fails, return empty list or garbage
    if (!verifySignature(env, context)) {
        return NULL;
    }

    std::vector<std::string> headers = {
        OBFUSCATE("Content-Type"),
        OBFUSCATE("application/x-www-form-urlencoded"),
        OBFUSCATE("Accept"),
        OBFUSCATE("application/json"),
        OBFUSCATE("Charset"),
        OBFUSCATE("UTF-8"),
        OBFUSCATE("User-Agent"),
        OBFUSCATE("AbsoluteX/2.0"),
        OBFUSCATE("PUBG"),
        OBFUSCATE("user_key"),
        OBFUSCATE("serial"),
        OBFUSCATE("Vm8Lk7Uj2JmsjCPVPVjrLa7zgfx3uz9E")
    };

    jclass arrayListClass = env->FindClass("java/util/ArrayList");
    jmethodID constructor = env->GetMethodID(arrayListClass, "<init>", "()V");
    jobject list = env->NewObject(arrayListClass, constructor);
    jmethodID add = env->GetMethodID(arrayListClass, "add", "(Ljava/lang/Object;)Z");

    for (auto &h : headers) {
        jstring str = env->NewStringUTF(h.c_str());
        env->CallBooleanMethod(list, add, str);
        env->DeleteLocalRef(str);
    }

    return list;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_pubgm_Login_getbaseurl(JNIEnv *env, jclass clazz, jobject context) {
    // SECURITY: If signature check fails, return fake URL
    if (!verifySignature(env, context)) {
        return env->NewStringUTF(OBFUSCATE("https://google.com/error"));
    }

    std::string url = OBFUSCATE("https://zoro.manishflash.online/connect");
    return env->NewStringUTF(url.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pubgm_floating_Overlay_isDaemonConnected(JNIEnv* env, jclass) {
    return clientD >= 0;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pubgm_Login_setAuthToken(JNIEnv* env, jclass, jstring token) {
    if (token == NULL) return;
    const char* nativeToken = env->GetStringUTFChars(token, 0);
    g_Auth = std::string(nativeToken);
    g_Token = g_Auth; // Force sync
    env->ReleaseStringUTFChars(token, nativeToken);
}

extern "C" JNIEXPORT jstring JNICALL 
Java_com_pubgm_BoxApplication_BoxApp(JNIEnv* env, jobject thiz) {
    return env->NewStringUTF(OBFUSCATE("ZORO-6069927A-318"));
    //return env->NewStringUTF(OBFUSCATE("60day>game-rhsi568"));
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_pubgm_Login_FixCrash(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF(OBFUSCATE("https://github.com/sk8787914-maker/JAVA/releases/download/Java/assets.zip"));
   // return env->NewStringUTF(OBFUSCATE("https://github.com/omii71/BEASTCROWN1/releases/download/BEASTCROWN/Saved.zip"));
   // return env->NewStringUTF(OBFUSCATE("https://github.com/Jagdishvip/Bgmi/releases/download/Bgmi/BGMI.zip"));
}
