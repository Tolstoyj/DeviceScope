package com.dps.devicescope.ui.screens.biometric

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dps.devicescope.core.DeviceContainer
import com.dps.devicescope.data.model.BiometricSnapshot
import com.dps.devicescope.data.model.StatusLevel
import com.dps.devicescope.ui.components.DiagnosticScaffold
import com.dps.devicescope.ui.components.MetricRow
import com.dps.devicescope.ui.components.SectionHeader
import com.dps.devicescope.ui.components.StatusBadge
import com.dps.devicescope.ui.components.TelemetryCard
import com.dps.devicescope.ui.theme.LocalTelemetryColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private enum class BiometricTestState(val label: String, val level: StatusLevel) {
    IDLE("Ready to Test", StatusLevel.INFO),
    VERIFYING("Awaiting Sensor...", StatusLevel.WARNING),
    VERIFIED("Authentication Succeeded", StatusLevel.GOOD),
    FAILED("Authentication Failed / Cancelled", StatusLevel.CRITICAL)
}

class BiometricViewModel(private val container: DeviceContainer) : ViewModel() {
    private val _state = MutableStateFlow(container.biometricRepository.captureSnapshot())
    val state: StateFlow<BiometricSnapshot> = _state.asStateFlow()

    fun refresh() {
        _state.value = container.biometricRepository.captureSnapshot()
    }

    companion object {
        fun factory(container: DeviceContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    BiometricViewModel(container) as T
            }
    }
}

@Composable
fun BiometricScreen(
    container: DeviceContainer,
    onBack: () -> Unit
) {
    val viewModel: BiometricViewModel = viewModel(factory = BiometricViewModel.factory(container))
    val snapshot by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalTelemetryColors.current
    val context = LocalContext.current

    var testState by remember { mutableStateOf(BiometricTestState.IDLE) }
    var testMessage by remember { mutableStateOf("Tap the button below to trigger the Android BiometricPrompt.") }

    fun launchBiometricTest() {
        val activity = context.findFragmentActivity()
        if (activity == null) {
            testState = BiometricTestState.FAILED
            testMessage = "Host activity unavailable for BiometricPrompt."
            return
        }
        testState = BiometricTestState.VERIFYING
        testMessage = "Touch the biometric sensor or present your face..."

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    testState = BiometricTestState.VERIFIED
                    val typeLabel = when (result.authenticationType) {
                        BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC -> "Biometric Sensor"
                        BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL -> "Device Credential"
                        else -> "Verified"
                    }
                    testMessage = "Verified cleanly via $typeLabel."
                    viewModel.refresh()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    testState = BiometricTestState.FAILED
                    testMessage = errString.toString()
                }

                override fun onAuthenticationFailed() {
                    testState = BiometricTestState.FAILED
                    testMessage = "Biometric did not match. Try again."
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("DeviceScope Biometric Test")
            .setSubtitle("Verify your device biometric hardware")
            .setAllowedAuthenticators(Authenticators.BIOMETRIC_STRONG or Authenticators.BIOMETRIC_WEAK)
            .setNegativeButtonText("Cancel")
            .build()

        runCatching { prompt.authenticate(promptInfo) }.onFailure { err ->
            testState = BiometricTestState.FAILED
            testMessage = err.localizedMessage ?: "Unable to launch biometric prompt."
        }
    }

    DiagnosticScaffold(
        title = "Biometrics",
        subtitle = "BiometricManager & live sensor verification",
        onBack = onBack
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                val activeColor = colors.forLevel(
                    if (testState != BiometricTestState.IDLE) testState.level
                    else if (snapshot.overallReady) StatusLevel.GOOD else StatusLevel.WARNING
                )
                TelemetryCard(accentColor = activeColor) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Biometric Sensor Test",
                            style = MaterialTheme.typography.titleMedium
                        )
                        StatusBadge(
                            text = if (testState != BiometricTestState.IDLE) testState.label else snapshot.overallStatus,
                            level = if (testState != BiometricTestState.IDLE) testState.level
                            else if (snapshot.overallReady) StatusLevel.GOOD else StatusLevel.WARNING
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    BiometricPulseVisualizer(
                        color = activeColor,
                        isPulsing = testState == BiometricTestState.VERIFYING || testState == BiometricTestState.VERIFIED,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = testMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { launchBiometricTest() },
                        enabled = snapshot.overallReady,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Fingerprint,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (snapshot.overallReady) "Test Biometric Authentication" else "No Enrolled Biometrics")
                    }
                }
            }

            item { SectionHeader("Authenticator Readiness") }

            item {
                TelemetryCard {
                    MetricRow(
                        snapshot.strongBiometric.label,
                        snapshot.strongBiometric.statusText,
                        valueColor = if (snapshot.strongBiometric.isReady) colors.good else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MetricRow(
                        snapshot.weakBiometric.label,
                        snapshot.weakBiometric.statusText,
                        valueColor = if (snapshot.weakBiometric.isReady) colors.good else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MetricRow(
                        snapshot.deviceCredential.label,
                        snapshot.deviceCredential.statusText,
                        valueColor = if (snapshot.deviceCredential.isReady) colors.good else MaterialTheme.colorScheme.onSurfaceVariant,
                        showDivider = false
                    )
                }
            }

            item { SectionHeader("Hardware Feature Flags") }

            item {
                TelemetryCard {
                    MetricRow("Fingerprint Sensor", if (snapshot.hasFingerprintHardware) "Present" else "Not Detected")
                    MetricRow("Face Hardware", if (snapshot.hasFaceHardware) "Present" else "Not Detected")
                    MetricRow("Iris Hardware", if (snapshot.hasIrisHardware) "Present" else "Not Detected", showDivider = false)
                }
            }
        }
    }
}

@Composable
private fun BiometricPulseVisualizer(
    color: Color,
    isPulsing: Boolean,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "bio_pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ripple"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseRadius = size.minDimension * 0.28f

        if (isPulsing) {
            val r1 = baseRadius + progress * (size.minDimension * 0.22f)
            drawCircle(
                color = color.copy(alpha = (1f - progress) * 0.45f),
                radius = r1,
                center = Offset(cx, cy),
                style = Stroke(width = 4f)
            )
            val p2 = (progress + 0.5f) % 1f
            val r2 = baseRadius + p2 * (size.minDimension * 0.22f)
            drawCircle(
                color = color.copy(alpha = (1f - p2) * 0.35f),
                radius = r2,
                center = Offset(cx, cy),
                style = Stroke(width = 3f)
            )
        }

        // Stylized concentric fingerprint ridge arcs
        for (ring in 1..5) {
            val w = baseRadius * (0.35f + ring * 0.16f)
            val h = w * 1.28f
            drawArc(
                color = color.copy(alpha = 0.35f + ring * 0.12f),
                startAngle = -140f + ring * 8f,
                sweepAngle = 250f - ring * 10f,
                useCenter = false,
                topLeft = Offset(cx - w, cy - h),
                size = Size(w * 2f, h * 2f),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
        }
    }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
