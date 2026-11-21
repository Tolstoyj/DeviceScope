package com.dps.devicescope.ui.screens.report

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SaveAlt
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dps.devicescope.core.DeviceContainer
import com.dps.devicescope.data.model.DiagnosticReport
import com.dps.devicescope.data.model.ReportFormat
import com.dps.devicescope.ui.components.DiagnosticScaffold
import com.dps.devicescope.ui.components.SectionHeader
import com.dps.devicescope.ui.components.TelemetryCard
import com.dps.devicescope.ui.theme.TelemetryMono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportViewModel(private val container: DeviceContainer) : ViewModel() {

    private val _report = MutableStateFlow<DiagnosticReport?>(null)
    val report: StateFlow<DiagnosticReport?> = _report.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        regenerate()
    }

    fun regenerate() {
        viewModelScope.launch {
            _isLoading.value = true
            _report.value = withContext(Dispatchers.Default) {
                container.reportBuilder.build(
                    system = container.systemInfoRepository.captureSnapshot(),
                    battery = container.batteryThermalRepository.captureSnapshot(),
                    display = container.displayGpuRepository.captureSnapshot(),
                    connectivity = container.connectivityRepository.captureSnapshot(),
                    camera = container.cameraInfoRepository.captureSnapshot(),
                    cellular = container.cellularRepository.captureSnapshot(),
                    location = container.locationRepository.captureSnapshot(),
                    phoneSim = container.phoneSimRepository.captureSnapshot(),
                    biometric = container.biometricRepository.captureSnapshot(),
                    sensors = container.sensorRepository.getAllSensorsCatalog()
                )
            }
            _isLoading.value = false
        }
    }

    companion object {
        fun factory(container: DeviceContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReportViewModel(container) as T
            }
    }
}

@Composable
fun ReportScreen(
    container: DeviceContainer,
    onBack: () -> Unit
) {
    val viewModel: ReportViewModel = viewModel(factory = ReportViewModel.factory(container))
    val report by viewModel.report.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    var format by rememberSaveable { mutableStateOf(ReportFormat.TEXT) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val activeContent = remember(report, format) {
        when (format) {
            ReportFormat.TEXT -> report?.textContent.orEmpty()
            ReportFormat.JSON -> report?.jsonContent.orEmpty()
        }
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(format.mimeType)
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(activeContent.toByteArray(Charsets.UTF_8))
                        }
                    }.isSuccess
                }
                snackbarHostState.showSnackbar(
                    if (ok) "Report saved successfully" else "Failed to write file"
                )
            }
        }
    }

    DiagnosticScaffold(
        title = "Diagnostic Report",
        subtitle = report?.deviceSummary ?: "Generating full device snapshot...",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        actions = {
            IconButton(onClick = { viewModel.regenerate() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Regenerate report")
            }
        }
    ) { padding ->
        if (isLoading && report == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@DiagnosticScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ReportFormat.entries.forEachIndexed { index, item ->
                        SegmentedButton(
                            selected = format == item,
                            onClick = { format = item },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ReportFormat.entries.size
                            )
                        ) {
                            Text(item.label)
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = format.mimeType
                                putExtra(Intent.EXTRA_SUBJECT, "DeviceScope Report - ${report?.deviceSummary.orEmpty()}")
                                putExtra(Intent.EXTRA_TEXT, activeContent)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Diagnostic Report"))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }

                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("DeviceScope Report", activeContent))
                            scope.launch { snackbarHostState.showSnackbar("Copied ${format.label} report to clipboard") }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }

                    OutlinedButton(
                        onClick = {
                            val safeName = "devicescope_report.${format.extension}"
                            saveFileLauncher.launch(safeName)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.SaveAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save")
                    }
                }
            }

            item {
                SectionHeader(
                    title = "Preview (${format.label})",
                    trailingText = "${activeContent.length} chars"
                )
            }

            item {
                TelemetryCard {
                    SelectionContainer {
                        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            Text(
                                text = activeContent,
                                style = TelemetryMono.copy(fontSize = 12.sp, lineHeight = 18.sp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
