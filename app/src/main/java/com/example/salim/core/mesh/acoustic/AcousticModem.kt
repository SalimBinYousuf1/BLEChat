package com.example.salim.core.mesh.acoustic

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sin

/**
 * Audio-Acoustic Backup (Ultrasonic / Acoustic Pairing):
 * Transmits crypto handshakes, public keys, or short verification codes over
 * dual-tone FSK chirps through the device speaker/microphone when Bluetooth is congested.
 */
class AcousticModem {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isChirping = MutableStateFlow(false)
    val isChirping: StateFlow<Boolean> = _isChirping.asStateFlow()

    private val _lastDecodedToken = MutableStateFlow<String?>(null)
    val lastDecodedToken: StateFlow<String?> = _lastDecodedToken.asStateFlow()

    companion object {
        const val SAMPLE_RATE = 44100
        const val MARK_FREQ = 18500.0   // 18.5 kHz (near-ultrasonic, safe for speakers)
        const val SPACE_FREQ = 19500.0  // 19.5 kHz
        const val AUDIBLE_MARK = 1800.0 // 1.8 kHz audible fallback
        const val AUDIBLE_SPACE = 2400.0// 2.4 kHz audible fallback
    }

    /**
     * Synthesizes and plays a dual-frequency audio chirp encoding an authentication token.
     */
    fun transmitAcousticToken(token: String, useNearUltrasonic: Boolean = true, onComplete: () -> Unit = {}) {
        scope.launch {
            _isChirping.value = true
            val mark = if (useNearUltrasonic) MARK_FREQ else AUDIBLE_MARK
            val space = if (useNearUltrasonic) SPACE_FREQ else AUDIBLE_SPACE

            val bitDurationMs = 40
            val numSamplesPerBit = (SAMPLE_RATE * bitDurationMs) / 1000
            val bytes = token.toByteArray(Charsets.UTF_8).take(16)

            val totalBits = bytes.size * 8
            val totalSamples = totalBits * numSamplesPerBit
            val audioBuffer = ShortArray(totalSamples)

            var sampleIdx = 0
            for (b in bytes) {
                for (i in 7 downTo 0) {
                    val bit = (b.toInt() shr i) and 1
                    val freq = if (bit == 1) mark else space
                    for (s in 0 until numSamplesPerBit) {
                        val angle = 2.0 * Math.PI * s / (SAMPLE_RATE / freq)
                        val sampleVal = (sin(angle) * Short.MAX_VALUE * 0.7).toInt().toShort()
                        if (sampleIdx < audioBuffer.size) {
                            audioBuffer[sampleIdx++] = sampleVal
                        }
                    }
                }
            }

            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(audioBuffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(audioBuffer, 0, audioBuffer.size)
                track.play()
                kotlinx.coroutines.delay((totalSamples * 1000L) / SAMPLE_RATE + 200)
                track.stop()
                track.release()
            } catch (_: Exception) {}

            _isChirping.value = false
            _lastDecodedToken.value = token
            onComplete()
        }
    }
}
