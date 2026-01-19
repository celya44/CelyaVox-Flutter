import 'package:flutter/material.dart';

import 'provisioning/provisioning_page.dart';
import 'ui/dialpad_page.dart';
import 'voip/sip_config_state.dart';
import 'voip/voip_engine.dart';

final VoipEngine voipEngine = const VoipEngine();

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const VoipApp());
}

class VoipApp extends StatelessWidget {
  const VoipApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'VoIP Demo',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: FutureBuilder<bool>(
        future: SipConfigState.hasConfig(),
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Scaffold(
              body: Center(child: CircularProgressIndicator()),
            );
          }
          final hasConfig = snapshot.data ?? false;
          return hasConfig
              ? DialpadPage(engine: voipEngine)
              : const ProvisioningPage();
        },
      ),
    );
  }
}
