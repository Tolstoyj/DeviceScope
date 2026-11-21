package com.dps.devicescope.ui.screens.system

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dps.devicescope.core.DeviceContainer
import com.dps.devicescope.core.Formatters
import com.dps.devicescope.data.model.StatusLevel
import com.dps.devicescope.data.model.SystemSnapshot
import com.dps.devicescope.ui.components.DiagnosticScaffold
import com.dps.devicescope.ui.components.FeatureChipGrid
import com.dps.devicescope.ui.components.HeroMetricTile
import com.dps.devicescope.ui.components.MetricRow
import com.dps.devicescope.ui.components.SectionHeader
import com.dps.devicescope.ui.components.StatusBadge
import com.dps.devicescope.ui.components.TelemetryCard
import com.dps.devicescope.ui.components.TelemetryProgressBar
import com.dps.devicescope.ui.theme.LocalTelemetryColors
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class SystemViewModel(container: DeviceContainer) : ViewModel() {
    val state: StateFlow<SystemSnapshot?> = container.systemInfoRepository
        .observeSystem(intervalMs = 1500L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    companion object {
        fun factory(container: DeviceContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SystemViewModel(container) as T
            }
    }
}

@Composable
fun SystemScreen(
    container: DeviceContainer,
    onBack: () -> Unit
) {
    val viewModel: SystemViewModel = viewModel(factory = SystemViewModel.factory(container))
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalTelemetryColors.current

    DiagnosticScaffold(
        title = "System & Hardware",
        subtitle = snapshot?.let { "${it.manufacturer} ${it.model}" } ?: "Loading...",
        onBack = onBack
    ) { padding ->
        val data = snapshot
        if (data == null) return@DiagnosticScaffold

        val ramPct = Formatters.formatPercentage(data.usedRamBytes, data.totalRamBytes)
        val storagePct = Formatters.formatPercentage(data.usedStorageBytes, data.totalStorageBytes)

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HeroMetricTile(
                        icon = Icons.Rounded.Memory,
                        label = "RAM Usage",
                        value = "$ramPct%",
                        sublabel = "${Formatters.formatBytes(data.availableRamBytes)} free",
                        accent = if (ramPct > 85) colors.warning else colors.info,
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetricTile(
                        icon = Icons.Rounded.Storage,
                        label = "Storage",
                        value = "$storagePct%",
                        sublabel = "${Formatters.formatBytes(data.availableStorageBytes)} free",
                        accent = if (storagePct > 88) colors.critical else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { SectionHeader("Memory & Storage") }

            item {
                TelemetryCard {
                    TelemetryProgressBar(
                        label = "RAM Used",
                        valueLabel = "${Formatters.formatBytes(data.usedRamBytes)} / ${Formatters.formatBytes(data.totalRamBytes)}",
                        progress = ramPct / 100f,
                        barColor = if (data.isLowMemory) colors.critical else colors.info
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    TelemetryProgressBar(
                        label = "Internal Storage Used",
                        valueLabel = "${Formatters.formatBytes(data.usedStorageBytes)} / ${Formatters.formatBytes(data.totalStorageBytes)}",
                        progress = storagePct / 100f,
                        barColor = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Memory Pressure",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatusBadge(
                            text = if (data.isLowMemory) "Low Memory" else "Healthy",
                            level = if (data.isLowMemory) StatusLevel.CRITICAL else StatusLevel.GOOD
                        )
                    }
                }
            }

            item { SectionHeader("Processor & SoC", trailingText = "${data.cpuCoreCount} Cores") }

            item {
                TelemetryCard {
                    MetricRow("SoC Manufacturer", data.socManufacturer)
                    MetricRow("SoC / Platform", data.socModel)
                    MetricRow("Hardware", data.cpuHardwareLine)
                    MetricRow("Board", data.board)
                    MetricRow("Architecture", if (data.is64Bit) "64-bit" else "32-bit", showDivider = false)
                    Spacer(modifier = Modifier.height(10.dp))
                    FeatureChipGrid(items = data.supportedAbis)
                }
            }

            item {
                TelemetryCard {
                    Text(
                        text = "Live CPU Core Frequencies",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    val anyReadable = data.coreFrequencies.any { it.currentKhz != null }
                    if (!anyReadable) {
                        Text(
                            text = "Per-core CPU frequency scaling is virtualized or restricted by the kernel on this device (${data.cpuCoreCount} active cores detected).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        data.coreFrequencies.forEachIndexed { idx, core ->
                            val max = core.maxKhz?.takeIf { it > 0 } ?: 3_000_000L
                            val cur = core.currentKhz ?: 0L
                            val ratio = if (cur > 0) (cur.toFloat() / max.toFloat()).coerceIn(0.05f, 1f) else 0f
                            TelemetryProgressBar(
                                label = "Core ${core.coreIndex}",
                                valueLabel = if (core.maxKhz != null) {
                                    "${Formatters.formatFrequencyKhz(core.currentKhz)} / ${Formatters.formatFrequencyKhz(core.maxKhz)}"
                                } else {
                                    Formatters.formatFrequencyKhz(core.currentKhz)
                                },
                                progress = ratio,
                                barColor = colors.info
                            )
                            if (idx != data.coreFrequencies.lastIndex) {
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Device & Operating System") }

            item {
                TelemetryCard {
                    MetricRow("Manufacturer", data.manufacturer)
                    MetricRow("Brand", data.brand)
                    MetricRow("Model", data.model)
                    MetricRow("Device Codename", data.device)
                    MetricRow("Android Version", "${data.androidVersion} (API ${data.sdkInt})")
                    MetricRow("Security Patch", data.securityPatch)
                    MetricRow("Kernel Version", data.kernelVersion)
                    MetricRow("Build Display", data.buildDisplay)
                    MetricRow("System Uptime", Formatters.formatUptime(data.uptimeMs), showDivider = false)
                }
            }
        }
    }
}
