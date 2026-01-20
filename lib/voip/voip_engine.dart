import 'package:flutter/services.dart';

/// Flutter-facing VoIP bridge using a platform MethodChannel.
class VoipEngine {
  const VoipEngine();

  static const MethodChannel _channel = MethodChannel('voip_engine');

  Future<void> init() => _invoke('init');

  Future<void> register(
    String username,
    String password,
    String domain,
    String proxy,
  ) =>
      _invoke('register', <String, dynamic>{
        'username': username,
        'password': password,
        'domain': domain,
        'proxy': proxy,
      });

  Future<void> registerProvisioned() => _invoke('registerProvisioned');

  Future<void> unregister() => _invoke('unregister');

  Future<void> makeCall(String callee) =>
      _invoke('makeCall', <String, dynamic>{'callee': callee});

  Future<void> acceptCall(String callId) =>
      _invoke('acceptCall', <String, dynamic>{'callId': callId});

  Future<void> hangupCall(String callId) =>
      _invoke('hangupCall', <String, dynamic>{'callId': callId});

  Future<void> _invoke(String method, [Map<String, dynamic>? arguments]) async {
    try {
      await _channel.invokeMethod<void>(method, arguments);
    } on PlatformException catch (e) {
      final message = e.message ?? e.code;
      throw Exception('VoIP method "$method" failed: $message');
    } catch (e) {
      throw Exception('VoIP method "$method" failed: $e');
    }
  }
}
