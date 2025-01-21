package com.dps.mytestc

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.dps.mytestc.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.sensorsButton.setOnClickListener {
            startActivity(Intent(this, SensorsActivity::class.java))
        }

        binding.cellularCard.setOnClickListener {
            startActivity(Intent(this, CellularActivity::class.java))
        }

        binding.batteryCard.setOnClickListener {
            startActivity(Intent(this, BatteryActivity::class.java))
        }

        binding.locationCard.setOnClickListener {
            startActivity(Intent(this, LocationActivity::class.java))
        }

        binding.microphoneCard.setOnClickListener {
            startActivity(Intent(this, MicrophoneActivity::class.java))
        }

        binding.fingerprintCard.setOnClickListener {
            startActivity(Intent(this, FingerprintActivity::class.java))
        }

        binding.phoneCard.setOnClickListener {
            startActivity(Intent(this, PhoneActivity::class.java))
        }

        binding.cameraCard.setOnClickListener {
            startActivity(Intent(this, CameraInfoActivity::class.java))
        }
    }
}