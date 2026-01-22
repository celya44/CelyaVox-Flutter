import 'package:flutter/material.dart';
import 'package:share_plus/share_plus.dart';

import '../log/app_logger.dart';
import '../provisioning/provisioning_channel.dart';
import '../provisioning/provisioning_page.dart';
import '../provisioning/provisioning_state.dart';

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  bool _loadingDump = false;
  bool _resetting = false;
  bool _exportingLogs = false;

  Future<void> _exportLogs() async {
    if (_exportingLogs) return;
    setState(() => _exportingLogs = true);
    try {
      final file = await AppLogger.instance.getLogFile();
      final box = context.findRenderObject() as RenderBox?;
      await Share.shareXFiles(
        [XFile(file.path)],
        text: 'Logs CelyaVox',
        sharePositionOrigin: box == null
            ? null
            : box.localToGlobal(Offset.zero) & box.size,
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Erreur export logs: $e')),
      );
    } finally {
      if (mounted) setState(() => _exportingLogs = false);
    }
  }

  Future<void> _showDump() async {
    setState(() => _loadingDump = true);
    try {
      final dump = await ProvisioningChannel.getProvisioningDump();
      if (!mounted) return;
      final entries = dump.entries.toList()
        ..sort((a, b) => a.key.compareTo(b.key));
      final text = entries.isEmpty
          ? 'Aucune donnée de provisioning.'
          : entries.map((e) => '${e.key}: ${e.value}').join('\n');
      await showDialog<void>(
        context: context,
        builder: (context) => AlertDialog(
          title: const Text('Dump provisioning'),
          content: SingleChildScrollView(
            child: SelectableText(text),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Fermer'),
            ),
          ],
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Erreur: $e')),
      );
    } finally {
      if (mounted) setState(() => _loadingDump = false);
    }
  }

  Future<void> _resetProvisioning() async {
    if (_resetting) return;
    final confirm = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Réinitialiser le provisioning'),
        content: const Text(
          'Cette action supprimera les variables de provisioning. Continuer ?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Annuler'),
          ),
          FilledButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Confirmer'),
          ),
        ],
      ),
    );

    if (confirm != true) return;

    setState(() => _resetting = true);
    try {
      await ProvisioningChannel.resetProvisioning();
      await ProvisioningState.setProvisioned(false);
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(builder: (_) => const ProvisioningPage()),
        (_) => false,
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('Erreur: $e')),
      );
    } finally {
      if (mounted) setState(() => _resetting = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Paramètres')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            FilledButton.icon(
              onPressed: _loadingDump ? null : _showDump,
              icon: const Icon(Icons.receipt_long),
              label: Text(_loadingDump ? 'Chargement...' : 'Dump provisioning'),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: _resetting ? null : _resetProvisioning,
              icon: const Icon(Icons.restart_alt),
              label: Text(_resetting ? 'Réinitialisation...' : 'Reset provisioning'),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: _exportingLogs ? null : _exportLogs,
              icon: const Icon(Icons.share),
              label: Text(_exportingLogs ? 'Export...' : 'Exporter les logs'),
            ),
          ],
        ),
      ),
    );
  }
}
