package com.dps.mytestc

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.fingerprint.FingerprintManager
import android.os.Bundle
import android.os.CancellationSignal
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dps.mytestc.databinding.ActivityFingerprintBinding
import com.google.android.material.card.MaterialCardView

class FingerprintActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFingerprintBinding
    private var fingerprintManager: FingerprintManager? = null
    private var cancellationSignal: CancellationSignal? = null
    private lateinit var fingerprintVisualView: FingerprintVisualView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFingerprintBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        fingerprintVisualView = binding.fingerprintVisualView
        initializeFingerprintManager()
        checkFingerprintStatus()
        startFingerprintListener()
    }

    private fun initializeFingerprintManager() {
        fingerprintManager = getSystemService(FingerprintManager::class.java)
    }

    private fun startFingerprintListener() {
        if (fingerprintManager?.isHardwareDetected == true && 
            fingerprintManager?.hasEnrolledFingerprints() == true) {
            
            cancellationSignal = CancellationSignal()
            fingerprintManager?.authenticate(
                null,
                cancellationSignal,
                0,
                object : FingerprintManager.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                        updateVisualization(false)
                    }

                    override fun onAuthenticationHelp(helpCode: Int, helpString: CharSequence?) {
                        updateVisualization(false)
                    }

                    override fun onAuthenticationSucceeded(result: FingerprintManager.AuthenticationResult?) {
                        updateVisualization(true)
                    }

                    override fun onAuthenticationFailed() {
                        updateVisualization(false)
                    }
                },
                null
            )
        }
    }

    private fun updateVisualization(success: Boolean) {
        if (success) {
            fingerprintVisualView.startAnimation()
        } else {
            fingerprintVisualView.stopAnimation()
        }
    }

    private fun checkFingerprintStatus() {
        val statusCard = binding.fingerprintStatusCard
        val statusValue = binding.fingerprintStatusValue
        val enrolledValue = binding.enrolledFingerprintsValue
        val hardwareValue = binding.hardwareDetailsValue

        if (fingerprintManager == null) {
            updateStatus(statusValue, statusCard, "Not Available", false)
            enrolledValue.text = "Fingerprint hardware not available"
            hardwareValue.text = "Hardware not present"
            return
        }

        val isHardwareDetected = fingerprintManager?.isHardwareDetected ?: false
        val hasEnrolledFingerprints = fingerprintManager?.hasEnrolledFingerprints() ?: false

        when {
            !isHardwareDetected -> {
                updateStatus(statusValue, statusCard, "Hardware Not Present", false)
                enrolledValue.text = "No fingerprint hardware"
                hardwareValue.text = "Hardware not detected"
            }
            !hasEnrolledFingerprints -> {
                updateStatus(statusValue, statusCard, "No Fingerprints Enrolled", false)
                enrolledValue.text = "No fingerprints enrolled"
                hardwareValue.text = "Hardware present but no enrolled fingerprints"
            }
            else -> {
                updateStatus(statusValue, statusCard, "Available", true)
                enrolledValue.text = "Fingerprints enrolled"
                hardwareValue.text = "Hardware present and functioning"
            }
        }
    }

    private fun updateStatus(textView: TextView, card: MaterialCardView, status: String, isActive: Boolean) {
        animateTextChange(textView, status)
        animateCard(card, isActive)
    }

    private fun animateTextChange(textView: TextView, newText: String) {
        textView.animate()
            .alpha(0f)
            .setDuration(150)
            .withEndAction {
                textView.text = newText
                textView.animate()
                    .alpha(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun animateCard(card: MaterialCardView, isActive: Boolean) {
        val colorFrom = ContextCompat.getColor(this, R.color.cardBackground)
        val colorTo = if (isActive) {
            Color.parseColor("#4CAF50") // Green
        } else {
            Color.parseColor("#F44336") // Red
        }

        ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
            duration = 300
            addUpdateListener { animator ->
                card.setCardBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancellationSignal?.cancel()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 