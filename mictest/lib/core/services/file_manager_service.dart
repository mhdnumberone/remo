import '../utils/logger.dart';
import '../models/audio_file.dart';
import 'background_service.dart';
import 'websocket_service.dart';

class FileManagerService {
  static const String audioDirectory = 'Mictest_Recordings';

  static Future<List<AudioFile>> getAudioFiles() async {
    AppLogger.info('Requesting audio files from WebSocketService...');

    // طلب قائمة الملفات من الخدمة الأصلية
    await WebSocketService.instance.requestFilesList();
    await BackgroundServiceManager.listAudioFiles();

    // إرجاع قائمة فارغة والاعتماد على Stream للحصول على البيانات
    return [];
  }

  static Future<bool> deleteFile(String fileName) async {
    try {
      AppLogger.info('Requesting native to delete file: $fileName');
      final result = await BackgroundServiceManager.deleteAudioFile(fileName);
      return result == true;
    } catch (e) {
      AppLogger.error('Error deleting file via native: $fileName', e);
      return false;
    }
  }

  static Future<String> getStorageInfo() async {
    try {
      AppLogger.info('Requesting storage info...');
      return 'معلومات التخزين غير متوفرة حالياً من Flutter';
    } catch (e) {
      AppLogger.error('خطأ في جلب معلومات التخزين', e);
      return 'خطأ في جلب المعلومات';
    }
  }
}
