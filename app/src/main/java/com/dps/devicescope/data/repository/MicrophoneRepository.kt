package com.dps.devicescope.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import com.dps.devicescope.core.Formatters
import com.dps.devicescope.core.hasPermission
import com.dps.devicescope.data.model.AudioInputDeviceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

data class AudioFrame(
    val rmsDbfs: Float,
    val peakDbfs: Float,
    val normalizedLevel: Float
)

class MicrophoneRepository(private val context: Context) {

    private val audioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun getInputDevices(): List<AudioInputDeviceInfo> {
        return runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).map { device ->
                AudioInputDeviceInfo(
                    productName = device.productName?.toString()?.ifBlank { "Built-in Input" } ?: "Audio Input",
                    typeLabel = mapAudioDeviceType(device.type),
                    sampleRates = device.sampleRates.takeIf { it.isNotEmpty() }
                        ?.sorted()?.joinToString(", ") { "${it / 1000.0} kHz" }
                        ?: "System Default (44.1 / 48 kHz)",
                    channelCounts = device.channelCounts.takeIf { it.isNotEmpty() }
                        ?.sorted()?.joinToString(", ") { "$it ch" }
                        ?: "Mono / Stereo"
                )
            }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    fun observeAudioFrames(sampleRateHz: Int = DEFAULT_SAMPLE_RATE): Flow<AudioFrame> = flow {
        if (!context.hasPermission(Manifest.permission.RECORD_AUDIO)) {
            throw SecurityException("RECORD_AUDIO permission not granted")
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            throw IllegalStateException("AudioRecord buffer initialization failed ($minBuffer)")
        }

        val bufferSize = (minBuffer * 2).coerceAtLeast(2048)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IllegalStateException("Microphone hardware failed to initialize")
        }

        val samples = ShortArray(1024)
        try {
            recorder.startRecording()
            while (currentCoroutineContext().isActive) {
                val read = recorder.read(samples, 0, samples.size)
                if (read > 0) {
                    val (rms, peak) = Formatters.calculatePcmRmsAndPeak(samples, read)
                    val rmsDb = Formatters.calculateAudioDbfs(rms)
                    val peakDb = Formatters.calculateAudioDbfs(peak.toDouble())
                    val level = Formatters.normalizeDbfsToLevel(rmsDb)
                    emit(AudioFrame(rmsDbfs = rmsDb, peakDbfs = peakDb, normalizedLevel = level))
                } else if (read < 0) {
                    break
                }
            }
        } finally {
            runCatching {
                if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    recorder.stop()
                }
            }
            runCatching { recorder.release() }
        }
    }.flowOn(Dispatchers.IO)

    private fun mapAudioDeviceType(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in Microphone"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset Mic"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB Audio"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset"
        AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "Remote Submix"
        else -> "Audio Input #$type"
    }

    companion object {
        const val DEFAULT_SAMPLE_RATE = 44_100
    }
}
