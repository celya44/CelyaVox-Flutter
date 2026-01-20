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
