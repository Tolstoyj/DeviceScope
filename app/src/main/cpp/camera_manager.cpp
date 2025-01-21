#include "camera_manager.h"
#include <android/log.h>
#include <string>

#define LOG_TAG "CameraManager"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

CameraManager::CameraManager(JavaVM* jvm)
    : m_cameraManager(nullptr)
    , m_cameraDevice(nullptr)
    , m_captureSession(nullptr)
    , m_outputTarget(nullptr)
    , m_imageReader(nullptr)
    , m_window(nullptr)
    , m_isReady(false)
    , m_javaVM(jvm) {
    
    LOGD("Initializing CameraManager");
    m_cameraManager = ACameraManager_create();
    if (!m_cameraManager) {
        LOGE("Failed to create camera manager");
        return;
    }
    LOGI("Camera manager created successfully");
}

CameraManager::~CameraManager() {
    closeCamera();
    if (m_cameraManager) {
        ACameraManager_delete(m_cameraManager);
    }
}

bool CameraManager::openCamera() {
    LOGD("Opening camera...");
    ACameraIdList* cameraList = nullptr;
    camera_status_t status = ACameraManager_getCameraIdList(m_cameraManager, &cameraList);
    
    if (status != ACAMERA_OK) {
        LOGE("Failed to get camera list: %d", status);
        return false;
    }
    
    if (!cameraList || cameraList->numCameras < 1) {
        LOGE("No camera available. Number of cameras: %d", cameraList ? cameraList->numCameras : 0);
        return false;
    }
    
    // Use first available camera
    const char* cameraId = cameraList->cameraIds[0];
    LOGI("Using camera ID: %s", cameraId);
    
    ACameraDevice_StateCallbacks callbacks{};
    callbacks.context = this;
    callbacks.onDisconnected = onDisconnected;
    callbacks.onError = onError;
    
    status = ACameraManager_openCamera(m_cameraManager, cameraId, &callbacks, &m_cameraDevice);
    if (status != ACAMERA_OK) {
        LOGE("Failed to open camera: %d", status);
        ACameraManager_deleteCameraIdList(cameraList);
        return false;
    }
    
    LOGI("Camera opened successfully");
    ACameraManager_deleteCameraIdList(cameraList);
    
    if (!setupImageReader()) {
        LOGE("Failed to setup image reader");
        return false;
    }
    
    m_isReady = true;
    LOGI("Camera initialization complete");
    return true;
}

bool CameraManager::setupImageReader() {
    LOGD("Setting up image reader...");
    media_status_t status = AImageReader_new(1280, 720, AIMAGE_FORMAT_YUV_420_888, 2, &m_imageReader);
    if (status != AMEDIA_OK) {
        LOGE("Failed to create image reader: %d", status);
        return false;
    }
    
    AImageReader_ImageListener listener{};
    listener.context = this;
    listener.onImageAvailable = [](void* context, AImageReader* reader) {
        LOGD("New image available");
        AImage* image;
        if (AImageReader_acquireLatestImage(reader, &image) == AMEDIA_OK) {
            static_cast<CameraManager*>(context)->processImage(image);
            AImage_delete(image);
        }
    };
    
    AImageReader_setImageListener(m_imageReader, &listener);
    LOGI("Image reader setup complete");
    return true;
}

void CameraManager::processImage(AImage* image) {
    if (!m_frameCallback) return;
    
    uint8_t* data;
    int len;
    if (AImage_getPlaneData(image, 0, &data, &len) != AMEDIA_OK) {
        return;
    }
    
    int32_t width, height;
    AImage_getWidth(image, &width);
    AImage_getHeight(image, &height);
    
    // Get JNIEnv for current thread
    JNIEnv* env = nullptr;
    if (!m_javaVM) {
        LOGE("No JavaVM available");
        return;
    }
    
    // Attach current thread to JVM
    jint attach_result = m_javaVM->AttachCurrentThread(&env, nullptr);
    if (attach_result != 0 || !env) {
        LOGE("Failed to attach thread to JVM");
        return;
    }
    
    // Call the frame callback
    m_frameCallback(data, width, height);
    
    // Detach thread from JVM
    m_javaVM->DetachCurrentThread();
}

