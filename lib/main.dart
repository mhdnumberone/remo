import 'package:flutter/material.dart';
import 'features/audio/screens/audio_screen.dart';
import 'core/services/websocket_service.dart';
import 'core/services/background_service.dart';
import 'core/utils/logger.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  AppLogger.info('تشغيل التطبيق');

  // تهيئة الخدمات
  await BackgroundServiceManager.initialize();

  // إنشاء instance من WebSocketService لتهيئته
  WebSocketService();

  runApp(MictestApp());
}

class MictestApp extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Mictest Audio Recorder',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        primarySwatch: Colors.blue,
        fontFamily: 'Arial',
      ),
      home: AudioScreen(),
    );
  }
}
