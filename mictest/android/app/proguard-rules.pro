# ✅ Kotlin specific rules
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# ✅ OkHttp specific rules
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ✅ WebSocket specific rules
-keep class * extends okhttp3.WebSocketListener { *; }
-keep class com.example.mictest.WebSocketManager { *; }

# ✅ Service specific rules
-keep class com.example.mictest.AudioRecordingService { *; }
-keep class com.example.mictest.MainActivity { *; }
-keep class com.example.mictest.NotificationHelper { *; }

# ✅ JSON parsing rules
-keep class com.google.gson.** { *; }
-keep class org.json.** { *; }

# ✅ Android specific rules
-keep class androidx.lifecycle.** { *; }
-keep class androidx.work.** { *; }

# ✅ Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# ✅ Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
