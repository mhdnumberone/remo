package com.example.mictest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.util.Log

/**
 * Professional Notification Helper for Audio Recording Service
 * Manages all notification types for the two main features:
 * 1. Timed Recording (التسجيل المحدد بوقت)
 * 2. Live Audio Streaming (البث المباشر للصوت)
 *
 * @author Mictest Team
 * @version 3.0.1 - Fixed Icons
 */
class NotificationHelper(private val context: Context) {

    companion object {
        private const val TAG = "Mictest_NotificationHelper"

        // Notification Channels
        private const val CHANNEL_ID_SERVICE = "mictest_service_channel"
        private const val CHANNEL_ID_RECORDING = "mictest_recording_channel"
        private const val CHANNEL_ID_STREAMING = "mictest_streaming_channel"
        private const val CHANNEL_ID_ALERTS = "mictest_alerts_channel"

        // Notification IDs
        const val NOTIFICATION_ID_SERVICE = 1001
        const val NOTIFICATION_ID_RECORDING = 1002
        const val NOTIFICATION_ID_STREAMING = 1003
        const val NOTIFICATION_ID_ALERT = 1004

        // Action IDs
        private const val ACTION_STOP_RECORDING = "action_stop_recording"
        private const val ACTION_STOP_STREAMING = "action_stop_streaming"
        private const val ACTION_OPEN_APP = "action_open_app"
        private const val ACTION_FORCE_STOP = "action_force_stop"
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
        Log.i(TAG, "NotificationHelper initialized with channel support")
    }

