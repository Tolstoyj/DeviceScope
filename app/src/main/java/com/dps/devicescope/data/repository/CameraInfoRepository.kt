package com.dps.devicescope.data.repository

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import com.dps.devicescope.data.model.CameraSnapshot
import com.dps.devicescope.data.model.CameraSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class CameraInfoRepository(private val context: Context) {

    private val cameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    suspend fun loadCameras(): CameraSnapshot = withContext(Dispatchers.IO) {
        captureSnapshot()
    }

    fun captureSnapshot(): CameraSnapshot = runCatching {
        val specs = cameraManager.cameraIdList.map { cameraId ->
            val chars = cameraManager.getCameraCharacteristics(cameraId)

            val lensFacing = when (chars.get(CameraCharacteristics.LENS_FACING)) {
                CameraCharacteristics.LENS_FACING_BACK -> "Back"
                CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
                else -> "Unknown"
            }

            val hardwareLevel = when (chars.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
                CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_EXTERNAL -> "External"
                else -> "Unknown"
            }

            val streamConfigMap = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val maxSize = streamConfigMap?.getOutputSizes(ImageFormat.JPEG)
                ?.maxByOrNull { it.width.toLong() * it.height.toLong() }

            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.toList() ?: emptyList()
            val apertures = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)
                ?.toList() ?: emptyList()

            val physicalSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val sensorSizeStr = if (physicalSize != null) {
                String.format(Locale.US, "%.2f × %.2f mm", physicalSize.width, physicalSize.height)
            } else {
                "Unknown"
            }

            val orientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                ?.map { "${it.lower}–${it.upper} fps" }
                ?.distinct()
                ?: emptyList()

            val capabilities = buildList {
                val afModes = chars.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
                if (afModes != null && afModes.any { it != CameraMetadata.CONTROL_AF_MODE_OFF }) {
                    add("Auto Focus")
                }
                val oisModes = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                if (oisModes?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true) {
                    add("Optical Stabilization (OIS)")
                }
                val videoStab = chars.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
                if (videoStab?.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON) == true) {
                    add("Video Stabilization")
                }
                if (streamConfigMap?.isOutputSupportedFor(ImageFormat.RAW_SENSOR) == true) {
                    add("RAW Capture")
                }
                val reqCaps = chars.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
                if (reqCaps?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true) {
                    add("Manual Sensor")
                }
                if (reqCaps?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT) == true) {
                    add("Depth Output")
                }
                if (reqCaps?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA) == true) {
                    add("Logical Multi-Camera")
                }
                if (reqCaps?.contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE) == true) {
                    add("Burst Capture")
                }
            }

            val physicalIds = runCatching { chars.physicalCameraIds.sorted() }.getOrDefault(emptyList())

            CameraSpec(
                id = cameraId,
                lensFacing = lensFacing,
                hardwareLevel = hardwareLevel,
                maxJpegWidth = maxSize?.width ?: 0,
                maxJpegHeight = maxSize?.height ?: 0,
                hasFlash = hasFlash,
                focalLengthsMm = focalLengths,
                apertures = apertures,
                sensorSizeMm = sensorSizeStr,
                sensorOrientationDeg = orientation,
                fpsRanges = fpsRanges,
                capabilities = capabilities,
                physicalCameraIds = physicalIds
            )
        }
        CameraSnapshot(cameras = specs)
    }.getOrElse { e ->
        CameraSnapshot(cameras = emptyList(), error = e.localizedMessage ?: "Failed to read cameras")
    }
}
