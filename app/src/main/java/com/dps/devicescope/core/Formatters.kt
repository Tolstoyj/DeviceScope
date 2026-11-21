package com.dps.devicescope.core

import com.dps.devicescope.data.model.StatusLevel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

object Formatters {

    fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "Unknown"
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unitIndex = 0
        while (value >= 1024.0 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex++
        }
        return if (unitIndex <= 1) {
            String.format(Locale.US, "%.0f %s", value, units[unitIndex])
        } else {
            String.format(Locale.US, "%.2f %s", value, units[unitIndex])
        }
    }

    fun formatFrequencyKhz(khz: Long?): String {
        if (khz == null || khz <= 0L) return "N/A"
        return if (khz >= 1_000_000L) {
            String.format(Locale.US, "%.2f GHz", khz / 1_000_000.0)
        } else {
            String.format(Locale.US, "%.0f MHz", khz / 1_000.0)
        }
    }

    fun formatUptime(elapsedMs: Long): String {
        if (elapsedMs < 0L) return "0m"
        val totalSeconds = elapsedMs / 1000L
        val days = totalSeconds / 86_400L
        val hours = (totalSeconds % 86_400L) / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0 || days > 0) append("${hours}h ")
            if (minutes > 0 || hours > 0 || days > 0) append("${minutes}m ")
            append("${seconds}s")
        }.trim()
    }

    /**
     * Converts PCM 16-bit RMS amplitude to dBFS in [-90.0, 0.0], avoiding -Infinity on silence.
     */
    fun calculateAudioDbfs(
        rms: Double,
        maxAmplitude: Double = Short.MAX_VALUE.toDouble()
    ): Float {
        if (rms <= 0.0 || maxAmplitude <= 0.0) return -90f
        val db = 20.0 * log10(rms / maxAmplitude)
        return db.coerceIn(-90.0, 0.0).toFloat()
    }

    /**
     * Maps dBFS onto a perceptual 0..1 bar/waveform amplitude with a clean noise floor.
     */
    fun normalizeDbfsToLevel(dbfs: Float, floorDb: Float = -65f): Float {
        if (floorDb >= 0f) return 0f
        return ((dbfs - floorDb) / (0f - floorDb)).coerceIn(0f, 1f)
    }

    fun calculatePcmRmsAndPeak(samples: ShortArray, count: Int): Pair<Double, Int> {
        val safeCount = count.coerceAtMost(samples.size)
        if (safeCount <= 0) return 0.0 to 0
        var sumSq = 0.0
        var peak = 0
        for (i in 0 until safeCount) {
            val s = samples[i].toInt()
            val absS = abs(s)
            if (absS > peak) peak = absS
            sumSq += s.toDouble() * s.toDouble()
        }
        return sqrt(sumSq / safeCount) to peak
    }

    fun formatMegapixels(width: Int, height: Int): String {
        if (width <= 0 || height <= 0) return "Unknown"
        val mp = (width.toLong() * height.toLong()) / 1_000_000.0
        return String.format(Locale.US, "%d × %d (%.1f MP)", width, height, mp)
    }

    fun classifyWifiBand(frequencyMhz: Int): String = when (frequencyMhz) {
        in 2400..2500 -> "2.4 GHz"
        in 4900..5900 -> "5 GHz"
        in 5925..7125 -> "6 GHz"
        else -> if (frequencyMhz > 0) "$frequencyMhz MHz" else "Unknown"
    }

    fun formatBearing(degrees: Float): String {
        val normalized = ((degrees % 360f) + 360f) % 360f
        val directions = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val index = ((normalized + 22.5f) / 45f).toInt() % 8
        return String.format(Locale.US, "%.0f° %s", normalized, directions[index])
    }

    fun formatDistanceMeters(meters: Float): String {
        if (meters < 0f) return "0 m"
        return if (meters >= 1000f) {
            String.format(Locale.US, "%.2f km", meters / 1000f)
        } else {
            String.format(Locale.US, "%.1f m", meters)
        }
    }

    fun formatCoordinateDms(value: Double, isLatitude: Boolean): String {
        val hemi = if (isLatitude) {
            if (value >= 0) "N" else "S"
        } else {
            if (value >= 0) "E" else "W"
        }
        val absVal = abs(value)
        val deg = floor(absVal).toInt()
        val minFloat = (absVal - deg) * 60.0
        val min = floor(minFloat).toInt()
        val sec = (minFloat - min) * 60.0
        return String.format(Locale.US, "%d°%02d'%04.1f\"%s", deg, min, sec, hemi)
    }

    fun formatPowerWatts(voltageMv: Int, currentMa: Float?): String {
        if (voltageMv <= 0 || currentMa == null) return "N/A"
        val watts = (voltageMv / 1000.0) * (abs(currentMa) / 1000.0)
        return String.format(Locale.US, "%.2f W", watts)
    }

    fun classifyBatteryLevel(percent: Int): StatusLevel = when {
        percent >= 50 -> StatusLevel.GOOD
        percent >= 20 -> StatusLevel.WARNING
        else -> StatusLevel.CRITICAL
    }

    fun classifyTemperatureC(tempC: Float): StatusLevel = when {
        tempC >= 43f -> StatusLevel.CRITICAL
        tempC >= 38f -> StatusLevel.WARNING
        tempC <= 5f -> StatusLevel.INFO
        else -> StatusLevel.GOOD
    }

    fun classifySignalLevel(level: Int): Pair<String, StatusLevel> = when {
        level >= 4 -> "Excellent" to StatusLevel.GOOD
        level == 3 -> "Good" to StatusLevel.GOOD
        level == 2 -> "Fair" to StatusLevel.WARNING
        level == 1 -> "Poor" to StatusLevel.CRITICAL
        else -> "No Signal" to StatusLevel.CRITICAL
    }

    fun calculateVectorMagnitude(x: Float, y: Float, z: Float): Float {
        return sqrt((x * x + y * y + z * z).toDouble()).toFloat()
    }

    fun formatPercentage(used: Long, total: Long): Int {
        if (total <= 0L) return 0
        return ((used.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
    }
}
