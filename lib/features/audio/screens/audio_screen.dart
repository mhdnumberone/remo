import 'dart:async';
import 'package:flutter/material.dart';
import '../../../core/services/background_service.dart';
import '../../../core/services/websocket_service.dart';
import '../../../core/services/file_manager_service.dart';
import '../../../core/utils/logger.dart';
import '../../../core/models/audio_file.dart';

class AudioScreen extends StatefulWidget {
  @override
  _AudioScreenState createState() => _AudioScreenState();
}

class _AudioScreenState extends State<AudioScreen> {
  // حالات الميزتين
  bool _isTimedRecording = false;
  bool _isLiveStreaming = false;
  bool _isServiceRunning = false;
  bool _isConnected = false;

  // حالات إضافية
  List<AudioFile> _audioFiles = [];
  bool _isLoadingFiles = false;
  bool _isAutoStartEnabled = false;

  // إعدادات التسجيل المحدد بوقت
  int _timedRecordingDuration = 30; // بالثواني
  String _recordingQuality = 'high';
  bool _adaptiveQuality = true;

  // Subscriptions
  StreamSubscription? _connectionSubscription;
  StreamSubscription? _audioFilesSubscription;

  @override
  void initState() {
    super.initState();
    _checkServiceStatus();
    _listenToWebSocketConnection();
    _listenToAudioFiles();
    _loadAudioFiles();
    _checkAutoStartStatus();
  }

  @override
  void dispose() {
    _connectionSubscription?.cancel();
    _audioFilesSubscription?.cancel();
    super.dispose();
  }

  void _listenToWebSocketConnection() {
    _connectionSubscription = WebSocketService.instance.connectionStream.listen((isConnected) {
      if (mounted) {
        setState(() {
          _isConnected = isConnected;
        });
      }
    });
    _isConnected = WebSocketService.instance.isConnected;
  }

  void _listenToAudioFiles() {
    _audioFilesSubscription = WebSocketService.instance.audioFilesStream.listen((files) {
      if (mounted) {
        setState(() {
          _audioFiles = files;
          _isLoadingFiles = false;
        });
        AppLogger.info('تم تحديث قائمة الملفات: ${files.length} ملف');
      }
    });
  }

  Future<void> _checkServiceStatus() async {
    final statusMap = await BackgroundServiceManager.checkServiceStatus();
    if (mounted) {
      setState(() {
        _isServiceRunning = statusMap['service_running'] ?? false;
        _isTimedRecording = statusMap['timed_recording_active'] ?? false;
        _isLiveStreaming = statusMap['live_streaming_active'] ?? false;
      });
    }
    AppLogger.info('حالة الخدمة: خدمة=$_isServiceRunning، تسجيل محدد=$_isTimedRecording، بث مباشر=$_isLiveStreaming');
  }

  Future<void> _checkAutoStartStatus() async {
    final status = await BackgroundServiceManager.isAutoStartEnabled();
    if (mounted) {
      setState(() {
        _isAutoStartEnabled = status;
      });
    }
  }

  Future<void> _loadAudioFiles() async {
    setState(() {
      _isLoadingFiles = true;
    });
    try {
      await FileManagerService.getAudioFiles();
      AppLogger.info('تم طلب تحميل الملفات الصوتية');
    } catch (e) {
      AppLogger.error('خطأ في تحميل الملفات', e);
    } finally {
      Future.delayed(const Duration(milliseconds: 500), () {
        if (mounted) {
          setState(() {
            _isLoadingFiles = false;
          });
        }
      });
    }
  }

  // ===========================================
  // ميزة التسجيل المحدد بوقت - الميزة الأولى
  // ===========================================

  Future<void> _startTimedRecording() async {
    AppLogger.info('المستخدم طلب بدء التسجيل المحدد بوقت: ${_timedRecordingDuration} ثانية');

    final success = await BackgroundServiceManager.startTimedRecording(
      _timedRecordingDuration * 1000, // تحويل للميلي ثانية
      quality: _recordingQuality,
    );

    if (success) {
      setState(() {
        _isTimedRecording = true;
        _isServiceRunning = true;
      });
      _showSnackBar('تم بدء التسجيل المحدد بوقت: ${_timedRecordingDuration} ثانية', Colors.green);
    } else {
      _showSnackBar('فشل في بدء التسجيل المحدد بوقت', Colors.red);
    }
  }

