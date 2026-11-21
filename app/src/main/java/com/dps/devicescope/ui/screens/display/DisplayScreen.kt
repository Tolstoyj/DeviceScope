package com.dps.devicescope.ui.screens.display

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dps.devicescope.core.DeviceContainer
import com.dps.devicescope.data.model.DisplayGpuSnapshot
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class DisplayViewModel(private val container: DeviceContainer) : ViewModel() {
    private val _state = MutableStateFlow<DisplayGpuSnapshot?>(null)
    val state: StateFlow<DisplayGpuSnapshot?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = container.displayGpuRepository.loadSnapshot()
        }
    }

    companion object {
        fun factory(container: DeviceContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    DisplayViewModel(container) as T
            }
    }
}

@Composable
fun DisplayScreen(
    container: DeviceContainer,
    onBack: () -> Unit
) {
    val viewModel: DisplayViewModel = viewModel(factory = DisplayViewModel.factory(container))
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalTelemetryColors.current
    var showScreenTest by remember { mutableStateOf(false) }

    if (showScreenTest) {
        DeadPixelTestDialog(onDismiss = { showScreenTest = false })
    }

    DiagnosticScaffold(
        title = "Display & GPU",
        subtitle = "Panel metrics, HDR & graphics stack",
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
                        icon = Icons.Rounded.AspectRatio,
                        label = "Resolution",
                        value = "${data.widthPx}×${data.heightPx}",
                        sublabel = "${data.densityDpi} dpi · ~${String.format(Locale.US, "%.1f\"", data.diagonalInches)}",
                        accent = colors.info,
                        modifier = Modifier.weight(1f)
                    )
                    HeroMetricTile(
                        icon = Icons.Rounded.Speed,
                        label = "Refresh Rate",
                        value = String.format(Locale.US, "%.0f Hz", data.activeRefreshRateHz),
                        sublabel = "${data.supportedRefreshRatesHz.size} mode(s)",
                        accent = colors.good,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item { SectionHeader("Panel Characteristics") }

            item {
                TelemetryCard {
                    MetricRow("Physical Resolution", "${data.widthPx} × ${data.heightPx} px")
                    MetricRow("Logical Size", "${data.widthDp} × ${data.heightDp} dp")
                    MetricRow("Density", "${data.densityDpi} dpi (${String.format(Locale.US, "%.2fx", data.densityScale)})")
                    MetricRow("Exact PPI (X / Y)", String.format(Locale.US, "%.1f / %.1f ppi", data.xdpi, data.ydpi))
                    MetricRow("Est. Diagonal", String.format(Locale.US, "%.2f inches", data.diagonalInches))
                    MetricRow("Orientation", data.orientation)
                    MetricRow("Wide Color Gamut", if (data.isWideColorGamut) "Supported" else "Standard sRGB")
                    MetricRow(
                        "HDR Support",
                        if (data.isHdrSupported) "Supported" else "Not Supported",
                        valueColor = if (data.isHdrSupported) colors.good else MaterialTheme.colorScheme.onSurfaceVariant,
                        showDivider = false
                    )
                    if (data.hdrTypes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        FeatureChipGrid(items = data.hdrTypes)
                    }
                    if (data.supportedRefreshRatesHz.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Supported Refresh Modes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        FeatureChipGrid(
                            items = data.supportedRefreshRatesHz.map {
                                String.format(Locale.US, "%.0f Hz", it)
                            }
                        )
                    }
                }
            }

            item { SectionHeader("GPU & Graphics APIs") }

            item {
                TelemetryCard {
                    MetricRow("GPU Renderer", data.gpuRenderer)
                    MetricRow("GPU Vendor", data.gpuVendor)
                    MetricRow("OpenGL ES", data.openGlEsVersion)
                    MetricRow("Driver Version", data.gpuGlVersion)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vulkan API",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        StatusBadge(
                            text = if (data.vulkanSupported) {
                                "v${data.vulkanVersion} (${data.vulkanHardwareLevel})"
                            } else {
                                "Unsupported"
                            },
                            level = if (data.vulkanSupported) StatusLevel.GOOD else StatusLevel.NEUTRAL
                        )
                    }
                }
            }

            item { SectionHeader("Interactive Display Diagnostics") }

            item {
                TelemetryCard(accentColor = MaterialTheme.colorScheme.primary) {
                    Text(
                        text = "Dead Pixel & Uniformity Tester",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cycle through full-screen Red, Green, Blue, White, Black, Grayscale gradient, and alignment grid to inspect panel defects.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showScreenTest = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ColorLens,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Launch Screen Test")
                    }
                }
            }
        }
    }
}

@Composable
private fun DeadPixelTestDialog(onDismiss: () -> Unit) {
    val testPatterns = remember {
        listOf("Solid Red", "Solid Green", "Solid Blue", "Pure White", "Pure Black", "Grayscale Ramp", "Alignment Grid")
    }
    var index by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { index = (index + 1) % testPatterns.size }
        ) {
            when (index) {
                0 -> Box(Modifier.fillMaxSize().background(Color.Red))
                1 -> Box(Modifier.fillMaxSize().background(Color.Green))
                2 -> Box(Modifier.fillMaxSize().background(Color.Blue))
                3 -> Box(Modifier.fillMaxSize().background(Color.White))
                4 -> Box(Modifier.fillMaxSize().background(Color.Black))
                5 -> Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Black, Color.Gray, Color.White)))
                )
                else -> Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    val step = 64f
                    var x = 0f
                    while (x <= size.width) {
                        drawLine(Color.White.copy(alpha = 0.5f), Offset(x, 0f), Offset(x, size.height), 1.5f)
                        x += step
                    }
                    var y = 0f
                    while (y <= size.height) {
                        drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1.5f)
                        y += step
                    }
                    drawLine(Color.Cyan, Offset(0f, 0f), Offset(size.width, size.height), 2f)
                    drawLine(Color.Cyan, Offset(size.width, 0f), Offset(0f, size.height), 2f)
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 36.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.68f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "${testPatterns[index]} (${index + 1}/${testPatterns.size}) · Tap to cycle",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Exit screen test",
                            tint = Color.White,
                            modifier = Modifier.padding(4.dp).size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
