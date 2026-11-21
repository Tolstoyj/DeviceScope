package com.dps.devicescope.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CellTower
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material.icons.rounded.SimCard
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dps.devicescope.core.DeviceContainer
import com.dps.devicescope.core.Formatters
import com.dps.devicescope.data.model.BatteryThermalSnapshot
import com.dps.devicescope.data.model.ConnectivitySnapshot
import com.dps.devicescope.data.model.DisplayGpuSnapshot
import com.dps.devicescope.data.model.StatusLevel
import com.dps.devicescope.data.model.SystemSnapshot
import com.dps.devicescope.data.model.ThemeMode
import com.dps.devicescope.ui.components.DiagnosticScaffold
import com.dps.devicescope.ui.components.SectionHeader
import com.dps.devicescope.ui.components.StatusBadge
import com.dps.devicescope.ui.components.TelemetryProgressBar
import com.dps.devicescope.ui.navigation.Routes
import com.dps.devicescope.ui.theme.LocalTelemetryColors
import com.dps.devicescope.ui.theme.TelemetryMono
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

data class DashboardUiState(
    val system: SystemSnapshot? = null,
    val battery: BatteryThermalSnapshot? = null,
    val connectivity: ConnectivitySnapshot? = null,
    val display: DisplayGpuSnapshot? = null,
    val sensorCount: Int = 0,
    val cameraCount: Int = 0,
    val biometricStatus: String = "Checking..."
)

class DashboardViewModel(container: DeviceContainer) : ViewModel() {

    private val staticMetaFlow = flow {
        val display = container.displayGpuRepository.loadSnapshot()
        val sensors = container.sensorRepository.getAllSensorsCatalog().size
        val cameras = container.cameraInfoRepository.loadCameras().cameras.size
        val bio = container.biometricRepository.captureSnapshot().overallStatus
        emit(Quad(display, sensors, cameras, bio))
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        container.systemInfoRepository.observeSystem(intervalMs = 2500L),
        container.batteryThermalRepository.observeBatteryAndThermal(),
        container.connectivityRepository.observeConnectivity(),
        staticMetaFlow
    ) { system, battery, connectivity, meta ->
        DashboardUiState(
            system = system,
            battery = battery,
            connectivity = connectivity,
            display = meta.first,
            sensorCount = meta.second,
            cameraCount = meta.third,
            biometricStatus = meta.fourth
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = DashboardUiState()
    )

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    companion object {
        fun factory(container: DeviceContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DashboardViewModel(container) as T
            }
    }
}

private data class DiagnosticModuleItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val route: String,
    val accent: Color
)

