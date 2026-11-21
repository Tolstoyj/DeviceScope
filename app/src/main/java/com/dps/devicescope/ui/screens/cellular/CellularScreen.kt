package com.dps.devicescope.ui.screens.cellular

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dps.devicescope.core.DeviceContainer
import com.dps.devicescope.core.rememberMultiPermissionState
import com.dps.devicescope.data.model.CellularSnapshot
import com.dps.devicescope.data.model.StatusLevel
import com.dps.devicescope.ui.components.DiagnosticScaffold
import com.dps.devicescope.ui.components.MetricRow
import com.dps.devicescope.ui.components.PermissionGateCard
import com.dps.devicescope.ui.components.SectionHeader
import com.dps.devicescope.ui.components.StatusBadge
import com.dps.devicescope.ui.components.TelemetryCard
import com.dps.devicescope.ui.theme.LocalTelemetryColors
import com.dps.devicescope.ui.theme.TelemetryHeroNumber
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class CellularViewModel(private val container: DeviceContainer) : ViewModel() {
    private val restartSignal = MutableStateFlow(0)

    val state: StateFlow<CellularSnapshot?> = restartSignal
        .flatMapLatest { container.cellularRepository.observeCellular() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    fun onPermissionUpdated() {
        restartSignal.value++
    }

    companion object {
        fun factory(container: DeviceContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CellularViewModel(container) as T
            }
    }
}

@Composable
fun CellularScreen(
    container: DeviceContainer,
    onBack: () -> Unit
) {
    val viewModel: CellularViewModel = viewModel(factory = CellularViewModel.factory(container))
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalTelemetryColors.current

    val permState = rememberMultiPermissionState(listOf(Manifest.permission.READ_PHONE_STATE)) {
        viewModel.onPermissionUpdated()
    }
    LaunchedEffect(permState.allGranted) {
        if (permState.allGranted) viewModel.onPermissionUpdated()
    }

    DiagnosticScaffold(
        title = "Cellular Radio",
        subtitle = "Signal strength, network gen & voice/data",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!permState.allGranted) {
                item {
                    PermissionGateCard(
                        title = "Phone State Permission Required",
                        description = "DeviceScope needs Read Phone State permission to inspect live cellular signal strength, 5G/LTE network generation, and data/voice connection states.",
                        hasRequestedOnce = permState.hasRequestedOnce,
                        onGrantClick = permState.launchRequest,
                        onOpenSettings = permState.openSettings
                    )
                }
                return@LazyColumn
            }

            val data = snapshot ?: return@LazyColumn
            val signalColor = colors.forLevel(data.signalSeverity)

            item {
                TelemetryCard(accentColor = signalColor) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = data.operatorName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = data.signalDbm?.let { "$it dBm" } ?: data.signalQuality,
                                style = TelemetryHeroNumber,
                                color = signalColor
                            )
                            Text(
                                text = buildString {
                                    append("Signal: ${data.signalQuality} (${data.signalLevel}/4)")
                                    data.signalAsu?.let { append(" · ASU $it") }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        SignalStrengthBars(level = data.signalLevel, activeColor = signalColor)
                    }
                }
            }

            item { SectionHeader("Radio & Network Telemetry") }

            item {
                TelemetryCard {
                    MetricRow("Network Generation", data.networkType)
                    MetricRow("Display Override", data.displayOverride)
                    MetricRow(
                        "Data Connection",
                        data.dataState,
                        valueColor = if (data.dataState == "Connected") colors.good else colors.neutral
                    )
                    MetricRow("Voice / Call State", data.voiceState)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Roaming Status",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatusBadge(
                            text = if (data.isRoaming) "Roaming" else "Home Network",
                            level = if (data.isRoaming) StatusLevel.WARNING else StatusLevel.GOOD
                        )
                    }
                    MetricRow(
                        "Network Metering",
                        if (data.isCellularMetered) "Metered" else "Unmetered",
                        showDivider = false
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalStrengthBars(
    level: Int,
    activeColor: Color
) {
    val inactiveColor = MaterialTheme.colorScheme.surfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        (1..4).forEach { bar ->
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height((14 + bar * 10).dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (level >= bar) activeColor else inactiveColor)
            )
        }
    }
}
