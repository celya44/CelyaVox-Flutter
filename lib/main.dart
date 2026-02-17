import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';

import 'provisioning/provisioning_page.dart';
import 'provisioning/provisioning_state.dart';
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
  bool _overlayPromptedThisSession = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    FcmTokenSync.instance.syncCachedToken();
    _ensureOverlayPermission();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      FcmTokenSync.instance.syncCachedToken();
      voipEngine.registerProvisioned();
      _ensureOverlayPermission();
    } else if (state == AppLifecycleState.inactive ||
        state == AppLifecycleState.paused ||
        state == AppLifecycleState.detached) {
      voipEngine.unregister();
    }
  }

  Future<void> _ensureOverlayPermission() async {
    if (kIsWeb) return;
    if (defaultTargetPlatform != TargetPlatform.android) return;
    if (_overlayPromptedThisSession) return;
    final canDraw = await voipEngine.canDrawOverlays();
    if (canDraw) return;
    _overlayPromptedThisSession = true;
    if (!mounted) return;
    final shouldOpen = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (dialogContext) => AlertDialog(
        title: const Text('Autorisation obligatoire'),
        content: const Text(
          "L'autorisation d'affichage par-dessus les autres applis est requise. "
          "Veuillez l'activer dans les reglages.",
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(dialogContext).pop(false),
            child: const Text('Plus tard'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(dialogContext).pop(true),
            child: const Text('Activer'),
          ),
        ],
      ),
    );
    if (shouldOpen == true) {
      await voipEngine.openOverlaySettings();
    }
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'VoIP Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
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
  }
}
