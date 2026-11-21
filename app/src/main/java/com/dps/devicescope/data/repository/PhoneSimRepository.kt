package com.dps.devicescope.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import com.dps.devicescope.core.hasPermission
import com.dps.devicescope.data.model.PhoneSimSnapshot
import com.dps.devicescope.data.model.SimCardSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhoneSimRepository(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val subscriptionManager =
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

    suspend fun loadSnapshot(): PhoneSimSnapshot = withContext(Dispatchers.IO) {
        captureSnapshot()
    }

    @SuppressLint("MissingPermission")
    fun captureSnapshot(): PhoneSimSnapshot {
        val hasPhoneState = context.hasPermission(Manifest.permission.READ_PHONE_STATE)
        val hasPhoneNumbers = context.hasPermission(Manifest.permission.READ_PHONE_NUMBERS)

        val phoneType = when (runCatching { telephonyManager.phoneType }.getOrDefault(TelephonyManager.PHONE_TYPE_NONE)) {
            TelephonyManager.PHONE_TYPE_GSM -> "GSM"
            TelephonyManager.PHONE_TYPE_CDMA -> "CDMA"
            TelephonyManager.PHONE_TYPE_SIP -> "SIP"
            else -> "None"
        }

        val simState = when (runCatching { telephonyManager.simState }.getOrDefault(TelephonyManager.SIM_STATE_UNKNOWN)) {
            TelephonyManager.SIM_STATE_READY -> "Ready"
            TelephonyManager.SIM_STATE_ABSENT -> "No SIM Inserted"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN Required"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK Required"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
            TelephonyManager.SIM_STATE_NOT_READY -> "Not Ready"
            TelephonyManager.SIM_STATE_PERM_DISABLED -> "Permanently Disabled"
            TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "Card I/O Error"
            else -> "Unknown"
        }

        val operatorName = runCatching { telephonyManager.networkOperatorName }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "Not Available"
        val countryIso = runCatching { telephonyManager.networkCountryIso }
            .getOrNull()?.takeIf { it.isNotBlank() }?.uppercase() ?: "Unknown"

        val maxSimSlots = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { telephonyManager.activeModemCount }.getOrDefault(1)
        } else {
            @Suppress("DEPRECATION")
            runCatching { telephonyManager.phoneCount }.getOrDefault(1)
        }

        if (!hasPhoneState) {
            return PhoneSimSnapshot(
                hasPermission = false,
                phoneType = phoneType,
                simState = simState,
                operatorName = operatorName,
                networkCountryIso = countryIso,
                dataNetworkType = "Permission Required",
                activeSimCount = 0,
                maxSimSlots = maxSimSlots,
                simCards = emptyList(),
                deviceNumberFallback = "Permission Required"
            )
        }

        val dataNetworkType = CellularRepository.formatDataNetworkType(
            runCatching { telephonyManager.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        )

        val activeSubs = runCatching { subscriptionManager.activeSubscriptionInfoList }.getOrNull().orEmpty()
        val simCards = activeSubs.map { sub ->
            val number = if (hasPhoneNumbers) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runCatching { subscriptionManager.getPhoneNumber(sub.subscriptionId) }
                        .getOrNull()?.takeIf { it.isNotBlank() } ?: "Not Provisioned"
                } else {
                    @Suppress("DEPRECATION")
                    sub.number?.takeIf { it.isNotBlank() } ?: "Not Provisioned"
                }
            } else {
                "Phone numbers permission needed"
            }

            SimCardSpec(
                slotIndex = sub.simSlotIndex + 1,
                displayName = sub.displayName?.toString()?.ifBlank { "SIM ${sub.simSlotIndex + 1}" }
                    ?: "SIM ${sub.simSlotIndex + 1}",
                carrierName = sub.carrierName?.toString()?.ifBlank { "Unknown Carrier" }
                    ?: "Unknown Carrier",
                countryIso = sub.countryIso?.uppercase()?.ifBlank { "N/A" } ?: "N/A",
                phoneNumber = number,
                isEmbedded = sub.isEmbedded,
                isRoaming = sub.dataRoaming == SubscriptionManager.DATA_ROAMING_ENABLE
            )
        }

        val deviceNumberFallback = if (hasPhoneNumbers) {
            @Suppress("DEPRECATION")
            runCatching { telephonyManager.line1Number }
                .getOrNull()?.takeIf { it.isNotBlank() }
                ?: simCards.firstOrNull()?.phoneNumber
                ?: "Not Provisioned by Carrier"
        } else {
            "Phone numbers permission needed"
        }

        return PhoneSimSnapshot(
            hasPermission = true,
            phoneType = phoneType,
            simState = simState,
            operatorName = operatorName,
            networkCountryIso = countryIso,
            dataNetworkType = dataNetworkType,
            activeSimCount = simCards.size,
            maxSimSlots = maxSimSlots,
            simCards = simCards,
            deviceNumberFallback = deviceNumberFallback
        )
    }
}
