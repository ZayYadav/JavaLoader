#ifndef ESP_ESP_H
#define ESP_ESP_H
#include "struct.h"
#include <android/log.h>

class ESP {
private:
    JNIEnv *_env;
    jobject _cvsView;
    jobject _cvs;

    // Cached Method IDs
    static jmethodID _mDrawLine;
    static jmethodID _mDrawRect;
    static jmethodID _mDrawFilledRect;
    static jmethodID _mDrawCircle;
    static jmethodID _mDrawFilledCircle;
    static jmethodID _mDrawText;
    static jmethodID _mDrawName;
    static jmethodID _mDrawTriangle;
    static jmethodID _mDrawTransRoundRect;
    static jmethodID _mDrawFilledRoundRect;
    static jmethodID _mDrawRoundRect;
    static jmethodID _mDrawItems;
    static jmethodID _mDrawVehicles;
    static jmethodID _mDrawDeadBoxItems;
    static jmethodID _mDrawWeapon;
    static jmethodID _mDrawUserID;
    static jmethodID _mDrawTextName;
    static jmethodID _mGetWidth;
    static jmethodID _mGetHeight;
    static bool _isInitialized;

public:
    ESP() : _env(nullptr), _cvsView(nullptr), _cvs(nullptr) {}
    ESP(JNIEnv *env, jobject cvsView, jobject cvs) : _env(env), _cvsView(cvsView), _cvs(cvs) {
        if (!_isInitialized && env != nullptr && cvsView != nullptr) {
            jclass clz = env->GetObjectClass(cvsView);
            _mDrawLine = env->GetMethodID(clz, "DrawLine", "(Landroid/graphics/Canvas;IIIIFFFFF)V");
            _mDrawRect = env->GetMethodID(clz, "DrawRect", "(Landroid/graphics/Canvas;IIIIFFFFF)V");
            _mDrawFilledRect = env->GetMethodID(clz, "DrawFilledRect", "(Landroid/graphics/Canvas;IIIIFFFF)V");
            _mDrawCircle = env->GetMethodID(clz, "DrawCircle", "(Landroid/graphics/Canvas;IIIIFFFF)V");
            _mDrawFilledCircle = env->GetMethodID(clz, "DrawFilledCircle", "(Landroid/graphics/Canvas;IIIIFFF)V");
            _mDrawText = env->GetMethodID(clz, "DrawText", "(Landroid/graphics/Canvas;IIIILjava/lang/String;FFF)V");
            _mDrawName = env->GetMethodID(clz, "DrawName", "(Landroid/graphics/Canvas;IIIILjava/lang/String;FFF)V");
            _mDrawTriangle = env->GetMethodID(clz, "DrawTriangle", "(Landroid/graphics/Canvas;IIIIFFFF)V");
            _mDrawTransRoundRect = env->GetMethodID(clz, "DrawTransRoundRect", "(Landroid/graphics/Canvas;IIIIFFFF)V");
            _mDrawFilledRoundRect = env->GetMethodID(clz, "DrawFilledRoundRect", "(Landroid/graphics/Canvas;IIIIFFFFF)V");
            _mDrawRoundRect = env->GetMethodID(clz, "DrawRoundRect", "(Landroid/graphics/Canvas;IIIIFFFFFF)V");
            _mDrawItems = env->GetMethodID(clz, "DrawItems", "(Landroid/graphics/Canvas;Ljava/lang/String;FFFF)V");
            _mDrawVehicles = env->GetMethodID(clz, "DrawVehicles", "(Landroid/graphics/Canvas;Ljava/lang/String;FFFFFF)V");
            _mDrawDeadBoxItems = env->GetMethodID(clz, "DrawDeadBoxItems", "(Landroid/graphics/Canvas;IIIILjava/lang/String;FFF)V");
            _mDrawWeapon = env->GetMethodID(clz, "DrawWeapon", "(Landroid/graphics/Canvas;IIIIIIIFFF)V");
            _mDrawUserID = env->GetMethodID(clz, "DrawUserID", "(Landroid/graphics/Canvas;IIIILjava/lang/String;FFF)V");
            _mDrawTextName = env->GetMethodID(clz, "DrawTextName", "(Landroid/graphics/Canvas;IIIILjava/lang/String;FFF)V");

            jclass cvsClz = env->FindClass("android/graphics/Canvas");
            _mGetWidth = env->GetMethodID(cvsClz, "getWidth", "()I");
            _mGetHeight = env->GetMethodID(cvsClz, "getHeight", "()I");

            env->DeleteLocalRef(clz);
            env->DeleteLocalRef(cvsClz);
            _isInitialized = true;
        }
    }

    bool isValid() const { return _env != nullptr && _cvsView != nullptr && _cvs != nullptr; }

    int getWidth() const {
        if (!isValid() || !_mGetWidth) return 0;
        return _env->CallIntMethod(_cvs, _mGetWidth);
    }

    int getHeight() const {
        if (!isValid() || !_mGetHeight) return 0;
        return _env->CallIntMethod(_cvs, _mGetHeight);
    }

    void DrawLine(Color c, float t, Vec2 s, Vec2 e) {
        if (!isValid() || !_mDrawLine) return;
        _env->CallVoidMethod(_cvsView, _mDrawLine, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, (jfloat)t, (jfloat)s.x, (jfloat)s.y, (jfloat)e.x, (jfloat)e.y);
    }

    void DrawRect(Color c, float t, Vec2 s, Vec2 e) {
        if (!isValid() || !_mDrawRect) return;
        _env->CallVoidMethod(_cvsView, _mDrawRect, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, (jfloat)t, (jfloat)s.x, (jfloat)s.y, (jfloat)e.x, (jfloat)e.y);
    }

