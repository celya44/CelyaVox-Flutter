import 'package:flutter/material.dart';

import '../voip/voip_engine.dart';

class InCallPage extends StatefulWidget {
  const InCallPage({super.key, required this.engine, required this.callId});

  final VoipEngine engine;
  final String callId;

  @override
  State<InCallPage> createState() => _InCallPageState();
}

class _InCallPageState extends State<InCallPage> {
  bool _isHangingUp = false;

  Future<void> _hangup() async {
    setState(() => _isHangingUp = true);
    try {
      await widget.engine.hangupCall(widget.callId);
      if (!mounted) return;
      Navigator.of(context).popUntil((route) => route.isFirst);
    } catch (e) {
      _showMessage('Erreur: $e');
    } finally {
      if (mounted) {
        setState(() => _isHangingUp = false);
      }
    }
  }

  void _showMessage(String text) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(text)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Appel en cours')),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.call, size: 48),
            const SizedBox(height: 12),
            Text(
              widget.callId,
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: 24),
            ElevatedButton.icon(
              onPressed: _isHangingUp ? null : _hangup,
              icon: const Icon(Icons.call_end),
              label: Text(_isHangingUp ? 'Raccrochage...' : 'Raccrocher'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.redAccent,
                foregroundColor: Colors.white,
                minimumSize: const Size(200, 48),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
