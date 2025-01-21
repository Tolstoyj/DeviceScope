package com.dps.mytestc

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import android.view.animation.Animation
import android.view.animation.ScaleAnimation

class SensorsActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var lightSensor: Sensor? = null
    private var proximitySensor: Sensor? = null
    private var pressureSensor: Sensor? = null
    private var humiditySensor: Sensor? = null
    private var temperatureSensor: Sensor? = null

    private lateinit var accelerometerCard: MaterialCardView
    private lateinit var accelerometerValue: TextView
    private lateinit var magnetometerCard: MaterialCardView
    private lateinit var magnetometerValue: TextView
    private lateinit var lightCard: MaterialCardView
    private lateinit var lightValue: TextView
    private lateinit var proximityCard: MaterialCardView
    private lateinit var proximityValue: TextView
    private lateinit var pressureCard: MaterialCardView
    private lateinit var pressureValue: TextView
    private lateinit var humidityCard: MaterialCardView
    private lateinit var humidityValue: TextView
    private lateinit var temperatureCard: MaterialCardView
    private lateinit var temperatureValue: TextView

    // Animation constants
    private val ANIMATION_DURATION = 500L
    private val SCALE_UP = 1.05f
    private val SCALE_NORMAL = 1.0f
    private val COLOR_NORMAL = Color.WHITE
    private val COLOR_HOT = Color.parseColor("#FFEBEE")
    private val COLOR_COLD = Color.parseColor("#E3F2FD")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sensors)

        // Initialize views
        accelerometerCard = findViewById(R.id.accelerometerCard)
        accelerometerValue = findViewById(R.id.accelerometerValue)
        magnetometerCard = findViewById(R.id.magnetometerCard)
        magnetometerValue = findViewById(R.id.magnetometerValue)
        lightCard = findViewById(R.id.lightCard)
        lightValue = findViewById(R.id.lightValue)
        proximityCard = findViewById(R.id.proximityCard)
        proximityValue = findViewById(R.id.proximityValue)
        pressureCard = findViewById(R.id.pressureCard)
        pressureValue = findViewById(R.id.pressureValue)
        humidityCard = findViewById(R.id.humidityCard)
        humidityValue = findViewById(R.id.humidityValue)
        temperatureCard = findViewById(R.id.temperatureCard)
        temperatureValue = findViewById(R.id.temperatureValue)

        // Initialize sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)
        humiditySensor = sensorManager.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)

        updateSensorAvailability()
    }

    private fun updateSensorAvailability() {
        accelerometerValue.text = if (accelerometer != null) "Waiting for data..." else "Not available"
        magnetometerValue.text = if (magnetometer != null) "Waiting for data..." else "Not available"
        lightValue.text = if (lightSensor != null) "Waiting for data..." else "Not available"
        proximityValue.text = if (proximitySensor != null) "Waiting for data..." else "Not available"
        pressureValue.text = if (pressureSensor != null) "Waiting for data..." else "Not available"
        humidityValue.text = if (humiditySensor != null) "Waiting for data..." else "Not available"
        temperatureValue.text = if (temperatureSensor != null) "Waiting for data..." else "Not available"
    }

    private fun animateCard(card: MaterialCardView, scale: Float, color: Int) {
        // Scale animation
        val scaleAnim = ScaleAnimation(
            SCALE_NORMAL, scale,
            SCALE_NORMAL, scale,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = ANIMATION_DURATION
            interpolator = AccelerateDecelerateInterpolator()
            fillAfter = true
        }

        // Color animation
        val colorAnim = ValueAnimator.ofObject(
            ArgbEvaluator(),
            card.cardBackgroundColor.defaultColor,
            color
        ).apply {
            duration = ANIMATION_DURATION
            addUpdateListener { animator ->
                card.setCardBackgroundColor(animator.animatedValue as Int)
            }
        }

        card.startAnimation(scaleAnim)
        colorAnim.start()
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also { 
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        magnetometer?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        lightSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        proximitySensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        pressureSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        humiditySensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        temperatureSensor?.also {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                accelerometerValue.text = String.format("X: %.2f, Y: %.2f, Z: %.2f m/s²", x, y, z)
                
                // Animate based on movement intensity
                val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                when {
                    magnitude > 15 -> animateCard(accelerometerCard, SCALE_UP, COLOR_HOT)
                    magnitude < 2 -> animateCard(accelerometerCard, SCALE_NORMAL, COLOR_COLD)
                    else -> animateCard(accelerometerCard, SCALE_NORMAL, COLOR_NORMAL)
                }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                magnetometerValue.text = String.format("X: %.2f, Y: %.2f, Z: %.2f µT", x, y, z)
                
                // Animate based on magnetic field strength
                val magnitude = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                when {
                    magnitude > 70 -> animateCard(magnetometerCard, SCALE_UP, COLOR_HOT)
                    magnitude < 20 -> animateCard(magnetometerCard, SCALE_NORMAL, COLOR_COLD)
                    else -> animateCard(magnetometerCard, SCALE_NORMAL, COLOR_NORMAL)
                }
            }
            Sensor.TYPE_LIGHT -> {
                val light = event.values[0]
                lightValue.text = String.format("%.1f lux", light)
                
                // Animate based on light intensity
                when {
                    light > 10000 -> animateCard(lightCard, SCALE_UP, COLOR_HOT)
                    light < 100 -> animateCard(lightCard, SCALE_NORMAL, COLOR_COLD)
                    else -> animateCard(lightCard, SCALE_NORMAL, COLOR_NORMAL)
                }
            }
            Sensor.TYPE_PROXIMITY -> {
                val distance = event.values[0]
                proximityValue.text = String.format("%.1f cm", distance)
                
                // Animate based on proximity
                when {
                    distance < 5 -> animateCard(proximityCard, SCALE_UP, COLOR_HOT)
                    distance > 10 -> animateCard(proximityCard, SCALE_NORMAL, COLOR_COLD)
                    else -> animateCard(proximityCard, SCALE_NORMAL, COLOR_NORMAL)
                }
            }
            Sensor.TYPE_PRESSURE -> {
                val pressure = event.values[0]
                pressureValue.text = String.format("%.1f hPa", pressure)
                
                // Animate based on pressure
                when {
                    pressure > 1020 -> animateCard(pressureCard, SCALE_UP, COLOR_HOT)
                    pressure < 980 -> animateCard(pressureCard, SCALE_NORMAL, COLOR_COLD)
                    else -> animateCard(pressureCard, SCALE_NORMAL, COLOR_NORMAL)
                }
            }
            Sensor.TYPE_RELATIVE_HUMIDITY -> {
                val humidity = event.values[0]
                humidityValue.text = String.format("%.1f%%", humidity)
                
                // Animate based on humidity
                when {
                    humidity > 70 -> animateCard(humidityCard, SCALE_UP, COLOR_HOT)
                    humidity < 30 -> animateCard(humidityCard, SCALE_NORMAL, COLOR_COLD)
                    else -> animateCard(humidityCard, SCALE_NORMAL, COLOR_NORMAL)
                }
            }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> {
                val temperature = event.values[0]
                temperatureValue.text = String.format("%.1f°C", temperature)
                
                // Animate based on temperature
                when {
                    temperature > 30 -> animateCard(temperatureCard, SCALE_UP, COLOR_HOT)
                    temperature < 15 -> animateCard(temperatureCard, SCALE_NORMAL, COLOR_COLD)
                    else -> animateCard(temperatureCard, SCALE_NORMAL, COLOR_NORMAL)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }
} 