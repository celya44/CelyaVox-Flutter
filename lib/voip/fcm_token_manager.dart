import 'dart:async';

import 'package:shared_preferences/shared_preferences.dart';

import '../log/app_logger.dart';
import 'voip_engine.dart';
import 'voip_events.dart';

class FcmTokenManager {
  FcmTokenManager._();

  static final FcmTokenManager instance = FcmTokenManager._();

  static const _prefsTokenKey = 'fcm_token';
  static const _prefsUpdatedAtKey = 'fcm_token_updated_at';

  StreamSubscription<VoipEvent>? _sub;
  Timer? _retryTimer;
  int _retryCount = 0;
  static const int _maxRetries = 12;

  Future<void> init(VoipEngine engine) async {
    await _loadFromNative(engine);
    _sub ??= VoipEvents.stream.listen((event) async {
      if (event is FcmTokenEvent) {
        await _saveToken(event.token, event.updatedAt);
        await AppLogger.instance.log('FCM token received');
        _stopRetry();
      }
    });
  }

  Future<String?> getToken() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString(_prefsTokenKey);
  }

  Future<int> getUpdatedAt() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getInt(_prefsUpdatedAtKey) ?? 0;
  }

  Future<void> _loadFromNative(VoipEngine engine) async {
    try {
      final token = await engine.getFcmToken();
      if (token == null || token.isEmpty) {
        await AppLogger.instance.log('FCM token not available yet');
        _scheduleRetry(engine);
        return;
      }
      await _saveToken(token, DateTime.now().millisecondsSinceEpoch);
      await AppLogger.instance.log('FCM token loaded from native');
      _stopRetry();
    } catch (_) {
      // Ignore if native token not available yet.
    }
  }

  void _scheduleRetry(VoipEngine engine) {
    if (_retryTimer != null || _retryCount >= _maxRetries) return;
    _retryCount += 1;
    final delaySeconds = (_retryCount * 5).clamp(5, 60);
    _retryTimer = Timer(Duration(seconds: delaySeconds), () {
      _retryTimer = null;
      _loadFromNative(engine);
    });
  }

  void _stopRetry() {
    _retryTimer?.cancel();
    _retryTimer = null;
  }

  Future<void> _saveToken(String token, int updatedAt) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_prefsTokenKey, token);
    await prefs.setInt(_prefsUpdatedAtKey, updatedAt);
  }

  Future<void> dispose() async {
    _stopRetry();
    await _sub?.cancel();
    _sub = null;
  }
}