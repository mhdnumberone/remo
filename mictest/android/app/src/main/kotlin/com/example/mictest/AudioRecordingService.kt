package com.example.mictest

import android.Manifest
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class AudioRecordingService : Service() {

    companion object {
        private const val TAG = "Mictest_AudioService"
        const val NOTIFICATION_ID = 1001
        var isServiceRunning = false
            private set
    }

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var webSocketManager: WebSocketManager
    private lateinit var timedRecorder: TimedRecorder
    private lateinit var liveAudioStreamer: LiveAudioStreamer
    private lateinit var uploadQueue: UploadQueue
    private var wakeLock: PowerManager.WakeLock? = null

    // States for the two main features
    private var isTimedRecording = false
    private var isLiveStreaming = false

    // Coroutine scope for async operations
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "🚀 تم إنشاء خدمة التسجيل - ميزتان فقط")

        try {
            notificationHelper = NotificationHelper(this)
            startForeground(NOTIFICATION_ID, notificationHelper.createIdleNotification())
            Log.i(TAG, "✅ تم بدء الخدمة كـ foreground")

            // Initialize components for two features only
            webSocketManager = WebSocketManager(this)
            timedRecorder = TimedRecorder(this, ::onTimedRecordingComplete)
            liveAudioStreamer = LiveAudioStreamer(this, webSocketManager)
            uploadQueue = UploadQueue(this, webSocketManager)

            Handler(Looper.getMainLooper()).postDelayed({
                Log.i(TAG, "🔗 بدء الاتصال بـ WebSocket...")
                webSocketManager.connect()
            }, 2000)

            isServiceRunning = true
            Log.i(TAG, "✅ تم إنشاء الخدمة بنجاح - جاهزة للميزتين")

        } catch (e: Exception) {
            Log.e(TAG, "💥 خطأ في إنشاء الخدمة", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "📥 تم استلام أمر: ${intent?.action}")

        try {
            if (!isServiceRunning) {
                startForeground(NOTIFICATION_ID, notificationHelper.createIdleNotification())
                isServiceRunning = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ فشل في startForeground", e)
        }

        when (intent?.action) {
            "INIT_SERVICE" -> {
                Log.i(TAG, "🔧 تهيئة الخدمة")
            }

            // ===========================================
            // تسجيل محدد بوقت (Timed Recording) - الميزة الأولى
            // ===========================================
            "START_TIMED_RECORDING" -> {
                val duration = intent.getLongExtra("duration", 30000L) // افتراضي 30 ثانية
                if (!isTimedRecording && !isLiveStreaming) {
                    Log.i(TAG, "⏱️ بدء التسجيل المحدد بوقت: ${duration}ms")
                    startTimedRecording(duration)
                } else {
                    Log.w(TAG, "⚠️ التسجيل المحدد بوقت أو البث المباشر يعمل بالفعل")
                }
            }

            "STOP_TIMED_RECORDING" -> {
                if (isTimedRecording) {
                    Log.i(TAG, "⏹️ إيقاف التسجيل المحدد بوقت...")
                    stopTimedRecording()
                }
            }

            // ===========================================
            // بث مباشر (Live Streaming) - الميزة الثانية
            // ===========================================
            "START_LIVE_STREAM" -> {
                if (!isTimedRecording && !isLiveStreaming) {
                    Log.i(TAG, "📡 بدء البث المباشر...")
                    startLiveStreaming()
                } else {
                    Log.w(TAG, "⚠️ التسجيل المحدد بوقت أو البث المباشر يعمل بالفعل")
                }
            }

            "STOP_LIVE_STREAM" -> {
                if (isLiveStreaming) {
                    Log.i(TAG, "⏹️ إيقاف البث المباشر...")
                    stopLiveStreaming()
                }
            }

            // ===========================================
            // أوامر مساعدة
            // ===========================================
            "LIST_FILES" -> {
                Log.i(TAG, "📁 جلب قائمة الملفات...")
                sendFilesList()
            }

            "CONNECT_WEBSOCKET" -> {
                Log.i(TAG, "🔌 إعادة الاتصال بـ WebSocket...")
                if (!webSocketManager.isConnected()) {
                    webSocketManager.forceReconnect()
                }
            }

            "PROCESS_UPLOAD_QUEUE" -> {
                Log.i(TAG, "📤 معالجة قائمة انتظار الرفع...")
                uploadQueue.processQueue()
            }

            "CLEAR_CACHE" -> {
                Log.i(TAG, "🧹 تنظيف الكاش...")
                clearCacheDirectory()
            }
        }

        return START_STICKY
    }

    // =================================
    // ميزة التسجيل المحدد بوقت - الميزة الأولى
    // =================================

    fun isTimedRecording(): Boolean = isTimedRecording
    fun isLiveStreaming(): Boolean = isLiveStreaming

    private fun startTimedRecording(durationMs: Long) {
        serviceScope.launch {
            try {
                val success = timedRecorder.startTimedRecording(durationMs)
                if (success) {
                    isTimedRecording = true
                    withContext(Dispatchers.Main) {
                        startForeground(NOTIFICATION_ID, notificationHelper.createTimedRecordingNotification(durationMs))
                    }
                    Log.i(TAG, "✅ تم بدء التسجيل المحدد بوقت: ${durationMs}ms")
                } else {
                    Log.e(TAG, "❌ فشل في بدء التسجيل المحدد بوقت")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في بدء التسجيل المحدد بوقت", e)
            }
        }
    }

    private fun stopTimedRecording() {
        serviceScope.launch {
            try {
                timedRecorder.stopTimedRecording()
                isTimedRecording = false
                withContext(Dispatchers.Main) {
                    startForeground(NOTIFICATION_ID, notificationHelper.createIdleNotification())
                }
                Log.i(TAG, "✅ تم إيقاف التسجيل المحدد بوقت")
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في إيقاف التسجيل المحدد بوقت", e)
            }
        }
    }

    private fun onTimedRecordingComplete(filePath: String) {
        Log.i(TAG, "🎯 اكتمل التسجيل المحدد بوقت: $filePath")
        isTimedRecording = false

        // إضافة للرفع الفوري
        uploadQueue.addToQueue(filePath)

        // العودة للحالة الخاملة
        serviceScope.launch {
            withContext(Dispatchers.Main) {
                try {
                    startForeground(NOTIFICATION_ID, notificationHelper.createIdleNotification())
                } catch (e: Exception) {
                    Log.e(TAG, "❌ فشل في العودة للحالة الخاملة", e)
                }
            }
        }
    }

    // =================================
    // ميزة البث المباشر - الميزة الثانية
    // =================================

    private fun startLiveStreaming() {
        serviceScope.launch {
            try {
                val success = liveAudioStreamer.startStreaming()
                if (success) {
                    isLiveStreaming = true
                    withContext(Dispatchers.Main) {
                        startForeground(NOTIFICATION_ID, notificationHelper.createLiveStreamingNotification())
                    }
                    Log.i(TAG, "✅ تم بدء البث المباشر بنجاح")
                } else {
                    Log.e(TAG, "❌ فشل في بدء البث المباشر")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في بدء البث المباشر", e)
            }
        }
    }

    private fun stopLiveStreaming() {
        serviceScope.launch {
            try {
                liveAudioStreamer.stopStreaming()
                isLiveStreaming = false
                withContext(Dispatchers.Main) {
                    startForeground(NOTIFICATION_ID, notificationHelper.createIdleNotification())
                }
                Log.i(TAG, "✅ تم إيقاف البث المباشر")
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في إيقاف البث المباشر", e)
            }
        }
    }

    // =================================
    // إدارة الملفات
    // =================================

    fun sendFilesList() {
        try {
            val allFiles = mutableListOf<Map<String, Any>>()

            // الحصول على الملفات من مجلد الكاش (ملفات مؤقتة من التسجيل المحدد بوقت)
            val cacheDir = java.io.File(cacheDir, "timed_recordings")
            if (cacheDir.exists()) {
                val cacheFiles = cacheDir.listFiles { file ->
                    file.isFile && (file.extension == "aac" || file.extension == "wav" || file.extension == "mp3")
                }
                cacheFiles?.forEach { file ->
                    allFiles.add(mapOf(
                        "name" to file.name,
                        "path" to file.absolutePath,
                        "sizeInBytes" to file.length(),
                        "sizeInMB" to String.format("%.2f", file.length() / (1024.0 * 1024.0)),
                        "createdAt" to file.lastModified(),
                        "type" to "timed_recording"
                    ))
                }
            }

            Log.d(TAG, "📊 عدد الملفات المسجلة بوقت: ${allFiles.size}")
            webSocketManager.sendFilesListToFlutter(allFiles)

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في جلب قائمة الملفات", e)
        }
    }

    private fun clearCacheDirectory() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = java.io.File(cacheDir, "timed_recordings")
                if (cacheDir.exists()) {
                    val files = cacheDir.listFiles()
                    files?.forEach { file ->
                        if (file.isFile) {
                            val deleted = file.delete()
                            Log.d(TAG, "${if (deleted) "✅" else "❌"} حذف الملف: ${file.name}")
                        }
                    }
                    Log.i(TAG, "🧹 تم تنظيف مجلد التسجيلات المؤقتة")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في تنظيف الكاش", e)
            }
        }
    }

    fun getWebSocketInfo(): Map<String, Any> {
        return webSocketManager.getConnectionInfo()
    }

    fun getUploadQueueInfo(): Map<String, Any> {
        return uploadQueue.getQueueInfo()
    }

    fun getServiceStatus(): Map<String, Any> {
        return mapOf(
            "service_running" to isServiceRunning,
            "timed_recording_active" to isTimedRecording,
            "live_streaming_active" to isLiveStreaming,
            "websocket_connected" to webSocketManager.isConnected(),
            "upload_queue_size" to uploadQueue.getQueueSize()
        )
    }

    private fun cleanup() {
        isTimedRecording = false
        isLiveStreaming = false
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "🗑️ تم تدمير خدمة التسجيل")

        if (isTimedRecording) {
            stopTimedRecording()
        }
        if (isLiveStreaming) {
            serviceScope.launch {
                liveAudioStreamer.stopStreaming()
            }
        }

        cleanup()
        timedRecorder.cleanup()
        liveAudioStreamer.cleanup()
        uploadQueue.cleanup()
        webSocketManager.disconnect()
        serviceScope.cancel()
        isServiceRunning = false
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.w(TAG, "⚠️ تم إزالة المهمة")

        if (isTimedRecording) {
            stopTimedRecording()
        }
        if (isLiveStreaming) {
            serviceScope.launch {
                liveAudioStreamer.stopStreaming()
            }
        }

        try {
            startForeground(NOTIFICATION_ID, notificationHelper.createIdleNotification())
        } catch (e: Exception) {
            Log.e(TAG, "❌ فشل في العودة للحالة الخاملة", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
