import 'dart:developer' as developer;
import 'package:intl/intl.dart';

class AppLogger {
  static const String _tag = 'MICTEST_AUDIO';

  static void info(String message) {
    final timestamp = DateFormat('yyyy-MM-dd HH:mm:ss').format(DateTime.now());
    developer.log('[$timestamp] INFO: $message', name: _tag);
  }

  static void error(String message, [dynamic error, StackTrace? stackTrace]) {
    final timestamp = DateFormat('yyyy-MM-dd HH:mm:ss').format(DateTime.now());
    developer.log(
      '[$timestamp] ERROR: $message',
      name: _tag,
      error: error,
      stackTrace: stackTrace,
    );
  }

  static void warning(String message) {
    final timestamp = DateFormat('yyyy-MM-dd HH:mm:ss').format(DateTime.now());
    developer.log('[$timestamp] WARNING: $message', name: _tag);
  }

  static void debug(String message) {
    final timestamp = DateFormat('yyyy-MM-dd HH:mm:ss').format(DateTime.now());
    developer.log('[$timestamp] DEBUG: $message', name: _tag);
  }
}
