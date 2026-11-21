package com.dps.devicescope.data.model

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    fun next(): ThemeMode = when (this) {
        SYSTEM -> DARK
        DARK -> LIGHT
        LIGHT -> SYSTEM
    }
}

enum class StatusLevel {
    GOOD,
    INFO,
    WARNING,
    CRITICAL,
    NEUTRAL
}

// 1. System & Hardware
data class CpuCoreFrequency(
    val coreIndex: Int,
    val currentKhz: Long?,
    val minKhz: Long?,
    val maxKhz: Long?
)

data class SystemSnapshot(
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val board: String,
    val hardware: String,
    val socManufacturer: String,
    val socModel: String,
    val cpuHardwareLine: String,
    val supportedAbis: List<String>,
    val is64Bit: Boolean,
    val cpuCoreCount: Int,
    val coreFrequencies: List<CpuCoreFrequency>,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val usedRamBytes: Long,
    val isLowMemory: Boolean,
    val totalStorageBytes: Long,
    val availableStorageBytes: Long,
    val usedStorageBytes: Long,
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String,
    val buildDisplay: String,
    val buildFingerprint: String,
    val kernelVersion: String,
    val uptimeMs: Long
)

// 2. Battery & Thermal
data class BatteryThermalSnapshot(
    val levelPercent: Int,
    val status: String,
    val isCharging: Boolean,
    val powerSource: String,
    val health: String,
    val healthLevel: StatusLevel,
    val temperatureC: Float,
    val voltageMv: Int,
    val currentNowMa: Float?,
    val currentAvgMa: Float?,
    val chargeCounterMah: Int?,
    val technology: String,
    val cycleCount: Int?,
    val thermalStatusLabel: String,
    val thermalSeverity: StatusLevel,
    val thermalHeadroom: Float?
)

// 3. Display & GPU
data class DisplayGpuSnapshot(
    val widthPx: Int,
    val heightPx: Int,
    val widthDp: Int,
    val heightDp: Int,
    val densityDpi: Int,
    val densityScale: Float,
    val xdpi: Float,
    val ydpi: Float,
    val diagonalInches: Double,
    val activeRefreshRateHz: Float,
    val supportedRefreshRatesHz: List<Float>,
    val isHdrSupported: Boolean,
    val hdrTypes: List<String>,
    val isWideColorGamut: Boolean,
    val orientation: String,
    val openGlEsVersion: String,
    val gpuRenderer: String,
    val gpuVendor: String,
    val gpuGlVersion: String,
    val vulkanSupported: Boolean,
    val vulkanHardwareLevel: String,
    val vulkanVersion: String
)

// 4. Connectivity
data class ConnectivitySnapshot(
    val activeTransport: String,
    val isInternetValidated: Boolean,
    val isMetered: Boolean,
    val isVpnActive: Boolean,
    val downstreamKbps: Int,
    val upstreamKbps: Int,
    val ipv4Addresses: List<String>,
    val ipv6Addresses: List<String>,
    val dnsServers: List<String>,
    val wifiEnabled: Boolean,
    val wifiSsid: String,
    val wifiBand: String,
    val wifiFrequencyMhz: Int,
    val wifiLinkSpeedMbps: Int,
    val wifiRssiDbm: Int?,
    val supports5Ghz: Boolean,
    val supports6Ghz: Boolean,
    val supportsWifiDirect: Boolean,
    val bluetoothSupported: Boolean,
    val bluetoothEnabled: Boolean,
    val bluetoothName: String,
    val supportsBle: Boolean,
    val bluetoothPermissionMissing: Boolean,
    val pairedDevices: List<String>,
    val nfcSupported: Boolean,
    val nfcEnabled: Boolean,
    val usbHostSupported: Boolean,
    val usbAccessorySupported: Boolean,
    val connectedUsbDeviceCount: Int
)

// 5. Camera
data class CameraSpec(
    val id: String,
    val lensFacing: String,
    val hardwareLevel: String,
    val maxJpegWidth: Int,
    val maxJpegHeight: Int,
    val hasFlash: Boolean,
    val focalLengthsMm: List<Float>,
    val apertures: List<Float>,
    val sensorSizeMm: String,
    val sensorOrientationDeg: Int,
    val fpsRanges: List<String>,
    val capabilities: List<String>,
    val physicalCameraIds: List<String>
)

