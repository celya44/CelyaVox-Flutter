import 'dart:async';

import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import '../voip/voip_engine.dart';
import '../voip/voip_events.dart';
import 'dialpad_page.dart';

class InCallPage extends StatefulWidget {
  const InCallPage({super.key, required this.engine, required this.callId});

  final VoipEngine engine;
  final String callId;

  @override
  State<InCallPage> createState() => _InCallPageState();
}

class _InCallPageState extends State<InCallPage> {
  bool _isHangingUp = false;
  bool _speakerOn = false;
  bool _bluetoothOn = false;
  bool _muted = false;
  bool _showDtmf = false;
  bool _bluetoothAvailable = false;
  String _bluetoothName = '';
  String _activeCallId = '';
  StreamSubscription<VoipEvent>? _eventsSub;

  @override
  void initState() {
    super.initState();
    _activeCallId = widget.callId;
    _ensureMicPermission();
    _loadBluetoothAvailability();
    _listenCallUpdates();
  }

  @override
  void dispose() {
    _eventsSub?.cancel();
    super.dispose();
  }

  Future<void> _ensureMicPermission() async {
    final status = await Permission.microphone.request();
    if (!mounted) return;
    if (!status.isGranted) {
      _showMessage('Permission micro refusée. L’appel audio ne pourra pas démarrer.');
      return;
    }
    try {
      await widget.engine.refreshAudio();
    } catch (_) {
      _showMessage('Audio non initialisé. Redémarrez l’application.');
    }
  }

  Future<void> _loadBluetoothAvailability() async {
    try {
      final available = await widget.engine.isBluetoothAvailable();
      if (mounted) setState(() => _bluetoothAvailable = available);
    } catch (_) {
      if (mounted) setState(() => _bluetoothAvailable = false);
    }
  }

  void _listenCallUpdates() {
    _eventsSub = VoipEvents.stream.listen((event) {
      if (event is CallConnectedEvent) {
        if (mounted && event.callId.isNotEmpty) {
          setState(() => _activeCallId = event.callId);
        }
      } else if (event is CallEndedEvent) {
        if (!mounted) return;
        Navigator.of(context).pushAndRemoveUntil(
          MaterialPageRoute(
            builder: (_) => DialpadPage(engine: widget.engine),
          ),
          (_) => false,
        );
      } else if (event is BluetoothAvailabilityEvent) {
        if (!mounted) return;
        setState(() {
          _bluetoothAvailable = event.available;
          _bluetoothName = event.name;
          if (!event.available) {
            _bluetoothOn = false;
          }
        });
      }
    });
  }

  Future<void> _toggleSpeaker() async {
    final next = !_speakerOn;
    setState(() => _speakerOn = next);
    try {
      if (next && _bluetoothOn) {
        setState(() => _bluetoothOn = false);
        await widget.engine.setBluetooth(false);
      }
      await widget.engine.setSpeakerphone(next);
    } catch (e) {
      _showMessage('Erreur audio: $e');
    }
  }

  Future<void> _toggleBluetooth() async {
    if (!_bluetoothAvailable) return;
    final next = !_bluetoothOn;
    setState(() => _bluetoothOn = next);
    try {
      if (next && _speakerOn) {
        setState(() => _speakerOn = false);
        await widget.engine.setSpeakerphone(false);
      }
      await widget.engine.setBluetooth(next);
    } catch (e) {
      _showMessage('Erreur Bluetooth: $e');
    }
  }

  Future<void> _toggleMute() async {
    final next = !_muted;
    setState(() => _muted = next);
    try {
      await widget.engine.setMuted(next);
    } catch (e) {
      _showMessage('Erreur micro: $e');
    }
  }

  Future<void> _sendDtmf(String digit) async {
    if (_activeCallId.isEmpty) {
      _showMessage('CallId indisponible');
      return;
    }
    try {
      await widget.engine.sendDtmf(_activeCallId, digit);
    } catch (e) {
      _showMessage('DTMF échoué: $e');
    }
  }

  Future<void> _hangup() async {
    setState(() => _isHangingUp = true);
    final callId = _activeCallId.isNotEmpty ? _activeCallId : widget.callId;
    try {
      await widget.engine.hangupCall(callId);
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(
        MaterialPageRoute(
          builder: (_) => DialpadPage(engine: widget.engine),
        ),
        (_) => false,
      );
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
    return PopScope(
      canPop: false,
      child: Scaffold(
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
              Wrap(
                spacing: 12,
                runSpacing: 12,
                alignment: WrapAlignment.center,
                children: [
                  ElevatedButton.icon(
                    onPressed: _toggleSpeaker,
                    icon: Icon(_speakerOn ? Icons.volume_up : Icons.volume_mute),
                    label: Text(_speakerOn ? 'Haut-parleur' : 'Écouteur'),
                  ),
                  ElevatedButton.icon(
                    onPressed: _bluetoothAvailable ? _toggleBluetooth : null,
                    icon: const Icon(Icons.bluetooth_audio),
                    label: Text(
                      _bluetoothOn
                          ? (_bluetoothName.isNotEmpty ? _bluetoothName : 'Bluetooth')
                          : 'Bluetooth off',
                    ),
                  ),
                  ElevatedButton.icon(
                    onPressed: _toggleMute,
                    icon: Icon(_muted ? Icons.mic_off : Icons.mic),
                    label: Text(_muted ? 'Muet' : 'Micro'),
                  ),
                  OutlinedButton.icon(
                    onPressed: () => setState(() => _showDtmf = !_showDtmf),
                    icon: const Icon(Icons.dialpad),
                    label: Text(_showDtmf ? 'Masquer clavier' : 'Clavier'),
                  ),
                ],
              ),
              if (_showDtmf) ...[
                const SizedBox(height: 20),
                SizedBox(
                  width: 260,
                  child: GridView.count(
                    crossAxisCount: 3,
                    mainAxisSpacing: 8,
                    crossAxisSpacing: 8,
                    shrinkWrap: true,
                    physics: const NeverScrollableScrollPhysics(),
                    children: const [
                      '1','2','3','4','5','6','7','8','9','*','0','#'
                    ].map((digit) {
                      return ElevatedButton(
                        onPressed: () => _sendDtmf(digit),
                        child: Text(
                          digit,
                          style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                        ),
                      );
                    }).toList(),
                  ),
                ),
              ],
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
      ),
    );
  }
}
