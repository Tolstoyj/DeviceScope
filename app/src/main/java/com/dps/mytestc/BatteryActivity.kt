package com.dps.mytestc

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.dps.mytestc.databinding.ActivityBatteryBinding
import com.google.android.material.card.MaterialCardView

class BatteryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBatteryBinding
    private lateinit var batteryLevelCard: MaterialCardView
    private lateinit var chargerConnectionCard: MaterialCardView
    private lateinit var batteryHealthCard: MaterialCardView
    private lateinit var batteryStatusCard: MaterialCardView
    private lateinit var batteryLevelValue: TextView
    private lateinit var chargerConnectionValue: TextView
    private lateinit var batteryHealthValue: TextView
    private lateinit var batteryStatusValue: TextView

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_BATTERY_CHANGED) {
                updateBatteryInfo(intent)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatteryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        initializeViews()
        registerBatteryReceiver()
    }

    private fun initializeViews() {
        batteryLevelCard = binding.batteryLevelCard
        chargerConnectionCard = binding.chargerConnectionCard
        batteryHealthCard = binding.batteryHealthCard
        batteryStatusCard = binding.batteryStatusCard

        batteryLevelValue = binding.batteryLevelValue
        chargerConnectionValue = binding.chargerConnectionValue
        batteryHealthValue = binding.batteryHealthValue
        batteryStatusValue = binding.batteryStatusValue
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun updateBatteryInfo(intent: Intent) {
        // Battery Level
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = level * 100 / scale.toFloat()
        animateTextChange(batteryLevelValue, "${batteryPct.toInt()}%")
        animateBatteryLevel(batteryLevelCard, batteryPct)

        // Charger Connection
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                        status == BatteryManager.BATTERY_STATUS_FULL
        val chargeType = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Not charging"
        }
        animateTextChange(chargerConnectionValue, chargeType)
        animateChargerStatus(chargerConnectionCard, isCharging)

        // Battery Health
        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheated"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failed"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }
        animateTextChange(batteryHealthValue, health)
        animateBatteryHealth(batteryHealthCard, health)

        // Battery Status
        val batteryStatus = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        animateTextChange(batteryStatusValue, batteryStatus)
        animateBatteryStatus(batteryStatusCard, status)
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

    private fun animateBatteryLevel(card: MaterialCardView, level: Float) {
        val colorFrom = ContextCompat.getColor(this, R.color.cardBackground)
        val colorTo = when {
            level >= 75f -> Color.parseColor("#4CAF50") // Green
            level >= 50f -> Color.parseColor("#8BC34A") // Light Green
            level >= 25f -> Color.parseColor("#FFC107") // Yellow
            level >= 15f -> Color.parseColor("#FF9800") // Orange
            else -> Color.parseColor("#F44336") // Red
        }

        animateCardColor(card, colorFrom, colorTo)
    }

    private fun animateChargerStatus(card: MaterialCardView, isCharging: Boolean) {
        val colorFrom = ContextCompat.getColor(this, R.color.cardBackground)
        val colorTo = if (isCharging) {
            Color.parseColor("#4CAF50") // Green
        } else {
            ContextCompat.getColor(this, R.color.cardBackground)
        }

        animateCardColor(card, colorFrom, colorTo)
    }

    private fun animateBatteryHealth(card: MaterialCardView, health: String) {
        val colorFrom = ContextCompat.getColor(this, R.color.cardBackground)
        val colorTo = when (health) {
            "Good" -> Color.parseColor("#4CAF50") // Green
            "Overheated", "Over Voltage", "Failed", "Dead" -> Color.parseColor("#F44336") // Red
            "Cold" -> Color.parseColor("#2196F3") // Blue
            else -> ContextCompat.getColor(this, R.color.cardBackground)
        }

        animateCardColor(card, colorFrom, colorTo)
    }

    private fun animateBatteryStatus(card: MaterialCardView, status: Int) {
        val colorFrom = ContextCompat.getColor(this, R.color.cardBackground)
        val colorTo = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> Color.parseColor("#4CAF50") // Green
            BatteryManager.BATTERY_STATUS_FULL -> Color.parseColor("#2196F3") // Blue
            BatteryManager.BATTERY_STATUS_DISCHARGING -> Color.parseColor("#FFC107") // Yellow
            else -> ContextCompat.getColor(this, R.color.cardBackground)
        }

        animateCardColor(card, colorFrom, colorTo)
    }

    private fun animateCardColor(card: MaterialCardView, colorFrom: Int, colorTo: Int) {
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
        unregisterReceiver(batteryReceiver)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 