bool CameraManager::startPreview() {
    LOGD("Starting preview...");
    if (!m_isReady || !m_imageReader) {
        LOGE("Camera not ready or image reader not initialized");
        return false;
    }
    
    // Get the native window from image reader
    media_status_t media_status = AImageReader_getWindow(m_imageReader, &m_window);
    if (media_status != AMEDIA_OK || !m_window) {
        LOGE("Failed to get native window");
        return false;
    }
    
    // Create capture session
    ACaptureSessionOutput* sessionOutput = nullptr;
    ACameraOutputTarget* outputTarget = nullptr;
    camera_status_t camera_status;
    
    // Create output target with native window
    camera_status = ACameraOutputTarget_create(m_window, &outputTarget);
    if (camera_status != ACAMERA_OK) {
        LOGE("Failed to create camera output target: %d", camera_status);
        return false;
    }
    m_outputTarget = outputTarget;
    
    // Create session output
    camera_status = ACaptureSessionOutput_create(m_window, &sessionOutput);
    if (camera_status != ACAMERA_OK) {
        LOGE("Failed to create capture session output: %d", camera_status);
        ACameraOutputTarget_free(m_outputTarget);
        m_outputTarget = nullptr;
        return false;
    }
    
    // Create output container
    ACaptureSessionOutputContainer* outputs = nullptr;
    camera_status = ACaptureSessionOutputContainer_create(&outputs);
    if (camera_status != ACAMERA_OK) {
        LOGE("Failed to create capture session output container: %d", camera_status);
        ACaptureSessionOutput_free(sessionOutput);
        ACameraOutputTarget_free(m_outputTarget);
        m_outputTarget = nullptr;
        return false;
    }
    
    camera_status = ACaptureSessionOutputContainer_add(outputs, sessionOutput);
    if (camera_status != ACAMERA_OK) {
        LOGE("Failed to add output to container: %d", camera_status);
        ACaptureSessionOutputContainer_free(outputs);
        ACaptureSessionOutput_free(sessionOutput);
        ACameraOutputTarget_free(m_outputTarget);
        m_outputTarget = nullptr;
        return false;
    }
    
    // Create capture session
    ACameraCaptureSession_stateCallbacks callbacks{};
    callbacks.context = this;
    callbacks.onReady = [](void* context, ACameraCaptureSession* session) {
        LOGI("Capture session ready");
    };
    callbacks.onActive = [](void* context, ACameraCaptureSession* session) {
        LOGI("Capture session active");
    };
    
    camera_status = ACameraDevice_createCaptureSession(m_cameraDevice, outputs, &callbacks, &m_captureSession);
    if (camera_status != ACAMERA_OK) {
        LOGE("Failed to create capture session: %d", camera_status);
        ACaptureSessionOutputContainer_free(outputs);
        ACaptureSessionOutput_free(sessionOutput);
        ACameraOutputTarget_free(m_outputTarget);
        m_outputTarget = nullptr;
        return false;
    }
    
    // Create capture request
    ACaptureRequest* request = nullptr;
    camera_status = ACameraDevice_createCaptureRequest(m_cameraDevice, TEMPLATE_PREVIEW, &request);
    if (camera_status != ACAMERA_OK) {
        LOGE("Failed to create capture request: %d", camera_status);
        return false;
    }
    
    // Add target to request
    camera_status = ACaptureRequest_addTarget(request, m_outputTarget);
    if (camera_status != ACAMERA_OK) {
        LOGE("Failed to add target to request: %d", camera_status);
        ACaptureRequest_free(request);
        return false;
    }
    
    // Start repeating request
    camera_status = ACameraCaptureSession_setRepeatingRequest(m_captureSession, nullptr, 1, &request, nullptr);
    if (camera_status != ACAMERA_OK) {
        LOGE("Failed to start repeating request: %d", camera_status);
        ACaptureRequest_free(request);
        return false;
    }
    
    ACaptureRequest_free(request);
    LOGI("Preview started successfully");
    return true;
}

void CameraManager::stopPreview() {
    if (m_captureSession) {
        ACameraCaptureSession_stopRepeating(m_captureSession);
        ACameraCaptureSession_close(m_captureSession);
        m_captureSession = nullptr;
    }
}

void CameraManager::closeCamera() {
    stopPreview();
    
    if (m_cameraDevice) {
        ACameraDevice_close(m_cameraDevice);
        m_cameraDevice = nullptr;
    }
    
    if (m_imageReader) {
        AImageReader_delete(m_imageReader);
        m_imageReader = nullptr;
    }
    
    m_isReady = false;
}

void CameraManager::setFrameCallback(std::function<void(const uint8_t*, size_t, size_t)> callback) {
    m_frameCallback = std::move(callback);
}

bool CameraManager::isCameraReady() const {
    return m_isReady;
}

void CameraManager::onDisconnected(void* context, ACameraDevice* device) {
    auto* manager = static_cast<CameraManager*>(context);
    LOGE("Camera disconnected");
    manager->m_isReady = false;
}

void CameraManager::onError(void* context, ACameraDevice* device, int error) {
    auto* manager = static_cast<CameraManager*>(context);
    LOGE("Camera error: %d", error);
    manager->m_isReady = false;
} 