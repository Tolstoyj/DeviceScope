package com.dps.devicescope

import com.dps.devicescope.core.Formatters
import com.dps.devicescope.data.model.StatusLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormattersTest {

    @Test
    fun formatBytes_scalesAcrossUnits() {
        assertEquals("0 B", Formatters.formatBytes(0))
        assertEquals("512 B", Formatters.formatBytes(512))
        assertEquals("1 KB", Formatters.formatBytes(1024))
        assertEquals("1.50 MB", Formatters.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("8.00 GB", Formatters.formatBytes(8L * 1024 * 1024 * 1024))
        assertEquals("Unknown", Formatters.formatBytes(-5))
    }

    @Test
    fun formatFrequencyKhz_formatsMhzAndGhz() {
        assertEquals("N/A", Formatters.formatFrequencyKhz(null))
        assertEquals("N/A", Formatters.formatFrequencyKhz(0))
        assertEquals("800 MHz", Formatters.formatFrequencyKhz(800_000))
        assertEquals("2.45 GHz", Formatters.formatFrequencyKhz(2_450_000))
    }

    @Test
    fun formatUptime_formatsDaysHoursMinutesSeconds() {
        assertEquals("45s", Formatters.formatUptime(45_000L))
        assertEquals("2m 5s", Formatters.formatUptime(125_000L))
        assertEquals("1h 1m 1s", Formatters.formatUptime(3_661_000L))
        assertEquals("1d 0h 0m 0s", Formatters.formatUptime(86_400_000L))
    }

    @Test
    fun audioDbfsAndNormalization_handlesSilenceAndFullScaleSafely() {
        // Silence must clamp to -90 dBFS rather than -Infinity or NaN
        val silenceDb = Formatters.calculateAudioDbfs(0.0)
        assertEquals(-90f, silenceDb, 0.01f)
        assertEquals(0f, Formatters.normalizeDbfsToLevel(silenceDb), 0.001f)

        // Full scale sine/square peak -> 0 dBFS -> 1.0 normalized
        val fullScaleDb = Formatters.calculateAudioDbfs(Short.MAX_VALUE.toDouble())
        assertEquals(0f, fullScaleDb, 0.01f)
        assertEquals(1f, Formatters.normalizeDbfsToLevel(fullScaleDb), 0.001f)

        // Mid-level speech (~-20 dBFS) should produce a clearly visible waveform (> 0.6)
        val speechLevel = Formatters.normalizeDbfsToLevel(-20f)
        assertTrue("Expected visible speech waveform level, got $speechLevel", speechLevel > 0.6f)
    }

    @Test
    fun calculatePcmRmsAndPeak_computesExpectedValues() {
        val samples = shortArrayOf(1000, -1000, 1000, -1000)
        val (rms, peak) = Formatters.calculatePcmRmsAndPeak(samples, samples.size)
        assertEquals(1000.0, rms, 0.001)
        assertEquals(1000, peak)
    }

    @Test
    fun wifiBandClassification_maps24_5_6Ghz() {
        assertEquals("2.4 GHz", Formatters.classifyWifiBand(2437))
        assertEquals("5 GHz", Formatters.classifyWifiBand(5180))
        assertEquals("6 GHz", Formatters.classifyWifiBand(6175))
        assertEquals("Unknown", Formatters.classifyWifiBand(0))
    }

    @Test
    fun formatBearingAndDistance_formatsCleanly() {
        assertEquals("0° N", Formatters.formatBearing(0f))
        assertEquals("90° E", Formatters.formatBearing(90f))
        assertEquals("225° SW", Formatters.formatBearing(225f))
        assertEquals("450.0 m", Formatters.formatDistanceMeters(450f))
        assertEquals("2.50 km", Formatters.formatDistanceMeters(2500f))
    }

    @Test
    fun formatMegapixelsAndPowerWatts() {
        assertEquals("4032 × 3024 (12.2 MP)", Formatters.formatMegapixels(4032, 3024))
        assertEquals("5.00 W", Formatters.formatPowerWatts(5000, -1000f))
        assertEquals("N/A", Formatters.formatPowerWatts(5000, null))
    }

    @Test
    fun classifications_returnExpectedSeverities() {
        assertEquals(StatusLevel.GOOD, Formatters.classifyBatteryLevel(80))
        assertEquals(StatusLevel.WARNING, Formatters.classifyBatteryLevel(30))
        assertEquals(StatusLevel.CRITICAL, Formatters.classifyBatteryLevel(10))

        assertEquals(StatusLevel.GOOD, Formatters.classifyTemperatureC(32f))
        assertEquals(StatusLevel.WARNING, Formatters.classifyTemperatureC(39f))
        assertEquals(StatusLevel.CRITICAL, Formatters.classifyTemperatureC(45f))

        assertEquals("Excellent" to StatusLevel.GOOD, Formatters.classifySignalLevel(4))
        assertEquals("No Signal" to StatusLevel.CRITICAL, Formatters.classifySignalLevel(0))
    }
}
