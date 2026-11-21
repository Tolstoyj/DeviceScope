package com.dps.devicescope.ui.screens.connectivity

import android.Manifest
import android.os.Build
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
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.dps.devicescope.core.rememberMultiPermissionState
import com.dps.devicescope.data.model.ConnectivitySnapshot
import com.dps.devicescope.data.model.StatusLevel
import com.dps.devicescope.ui.components.DiagnosticScaffold
import com.dps.devicescope.ui.components.FeatureChipGrid
import com.dps.devicescope.ui.components.HeroMetricTile
import com.dps.devicescope.ui.components.MetricRow
import com.dps.devicescope.ui.components.SectionHeader
import com.dps.devicescope.ui.components.StatusBadge
import com.dps.devicescope.ui.components.TelemetryCard
import com.dps.devicescope.ui.theme.LocalTelemetryColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class ConnectivityViewModel(private val container: DeviceContainer) : ViewModel() {
    private val refreshTrigger = MutableStateFlow(0)

    val state: StateFlow<ConnectivitySnapshot?> = combine(
        container.connectivityRepository.observeConnectivity(),
        refreshTrigger
    ) { snapshot, _ -> container.connectivityRepository.captureSnapshot().takeIf { refreshTrigger.value > 0 } ?: snapshot }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    fun refresh() {
        refreshTrigger.value++
    }

    companion object {
        fun factory(container: DeviceContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ConnectivityViewModel(container) as T
            }
    }
}

@Composable
fun ConnectivityScreen(
    container: DeviceContainer,
    onBack: () -> Unit
) {
    val viewModel: ConnectivityViewModel = viewModel(factory = ConnectivityViewModel.factory(container))
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalTelemetryColors.current

    val optionalPerms = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }
    val permState = rememberMultiPermissionState(optionalPerms) {
        viewModel.refresh()
    }

    DiagnosticScaffold(
        title = "Connectivity",
        subtitle = "Wi-Fi, Bluetooth, NFC & USB OTG",
        onBack = onBack
    ) { padding ->
        val data = snapshot ?: return@DiagnosticScaffold

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
                        icon = Icons.Rounded.Wifi,
                        label = "Active Network",
                        value = data.activeTransport,
                        sublabel = if (data.isInternetValidated) "Internet Validated" else "No Internet",
                        accent = if (data.isInternetValidated) colors.good else colors.warning,
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetricTile(
                        icon = Icons.Rounded.Bluetooth,
                        label = "Bluetooth",
                        value = when {
                            !data.bluetoothSupported -> "None"
                            data.bluetoothEnabled -> "On"
                            else -> "Off"
                        },
                        sublabel = if (data.supportsBle) "BLE Supported" else "Classic Only",
                        accent = if (data.bluetoothEnabled) colors.info else colors.neutral,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            if (!permState.allGranted) {
                item {
                    TelemetryCard(accentColor = MaterialTheme.colorScheme.primary) {
                        Text(
                            text = "Optional Permissions",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Grant Location and Nearby Devices permissions to also read Wi-Fi SSID and paired Bluetooth device names.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(onClick = permState.launchRequest) {
                            Text("Grant Optional Permissions")
                        }
                    }
                }
            }

            item { SectionHeader("Active Network & IP") }

            item {
                TelemetryCard {
                    MetricRow("Active Transport", data.activeTransport)
                    MetricRow(
                        "Internet Validated",
                        if (data.isInternetValidated) "Yes" else "No",
                        valueColor = if (data.isInternetValidated) colors.good else colors.warning
                    )
                    MetricRow("Metered Connection", if (data.isMetered) "Metered" else "Unmetered")
                    MetricRow("VPN Active", if (data.isVpnActive) "Active" else "None")
                    if (data.downstreamKbps > 0 || data.upstreamKbps > 0) {
                        MetricRow(
                            "Est. Bandwidth",
                            "↓ ${data.downstreamKbps / 1000} Mbps · ↑ ${data.upstreamKbps / 1000} Mbps"
                        )
                    }
                    MetricRow("IPv4 Address", data.ipv4Addresses.joinToString("\n").ifBlank { "None" })
                    MetricRow("IPv6 Address", data.ipv6Addresses.firstOrNull() ?: "None")
                    MetricRow("DNS Servers", data.dnsServers.joinToString("\n").ifBlank { "None" }, showDivider = false)
                }
            }

            item { SectionHeader("Wi-Fi Radio") }

            item {
                TelemetryCard {
                    MetricRow(
                        "Wi-Fi State",
                        if (data.wifiEnabled) "Enabled" else "Disabled",
                        valueColor = if (data.wifiEnabled) colors.good else colors.neutral
                    )
                    MetricRow("SSID", data.wifiSsid)
                    MetricRow(
                        "Frequency / Band",
                        if (data.wifiFrequencyMhz > 0) "${data.wifiBand} (${data.wifiFrequencyMhz} MHz)" else "N/A"
                    )
                    MetricRow(
                        "Link Speed",
                        if (data.wifiLinkSpeedMbps > 0) "${data.wifiLinkSpeedMbps} Mbps" else "N/A"
                    )
                    MetricRow(
                        "Signal RSSI",
                        data.wifiRssiDbm?.let { "$it dBm" } ?: "N/A",
                        showDivider = false
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    FeatureChipGrid(
                        items = buildList {
                            add("2.4 GHz")
                            if (data.supports5Ghz) add("5 GHz")
                            if (data.supports6Ghz) add("6 GHz (Wi-Fi 6E)")
                            if (data.supportsWifiDirect) add("Wi-Fi Direct")
                        }
                    )
                }
            }

            item { SectionHeader("Bluetooth") }

            item {
                TelemetryCard {
                    MetricRow("Adapter Name", data.bluetoothName)
                    MetricRow(
                        "Bluetooth State",
                        when {
                            !data.bluetoothSupported -> "Not Supported"
                            data.bluetoothEnabled -> "Enabled"
                            else -> "Disabled"
                        }
                    )
                    MetricRow("Bluetooth Low Energy (BLE)", if (data.supportsBle) "Supported" else "No")
                    MetricRow(
                        "Paired Devices",
                        if (data.bluetoothPermissionMissing) "Permission needed" else "${data.pairedDevices.size} paired",
                        showDivider = data.pairedDevices.isNotEmpty()
                    )
                    if (data.pairedDevices.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FeatureChipGrid(items = data.pairedDevices)
                    }
                }
            }

            item { SectionHeader("NFC & USB Hardware") }

            item {
                TelemetryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("NFC Hardware", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        StatusBadge(
                            text = when {
                                !data.nfcSupported -> "Not Present"
                                data.nfcEnabled -> "Enabled"
                                else -> "Disabled"
                            },
                            level = when {
                                !data.nfcSupported -> StatusLevel.NEUTRAL
                                data.nfcEnabled -> StatusLevel.GOOD
                                else -> StatusLevel.WARNING
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    MetricRow("USB Host (OTG)", if (data.usbHostSupported) "Supported" else "Not Supported")
                    MetricRow("USB Accessory Mode", if (data.usbAccessorySupported) "Supported" else "Not Supported")
                    MetricRow("Connected USB Devices", "${data.connectedUsbDeviceCount}", showDivider = false)
                }
            }
        }
    }
}
