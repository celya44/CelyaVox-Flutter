import 'dart:async';

import 'package:shared_preferences/shared_preferences.dart';

import '../api/celyavox_api.dart';
import '../provisioning/provisioning_channel.dart';
import '../log/app_logger.dart';
import 'fcm_token_manager.dart';
import 'voip_events.dart';
import 'voip_engine.dart';

class FcmTokenSync {
  FcmTokenSync._();

  static final FcmTokenSync instance = FcmTokenSync._();

  static const _prefsLastTokenKey = 'fcm_token_last_sent';
  static const _prefsLastSentAtKey = 'fcm_token_last_sent_at';

  final _api = CelyaVoxApiClient();
  StreamSubscription<VoipEvent>? _sub;

  Future<void> init(VoipEngine engine) async {
    await _maybeSendCachedToken();
    _sub ??= VoipEvents.stream.listen((event) {
      if (event is FcmTokenEvent) {
        _sendTokenIfNeeded(event.token);
      }
    });
  }

  Future<void> syncCachedToken() async {
    await _maybeSendCachedToken();
  }

  Future<void> _maybeSendCachedToken() async {
    final token = await FcmTokenManager.instance.getToken();
    if (token == null || token.isEmpty) return;
    await _sendTokenIfNeeded(token);
  }

  Future<void> _sendTokenIfNeeded(String token) async {
    final prefs = await SharedPreferences.getInstance();
    final lastToken = prefs.getString(_prefsLastTokenKey);
    if (lastToken == token) return;

    final domain = await ProvisioningChannel.getSipDomain();
    final apiKey = await ProvisioningChannel.getApiKey();
    if (domain == null || domain.isEmpty || apiKey == null || apiKey.isEmpty) {
      // Provisioning not ready; try again on next token event.
      await AppLogger.instance.log('FCM sync skipped: provisioning not ready');
      return;
    }

    final response = await _api.callProvisioned(
      apiClass: 'fcm',
      function: 'settoken',
      params: {
        'token_fcm': token,
      },
      includeExtension: true,
    );

    if (response.isOk) {
      await AppLogger.instance.log('FCM sync OK');
      await prefs.setString(_prefsLastTokenKey, token);
      await prefs.setInt(
        _prefsLastSentAtKey,
        DateTime.now().millisecondsSinceEpoch,
      );
    } else {
      await AppLogger.instance.log('FCM sync ERROR: ${response.message}');
    }
  }

  Future<void> dispose() async {
    await _sub?.cancel();
    _sub = null;
  }
}