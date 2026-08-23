#include <jni.h>
#include <string>
#include <obfuscate.h>
#include "ESP/ESP.h"
#include "ESP/Hacks.h"
#define LOG_TAG "CleanLogs"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#include <sys/stat.h>

// Initialize ESP static members
jmethodID ESP::_mDrawLine = nullptr;
jmethodID ESP::_mDrawRect = nullptr;
jmethodID ESP::_mDrawFilledRect = nullptr;
jmethodID ESP::_mDrawCircle = nullptr;
jmethodID ESP::_mDrawFilledCircle = nullptr;
jmethodID ESP::_mDrawText = nullptr;
jmethodID ESP::_mDrawName = nullptr;
jmethodID ESP::_mDrawTriangle = nullptr;
jmethodID ESP::_mDrawTransRoundRect = nullptr;
jmethodID ESP::_mDrawFilledRoundRect = nullptr;
jmethodID ESP::_mDrawRoundRect = nullptr;
jmethodID ESP::_mDrawItems = nullptr;
jmethodID ESP::_mDrawVehicles = nullptr;
jmethodID ESP::_mDrawDeadBoxItems = nullptr;
jmethodID ESP::_mDrawWeapon = nullptr;
jmethodID ESP::_mDrawUserID = nullptr;
jmethodID ESP::_mDrawTextName = nullptr;
jmethodID ESP::_mGetWidth = nullptr;
jmethodID ESP::_mGetHeight = nullptr;
bool ESP::_isInitialized = false;

int type=1,utype=2;

extern "C" JNIEXPORT void JNICALL
Java_com_pubgm_floating_Overlay_DrawOn(JNIEnv *env, jclass , jobject espView, jobject canvas) {
    if (env == nullptr || espView == nullptr || canvas == nullptr) return;

    ESP espOverlay(env, espView, canvas);
    if (espOverlay.isValid()){
        DrawESP(espOverlay, espOverlay.getWidth(), espOverlay.getHeight());
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_pubgm_floating_Overlay_Close(JNIEnv *env,  jobject ) {
    LOGI("Overlay Closing - Setting options to -1");
    Close();
    options.openState = -1;
    options.aimBullet = -1;
    options.aimT = -1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_SettingValue(JNIEnv *, jobject, jint code, jboolean jboolean1) {
    switch (code) {
        case 1:  isPlayerBox = jboolean1; break;
        case 2:  isPlayerLine = jboolean1; break;
        case 3:  isPlayerDistance = jboolean1; break;
        case 4:  isPlayerHealth = jboolean1; break;
        case 5:  isPlayerName = jboolean1; break;
        case 6:  isPlayerHead = jboolean1; break;
        case 7:  is360Alert = jboolean1; break;
        case 8:  isSkeleton = jboolean1; break;
        case 9:  isGrenadeWarning = jboolean1; break;
        case 10: isPlayerWeapon = jboolean1; break;
        case 11: isLootItems = jboolean1; break;
        case 12: isPlayerUID = jboolean1; break;
        case 13: isPlayerNation = jboolean1; break;
        case 14: isPlayerTeamID = jboolean1; break;
        case 15: isVehicles = jboolean1; break;
        case 16: options.ignoreAi = jboolean1; break;
        case 17: isItems = jboolean1; break;
        case 18: isFightMode = jboolean1; break;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pubgm_floating_ToggleAim_ToggleAim(JNIEnv *, jobject thiz, jboolean value) {
    if (value) options.openState = 0; else options.openState = -1;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_ToggleAim(JNIEnv *, jobject thiz, jboolean value) {
    if (value) options.openState = 0; else options.openState = -1;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pubgm_floating_ToggleBullet_ToggleBullet(JNIEnv *, jobject thiz, jboolean value) {
    if (value) options.aimBullet = 0; else options.aimBullet = -1;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pubgm_floating_ToggleSimulation_ToggleSimulation(JNIEnv *, jobject thiz, jboolean value) {
    if (value) options.aimT = 0; else options.aimT = -1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_Range(JNIEnv *, jobject, jint range) {
    options.aimingRange = 1 + range;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_distances(JNIEnv *, jobject, jint distances) {
    options.aimingDist = distances;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_WideView(JNIEnv *env, jobject thiz, jint wideview) {
    otherFeature.WideView = wideview;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_Target(JNIEnv *, jobject, jint target) {
    options.aimbotmode = target;
}
extern "C" JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_AimWhen(JNIEnv *, jobject, jint state) {
    options.aimingState = state;
}
extern "C" JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_AimBy(JNIEnv *, jobject, jint aimby) {
    options.priority = aimby;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_recoil(JNIEnv *env, jobject thiz, jint recoil) {
    options.recCompe = recoil;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_recoil1(JNIEnv *, jobject, jint recoil1) {
    options.recCompe1 = recoil1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_recoil3(JNIEnv *, jobject, jint recoil2) {
    options.recCompe2 = recoil2;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_Bulletspeed(JNIEnv *, jobject, jint speed) {
    options.aimingSpeed = speed;
}

extern "C" JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_AimingSpeed(JNIEnv *, jobject, jint speed1) {
    options.touchSpeed = speed1;
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_SettingMemory(JNIEnv *env, jobject thiz, jint setting_code, jboolean value) {
    switch ((int) setting_code) {
        case 2: otherFeature.SmallCrosshair = value; break;
        case 3: otherFeature.Aimbot = value; break;
        case 4: otherFeature.WideView = value; break;
    }
}

extern "C"
JNIEXPORT void JNICALL
Java_com_pubgm_floating_FloatLogo_SettingAim(JNIEnv *env, jobject thiz, jint setting_code, jboolean value) {
    switch ((int) setting_code) {
        case 1: options.openState = -1; break;
        case 2: options.aimBullet = -1; break;
        case 3: options.pour = value; break;
        case 4: options.ignoreBot = value; break;
        case 5: options.InputInversion = value; break;
        case 6: options.tracingStatus = value; break;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pubgm_floating_Overlay_getReady(JNIEnv *, jobject thiz) {
    int sockCheck=1;

    // Safety: Ensure we clean up if already connected
    if (clientD >= 0) {
        Close();
    }

    if (!Create()) {
        perror("Creation failed");
        return false;
    }
    setsockopt(sock,SOL_SOCKET,SO_REUSEADDR,&sockCheck, sizeof(int));
    if (!Bind()) {
        perror("Bind failed");
        return false;
    }

    if (!Listen()) {
        perror("Listen failed");
        return false;
    }
    if (Accept()) {
        LOGI("Daemon Connected Successfully");
        return true;
    }
    return false;
}
