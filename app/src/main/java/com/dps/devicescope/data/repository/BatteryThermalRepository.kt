package com.dps.devicescope.data.repository

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.dps.devicescope.data.model.BatteryThermalSnapshot
import com.dps.devicescope.data.model.StatusLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BatteryThermalRepository(private val context: Context) {

    private val batteryManager =
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    fun observeBatteryAndThermal(): Flow<BatteryThermalSnapshot> = callbackFlow {
        var lastBatteryIntent: Intent? = ContextCompat.registerReceiver(
            context,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )

        fun emitNow() {
            trySend(buildSnapshot(lastBatteryIntent))
        }

        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                    lastBatteryIntent = intent
                    emitNow()
                }
            }
        }

        val thermalListener = PowerManager.OnThermalStatusChangedListener {
            emitNow()
        }

        ContextCompat.registerReceiver(
            context,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        runCatching { powerManager.addThermalStatusListener(thermalListener) }

        emitNow()

        // Periodic refresh for live current (mA) and thermal headroom
        val pollJob = launch {
            while (isActive) {
                delay(2000L)
                emitNow()
            }
        }

        awaitClose {
            pollJob.cancel()
            runCatching { context.unregisterReceiver(batteryReceiver) }
            runCatching { powerManager.removeThermalStatusListener(thermalListener) }
        }
    }

    fun captureSnapshot(): BatteryThermalSnapshot {
        val intent = ContextCompat.registerReceiver(
            context,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        return buildSnapshot(intent)
    }

    private fun buildSnapshot(intent: Intent?): BatteryThermalSnapshot {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) {
            ((level * 100f) / scale).toInt().coerceIn(0, 100)
        } else {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        }

        val statusRaw = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val statusText = when (statusRaw) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
            else -> "Unknown"
        }
        val isCharging = statusRaw == BatteryManager.BATTERY_STATUS_CHARGING ||
            statusRaw == BatteryManager.BATTERY_STATUS_FULL

        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val powerSource = when {
            plugged and BatteryManager.BATTERY_PLUGGED_AC != 0 -> "AC Adapter"
            plugged and BatteryManager.BATTERY_PLUGGED_USB != 0 -> "USB"
            plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS != 0 -> "Wireless"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                plugged and BatteryManager.BATTERY_PLUGGED_DOCK != 0 -> "Dock"
            else -> "Battery"
        }

        val healthRaw = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
        val (healthText, healthLevel) = when (healthRaw) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good" to StatusLevel.GOOD
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated" to StatusLevel.CRITICAL
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead" to StatusLevel.CRITICAL
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage" to StatusLevel.CRITICAL
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure" to StatusLevel.CRITICAL
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold" to StatusLevel.INFO
            else -> "Unknown" to StatusLevel.NEUTRAL
        }

        val tempTenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempC = tempTenths / 10f

        val voltageMv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
        val technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)
            ?.takeIf { it.isNotBlank() } ?: "Unknown"

        val currentNowRaw = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentNowMa = normalizeCurrentToMa(currentNowRaw)

        val currentAvgRaw = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val currentAvgMa = normalizeCurrentToMa(currentAvgRaw)

        val chargeCounterUah = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val chargeCounterMah = if (chargeCounterUah > 0 && chargeCounterUah != Int.MIN_VALUE) {
            chargeCounterUah / 1000
        } else {
            null
        }

        val cycleCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it >= 0 }
        } else {
            null
        }

        val (thermalLabel, thermalSeverity) = mapThermalStatus(powerManager.currentThermalStatus)
        val thermalHeadroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val headroom = powerManager.getThermalHeadroom(10)
                if (headroom.isNaN() || headroom < 0f) null else headroom
            }.getOrNull()
        } else {
            null
        }

        return BatteryThermalSnapshot(
            levelPercent = pct,
            status = statusText,
            isCharging = isCharging,
            powerSource = powerSource,
            health = healthText,
            healthLevel = healthLevel,
            temperatureC = tempC,
            voltageMv = voltageMv,
            currentNowMa = currentNowMa,
            currentAvgMa = currentAvgMa,
            chargeCounterMah = chargeCounterMah,
            technology = technology,
            cycleCount = cycleCount,
            thermalStatusLabel = thermalLabel,
            thermalSeverity = thermalSeverity,
            thermalHeadroom = thermalHeadroom
        )
    }

    private fun normalizeCurrentToMa(rawValue: Int): Float? {
        if (rawValue == Int.MIN_VALUE || rawValue == 0) return null
        // Android BatteryManager returns microamperes on standard devices, though some OEMs report mA directly.
        return if (kotlin.math.abs(rawValue) > 10_000) {
            rawValue / 1000f
        } else {
            rawValue.toFloat()
        }
    }

    private fun mapThermalStatus(status: Int): Pair<String, StatusLevel> = when (status) {
        PowerManager.THERMAL_STATUS_NONE -> "Nominal (No Throttling)" to StatusLevel.GOOD
        PowerManager.THERMAL_STATUS_LIGHT -> "Light Throttling" to StatusLevel.INFO
        PowerManager.THERMAL_STATUS_MODERATE -> "Moderate Throttling" to StatusLevel.WARNING
        PowerManager.THERMAL_STATUS_SEVERE -> "Severe Throttling" to StatusLevel.CRITICAL
        PowerManager.THERMAL_STATUS_CRITICAL -> "Critical Thermal Limit" to StatusLevel.CRITICAL
        PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency" to StatusLevel.CRITICAL
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutdown Imminent" to StatusLevel.CRITICAL
        else -> "Unknown" to StatusLevel.NEUTRAL
    }
}
