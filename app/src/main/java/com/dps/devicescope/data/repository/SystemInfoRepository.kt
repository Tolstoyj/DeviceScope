package com.dps.devicescope.data.repository

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.dps.devicescope.data.model.CpuCoreFrequency
import com.dps.devicescope.data.model.SystemSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.io.File

class SystemInfoRepository(private val context: Context) {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    private val cachedCpuHardwareLine: String by lazy { parseCpuInfoHardware() }

    fun observeSystem(intervalMs: Long = 1500L): Flow<SystemSnapshot> = flow {
        while (currentCoroutineContext().isActive) {
            emit(captureSnapshot())
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    fun captureSnapshot(): SystemSnapshot {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalRam = memInfo.totalMem
        val availRam = memInfo.availMem
        val usedRam = (totalRam - availRam).coerceAtLeast(0L)

        val (totalStorage, availStorage) = readInternalStorage()
        val usedStorage = (totalStorage - availStorage).coerceAtLeast(0L)

        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val cores = (0 until coreCount).map { idx ->
            CpuCoreFrequency(
                coreIndex = idx,
                currentKhz = readFrequencyKhz("/sys/devices/system/cpu/cpu$idx/cpufreq/scaling_cur_freq"),
                minKhz = readFrequencyKhz("/sys/devices/system/cpu/cpu$idx/cpufreq/cpuinfo_min_freq"),
                maxKhz = readFrequencyKhz("/sys/devices/system/cpu/cpu$idx/cpufreq/cpuinfo_max_freq")
            )
        }

        val socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: "Unknown"
        } else {
            "N/A (Android 12+)"
        }

        val socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: Build.HARDWARE
        } else {
            Build.HARDWARE
        }

        return SystemSnapshot(
            manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            brand = Build.BRAND.replaceFirstChar { it.uppercase() },
            model = Build.MODEL,
            device = Build.DEVICE,
            board = Build.BOARD,
            hardware = Build.HARDWARE,
            socManufacturer = socManufacturer,
            socModel = socModel,
            cpuHardwareLine = cachedCpuHardwareLine,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            cpuCoreCount = coreCount,
            coreFrequencies = cores,
            totalRamBytes = totalRam,
            availableRamBytes = availRam,
            usedRamBytes = usedRam,
            isLowMemory = memInfo.lowMemory,
            totalStorageBytes = totalStorage,
            availableStorageBytes = availStorage,
            usedStorageBytes = usedStorage,
            androidVersion = Build.VERSION.RELEASE,
            sdkInt = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH.ifBlank { "Unknown" },
            buildDisplay = Build.DISPLAY,
            buildFingerprint = Build.FINGERPRINT,
            kernelVersion = System.getProperty("os.version") ?: "Unknown",
            uptimeMs = SystemClock.elapsedRealtime()
        )
    }

    private fun readInternalStorage(): Pair<Long, Long> = runCatching {
        val stat = StatFs(Environment.getDataDirectory().path)
        stat.totalBytes to stat.availableBytes
    }.getOrDefault(0L to 0L)

    private fun readFrequencyKhz(path: String): Long? = runCatching {
        val file = File(path)
        if (!file.canRead()) return@runCatching null
        // Filter out non-kHz virtualized placeholder integers (e.g. 1/2 on emulators)
        file.readText().trim().toLongOrNull()?.takeIf { it >= 10_000L }
    }.getOrNull()

    private fun parseCpuInfoHardware(): String = runCatching {
        val file = File("/proc/cpuinfo")
        if (!file.canRead()) return@runCatching Build.HARDWARE
        val lines = file.readLines()
        val match = lines.firstOrNull { line ->
            val key = line.substringBefore(":").trim()
            key.equals("Hardware", ignoreCase = true) ||
                key.equals("model name", ignoreCase = true)
        }
        val parsed = match?.substringAfter(":")?.trim()
        if (!parsed.isNullOrBlank() && parsed.toIntOrNull() == null) {
            parsed
        } else {
            Build.HARDWARE
        }
    }.getOrDefault(Build.HARDWARE)
}
