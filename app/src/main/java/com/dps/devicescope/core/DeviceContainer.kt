package com.dps.devicescope.core

import android.content.Context
import com.dps.devicescope.data.report.DiagnosticReportBuilder
import com.dps.devicescope.data.repository.BatteryThermalRepository
import com.dps.devicescope.data.repository.BiometricRepository
import com.dps.devicescope.data.repository.CameraInfoRepository
import com.dps.devicescope.data.repository.CellularRepository
import com.dps.devicescope.data.repository.ConnectivityRepository
import com.dps.devicescope.data.repository.DisplayGpuRepository
import com.dps.devicescope.data.repository.LocationRepository
import com.dps.devicescope.data.repository.MicrophoneRepository
import com.dps.devicescope.data.repository.PhoneSimRepository
import com.dps.devicescope.data.repository.SensorRepository
import com.dps.devicescope.data.repository.SystemInfoRepository

class DeviceContainer(context: Context) {
    private val appContext = context.applicationContext

    val systemInfoRepository by lazy { SystemInfoRepository(appContext) }
    val batteryThermalRepository by lazy { BatteryThermalRepository(appContext) }
    val displayGpuRepository by lazy { DisplayGpuRepository(appContext) }
    val connectivityRepository by lazy { ConnectivityRepository(appContext) }
    val cameraInfoRepository by lazy { CameraInfoRepository(appContext) }
    val cellularRepository by lazy { CellularRepository(appContext) }
    val locationRepository by lazy { LocationRepository(appContext) }
    val sensorRepository by lazy { SensorRepository(appContext) }
    val microphoneRepository by lazy { MicrophoneRepository(appContext) }
    val phoneSimRepository by lazy { PhoneSimRepository(appContext) }
    val biometricRepository by lazy { BiometricRepository(appContext) }
    val reportBuilder by lazy { DiagnosticReportBuilder() }
}
