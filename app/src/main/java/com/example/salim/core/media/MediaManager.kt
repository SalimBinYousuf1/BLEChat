package com.example.salim.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object MediaManager {

    /**
     * Aggressively compresses user selected photo:
     * - Safely subsamples to prevent OOM
     * - Resizes to max 480x480 preserving aspect ratio
     * - Strips EXIF metadata
     * - Step-down quality compression to ensure size <= 24 KB for reliable mesh delivery
     */
    fun compressImage(context: Context, imageUri: Uri): ByteArray {
        return try {
            // Step 1: Decode image bounds only to avoid OutOfMemoryError
            var input: InputStream? = context.contentResolver.openInputStream(imageUri)
                ?: return ByteArray(0)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(input, null, options)
            input.close()

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return ByteArray(0)

            // Calculate sample size for max 480px dimension
            val targetMaxDim = 480
            var sampleSize = 1
            while ((origWidth / sampleSize) > (targetMaxDim * 2) || (origHeight / sampleSize) > (targetMaxDim * 2)) {
                sampleSize *= 2
            }

            // Step 2: Decode subsampled bitmap safely
            input = context.contentResolver.openInputStream(imageUri) ?: return ByteArray(0)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565 // 50% memory savings vs ARGB_8888
            }
            val sampledBitmap = BitmapFactory.decodeStream(input, null, decodeOptions)
            input.close()
            sampledBitmap ?: return ByteArray(0)

            // Step 3: Exact scale down to targetMaxDim
            val curWidth = sampledBitmap.width
            val curHeight = sampledBitmap.height
            val scale = if (curWidth > targetMaxDim || curHeight > targetMaxDim) {
                val rW = targetMaxDim.toFloat() / curWidth
                val rH = targetMaxDim.toFloat() / curHeight
                minOf(rW, rH)
            } else 1.0f

            val scaledBitmap = if (scale < 1.0f) {
                val sW = (curWidth * scale).toInt().coerceAtLeast(1)
                val sH = (curHeight * scale).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(sampledBitmap, sW, sH, true).also {
                    if (it != sampledBitmap) sampledBitmap.recycle()
                }
            } else {
                sampledBitmap
            }

            // Step 4: Iterative compression loop down to target max 24,000 bytes
            val maxAllowedBytes = 24000
            val qualities = intArrayOf(75, 60, 45, 30, 20)
            var finalBytes = ByteArray(0)

            for (quality in qualities) {
                val out = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                val current = out.toByteArray()
                finalBytes = current
                if (current.size <= maxAllowedBytes) break
            }

            scaledBitmap.recycle()
            finalBytes
        } catch (_: Throwable) {
            ByteArray(0)
        }
    }

    /**
     * Saves incoming or outgoing image bytes to private app storage.
     * Returns absolute file path.
     */
    fun saveImageFile(context: Context, imageBytes: ByteArray, id: String = UUID.randomUUID().toString()): String {
        return try {
            val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
            val file = File(mediaDir, "img_${id.take(16)}.jpg")
            FileOutputStream(file).use { it.write(imageBytes) }
            file.absolutePath
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Saves incoming or outgoing audio bytes to private app storage.
     * Returns absolute file path.
     */
    fun saveAudioFile(context: Context, audioBytes: ByteArray, id: String = UUID.randomUUID().toString()): String {
        return try {
            val audioDir = File(context.filesDir, "audio").apply { mkdirs() }
            val file = File(audioDir, "audio_${id.take(16)}.m4a")
            FileOutputStream(file).use { it.write(audioBytes) }
            file.absolutePath
        } catch (_: Exception) {
            ""
        }
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
