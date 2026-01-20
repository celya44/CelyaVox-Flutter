import 'package:flutter/material.dart';

import 'dart:async';

import 'package:permission_handler/permission_handler.dart';

import '../provisioning/provisioning_channel.dart';
import '../voip/voip_events.dart';
import '../voip/voip_engine.dart';
import 'in_call_page.dart';
import 'settings_page.dart';

class DialpadPage extends StatefulWidget {
  const DialpadPage({super.key, required this.engine});

  final VoipEngine engine;

  @override
  State<DialpadPage> createState() => _DialpadPageState();
}

class _DialpadPageState extends State<DialpadPage> {
  final TextEditingController _controller = TextEditingController();
  bool _isCalling = false;
  bool _isRegistered = false;
  String? _username;
  StreamSubscription<VoipEvent>? _eventsSub;

  @override
  void initState() {
    super.initState();
    _ensureMicPermission();
    _loadUsername();
    _listenRegistration();
    _registerOnStart();
  }

  @override
  void dispose() {
    _eventsSub?.cancel();
    _controller.dispose();
    super.dispose();
  }

  Future<void> _loadUsername() async {
    try {
      final value = await ProvisioningChannel.getSipUsername();
      if (!mounted) return;
      setState(() => _username = value?.trim().isNotEmpty == true ? value : null);
    } catch (_) {
      if (!mounted) return;
      setState(() => _username = null);
    }
  }

  Future<void> _ensureMicPermission() async {
    final status = await Permission.microphone.request();
    if (!mounted) return;
    if (!status.isGranted) {
      _showMessage('Permission micro refusée. L’appel audio ne pourra pas démarrer.');
    }
  }

  void _listenRegistration() {
    _eventsSub = VoipEvents.stream.listen((event) {
      if (event is RegistrationEvent) {
        final ok = event.statusText.contains('200');
        if (mounted) setState(() => _isRegistered = ok);
      }
    }, onError: (_) {
      if (mounted) setState(() => _isRegistered = false);
    });
  }

  Future<void> _registerOnStart() async {
    try {
      await widget.engine.registerProvisioned();
    } catch (_) {
      // Ignore errors here; registration status is reflected via events.
    }
  }

  Future<void> _makeCall() async {
    var callee = _controller.text.trim();
    if (callee.isEmpty) {
      _showMessage('Entrez un numéro.');
      return;
    }
    if (callee.startsWith('sip:')) {
      callee = callee.substring(4);
    }
    if (!callee.contains('@')) {
      final domain = await ProvisioningChannel.getSipDomain();
      if (domain != null && domain.trim().isNotEmpty) {
        callee = '$callee@${domain.trim()}';
      } else {
        _showMessage('Domaine SIP manquant, appel sans domaine.');
      }
    }
    setState(() => _isCalling = true);
    try {
      await widget.engine.makeCall(callee);
      if (!mounted) return;
      Navigator.of(context).push(
        MaterialPageRoute(
          builder: (_) => InCallPage(
            engine: widget.engine,
            callId: callee,
          ),
        ),
      );
    } catch (e) {
      _showMessage('Erreur: $e');
    } finally {
      if (mounted) {
        setState(() => _isCalling = false);
      }
    }
  }

  void _showMessage(String text) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(text)),
    );
  }

  void _appendDigit(String digit) {
    _controller.text += digit;
  }

  void _backspace() {
    final text = _controller.text;
    if (text.isNotEmpty) {
      _controller.text = text.substring(0, text.length - 1);
    }
  }

  @override
  Widget build(BuildContext context) {
    final displayName = _username ?? 'Compte';
    final statusColor = _isRegistered ? Colors.green : Colors.red;
    return Scaffold(
      appBar: AppBar(
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(
              child: Text(
                displayName,
                overflow: TextOverflow.ellipsis,
              ),
            ),
            const SizedBox(width: 8),
            Tooltip(
              message: _isRegistered ? 'Enregistré' : 'Non enregistré',
              child: Container(
                width: 10,
                height: 10,
                decoration: BoxDecoration(
                  color: statusColor,
                  shape: BoxShape.circle,
                ),
              ),
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Paramètres',
            onPressed: () {
              Navigator.of(context).push(
                MaterialPageRoute(
                  builder: (_) => const SettingsPage(),
                ),
              );
            },
            icon: const Icon(Icons.more_vert),
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              controller: _controller,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                labelText: 'Numéro',
              ),
              keyboardType: TextInputType.phone,
              readOnly: true,
            ),
            const SizedBox(height: 16),
            _Dialpad(onDigit: _appendDigit, onBackspace: _backspace),
          ],
        ),
      ),
      bottomNavigationBar: SafeArea(
        minimum: const EdgeInsets.all(16),
        child: ElevatedButton.icon(
          onPressed: _isCalling ? null : _makeCall,
          icon: const Icon(Icons.phone),
          label: Text(_isCalling ? 'Appel...' : 'Appeler'),
          style: ElevatedButton.styleFrom(
            minimumSize: const Size.fromHeight(48),
          ),
        ),
      ),
    );
  }
}

class _Dialpad extends StatelessWidget {
  const _Dialpad({required this.onDigit, required this.onBackspace});

  final ValueChanged<String> onDigit;
  final VoidCallback onBackspace;

  @override
  Widget build(BuildContext context) {
    const digits = ['1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#'];
    return Column(
      children: [
        for (var row = 0; row < 4; row++)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceEvenly,
              children: [
                for (var col = 0; col < 3; col++)
                  _DialButton(
                    label: digits[row * 3 + col],
                    onTap: () => onDigit(digits[row * 3 + col]),
                  ),
              ],
            ),
          ),
        Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            IconButton(
              onPressed: onBackspace,
              icon: const Icon(Icons.backspace_outlined),
            ),
          ],
        ),
      ],
    );
  }
}

class _DialButton extends StatelessWidget {
  const _DialButton({required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkResponse(
      onTap: onTap,
      radius: 32,
      child: Container(
        width: 72,
        height: 72,
        decoration: BoxDecoration(
          shape: BoxShape.circle,
          color: Theme.of(context).colorScheme.primaryContainer,
        ),
        alignment: Alignment.center,
        child: Text(
          label,
          style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
        ),
      ),
    );
  }
}
