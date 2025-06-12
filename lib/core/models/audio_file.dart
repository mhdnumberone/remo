import 'dart:io';

class AudioFile {
  final String name;
  final String path;
  final int sizeInBytes;
  final DateTime createdAt;

  AudioFile({
    required this.name,
    required this.path,
    required this.sizeInBytes,
    required this.createdAt,
  });

  factory AudioFile.fromJson(Map<String, dynamic> json) {
    return AudioFile(
      name: json['name'] as String,
      path: json['path'] as String,
      sizeInBytes: json['sizeInBytes'] as int,
      createdAt: DateTime.fromMillisecondsSinceEpoch(json['createdAt'] as int),
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'name': name,
      'path': path,
      'sizeInBytes': sizeInBytes,
      'createdAt': createdAt.millisecondsSinceEpoch,
    };
  }

  String get formattedSize {
    if (sizeInBytes < 1024) return '$sizeInBytes B';
    if (sizeInBytes < 1024 * 1024) return '${(sizeInBytes / 1024).toStringAsFixed(1)} KB';
    if (sizeInBytes < 1024 * 1024 * 1024) return '${(sizeInBytes / (1024 * 1024)).toStringAsFixed(1)} MB';
    return '${(sizeInBytes / (1024 * 1024 * 1024)).toStringAsFixed(1)} GB';
  }

  String get formattedDate {
    return '${createdAt.day}/${createdAt.month}/${createdAt.year} ${createdAt.hour}:${createdAt.minute.toString().padLeft(2, '0')}';
  }
}