  Future<void> _stopTimedRecording() async {
    AppLogger.info('المستخدم طلب إيقاف التسجيل المحدد بوقت');

    final success = await BackgroundServiceManager.stopTimedRecording();
    if (success) {
      setState(() {
        _isTimedRecording = false;
      });
      _showSnackBar('تم إيقاف التسجيل المحدد بوقت', Colors.blue);

      await Future.delayed(Duration(seconds: 2));
      _loadAudioFiles();
    } else {
      _showSnackBar('فشل في إيقاف التسجيل المحدد بوقت', Colors.red);
    }
  }

  // ===========================================
  // ميزة البث المباشر - الميزة الثانية
  // ===========================================

  Future<void> _startLiveStreaming() async {
    AppLogger.info('المستخدم طلب بدء البث المباشر');

    final success = await BackgroundServiceManager.startLiveStreaming(
      quality: _recordingQuality,
      adaptiveQuality: _adaptiveQuality,
    );

    if (success) {
      setState(() {
        _isLiveStreaming = true;
        _isServiceRunning = true;
      });
      _showSnackBar('تم بدء البث المباشر', Colors.green);
    } else {
      _showSnackBar('فشل في بدء البث المباشر', Colors.red);
    }
  }

  Future<void> _stopLiveStreaming() async {
    AppLogger.info('المستخدم طلب إيقاف البث المباشر');

    final success = await BackgroundServiceManager.stopLiveStreaming();
    if (success) {
      setState(() {
        _isLiveStreaming = false;
      });
      _showSnackBar('تم إيقاف البث المباشر', Colors.blue);
    } else {
      _showSnackBar('فشل في إيقاف البث المباشر', Colors.red);
    }
  }

  // ===========================================
  // دوال مساعدة
  // ===========================================

  Future<void> _toggleAutoStart(bool enabled) async {
    await BackgroundServiceManager.setAutoStartEnabled(enabled);
    setState(() {
      _isAutoStartEnabled = enabled;
    });
    _showSnackBar(
      enabled ? 'تم تفعيل التشغيل التلقائي' : 'تم تعطيل التشغيل التلقائي',
      Colors.blue,
    );
  }

