package com.dps.devicescope.ui.screens.camera

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
import com.dps.devicescope.data.model.CameraSnapshot
import com.dps.devicescope.data.model.CameraSpec
import com.dps.devicescope.data.model.StatusLevel
import com.dps.devicescope.ui.components.DiagnosticScaffold
import com.dps.devicescope.ui.components.FeatureChipGrid
import com.dps.devicescope.ui.components.MetricRow
import com.dps.devicescope.ui.components.SectionHeader
import com.dps.devicescope.ui.components.StatusBadge
import com.dps.devicescope.ui.components.TelemetryCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale

class CameraViewModel(private val container: DeviceContainer) : ViewModel() {
    private val _state = MutableStateFlow<CameraSnapshot?>(null)
    val state: StateFlow<CameraSnapshot?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = container.cameraInfoRepository.loadCameras()
        }
    }

    companion object {
        fun factory(container: DeviceContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CameraViewModel(container) as T
            }
    }
}

@Composable
fun CameraScreen(
    container: DeviceContainer,
    onBack: () -> Unit
) {
    val viewModel: CameraViewModel = viewModel(factory = CameraViewModel.factory(container))
    val snapshot by viewModel.state.collectAsStateWithLifecycle()

    DiagnosticScaffold(
        title = "Cameras",
        subtitle = "Camera2 hardware & lens capabilities",
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
                TelemetryCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Detected Cameras",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = data.error ?: "${data.cameras.size} logical camera(s) enumerated via Camera2 API",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusBadge(
                            text = "${data.cameras.size} Found",
                            level = if (data.cameras.isNotEmpty()) StatusLevel.GOOD else StatusLevel.WARNING
                        )
                    }
                }
            }

            if (data.cameras.isNotEmpty()) {
                item { SectionHeader("Camera Modules") }
                items(data.cameras, key = { it.id }) { camera ->
                    CameraModuleCard(camera)
                }
            }
        }
    }
}

@Composable
private fun CameraModuleCard(camera: CameraSpec) {
    TelemetryCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${camera.lensFacing} Camera (ID ${camera.id})",
                style = MaterialTheme.typography.titleMedium
            )
            StatusBadge(
                text = camera.hardwareLevel,
                level = when (camera.hardwareLevel) {
                    "Level 3", "Full" -> StatusLevel.GOOD
                    "Limited" -> StatusLevel.INFO
                    else -> StatusLevel.NEUTRAL
                }
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        MetricRow("Max Resolution", Formatters.formatMegapixels(camera.maxJpegWidth, camera.maxJpegHeight))
        MetricRow("Sensor Size", camera.sensorSizeMm)
        MetricRow("Sensor Orientation", "${camera.sensorOrientationDeg}°")
        MetricRow("Flash Unit", if (camera.hasFlash) "Available" else "None")
        if (camera.focalLengthsMm.isNotEmpty()) {
            MetricRow(
                "Focal Length(s)",
                camera.focalLengthsMm.joinToString(", ") { String.format(Locale.US, "%.2f mm", it) }
            )
        }
        if (camera.apertures.isNotEmpty()) {
            MetricRow(
                "Aperture(s)",
                camera.apertures.joinToString(", ") { String.format(Locale.US, "f/%.1f", it) }
            )
        }
        if (camera.physicalCameraIds.isNotEmpty()) {
            MetricRow("Physical IDs", camera.physicalCameraIds.joinToString(", "))
        }
        MetricRow(
            "AE FPS Ranges",
            camera.fpsRanges.joinToString(", ").ifBlank { "Unknown" },
            showDivider = false
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Capabilities",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        FeatureChipGrid(items = camera.capabilities, emptyText = "Basic camera capture only")
    }
}
