package com.dps.mytestc

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.*
import android.view.MenuItem
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dps.mytestc.databinding.ActivityCellularBinding
import com.google.android.material.card.MaterialCardView

class CellularActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCellularBinding
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var networkTypeCard: MaterialCardView
    private lateinit var signalStrengthCard: MaterialCardView
    private lateinit var voiceStatusCard: MaterialCardView
    private lateinit var dataStatusCard: MaterialCardView
    private lateinit var meterStatusCard: MaterialCardView
    private lateinit var networkTypeValue: TextView
    private lateinit var signalStrengthValue: TextView
    private lateinit var voiceStatusValue: TextView
    private lateinit var dataStatusValue: TextView
    private lateinit var meterStatusValue: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 1000L // 1 second

    private val phoneStateListener = object : PhoneStateListener() {
        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            super.onSignalStrengthsChanged(signalStrength)
            updateSignalStrength(signalStrength)
        }

        override fun onDataConnectionStateChanged(state: Int) {
            super.onDataConnectionStateChanged(state)
            updateDataStatus(state)
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCellularBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        initializeViews()
        setupTelephonyManager()
        startPeriodicUpdates()
    }

    private fun initializeViews() {
        networkTypeCard = binding.networkTypeCard
        signalStrengthCard = binding.signalStrengthCard
        voiceStatusCard = binding.voiceStatusCard
        dataStatusCard = binding.dataStatusCard
        meterStatusCard = binding.meterStatusCard

        networkTypeValue = binding.networkTypeValue
        signalStrengthValue = binding.signalStrengthValue
        voiceStatusValue = binding.voiceStatusValue
        dataStatusValue = binding.dataStatusValue
        meterStatusValue = binding.meterStatusValue
    }

    private fun setupTelephonyManager() {
        if (checkPermission()) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_DATA_CONNECTION_STATE)
            updateNetworkInfo()
        } else {
            requestPermission()
        }
    }

    private fun startPeriodicUpdates() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (checkPermission()) {
                    updateNetworkInfo()
                }
                handler.postDelayed(this, updateInterval)
            }
        }, updateInterval)
    }

    private fun updateNetworkInfo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
            // Network Type
            val networkType = when (telephonyManager.dataNetworkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
                else -> "Unknown"
            }
            animateTextChange(networkTypeValue, networkType)
            animateCard(networkTypeCard)

            // Voice Status
            val voiceState = when (telephonyManager.callState) {
                TelephonyManager.CALL_STATE_IDLE -> "Available"
                TelephonyManager.CALL_STATE_RINGING -> "Ringing"
                TelephonyManager.CALL_STATE_OFFHOOK -> "In Call"
                else -> "Unknown"
            }
            animateTextChange(voiceStatusValue, voiceState)
            animateCard(voiceStatusCard)

            // Meter Status
            val meterState = if (telephonyManager.isNetworkRoaming) "Roaming" else "Home"
            animateTextChange(meterStatusValue, meterState)
            animateCard(meterStatusCard)
        }
    }

    private fun updateSignalStrength(signalStrength: SignalStrength) {
        val strength = when {
            signalStrength.level >= 4 -> "Excellent"
            signalStrength.level >= 3 -> "Good"
            signalStrength.level >= 2 -> "Fair"
            signalStrength.level >= 1 -> "Poor"
            else -> "No Signal"
        }
        animateTextChange(signalStrengthValue, strength)
        animateSignalStrength(signalStrengthCard, signalStrength.level)
    }

    private fun updateDataStatus(state: Int) {
        val status = when (state) {
            TelephonyManager.DATA_CONNECTED -> "Connected"
            TelephonyManager.DATA_CONNECTING -> "Connecting"
            TelephonyManager.DATA_DISCONNECTED -> "Disconnected"
            else -> "Unknown"
        }
        animateTextChange(dataStatusValue, status)
        animateDataStatus(dataStatusCard, state)
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

    private fun animateCard(card: MaterialCardView) {
        card.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(150)
            .withEndAction {
                card.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(150)
                    .start()
            }
            .start()
    }

    private fun animateSignalStrength(card: MaterialCardView, level: Int) {
        val colorFrom = ContextCompat.getColor(this, R.color.cardBackground)
        val colorTo = when {
            level >= 4 -> Color.parseColor("#4CAF50") // Green
            level >= 3 -> Color.parseColor("#8BC34A") // Light Green
            level >= 2 -> Color.parseColor("#FFC107") // Yellow
            level >= 1 -> Color.parseColor("#FF9800") // Orange
            else -> Color.parseColor("#F44336") // Red
        }

        ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
            duration = 300
            addUpdateListener { animator ->
                card.setCardBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun animateDataStatus(card: MaterialCardView, state: Int) {
        val colorFrom = ContextCompat.getColor(this, R.color.cardBackground)
        val colorTo = when (state) {
            TelephonyManager.DATA_CONNECTED -> Color.parseColor("#4CAF50") // Green
            TelephonyManager.DATA_CONNECTING -> Color.parseColor("#FFC107") // Yellow
            else -> Color.parseColor("#F44336") // Red
        }

        ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo).apply {
            duration = 300
            addUpdateListener { animator ->
                card.setCardBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun checkPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_PHONE_STATE),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupTelephonyManager()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (checkPermission()) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 