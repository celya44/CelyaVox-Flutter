import 'package:shared_preferences/shared_preferences.dart';

class SipConfigState {
  static const String _configuredKey = 'sip_configured';
  static const String _usernameKey = 'sip_username';
  static const String _domainKey = 'sip_domain';
  static const String _proxyKey = 'sip_proxy';

  SipConfigState._();

  static Future<bool> hasConfig() async {
    final prefs = await SharedPreferences.getInstance();
    final configured = prefs.getBool(_configuredKey);
    if (configured != null) return configured;
    final username = prefs.getString(_usernameKey);
    final domain = prefs.getString(_domainKey);
    return (username != null && username.trim().isNotEmpty) &&
        (domain != null && domain.trim().isNotEmpty);
  }

  static Future<void> saveConfig({
    required String username,
    required String domain,
    String? proxy,
  }) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_usernameKey, username);
    await prefs.setString(_domainKey, domain);
    if (proxy != null) {
      await prefs.setString(_proxyKey, proxy);
    }
    await prefs.setBool(_configuredKey, true);
  }

  static Future<void> setConfigured(bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_configuredKey, value);
  }

  static Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_configuredKey);
    await prefs.remove(_usernameKey);
    await prefs.remove(_domainKey);
    await prefs.remove(_proxyKey);
  }
}
