package com.dps.devicescope.ui.screens.sensors

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dps.devicescope.core.DeviceContainer
import com.dps.devicescope.data.model.LiveSensorReading
import com.dps.devicescope.data.model.SensorCatalogItem
import com.dps.devicescope.data.model.StatusLevel
import com.dps.devicescope.ui.components.DiagnosticScaffold
import com.dps.devicescope.ui.components.MetricRow
import com.dps.devicescope.ui.components.StatusBadge
import com.dps.devicescope.ui.components.TelemetryCard
import com.dps.devicescope.ui.components.TelemetryProgressBar
import com.dps.devicescope.ui.theme.LocalTelemetryColors
import com.dps.devicescope.ui.theme.TelemetryMono
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

class SensorsViewModel(container: DeviceContainer) : ViewModel() {
    val liveReadings: StateFlow<List<LiveSensorReading>> = container.sensorRepository
        .observeLiveReadings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val catalog: List<SensorCatalogItem> = container.sensorRepository.getAllSensorsCatalog()

    companion object {
        fun factory(container: DeviceContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SensorsViewModel(container) as T
            }
    }
}

@Composable
fun SensorsScreen(
    container: DeviceContainer,
    onBack: () -> Unit
) {
    val viewModel: SensorsViewModel = viewModel(factory = SensorsViewModel.factory(container))
    val readings by viewModel.liveReadings.collectAsStateWithLifecycle()
    val catalog = viewModel.catalog
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    DiagnosticScaffold(
        title = "Sensors Hub",
        subtitle = "${catalog.size} hardware & virtual sensors",
        onBack = onBack
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("Live Telemetry")
                }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("All Sensors (${catalog.size})")
                }
            }

            if (selectedTab == 0) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(readings, key = { it.type }) { reading ->
                        LiveSensorCard(reading)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(catalog.indices.toList(), key = { it }) { idx ->
                        SensorCatalogCard(catalog[idx])
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSensorCard(reading: LiveSensorReading) {
    val colors = LocalTelemetryColors.current
    val accent = if (reading.isAvailable) colors.forLevel(reading.severity) else colors.neutral

    TelemetryCard(accentColor = if (reading.isAvailable) accent else null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = reading.label,
                style = MaterialTheme.typography.titleMedium
            )
            StatusBadge(
                text = if (reading.isAvailable) "Live" else "Unavailable",
                level = if (reading.isAvailable) reading.severity else StatusLevel.NEUTRAL
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = reading.formattedValue,
            style = TelemetryMono.copy(fontSize = MaterialTheme.typography.titleSmall.fontSize),
            color = if (reading.isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (reading.secondaryValue.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = reading.secondaryValue,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (reading.isAvailable) {
            Spacer(modifier = Modifier.height(10.dp))
            TelemetryProgressBar(
                label = "Relative Intensity",
                valueLabel = "${(reading.normalizedIntensity * 100).toInt()}%",
                progress = reading.normalizedIntensity,
                barColor = accent
            )
        }
    }
}

@Composable
private fun SensorCatalogCard(item: SensorCatalogItem) {
    TelemetryCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.typeLabel, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (item.isWakeUp) {
                StatusBadge(text = "Wake-Up", level = StatusLevel.INFO)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        MetricRow("Vendor", item.vendor)
        MetricRow("Power Draw", String.format(Locale.US, "%.2f mA", item.powerMa))
        MetricRow("Max Range", String.format(Locale.US, "%.2f", item.maxRange))
        MetricRow("Resolution", String.format(Locale.US, "%.5f", item.resolution), showDivider = false)
    }
}
