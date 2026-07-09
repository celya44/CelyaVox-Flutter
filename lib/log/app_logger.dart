import 'dart:io';
import 'dart:developer' as developer;

import 'package:path_provider/path_provider.dart';

class AppLogger {
  AppLogger._();

  static final AppLogger instance = AppLogger._();

  File? _file;

  Future<void> init() async {
    if (_file != null) return;
    try {
      Directory dir;
      try {
        dir = await getApplicationDocumentsDirectory();
      } catch (_) {
        dir = await getTemporaryDirectory();
      }
      final file = File('${dir.path}/celyavox.log');
      if (!await file.exists()) {
        await file.create(recursive: true);
      }
      _file = file;
    } catch (_) {
      // Give up silently if storage is unavailable.
    }
  }

  Future<void> log(String message) async {
    try {
      await init();
      final timestamp = DateTime.now().toIso8601String();
      await _file?.writeAsString('[$timestamp] $message\n', mode: FileMode.append);
    } catch (_) {
      // Ignore logging errors.
    }
  }

  Future<void> logApiRequest(String url, Map<String, String> params) async {
    final logMsg = 'API REQUEST: $url params=${_maskParams(params)}';
    developer.log(logMsg, name: 'CelyaVoxApi');
    await log(logMsg);
  }

  Future<void> logApiResponse(String url, int statusCode, String body) async {
    final safeBody = body.length > 512 ? '${body.substring(0, 512)}…' : body;
    final logMsg = 'API RESPONSE: $url status=$statusCode body=$safeBody';
    developer.log(logMsg, name: 'CelyaVoxApi');
    await log(logMsg);
  }

  Future<File> getLogFile() async {
    await init();
    final file = _file;
    if (file == null) {
      throw StateError('Log file unavailable');
    }
    if (!await file.exists()) {
      await file.create(recursive: true);
    }
    return file;
  }

  Future<void> clearLogs() async {
    try {
      await init();
      if (_file != null && await _file!.exists()) {
        await _file!.delete();
        _file = null;
      }
    } catch (_) {
      // Ignore errors silently
    }
  }

  Map<String, String> _maskParams(Map<String, String> params) {
    final masked = <String, String>{};
    params.forEach((key, value) {
      if (key == 'api_key' || key == 'token_fcm' || key == 'token') {
        masked[key] = _maskValue(value);
      } else {
        masked[key] = value;
      }
    });
    return masked;
  }

  String _maskValue(String value) {
    if (value.length <= 8) return '***';
    return '${value.substring(0, 4)}***${value.substring(value.length - 4)}';
  }
}