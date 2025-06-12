package com.example.mictest

// FIX: Add the correct import for the @Serializable annotation
import kotlinx.serialization.Serializable

@Serializable
data class ExternalFile(
    val name: String,
    val path: String,
    val sizeInBytes: Long,
    val extension: String,
    val isDirectory: Boolean,
    val lastModified: Long,
    val parentPath: String
) {
    val formattedSize: String
        get() {
            return when {
                sizeInBytes < 1024 -> "$sizeInBytes B"
                sizeInBytes < 1024 * 1024 -> "${(sizeInBytes / 1024.0).format(1)} KB"
                sizeInBytes < 1024 * 1024 * 1024 -> "${(sizeInBytes / (1024.0 * 1024.0)).format(1)} MB"
                else -> "${(sizeInBytes / (1024.0 * 1024.0 * 1024.0)).format(1)} GB"
            }
        }

    val formattedDate: String
        get() {
            val date = java.util.Date(lastModified)
            val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            return format.format(date)
        }
}

@Serializable
data class SDCardScanResult(
    val totalFiles: Int,
    val totalDirectories: Int,
    val totalSize: Long,
    val scanDuration: Long,
    val timestamp: Long,
    val rootPath: String,
    val files: List<ExternalFile>
) {
    val formattedTotalSize: String
        get() {
            return when {
                totalSize < 1024 -> "$totalSize B"
                totalSize < 1024 * 1024 -> "${(totalSize / 1024.0).format(1)} KB"
                totalSize < 1024 * 1024 * 1024 -> "${(totalSize / (1024.0 * 1024.0)).format(1)} MB"
                else -> "${(totalSize / (1024.0 * 1024.0 * 1024.0)).format(1)} GB"
            }
        }
}

private fun Double.format(digits: Int) = "%.${digits}f".format(this)