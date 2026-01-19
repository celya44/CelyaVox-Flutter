import 'package:flutter/material.dart';

import '../main.dart';
import '../voip/voip_engine.dart';
import 'in_call_page.dart';
import 'incoming_call_page.dart';

class DialpadPage extends StatefulWidget {
  const DialpadPage({super.key, required this.engine});

  final VoipEngine engine;

  @override
  State<DialpadPage> createState() => _DialpadPageState();
}

class _DialpadPageState extends State<DialpadPage> {
  final TextEditingController _controller = TextEditingController();
  bool _isCalling = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _makeCall() async {
    final callee = _controller.text.trim();
    if (callee.isEmpty) {
      _showMessage('Entrez un numéro.');
      return;
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

  void _simulateIncomingCall() {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => IncomingCallPage(
          engine: widget.engine,
          callId: 'incoming-demo',
          callerId: '+33123456789',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Dialpad'),
        actions: [
          IconButton(
            tooltip: 'Simuler appel entrant',
            onPressed: _simulateIncomingCall,
            icon: const Icon(Icons.phone_callback_outlined),
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
            const Spacer(),
            ElevatedButton.icon(
              onPressed: _isCalling ? null : _makeCall,
              icon: const Icon(Icons.phone),
              label: Text(_isCalling ? 'Appel...' : 'Appeler'),
              style: ElevatedButton.styleFrom(
                minimumSize: const Size.fromHeight(48),
              ),
            ),
          ],
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
