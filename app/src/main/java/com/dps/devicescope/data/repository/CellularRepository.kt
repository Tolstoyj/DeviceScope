package com.dps.devicescope.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import com.dps.devicescope.core.Formatters
import com.dps.devicescope.core.hasPermission
import com.dps.devicescope.data.model.CellularSnapshot
import com.dps.devicescope.data.model.StatusLevel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class CellularRepository(private val context: Context) {

    private val telephonyManager =
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @SuppressLint("MissingPermission")
    fun observeCellular(): Flow<CellularSnapshot> = callbackFlow {
        if (!context.hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            trySend(permissionMissingSnapshot())
            awaitClose { }
            return@callbackFlow
        }

        var latestSignal: SignalStrength? = runCatching { telephonyManager.signalStrength }.getOrNull()
        var latestDataState: Int = runCatching { telephonyManager.dataState }.getOrDefault(TelephonyManager.DATA_DISCONNECTED)
        var latestCallState: Int = TelephonyManager.CALL_STATE_IDLE
        var latestDisplayOverride = "Standard"

        fun emitState() {
            trySend(buildSnapshot(latestSignal, latestDataState, latestCallState, latestDisplayOverride))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(),
                TelephonyCallback.SignalStrengthsListener,
                TelephonyCallback.DataConnectionStateListener,
                TelephonyCallback.CallStateListener,
                TelephonyCallback.DisplayInfoListener {

                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    latestSignal = signalStrength
                    emitState()
                }

                override fun onDataConnectionStateChanged(state: Int, networkType: Int) {
                    latestDataState = state
                    emitState()
                }

                override fun onCallStateChanged(state: Int) {
                    latestCallState = state
                    emitState()
                }

                override fun onDisplayInfoChanged(telephonyDisplayInfo: TelephonyDisplayInfo) {
                    latestDisplayOverride = formatOverrideNetworkType(telephonyDisplayInfo.overrideNetworkType)
                    emitState()
                }
            }
            runCatching {
                telephonyManager.registerTelephonyCallback(context.mainExecutor, callback)
            }
            emitState()
            awaitClose {
                runCatching { telephonyManager.unregisterTelephonyCallback(callback) }
            }
        } else {
            @Suppress("DEPRECATION")
            val listener = object : android.telephony.PhoneStateListener() {
                override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                    latestSignal = signalStrength
                    emitState()
                }

                override fun onDataConnectionStateChanged(state: Int) {
                    latestDataState = state
                    emitState()
                }

                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    latestCallState = state
                    emitState()
                }
            }
            @Suppress("DEPRECATION")
            runCatching {
                telephonyManager.listen(
                    listener,
                    android.telephony.PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or
                        android.telephony.PhoneStateListener.LISTEN_DATA_CONNECTION_STATE or
                        android.telephony.PhoneStateListener.LISTEN_CALL_STATE
                )
            }
            emitState()
            awaitClose {
                @Suppress("DEPRECATION")
                runCatching { telephonyManager.listen(listener, android.telephony.PhoneStateListener.LISTEN_NONE) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun captureSnapshot(): CellularSnapshot {
        if (!context.hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            return permissionMissingSnapshot()
        }
        val signal = runCatching { telephonyManager.signalStrength }.getOrNull()
        val dataState = runCatching { telephonyManager.dataState }.getOrDefault(TelephonyManager.DATA_DISCONNECTED)
        @Suppress("DEPRECATION")
        val callState = runCatching { telephonyManager.callState }.getOrDefault(TelephonyManager.CALL_STATE_IDLE)
        return buildSnapshot(signal, dataState, callState, "Standard")
    }

    @SuppressLint("MissingPermission")
    private fun buildSnapshot(
        signalStrength: SignalStrength?,
        dataState: Int,
        callState: Int,
        displayOverride: String
    ): CellularSnapshot {
        val level = signalStrength?.level ?: 0
        val (quality, severity) = Formatters.classifySignalLevel(level)
        val firstCell = signalStrength?.cellSignalStrengths?.firstOrNull()
        val dbm = firstCell?.dbm?.takeIf { it in -150..-30 }
        val asu = firstCell?.asuLevel?.takeIf { it in 0..99 }

        val networkType = formatDataNetworkType(
            runCatching { telephonyManager.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
        )
        val operatorName = runCatching { telephonyManager.networkOperatorName }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: "No Carrier"

        val voiceText = when (callState) {
            TelephonyManager.CALL_STATE_IDLE -> "Idle / Ready"
            TelephonyManager.CALL_STATE_RINGING -> "Ringing"
            TelephonyManager.CALL_STATE_OFFHOOK -> "In Call"
            else -> "Unknown"
        }

        val dataText = when (dataState) {
            TelephonyManager.DATA_CONNECTED -> "Connected"
            TelephonyManager.DATA_CONNECTING -> "Connecting"
            TelephonyManager.DATA_SUSPENDED -> "Suspended"
            TelephonyManager.DATA_DISCONNECTED -> "Disconnected"
            else -> "Unknown"
        }

        val activeCaps = connectivityManager.activeNetwork?.let {
            connectivityManager.getNetworkCapabilities(it)
        }
        val isMetered = activeCaps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false

        return CellularSnapshot(
            hasPermission = true,
            operatorName = operatorName,
            networkType = networkType,
            displayOverride = displayOverride,
            signalLevel = level,
            signalQuality = quality,
            signalSeverity = severity,
            signalDbm = dbm,
            signalAsu = asu,
            voiceState = voiceText,
            dataState = dataText,
            isRoaming = runCatching { telephonyManager.isNetworkRoaming }.getOrDefault(false),
            isCellularMetered = isMetered
        )
    }

    private fun permissionMissingSnapshot() = CellularSnapshot(
        hasPermission = false,
        operatorName = "Permission Required",
        networkType = "Permission Required",
        displayOverride = "N/A",
        signalLevel = 0,
        signalQuality = "Permission Required",
        signalSeverity = StatusLevel.NEUTRAL,
        signalDbm = null,
        signalAsu = null,
        voiceState = "Permission Required",
        dataState = "Permission Required",
        isRoaming = false,
        isCellularMetered = false
    )

    @RequiresApi(Build.VERSION_CODES.S)
    private fun formatOverrideNetworkType(overrideType: Int): String = when (overrideType) {
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_NSA -> "5G NSA"
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_NR_ADVANCED -> "5G+ / mmWave"
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_CA -> "4G+ (LTE-CA)"
        TelephonyDisplayInfo.OVERRIDE_NETWORK_TYPE_LTE_ADVANCED_PRO -> "4G LTE Advanced Pro"
        else -> "Standard"
    }

    companion object {
        fun formatDataNetworkType(type: Int): String = when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G NR"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G HSPA+"
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA -> "3G HSPA"
            TelephonyManager.NETWORK_TYPE_UMTS,
            TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A,
            TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_CDMA,
            TelephonyManager.NETWORK_TYPE_1xRTT -> "2G"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "Wi-Fi Calling (IWLAN)"
            else -> "Unknown"
        }
    }
}
