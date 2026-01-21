import 'dart:async';

import 'package:shared_preferences/shared_preferences.dart';

import 'voip_engine.dart';
import 'voip_events.dart';

class FcmTokenManager {
  FcmTokenManager._();

  static final FcmTokenManager instance = FcmTokenManager._();

  static const _prefsTokenKey = 'fcm_token';
  static const _prefsUpdatedAtKey = 'fcm_token_updated_at';

  StreamSubscription<VoipEvent>? _sub;

  Future<void> init(VoipEngine engine) async {
    await _loadFromNative(engine);
    _sub ??= VoipEvents.stream.listen((event) async {
      if (event is FcmTokenEvent) {
        await _saveToken(event.token, event.updatedAt);
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
      if (token == null || token.isEmpty) return;
      await _saveToken(token, DateTime.now().millisecondsSinceEpoch);
    } catch (_) {
      // Ignore if native token not available yet.
    }
  }

  Future<void> _saveToken(String token, int updatedAt) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_prefsTokenKey, token);
    await prefs.setInt(_prefsUpdatedAtKey, updatedAt);
  }

  Future<void> dispose() async {
    await _sub?.cancel();
    _sub = null;
  }
}