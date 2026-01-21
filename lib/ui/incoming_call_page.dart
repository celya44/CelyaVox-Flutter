import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import '../voip/voip_engine.dart';
import 'in_call_page.dart';

class IncomingCallPage extends StatefulWidget {
  const IncomingCallPage({super.key, required this.engine, required this.callId, required this.callerId});

  final VoipEngine engine;
  final String callId;
  final String callerId;

  @override
  State<IncomingCallPage> createState() => _IncomingCallPageState();
}

class _IncomingCallPageState extends State<IncomingCallPage> {
  bool _isProcessing = false;

  Future<bool> _ensureMicPermission() async {
    final status = await Permission.microphone.request();
    if (!mounted) return false;
    if (!status.isGranted) {
      _showMessage('Permission micro refusée. L’appel audio ne pourra pas démarrer.');
      return false;
    }
    try {
      await widget.engine.refreshAudio();
    } catch (_) {
      _showMessage('Audio non initialisé. Redémarrez l’application.');
    }
    return true;
  }

  Future<void> _accept() async {
    setState(() => _isProcessing = true);
    try {
      final ok = await _ensureMicPermission();
      if (!ok) return;
      await widget.engine.acceptCall(widget.callId);
      if (!mounted) return;
      Navigator.of(context).pushReplacement(
        MaterialPageRoute(
          builder: (_) => InCallPage(
            engine: widget.engine,
            callId: widget.callId,
          ),
        ),
      );
    } catch (e) {
      _showMessage('Erreur: $e');
    } finally {
      if (mounted) {
        setState(() => _isProcessing = false);
      }
    }
  }

  Future<void> _decline() async {
    setState(() => _isProcessing = true);
    try {
      await widget.engine.hangupCall(widget.callId);
      if (!mounted) return;
      Navigator.of(context).pop();
    } catch (e) {
      _showMessage('Erreur: $e');
    } finally {
      if (mounted) {
        setState(() => _isProcessing = false);
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
      appBar: AppBar(title: const Text('Appel entrant')),
      body: Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.ring_volume, size: 64),
            const SizedBox(height: 16),
            Text(
              widget.callerId,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                ElevatedButton.icon(
                  onPressed: _isProcessing ? null : _accept,
                  icon: const Icon(Icons.call),
                  label: const Text('Accepter'),
                ),
                const SizedBox(width: 16),
                OutlinedButton.icon(
                  onPressed: _isProcessing ? null : _decline,
                  icon: const Icon(Icons.call_end),
                  label: const Text('Refuser'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
