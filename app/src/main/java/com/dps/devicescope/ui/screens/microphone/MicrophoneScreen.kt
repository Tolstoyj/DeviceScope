package com.dps.devicescope.ui.screens.microphone

import android.Manifest
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dps.devicescope.core.DeviceContainer
import com.dps.devicescope.core.rememberMultiPermissionState
import com.dps.devicescope.data.model.MicrophoneState
import com.dps.devicescope.data.model.StatusLevel
import com.dps.devicescope.ui.components.DiagnosticScaffold
import com.dps.devicescope.ui.components.MetricRow
import com.dps.devicescope.ui.components.PermissionGateCard
import com.dps.devicescope.ui.components.SectionHeader
import com.dps.devicescope.ui.components.StatusBadge
import com.dps.devicescope.ui.components.TelemetryCard
import com.dps.devicescope.ui.components.TelemetryProgressBar
import com.dps.devicescope.ui.theme.LocalTelemetryColors
import com.dps.devicescope.ui.theme.TelemetryHeroNumber
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

class MicrophoneViewModel(private val container: DeviceContainer) : ViewModel() {

    private val _state = MutableStateFlow(
        MicrophoneState(inputDevices = container.microphoneRepository.getInputDevices())
    )
    val state: StateFlow<MicrophoneState> = _state.asStateFlow()

    private var captureJob: Job? = null
    private val maxBars = 64

    fun startCapture() {
        if (captureJob?.isActive == true) return
        _state.update {
            it.copy(
                hasPermission = true,
                isRecording = true,
                isPaused = false,
                statusLabel = "Live Monitoring",
                error = null
            )
        }
        captureJob = viewModelScope.launch {
            container.microphoneRepository.observeAudioFrames()
                .catch { e ->
                    _state.update {
                        it.copy(
                            isRecording = false,
                            statusLabel = "Error",
                            error = e.localizedMessage ?: "Microphone capture failed"
                        )
                    }
                }
                .collect { frame ->
                    _state.update { current ->
                        val nextHistory = (current.waveformHistory + frame.normalizedLevel)
                            .takeLast(maxBars)
                        current.copy(
                            isRecording = true,
                            statusLabel = "Live Monitoring",
                            rmsDbfs = frame.rmsDbfs,
                            peakDbfs = frame.peakDbfs,
                            peakHoldDbfs = maxOf(current.peakHoldDbfs, frame.peakDbfs),
                            normalizedLevel = frame.normalizedLevel,
                            waveformHistory = nextHistory
                        )
                    }
                }
        }
    }

    fun stopCapture(paused: Boolean = false) {
        captureJob?.cancel()
        captureJob = null
        _state.update {
            it.copy(
                isRecording = false,
                isPaused = paused,
                statusLabel = if (paused) "Paused" else "Idle"
            )
        }
    }

    fun togglePause() {
        if (_state.value.isRecording) {
            stopCapture(paused = true)
        } else {
            startCapture()
        }
    }

    fun resetPeakHold() {
        _state.update { it.copy(peakHoldDbfs = it.peakDbfs) }
    }

    override fun onCleared() {
        super.onCleared()
        captureJob?.cancel()
    }

    companion object {
        fun factory(container: DeviceContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MicrophoneViewModel(container) as T
            }
    }
}

