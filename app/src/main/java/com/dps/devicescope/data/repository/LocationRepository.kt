package com.dps.devicescope.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.dps.devicescope.core.hasAnyPermission
import com.dps.devicescope.core.hasPermission
import com.dps.devicescope.data.model.LocationSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationRepository(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    fun observeLocation(): Flow<LocationSnapshot> = callbackFlow {
        val hasFine = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarse = context.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFine && !hasCoarse) {
            trySend(permissionMissingSnapshot())
            awaitClose { }
            return@callbackFlow
        }

        var lastLocation: Location? = null
        var cumulativeDistanceMeters = 0f
        var visibleSats = 0
        var usedSats = 0

        fun emitSnapshot(loc: Location?) {
            val gpsEnabled = runCatching {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }.getOrDefault(false)
            val netEnabled = runCatching {
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false)

            trySend(
                LocationSnapshot(
                    hasPermission = true,
                    gpsProviderEnabled = gpsEnabled,
                    networkProviderEnabled = netEnabled,
                    hasFix = loc != null,
                    latitude = loc?.latitude,
                    longitude = loc?.longitude,
                    speedMps = loc?.takeIf { it.hasSpeed() }?.speed ?: 0f,
                    altitudeMeters = loc?.takeIf { it.hasAltitude() }?.altitude,
                    bearingDegrees = loc?.takeIf { it.hasBearing() }?.bearing,
                    accuracyMeters = loc?.takeIf { it.hasAccuracy() }?.accuracy,
                    providerName = loc?.provider?.uppercase() ?: "Searching...",
                    totalDistanceMeters = cumulativeDistanceMeters,
                    visibleSatellites = visibleSats,
                    usedSatellites = usedSats,
                    lastFixTimestampMs = loc?.time
                )
            )
        }

        // Seed with best last known location immediately
        val seedLocation = getBestLastKnownLocation(hasFine)
        if (seedLocation != null) {
            lastLocation = seedLocation
        }
        emitSnapshot(seedLocation)

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                lastLocation?.let { prev ->
                    val delta = location.distanceTo(prev)
                    // Filter tiny GPS jitter (< 1m) or unrealistic jumps
                    if (delta >= 1f && location.accuracy <= 50f) {
                        cumulativeDistanceMeters += delta
                    }
                }
                lastLocation = location
                emitSnapshot(location)
            }

            override fun onProviderEnabled(provider: String) = emitSnapshot(lastLocation)
            override fun onProviderDisabled(provider: String) = emitSnapshot(lastLocation)
            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        val providers = buildList {
            if (hasFine && locationManager.allProviders.contains(LocationManager.GPS_PROVIDER)) {
                add(LocationManager.GPS_PROVIDER)
            }
            if (locationManager.allProviders.contains(LocationManager.NETWORK_PROVIDER)) {
                add(LocationManager.NETWORK_PROVIDER)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                locationManager.allProviders.contains(LocationManager.FUSED_PROVIDER)
            ) {
                add(LocationManager.FUSED_PROVIDER)
            }
        }

        providers.forEach { provider ->
            runCatching {
                locationManager.requestLocationUpdates(
                    provider,
                    1000L,
                    1f,
                    listener,
                    Looper.getMainLooper()
                )
            }
        }

        val gnssCallback = if (hasFine) {
            object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    visibleSats = status.satelliteCount
                    var used = 0
                    for (i in 0 until status.satelliteCount) {
                        if (status.usedInFix(i)) used++
                    }
                    usedSats = used
                    emitSnapshot(lastLocation)
                }
            }.also { cb ->
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        locationManager.registerGnssStatusCallback(context.mainExecutor, cb)
                    } else {
                        @Suppress("DEPRECATION")
                        locationManager.registerGnssStatusCallback(cb, Handler(Looper.getMainLooper()))
                    }
                }
            }
        } else {
            null
        }

        awaitClose {
            runCatching { locationManager.removeUpdates(listener) }
            gnssCallback?.let { runCatching { locationManager.unregisterGnssStatusCallback(it) } }
        }
    }

    @SuppressLint("MissingPermission")
    fun captureSnapshot(): LocationSnapshot {
        val hasLocation = context.hasAnyPermission(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
        if (!hasLocation) return permissionMissingSnapshot()

        val hasFine = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val loc = getBestLastKnownLocation(hasFine)
        val gpsEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false)
        val netEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false)

        return LocationSnapshot(
            hasPermission = true,
            gpsProviderEnabled = gpsEnabled,
            networkProviderEnabled = netEnabled,
            hasFix = loc != null,
            latitude = loc?.latitude,
            longitude = loc?.longitude,
            speedMps = loc?.takeIf { it.hasSpeed() }?.speed ?: 0f,
            altitudeMeters = loc?.takeIf { it.hasAltitude() }?.altitude,
            bearingDegrees = loc?.takeIf { it.hasBearing() }?.bearing,
            accuracyMeters = loc?.takeIf { it.hasAccuracy() }?.accuracy,
            providerName = loc?.provider?.uppercase() ?: "No Cached Fix",
            totalDistanceMeters = 0f,
            visibleSatellites = 0,
            usedSatellites = 0,
            lastFixTimestampMs = loc?.time
        )
    }

    @SuppressLint("MissingPermission")
    private fun getBestLastKnownLocation(hasFine: Boolean): Location? {
        val candidates = buildList {
            if (hasFine) {
                runCatching { locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }
                    .getOrNull()?.let { add(it) }
            }
            runCatching { locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }
                .getOrNull()?.let { add(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching { locationManager.getLastKnownLocation(LocationManager.FUSED_PROVIDER) }
                    .getOrNull()?.let { add(it) }
            }
        }
        return candidates.maxByOrNull { it.time }
    }

    private fun permissionMissingSnapshot() = LocationSnapshot(
        hasPermission = false,
        gpsProviderEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }.getOrDefault(false),
        networkProviderEnabled = runCatching {
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }.getOrDefault(false),
        hasFix = false,
        latitude = null,
        longitude = null,
        speedMps = 0f,
        altitudeMeters = null,
        bearingDegrees = null,
        accuracyMeters = null,
        providerName = "Permission Required",
        totalDistanceMeters = 0f,
        visibleSatellites = 0,
        usedSatellites = 0,
        lastFixTimestampMs = null
    )
}
