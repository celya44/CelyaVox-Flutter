import 'package:shared_preferences/shared_preferences.dart';

class ProvisioningState {
  static const String _provisionedKey = 'is_provisioned';

  ProvisioningState._();

  static Future<bool> isProvisioned() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getBool(_provisionedKey) ?? false;
  }

  static Future<void> setProvisioned(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_provisionedKey, value);
  }

  static Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_provisionedKey);
  }
}