    // ================================
    // Notification Channels Setup
    // ================================

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createServiceChannel()
            createRecordingChannel()
            createStreamingChannel()
            createAlertsChannel()
            Log.i(TAG, "✅ تم إنشاء جميع قنوات الإشعارات")
        }
    }

    private fun createServiceChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_SERVICE,
                "حالة الخدمة",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "إشعارات حالة خدمة التسجيل الصوتي"
                enableLights(false)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createRecordingChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_RECORDING,
                "التسجيل المحدد بوقت",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات التسجيل المحدد بوقت والتسجيل العادي"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(false)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createStreamingChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_STREAMING,
                "البث المباشر",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "إشعارات البث المباشر للصوت"
                enableLights(true)
                lightColor = Color.BLUE
                enableVibration(false)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createAlertsChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_ALERTS,
                "تنبيهات مهمة",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "تنبيهات مهمة وأخطاء النظام"
                enableLights(true)
                lightColor = Color.YELLOW
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    // ================================
    // Service Status Notifications
    // ================================

    fun createIdleNotification(): Notification {
        val openAppIntent = createOpenAppIntent()

        return NotificationCompat.Builder(context, CHANNEL_ID_SERVICE)
            .setContentTitle("Mictest Audio Service")
            .setContentText("الخدمة تعمل - جاهزة للتسجيل المحدد بوقت أو البث المباشر")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setColor(ContextCompat.getColor(context, android.R.color.holo_blue_dark))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .addAction(createOpenAppAction())
            .addAction(createForceStopAction())
            .build()
    }

    // ================================
    // Timed Recording Notifications
    // ================================

    fun createRecordingNotification(): Notification {
        val openAppIntent = createOpenAppIntent()

        return NotificationCompat.Builder(context, CHANNEL_ID_RECORDING)
            .setContentTitle("🎙️ التسجيل نشط")
            .setContentText("جاري التسجيل الصوتي - اضغط لإيقاف")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .addAction(createStopRecordingAction())
            .addAction(createOpenAppAction())
            .setProgress(0, 0, true)
            .build()
    }

    fun createTimedRecordingNotification(durationMs: Long): Notification {
        val openAppIntent = createOpenAppIntent()
        val durationSeconds = durationMs / 1000

        return NotificationCompat.Builder(context, CHANNEL_ID_RECORDING)
            .setContentTitle("⏱️ التسجيل المحدد بوقت")
            .setContentText("جاري التسجيل لمدة $durationSeconds ثانية - سيتوقف تلقائياً")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .addAction(createStopTimedRecordingAction())
            .addAction(createOpenAppAction())
            .setProgress(100, 0, false)
            .setSubText("المدة: $durationSeconds ثانية")
            .build()
    }

    fun updateTimedRecordingProgress(
        notificationId: Int,
        elapsedMs: Long,
        totalMs: Long,
        remainingMs: Long
    ) {
        try {
            val progress = ((elapsedMs * 100) / totalMs).toInt()
            val remainingSeconds = remainingMs / 1000
            val totalSeconds = totalMs / 1000

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_RECORDING)
                .setContentTitle("⏱️ التسجيل المحدد بوقت")
                .setContentText("متبقي: $remainingSeconds ثانية من أصل $totalSeconds ثانية")
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setColor(ContextCompat.getColor(context, android.R.color.holo_orange_dark))
                .setContentIntent(createOpenAppIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setProgress(100, progress, false)
                .setSubText("التقدم: $progress%")
                .addAction(createStopTimedRecordingAction())
                .addAction(createOpenAppAction())
                .build()

            notificationManager.notify(notificationId, notification)
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في تحديث تقدم التسجيل المحدد بوقت", e)
        }
    }

    // ================================
    // Live Streaming Notifications
    // ================================

    fun createLiveStreamingNotification(): Notification {
        val openAppIntent = createOpenAppIntent()

        return NotificationCompat.Builder(context, CHANNEL_ID_STREAMING)
            .setContentTitle("📡 البث المباشر نشط")
            .setContentText("جاري بث الصوت مباشرة للخادم")
            .setSmallIcon(android.R.drawable.ic_menu_send) // تم التغيير
            .setColor(ContextCompat.getColor(context, android.R.color.holo_blue_bright))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(false)
            .addAction(createStopStreamingAction())
            .addAction(createOpenAppAction())
            .setProgress(0, 0, true)
            .setSubText("البث المباشر")
            .build()
    }

    fun createStreamingNotification(): Notification {
        return createLiveStreamingNotification()
    }

    fun updateLiveStreamingStats(
        notificationId: Int,
        chunksSent: Int,
        durationMs: Long,
        dataTransferred: String
    ) {
        try {
            val durationSeconds = durationMs / 1000
            val chunksPerSecond = if (durationSeconds > 0) chunksSent / durationSeconds else 0

            val notification = NotificationCompat.Builder(context, CHANNEL_ID_STREAMING)
                .setContentTitle("📡 البث المباشر نشط")
                .setContentText("تم إرسال $chunksSent جزء صوتي ($chunksPerSecond جزء/ثانية)")
                .setSmallIcon(android.R.drawable.ic_menu_send) // تم التغيير
                .setColor(ContextCompat.getColor(context, android.R.color.holo_blue_bright))
                .setContentIntent(createOpenAppIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setProgress(0, 0, true)
                .setSubText("البث: ${durationSeconds}s | البيانات: $dataTransferred")
                .addAction(createStopStreamingAction())
                .addAction(createOpenAppAction())
                .build()

            notificationManager.notify(notificationId, notification)
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في تحديث إحصائيات البث المباشر", e)
        }
    }

    // ================================
    // Alert Notifications
    // ================================

    fun createErrorNotification(title: String, message: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setContentTitle("❌ $title")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setColor(ContextCompat.getColor(context, android.R.color.holo_red_light))
            .setContentIntent(createOpenAppIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .addAction(createOpenAppAction())
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .build()
    }

    fun createSuccessNotification(title: String, message: String): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setContentTitle("✅ $title")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setColor(ContextCompat.getColor(context, android.R.color.holo_green_light))
            .setContentIntent(createOpenAppIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .addAction(createOpenAppAction())
            .build()
    }

    fun createUploadCompleteNotification(filesCount: Int, successCount: Int): Notification {
        val title = if (successCount == filesCount) "اكتمل الرفع بنجاح" else "اكتمل الرفع مع أخطاء"
        val message = "تم رفع $successCount من أصل $filesCount ملف"

        return NotificationCompat.Builder(context, CHANNEL_ID_ALERTS)
            .setContentTitle("📤 $title")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
            .setContentIntent(createOpenAppIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .addAction(createOpenAppAction())
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "تفاصيل الرفع:\n" +
                            "• الملفات المرفوعة: $successCount\n" +
                            "• الملفات الفاشلة: ${filesCount - successCount}\n" +
                            "• إجمالي الملفات: $filesCount"
                )
            )
            .build()
    }

    // ================================
    // Pending Intents Factory
    // ================================

    private fun createOpenAppIntent(): PendingIntent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().apply {
                setClassName(context, "${context.packageName}.MainActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createStopRecordingIntent(): PendingIntent {
        val intent = Intent(context, AudioRecordingService::class.java).apply {
            action = "STOP_RECORDING"
        }

        return PendingIntent.getService(
            context,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createStopTimedRecordingIntent(): PendingIntent {
        val intent = Intent(context, AudioRecordingService::class.java).apply {
            action = "STOP_TIMED_RECORDING"
        }

        return PendingIntent.getService(
            context,
            2,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createStopStreamingIntent(): PendingIntent {
        val intent = Intent(context, AudioRecordingService::class.java).apply {
            action = "STOP_LIVE_STREAM"
        }

        return PendingIntent.getService(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createForceStopIntent(): PendingIntent {
        val intent = Intent(context, AudioRecordingService::class.java).apply {
            action = "FORCE_STOP_SERVICE"
        }

        return PendingIntent.getService(
            context,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ================================
    // Notification Actions Factory
    // ================================

    private fun createOpenAppAction(): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "فتح التطبيق",
            createOpenAppIntent()
        ).build()
    }

    private fun createStopRecordingAction(): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, // تم التغيير
            "إيقاف التسجيل",
            createStopRecordingIntent()
        ).build()
    }

    private fun createStopTimedRecordingAction(): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, // تم التغيير
            "إيقاف التسجيل المحدد",
            createStopTimedRecordingIntent()
        ).build()
    }

    private fun createStopStreamingAction(): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, // تم التغيير
            "إيقاف البث",
            createStopStreamingIntent()
        ).build()
    }

    private fun createForceStopAction(): NotificationCompat.Action {
        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_delete,
            "إيقاف إجباري",
            createForceStopIntent()
        ).build()
    }

    // ================================
    // Notification Management
    // ================================

    fun showNotification(id: Int, notification: Notification) {
        try {
            notificationManager.notify(id, notification)
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في عرض الإشعار $id", e)
        }
    }

    fun cancelNotification(id: Int) {
        try {
            notificationManager.cancel(id)
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في إلغاء الإشعار $id", e)
        }
    }

    fun cancelAllNotifications() {
        try {
            notificationManager.cancelAll()
            Log.i(TAG, "تم إلغاء جميع الإشعارات")
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في إلغاء جميع الإشعارات", e)
        }
    }

    fun areNotificationsEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            notificationManager.areNotificationsEnabled()
        } else {
            true
        }
    }

    fun getChannelImportance(channelId: String): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.getNotificationChannel(channelId)?.importance
                ?: NotificationManager.IMPORTANCE_NONE
        } else {
            NotificationManager.IMPORTANCE_DEFAULT
        }
    }

    // ================================
    // Helper Functions
    // ================================

    private fun createBaseNotificationBuilder(channelId: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(context, channelId)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setLocalOnly(true)
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) {
            "${minutes}د ${seconds}ث"
        } else {
            "${seconds}ث"
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${String.format("%.1f", bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", bytes / (1024.0 * 1024.0))} MB"
            else -> "${String.format("%.1f", bytes / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    private fun getRecordingIcon(isTimedRecording: Boolean, isLiveStreaming: Boolean): Int {
        return when {
            isLiveStreaming -> android.R.drawable.ic_menu_send // تم التغيير
            isTimedRecording -> android.R.drawable.ic_menu_recent_history
            else -> android.R.drawable.ic_media_play
        }
    }

    private fun getRecordingColor(isTimedRecording: Boolean, isLiveStreaming: Boolean): Int {
        return when {
            isLiveStreaming -> ContextCompat.getColor(context, android.R.color.holo_blue_bright)
            isTimedRecording -> ContextCompat.getColor(context, android.R.color.holo_orange_dark)
            else -> ContextCompat.getColor(context, android.R.color.holo_red_dark)
        }
    }
}