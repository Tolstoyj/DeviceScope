package com.dps.devicescope.ui.screens.battery

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
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DeviceThermostat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.dps.devicescope.data.model.BatteryThermalSnapshot
import com.dps.devicescope.data.model.StatusLevel
import com.dps.devicescope.ui.components.DiagnosticScaffold
import com.dps.devicescope.ui.components.HeroMetricTile
import com.dps.devicescope.ui.components.MetricRow
import com.dps.devicescope.ui.components.SectionHeader
import com.dps.devicescope.ui.components.StatusBadge
import com.dps.devicescope.ui.components.TelemetryCard
import com.dps.devicescope.ui.components.TelemetryProgressBar
import com.dps.devicescope.ui.theme.LocalTelemetryColors
import com.dps.devicescope.ui.theme.TelemetryHeroNumber
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

class BatteryViewModel(container: DeviceContainer) : ViewModel() {
    val state: StateFlow<BatteryThermalSnapshot?> = container.batteryThermalRepository
        .observeBatteryAndThermal()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    companion object {
        fun factory(container: DeviceContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BatteryViewModel(container) as T
            }
    }
}

@Composable
fun BatteryScreen(
    container: DeviceContainer,
    onBack: () -> Unit
) {
    val viewModel: BatteryViewModel = viewModel(factory = BatteryViewModel.factory(container))
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalTelemetryColors.current

    DiagnosticScaffold(
        title = "Battery & Thermal",
        subtitle = "Real-time power & thermal analytics",
        onBack = onBack
    ) { padding ->
        val data = snapshot ?: return@DiagnosticScaffold
        val levelSeverity = Formatters.classifyBatteryLevel(data.levelPercent)
        val levelColor = colors.forLevel(levelSeverity)
        val tempSeverity = Formatters.classifyTemperatureC(data.temperatureC)
        val tempColor = colors.forLevel(tempSeverity)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                TelemetryCard(accentColor = levelColor) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Charge Level",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${data.levelPercent}%",
                                style = TelemetryHeroNumber,
                                color = levelColor
                            )
                        }
                        StatusBadge(
                            text = "${data.status} · ${data.powerSource}",
                            level = if (data.isCharging) StatusLevel.GOOD else levelSeverity
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    TelemetryProgressBar(
                        label = "Battery Capacity",
                        valueLabel = data.chargeCounterMah?.let { "$it mAh remaining" } ?: "${data.levelPercent}%",
                        progress = data.levelPercent / 100f,
                        barColor = levelColor
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroMetricTile(
                        icon = Icons.Rounded.DeviceThermostat,
                        label = "Battery Temp",
                        value = String.format(Locale.US, "%.1f °C", data.temperatureC),
                        sublabel = String.format(Locale.US, "%.1f °F", data.temperatureC * 9f / 5f + 32f),
                        accent = tempColor,
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetricTile(
                        icon = Icons.Rounded.Bolt,
                        label = "Voltage / Power",
                        value = String.format(Locale.US, "%.2f V", data.voltageMv / 1000f),
                        sublabel = Formatters.formatPowerWatts(data.voltageMv, data.currentNowMa),
                        accent = colors.info,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { SectionHeader("Electrical Telemetry") }

            item {
                TelemetryCard {
                    MetricRow("Charging Status", data.status)
                    MetricRow("Power Source", data.powerSource)
                    MetricRow(
                        label = "Battery Health",
                        value = data.health,
                        valueColor = colors.forLevel(data.healthLevel)
                    )
                    MetricRow("Voltage", "${data.voltageMv} mV")
                    MetricRow(
                        "Instant Current",
                        data.currentNowMa?.let { String.format(Locale.US, "%.0f mA", it) } ?: "Not Reported"
                    )
                    MetricRow(
                        "Average Current",
                        data.currentAvgMa?.let { String.format(Locale.US, "%.0f mA", it) } ?: "Not Reported"
                    )
                    MetricRow("Estimated Power", Formatters.formatPowerWatts(data.voltageMv, data.currentNowMa))
                    MetricRow("Technology", data.technology)
                    MetricRow(
                        "Charge Counter",
                        data.chargeCounterMah?.let { "$it mAh" } ?: "Not Reported",
                        showDivider = data.cycleCount != null
                    )
                    if (data.cycleCount != null) {
                        MetricRow("Charge Cycles", "${data.cycleCount} cycles", showDivider = false)
                    }
                }
            }

            item { SectionHeader("System Thermal State") }

            item {
                TelemetryCard(accentColor = colors.forLevel(data.thermalSeverity)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Throttling Status",
                            style = MaterialTheme.typography.titleSmall
                        )
                        StatusBadge(
                            text = data.thermalStatusLabel,
                            level = data.thermalSeverity
                        )
                    }
                    if (data.thermalHeadroom != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        val headroomPct = (data.thermalHeadroom * 100f).toInt().coerceIn(0, 150)
                        TelemetryProgressBar(
                            label = "10s Thermal Headroom Utilization",
                            valueLabel = "$headroomPct% of throttling limit",
                            progress = data.thermalHeadroom.coerceIn(0f, 1f),
                            barColor = when {
                                data.thermalHeadroom >= 0.9f -> colors.critical
                                data.thermalHeadroom >= 0.7f -> colors.warning
                                else -> colors.good
                            }
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Thermal headroom forecast is unavailable on this hardware/emulator.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