  Future<void> _deleteFile(AudioFile file) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('تأكيد الحذف'),
        content: Text('هل تريد حذف الملف "${file.name}"؟'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: Text('إلغاء'),
          ),
          TextButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: Text('حذف'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      final success = await FileManagerService.deleteFile(file.path);
      if (success) {
        _showSnackBar('تم حذف الملف بنجاح', Colors.blue);
        _loadAudioFiles();
      } else {
        _showSnackBar('فشل في حذف الملف', Colors.red);
      }
    }
  }

  void _showSnackBar(String message, Color color) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: color,
        duration: Duration(seconds: 3),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text('Mictest Audio - ميزتان'),
        backgroundColor: Colors.blue,
        actions: [
          Container(
            padding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            margin: EdgeInsets.only(right: 8),
            decoration: BoxDecoration(
              color: _isConnected ? Colors.green.shade100 : Colors.red.shade100,
              borderRadius: BorderRadius.circular(20),
              border: Border.all(
                color: _isConnected ? Colors.green : Colors.red,
                width: 1,
              ),
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(
                  _isConnected ? Icons.wifi : Icons.wifi_off,
                  color: _isConnected ? Colors.green.shade700 : Colors.red.shade700,
                  size: 16,
                ),
                SizedBox(width: 6),
                Text(
                  _isConnected ? 'متصل' : 'غير متصل',
                  style: TextStyle(
                    color: _isConnected ? Colors.green.shade700 : Colors.red.shade700,
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
      body: Column(
        children: [
          // حالة الخدمة
          Container(
            padding: EdgeInsets.all(16),
            margin: EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: _isServiceRunning ? Colors.green.shade100 : Colors.grey.shade100,
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: _isServiceRunning ? Colors.green : Colors.grey,
                width: 2,
              ),
            ),
            child: Column(
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Icon(
                      _isServiceRunning ? Icons.radio_button_checked : Icons.radio_button_unchecked,
                      color: _isServiceRunning ? Colors.green : Colors.grey,
                      size: 24,
                    ),
                    SizedBox(width: 12),
                    Text(
                      _isServiceRunning ? 'الخدمة تعمل' : 'الخدمة متوقفة',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                        color: _isServiceRunning ? Colors.green.shade700 : Colors.grey.shade700,
                      ),
                    ),
                  ],
                ),
                SizedBox(height: 8),
                // مؤشرات الميزتين
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: [
                    _buildFeatureIndicator(
                      'تسجيل محدد بوقت',
                      _isTimedRecording,
                      Icons.timer,
                    ),
                    _buildFeatureIndicator(
                      'بث مباشر',
                      _isLiveStreaming,
                      Icons.broadcast_on_personal,
                    ),
                  ],
                ),
              ],
            ),
          ),

          // ===========================================
          // قسم التسجيل المحدد بوقت - الميزة الأولى
          // ===========================================
          Card(
            margin: EdgeInsets.all(16),
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.timer, color: Colors.orange),
                      SizedBox(width: 8),
                      Text(
                        'التسجيل المحدد بوقت',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  SizedBox(height: 16),

                  // مدة التسجيل
                  Row(
                    children: [
                      Text('المدة: '),
                      Expanded(
                        child: Slider(
                          value: _timedRecordingDuration.toDouble(),
                          min: 5,
                          max: 300,
                          divisions: 59,
                          label: '$_timedRecordingDuration ثانية',
                          onChanged: _isTimedRecording ? null : (value) {
                            setState(() {
                              _timedRecordingDuration = value.round();
                            });
                          },
                        ),
                      ),
                      Text('$_timedRecordingDuration ث'),
                    ],
                  ),

                  // أزرار التحكم
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      ElevatedButton.icon(
                        onPressed: (_isTimedRecording || _isLiveStreaming) ? null : _startTimedRecording,
                        icon: Icon(Icons.play_arrow),
                        label: Text('بدء التسجيل'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.orange,
                          foregroundColor: Colors.white,
                        ),
                      ),
                      ElevatedButton.icon(
                        onPressed: _isTimedRecording ? _stopTimedRecording : null,
                        icon: Icon(Icons.stop),
                        label: Text('إيقاف التسجيل'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.grey,
                          foregroundColor: Colors.white,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          // ===========================================
          // قسم البث المباشر - الميزة الثانية
          // ===========================================
          Card(
            margin: EdgeInsets.symmetric(horizontal: 16),
            child: Padding(
              padding: EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.broadcast_on_personal, color: Colors.red),
                      SizedBox(width: 8),
                      Text(
                        'البث المباشر',
                        style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                  SizedBox(height: 16),

                  // جودة البث
                  Row(
                    children: [
                      Text('الجودة: '),
                      DropdownButton<String>(
                        value: _recordingQuality,
                        onChanged: (_isTimedRecording || _isLiveStreaming) ? null : (value) {
                          setState(() {
                            _recordingQuality = value!;
                          });
                        },
                        items: [
                          DropdownMenuItem(value: 'low', child: Text('منخفضة')),
                          DropdownMenuItem(value: 'medium', child: Text('متوسطة')),
                          DropdownMenuItem(value: 'high', child: Text('عالية')),
                        ],
                      ),
                      SizedBox(width: 20),
                      Text('جودة متكيفة: '),
                      Switch(
                        value: _adaptiveQuality,
                        onChanged: (_isTimedRecording || _isLiveStreaming) ? null : (value) {
                          setState(() {
                            _adaptiveQuality = value;
                          });
                        },
                      ),
                    ],
                  ),

                  // أزرار التحكم
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      ElevatedButton.icon(
                        onPressed: (_isTimedRecording || _isLiveStreaming) ? null : _startLiveStreaming,
                        icon: Icon(Icons.live_tv),
                        label: Text('بدء البث'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.red,
                          foregroundColor: Colors.white,
                        ),
                      ),
                      ElevatedButton.icon(
                        onPressed: _isLiveStreaming ? _stopLiveStreaming : null,
                        icon: Icon(Icons.stop_circle),
                        label: Text('إيقاف البث'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.grey,
                          foregroundColor: Colors.white,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),

          SizedBox(height: 20),

          // التشغيل التلقائي
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 24.0),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  'التشغيل التلقائي عند إقلاع النظام',
                  style: TextStyle(fontSize: 16, color: Colors.black87),
                ),
                Switch(
                  value: _isAutoStartEnabled,
                  onChanged: _toggleAutoStart,
                  activeColor: Colors.blue,
                ),
              ],
            ),
          ),

          SizedBox(height: 10),

          // أزرار الخدمات الإضافية
          Padding(
            padding: EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                ElevatedButton.icon(
                  onPressed: _checkServiceStatus,
                  icon: Icon(Icons.refresh),
                  label: Text('فحص الحالة'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.blue,
                    foregroundColor: Colors.white,
                  ),
                ),
                ElevatedButton.icon(
                  onPressed: _loadAudioFiles,
                  icon: Icon(Icons.folder),
                  label: Text('تحديث الملفات'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.green,
                    foregroundColor: Colors.white,
                  ),
                ),
              ],
            ),
          ),

          SizedBox(height: 20),

          // قائمة الملفات
          Expanded(
            child: Container(
              margin: EdgeInsets.all(16),
              decoration: BoxDecoration(
                border: Border.all(color: Colors.grey.shade300),
                borderRadius: BorderRadius.circular(12),
              ),
              child: Column(
                children: [
                  Container(
                    padding: EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: Colors.grey.shade100,
                      borderRadius: BorderRadius.only(
                        topLeft: Radius.circular(12),
                        topRight: Radius.circular(12),
                      ),
                    ),
                    child: Row(
                      children: [
                        Icon(Icons.audiotrack, color: Colors.blue),
                        SizedBox(width: 8),
                        Text(
                          'الملفات المحفوظة (${_audioFiles.length})',
                          style: TextStyle(
                            fontSize: 18,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ],
                    ),
                  ),
                  Expanded(
                    child: _isLoadingFiles
                        ? Center(child: CircularProgressIndicator())
                        : _audioFiles.isEmpty
                        ? Center(
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          Icon(Icons.folder_open, size: 64, color: Colors.grey),
                          SizedBox(height: 16),
                          Text(
                            'لا توجد ملفات صوتية',
                            style: TextStyle(
                              fontSize: 16,
                              color: Colors.grey.shade600,
                            ),
                          ),
                        ],
                      ),
                    )
                        : ListView.builder(
                      itemCount: _audioFiles.length,
                      itemBuilder: (context, index) {
                        final file = _audioFiles[index];
                        return ListTile(
                          leading: Icon(Icons.audiotrack, color: Colors.blue),
                          title: Text(file.name),
                          subtitle: Text('${file.formattedSize} • ${file.formattedDate}'),
                          trailing: IconButton(
                            icon: Icon(Icons.delete, color: Colors.red),
                            onPressed: () => _deleteFile(file),
                          ),
                        );
                      },
                    ),
                  ),
                ],
              ),
            ),
          ),

          // مؤشر النشاط
          if (_isTimedRecording || _isLiveStreaming)
            Container(
              padding: EdgeInsets.all(16),
              child: Column(
                children: [
                  CircularProgressIndicator(),
                  SizedBox(height: 12),
                  Text(
                    _isTimedRecording
                        ? 'جاري التسجيل المحدد بوقت...\nسيتوقف تلقائياً بعد $_timedRecordingDuration ثانية'
                        : 'جاري البث المباشر...\nيتم إرسال الصوت مباشرة للخادم',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      fontSize: 16,
                      color: Colors.grey.shade600,
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildFeatureIndicator(String title, bool isActive, IconData icon) {
    return Container(
      padding: EdgeInsets.symmetric(horizontal: 12, vertical: 6),
      decoration: BoxDecoration(
        color: isActive ? Colors.green.shade200 : Colors.grey.shade200,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isActive ? Colors.green : Colors.grey,
          width: 1,
        ),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            icon,
            color: isActive ? Colors.green.shade700 : Colors.grey.shade700,
            size: 16,
          ),
          SizedBox(width: 4),
          Text(
            title,
            style: TextStyle(
              color: isActive ? Colors.green.shade700 : Colors.grey.shade700,
              fontSize: 12,
              fontWeight: FontWeight.bold,
            ),
          ),
        ],
      ),
    );
  }
}
