package com.example.salim.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object MediaManager {

    /**
     * Aggressively compresses user selected photo:
     * - Resizes to max 640x640 preserving aspect ratio
     * - Strips EXIF metadata
     * - Compresses to JPEG 75%
     * Returns raw byte array capped for mesh transmission.
     */
    fun compressImage(context: Context, imageUri: Uri): ByteArray {
        val inputStream = context.contentResolver.openInputStream(imageUri) ?: return ByteArray(0)
        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        originalBitmap ?: return ByteArray(0)

        val maxDim = 640
        val width = originalBitmap.width
        val height = originalBitmap.height
        val scale = if (width > maxDim || height > maxDim) {
            val rWidth = maxDim.toFloat() / width
            val rHeight = maxDim.toFloat() / height
            minOf(rWidth, rHeight)
        } else 1.0f

        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, targetWidth, targetHeight, true)

        val outStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outStream)
        return outStream.toByteArray()
    }

    /**
     * Saves incoming or outgoing image bytes to private app storage.
     * Returns absolute file path.
     */
    fun saveImageFile(context: Context, imageBytes: ByteArray, id: String = UUID.randomUUID().toString()): String {
        val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
        val file = File(mediaDir, "img_${id.take(16)}.jpg")
        FileOutputStream(file).use { it.write(imageBytes) }
        return file.absolutePath
    }

    /**
     * Saves incoming or outgoing audio bytes to private app storage.
     * Returns absolute file path.
     */
    fun saveAudioFile(context: Context, audioBytes: ByteArray, id: String = UUID.randomUUID().toString()): String {
        val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
        val file = File(audioDir, "audio_${id.take(16)}.m4a")
        FileOutputStream(file).use { it.write(audioBytes) }
        return file.absolutePath
    }

    /**
     * Real Android MediaRecorder wrapper for voice notes.
     */
    class VoiceRecorder(private val context: Context) {
        private var recorder: MediaRecorder? = null
        var currentOutputFile: File? = null
            private set

        fun startRecording(): Boolean {
            return try {
                val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
                val file = File(audioDir, "temp_rec_${System.currentTimeMillis()}.m4a")
                currentOutputFile = file

                val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(context)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

                mr.setAudioSource(MediaRecorder.AudioSource.MIC)
                mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mr.setAudioEncodingBitRate(32000) // Compact bit rate for mesh
                mr.setAudioSamplingRate(16000)
                mr.setOutputFile(file.absolutePath)
                mr.prepare()
                mr.start()
                recorder = mr
                true
            } catch (_: Exception) {
                recorder = null
                false
            }
        }

        fun stopRecording(): ByteArray? {
            return try {
                recorder?.stop()
                recorder?.release()
                recorder = null
                val file = currentOutputFile
                if (file != null && file.exists()) {
                    val bytes = file.readBytes()
                    bytes
                } else null
            } catch (_: Exception) {
                recorder = null
                null
            }
        }

        fun cancelRecording() {
            try {
                recorder?.stop()
                recorder?.release()
                recorder = null
                currentOutputFile?.delete()
                currentOutputFile = null
            } catch (_: Exception) {}
        }
    }

    /**
     * Real Android MediaPlayer wrapper for playing voice notes.
     */
    class VoicePlayer {
        private var player: MediaPlayer? = null

        fun play(filePath: String, onCompletion: () -> Unit): Boolean {
            stop()
            return try {
                val mp = MediaPlayer()
                mp.setDataSource(filePath)
                mp.prepare()
                mp.setOnCompletionListener {
                    stop()
                    onCompletion()
                }
                mp.start()
                player = mp
                true
            } catch (_: Exception) {
                stop()
                false
            }
        }

        fun stop() {
            try {
                player?.stop()
                player?.release()
            } catch (_: Exception) {}
            player = null
        }

        val isPlaying: Boolean get() = player?.isPlaying ?: false
    }
}