@Composable
fun MicrophoneScreen(
    container: DeviceContainer,
    onBack: () -> Unit
) {
    val viewModel: MicrophoneViewModel = viewModel(factory = MicrophoneViewModel.factory(container))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalTelemetryColors.current

    val permState = rememberMultiPermissionState(listOf(Manifest.permission.RECORD_AUDIO)) { granted ->
        if (granted[Manifest.permission.RECORD_AUDIO] == true) {
            viewModel.startCapture()
        }
    }

    LaunchedEffect(permState.allGranted) {
        if (permState.allGranted && !state.isPaused) {
            viewModel.startCapture()
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.stopCapture(paused = false) }
    }

    DiagnosticScaffold(
        title = "Microphone",
        subtitle = "Real-time PCM level meter & waveform",
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
                        title = "Microphone Permission Required",
                        description = "DeviceScope samples raw PCM audio in memory solely to compute real-time dBFS levels and draw the live waveform. No audio is saved or transmitted.",
                        hasRequestedOnce = permState.hasRequestedOnce,
                        onGrantClick = permState.launchRequest,
                        onOpenSettings = permState.openSettings
                    )
                }
            } else {
                item {
                    val levelColor = when {
                        state.normalizedLevel >= 0.85f -> colors.critical
                        state.normalizedLevel >= 0.6f -> colors.warning
                        state.isRecording -> colors.good
                        else -> colors.neutral
                    }

                    TelemetryCard(accentColor = levelColor) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "RMS Input Level",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f dBFS", state.rmsDbfs),
                                    style = TelemetryHeroNumber,
                                    color = levelColor
                                )
                            }
                            StatusBadge(
                                text = state.statusLabel,
                                level = when {
                                    state.error != null -> StatusLevel.CRITICAL
                                    state.isRecording -> StatusLevel.GOOD
                                    else -> StatusLevel.NEUTRAL
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        AudioWaveformCanvas(
                            amplitudes = state.waveformHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(124.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        TelemetryProgressBar(
                            label = "Normalized Signal",
                            valueLabel = "Peak Hold: ${String.format(Locale.US, "%.1f dBFS", state.peakHoldDbfs)}",
                            progress = state.normalizedLevel,
                            barColor = levelColor
                        )

                        if (state.error != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = state.error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.critical
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { viewModel.togglePause() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = if (state.isRecording) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (state.isRecording) "Pause" else "Resume")
                            }
                            OutlinedButton(
                                onClick = { viewModel.resetPeakHold() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset Peak")
                            }
                        }
                    }
                }
            }

            item { SectionHeader("Capture Configuration") }

            item {
                TelemetryCard {
                    MetricRow("Sample Rate", "${state.sampleRateHz} Hz")
                    MetricRow("Channel Format", state.channelLabel)
                    MetricRow("Instant Peak", String.format(Locale.US, "%.1f dBFS", state.peakDbfs), showDivider = false)
                }
            }

            if (state.inputDevices.isNotEmpty()) {
                item { SectionHeader("Audio Input Devices", trailingText = "${state.inputDevices.size} found") }
                state.inputDevices.forEach { dev ->
                    item {
                        TelemetryCard {
                            Text(text = dev.typeLabel, style = MaterialTheme.typography.titleSmall)
                            Spacer(modifier = Modifier.height(6.dp))
                            MetricRow("Product Name", dev.productName)
                            MetricRow("Sample Rates", dev.sampleRates)
                            MetricRow("Channels", dev.channelCounts, showDivider = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioWaveformCanvas(
    amplitudes: List<Float>,
    modifier: Modifier = Modifier
) {
    val startColor = MaterialTheme.colorScheme.primary
    val endColor = MaterialTheme.colorScheme.secondary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier) {
        val centerY = size.height / 2f
        drawLine(
            color = baselineColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 1.5f
        )

        if (amplitudes.isEmpty()) return@Canvas

        val barCount = 64
        val slotWidth = size.width / barCount
        val barWidth = (slotWidth * 0.62f).coerceAtLeast(3f)
        val gradient = Brush.verticalGradient(listOf(startColor, endColor, startColor))

        val offset = (barCount - amplitudes.size).coerceAtLeast(0)
        amplitudes.forEachIndexed { idx, amp ->
            val clamped = amp.coerceIn(0.03f, 1f)
            val halfHeight = clamped * (size.height * 0.46f)
            val x = (offset + idx) * slotWidth + (slotWidth - barWidth) / 2f
            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, centerY - halfHeight),
                size = Size(barWidth, halfHeight * 2f),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
