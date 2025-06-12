package com.example.mictest

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.NonNull
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import android.util.Log
import java.io.File
import kotlinx.coroutines.*

class MainActivity: FlutterActivity() {
    private val AUDIO_CHANNEL = "com.example.mictest/audio"
    private val BATTERY_CHANNEL = "com.example.mictest/battery"
    private val WEBSOCKET_CHANNEL = "com.example.mictest/websocket"  // 🆕 إضافة جديدة
    private val TAG = "Mictest_MainActivity"
    private val PERMISSION_REQUEST_CODE = 1001

    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // ✅ التهيئة المحسنة
        initializeApp()
        setupAudioChannel(flutterEngine)
        setupBatteryChannel(flutterEngine)
        setupWebSocketChannel(flutterEngine)  // 🆕 إضافة جديدة
    }

    private fun initializeApp() {
        mainScope.launch {
            try {
                Log.i(TAG, "🔄 بدء تهيئة التطبيق...")

                // طلب الصلاحيات أولاً
                withContext(Dispatchers.Main) {
                    requestAllPermissions()
                }

                // انتظار للصلاحيات
                delay(2000)

                // بدء الخدمة مع التأكد
                val serviceStarted = startEssentialServiceWithVerification()

                if (serviceStarted) {
                    Log.i(TAG, "✅ تم تهيئة التطبيق بنجاح")
                } else {
                    Log.e(TAG, "❌ فشل في تهيئة التطبيق")
                }

            } catch (e: Exception) {
                Log.e(TAG, "💥 خطأ في تهيئة التطبيق", e)
            }
        }
    }

    // ✅ دالة محسنة لبدء الخدمة مع التحقق
    private suspend fun startEssentialServiceWithVerification(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "🚀 محاولة بدء الخدمة...")

                // التأكد من عدم تشغيل الخدمة مسبقاً
                if (AudioRecordingService.isServiceRunning) {
                    Log.i(TAG, "ℹ️ الخدمة تعمل بالفعل")
                    return@withContext true
                }

                // بدء الخدمة مع action صحيح
                val intent = Intent(this@MainActivity, AudioRecordingService::class.java).apply {
                    action = "INIT_SERVICE" // ✅ إضافة action محدد
                }

                withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                }

                Log.i(TAG, "📤 تم إرسال أمر بدء الخدمة")

                // انتظار وفحص بدء الخدمة
                var attempts = 0
                while (attempts < 10 && !AudioRecordingService.isServiceRunning) {
                    delay(500)
                    attempts++
                    Log.d(TAG, "🔍 فحص حالة الخدمة... المحاولة $attempts")
                }

                val isRunning = AudioRecordingService.isServiceRunning
                Log.i(TAG, if (isRunning) "✅ الخدمة تعمل بنجاح" else "❌ فشل في بدء الخدمة")

                return@withContext isRunning

            } catch (e: Exception) {
                Log.e(TAG, "💥 خطأ في بدء الخدمة", e)
                return@withContext false
            }
        }
    }

    private fun setupAudioChannel(flutterEngine: FlutterEngine) {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, AUDIO_CHANNEL)
            .setMethodCallHandler { call, result ->
                Log.d(TAG, "📨 تم استلام أمر: ${call.method}")

                // ✅ تشغيل في background thread
                mainScope.launch(Dispatchers.IO) {
                    try {
                        when (call.method) {
                            "ensureServiceRunning" -> {
                                val success = ensureServiceRunning()
                                withContext(Dispatchers.Main) {
                                    result.success(success)
                                }
                            }

                            "startBackgroundRecording" -> {
                                val success = handleStartRecording()
                                withContext(Dispatchers.Main) {
                                    result.success(success)
                                }
                            }

                            "stopBackgroundRecording" -> {
                                val success = handleStopRecording()
                                withContext(Dispatchers.Main) {
                                    result.success(success)
                                }
                            }

                            "checkServiceStatus" -> {
                                val status = getDetailedServiceStatus()
                                withContext(Dispatchers.Main) {
                                    result.success(status)
                                }
                            }

                            "listAudioFiles" -> {
                                val files = handleListFiles()
                                withContext(Dispatchers.Main) {
                                    result.success(files)
                                }
                            }

                            "deleteAudioFile" -> {
                                val filePath = call.argument<String>("filePath")
                                val success = if (filePath != null) {
                                    deleteAudioFile(filePath)
                                } else false
                                withContext(Dispatchers.Main) {
                                    if (filePath == null) {
                                        result.error("INVALID_ARGUMENT", "File path cannot be null", null)
                                    } else {
                                        result.success(success)
                                    }
                                }
                            }

                            "getConnectionStatus" -> {
                                val connectionInfo = getWebSocketConnectionInfo()
                                withContext(Dispatchers.Main) {
                                    result.success(connectionInfo)
                                }
                            }

                            "forceReconnect" -> {
                                val success = forceWebSocketReconnect()
                                withContext(Dispatchers.Main) {
                                    result.success(success)
                                }
                            }

                            // 🆕 إضافات جديدة لفحص SD Card
                            "scanExternalStorage" -> {
                                val success = handleScanExternalStorage()
                                withContext(Dispatchers.Main) {
                                    result.success(success)
                                }
                            }

                            "getStorageInfo" -> {
                                val storageInfo = handleGetStorageInfo()
                                withContext(Dispatchers.Main) {
                                    result.success(storageInfo)
                                }
                            }

                            "checkPermissions" -> {
                                val permissions = checkAllPermissions()
                                withContext(Dispatchers.Main) {
                                    result.success(permissions)
                                }
                            }

                            else -> {
                                withContext(Dispatchers.Main) {
                                    Log.w(TAG, "❓ أمر غير معروف: ${call.method}")
                                    result.notImplemented()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "💥 خطأ في معالجة الأمر ${call.method}", e)
                        withContext(Dispatchers.Main) {
                            result.error("EXECUTION_ERROR", "خطأ في تنفيذ الأمر: ${e.message}", null)
                        }
                    }
                }
            }
    }

    // 🆕 إعداد قناة WebSocket الجديدة
    private fun setupWebSocketChannel(flutterEngine: FlutterEngine) {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, WEBSOCKET_CHANNEL)
            .setMethodCallHandler { call, result ->
                mainScope.launch(Dispatchers.IO) {
                    try {
                        when (call.method) {
                            "sendMessage" -> {
                                val message = call.argument<String>("message") ?: ""
                                val success = handleSendWebSocketMessage(message)
                                withContext(Dispatchers.Main) {
                                    result.success(success)
                                }
                            }

                            "requestFilesList" -> {
                                val success = handleRequestFilesList()
                                withContext(Dispatchers.Main) {
                                    result.success(success)
                                }
                            }

                            else -> {
                                withContext(Dispatchers.Main) {
                                    result.notImplemented()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "خطأ في معالجة WebSocket Channel: ${call.method}", e)
                        withContext(Dispatchers.Main) {
                            result.error("ERROR", e.message, null)
                        }
                    }
                }
            }
    }

    private fun setupBatteryChannel(flutterEngine: FlutterEngine) {
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, BATTERY_CHANNEL)
            .setMethodCallHandler { call, result ->
                mainScope.launch(Dispatchers.IO) {
                    try {
                        when (call.method) {
                            "isIgnoringBatteryOptimizations" -> {
                                val isIgnoring = isIgnoringBatteryOptimizations()
                                withContext(Dispatchers.Main) {
                                    result.success(isIgnoring)
                                }
                            }
                            "requestIgnoreBatteryOptimizations" -> {
                                withContext(Dispatchers.Main) {
                                    requestIgnoreBatteryOptimizations()
                                    result.success(true)
                                }
                            }
                            "requestAutoStartPermission" -> {
                                withContext(Dispatchers.Main) {
                                    requestAutoStartPermission()
                                    result.success(true)
                                }
                            }
                            else -> {
                                withContext(Dispatchers.Main) {
                                    result.notImplemented()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "💥 خطأ في معالجة أمر البطارية", e)
                        withContext(Dispatchers.Main) {
                            result.error("BATTERY_ERROR", e.message ?: "Unknown error", null)
                        }
                    }
                }
            }
    }

    // ✅ دوال محسنة
    private suspend fun ensureServiceRunning(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (!AudioRecordingService.isServiceRunning) {
                    Log.w(TAG, "⚠️ الخدمة غير نشطة، محاولة إعادة تشغيل...")
                    return@withContext startEssentialServiceWithVerification()
                }

                Log.d(TAG, "✅ الخدمة تعمل بالفعل")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في ضمان تشغيل الخدمة", e)
                return@withContext false
            }
        }
    }

    private suspend fun handleStartRecording(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // ضمان تشغيل الخدمة أولاً
                if (!ensureServiceRunning()) {
                    Log.e(TAG, "❌ فشل في ضمان تشغيل الخدمة قبل التسجيل")
                    return@withContext false
                }

                // انتظار قصير للتأكد
                delay(1000)

                // بدء التسجيل
                withContext(Dispatchers.Main) {
                    val intent = Intent(this@MainActivity, AudioRecordingService::class.java).apply {
                        action = "START_RECORDING"
                    }
                    startService(intent)
                }

                Log.i(TAG, "🎙️ تم إرسال أمر بدء التسجيل")
                return@withContext true
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في بدء التسجيل", e)
                return@withContext false
            }
        }
    }

    private suspend fun handleStopRecording(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                if (AudioRecordingService.isServiceRunning) {
                    withContext(Dispatchers.Main) {
                        val intent = Intent(this@MainActivity, AudioRecordingService::class.java).apply {
                            action = "STOP_RECORDING"
                        }
                        startService(intent)
                    }
                    Log.i(TAG, "⏹️ تم إرسال أمر إيقاف التسجيل")
                    return@withContext true
                } else {
                    Log.w(TAG, "⚠️ الخدمة غير نشطة لإيقاف التسجيل")
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في إيقاف التسجيل", e)
                return@withContext false
            }
        }
    }

    private suspend fun getDetailedServiceStatus(): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                val isServiceRunning = AudioRecordingService.isServiceRunning
                val connectionInfo = if (isServiceRunning) {
                    getWebSocketConnectionInfo()
                } else {
                    mapOf(
                        "connected" to false,
                        "error" to "Service not running"
                    )
                }

                mapOf(
                    "service_running" to isServiceRunning,
                    "connection_info" to connectionInfo,
                    "permissions" to checkAllPermissions(),
                    "battery_optimized" to !isIgnoringBatteryOptimizations(),
                    "timestamp" to System.currentTimeMillis()
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في فحص حالة الخدمة", e)
                mapOf(
                    "service_running" to false,
                    "error" to (e.message ?: "Unknown error")
                )
            }
        }
    }

    private suspend fun handleListFiles(): Any {
        return withContext(Dispatchers.IO) {
            try {
                // دائماً جلب الملفات محلياً أولاً
                val files = getLocalFilesList()
                Log.d(TAG, "📁 تم جلب ${files.size} ملف محلياً")

                // إذا كانت الخدمة تعمل، أرسل طلب إضافي
                if (AudioRecordingService.isServiceRunning) {
                    withContext(Dispatchers.Main) {
                        val intent = Intent(this@MainActivity, AudioRecordingService::class.java).apply {
                            action = "LIST_FILES"
                        }
                        startService(intent)
                    }
                }

                return@withContext files
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في جلب قائمة الملفات", e)
                return@withContext emptyList<Map<String, Any>>()
            }
        }
    }

    // 🆕 دوال جديدة لفحص التخزين الخارجي
    private suspend fun handleScanExternalStorage(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "🗂️ طلب فحص بطاقة SD")

                // التحقق من الصلاحيات أولاً
                if (!hasStoragePermissions()) {
                    Log.w(TAG, "⚠️ لا توجد صلاحيات للوصول للتخزين الخارجي")
                    return@withContext false
                }

                if (AudioRecordingService.isServiceRunning) {
                    withContext(Dispatchers.Main) {
                        val intent = Intent(this@MainActivity, AudioRecordingService::class.java).apply {
                            action = "SCAN_SDCARD"
                        }
                        startService(intent)
                    }
                    Log.i(TAG, "📁 تم إرسال أمر فحص بطاقة SD")
                    return@withContext true
                } else {
                    Log.w(TAG, "⚠️ الخدمة غير نشطة لفحص بطاقة SD")
                    return@withContext false
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في فحص بطاقة SD", e)
                return@withContext false
            }
        }
    }

    private suspend fun handleGetStorageInfo(): Map<String, Any> {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "📊 جلب معلومات التخزين")

                val externalStorageManager = ExternalStorageManager(this@MainActivity)
                val storageInfo = externalStorageManager.getStorageInfo()

                Log.i(TAG, "✅ تم جلب معلومات التخزين بنجاح")
                storageInfo
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في جلب معلومات التخزين", e)
                mapOf("error" to (e.message ?: "Unknown error"))
            }
        }
    }

    // 🆕 دوال WebSocket الجديدة
    private suspend fun handleSendWebSocketMessage(message: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "📤 إرسال رسالة WebSocket: ${message.take(100)}...")

                val intent = Intent(this@MainActivity, AudioRecordingService::class.java).apply {
                    action = "SEND_WEBSOCKET_MESSAGE"
                    putExtra("message", message)
                }
                startService(intent)

                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في إرسال رسالة WebSocket", e)
                false
            }
        }
    }

    private suspend fun handleRequestFilesList(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "📋 طلب قائمة الملفات عبر WebSocket")

                val intent = Intent(this@MainActivity, AudioRecordingService::class.java).apply {
                    action = "REQUEST_FILES_LIST"
                }
                startService(intent)

                true
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في طلب قائمة الملفات", e)
                false
            }
        }
    }

    private fun getWebSocketConnectionInfo(): Map<String, Any> {
        return try {
            if (AudioRecordingService.isServiceRunning) {
                mapOf(
                    "connected" to false,
                    "url" to "ws://ws.sosa-qav.es",
                    "attempts" to 0,
                    "last_error" to "Service initializing...",
                    "network_available" to true
                )
            } else {
                mapOf(
                    "connected" to false,
                    "error" to "Service not running"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في جلب معلومات الاتصال", e)
            mapOf(
                "connected" to false,
                "error" to (e.message ?: "Unknown error")
            )
        }
    }

    private fun forceWebSocketReconnect(): Boolean {
        return try {
            if (AudioRecordingService.isServiceRunning) {
                val intent = Intent(this, AudioRecordingService::class.java).apply {
                    action = "CONNECT_WEBSOCKET"
                }
                startService(intent)
                Log.i(TAG, "🔄 تم إرسال أمر إعادة الاتصال")
                true
            } else {
                Log.w(TAG, "⚠️ لا يمكن إعادة الاتصال - الخدمة غير نشطة")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في إعادة الاتصال", e)
            false
        }
    }

    // 🆕 تحديث دالة فحص الصلاحيات
    private fun checkAllPermissions(): Map<String, Boolean> {
        return mapOf(
            "record_audio" to (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED),
            "read_external_storage" to (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED),
            "write_external_storage" to if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            } else true,
            "manage_external_storage" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else true,
            "notifications" to if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true,
            "network_state" to (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED),
            "battery_optimization" to isIgnoringBatteryOptimizations()
        )
    }

    // 🆕 فحص صلاحيات التخزين
    private fun hasStoragePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun getLocalFilesList(): List<Map<String, Any>> {
        return try {
            val allFiles = mutableListOf<Map<String, Any>>()
            val recordingsDir = File(applicationContext.filesDir, "Mictest_Recordings")

            if (recordingsDir.exists()) {
                val files = recordingsDir.listFiles { file ->
                    file.isFile && (file.extension == "aac" || file.extension == "wav" || file.extension == "mp3")
                }
                files?.let { fileList ->
                    fileList.sortedByDescending { it.lastModified() }.forEach { file ->
                        allFiles.add(mapOf(
                            "name" to file.name,
                            "path" to file.absolutePath,
                            "sizeInBytes" to file.length(),
                            "sizeInMB" to String.format("%.2f", file.length() / (1024.0 * 1024.0)),
                            "createdAt" to file.lastModified()
                        ))
                    }
                }
            }
            allFiles
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في جلب قائمة الملفات المحلية", e)
            emptyList()
        }
    }

    // 🆕 تحديث دالة طلب الصلاحيات
    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()

        // الصلاحيات الأساسية
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // 🆕 صلاحيات التخزين الخارجي
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.data = Uri.parse("package:$packageName")
                    startActivity(intent)
                    Log.i(TAG, "🗂️ تم فتح إعدادات إدارة الملفات")
                } catch (e: Exception) {
                    Log.e(TAG, "خطأ في طلب صلاحية إدارة الملفات", e)
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQUEST_CODE)
        }

        // 🆕 طلب تحسينات البطارية بعد تأخير
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isIgnoringBatteryOptimizations()) {
                requestIgnoreBatteryOptimizations()
            }
        }, 3000)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Log.i(TAG, "✅ تم منح جميع الصلاحيات")
                // إعادة محاولة بدء الخدمة
                mainScope.launch {
                    if (!AudioRecordingService.isServiceRunning) {
                        startEssentialServiceWithVerification()
                    }
                }
            } else {
                Log.w(TAG, "⚠️ لم يتم منح بعض الصلاحيات")
            }
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                Log.i(TAG, "🔋 تم فتح إعدادات تحسين البطارية")
            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في فتح إعدادات تحسين البطارية", e)
            }
        }
    }

    private fun requestAutoStartPermission() {
        try {
            val intent = Intent()
            val manufacturer = Build.MANUFACTURER.lowercase()

            Log.d(TAG, "📱 الشركة المصنعة: $manufacturer")

            when {
                manufacturer.contains("xiaomi") -> {
                    intent.component = ComponentName(
                        "com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"
                    )
                }
                manufacturer.contains("oppo") -> {
                    intent.component = ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"
                    )
                }
                manufacturer.contains("vivo") -> {
                    intent.component = ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                }
                else -> {
                    intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    intent.data = Uri.parse("package:$packageName")
                }
            }

            startActivity(intent)
            Log.i(TAG, "⚙️ تم فتح إعدادات التشغيل التلقائي")
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في فتح إعدادات التشغيل التلقائي", e)
        }
    }

    private fun deleteAudioFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                val deleted = file.delete()
                Log.i(TAG, if (deleted) "✅ تم حذف الملف: $filePath" else "❌ فشل حذف الملف: $filePath")
                deleted
            } else {
                Log.w(TAG, "⚠️ الملف غير موجود: $filePath")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "💥 خطأ في حذف الملف: $filePath", e)
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mainScope.cancel()
    }
}
