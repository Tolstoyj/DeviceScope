#ifndef CAMERA_MANAGER_H
#define CAMERA_MANAGER_H

#include <jni.h>
#include <functional>
#include <cstddef>
#include <android/native_window.h>
#include <camera/NdkCameraError.h>
#include <camera/NdkCameraManager.h>
#include <camera/NdkCameraDevice.h>
#include <camera/NdkCameraMetadata.h>
#include <camera/NdkCameraCaptureSession.h>
#include <camera/NdkCaptureRequest.h>
#include <media/NdkImageReader.h>

class CameraManager {
public:
    CameraManager(JavaVM* jvm);
    ~CameraManager();
    
    bool openCamera();
    void closeCamera();
    bool startPreview();
    void stopPreview();
    bool isCameraReady() const;
    
    // Callback for frame processing
    void setFrameCallback(std::function<void(const uint8_t*, size_t, size_t)> callback);

private:
    ACameraManager* m_cameraManager;
    ACameraDevice* m_cameraDevice;
    ACameraCaptureSession* m_captureSession;
    ACameraOutputTarget* m_outputTarget;
    AImageReader* m_imageReader;
    ANativeWindow* m_window;
    
    bool setupImageReader();
    void processImage(AImage* image);
    
    static void onDisconnected(void* context, ACameraDevice* device);
    static void onError(void* context, ACameraDevice* device, int error);
    
    std::function<void(const uint8_t*, size_t, size_t)> m_frameCallback;
    bool m_isReady;
    JavaVM* m_javaVM;
};

#endif // CAMERA_MANAGER_H 