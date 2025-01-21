#include <jni.h>
#include <string>
#include <memory>
#include "camera_manager.h"
#include <android/log.h>

#define LOG_TAG "NativeLib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// Global camera manager instances for each activity
static std::unique_ptr<CameraManager> g_mainActivityCameraManager;
static std::unique_ptr<CameraManager> g_scannerActivityCameraManager;
static JavaVM* g_javaVM = nullptr;
static jclass g_frameCallbackClass = nullptr;
static jmethodID g_onFrameMethodId = nullptr;
static jobject g_frameCallback = nullptr;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_javaVM = vm;
    JNIEnv* env;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}

// MainActivity native methods
extern "C" JNIEXPORT jboolean JNICALL
Java_com_dps_mytestc_MainActivity_initializeCamera(JNIEnv* env, jobject /* this */) {
    LOGD("MainActivity: Initializing camera from JNI");
    if (!g_mainActivityCameraManager) {
        LOGD("Creating new CameraManager instance for MainActivity");
        g_mainActivityCameraManager = std::make_unique<CameraManager>(g_javaVM);
    }
    bool result = g_mainActivityCameraManager->openCamera();
    LOGI("MainActivity: Camera initialization %s", result ? "successful" : "failed");
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dps_mytestc_MainActivity_startCamera(JNIEnv* env, jobject /* this */) {
    LOGD("MainActivity: Starting camera preview from JNI");
    if (!g_mainActivityCameraManager) {
        LOGE("MainActivity: CameraManager not initialized");
        return false;
    }
    bool result = g_mainActivityCameraManager->startPreview();
    LOGI("MainActivity: Camera preview start %s", result ? "successful" : "failed");
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_dps_mytestc_MainActivity_stopCamera(JNIEnv* env, jobject /* this */) {
    LOGD("MainActivity: Stopping camera from JNI");
    if (g_mainActivityCameraManager) {
        g_mainActivityCameraManager->stopPreview();
        g_mainActivityCameraManager->closeCamera();
    }
    LOGI("MainActivity: Camera stopped");
}

// ScannerActivity native methods
extern "C" JNIEXPORT jboolean JNICALL
Java_com_dps_mytestc_ScannerActivity_initializeCamera(JNIEnv* env, jobject /* this */) {
    LOGD("ScannerActivity: Initializing camera from JNI");
    if (!g_scannerActivityCameraManager) {
        LOGD("Creating new CameraManager instance for ScannerActivity");
        g_scannerActivityCameraManager = std::make_unique<CameraManager>(g_javaVM);
    }
    bool result = g_scannerActivityCameraManager->openCamera();
    LOGI("ScannerActivity: Camera initialization %s", result ? "successful" : "failed");
    return result;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_dps_mytestc_ScannerActivity_startCamera(JNIEnv* env, jobject /* this */) {
    LOGD("ScannerActivity: Starting camera preview from JNI");
    if (!g_scannerActivityCameraManager) {
        LOGE("ScannerActivity: CameraManager not initialized");
        return false;
    }
    bool result = g_scannerActivityCameraManager->startPreview();
    LOGI("ScannerActivity: Camera preview start %s", result ? "successful" : "failed");
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_dps_mytestc_ScannerActivity_stopCamera(JNIEnv* env, jobject /* this */) {
    LOGD("ScannerActivity: Stopping camera from JNI");
    if (g_scannerActivityCameraManager) {
        g_scannerActivityCameraManager->stopPreview();
        g_scannerActivityCameraManager->closeCamera();
    }
    LOGI("ScannerActivity: Camera stopped");
}

extern "C" JNIEXPORT void JNICALL
Java_com_dps_mytestc_ScannerActivity_setFrameCallback(JNIEnv* env, jobject /* this */, jobject callback) {
    LOGD("ScannerActivity: Setting frame callback from JNI");
    if (!g_scannerActivityCameraManager) {
        LOGE("ScannerActivity: CameraManager not initialized");
        return;
    }

    // Clear previous global references if they exist
    if (g_frameCallback != nullptr) {
        env->DeleteGlobalRef(g_frameCallback);
        g_frameCallback = nullptr;
    }
    if (g_frameCallbackClass != nullptr) {
        env->DeleteGlobalRef(g_frameCallbackClass);
        g_frameCallbackClass = nullptr;
    }

    // Store new global references
    g_frameCallback = env->NewGlobalRef(callback);
    jclass localCallbackClass = env->GetObjectClass(callback);
    g_frameCallbackClass = (jclass)env->NewGlobalRef(localCallbackClass);
    g_onFrameMethodId = env->GetMethodID(g_frameCallbackClass, "onFrame", "([BII)V");
    env->DeleteLocalRef(localCallbackClass);

    // Set the frame callback
    g_scannerActivityCameraManager->setFrameCallback([](const uint8_t* data, size_t width, size_t height) {
        JNIEnv* env;
        // Attach current thread to JVM
        jint attachResult = g_javaVM->AttachCurrentThread(&env, nullptr);
        if (attachResult != JNI_OK || !env) {
            LOGE("Failed to attach thread to JVM");
            return;
        }

        // Create byte array for the frame data
        jbyteArray byteArray = env->NewByteArray(width * height * 3/2); // YUV420 format
        if (byteArray != nullptr) {
            env->SetByteArrayRegion(byteArray, 0, width * height * 3/2, reinterpret_cast<const jbyte*>(data));
            
            // Call the Java callback
            env->CallVoidMethod(g_frameCallback, g_onFrameMethodId, byteArray, width, height);
            
            // Cleanup local reference
            env->DeleteLocalRef(byteArray);
        }

        // Detach thread from JVM
        g_javaVM->DetachCurrentThread();
    });
    LOGI("ScannerActivity: Frame callback set successfully");
}

extern "C" JNIEXPORT void JNICALL
Java_com_dps_mytestc_MainActivity_setFrameCallback(JNIEnv* env, jobject /* this */, jobject callback) {
    // Same implementation as ScannerActivity but using g_mainActivityCameraManager
    LOGD("MainActivity: Setting frame callback from JNI");
    if (!g_mainActivityCameraManager) {
        LOGE("MainActivity: CameraManager not initialized");
        return;
    }

    // Clear previous global references if they exist
    if (g_frameCallback != nullptr) {
        env->DeleteGlobalRef(g_frameCallback);
        g_frameCallback = nullptr;
    }
    if (g_frameCallbackClass != nullptr) {
        env->DeleteGlobalRef(g_frameCallbackClass);
        g_frameCallbackClass = nullptr;
    }

    // Store new global references
    g_frameCallback = env->NewGlobalRef(callback);
    jclass localCallbackClass = env->GetObjectClass(callback);
    g_frameCallbackClass = (jclass)env->NewGlobalRef(localCallbackClass);
    g_onFrameMethodId = env->GetMethodID(g_frameCallbackClass, "onFrame", "([BII)V");
    env->DeleteLocalRef(localCallbackClass);

    // Set the frame callback
    g_mainActivityCameraManager->setFrameCallback([](const uint8_t* data, size_t width, size_t height) {
        JNIEnv* env;
        // Attach current thread to JVM
        jint attachResult = g_javaVM->AttachCurrentThread(&env, nullptr);
        if (attachResult != JNI_OK || !env) {
            LOGE("Failed to attach thread to JVM");
            return;
        }

        // Create byte array for the frame data
        jbyteArray byteArray = env->NewByteArray(width * height * 3/2); // YUV420 format
        if (byteArray != nullptr) {
            env->SetByteArrayRegion(byteArray, 0, width * height * 3/2, reinterpret_cast<const jbyte*>(data));
            
            // Call the Java callback
            env->CallVoidMethod(g_frameCallback, g_onFrameMethodId, byteArray, width, height);
            
            // Cleanup local reference
            env->DeleteLocalRef(byteArray);
        }

        // Detach thread from JVM
        g_javaVM->DetachCurrentThread();
    });
    LOGI("MainActivity: Frame callback set successfully");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_dps_mytestc_MainActivity_stringFromJNI(JNIEnv* env, jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}