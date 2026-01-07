import 'package:flutter/services.dart';

class ProvisioningChannel {
  static const MethodChannel _channel = MethodChannel('com.celya.voip/provisioning');

  static Future<void> startProvisioning(String url) {
    return _channel.invokeMethod<void>('start', {'url': url});
  }
}
