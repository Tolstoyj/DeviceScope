package com.dps.devicescope

import com.dps.devicescope.data.model.BatteryThermalSnapshot
import com.dps.devicescope.data.model.BiometricCapabilityRow
import com.dps.devicescope.data.model.BiometricSnapshot
import com.dps.devicescope.data.model.CameraSnapshot
import com.dps.devicescope.data.model.CameraSpec
import com.dps.devicescope.data.model.CellularSnapshot
import com.dps.devicescope.data.model.ConnectivitySnapshot
import com.dps.devicescope.data.model.DisplayGpuSnapshot
import com.dps.devicescope.data.model.LocationSnapshot
import com.dps.devicescope.data.model.PhoneSimSnapshot
import com.dps.devicescope.data.model.SensorCatalogItem
import com.dps.devicescope.data.model.StatusLevel
import com.dps.devicescope.data.model.SystemSnapshot
import com.dps.devicescope.data.report.DiagnosticReportBuilder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportBuilderTest {

    @Test
    fun buildReport_producesValidMarkdownAndJson() {
        val builder = DiagnosticReportBuilder()

        val report = builder.build(
            system = SystemSnapshot(
                manufacturer = "Google",
                brand = "Google",
                model = "Pixel 9",
                device = "tokay",
                board = "tokay",
                hardware = "tokay",
                socManufacturer = "Google",
                socModel = "Tensor G4",
                cpuHardwareLine = "Tensor G4",
                supportedAbis = listOf("arm64-v8a"),
                is64Bit = true,
                cpuCoreCount = 8,
                coreFrequencies = emptyList(),
                totalRamBytes = 12L * 1024 * 1024 * 1024,
                availableRamBytes = 6L * 1024 * 1024 * 1024,
                usedRamBytes = 6L * 1024 * 1024 * 1024,
                isLowMemory = false,
                totalStorageBytes = 128L * 1024 * 1024 * 1024,
                availableStorageBytes = 64L * 1024 * 1024 * 1024,
                usedStorageBytes = 64L * 1024 * 1024 * 1024,
                androidVersion = "15",
                sdkInt = 35,
                securityPatch = "2026-11-05",
                buildDisplay = "AP4A.test",
                buildFingerprint = "google/tokay/tokay:15/AP4A",
                kernelVersion = "6.1.75",
                uptimeMs = 3_600_000L
            ),
            battery = BatteryThermalSnapshot(
                levelPercent = 82,
                status = "Charging",
                isCharging = true,
                powerSource = "AC Adapter",
                health = "Good",
                healthLevel = StatusLevel.GOOD,
                temperatureC = 31.4f,
                voltageMv = 4250,
                currentNowMa = 1200f,
                currentAvgMa = 1150f,
                chargeCounterMah = 3850,
                technology = "Li-ion",
                cycleCount = 42,
                thermalStatusLabel = "Nominal (No Throttling)",
                thermalSeverity = StatusLevel.GOOD,
                thermalHeadroom = 0.25f
            ),
            display = DisplayGpuSnapshot(
                widthPx = 1080,
                heightPx = 2424,
                widthDp = 412,
                heightDp = 924,
                densityDpi = 420,
                densityScale = 2.625f,
                xdpi = 422f,
                ydpi = 422f,
                diagonalInches = 6.3,
                activeRefreshRateHz = 120f,
                supportedRefreshRatesHz = listOf(60f, 120f),
                isHdrSupported = true,
                hdrTypes = listOf("HDR10", "HLG"),
                isWideColorGamut = true,
                orientation = "Portrait",
                openGlEsVersion = "3.2",
                gpuRenderer = "Mali-G715",
                gpuVendor = "ARM",
                gpuGlVersion = "OpenGL ES 3.2",
                vulkanSupported = true,
                vulkanHardwareLevel = "Level 1",
                vulkanVersion = "1.3.0"
            ),
            connectivity = ConnectivitySnapshot(
                activeTransport = "Wi-Fi",
                isInternetValidated = true,
                isMetered = false,
                isVpnActive = false,
                downstreamKbps = 500_000,
                upstreamKbps = 200_000,
                ipv4Addresses = listOf("192.168.1.42"),
                ipv6Addresses = emptyList(),
                dnsServers = listOf("8.8.8.8"),
                wifiEnabled = true,
                wifiSsid = "StudioWiFi",
                wifiBand = "5 GHz",
                wifiFrequencyMhz = 5180,
                wifiLinkSpeedMbps = 866,
                wifiRssiDbm = -54,
                supports5Ghz = true,
                supports6Ghz = true,
                supportsWifiDirect = true,
                bluetoothSupported = true,
                bluetoothEnabled = true,
                bluetoothName = "Pixel 9",
                supportsBle = true,
                bluetoothPermissionMissing = false,
                pairedDevices = listOf("Pixel Buds"),
                nfcSupported = true,
                nfcEnabled = true,
                usbHostSupported = true,
                usbAccessorySupported = true,
                connectedUsbDeviceCount = 0
            ),
            camera = CameraSnapshot(
                cameras = listOf(
                    CameraSpec(
                        id = "0",
                        lensFacing = "Back",
                        hardwareLevel = "Level 3",
                        maxJpegWidth = 4080,
                        maxJpegHeight = 3072,
                        hasFlash = true,
                        focalLengthsMm = listOf(6.9f),
                        apertures = listOf(1.7f),
                        sensorSizeMm = "8.20 × 6.14 mm",
                        sensorOrientationDeg = 90,
                        fpsRanges = listOf("30–60 fps"),
                        capabilities = listOf("Auto Focus", "RAW Capture"),
                        physicalCameraIds = listOf("2", "3")
                    )
                )
            ),
            cellular = CellularSnapshot(
                hasPermission = true,
                operatorName = "TestCarrier",
                networkType = "5G NR",
                displayOverride = "5G NSA",
                signalLevel = 4,
                signalQuality = "Excellent",
                signalSeverity = StatusLevel.GOOD,
                signalDbm = -82,
                signalAsu = 58,
                voiceState = "Idle / Ready",
                dataState = "Connected",
                isRoaming = false,
                isCellularMetered = true
            ),
            location = LocationSnapshot(
                hasPermission = true,
                gpsProviderEnabled = true,
                networkProviderEnabled = true,
                hasFix = true,
                latitude = 8.5241,
                longitude = 76.9366,
                speedMps = 0f,
                altitudeMeters = 12.0,
                bearingDegrees = 0f,
                accuracyMeters = 5.0f,
                providerName = "GPS",
                totalDistanceMeters = 0f,
                visibleSatellites = 18,
                usedSatellites = 12,
                lastFixTimestampMs = 1_700_000_000_000L
            ),
            phoneSim = PhoneSimSnapshot(
                hasPermission = true,
                phoneType = "GSM",
                simState = "Ready",
                operatorName = "TestCarrier",
                networkCountryIso = "IN",
                dataNetworkType = "5G NR",
                activeSimCount = 1,
                maxSimSlots = 2,
                simCards = emptyList(),
                deviceNumberFallback = "Not Provisioned"
            ),
            biometric = BiometricSnapshot(
                overallReady = true,
                overallStatus = "Strong Biometric Ready",
                strongBiometric = BiometricCapabilityRow("Strong", "Ready & Enrolled", true),
                weakBiometric = BiometricCapabilityRow("Weak", "Ready & Enrolled", true),
                deviceCredential = BiometricCapabilityRow("Credential", "Ready & Enrolled", true),
                hasFingerprintHardware = true,
                hasFaceHardware = true,
                hasIrisHardware = false
            ),
            sensors = listOf(
                SensorCatalogItem(
                    name = "LSM6DSO Accelerometer",
                    vendor = "STMicro",
                    type = 1,
                    typeLabel = "Accelerometer",
                    maxRange = 78.4f,
                    resolution = 0.002f,
                    powerMa = 0.25f,
                    isWakeUp = false
                )
            ),
            timestampMs = 1_700_000_000_000L
        )

        // Verify Markdown sections
        assertTrue(report.textContent.contains("# DeviceScope Diagnostic Report"))
        assertTrue(report.textContent.contains("Google Pixel 9"))
        assertTrue(report.textContent.contains("82% (Charging via AC Adapter)"))
        assertTrue(report.textContent.contains("Mali-G715"))
        assertTrue(report.textContent.contains("5G NR"))

        // Verify valid JSON structure
        val json = JSONObject(report.jsonContent)
        assertEquals("DeviceScope", json.getString("app"))
        assertEquals("Pixel 9", json.getJSONObject("system").getString("model"))
        assertEquals(82, json.getJSONObject("batteryThermal").getInt("levelPercent"))
        assertEquals(120.0, json.getJSONObject("displayGpu").getDouble("activeRefreshRateHz"), 0.01)
        assertEquals("Wi-Fi", json.getJSONObject("connectivity").getString("activeTransport"))
        assertEquals(1, json.getJSONArray("cameras").length())
        assertEquals(1, json.getInt("sensorCount"))
    }
}
