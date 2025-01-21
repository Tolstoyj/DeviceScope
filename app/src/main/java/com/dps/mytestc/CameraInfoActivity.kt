package com.dps.mytestc

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.os.Bundle
import android.util.Size
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dps.mytestc.databinding.ActivityCameraInfoBinding

class CameraInfoActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCameraInfoBinding
    private lateinit var cameraManager: CameraManager

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager

        if (checkCameraPermission()) {
            loadCameraInfo()
        } else {
            requestCameraPermission()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            PERMISSION_REQUEST_CODE
        )
    }

    private fun loadCameraInfo() {
        try {
            val cameraIds = cameraManager.cameraIdList
            binding.cameraCountValue.text = "${cameraIds.size} camera(s) found"

            cameraIds.forEach { cameraId ->
                addCameraInfo(cameraId)
            }
        } catch (e: Exception) {
            binding.cameraCountValue.text = "Failed to get camera info"
        }
    }

    private fun addCameraInfo(cameraId: String) {
        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
        val view = layoutInflater.inflate(R.layout.item_camera_info, binding.cameraListContainer, false)

        // Set camera title
        val lensFacing = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_FRONT -> "Front Camera"
            CameraCharacteristics.LENS_FACING_BACK -> "Back Camera"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External Camera"
            else -> "Unknown Camera"
        }
        view.findViewById<TextView>(R.id.cameraTitle).text = "$lensFacing (ID: $cameraId)"

        // Set lens facing
        view.findViewById<TextView>(R.id.lensFacing).text = when (characteristics.get(CameraCharacteristics.LENS_FACING)) {
            CameraCharacteristics.LENS_FACING_FRONT -> "Front"
            CameraCharacteristics.LENS_FACING_BACK -> "Back"
            CameraCharacteristics.LENS_FACING_EXTERNAL -> "External"
            else -> "Unknown"
        }

        // Set max resolution
        val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val maxResolution = streamConfigMap?.getOutputSizes(android.graphics.ImageFormat.JPEG)
            ?.maxByOrNull { it.width * it.height }
        view.findViewById<TextView>(R.id.maxResolution).text = if (maxResolution != null) {
            "${maxResolution.width} x ${maxResolution.height}"
        } else {
            "Unknown"
        }

        // Set hardware level
        val hardwareLevel = when (characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)) {
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY -> "Legacy"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED -> "Limited"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL -> "Full"
            CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3 -> "Level 3"
            else -> "Unknown"
        }
        view.findViewById<TextView>(R.id.hardwareLevel).text = hardwareLevel

        // Set flash availability
        val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
        view.findViewById<TextView>(R.id.flashAvailable).text = if (hasFlash) "Yes" else "No"

        // Set supported features
        val features = mutableListOf<String>()
        
        // Check for auto-focus
        val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        if (afModes != null && afModes.contains(CameraMetadata.CONTROL_AF_MODE_AUTO)) {
            features.add("Auto Focus")
        }

        // Check for optical stabilization
        val stabilizationModes = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
        if (stabilizationModes != null && stabilizationModes.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)) {
            features.add("Video Stabilization")
        }

        // Check for RAW capability
        if (streamConfigMap?.isOutputSupportedFor(android.graphics.ImageFormat.RAW_SENSOR) == true) {
            features.add("RAW Capture")
        }

        // Check for manual sensor control
        if (characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.contains(
                CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR) == true) {
            features.add("Manual Controls")
        }

        view.findViewById<TextView>(R.id.supportedFeatures).text = if (features.isNotEmpty()) {
            features.joinToString("\n")
        } else {
            "Basic features only"
        }

        // Set FPS ranges
        val fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        val fpsText = fpsRanges?.joinToString("\n") { range ->
            "${range.lower}-${range.upper} fps"
        } ?: "Unknown"
        view.findViewById<TextView>(R.id.fpsRanges).text = fpsText

        binding.cameraListContainer.addView(view)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadCameraInfo()
            } else {
                binding.cameraCountValue.text = "Camera permission required"
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 