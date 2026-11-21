package com.dps.devicescope.data.repository

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.Build
import android.util.DisplayMetrics
import android.view.Display
import android.view.WindowManager
import com.dps.devicescope.data.model.DisplayGpuSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class DisplayGpuRepository(private val context: Context) {

    private data class GpuStrings(
        val renderer: String,
        val vendor: String,
        val version: String
    )

    @Volatile
    private var cachedGpu: GpuStrings? = null

    suspend fun loadSnapshot(): DisplayGpuSnapshot = withContext(Dispatchers.Default) {
        captureSnapshot()
    }

    fun captureSnapshot(): DisplayGpuSnapshot {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
        val dm = context.resources.displayMetrics

        val (widthPx, heightPx) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val realMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            display?.getRealMetrics(realMetrics)
            if (realMetrics.widthPixels > 0) {
                realMetrics.widthPixels to realMetrics.heightPixels
            } else {
                dm.widthPixels to dm.heightPixels
            }
        }

        val density = dm.density.coerceAtLeast(0.5f)
        val widthDp = (widthPx / density).roundToInt()
        val heightDp = (heightPx / density).roundToInt()

        val xdpi = dm.xdpi.takeIf { it > 0f } ?: dm.densityDpi.toFloat()
        val ydpi = dm.ydpi.takeIf { it > 0f } ?: dm.densityDpi.toFloat()
        val diagonalInches = sqrt(
            (widthPx / xdpi).toDouble().pow(2.0) + (heightPx / ydpi).toDouble().pow(2.0)
        )

        val activeRefreshHz = display?.refreshRate ?: 60f
        val supportedRates = display?.supportedModes
            ?.map { (it.refreshRate * 10f).roundToInt() / 10f }
            ?.distinct()
            ?.sorted()
            ?: listOf(activeRefreshHz)

        val hdrTypes = extractHdrTypes(display)
        val isHdr = display?.isHdr == true || hdrTypes.isNotEmpty()
        val isWideColorGamut = context.resources.configuration.isScreenWideColorGamut

        val orientation = when (context.resources.configuration.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> "Landscape"
            Configuration.ORIENTATION_PORTRAIT -> "Portrait"
            else -> "Undefined"
        }

        val glEsVersion = activityManager.deviceConfigurationInfo.glEsVersion ?: "Unknown"
        val gpu = cachedGpu ?: queryGpuViaEgl().also { cachedGpu = it }

        val (vulkanSupported, vulkanLevel, vulkanVersion) = queryVulkanSupport()

        return DisplayGpuSnapshot(
            widthPx = widthPx,
            heightPx = heightPx,
            widthDp = widthDp,
            heightDp = heightDp,
            densityDpi = dm.densityDpi,
            densityScale = density,
            xdpi = xdpi,
            ydpi = ydpi,
            diagonalInches = diagonalInches,
            activeRefreshRateHz = activeRefreshHz,
            supportedRefreshRatesHz = supportedRates,
            isHdrSupported = isHdr,
            hdrTypes = hdrTypes,
            isWideColorGamut = isWideColorGamut,
            orientation = orientation,
            openGlEsVersion = glEsVersion,
            gpuRenderer = gpu.renderer,
            gpuVendor = gpu.vendor,
            gpuGlVersion = gpu.version,
            vulkanSupported = vulkanSupported,
            vulkanHardwareLevel = vulkanLevel,
            vulkanVersion = vulkanVersion
        )
    }

    private fun extractHdrTypes(display: Display?): List<String> {
        if (display == null) return emptyList()
        val rawTypes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            display.mode.supportedHdrTypes
        } else {
            @Suppress("DEPRECATION")
            display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
        }
        return rawTypes.map { type ->
            when (type) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> "Dolby Vision"
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> "HDR10"
                Display.HdrCapabilities.HDR_TYPE_HLG -> "HLG"
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> "HDR10+"
                else -> "HDR Type $type"
            }
        }.distinct()
    }

    private fun queryVulkanSupport(): Triple<Boolean, String, String> {
        val features = context.packageManager.systemAvailableFeatures
        val levelFeature = features.firstOrNull {
            it.name == PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL
        }
        val versionFeature = features.firstOrNull {
            it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION
        }
        if (levelFeature == null && versionFeature == null) {
            return Triple(false, "Not Supported", "N/A")
        }
        val level = levelFeature?.version?.let { "Level $it" } ?: "Supported"
        val version = versionFeature?.version?.let { packed ->
            val major = (packed shr 22) and 0x3FF
            val minor = (packed shr 12) and 0x3FF
            val patch = packed and 0xFFF
            String.format(Locale.US, "%d.%d.%d", major, minor, patch)
        } ?: "Unknown"
        return Triple(true, level, version)
    }

    private fun queryGpuViaEgl(): GpuStrings = runCatching {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            return@runCatching GpuStrings("Unavailable", "Unavailable", "Unavailable")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            return@runCatching GpuStrings("Unavailable", "Unavailable", "Unavailable")
        }

        try {
            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) ||
                numConfigs[0] == 0 || configs[0] == null
            ) {
                return@runCatching GpuStrings("Unavailable", "Unavailable", "Unavailable")
            }

            val surfaceAttribs = intArrayOf(
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
            )
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0)
            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            val eglContext = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0
            )

            try {
                if (surface == EGL14.EGL_NO_SURFACE || eglContext == EGL14.EGL_NO_CONTEXT) {
                    return@runCatching GpuStrings("Unavailable", "Unavailable", "Unavailable")
                }
                EGL14.eglMakeCurrent(display, surface, surface, eglContext)
                val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "Unknown"
                val vendor = GLES20.glGetString(GLES20.GL_VENDOR) ?: "Unknown"
                val glVersion = GLES20.glGetString(GLES20.GL_VERSION) ?: "Unknown"
                GpuStrings(renderer, vendor, glVersion)
            } finally {
                EGL14.eglMakeCurrent(
                    display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                )
                if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
                if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, eglContext)
            }
        } finally {
            EGL14.eglTerminate(display)
        }
    }.getOrElse {
        GpuStrings("Unavailable", "Unavailable", "Unavailable")
    }
}
