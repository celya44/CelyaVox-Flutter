import 'package:flutter/material.dart';
import 'voip_engine.dart';

class SipConfigForm extends StatefulWidget {
  const SipConfigForm({super.key, required this.engine});

  final VoipEngine engine;

  @override
  State<SipConfigForm> createState() => _SipConfigFormState();
}

class _SipConfigFormState extends State<SipConfigForm> {
  final _formKey = GlobalKey<FormState>();
  final _usernameCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();
  final _domainCtrl = TextEditingController();
  final _proxyCtrl = TextEditingController();

  bool _isSubmitting = false;

  @override
  void dispose() {
    _usernameCtrl.dispose();
    _passwordCtrl.dispose();
    _domainCtrl.dispose();
    _proxyCtrl.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    final form = _formKey.currentState;
    if (form == null || !form.validate()) return;

    setState(() => _isSubmitting = true);
    try {
      await widget.engine.register(
        _usernameCtrl.text.trim(),
        _passwordCtrl.text.trim(),
        _domainCtrl.text.trim(),
        _proxyCtrl.text.trim(),
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Enregistrement SIP demandé.')),
      );
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Erreur: $e')),
      );
    } finally {
      if (mounted) setState(() => _isSubmitting = false);
    }
  }

  String? _required(String? value) {
    if (value == null || value.trim().isEmpty) return 'Requis';
    return null;
  }

  @override
  Widget build(BuildContext context) {
    return Form(
      key: _formKey,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          TextFormField(
            controller: _usernameCtrl,
            decoration: const InputDecoration(labelText: 'Username'),
            validator: _required,
          ),
          TextFormField(
            controller: _passwordCtrl,
            decoration: const InputDecoration(labelText: 'Password'),
            obscureText: true,
            validator: _required,
          ),
          TextFormField(
            controller: _domainCtrl,
            decoration: const InputDecoration(labelText: 'Domain'),
            validator: _required,
          ),
          TextFormField(
            controller: _proxyCtrl,
            decoration: const InputDecoration(labelText: 'Proxy (optionnel)'),
          ),
          const SizedBox(height: 12),
          FilledButton.icon(
            onPressed: _isSubmitting ? null : _submit,
            icon: const Icon(Icons.cloud_done),
            label: Text(_isSubmitting ? 'Enregistrement...' : 'Enregistrer'),
          ),
        ],
      ),
    );
  }
}
