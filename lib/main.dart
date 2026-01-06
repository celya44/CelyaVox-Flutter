import 'package:flutter/material.dart';

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
      home: DialpadPage(engine: voipEngine),
    );
  }
}
