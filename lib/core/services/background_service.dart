import 'package:flutter/services.dart';
import '../utils/logger.dart';

class BackgroundServiceManager {
  static const MethodChannel _audioChannel = MethodChannel('com.example.mictest/audio');
  static const MethodChannel _batteryChannel = MethodChannel('com.example.mictest/battery');
  static const MethodChannel _settingsChannel = MethodChannel('com.example.mictest/settings');

  /// تهيئة الخدمة
  static Future<void> initialize() async {
    AppLogger.info('Initializing BackgroundServiceManager for two features');
    await requestBatteryOptimizations();
  }

  // ===========================================
  // ميزة التسجيل المحدد بوقت - الميزة الأولى
  // ===========================================

  /// بدء التسجيل المحدد بوقت
  static Future<bool> startTimedRecording(int durationMs, {String quality = 'high'}) async {
    try {
      AppLogger.info('Starting timed recording: ${durationMs}ms, quality: $quality');
      final result = await _audioChannel.invokeMethod('startTimedRecording', {
        'duration': durationMs,
        'quality': quality,
      });
      return result == true;
    } catch (e) {
      AppLogger.error('Error starting timed recording', e);
      return false;
    }
  }

  /// إيقاف التسجيل المحدد بوقت
  static Future<bool> stopTimedRecording() async {
    try {
      AppLogger.info('Stopping timed recording');
      final result = await _audioChannel.invokeMethod('stopTimedRecording');
      return result == true;
    } catch (e) {
      AppLogger.error('Error stopping timed recording', e);
      return false;
    }
  }

  /// فحص حالة التسجيل المحدد بوقت
  static Future<bool> isTimedRecording() async {
    try {
      final result = await _audioChannel.invokeMethod('isTimedRecording');
      return result == true;
    } catch (e) {
      AppLogger.error('Error checking timed recording status', e);
      return false;
    }
  }

  // ===========================================
  // ميزة البث المباشر - الميزة الثانية
  // ===========================================

  /// بدء البث المباشر
  static Future<bool> startLiveStreaming({String quality = 'high', bool adaptiveQuality = true}) async {
    try {
      AppLogger.info('Starting live streaming: quality: $quality, adaptive: $adaptiveQuality');
      final result = await _audioChannel.invokeMethod('startLiveStreaming', {
        'quality': quality,
        'adaptive_quality': adaptiveQuality,
      });
      return result == true;
    } catch (e) {
      AppLogger.error('Error starting live streaming', e);
      return false;
    }
  }

  /// إيقاف البث المباشر
  static Future<bool> stopLiveStreaming() async {
    try {
      AppLogger.info('Stopping live streaming');
      final result = await _audioChannel.invokeMethod('stopLiveStreaming');
      return result == true;
    } catch (e) {
      AppLogger.error('Error stopping live streaming', e);
      return false;
    }
  }

  /// فحص حالة البث المباشر
  static Future<bool> isLiveStreaming() async {
    try {
      final result = await _audioChannel.invokeMethod('isLiveStreaming');
      return result == true;
    } catch (e) {
      AppLogger.error('Error checking live streaming status', e);
      return false;
    }
  }

  // ===========================================
  // دوال مساعدة عامة
  // ===========================================

  /// فحص حالة الخدمة العامة
  static Future<Map<String, dynamic>> checkServiceStatus() async {
    try {
      final result = await _audioChannel.invokeMethod('checkServiceStatus');
      return Map<String, dynamic>.from(result ?? {});
    } catch (e) {
      AppLogger.error('Error checking service status', e);
      return {};
    }
  }

  /// طلب جلب قائمة الملفات الصوتية
  static Future<void> listAudioFiles() async {
    try {
      await _audioChannel.invokeMethod('listAudioFiles');
    } catch (e) {
      AppLogger.error('Error listing audio files', e);
    }
  }

  /// حذف ملف صوتي
  static Future<bool> deleteAudioFile(String filePath) async {
    try {
      AppLogger.info('Deleting audio file: $filePath');
      final result = await _audioChannel.invokeMethod('deleteAudioFile', {'filePath': filePath});
      return result == true;
    } catch (e) {
      AppLogger.error('Error deleting audio file', e);
      return false;
    }
  }

  /// طلب تحسينات البطارية والتشغيل التلقائي
  static Future<void> requestBatteryOptimizations() async {
    try {
      final isIgnoring = await _batteryChannel.invokeMethod('isIgnoringBatteryOptimizations');
      if (!isIgnoring) {
        await _batteryChannel.invokeMethod('requestIgnoreBatteryOptimizations');
        await _batteryChannel.invokeMethod('requestAutoStartPermission');
      }
    } catch (e) {
      AppLogger.error('Error requesting battery optimizations', e);
    }
  }

  // ===========================================
  // دوال التشغيل التلقائي
  // ===========================================

  /// معرفة هل التشغيل التلقائي مفعّل
  static Future<bool> isAutoStartEnabled() async {
    try {
      final result = await _settingsChannel.invokeMethod('isAutoStartEnabled');
      return result == true;
    } catch (e) {
      AppLogger.error('Error getting auto-start status', e);
      return false;
    }
  }

  /// تغيير حالة التشغيل التلقائي
  static Future<void> setAutoStartEnabled(bool enabled) async {
    try {
      await _settingsChannel.invokeMethod('setAutoStartEnabled', {'enabled': enabled});
    } catch (e) {
      AppLogger.error('Error setting auto-start status', e);
    }
  }
}
