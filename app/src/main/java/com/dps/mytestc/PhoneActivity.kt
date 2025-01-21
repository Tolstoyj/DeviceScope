package com.dps.mytestc

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dps.mytestc.databinding.ActivityPhoneBinding
import com.google.android.material.card.MaterialCardView

class PhoneActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPhoneBinding
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var subscriptionManager: SubscriptionManager

    companion object {
        private const val PERMISSION_REQUEST_CODE = 123
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_PHONE_NUMBERS
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhoneBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        initializeManagers()
        checkPermissions()
    }

    private fun initializeManagers() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
    }

    private fun checkPermissions() {
        if (hasRequiredPermissions()) {
            loadPhoneInfo()
        } else {
            requestPermissions()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            REQUIRED_PERMISSIONS,
            PERMISSION_REQUEST_CODE
        )
    }

    private fun loadPhoneInfo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        // Phone Number
        try {
            val phoneNumber = telephonyManager.line1Number ?: "Not Available"
            binding.phoneNumberValue.text = phoneNumber
        } catch (e: Exception) {
            binding.phoneNumberValue.text = "Not Available"
        }

        // Network Provider
        try {
            val operatorName = telephonyManager.networkOperatorName
            binding.networkProviderValue.text = if (operatorName.isNotEmpty()) operatorName else "Unknown"
        } catch (e: Exception) {
            binding.networkProviderValue.text = "Unknown"
        }

        // Network Type
        val networkType = when (telephonyManager.dataNetworkType) {
            TelephonyManager.NETWORK_TYPE_LTE -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_HSDPA,
            TelephonyManager.NETWORK_TYPE_HSUPA,
            TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSPAP -> "3G"
            TelephonyManager.NETWORK_TYPE_GPRS,
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G"
            else -> "Unknown"
        }
        binding.networkTypeValue.text = networkType

        // IMEI Information
        try {
            val imei = telephonyManager.getImei(0) ?: "Not Available"
            binding.imeiValue.text = imei
        } catch (e: Exception) {
            binding.imeiValue.text = "Not Available"
        }

        // SIM Information
        loadSimInfo()
    }

    private fun loadSimInfo() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val activeSubscriptions = subscriptionManager.activeSubscriptionInfoList
        if (activeSubscriptions != null && activeSubscriptions.isNotEmpty()) {
            binding.simInfoValue.text = "${activeSubscriptions.size} SIM card(s) detected"
            binding.simCardsContainer.removeAllViews()
            
            activeSubscriptions.forEachIndexed { index, subscriptionInfo ->
                addSimCardView(index + 1, subscriptionInfo)
            }
        } else {
            binding.simInfoValue.text = "No SIM cards detected"
        }
    }

    private fun addSimCardView(simNumber: Int, info: SubscriptionInfo) {
        val simView = layoutInflater.inflate(R.layout.item_sim_card, binding.simCardsContainer, false)
        
        simView.findViewById<TextView>(R.id.simNumberText).text = "SIM $simNumber"
        simView.findViewById<TextView>(R.id.carrierNameText).text = info.carrierName
        simView.findViewById<TextView>(R.id.simSlotText).text = "Slot: ${info.simSlotIndex + 1}"
        simView.findViewById<TextView>(R.id.numberText).text = info.number ?: "Unknown"
        
        binding.simCardsContainer.addView(simView)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadPhoneInfo()
            } else {
                // Show limited information
                binding.phoneNumberValue.text = "Permission Required"
                binding.networkProviderValue.text = "Permission Required"
                binding.simInfoValue.text = "Permission Required"
                binding.imeiValue.text = "Permission Required"
            }
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