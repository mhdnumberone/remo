package com.example.mictest

import android.content.Context
import android.os.Environment
import android.util.Log
import org.json.JSONObject
import java.io.File
import kotlinx.coroutines.*
import com.google.gson.GsonBuilder

class ExternalStorageManager(private val context: Context) {

    companion object {
        private const val TAG = "Mictest_ExternalStorage"
        private const val MAX_SCAN_DEPTH = 5
        private const val MAX_FILES_PER_SCAN = 5000
        private const val SKIP_SYSTEM_DIRS = true
    }

    private val scanScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val importantDirectories = arrayOf(
        "Download", "Downloads", "Pictures", "Music", "Movies", "Documents",
        "DCIM", "WhatsApp", "Telegram", "Instagram", "TikTok"
    )

    private val skipDirectories = arrayOf(
        "Android", ".android_secure", "lost+found", ".thumbnails",
        ".trash", "cache", ".cache", "temp", ".temp"
    )

    suspend fun scanExternalStorage(): SDCardScanResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            Log.i(TAG, "🔍 بدء فحص التخزين الخارجي المتقدم...")

            try {
                val externalStorageState = Environment.getExternalStorageState()
                if (externalStorageState != Environment.MEDIA_MOUNTED) {
                    Log.w(TAG, "⚠️ التخزين الخارجي غير متاح: $externalStorageState")
                    return@withContext createEmptyResult(startTime)
                }

                val rootDir = Environment.getExternalStorageDirectory()
                Log.i(TAG, "📁 مسار الجذر: ${rootDir.absolutePath}")

                val allFiles = mutableListOf<ExternalFile>()
                var totalDirectories = 0
                var totalSize = 0L

                scanImportantDirectories(
                    rootDir = rootDir,
                    filesList = allFiles,
                    totalSize = { size -> totalSize += size },
                    directoryCount = { totalDirectories++ }
                )

                if (allFiles.size < MAX_FILES_PER_SCAN * 0.8) {
                    scanRemainingDirectories(
                        rootDir = rootDir,
                        filesList = allFiles,
                        totalSize = { size -> totalSize += size },
                        directoryCount = { totalDirectories++ }
                    )
                }

                val scanDuration = System.currentTimeMillis() - startTime
                val sortedFiles = sortFilesByImportance(allFiles)

                val result = SDCardScanResult(
                    totalFiles = allFiles.count { !it.isDirectory },
                    totalDirectories = totalDirectories,
                    totalSize = totalSize,
                    scanDuration = scanDuration,
                    timestamp = System.currentTimeMillis(),
                    rootPath = rootDir.absolutePath,
                    files = sortedFiles.take(MAX_FILES_PER_SCAN)
                )

                Log.i(TAG, "✅ اكتمل الفحص - الملفات: ${result.totalFiles}, المجلدات: ${result.totalDirectories}")
                Log.i(TAG, "📊 الحجم الإجمالي: ${result.formattedTotalSize}, المدة: ${scanDuration}ms")

                result

            } catch (e: Exception) {
                Log.e(TAG, "❌ خطأ في فحص التخزين الخارجي", e)
                createEmptyResult(startTime)
            }
        }
    }

    private suspend fun scanImportantDirectories(
        rootDir: File,
        filesList: MutableList<ExternalFile>,
        totalSize: (Long) -> Unit,
        directoryCount: () -> Unit
    ) {
        Log.i(TAG, "🎯 فحص المجلدات المهمة...")

        for (dirName in importantDirectories) {
            if (filesList.size >= MAX_FILES_PER_SCAN) break

            val importantDir = File(rootDir, dirName)
            if (importantDir.exists() && importantDir.isDirectory()) {
                Log.d(TAG, "📂 فحص مجلد مهم: $dirName")
                scanDirectory(
                    directory = importantDir,
                    currentDepth = 0,
                    maxDepth = 3,
                    filesList = filesList,
                    totalSize = totalSize,
                    directoryCount = directoryCount
                )
            }
        }
    }

    private suspend fun scanRemainingDirectories(
        rootDir: File,
        filesList: MutableList<ExternalFile>,
        totalSize: (Long) -> Unit,
        directoryCount: () -> Unit
    ) {
        Log.i(TAG, "📁 فحص باقي المجلدات...")

        try {
            val files = rootDir.listFiles() ?: return

            for (file in files) {
                if (filesList.size >= MAX_FILES_PER_SCAN) break

                if (file.isDirectory() && !shouldSkipDirectory(file.name)) {
                    if (!importantDirectories.contains(file.name)) {
                        scanDirectory(
                            directory = file,
                            currentDepth = 0,
                            maxDepth = 2,
                            filesList = filesList,
                            totalSize = totalSize,
                            directoryCount = directoryCount
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في فحص باقي المجلدات", e)
        }
    }

    private suspend fun scanDirectory(
        directory: File,
        currentDepth: Int,
        maxDepth: Int,
        filesList: MutableList<ExternalFile>,
        totalSize: (Long) -> Unit,
        directoryCount: () -> Unit
    ) {
        if (currentDepth > maxDepth || filesList.size >= MAX_FILES_PER_SCAN) {
            return
        }

        try {
            if (!directory.exists() || !directory.canRead()) {
                return
            }

            val files = directory.listFiles() ?: return

            for (file in files) {
                try {
                    if (filesList.size >= MAX_FILES_PER_SCAN) break

                    if (file.name.startsWith(".") && SKIP_SYSTEM_DIRS) {
                        continue
                    }

                    val externalFile = ExternalFile(
                        name = file.name,
                        path = file.absolutePath,
                        sizeInBytes = if (file.isFile) file.length() else 0L,
                        extension = if (file.isFile) file.extension.lowercase() else "",
                        isDirectory = file.isDirectory,
                        lastModified = file.lastModified(),
                        parentPath = file.parent ?: ""
                    )

                    filesList.add(externalFile)

                    if (file.isDirectory) {
                        directoryCount()
                        if (currentDepth < maxDepth && !shouldSkipDirectory(file.name)) {
                            scanDirectory(
                                file,
                                currentDepth + 1,
                                maxDepth,
                                filesList,
                                totalSize,
                                directoryCount
                            )
                        }
                    } else {
                        totalSize(file.length())
                    }

                    if (filesList.size % 200 == 0) {
                        delay(50)
                    }

                } catch (e: SecurityException) {
                    Log.d(TAG, "⚠️ لا توجد صلاحية للوصول: ${file.absolutePath}")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ خطأ في معالجة الملف: ${file.absolutePath}", e)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في فحص المجلد: ${directory.absolutePath}", e)
        }
    }

    private fun shouldSkipDirectory(dirName: String): Boolean {
        val lowerName = dirName.lowercase()
        return skipDirectories.any { lowerName.contains(it) } || lowerName.startsWith(".") && SKIP_SYSTEM_DIRS
    }

    private fun sortFilesByImportance(files: List<ExternalFile>): List<ExternalFile> {
        val importantExtensions = setOf(
            "jpg", "jpeg", "png", "gif", "bmp",
            "mp4", "avi", "mkv", "mov", "wmv",
            "mp3", "wav", "aac", "flac", "m4a",
            "pdf", "doc", "docx", "txt", "rtf",
            "zip", "rar", "7z", "tar", "gz",
            "apk", "exe", "msi", "dmg"
        )

        return files.sortedWith(
            compareBy<ExternalFile> { !it.isDirectory }
                .thenByDescending { importantExtensions.contains(it.extension) }
                .thenByDescending { it.sizeInBytes }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun createEmptyResult(startTime: Long): SDCardScanResult {
        return SDCardScanResult(
            totalFiles = 0,
            totalDirectories = 0,
            totalSize = 0L,
            scanDuration = System.currentTimeMillis() - startTime,
            timestamp = System.currentTimeMillis(),
            rootPath = "unknown",
            files = emptyList()
        )
    }

    fun convertToOrderedJSON(scanResult: SDCardScanResult): String {
        return try {
            val gson = GsonBuilder()
                .setPrettyPrinting()
                .serializeNulls()
                .create()

            val orderedData = mapOf(
                "scan_summary" to mapOf(
                    "total_files" to scanResult.totalFiles,
                    "total_directories" to scanResult.totalDirectories,
                    "total_size_bytes" to scanResult.totalSize,
                    "total_size_formatted" to scanResult.formattedTotalSize,
                    "scan_duration_ms" to scanResult.scanDuration,
                    "timestamp" to scanResult.timestamp,
                    "root_path" to scanResult.rootPath
                ),
                "files" to scanResult.files.map { file ->
                    mapOf(
                        "name" to file.name,
                        "path" to file.path,
                        "size_bytes" to file.sizeInBytes,
                        "size_formatted" to file.formattedSize,
                        "extension" to file.extension,
                        "is_directory" to file.isDirectory,
                        "last_modified" to file.lastModified,
                        "last_modified_formatted" to file.formattedDate,
                        "parent_path" to file.parentPath
                    )
                }
            )

            gson.toJson(orderedData)

        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في تحويل البيانات إلى JSON", e)
            JSONObject().apply {
                put("error", "Failed to convert to JSON: ${e.message}")
                put("timestamp", System.currentTimeMillis())
            }.toString()
        }
    }

    fun getStorageInfo(): Map<String, Any> {
        return try {
            val externalDir = Environment.getExternalStorageDirectory()
            val totalSpace = externalDir.totalSpace
            val freeSpace = externalDir.freeSpace
            val usedSpace = totalSpace - freeSpace

            mapOf(
                "total_space_bytes" to totalSpace,
                "free_space_bytes" to freeSpace,
                "used_space_bytes" to usedSpace,
                "total_space_gb" to String.format("%.2f", totalSpace / (1024.0 * 1024.0 * 1024.0)),
                "free_space_gb" to String.format("%.2f", freeSpace / (1024.0 * 1024.0 * 1024.0)),
                "used_space_gb" to String.format("%.2f", usedSpace / (1024.0 * 1024.0 * 1024.0)),
                "usage_percentage" to String.format("%.1f", (usedSpace * 100.0) / totalSpace)
            )
        } catch (e: Exception) {
            Log.e(TAG, "❌ خطأ في جلب معلومات التخزين", e)
            // FIX: Handle potential null from e.message to prevent type mismatch
            mapOf("error" to (e.message ?: "Unknown error"))
        }
    }
}