@Composable
fun DashboardScreen(
    container: DeviceContainer,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val telemetryColors = LocalTelemetryColors.current

    val hardwareModules = listOf(
        DiagnosticModuleItem(
            title = "System & CPU",
            subtitle = state.system?.let { "${it.cpuCoreCount} cores · ${it.socModel}" } ?: "SoC, RAM, Storage",
            icon = Icons.Rounded.Memory,
            route = Routes.SYSTEM,
            accent = telemetryColors.info
        ),
        DiagnosticModuleItem(
            title = "Battery & Thermal",
            subtitle = state.battery?.let {
                "${it.levelPercent}% · ${String.format(Locale.US, "%.1f°C", it.temperatureC)}"
            } ?: "Power & Thermals",
            icon = Icons.Rounded.BatteryChargingFull,
            route = Routes.BATTERY,
            accent = telemetryColors.good
        ),
        DiagnosticModuleItem(
            title = "Display & GPU",
            subtitle = state.display?.let {
                "${it.widthPx}×${it.heightPx} · ${it.activeRefreshRateHz.toInt()} Hz"
            } ?: "Screen, HDR, Vulkan",
            icon = Icons.Rounded.DisplaySettings,
            route = Routes.DISPLAY,
            accent = MaterialTheme.colorScheme.secondary
        ),
        DiagnosticModuleItem(
            title = "Sensors",
            subtitle = if (state.sensorCount > 0) "${state.sensorCount} sensors detected" else "Live sensor telemetry",
            icon = Icons.Rounded.Explore,
            route = Routes.SENSORS,
            accent = telemetryColors.warning
        ),
        DiagnosticModuleItem(
            title = "Cameras",
            subtitle = if (state.cameraCount > 0) "${state.cameraCount} camera(s) found" else "Camera2 hardware",
            icon = Icons.Rounded.CameraAlt,
            route = Routes.CAMERA,
            accent = telemetryColors.info
        ),
        DiagnosticModuleItem(
            title = "Microphone",
            subtitle = "Live PCM waveform & dB",
            icon = Icons.Rounded.Mic,
            route = Routes.MICROPHONE,
            accent = telemetryColors.critical
        )
    )

    val networkAndSecurityModules = listOf(
        DiagnosticModuleItem(
            title = "Connectivity",
            subtitle = state.connectivity?.let {
                if (it.wifiLinkSpeedMbps > 0 && it.activeTransport == "Wi-Fi") {
                    "Wi-Fi · ${it.wifiLinkSpeedMbps} Mbps"
                } else {
                    it.activeTransport
                }
            } ?: "Wi-Fi, BT, NFC, USB",
            icon = Icons.Rounded.Wifi,
            route = Routes.CONNECTIVITY,
            accent = telemetryColors.info
        ),
        DiagnosticModuleItem(
            title = "Cellular",
            subtitle = "Signal, 5G/LTE, Voice",
            icon = Icons.Rounded.CellTower,
            route = Routes.CELLULAR,
            accent = telemetryColors.good
        ),
        DiagnosticModuleItem(
            title = "Location & GNSS",
            subtitle = "GPS fix, Speed, Satellites",
            icon = Icons.Rounded.LocationOn,
            route = Routes.LOCATION,
            accent = telemetryColors.warning
        ),
        DiagnosticModuleItem(
            title = "Phone & SIM",
            subtitle = "Carrier, Dual-SIM, eSIM",
            icon = Icons.Rounded.SimCard,
            route = Routes.PHONE,
            accent = MaterialTheme.colorScheme.secondary
        ),
        DiagnosticModuleItem(
            title = "Biometrics",
            subtitle = state.biometricStatus,
            icon = Icons.Rounded.Fingerprint,
            route = Routes.BIOMETRIC,
            accent = telemetryColors.good
        ),
        DiagnosticModuleItem(
            title = "Export Report",
            subtitle = "Share Text or JSON",
            icon = Icons.Rounded.Description,
            route = Routes.REPORT,
            accent = telemetryColors.info
        )
    )

    val themeIcon = when (themeMode) {
        ThemeMode.SYSTEM -> Icons.Rounded.SettingsBrightness
        ThemeMode.LIGHT -> Icons.Rounded.LightMode
        ThemeMode.DARK -> Icons.Rounded.DarkMode
    }

    DiagnosticScaffold(
        title = "DeviceScope",
        subtitle = "Local Hardware Diagnostics",
        actions = {
            IconButton(onClick = onToggleTheme) {
                Icon(
                    imageVector = themeIcon,
                    contentDescription = "Theme: ${themeMode.label}"
                )
            }
            IconButton(onClick = { onNavigate(Routes.REPORT) }) {
                Icon(
                    imageVector = Icons.Rounded.Description,
                    contentDescription = "Export Diagnostic Report"
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                LiveVitalsHeroCard(
                    system = state.system,
                    battery = state.battery,
                    connectivity = state.connectivity,
                    onClickSystem = { onNavigate(Routes.SYSTEM) }
                )
            }

            item {
                SectionHeader(
                    title = "Hardware & Media",
                    trailingText = "6 modules"
                )
            }

            item {
                ModuleTwoColumnGrid(
                    items = hardwareModules,
                    onSelect = onNavigate
                )
            }

            item {
                SectionHeader(
                    title = "Network, Security & Export",
                    trailingText = "6 modules"
                )
            }

            item {
                ModuleTwoColumnGrid(
                    items = networkAndSecurityModules,
                    onSelect = onNavigate
                )
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun LiveVitalsHeroCard(
    system: SystemSnapshot?,
    battery: BatteryThermalSnapshot?,
    connectivity: ConnectivitySnapshot?,
    onClickSystem: () -> Unit
) {
    val telemetryColors = LocalTelemetryColors.current
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Card(
        onClick = onClickSystem,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, primary.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.12f),
                            secondary.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(primary.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PhoneAndroid,
                                contentDescription = null,
                                tint = primary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = system?.let { "${it.manufacturer} ${it.model}" } ?: "Scanning device...",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = system?.let {
                                    "Android ${it.androidVersion} (API ${it.sdkInt}) · ${if (it.is64Bit) "64-bit" else "32-bit"}"
                                } ?: "Reading system vitals...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    StatusBadge(
                        text = connectivity?.activeTransport ?: "Checking",
                        level = if (connectivity?.isInternetValidated == true) {
                            StatusLevel.GOOD
                        } else {
                            StatusLevel.NEUTRAL
                        }
                    )
                }

                // Quick live pills row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val batLevel = battery?.levelPercent ?: 0
                    val batColor = telemetryColors.forLevel(Formatters.classifyBatteryLevel(batLevel))
                    HeroMiniStat(
                        label = "Battery",
                        value = battery?.let { "${it.levelPercent}%" } ?: "--",
                        accent = batColor,
                        modifier = Modifier.weight(1f)
                    )
                    HeroMiniStat(
                        label = "Temp",
                        value = battery?.let { String.format(Locale.US, "%.1f°C", it.temperatureC) } ?: "--",
                        accent = battery?.let {
                            telemetryColors.forLevel(Formatters.classifyTemperatureC(it.temperatureC))
                        } ?: telemetryColors.info,
                        modifier = Modifier.weight(1f)
                    )
                    HeroMiniStat(
                        label = "Thermal",
                        value = battery?.thermalStatusLabel?.substringBefore(" ") ?: "--",
                        accent = battery?.let { telemetryColors.forLevel(it.thermalSeverity) }
                            ?: telemetryColors.neutral,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Live RAM & Storage bars
                if (system != null) {
                    val ramPct = Formatters.formatPercentage(system.usedRamBytes, system.totalRamBytes)
                    val storagePct = Formatters.formatPercentage(system.usedStorageBytes, system.totalStorageBytes)

                    TelemetryProgressBar(
                        label = "Memory (RAM)",
                        valueLabel = "${Formatters.formatBytes(system.usedRamBytes)} / ${Formatters.formatBytes(system.totalRamBytes)} ($ramPct%)",
                        progress = ramPct / 100f,
                        barColor = if (ramPct > 85) telemetryColors.warning else primary
                    )
                    TelemetryProgressBar(
                        label = "Internal Storage",
                        valueLabel = "${Formatters.formatBytes(system.usedStorageBytes)} / ${Formatters.formatBytes(system.totalStorageBytes)} ($storagePct%)",
                        progress = storagePct / 100f,
                        barColor = if (storagePct > 88) telemetryColors.critical else secondary
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroMiniStat(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = TelemetryMono.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ModuleTwoColumnGrid(
    items: List<DiagnosticModuleItem>,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowItems.forEach { item ->
                    ModuleTileCard(
                        item = item,
                        onClick = { onSelect(item.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ModuleTileCard(
    item: DiagnosticModuleItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(item.accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.accent,
                    modifier = Modifier.size(21.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
