package com.dps.devicescope.ui.screens.location

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SatelliteAlt
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dps.devicescope.core.DeviceContainer
import com.dps.devicescope.core.Formatters
import com.dps.devicescope.core.rememberMultiPermissionState
import com.dps.devicescope.data.model.LocationSnapshot
import com.dps.devicescope.data.model.StatusLevel
import com.dps.devicescope.ui.components.DiagnosticScaffold
import com.dps.devicescope.ui.components.HeroMetricTile
import com.dps.devicescope.ui.components.MetricRow
import com.dps.devicescope.ui.components.PermissionGateCard
import com.dps.devicescope.ui.components.SectionHeader
import com.dps.devicescope.ui.components.StatusBadge
import com.dps.devicescope.ui.components.TelemetryCard
import com.dps.devicescope.ui.theme.LocalTelemetryColors
import com.dps.devicescope.ui.theme.TelemetryMono
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class LocationViewModel(private val container: DeviceContainer) : ViewModel() {
    private val sessionTrigger = MutableStateFlow(0)

    val state: StateFlow<LocationSnapshot?> = sessionTrigger
        .flatMapLatest { container.locationRepository.observeLocation() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    fun resetSession() {
        sessionTrigger.value++
    }

    companion object {
        fun factory(container: DeviceContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LocationViewModel(container) as T
            }
    }
}

@Composable
fun LocationScreen(
    container: DeviceContainer,
    onBack: () -> Unit
) {
    val viewModel: LocationViewModel = viewModel(factory = LocationViewModel.factory(container))
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalTelemetryColors.current

    val permState = rememberMultiPermissionState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    ) {
        viewModel.resetSession()
    }
    LaunchedEffect(permState.anyGranted) {
        if (permState.anyGranted) viewModel.resetSession()
    }

    DiagnosticScaffold(
        title = "Location & GNSS",
        subtitle = "GPS telemetry, speed & satellites",
        onBack = onBack,
        actions = {
            if (permState.anyGranted) {
                IconButton(onClick = { viewModel.resetSession() }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reset distance tracker")
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!permState.anyGranted) {
                item {
                    PermissionGateCard(
                        title = "Location Permission Required",
                        description = "DeviceScope uses precise location access locally to read live GPS coordinates, speed, altitude, accuracy, and visible GNSS satellites.",
                        hasRequestedOnce = permState.hasRequestedOnce,
                        onGrantClick = permState.launchRequest,
                        onOpenSettings = permState.openSettings
                    )
                }
                return@LazyColumn
            }

            val data = snapshot ?: return@LazyColumn
            val speedKmh = data.speedMps * 3.6f

            item {
                TelemetryCard(accentColor = if (data.hasFix) colors.good else colors.warning) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Position", style = MaterialTheme.typography.titleMedium)
                        StatusBadge(
                            text = if (data.hasFix) "${data.providerName} Fix" else "Acquiring Fix...",
                            level = if (data.hasFix) StatusLevel.GOOD else StatusLevel.WARNING
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    if (data.latitude != null && data.longitude != null) {
                        Text(
                            text = String.format(Locale.US, "%.6f, %.6f", data.latitude, data.longitude),
                            style = TelemetryMono.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${Formatters.formatCoordinateDms(data.latitude, true)}   ${Formatters.formatCoordinateDms(data.longitude, false)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Waiting for satellite or network fix. Move near a window or set a mock location on an emulator.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroMetricTile(
                        icon = Icons.Rounded.Speed,
                        label = "Speed",
                        value = "${speedKmh.roundToInt()} km/h",
                        sublabel = String.format(Locale.US, "%.1f m/s", data.speedMps),
                        accent = colors.info,
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetricTile(
                        icon = Icons.Rounded.SatelliteAlt,
                        label = "GNSS Satellites",
                        value = "${data.usedSatellites} / ${data.visibleSatellites}",
                        sublabel = "Used / Visible",
                        accent = if (data.usedSatellites >= 4) colors.good else colors.warning,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { SectionHeader("Motion & Accuracy") }

            item {
                TelemetryCard {
                    MetricRow(
                        "Accuracy",
                        data.accuracyMeters?.let { String.format(Locale.US, "±%.1f m", it) } ?: "N/A",
                        valueColor = when {
                            data.accuracyMeters == null -> MaterialTheme.colorScheme.onSurfaceVariant
                            data.accuracyMeters <= 12f -> colors.good
                            data.accuracyMeters <= 35f -> colors.warning
                            else -> colors.critical
                        }
                    )
                    MetricRow(
                        "Altitude (WGS84)",
                        data.altitudeMeters?.let { String.format(Locale.US, "%.1f m", it) } ?: "N/A"
                    )
                    MetricRow(
                        "Bearing / Heading",
                        data.bearingDegrees?.let { Formatters.formatBearing(it) } ?: "N/A"
                    )
                    MetricRow(
                        "Session Distance",
                        Formatters.formatDistanceMeters(data.totalDistanceMeters)
                    )
                    MetricRow(
                        "GPS Provider",
                        if (data.gpsProviderEnabled) "Enabled" else "Disabled",
                        valueColor = if (data.gpsProviderEnabled) colors.good else colors.warning
                    )
                    MetricRow(
                        "Network Provider",
                        if (data.networkProviderEnabled) "Enabled" else "Disabled",
                        showDivider = false
                    )
                }
            }
        }
    }
}
