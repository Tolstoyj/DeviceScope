package com.dps.devicescope.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.usb.UsbManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.nfc.NfcAdapter
import android.os.Build
import com.dps.devicescope.core.Formatters
import com.dps.devicescope.core.hasPermission
import com.dps.devicescope.data.model.ConnectivitySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.Inet6Address

class ConnectivityRepository(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothManager =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val usbManager =
        context.getSystemService(Context.USB_SERVICE) as? UsbManager

    fun observeConnectivity(): Flow<ConnectivitySnapshot> = callbackFlow {
        fun emitNow() {
            trySend(captureSnapshot())
        }

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = emitNow()
            override fun onLost(network: Network) = emitNow()
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) = emitNow()
        }

        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
        emitNow()

        val pollJob = launch {
            while (isActive) {
                delay(3000L)
                emitNow()
            }
        }

        awaitClose {
            pollJob.cancel()
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        }
    }

    @SuppressLint("MissingPermission")
    fun captureSnapshot(): ConnectivitySnapshot {
        val activeNetwork = connectivityManager.activeNetwork
        val caps = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        val linkProps = activeNetwork?.let { connectivityManager.getLinkProperties(it) }

        val activeTransport = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        }

        val isValidated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val isMetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        val isVpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val downstreamKbps = caps?.linkDownstreamBandwidthKbps ?: 0
        val upstreamKbps = caps?.linkUpstreamBandwidthKbps ?: 0

        val ipv4 = linkProps?.linkAddresses
            ?.mapNotNull { it.address as? Inet4Address }
            ?.mapNotNull { it.hostAddress }
            ?: emptyList()
        val ipv6 = linkProps?.linkAddresses
            ?.mapNotNull { it.address as? Inet6Address }
            ?.mapNotNull { it.hostAddress?.substringBefore("%") }
            ?: emptyList()
        val dns = linkProps?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()

        // Wi-Fi info
        val wifiEnabled = runCatching { wifiManager?.isWifiEnabled == true }.getOrDefault(false)
        val wifiInfo = extractWifiInfo(caps)
        val freqMhz = wifiInfo?.frequency ?: 0
        val linkSpeedMbps = wifiInfo?.linkSpeed?.takeIf { it > 0 } ?: 0
        val rssiDbm = wifiInfo?.rssi?.takeIf { it != -127 && it != 0 }

        val hasLocation = context.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val rawSsid = wifiInfo?.ssid?.trim('"')
        val wifiSsid = when {
            !wifiEnabled -> "Wi-Fi Off"
            activeTransport != "Wi-Fi" && wifiInfo == null -> "Not Connected"
            !hasLocation && (rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>") ->
                "Location permission required"
            rawSsid.isNullOrBlank() || rawSsid == "<unknown ssid>" -> "Hidden / Unavailable"
            else -> rawSsid
        }

        val supports5Ghz = runCatching { wifiManager?.is5GHzBandSupported == true }.getOrDefault(false)
        val supports6Ghz = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { wifiManager?.is6GHzBandSupported == true }.getOrDefault(false)
        } else {
            false
        }
        val supportsWifiDirect = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)

        // Bluetooth
        val adapter = bluetoothManager?.adapter
        val btSupported = adapter != null
        val btEnabled = runCatching { adapter?.isEnabled == true }.getOrDefault(false)
        val supportsBle = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val btConnectGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            true
        }

        val btName = if (btSupported && btConnectGranted) {
            runCatching { adapter?.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Unnamed"
        } else if (!btSupported) {
            "Not Supported"
        } else {
            "Permission required"
        }

        val pairedDevices = if (btSupported && btEnabled && btConnectGranted) {
            runCatching {
                adapter?.bondedDevices?.map { it.name ?: it.address }?.sorted() ?: emptyList()
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        // NFC & USB
        val nfcAdapter = runCatching { NfcAdapter.getDefaultAdapter(context) }.getOrNull()
        val nfcSupported = nfcAdapter != null
        val nfcEnabled = runCatching { nfcAdapter?.isEnabled == true }.getOrDefault(false)

        val usbHost = context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST)
        val usbAccessory = context.packageManager.hasSystemFeature(PackageManager.FEATURE_USB_ACCESSORY)
        val usbDevicesCount = runCatching { usbManager?.deviceList?.size ?: 0 }.getOrDefault(0)

        return ConnectivitySnapshot(
            activeTransport = activeTransport,
            isInternetValidated = isValidated,
            isMetered = isMetered,
            isVpnActive = isVpn,
            downstreamKbps = downstreamKbps,
            upstreamKbps = upstreamKbps,
            ipv4Addresses = ipv4,
            ipv6Addresses = ipv6,
            dnsServers = dns,
            wifiEnabled = wifiEnabled,
            wifiSsid = wifiSsid,
            wifiBand = Formatters.classifyWifiBand(freqMhz),
            wifiFrequencyMhz = freqMhz,
            wifiLinkSpeedMbps = linkSpeedMbps,
            wifiRssiDbm = rssiDbm,
            supports5Ghz = supports5Ghz,
            supports6Ghz = supports6Ghz,
            supportsWifiDirect = supportsWifiDirect,
            bluetoothSupported = btSupported,
            bluetoothEnabled = btEnabled,
            bluetoothName = btName,
            supportsBle = supportsBle,
            bluetoothPermissionMissing = btSupported && !btConnectGranted,
            pairedDevices = pairedDevices,
            nfcSupported = nfcSupported,
            nfcEnabled = nfcEnabled,
            usbHostSupported = usbHost,
            usbAccessorySupported = usbAccessory,
            connectedUsbDeviceCount = usbDevicesCount
        )
    }

    @Suppress("DEPRECATION")
    private fun extractWifiInfo(caps: NetworkCapabilities?): WifiInfo? {
        val fromCaps = caps?.transportInfo as? WifiInfo
        if (fromCaps != null) return fromCaps
        return runCatching { wifiManager?.connectionInfo }.getOrNull()
    }
}
