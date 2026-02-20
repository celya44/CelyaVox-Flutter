import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';

import 'provisioning/provisioning_page.dart';
import 'provisioning/provisioning_state.dart';
import 'theme/theme_controller.dart';
import 'ui/dialpad_page.dart';
import 'log/app_logger.dart';
import 'voip/fcm_token_manager.dart';
import 'voip/fcm_token_sync.dart';
import 'voip/voip_engine.dart';

final VoipEngine voipEngine = const VoipEngine();

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await AppLogger.instance.init();
  await AppLogger.instance.log('App start');
  await FcmTokenManager.instance.init(voipEngine);
  await FcmTokenSync.instance.init(voipEngine);
  runApp(const VoipApp());
}

class VoipApp extends StatefulWidget {
  const VoipApp({super.key});

  @override
  State<VoipApp> createState() => _VoipAppState();
}

class _VoipAppState extends State<VoipApp> with WidgetsBindingObserver {
  final ThemeController _themeController = ThemeController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    FcmTokenSync.instance.syncCachedToken();
    _themeController.load();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      FcmTokenSync.instance.syncCachedToken();
      voipEngine.registerProvisioned();
    } else if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.detached) {
      voipEngine.unregister();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _themeController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ThemeControllerScope(
      controller: _themeController,
      child: AnimatedBuilder(
        animation: _themeController,
        builder: (context, _) {
          return MaterialApp(
            title: 'VoIP Demo',
            theme: ThemeData(
              colorScheme: ColorScheme.fromSeed(
                seedColor: Colors.blue,
                brightness: Brightness.light,
              ),
              useMaterial3: true,
            ),
            darkTheme: ThemeData(
              colorScheme: ColorScheme.fromSeed(
                seedColor: Colors.blue,
                brightness: Brightness.dark,
              ),
              useMaterial3: true,
            ),
            themeMode: _themeController.mode,
            home: FutureBuilder<bool>(
              future: ProvisioningState.isProvisioned(),
              builder: (context, snapshot) {
                if (snapshot.connectionState != ConnectionState.done) {
                  return const Scaffold(
                    body: Center(child: CircularProgressIndicator()),
                  );
                }
                final isProvisioned = snapshot.data ?? false;
                return isProvisioned
                    ? DialpadPage(engine: voipEngine)
                    : const ProvisioningPage();
              },
            ),
          );
        },
      ),
    );
  }
}
