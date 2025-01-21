package com.dps.mytestc

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.dps.mytestc.databinding.ActivityMicrophoneBinding
import com.google.android.material.card.MaterialCardView
import kotlin.math.abs
import kotlin.math.log10

class MicrophoneActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMicrophoneBinding
    private lateinit var waveformView: WaveformView
    private lateinit var micStatusCard: MaterialCardView
    private lateinit var audioLevelCard: MaterialCardView
    private lateinit var sampleRateCard: MaterialCardView
    private lateinit var channelCard: MaterialCardView
    private lateinit var micStatusValue: TextView
    private lateinit var audioLevelValue: TextView
    private lateinit var sampleRateValue: TextView
    private lateinit var channelValue: TextView

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private val handler = Handler(Looper.getMainLooper())
    private val bufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    companion object {
        private const val PERMISSION_REQUEST_CODE = 200
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMicrophoneBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        initializeViews()
        setupAudioInfo()

        if (checkAudioPermission()) {
            startAudioRecording()
        } else {
            requestAudioPermission()
        }
    }

    private fun initializeViews() {
        waveformView = WaveformView(this).also {
            binding.waveformView.addView(it)
        }
        micStatusCard = binding.micStatusCard
        audioLevelCard = binding.audioLevelCard
        sampleRateCard = binding.sampleRateCard
        channelCard = binding.channelCard

        micStatusValue = binding.micStatusValue
        audioLevelValue = binding.audioLevelValue
        sampleRateValue = binding.sampleRateValue
        channelValue = binding.channelValue
    }

    private fun setupAudioInfo() {
        sampleRateValue.text = "$SAMPLE_RATE Hz"
        channelValue.text = "Mono"
        animateTextChange(micStatusValue, "Initializing...")
    }

    private fun startAudioRecording() {
        if (isRecording) return

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        val buffer = ShortArray(bufferSize)
        isRecording = true

        audioRecord?.startRecording()
        animateTextChange(micStatusValue, "Recording")
        animateCard(micStatusCard, true)

        Thread {
            while (isRecording) {
                val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (readSize > 0) {
                    var sum = 0.0
                    for (i in 0 until readSize) {
                        sum += buffer[i] * buffer[i]
                    }
                    val rms = Math.sqrt(sum / readSize)
                    val db = 20 * log10(rms / Short.MAX_VALUE)
                    val amplitude = (rms / Short.MAX_VALUE).toFloat()

                    handler.post {
                        updateAudioLevel(db.toFloat(), amplitude)
                    }
                }
            }
        }.start()
    }

    private fun updateAudioLevel(db: Float, amplitude: Float) {
        val normalizedDb = (db + 60) / 60 * 100 // Normalize to 0-100 range
        binding.audioLevelBar.progress = normalizedDb.toInt()
        animateTextChange(audioLevelValue, String.format("%.1f dB", db))
        waveformView.addAmplitude(amplitude)
        animateAudioLevel(audioLevelCard, normalizedDb)
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
            ContextCompat.getColor(this, R.color.cardBackground)
        }

        animateCardColor(card, colorFrom, colorTo)
    }

    private fun animateAudioLevel(card: MaterialCardView, level: Float) {
        val colorFrom = ContextCompat.getColor(this, R.color.cardBackground)
        val colorTo = when {
            level >= 80 -> Color.parseColor("#F44336") // Red
            level >= 60 -> Color.parseColor("#FF9800") // Orange
            level >= 40 -> Color.parseColor("#FFC107") // Yellow
            level > 0 -> Color.parseColor("#4CAF50") // Green
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

    private fun checkAudioPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startAudioRecording()
            } else {
                animateTextChange(micStatusValue, "Permission Denied")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
} 