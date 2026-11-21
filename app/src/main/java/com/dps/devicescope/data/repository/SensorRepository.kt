package com.dps.devicescope.data.repository

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.dps.devicescope.core.Formatters
import com.dps.devicescope.data.model.LiveSensorReading
import com.dps.devicescope.data.model.SensorCatalogItem
import com.dps.devicescope.data.model.StatusLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.Locale

class SensorRepository(context: Context) {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val trackedSensorTypes = listOf(
        Sensor.TYPE_ACCELEROMETER to "Accelerometer",
        Sensor.TYPE_GYROSCOPE to "Gyroscope",
        Sensor.TYPE_MAGNETIC_FIELD to "Magnetometer",
        Sensor.TYPE_GRAVITY to "Gravity",
        Sensor.TYPE_LIGHT to "Ambient Light",
        Sensor.TYPE_PROXIMITY to "Proximity",
        Sensor.TYPE_PRESSURE to "Barometer",
        Sensor.TYPE_RELATIVE_HUMIDITY to "Humidity",
        Sensor.TYPE_AMBIENT_TEMPERATURE to "Ambient Temp",
        Sensor.TYPE_STEP_COUNTER to "Step Counter"
    )

    fun getAllSensorsCatalog(): List<SensorCatalogItem> {
        return sensorManager.getSensorList(Sensor.TYPE_ALL)
            .map { sensor ->
                SensorCatalogItem(
                    name = sensor.name,
                    vendor = sensor.vendor.ifBlank { "Unknown" },
                    type = sensor.type,
                    typeLabel = mapSensorTypeName(sensor.type, sensor.stringType),
                    maxRange = sensor.maximumRange,
                    resolution = sensor.resolution,
                    powerMa = sensor.power,
                    isWakeUp = sensor.isWakeUpSensor
                )
            }
            .sortedBy { it.typeLabel }
    }

