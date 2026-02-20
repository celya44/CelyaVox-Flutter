import 'dart:async';

import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ThemeController extends ChangeNotifier {
  static const String _prefsKey = 'theme_mode';

  ThemeMode _mode = ThemeMode.light;

  ThemeMode get mode => _mode;
  bool get isDark => _mode == ThemeMode.dark;

  void load() {
    unawaited(_loadFromPrefs());
  }

  void toggle() {
    final next = isDark ? ThemeMode.light : ThemeMode.dark;
    _setMode(next);
  }

  Future<void> _loadFromPrefs() async {
    final prefs = await SharedPreferences.getInstance();
    final value = prefs.getString(_prefsKey);
    final next = value == 'dark' ? ThemeMode.dark : ThemeMode.light;
    _setMode(next, persist: false);
  }

  void _setMode(ThemeMode next, {bool persist = true}) {
    if (_mode == next) return;
    _mode = next;
    notifyListeners();
    if (persist) {
      unawaited(_persist(next));
    }
  }

  Future<void> _persist(ThemeMode next) async {
    final prefs = await SharedPreferences.getInstance();
    final value = next == ThemeMode.dark ? 'dark' : 'light';
    await prefs.setString(_prefsKey, value);
  }
}

class ThemeControllerScope extends InheritedNotifier<ThemeController> {
  const ThemeControllerScope({
    super.key,
    required ThemeController controller,
    required Widget child,
  }) : super(notifier: controller, child: child);

  static ThemeController of(BuildContext context) {
    final scope = context.dependOnInheritedWidgetOfExactType<ThemeControllerScope>();
    assert(scope != null, 'ThemeControllerScope not found in widget tree');
    return scope!.notifier!;
  }
}