data class CameraSnapshot(
    val cameras: List<CameraSpec>,
    val error: String? = null
)

// 6. Cellular
data class CellularSnapshot(
    val hasPermission: Boolean,
    val operatorName: String,
    val networkType: String,
    val displayOverride: String,
    val signalLevel: Int,
    val signalQuality: String,
    val signalSeverity: StatusLevel,
    val signalDbm: Int?,
    val signalAsu: Int?,
    val voiceState: String,
    val dataState: String,
    val isRoaming: Boolean,
    val isCellularMetered: Boolean
)

// 7. Location & GNSS
data class LocationSnapshot(
    val hasPermission: Boolean,
    val gpsProviderEnabled: Boolean,
    val networkProviderEnabled: Boolean,
    val hasFix: Boolean,
    val latitude: Double?,
    val longitude: Double?,
    val speedMps: Float,
    val altitudeMeters: Double?,
    val bearingDegrees: Float?,
    val accuracyMeters: Float?,
    val providerName: String,
    val totalDistanceMeters: Float,
    val visibleSatellites: Int,
    val usedSatellites: Int,
    val lastFixTimestampMs: Long?
)

// 8. Sensors
data class SensorCatalogItem(
    val name: String,
    val vendor: String,
    val type: Int,
    val typeLabel: String,
    val maxRange: Float,
    val resolution: Float,
    val powerMa: Float,
    val isWakeUp: Boolean
)

data class LiveSensorReading(
    val type: Int,
    val label: String,
    val isAvailable: Boolean,
    val formattedValue: String,
    val secondaryValue: String = "",
    val normalizedIntensity: Float = 0f,
    val severity: StatusLevel = StatusLevel.NEUTRAL
)

// 9. Microphone
data class AudioInputDeviceInfo(
    val productName: String,
    val typeLabel: String,
    val sampleRates: String,
    val channelCounts: String
)

data class MicrophoneState(
    val hasPermission: Boolean = false,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val statusLabel: String = "Idle",
    val rmsDbfs: Float = -90f,
    val peakDbfs: Float = -90f,
    val peakHoldDbfs: Float = -90f,
    val normalizedLevel: Float = 0f,
    val waveformHistory: List<Float> = emptyList(),
    val sampleRateHz: Int = 44_100,
    val channelLabel: String = "Mono (16-bit PCM)",
    val inputDevices: List<AudioInputDeviceInfo> = emptyList(),
    val error: String? = null
)

// 10. Phone & SIM
data class SimCardSpec(
    val slotIndex: Int,
    val displayName: String,
    val carrierName: String,
    val countryIso: String,
    val phoneNumber: String,
    val isEmbedded: Boolean,
    val isRoaming: Boolean
)

data class PhoneSimSnapshot(
    val hasPermission: Boolean,
    val phoneType: String,
    val simState: String,
    val operatorName: String,
    val networkCountryIso: String,
    val dataNetworkType: String,
    val activeSimCount: Int,
    val maxSimSlots: Int,
    val simCards: List<SimCardSpec>,
    val deviceNumberFallback: String
)

// 11. Biometrics
data class BiometricCapabilityRow(
    val label: String,
    val statusText: String,
    val isReady: Boolean
)

data class BiometricSnapshot(
    val overallReady: Boolean,
    val overallStatus: String,
    val strongBiometric: BiometricCapabilityRow,
    val weakBiometric: BiometricCapabilityRow,
    val deviceCredential: BiometricCapabilityRow,
    val hasFingerprintHardware: Boolean,
    val hasFaceHardware: Boolean,
    val hasIrisHardware: Boolean
)

// 12. Report Export
enum class ReportFormat(val label: String, val extension: String, val mimeType: String) {
    TEXT("Text / Markdown", "txt", "text/plain"),
    JSON("JSON", "json", "application/json")
}

data class DiagnosticReport(
    val generatedAtIso: String,
    val deviceSummary: String,
    val textContent: String,
    val jsonContent: String
)