    void DrawFilledRect(Color c, Vec2 s, Vec2 e) {
        if (!isValid() || !_mDrawFilledRect) return;
        _env->CallVoidMethod(_cvsView, _mDrawFilledRect, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, (jfloat)s.x, (jfloat)s.y, (jfloat)e.x, (jfloat)e.y);
    }

    void DrawCircle(Color c, Vec2 p, float r, float t) {
        if (!isValid() || !_mDrawCircle) return;
        _env->CallVoidMethod(_cvsView, _mDrawCircle, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, (jfloat)p.x, (jfloat)p.y, (jfloat)r, (jfloat)t);
    }

    void DrawFilledCircle(Color c, Vec2 p, float r) {
        if (!isValid() || !_mDrawFilledCircle) return;
        _env->CallVoidMethod(_cvsView, _mDrawFilledCircle, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, (jfloat)p.x, (jfloat)p.y, (jfloat)r);
    }

    void DrawText(Color c, const char* t, Vec2 p, float s) {
        if (!isValid() || !_mDrawText) return;
        jstring js = _env->NewStringUTF(t ? t : "");
        _env->CallVoidMethod(_cvsView, _mDrawText, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, js, (jfloat)p.x, (jfloat)p.y, (jfloat)s);
        _env->DeleteLocalRef(js);
    }

    void DrawName(Color c, const char* t, Vec2 p, float s) {
        if (!isValid() || !_mDrawName) return;
        jstring js = _env->NewStringUTF(t ? t : "");
        _env->CallVoidMethod(_cvsView, _mDrawName, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, js, (jfloat)p.x, (jfloat)p.y, (jfloat)s);
        _env->DeleteLocalRef(js);
    }

    void DrawTriangle(Color c, Vec2 p, float s, float a) {
        if (!isValid() || !_mDrawTriangle) return;
        _env->CallVoidMethod(_cvsView, _mDrawTriangle, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, (jfloat)p.x, (jfloat)p.y, (jfloat)s, (jfloat)a);
    }

    void DrawTransRoundRect(Color c, Vec2 s, Vec2 e) {
        if (!isValid() || !_mDrawTransRoundRect) return;
        _env->CallVoidMethod(_cvsView, _mDrawTransRoundRect, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, (jfloat)s.x, (jfloat)s.y, (jfloat)e.x, (jfloat)e.y);
    }

    void DrawFilledRoundRect(Color c, Vec2 s, Vec2 e, float rd) {
        if (!isValid() || !_mDrawFilledRoundRect) return;
        _env->CallVoidMethod(_cvsView, _mDrawFilledRoundRect, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, (jfloat)s.x, (jfloat)s.y, (jfloat)e.x, (jfloat)e.y, (jfloat)rd);
    }

    void DrawRoundRect(Color c, float t, Vec2 s, Vec2 e, float rd) {
        if (!isValid() || !_mDrawRoundRect) return;
        _env->CallVoidMethod(_cvsView, _mDrawRoundRect, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, (jfloat)t, (jfloat)s.x, (jfloat)s.y, (jfloat)e.x, (jfloat)e.y, (jfloat)rd);
    }

    void DrawItems(const char* t, float d, Vec2 p, float s) {
        if (!isValid() || !_mDrawItems) return;
        jstring js = _env->NewStringUTF(t ? t : "");
        _env->CallVoidMethod(_cvsView, _mDrawItems, _cvs, js, (jfloat)d, (jfloat)p.x, (jfloat)p.y, (jfloat)s);
        _env->DeleteLocalRef(js);
    }

    void DrawVehicles(const char* t, float d, float h, float f, Vec2 p, float s) {
        if (!isValid() || !_mDrawVehicles) return;
        jstring js = _env->NewStringUTF(t ? t : "");
        _env->CallVoidMethod(_cvsView, _mDrawVehicles, _cvs, js, (jfloat)d, (jfloat)h, (jfloat)f, (jfloat)p.x, (jfloat)p.y, (jfloat)s);
        _env->DeleteLocalRef(js);
    }

    void DrawDeadBoxItems(Color c, const char* t, Vec2 p, float s) {
        if (!isValid() || !_mDrawDeadBoxItems) return;
        jstring js = _env->NewStringUTF(t ? t : "");
        _env->CallVoidMethod(_cvsView, _mDrawDeadBoxItems, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, js, (jfloat)p.x, (jfloat)p.y, (jfloat)s);
        _env->DeleteLocalRef(js);
    }

    void DrawWeapon(Color c, int wid, int a1, int a2, Vec2 p, float s) {
        if (!isValid() || !_mDrawWeapon) return;
        _env->CallVoidMethod(_cvsView, _mDrawWeapon, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, (jint)wid, (jint)a1, (jint)a2, (jfloat)p.x, (jfloat)p.y, (jfloat)s);
    }

    void DrawUserID(Color c, const char* t, Vec2 p, float s) {
        if (!isValid() || !_mDrawUserID) return;
        jstring js = _env->NewStringUTF(t ? t : "");
        _env->CallVoidMethod(_cvsView, _mDrawUserID, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, js, (jfloat)p.x, (jfloat)p.y, (jfloat)s);
        _env->DeleteLocalRef(js);
    }

    void DrawTextName(Color c, const char* t, Vec2 p, float s) {
        if (!isValid() || !_mDrawTextName) return;
        jstring js = _env->NewStringUTF(t ? t : "");
        _env->CallVoidMethod(_cvsView, _mDrawTextName, _cvs, (jint)c.a, (jint)c.r, (jint)c.g, (jint)c.b, js, (jfloat)p.x, (jfloat)p.y, (jfloat)s);
        _env->DeleteLocalRef(js);
    }
};

#endif // ESP_ESP_H
