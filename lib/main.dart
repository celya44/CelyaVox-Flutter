import 'package:flutter/material.dart';

import 'provisioning/provisioning_page.dart';
import 'provisioning/provisioning_state.dart';
import 'ui/dialpad_page.dart';
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
