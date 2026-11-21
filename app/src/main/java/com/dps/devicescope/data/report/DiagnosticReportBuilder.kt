package com.dps.devicescope.data.report

import com.dps.devicescope.core.Formatters
import com.dps.devicescope.data.model.BatteryThermalSnapshot
import com.dps.devicescope.data.model.BiometricSnapshot
import com.dps.devicescope.data.model.CameraSnapshot
import com.dps.devicescope.data.model.CellularSnapshot
import com.dps.devicescope.data.model.ConnectivitySnapshot
import com.dps.devicescope.data.model.DiagnosticReport
import com.dps.devicescope.data.model.DisplayGpuSnapshot
import com.dps.devicescope.data.model.LocationSnapshot
import com.dps.devicescope.data.model.PhoneSimSnapshot
import com.dps.devicescope.data.model.SensorCatalogItem
import com.dps.devicescope.data.model.SystemSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class DiagnosticReportBuilder {

    fun build(
        system: SystemSnapshot,
        battery: BatteryThermalSnapshot,
        display: DisplayGpuSnapshot,
        connectivity: ConnectivitySnapshot,
        camera: CameraSnapshot,
        cellular: CellularSnapshot,
        location: LocationSnapshot,
        phoneSim: PhoneSimSnapshot,
        biometric: BiometricSnapshot,
        sensors: List<SensorCatalogItem>,
        timestampMs: Long = System.currentTimeMillis()
    ): DiagnosticReport {
        val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val isoTimestamp = isoFormatter.format(Date(timestampMs))
        val deviceSummary = "${system.manufacturer} ${system.model} (Android ${system.androidVersion}, API ${system.sdkInt})"

        val text = buildMarkdownReport(
            isoTimestamp, deviceSummary, system, battery, display,
            connectivity, camera, cellular, location, phoneSim, biometric, sensors
        )
        val json = buildJsonReport(
            isoTimestamp, system, battery, display,
            connectivity, camera, cellular, location, phoneSim, biometric, sensors
        )

        return DiagnosticReport(
            generatedAtIso = isoTimestamp,
            deviceSummary = deviceSummary,
            textContent = text,
            jsonContent = json
        )
    }

    private fun buildMarkdownReport(
        isoTimestamp: String,
        deviceSummary: String,
        system: SystemSnapshot,
        battery: BatteryThermalSnapshot,
        display: DisplayGpuSnapshot,
        connectivity: ConnectivitySnapshot,
        camera: CameraSnapshot,
        cellular: CellularSnapshot,
        location: LocationSnapshot,
        phoneSim: PhoneSimSnapshot,
        biometric: BiometricSnapshot,
        sensors: List<SensorCatalogItem>
    ): String = buildString {
        appendLine("# DeviceScope Diagnostic Report")
        appendLine("Generated: $isoTimestamp")
        appendLine("Device: $deviceSummary")
        appendLine()

        appendLine("## System & Hardware")
        appendLine("- Manufacturer / Brand: ${system.manufacturer} / ${system.brand}")
        appendLine("- Model / Device: ${system.model} (${system.device})")
        appendLine("- SoC / Hardware: ${system.socManufacturer} ${system.socModel} (${system.hardware})")
        appendLine("- CPU Cores: ${system.cpuCoreCount} (${system.supportedAbis.joinToString(", ")})")
        appendLine("- RAM: ${Formatters.formatBytes(system.usedRamBytes)} / ${Formatters.formatBytes(system.totalRamBytes)} (${Formatters.formatPercentage(system.usedRamBytes, system.totalRamBytes)}% used)")
        appendLine("- Storage: ${Formatters.formatBytes(system.usedStorageBytes)} / ${Formatters.formatBytes(system.totalStorageBytes)} (${Formatters.formatPercentage(system.usedStorageBytes, system.totalStorageBytes)}% used)")
        appendLine("- Android OS: ${system.androidVersion} (API ${system.sdkInt}), Security Patch: ${system.securityPatch}")
        appendLine("- Kernel: ${system.kernelVersion}")
        appendLine("- Uptime: ${Formatters.formatUptime(system.uptimeMs)}")
        appendLine()

        appendLine("## Battery & Thermal")
        appendLine("- Level: ${battery.levelPercent}% (${battery.status} via ${battery.powerSource})")
        appendLine("- Health: ${battery.health} | Technology: ${battery.technology}")
        appendLine("- Temperature: ${String.format(Locale.US, "%.1f °C", battery.temperatureC)} | Voltage: ${battery.voltageMv} mV")
        battery.currentNowMa?.let {
            appendLine("- Current: ${String.format(Locale.US, "%.0f mA", it)} | Power: ${Formatters.formatPowerWatts(battery.voltageMv, it)}")
        }
        battery.chargeCounterMah?.let { appendLine("- Remaining Charge: $it mAh") }
        battery.cycleCount?.let { appendLine("- Cycle Count: $it") }
        appendLine("- Thermal Status: ${battery.thermalStatusLabel}")
        battery.thermalHeadroom?.let {
            appendLine("- Thermal Headroom (10s): ${String.format(Locale.US, "%.2f", it)}")
        }
        appendLine()

        appendLine("## Display & GPU")
        appendLine("- Resolution: ${display.widthPx} × ${display.heightPx} px (${display.widthDp} × ${display.heightDp} dp)")
        appendLine("- Density: ${display.densityDpi} dpi (${String.format(Locale.US, "%.2fx", display.densityScale)}) | ~${String.format(Locale.US, "%.2f\"", display.diagonalInches)}")
        appendLine("- Refresh Rate: ${String.format(Locale.US, "%.0f Hz", display.activeRefreshRateHz)} (Supported: ${display.supportedRefreshRatesHz.joinToString(", ") { "${it.toInt()} Hz" }})")
        appendLine("- HDR: ${if (display.isHdrSupported) display.hdrTypes.joinToString(", ").ifBlank { "Yes" } else "No"} | Wide Gamut: ${if (display.isWideColorGamut) "Yes" else "No"}")
        appendLine("- GPU Renderer: ${display.gpuRenderer} (${display.gpuVendor})")
        appendLine("- OpenGL ES: ${display.openGlEsVersion} | Vulkan: ${if (display.vulkanSupported) "${display.vulkanVersion} (${display.vulkanHardwareLevel})" else "Not Supported"}")
        appendLine()

        appendLine("## Connectivity")
        appendLine("- Active Transport: ${connectivity.activeTransport} (Internet Validated: ${connectivity.isInternetValidated}, Metered: ${connectivity.isMetered}, VPN: ${connectivity.isVpnActive})")
        if (connectivity.ipv4Addresses.isNotEmpty()) {
            appendLine("- IPv4: ${connectivity.ipv4Addresses.joinToString(", ")}")
        }
        appendLine("- Wi-Fi: ${if (connectivity.wifiEnabled) "Enabled" else "Disabled"} | SSID: ${connectivity.wifiSsid} | Band: ${connectivity.wifiBand} | Link: ${connectivity.wifiLinkSpeedMbps} Mbps")
        appendLine("- Bluetooth: ${if (!connectivity.bluetoothSupported) "Unsupported" else if (connectivity.bluetoothEnabled) "Enabled (${connectivity.pairedDevices.size} paired)" else "Disabled"} | BLE: ${connectivity.supportsBle}")
        appendLine("- NFC: ${if (!connectivity.nfcSupported) "Unsupported" else if (connectivity.nfcEnabled) "Enabled" else "Disabled"} | USB Host (OTG): ${connectivity.usbHostSupported}")
        appendLine()

        appendLine("## Cameras (${camera.cameras.size})")
        camera.cameras.forEach { cam ->
            appendLine("- Camera ${cam.id} (${cam.lensFacing}): ${Formatters.formatMegapixels(cam.maxJpegWidth, cam.maxJpegHeight)}, Level=${cam.hardwareLevel}, Flash=${cam.hasFlash}, Features=[${cam.capabilities.joinToString(", ")}]")
        }
        appendLine()

        appendLine("## Cellular & Telephony")
        appendLine("- Operator: ${phoneSim.operatorName} (${phoneSim.networkCountryIso}) | SIM State: ${phoneSim.simState} | Active SIMs: ${phoneSim.activeSimCount}/${phoneSim.maxSimSlots}")
        appendLine("- Network: ${cellular.networkType} (${cellular.displayOverride}) | Signal: ${cellular.signalQuality}${cellular.signalDbm?.let { " ($it dBm)" } ?: ""}")
        appendLine("- Data: ${cellular.dataState} | Voice: ${cellular.voiceState} | Roaming: ${cellular.isRoaming}")
        appendLine()

        appendLine("## Location & GNSS")
        appendLine("- GPS Provider: ${if (location.gpsProviderEnabled) "Enabled" else "Disabled"} | Network Provider: ${if (location.networkProviderEnabled) "Enabled" else "Disabled"}")
        if (location.hasFix && location.latitude != null && location.longitude != null) {
            appendLine("- Last Fix (${location.providerName}): ${String.format(Locale.US, "%.5f, %.5f", location.latitude, location.longitude)} (±${location.accuracyMeters?.toInt() ?: 0} m)")
        } else {
            appendLine("- Last Fix: ${if (location.hasPermission) "No fix yet" else "Location permission not granted"}")
        }
        appendLine()

        appendLine("## Biometrics")
        appendLine("- Status: ${biometric.overallStatus}")
        appendLine("- Strong: ${biometric.strongBiometric.statusText} | Weak: ${biometric.weakBiometric.statusText} | Credential: ${biometric.deviceCredential.statusText}")
        appendLine("- Hardware Flags: Fingerprint=${biometric.hasFingerprintHardware}, Face=${biometric.hasFaceHardware}, Iris=${biometric.hasIrisHardware}")
        appendLine()

        appendLine("## Sensors (${sensors.size} Total)")
        sensors.take(20).forEach { s ->
            appendLine("- ${s.typeLabel}: ${s.name} (${s.vendor}, ${String.format(Locale.US, "%.2f mA", s.powerMa)})")
        }
        if (sensors.size > 20) {
            appendLine("- ...and ${sensors.size - 20} more sensors")
        }
    }

    private fun buildJsonReport(
        isoTimestamp: String,
        system: SystemSnapshot,
        battery: BatteryThermalSnapshot,
        display: DisplayGpuSnapshot,
        connectivity: ConnectivitySnapshot,
        camera: CameraSnapshot,
        cellular: CellularSnapshot,
        location: LocationSnapshot,
        phoneSim: PhoneSimSnapshot,
        biometric: BiometricSnapshot,
        sensors: List<SensorCatalogItem>
    ): String {
        val root = JSONObject().apply {
            put("app", "DeviceScope")
            put("generatedAt", isoTimestamp)

            put("system", JSONObject().apply {
                put("manufacturer", system.manufacturer)
                put("brand", system.brand)
                put("model", system.model)
                put("device", system.device)
                put("socManufacturer", system.socManufacturer)
                put("socModel", system.socModel)
                put("hardware", system.hardware)
                put("cpuCores", system.cpuCoreCount)
                put("supportedAbis", JSONArray(system.supportedAbis))
                put("ramTotalBytes", system.totalRamBytes)
                put("ramUsedBytes", system.usedRamBytes)
                put("storageTotalBytes", system.totalStorageBytes)
                put("storageUsedBytes", system.usedStorageBytes)
                put("androidVersion", system.androidVersion)
                put("sdkInt", system.sdkInt)
                put("securityPatch", system.securityPatch)
                put("kernelVersion", system.kernelVersion)
                put("uptimeMs", system.uptimeMs)
            })

            put("batteryThermal", JSONObject().apply {
                put("levelPercent", battery.levelPercent)
                put("status", battery.status)
                put("powerSource", battery.powerSource)
                put("health", battery.health)
                put("temperatureC", battery.temperatureC.toDouble())
                put("voltageMv", battery.voltageMv)
                battery.currentNowMa?.let { put("currentNowMa", it.toDouble()) }
                battery.chargeCounterMah?.let { put("chargeCounterMah", it) }
                battery.cycleCount?.let { put("cycleCount", it) }
                put("technology", battery.technology)
                put("thermalStatus", battery.thermalStatusLabel)
                battery.thermalHeadroom?.let { put("thermalHeadroom", it.toDouble()) }
            })

            put("displayGpu", JSONObject().apply {
                put("widthPx", display.widthPx)
                put("heightPx", display.heightPx)
                put("densityDpi", display.densityDpi)
                put("activeRefreshRateHz", display.activeRefreshRateHz.toDouble())
                put("supportedRefreshRatesHz", JSONArray(display.supportedRefreshRatesHz.map { it.toDouble() }))
                put("isHdrSupported", display.isHdrSupported)
                put("hdrTypes", JSONArray(display.hdrTypes))
                put("wideColorGamut", display.isWideColorGamut)
                put("openGlEsVersion", display.openGlEsVersion)
                put("gpuRenderer", display.gpuRenderer)
                put("gpuVendor", display.gpuVendor)
                put("vulkanSupported", display.vulkanSupported)
                put("vulkanVersion", display.vulkanVersion)
            })

            put("connectivity", JSONObject().apply {
                put("activeTransport", connectivity.activeTransport)
                put("internetValidated", connectivity.isInternetValidated)
                put("metered", connectivity.isMetered)
                put("vpnActive", connectivity.isVpnActive)
                put("ipv4", JSONArray(connectivity.ipv4Addresses))
                put("wifiEnabled", connectivity.wifiEnabled)
                put("wifiBand", connectivity.wifiBand)
                put("wifiLinkSpeedMbps", connectivity.wifiLinkSpeedMbps)
                put("bluetoothSupported", connectivity.bluetoothSupported)
                put("bluetoothEnabled", connectivity.bluetoothEnabled)
                put("bleSupported", connectivity.supportsBle)
                put("nfcSupported", connectivity.nfcSupported)
                put("nfcEnabled", connectivity.nfcEnabled)
                put("usbHostSupported", connectivity.usbHostSupported)
            })

            put("cameras", JSONArray().apply {
                camera.cameras.forEach { cam ->
                    put(JSONObject().apply {
                        put("id", cam.id)
                        put("lensFacing", cam.lensFacing)
                        put("hardwareLevel", cam.hardwareLevel)
                        put("maxJpegWidth", cam.maxJpegWidth)
                        put("maxJpegHeight", cam.maxJpegHeight)
                        put("hasFlash", cam.hasFlash)
                        put("sensorSizeMm", cam.sensorSizeMm)
                        put("capabilities", JSONArray(cam.capabilities))
                    })
                }
            })

            put("cellular", JSONObject().apply {
                put("hasPermission", cellular.hasPermission)
                put("operatorName", phoneSim.operatorName)
                put("networkType", cellular.networkType)
                put("signalQuality", cellular.signalQuality)
                cellular.signalDbm?.let { put("signalDbm", it) }
                put("dataState", cellular.dataState)
                put("roaming", cellular.isRoaming)
                put("simState", phoneSim.simState)
                put("activeSimCount", phoneSim.activeSimCount)
            })

            put("location", JSONObject().apply {
                put("hasPermission", location.hasPermission)
                put("gpsEnabled", location.gpsProviderEnabled)
                put("networkProviderEnabled", location.networkProviderEnabled)
                put("hasFix", location.hasFix)
                location.latitude?.let { put("latitude", it) }
                location.longitude?.let { put("longitude", it) }
                location.accuracyMeters?.let { put("accuracyMeters", it.toDouble()) }
            })

            put("biometrics", JSONObject().apply {
                put("overallStatus", biometric.overallStatus)
                put("strong", biometric.strongBiometric.statusText)
                put("weak", biometric.weakBiometric.statusText)
                put("deviceCredential", biometric.deviceCredential.statusText)
                put("fingerprintHardware", biometric.hasFingerprintHardware)
                put("faceHardware", biometric.hasFaceHardware)
            })

            put("sensorCount", sensors.size)
            put("sensors", JSONArray().apply {
                sensors.forEach { s ->
                    put(JSONObject().apply {
                        put("type", s.typeLabel)
                        put("name", s.name)
                        put("vendor", s.vendor)
                        put("powerMa", s.powerMa.toDouble())
                    })
                }
            })
        }
        return root.toString(2)
    }
}