    fun observeLiveReadings(): Flow<List<LiveSensorReading>> = callbackFlow {
        val readingsMap = LinkedHashMap<Int, LiveSensorReading>()
        val registeredSensors = mutableListOf<Sensor>()

        trackedSensorTypes.forEach { (type, label) ->
            val sensor = sensorManager.getDefaultSensor(type)
            readingsMap[type] = LiveSensorReading(
                type = type,
                label = label,
                isAvailable = sensor != null,
                formattedValue = if (sensor != null) "Waiting for reading..." else "Not Available",
                secondaryValue = sensor?.name ?: "Sensor not present on device",
                normalizedIntensity = 0f,
                severity = StatusLevel.NEUTRAL
            )
            if (sensor != null) registeredSensors.add(sensor)
        }

        trySend(readingsMap.values.toList())

        var lastEmitTime = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val reading = formatEvent(event) ?: return
                readingsMap[event.sensor.type] = reading
                val now = System.currentTimeMillis()
                // Throttle UI emissions to ~12 Hz to keep animations smooth and CPU cool
                if (now - lastEmitTime >= 80L) {
                    lastEmitTime = now
                    trySend(readingsMap.values.toList())
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        registeredSensors.forEach { sensor ->
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }

    private fun formatEvent(event: SensorEvent): LiveSensorReading? {
        val v = event.values
        return when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val mag = Formatters.calculateVectorMagnitude(v[0], v[1], v[2])
                LiveSensorReading(
                    type = event.sensor.type,
                    label = "Accelerometer",
                    isAvailable = true,
                    formattedValue = String.format(Locale.US, "X %.2f  Y %.2f  Z %.2f m/s²", v[0], v[1], v[2]),
                    secondaryValue = String.format(Locale.US, "|a| = %.2f m/s²", mag),
                    normalizedIntensity = (mag / 25f).coerceIn(0f, 1f),
                    severity = if (mag > 16f) StatusLevel.WARNING else StatusLevel.GOOD
                )
            }
            Sensor.TYPE_GYROSCOPE -> {
                val mag = Formatters.calculateVectorMagnitude(v[0], v[1], v[2])
                LiveSensorReading(
                    type = event.sensor.type,
                    label = "Gyroscope",
                    isAvailable = true,
                    formattedValue = String.format(Locale.US, "X %.2f  Y %.2f  Z %.2f rad/s", v[0], v[1], v[2]),
                    secondaryValue = String.format(Locale.US, "|ω| = %.2f rad/s", mag),
                    normalizedIntensity = (mag / 8f).coerceIn(0f, 1f),
                    severity = if (mag > 4f) StatusLevel.INFO else StatusLevel.GOOD
                )
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val mag = Formatters.calculateVectorMagnitude(v[0], v[1], v[2])
                LiveSensorReading(
                    type = event.sensor.type,
                    label = "Magnetometer",
                    isAvailable = true,
                    formattedValue = String.format(Locale.US, "X %.1f  Y %.1f  Z %.1f µT", v[0], v[1], v[2]),
                    secondaryValue = String.format(Locale.US, "Field = %.1f µT", mag),
                    normalizedIntensity = (mag / 120f).coerceIn(0f, 1f),
                    severity = if (mag > 80f) StatusLevel.WARNING else StatusLevel.GOOD
                )
            }
            Sensor.TYPE_GRAVITY -> {
                val mag = Formatters.calculateVectorMagnitude(v[0], v[1], v[2])
                LiveSensorReading(
                    type = event.sensor.type,
                    label = "Gravity",
                    isAvailable = true,
                    formattedValue = String.format(Locale.US, "X %.2f  Y %.2f  Z %.2f m/s²", v[0], v[1], v[2]),
                    secondaryValue = String.format(Locale.US, "|g| = %.2f m/s²", mag),
                    normalizedIntensity = (mag / 12f).coerceIn(0f, 1f),
                    severity = StatusLevel.INFO
                )
            }
            Sensor.TYPE_LIGHT -> {
                val lux = v[0]
                LiveSensorReading(
                    type = event.sensor.type,
                    label = "Ambient Light",
                    isAvailable = true,
                    formattedValue = String.format(Locale.US, "%.1f lx", lux),
                    secondaryValue = when {
                        lux < 20f -> "Dark"
                        lux < 300f -> "Indoor"
                        lux < 2000f -> "Bright Indoor"
                        else -> "Direct Sunlight"
                    },
                    normalizedIntensity = (lux / 10_000f).coerceIn(0f, 1f),
                    severity = StatusLevel.INFO
                )
            }
            Sensor.TYPE_PROXIMITY -> {
                val cm = v[0]
                val maxRange = event.sensor.maximumRange.coerceAtLeast(1f)
                val isNear = cm < maxRange.coerceAtMost(5f)
                LiveSensorReading(
                    type = event.sensor.type,
                    label = "Proximity",
                    isAvailable = true,
                    formattedValue = String.format(Locale.US, "%.1f cm", cm),
                    secondaryValue = if (isNear) "Near Object Detected" else "Far / Clear",
                    normalizedIntensity = if (isNear) 1f else 0.15f,
                    severity = if (isNear) StatusLevel.WARNING else StatusLevel.GOOD
                )
            }
            Sensor.TYPE_PRESSURE -> {
                val hpa = v[0]
                LiveSensorReading(
                    type = event.sensor.type,
                    label = "Barometer",
                    isAvailable = true,
                    formattedValue = String.format(Locale.US, "%.1f hPa", hpa),
                    secondaryValue = String.format(Locale.US, "%.2f atm", hpa / 1013.25f),
                    normalizedIntensity = ((hpa - 950f) / 100f).coerceIn(0f, 1f),
                    severity = StatusLevel.GOOD
                )
            }
            Sensor.TYPE_RELATIVE_HUMIDITY -> {
                val rh = v[0]
                LiveSensorReading(
                    type = event.sensor.type,
                    label = "Humidity",
                    isAvailable = true,
                    formattedValue = String.format(Locale.US, "%.1f%%", rh),
                    secondaryValue = "Relative Humidity",
                    normalizedIntensity = (rh / 100f).coerceIn(0f, 1f),
                    severity = if (rh > 75f) StatusLevel.WARNING else StatusLevel.GOOD
                )
            }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                val temp = v[0]
                LiveSensorReading(
                    type = event.sensor.type,
                    label = "Ambient Temp",
                    isAvailable = true,
                    formattedValue = String.format(Locale.US, "%.1f °C", temp),
                    secondaryValue = String.format(Locale.US, "%.1f °F", temp * 9f / 5f + 32f),
                    normalizedIntensity = ((temp + 10f) / 60f).coerceIn(0f, 1f),
                    severity = Formatters.classifyTemperatureC(temp)
                )
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val steps = v[0].toLong()
                LiveSensorReading(
                    type = event.sensor.type,
                    label = "Step Counter",
                    isAvailable = true,
                    formattedValue = "$steps steps",
                    secondaryValue = "Since last device reboot",
                    normalizedIntensity = (steps / 10_000f).coerceIn(0f, 1f),
                    severity = StatusLevel.GOOD
                )
            }
            else -> null
        }
    }

    private fun mapSensorTypeName(type: Int, stringType: String?): String = when (type) {
        Sensor.TYPE_ACCELEROMETER -> "Accelerometer"
        Sensor.TYPE_MAGNETIC_FIELD -> "Magnetometer"
        Sensor.TYPE_GYROSCOPE -> "Gyroscope"
        Sensor.TYPE_LIGHT -> "Light"
        Sensor.TYPE_PRESSURE -> "Barometer"
        Sensor.TYPE_PROXIMITY -> "Proximity"
        Sensor.TYPE_GRAVITY -> "Gravity"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration"
        Sensor.TYPE_ROTATION_VECTOR -> "Rotation Vector"
        Sensor.TYPE_RELATIVE_HUMIDITY -> "Relative Humidity"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Ambient Temperature"
        Sensor.TYPE_GAME_ROTATION_VECTOR -> "Game Rotation Vector"
        Sensor.TYPE_STEP_DETECTOR -> "Step Detector"
        Sensor.TYPE_STEP_COUNTER -> "Step Counter"
        Sensor.TYPE_SIGNIFICANT_MOTION -> "Significant Motion"
        Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR -> "Geomagnetic Rotation"
        Sensor.TYPE_HEART_RATE -> "Heart Rate"
        Sensor.TYPE_HINGE_ANGLE -> "Hinge Angle"
        else -> stringType?.substringAfterLast(".")?.replace("_", " ")
            ?.replaceFirstChar { it.uppercase() } ?: "Sensor #$type"
    }
}
