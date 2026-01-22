import 'dart:io';

import 'package:path_provider/path_provider.dart';

class AppLogger {
  AppLogger._();

  static final AppLogger instance = AppLogger._();

  File? _file;

  Future<void> init() async {
    if (_file != null) return;
    final dir = await getApplicationDocumentsDirectory();
    final file = File('${dir.path}/celyavox.log');
    if (!await file.exists()) {
      await file.create(recursive: true);
    }
    _file = file;
  }

  Future<void> log(String message) async {
    await init();
    final timestamp = DateTime.now().toIso8601String();
    await _file?.writeAsString('[$timestamp] $message\n', mode: FileMode.append);
  }

  Future<void> logApiRequest(String url, Map<String, String> params) async {
    await log('API REQUEST: $url params=${_maskParams(params)}');
  }

  Future<void> logApiResponse(String url, int statusCode, String body) async {
    final safeBody = body.length > 512 ? '${body.substring(0, 512)}…' : body;
    await log('API RESPONSE: $url status=$statusCode body=$safeBody');
  }

  Future<File> getLogFile() async {
    await init();
    return _file!;
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