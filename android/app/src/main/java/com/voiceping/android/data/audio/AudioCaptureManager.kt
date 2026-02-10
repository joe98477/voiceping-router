package com.voiceping.android.data.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.os.Process
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages microphone audio capture for PTT transmission.
 *
 * Captures audio at 48kHz mono using VOICE_COMMUNICATION source (enables
 * built-in AEC/AGC/NS). Uses callback pattern to avoid direct dependency on
 * MediasoupClient.
 *
 * Thread priority: THREAD_PRIORITY_URGENT_AUDIO for real-time audio capture.
 * Buffer: 2x minBufferSize for stability.
 */
@Singleton
class AudioCaptureManager @Inject constructor() {
    private var audioRecord: AudioRecord? = null
    private var captureThread: Thread? = null
    private val isCapturing = AtomicBoolean(false)
    private var acousticEchoCanceler: AcousticEchoCanceler? = null

    /**
     * Callback invoked with captured audio data.
     * Set by PttManager to route audio to mediasoup producer.
     */
    var onAudioData: ((ByteArray, Int) -> Unit)? = null

    /**
     * Start capturing microphone audio.
     *
     * Throws IllegalStateException if AudioRecord initialization fails.
     */
    fun startCapture() {
        if (isCapturing.get()) {
            Log.w(TAG, "Already capturing")
            return
        }

        try {
            // Calculate buffer size (2x for stability)
            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            )
            if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                throw IllegalStateException("Failed to get minBufferSize: $minBufferSize")
            }
            val bufferSize = minBufferSize * 2

            // Create AudioRecord with VOICE_COMMUNICATION source (enables AEC/AGC/NS)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            // Check initialization state
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord?.release()
                audioRecord = null
                throw IllegalStateException("AudioRecord not initialized")
            }

            // Enable AcousticEchoCanceler if available
            val audioSessionId = audioRecord?.audioSessionId ?: 0
            if (AcousticEchoCanceler.isAvailable()) {
                acousticEchoCanceler = AcousticEchoCanceler.create(audioSessionId)
                acousticEchoCanceler?.enabled = true
                Log.d(TAG, "AcousticEchoCanceler enabled")
            } else {
                Log.w(TAG, "AcousticEchoCanceler not available")
            }

            // Start recording
            audioRecord?.startRecording()
            isCapturing.set(true)

            // Launch capture thread with URGENT_AUDIO priority
            captureThread = Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                Log.d(TAG, "Capture thread started with URGENT_AUDIO priority")

                val buffer = ByteArray(bufferSize)
                while (isCapturing.get()) {
                    val bytesRead = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                    if (bytesRead > 0) {
                        // Copy buffer to avoid overwriting before consumer processes it
                        onAudioData?.invoke(buffer.copyOf(bytesRead), bytesRead)
                    } else if (bytesRead < 0) {
                        Log.e(TAG, "AudioRecord.read() error: $bytesRead")
                        break
                    }
                }

                Log.d(TAG, "Capture thread stopped")
            }.apply { start() }

            Log.d(TAG, "Audio capture started: sampleRate=$SAMPLE_RATE, bufferSize=$bufferSize")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start capture", e)
            cleanup()
            throw e
        }
    }

    /**
     * Stop capturing microphone audio.
     */
    fun stopCapture() {
        if (!isCapturing.get()) {
            return
        }

        try {
            isCapturing.set(false)

            // Wait for capture thread to finish
            captureThread?.join(1000)
            captureThread = null

            cleanup()

            Log.d(TAG, "Audio capture stopped")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping capture", e)
            cleanup()
        }
    }

    /**
     * Clean up audio resources.
     * CRITICAL: Release in finally block to prevent memory leaks.
     */
    private fun cleanup() {
        try {
            acousticEchoCanceler?.release()
            acousticEchoCanceler = null

            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null

            Log.d(TAG, "Cleanup complete")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }

    companion object {
        private const val TAG = "AudioCaptureManager"
        private const val SAMPLE_RATE = 48000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}
