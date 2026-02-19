import 'package:flutter/services.dart';

class ProvisioningChannel {
  static const MethodChannel _channel = MethodChannel('com.celya.voip/provisioning');

  static Future<void> startProvisioning(String url) {
    return _channel.invokeMethod<void>('start', {'url': url});
  }

  static Future<String?> getSipUsername() {
    return _channel.invokeMethod<String>('getSipUsername');
  }

  static Future<String?> getSipDomain() {
    return _channel.invokeMethod<String>('getSipDomain');
  }

  static Future<String?> getApiKey() {
    return _channel.invokeMethod<String>('getApiKey');
  }

  static Future<String?> getApiPrefixe() async {
    final dump = await getProvisioningDump();
    const candidateKeys = [
      'prefixe',
      'Prefixe',
      'prefix',
      'Prefix',
      'PrefixNumber',
      'prefix_number',
    ];

    for (final key in candidateKeys) {
      final value = dump[key]?.trim();
      if (value != null && value.isNotEmpty) {
        return value;
      }
    }
    return null;
  }

  static Future<Map<String, String>> getProvisioningDump() async {
    final result = await _channel.invokeMethod<Map<dynamic, dynamic>>(
      'getProvisioningDump',
    );
    if (result == null) return {};
    return result.map(
      (key, value) => MapEntry(
        key.toString(),
        value?.toString() ?? '',
      ),
    );
  }

  static Future<void> resetProvisioning() {
    return _channel.invokeMethod<void>('resetProvisioning');
  }
}
