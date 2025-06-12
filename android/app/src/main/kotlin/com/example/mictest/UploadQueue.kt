package com.example.mictest

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages upload queue for temporary audio files
 * Ensures immediate upload and deletion of successfully uploaded files
 */
class UploadQueue(
    private val context: Context,
    private val webSocketManager: WebSocketManager
) {
    companion object {
        private const val TAG = "Mictest_UploadQueue"
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 5000L
        private const val QUEUE_PROCESS_INTERVAL = 2000L
    }

    private val uploadQueue = ConcurrentLinkedQueue<UploadTask>()
    private val isProcessing = AtomicBoolean(false)
    private val uploadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var processingJob: Job? = null

    data class UploadTask(
        val filePath: String,
        val fileName: String,
        val createdAt: Long,
        var attempts: Int = 0,
        var lastAttemptTime: Long = 0
    )

    init {
        startQueueProcessor()
    }

    fun addToQueue(filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG, "⚠️ الملف غير موجود للإضافة للقائمة: $filePath")
                return
            }

            val task = UploadTask(
                filePath = filePath,
                fileName = file.name,
                createdAt = System.currentTimeMillis()
            )

            uploadQueue.offer(task)
            Log.i(TAG, "📥 تم إضافة ملف لقائمة الرفع: ${file.name}")
            Log.d(TAG, "📊 حجم القائمة الحالي: ${uploadQueue.size}")

            // Trigger immediate processing
            triggerProcessing()

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في إضافة الملف للقائمة", e)
        }
    }

    private fun startQueueProcessor() {
        processingJob = uploadScope.launch {
            while (isActive) {
                try {
                    if (!uploadQueue.isEmpty() && webSocketManager.isConnected()) {
                        processQueue()
                    }
                    delay(QUEUE_PROCESS_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ خطأ في معالج القائمة", e)
                    delay(QUEUE_PROCESS_INTERVAL)
                }
            }
        }
    }

    fun processQueue() {
        if (!isProcessing.compareAndSet(false, true)) {
            Log.d(TAG, "🔄 معالجة القائمة جارية بالفعل")
            return
        }

        uploadScope.launch {
            try {
                Log.i(TAG, "🚀 بدء معالجة قائمة الرفع (${uploadQueue.size} ملف)")

                val iterator = uploadQueue.iterator()
                while (iterator.hasNext()) {
                    val task = iterator.next()

                    // Check if file still exists
                    val file = File(task.filePath)
                    if (!file.exists()) {
                        Log.w(TAG, "⚠️ الملف غير موجود، إزالة من القائمة: ${task.fileName}")
                        iterator.remove()
                        continue
                    }

                    // Check retry attempts
                    if (task.attempts >= MAX_RETRY_ATTEMPTS) {
                        Log.e(TAG, "❌ تجاوز الحد الأقصى للمحاولات: ${task.fileName}")
                        iterator.remove()
                        // Optionally move to failed files directory or delete
                        handleFailedUpload(task)
                        continue
                    }

                    // Check retry delay
                    val timeSinceLastAttempt = System.currentTimeMillis() - task.lastAttemptTime
                    if (task.attempts > 0 && timeSinceLastAttempt < RETRY_DELAY_MS) {
                        Log.d(TAG, "⏱️ انتظار قبل إعادة المحاولة: ${task.fileName}")
                        continue
                    }

                    // Attempt upload
                    val success = attemptUpload(task)
                    if (success) {
                        Log.i(TAG, "✅ تم رفع الملف بنجاح: ${task.fileName}")
                        iterator.remove()

                        // Delete file immediately after successful upload
                        val deleted = file.delete()
                        Log.i(TAG, "${if (deleted) "✅" else "❌"} حذف الملف بعد الرفع: ${task.fileName}")

                    } else {
                        task.attempts++
                        task.lastAttemptTime = System.currentTimeMillis()
                        Log.w(TAG, "❌ فشل رفع الملف (محاولة ${task.attempts}/${MAX_RETRY_ATTEMPTS}): ${task.fileName}")
                    }
                }

                Log.i(TAG, "🏁 انتهت معالجة القائمة (${uploadQueue.size} ملف متبقي)")

            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في معالجة القائمة", e)
            } finally {
                isProcessing.set(false)
            }
        }
    }

    private suspend fun attemptUpload(task: UploadTask): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "📤 محاولة رفع: ${task.fileName}")

                val file = File(task.filePath)
                val success = webSocketManager.uploadFileToServer(task.filePath)

                if (success) {
                    // Send confirmation to server
                    sendUploadConfirmation(task)
                }

                return@withContext success

            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في رفع الملف: ${task.fileName}", e)
                return@withContext false
            }
        }
    }

    private fun sendUploadConfirmation(task: UploadTask) {
        try {
            val confirmationMessage = JSONObject().apply {
                put("command", "upload_confirmation")
                put("filename", task.fileName)
                put("status", "success")
                put("upload_time", System.currentTimeMillis())
                put("attempts", task.attempts + 1)
                put("client_info", "Mictest-Android-Professional")
            }

            webSocketManager.sendMessage(confirmationMessage.toString())
            Log.d(TAG, "📋 تم إرسال تأكيد الرفع: ${task.fileName}")

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في إرسال تأكيد الرفع", e)
        }
    }

    private fun handleFailedUpload(task: UploadTask) {
        try {
            Log.w(TAG, "🗑️ معالجة الملف الفاشل: ${task.fileName}")

            val file = File(task.filePath)
            if (file.exists()) {
                // Option 1: Delete the file
                val deleted = file.delete()
                Log.w(TAG, "${if (deleted) "✅" else "❌"} حذف الملف الفاشل: ${task.fileName}")

                // Option 2: Move to failed directory (alternative implementation)
                // moveToFailedDirectory(file)
            }

            // Send failure notification to server
            sendFailureNotification(task)

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في معالجة الملف الفاشل", e)
        }
    }

    private fun sendFailureNotification(task: UploadTask) {
        try {
            val failureMessage = JSONObject().apply {
                put("command", "upload_failed")
                put("filename", task.fileName)
                put("attempts", task.attempts)
                put("reason", "Max retry attempts exceeded")
                put("timestamp", System.currentTimeMillis())
            }

            webSocketManager.sendMessage(failureMessage.toString())

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في إرسال إشعار الفشل", e)
        }
    }

    private fun triggerProcessing() {
        if (webSocketManager.isConnected() && !uploadQueue.isEmpty()) {
            uploadScope.launch {
                delay(500) // Small delay to batch multiple additions
                processQueue()
            }
        }
    }

    fun getQueueInfo(): Map<String, Any> {
        return mapOf(
            "queue_size" to uploadQueue.size,
            "is_processing" to isProcessing.get(),
            "files" to uploadQueue.map { task ->
                mapOf(
                    "filename" to task.fileName,
                    "attempts" to task.attempts,
                    "created_at" to task.createdAt,
                    "last_attempt" to task.lastAttemptTime
                )
            }
        )
    }

    fun clearQueue() {
        uploadQueue.clear()
        Log.i(TAG, "🧹 تم مسح قائمة الرفع")
    }

    fun getQueueSize(): Int = uploadQueue.size

    fun cleanup() {
        uploadScope.cancel()
        processingJob?.cancel()
        uploadQueue.clear()
        isProcessing.set(false)
    }
}
