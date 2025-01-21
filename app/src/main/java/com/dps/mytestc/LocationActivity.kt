package com.dps.mytestc

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dps.mytestc.databinding.ActivityLocationBinding
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class LocationActivity : AppCompatActivity(), LocationListener {
    private lateinit var binding: ActivityLocationBinding
    private lateinit var locationManager: LocationManager
    private lateinit var currentLocationCard: MaterialCardView
    private lateinit var speedCard: MaterialCardView
    private lateinit var altitudeCard: MaterialCardView
    private lateinit var distanceCard: MaterialCardView
    private lateinit var accuracyCard: MaterialCardView
    private lateinit var currentLocationValue: TextView
    private lateinit var speedValue: TextView
    private lateinit var altitudeValue: TextView
    private lateinit var distanceValue: TextView
    private lateinit var accuracyValue: TextView

    private var lastLocation: Location? = null
    private var totalDistance = 0.0f
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 100
        private const val MIN_TIME = 1000L // 1 second
        private const val MIN_DISTANCE = 1f // 1 meter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        initializeViews()
        setupLocationManager()
    }

    private fun initializeViews() {
        currentLocationCard = binding.currentLocationCard
        speedCard = binding.speedCard
        altitudeCard = binding.altitudeCard
        distanceCard = binding.distanceCard
        accuracyCard = binding.accuracyCard

        currentLocationValue = binding.currentLocationValue
        speedValue = binding.speedValue
        altitudeValue = binding.altitudeValue
        distanceValue = binding.distanceValue
        accuracyValue = binding.accuracyValue
    }

    private fun setupLocationManager() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        if (checkLocationPermission()) {
            startLocationUpdates()
        } else {
            requestLocationPermission()
        }
    }

    private fun startLocationUpdates() {
        if (checkLocationPermission()) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME,
                MIN_DISTANCE,
                this
            )
        }
    }

    override fun onLocationChanged(location: Location) {
        // Update current location
        val latitude = location.latitude
        val longitude = location.longitude
        animateTextChange(currentLocationValue, 
            String.format("%.6f, %.6f", latitude, longitude))
        animateCard(currentLocationCard)

        // Update speed (convert m/s to km/h)
        val speedKmh = (location.speed * 3.6).roundToInt()
        animateTextChange(speedValue, "$speedKmh km/h")
        animateSpeed(speedCard, speedKmh)

        // Update altitude
        val altitude = location.altitude.roundToInt()
        animateTextChange(altitudeValue, "$altitude m")
        animateAltitude(altitudeCard, altitude)

        // Update total distance
        lastLocation?.let { last ->
            val distance = location.distanceTo(last)
            totalDistance += distance
            animateTextChange(distanceValue, 
                String.format("%.2f km", totalDistance / 1000))
            animateDistance(distanceCard, totalDistance)
        }

        // Update accuracy
        val accuracy = location.accuracy.roundToInt()
        animateTextChange(accuracyValue, "$accuracy m")
        animateAccuracy(accuracyCard, accuracy)

        lastLocation = location
    }

    private fun animateTextChange(textView: TextView, newText: String) {
        textView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                textView.text = newText
                textView.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun animateCard(card: MaterialCardView) {
        card.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(150)
            .withEndAction {
                card.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun animateSpeed(card: MaterialCardView, speed: Int) {
        val colorFrom = ContextCompat.getColor(this, R.color.cardBackground)
        val colorTo = when {
            speed >= 100 -> Color.parseColor("#F44336") // Red
            speed >= 60 -> Color.parseColor("#FF9800") // Orange
            speed >= 30 -> Color.parseColor("#FFC107") // Yellow
            speed > 0 -> Color.parseColor("#4CAF50") // Green
            else -> ContextCompat.getColor(this, R.color.cardBackground)
        }

        animateCardColor(card, colorFrom, colorTo)
    }

    private fun animateAltitude(card: MaterialCardView, altitude: Int) {
        val colorFrom = ContextCompat.getColor(this, R.color.cardBackground)
        val colorTo = when {
            altitude >= 1000 -> Color.parseColor("#F44336") // Red
            altitude >= 500 -> Color.parseColor("#FF9800") // Orange
            altitude >= 100 -> Color.parseColor("#FFC107") // Yellow
            altitude > 0 -> Color.parseColor("#4CAF50") // Green
            else -> ContextCompat.getColor(this, R.color.cardBackground)
        }

        animateCardColor(card, colorFrom, colorTo)
    }

    private fun animateDistance(card: MaterialCardView, distance: Float) {
        val colorFrom = ContextCompat.getColor(this, R.color.cardBackground)
        val colorTo = when {
            distance >= 10000 -> Color.parseColor("#F44336") // Red
            distance >= 5000 -> Color.parseColor("#FF9800") // Orange
            distance >= 1000 -> Color.parseColor("#FFC107") // Yellow
            distance > 0 -> Color.parseColor("#4CAF50") // Green
            else -> ContextCompat.getColor(this, R.color.cardBackground)
        }

        animateCardColor(card, colorFrom, colorTo)
    }

    private fun animateAccuracy(card: MaterialCardView, accuracy: Int) {
        val colorFrom = ContextCompat.getColor(this, R.color.cardBackground)
        val colorTo = when {
            accuracy >= 50 -> Color.parseColor("#F44336") // Red
            accuracy >= 20 -> Color.parseColor("#FF9800") // Orange
            accuracy >= 10 -> Color.parseColor("#FFC107") // Yellow
            accuracy > 0 -> Color.parseColor("#4CAF50") // Green
            else -> ContextCompat.getColor(this, R.color.cardBackground)
        }

        animateCardColor(card, colorFrom, colorTo)
    }

    private fun animateCardColor(card: MaterialCardView, colorFrom: Int, colorTo: Int) {
        ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
            duration = 300
            addUpdateListener { animator ->
                card.setCardBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            }
        }
    }

    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(this)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 