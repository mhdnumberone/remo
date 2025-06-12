package com.example.mictest

import android.content.Context
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * التسجيل المحدد بوقت - الميزة الأولى
 * يسجل الصوت لمدة محددة يحددها السيرفر ويرفعه تلقائياً
 */
class TimedRecorder(
    private val context: Context,
    private val onRecordingComplete: (String) -> Unit
) {
    companion object {
        private const val TAG = "Mictest_TimedRecorder"
        private const val MIN_DURATION_MS = 1000L    // ثانية واحدة كحد أدنى
        private const val MAX_DURATION_MS = 600000L  // 10 دقائق كحد أقصى
    }

    private var mediaRecorder: MediaRecorder? = null
    private var timerJob: Job? = null
    private var progressJob: Job? = null
    private var currentRecordingPath: String? = null
    private val recordingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordingStartTime: Long = 0
    private var totalDuration: Long = 0

    suspend fun startTimedRecording(durationMs: Long): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (mediaRecorder != null) {
                    Log.w(TAG, "⚠️ تسجيل محدد بوقت يعمل بالفعل")
                    return@withContext false
                }

                // التحقق من صحة المدة
                if (durationMs < MIN_DURATION_MS || durationMs > MAX_DURATION_MS) {
                    Log.e(TAG, "❌ مدة التسجيل غير صالحة: ${durationMs}ms")
                    return@withContext false
                }

                // إنشاء ملف في مجلد cache خاص بالتسجيلات المحددة بوقت
                val cacheDir = File(context.cacheDir, "timed_recordings")
                if (!cacheDir.exists()) {
                    cacheDir.mkdirs()
                }

                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val fileName = "timed_${durationMs}ms_${timestamp}.aac"
                val recordingFile = File(cacheDir, fileName)
                currentRecordingPath = recordingFile.absolutePath

                Log.i(TAG, "📍 مسار التسجيل المحدد بوقت: $currentRecordingPath")
                Log.i(TAG, "⏱️ مدة التسجيل: ${durationMs}ms (${durationMs/1000} ثانية)")

                // إعداد MediaRecorder بجودة عالية للميزة المحددة بوقت
                mediaRecorder = MediaRecorder().apply {
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setOutputFormat(MediaRecorder.OutputFormat.AAC_ADTS)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    setAudioEncodingBitRate(128000)  // 128 kbps
                    setAudioSamplingRate(44100)      // 44.1 kHz
                    setOutputFile(currentRecordingPath)

                    prepare()
                    start()
                }

                recordingStartTime = System.currentTimeMillis()
                totalDuration = durationMs

                // تشغيل مؤقت التوقف التلقائي
                timerJob = recordingScope.launch {
                    delay(durationMs)
                    stopTimedRecording()
                }

                // تشغيل مؤقت التقدم (اختياري - يمكن إرسال التقدم للسيرفر)
                startProgressTracking()

                Log.i(TAG, "✅ تم بدء التسجيل المحدد بوقت بنجاح")
                return@withContext true

            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في بدء التسجيل المحدد بوقت", e)
                cleanupResources()
                return@withContext false
            }
        }
    }

    private fun startProgressTracking() {
        progressJob = recordingScope.launch {
            while (isRecording() && timerJob?.isActive == true) {
                val elapsed = System.currentTimeMillis() - recordingStartTime
                val remaining = totalDuration - elapsed
                val progress = (elapsed * 100) / totalDuration

                Log.d(TAG, "📊 تقدم التسجيل: ${progress}% - متبقي: ${remaining}ms")

                // يمكن إرسال التقدم للسيرفر هنا إذا كان مطلوباً
                // sendProgressToServer(elapsed, totalDuration, progress)

                delay(1000) // تحديث كل ثانية
            }
        }
    }

    suspend fun stopTimedRecording() {
        withContext(Dispatchers.IO) {
            try {
                timerJob?.cancel()
                progressJob?.cancel()

                mediaRecorder?.apply {
                    try {
                        stop()
                        release()
                        Log.i(TAG, "✅ تم إيقاف التسجيل المحدد بوقت")

                        // تحقق من صحة الملف وتسليمه للرفع
                        currentRecordingPath?.let { path ->
                            val file = File(path)
                            if (file.exists() && file.length() > 0) {
                                val durationRecorded = System.currentTimeMillis() - recordingStartTime
                                Log.i(TAG, "📤 تسليم الملف للرفع: ${file.name}")
                                Log.i(TAG, "📊 حجم الملف: ${file.length()} bytes")
                                Log.i(TAG, "⏱️ مدة التسجيل الفعلية: ${durationRecorded}ms")

                                onRecordingComplete(path)
                            } else {
                                Log.w(TAG, "⚠️ الملف فارغ أو غير موجود: $path")
                            }
                        }

                    } catch (e: Exception) {
                        Log.e(TAG, "❌ خطأ في إيقاف MediaRecorder", e)
                    }
                }

                cleanupResources()

            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في إيقاف التسجيل المحدد بوقت", e)
            }
        }
    }

    fun isRecording(): Boolean {
        return mediaRecorder != null
    }

    fun getCurrentRecordingPath(): String? {
        return currentRecordingPath
    }

    fun getRemainingTime(): Long {
        return if (isRecording() && timerJob?.isActive == true) {
            val elapsed = System.currentTimeMillis() - recordingStartTime
            maxOf(0, totalDuration - elapsed)
        } else {
            0L
        }
    }

    fun getRecordingProgress(): Int {
        return if (isRecording() && totalDuration > 0) {
            val elapsed = System.currentTimeMillis() - recordingStartTime
            ((elapsed * 100) / totalDuration).toInt()
        } else {
            0
        }
    }

    fun getRecordingInfo(): Map<String, Any> {
        return mapOf(
            "is_recording" to isRecording(),
            "current_path" to (currentRecordingPath ?: ""),
            "remaining_time_ms" to getRemainingTime(),
            "progress_percentage" to getRecordingProgress(),
            "total_duration_ms" to totalDuration,
            "elapsed_time_ms" to if (isRecording()) (System.currentTimeMillis() - recordingStartTime) else 0L
        )
    }

    private fun cleanupResources() {
        mediaRecorder?.release()
        mediaRecorder = null
        timerJob?.cancel()
        timerJob = null
        progressJob?.cancel()
        progressJob = null
        currentRecordingPath = null
        recordingStartTime = 0
        totalDuration = 0
    }

    fun cleanup() {
        recordingScope.cancel()
        cleanupResources()
    }
}
