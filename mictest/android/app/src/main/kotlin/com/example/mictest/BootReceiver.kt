package com.example.mictest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "Mictest_BootReceiver"
        private const val PREFS_NAME = "mictest_settings"
        private const val KEY_AUTO_START = "auto_start_enabled"

        // دالة مساعدة لتمكين/تعطيل التشغيل التلقائي
        fun setAutoStartEnabled(context: Context, enabled: Boolean) {
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
            Log.i(TAG, "تم تحديث إعداد التشغيل التلقائي: $enabled")
        }

        fun isAutoStartEnabled(context: Context): Boolean {
            val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_AUTO_START, false)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "تم استلام إشارة: ${intent.action}")

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REPLACED -> {
                Log.i(TAG, "تم إكمال إقلاع النظام أو تحديث التطبيق")

                // التحقق من إعدادات المستخدم للتشغيل التلقائي
                if (shouldAutoStart(context)) {
                    restartEssentialServices(context)
                } else {
                    Log.i(TAG, "التشغيل التلقائي معطل من قبل المستخدم")
                }
            }
        }
    }

    private fun shouldAutoStart(context: Context): Boolean {
        val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_START, false) // افتراضي: معطل
    }

    private fun restartEssentialServices(context: Context) {
        try {
            // بدء الخدمة بدون تشغيل التسجيل مباشرة
            val serviceIntent = Intent(context, AudioRecordingService::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            Log.i(TAG, "تم بدء AudioRecordingService بنجاح من BootReceiver")
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في إعادة تشغيل الخدمات من BootReceiver", e)
        }
    }
}
