package com.dps.devicescope.ui.screens.phone

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
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
import com.dps.devicescope.data.model.PhoneSimSnapshot
import com.dps.devicescope.data.model.SimCardSpec
import com.dps.devicescope.data.model.StatusLevel
import com.dps.devicescope.ui.components.DiagnosticScaffold
import com.dps.devicescope.ui.components.MetricRow
import com.dps.devicescope.ui.components.PermissionGateCard
import com.dps.devicescope.ui.components.SectionHeader
import com.dps.devicescope.ui.components.StatusBadge
import com.dps.devicescope.ui.components.TelemetryCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PhoneViewModel(private val container: DeviceContainer) : ViewModel() {
    private val _state = MutableStateFlow<PhoneSimSnapshot?>(null)
    val state: StateFlow<PhoneSimSnapshot?> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        viewModelScope.launch {
            _state.value = container.phoneSimRepository.loadSnapshot()
        }
    }

    companion object {
        fun factory(container: DeviceContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PhoneViewModel(container) as T
            }
    }
}

@Composable
fun PhoneScreen(
    container: DeviceContainer,
    onBack: () -> Unit
) {
    val viewModel: PhoneViewModel = viewModel(factory = PhoneViewModel.factory(container))
    val snapshot by viewModel.state.collectAsStateWithLifecycle()

    val permState = rememberMultiPermissionState(
        listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS
        )
    ) {
        viewModel.reload()
    }
    LaunchedEffect(permState.allGranted) {
        viewModel.reload()
    }

    DiagnosticScaffold(
        title = "Phone & SIM",
        subtitle = "Carrier subscriptions, SIM slots & telephony",
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
            if (!permState.allGranted) {
                item {
                    PermissionGateCard(
                        title = "Phone & SIM Permissions",
                        description = "Grant Phone State and Phone Numbers permissions to read active SIM cards, carrier details, eSIM status, and provisioned line numbers.",
                        hasRequestedOnce = permState.hasRequestedOnce,
                        onGrantClick = permState.launchRequest,
                        onOpenSettings = permState.openSettings
                    )
                }
            }

            item { SectionHeader("Telephony Summary") }

            item {
                TelemetryCard {
                    MetricRow("Carrier / Operator", data.operatorName)
                    MetricRow("Country ISO", data.networkCountryIso)
                    MetricRow("SIM State", data.simState)
                    MetricRow("Phone Radio Type", data.phoneType)
                    MetricRow("Data Network", data.dataNetworkType)
                    MetricRow("Active SIMs / Slots", "${data.activeSimCount} / ${data.maxSimSlots}")
                    MetricRow("Line Number", data.deviceNumberFallback, showDivider = false)
                }
            }

            if (data.simCards.isNotEmpty()) {
                item { SectionHeader("Active SIM Subscriptions", trailingText = "${data.simCards.size} SIM") }
                items(data.simCards, key = { it.slotIndex }) { sim ->
                    SimCardItem(sim)
                }
            }

            item { SectionHeader("Android Privacy Identifiers") }

            item {
                TelemetryCard {
                    Text(
                        text = "Hardware Identifiers (IMEI / Serial)",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Starting with Android 10 (API 29), Android restricts non-system apps from reading permanent hardware identifiers like IMEI, MEID, and SIM serial numbers to protect user privacy. You can view your IMEI in Android Settings → About phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SimCardItem(sim: SimCardSpec) {
    TelemetryCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Slot ${sim.slotIndex} · ${sim.displayName}",
                style = MaterialTheme.typography.titleMedium
            )
            StatusBadge(
                text = if (sim.isEmbedded) "eSIM" else "Physical SIM",
                level = StatusLevel.INFO
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        MetricRow("Carrier", sim.carrierName)
        MetricRow("Country ISO", sim.countryIso)
        MetricRow("Phone Number", sim.phoneNumber)
        MetricRow("Data Roaming", if (sim.isRoaming) "Enabled" else "Disabled", showDivider = false)
    }
}
