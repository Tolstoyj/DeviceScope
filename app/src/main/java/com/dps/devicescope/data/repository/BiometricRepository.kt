package com.dps.devicescope.data.repository

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import com.dps.devicescope.data.model.BiometricCapabilityRow
import com.dps.devicescope.data.model.BiometricSnapshot

class BiometricRepository(private val context: Context) {

    private val biometricManager = BiometricManager.from(context)

    fun captureSnapshot(): BiometricSnapshot {
        val strong = evaluate(
            label = "Strong Biometric (Class 3)",
            authenticator = Authenticators.BIOMETRIC_STRONG
        )
        val weak = evaluate(
            label = "Weak Biometric (Class 2)",
            authenticator = Authenticators.BIOMETRIC_WEAK
        )
        val credential = evaluate(
            label = "Device Credential (PIN / Pattern)",
            authenticator = Authenticators.DEVICE_CREDENTIAL
        )

        val overallReady = strong.isReady || weak.isReady
        val overallStatus = when {
            strong.isReady -> "Strong Biometric Ready"
            weak.isReady -> "Weak Biometric Ready"
            strong.statusText.contains("Enrolled", ignoreCase = true) ||
                weak.statusText.contains("Enrolled", ignoreCase = true) -> "Not Enrolled"
            else -> "Not Available"
        }

        val pm = context.packageManager
        return BiometricSnapshot(
            overallReady = overallReady,
            overallStatus = overallStatus,
            strongBiometric = strong,
            weakBiometric = weak,
            deviceCredential = credential,
            hasFingerprintHardware = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT),
            hasFaceHardware = pm.hasSystemFeature(PackageManager.FEATURE_FACE),
            hasIrisHardware = pm.hasSystemFeature(PackageManager.FEATURE_IRIS)
        )
    }

    private fun evaluate(label: String, authenticator: Int): BiometricCapabilityRow {
        val code = runCatching { biometricManager.canAuthenticate(authenticator) }
            .getOrDefault(BiometricManager.BIOMETRIC_STATUS_UNKNOWN)
        val (text, ready) = when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> "Ready & Enrolled" to true
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Hardware Not Present" to false
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Hardware Unavailable" to false
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "None Enrolled" to false
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "Security Update Required" to false
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "Unsupported" to false
            else -> "Unknown" to false
        }
        return BiometricCapabilityRow(label = label, statusText = text, isReady = ready)
    }
}
