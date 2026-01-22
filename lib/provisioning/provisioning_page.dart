import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';
import '../ui/dialpad_page.dart';
import '../voip/voip_engine.dart';
import '../voip/fcm_token_sync.dart';
import 'provisioning_channel.dart';
import 'provisioning_state.dart';

class ProvisioningPage extends StatefulWidget {
  const ProvisioningPage({super.key});

  @override
  State<ProvisioningPage> createState() => _ProvisioningPageState();
}

class _ProvisioningPageState extends State<ProvisioningPage> {
  final TextEditingController _urlController = TextEditingController();
  bool _isProvisioned = false;
  bool _submitting = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadState();
  }

  Future<void> _loadState() async {
    final provisioned = await ProvisioningState.isProvisioned();
    if (mounted) {
      setState(() {
        _isProvisioned = provisioned;
      });
    }
  }

  Future<void> _submit() async {
    final url = _urlController.text.trim();
    if (url.isEmpty) {
      setState(() => _error = 'Provisioning URL is required');
      return;
    }
    setState(() {
      _submitting = true;
      _error = null;
    });
    try {
      await ProvisioningChannel.startProvisioning(url);
      await ProvisioningState.setProvisioned(true);
      await FcmTokenSync.instance.syncCachedToken();
      if (!mounted) return;
      setState(() {
        _isProvisioned = true;
      });
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(
          builder: (_) => DialpadPage(engine: const VoipEngine()),
        ),
        (_) => false,
      );
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e.toString();
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _submitting = false;
        });
      }
    }
  }

  Future<void> _scanQr() async {
    if (_submitting) return;
    bool didScan = false;
    final scannedValue = await showDialog<String>(
      context: context,
      barrierDismissible: true,
      builder: (dialogContext) {
        return AlertDialog(
          contentPadding: EdgeInsets.zero,
          content: SizedBox(
            width: 320,
            height: 420,
            child: Stack(
              children: [
                MobileScanner(
                  onDetect: (capture) {
                    if (didScan) return;
                    final barcode = capture.barcodes.firstOrNull;
                    final value = barcode?.rawValue?.trim();
                    if (value == null || value.isEmpty) {
                      return;
                    }
                    didScan = true;
                    Navigator.of(dialogContext).pop(value);
                  },
                ),
                Positioned(
                  top: 12,
                  right: 12,
                  child: IconButton(
                    icon: const Icon(Icons.close),
                    onPressed: () => Navigator.of(dialogContext).pop(),
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );

    final value = scannedValue?.trim();
    if (value == null || value.isEmpty) {
      return;
    }
    _urlController.text = value;
    await _submit();
  }

  @override
  void dispose() {
    _urlController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    if (_isProvisioned) {
      return DialpadPage(engine: const VoipEngine());
    }

    return Scaffold(
      appBar: AppBar(title: const Text('Provisioning')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            TextField(
              controller: _urlController,
              decoration: const InputDecoration(
                labelText: 'Provisioning URL',
                hintText: 'https://example.com/provisioning.xml',
              ),
              keyboardType: TextInputType.url,
              autofillHints: const [AutofillHints.url],
            ),
            const SizedBox(height: 12),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Text(
                  _error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
            Row(
              children: [
                Expanded(
                  child: ElevatedButton(
                    onPressed: _submitting ? null : _submit,
                    child: _submitting
                        ? const SizedBox(
                            height: 20,
                            width: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          )
                        : const Text('Submit'),
                  ),
                ),
                const SizedBox(width: 12),
                ElevatedButton(
                  onPressed: _submitting ? null : _scanQr,
                  child: const Text('Scan QR'),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
