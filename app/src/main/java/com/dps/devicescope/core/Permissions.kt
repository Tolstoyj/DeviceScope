package com.dps.devicescope.core

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.hasAllPermissions(permissions: List<String>): Boolean =
    permissions.all { hasPermission(it) }

fun Context.hasAnyPermission(permissions: List<String>): Boolean =
    permissions.any { hasPermission(it) }

fun Context.openAppSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(intent)
}

class MultiPermissionState(
    val permissions: List<String>,
    val allGranted: Boolean,
    val anyGranted: Boolean,
    val hasRequestedOnce: Boolean,
    val launchRequest: () -> Unit,
    val openSettings: () -> Unit
)

@Composable
fun rememberMultiPermissionState(
    permissions: List<String>,
    onResult: (Map<String, Boolean>) -> Unit = {}
): MultiPermissionState {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var allGranted by remember(permissions) { mutableStateOf(context.hasAllPermissions(permissions)) }
    var anyGranted by remember(permissions) { mutableStateOf(context.hasAnyPermission(permissions)) }
    var hasRequestedOnce by remember(permissions) { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasRequestedOnce = true
        allGranted = context.hasAllPermissions(permissions)
        anyGranted = context.hasAnyPermission(permissions)
        onResult(result)
    }

    // Refresh permission state when returning from Android App Settings
    DisposableEffect(lifecycleOwner, permissions) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allGranted = context.hasAllPermissions(permissions)
                anyGranted = context.hasAnyPermission(permissions)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    return MultiPermissionState(
        permissions = permissions,
        allGranted = allGranted,
        anyGranted = anyGranted,
        hasRequestedOnce = hasRequestedOnce,
        launchRequest = { launcher.launch(permissions.toTypedArray()) },
        openSettings = { context.openAppSettings() }
    )
}
