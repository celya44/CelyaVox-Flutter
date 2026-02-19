import 'package:flutter/material.dart';

import 'dart:async';

import 'package:permission_handler/permission_handler.dart';

import '../api/celyavox_api.dart';
import '../provisioning/provisioning_channel.dart';
import '../voip/voip_events.dart';
import '../voip/voip_engine.dart';
import 'in_call_page.dart';
import 'settings_page.dart';
import 'incoming_call_page.dart';

class DialpadPage extends StatefulWidget {
  const DialpadPage({super.key, required this.engine});

  final VoipEngine engine;

  @override
  State<DialpadPage> createState() => _DialpadPageState();
}

class _DialpadPageState extends State<DialpadPage> {
  final TextEditingController _controller = TextEditingController();
  final TextEditingController _contactSearchController = TextEditingController();
  final CelyaVoxApiClient _apiClient = CelyaVoxApiClient();
  bool _isCalling = false;
  bool _isRegistered = false;
  bool _isOpeningInCall = false;
  bool _showContactSearch = false;
  bool _isSearchingContacts = false;
  int _selectedIndex = 2;
  String? _username;
  String? _contactsError;
  String? _dialpadSearchError;
  List<Map<String, dynamic>> _contactResults = const [];
  List<Map<String, dynamic>> _dialpadSearchResults = const [];
  StreamSubscription<VoipEvent>? _eventsSub;
  Timer? _contactSearchDebounce;
  Timer? _dialpadSearchDebounce;
  int _contactSearchRequestId = 0;
  int _dialpadSearchRequestId = 0;
  bool _isSearchingDialpad = false;

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
    _contactSearchDebounce?.cancel();
    _dialpadSearchDebounce?.cancel();
    _controller.dispose();
    _contactSearchController.dispose();
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
      return;
    }
    try {
      await widget.engine.refreshAudio();
    } catch (_) {
      _showMessage('Audio non initialisé. Redémarrez l’application.');
    }
  }

  void _listenRegistration() {
    _eventsSub = VoipEvents.stream.listen((event) {
      if (event is RegistrationEvent) {
        final ok = event.statusText.contains('200');
        if (mounted) setState(() => _isRegistered = ok);
      } else if (event is CallConnectedEvent) {
        if (!mounted || _isOpeningInCall) return;
        if (event.callId.isEmpty) return;
        _isOpeningInCall = true;
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => InCallPage(
              engine: widget.engine,
              callId: event.callId,
            ),
          ),
        ).then((_) {
          _isOpeningInCall = false;
        });
      } else if (event is IncomingCallEvent) {
        if (!mounted) return;
        Navigator.of(context).push(
          MaterialPageRoute(
            builder: (_) => IncomingCallPage(
              engine: widget.engine,
              callId: event.callId,
              callerId: event.callerId,
            ),
          ),
        );
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
      _onDialpadSearchChanged(_controller.text);
    }
  }

  void _onDialpadSearchChanged(String value) {
    _dialpadSearchDebounce?.cancel();
    _dialpadSearchDebounce = Timer(const Duration(milliseconds: 300), () {
      final query = value.trim();
      if (query.length < 3) {
        if (!mounted) return;
        setState(() {
          _isSearchingDialpad = false;
          _dialpadSearchError = null;
          _dialpadSearchResults = const [];
        });
        return;
      }
      _searchDialpadContacts(query);
    });
  }

  Future<void> _searchDialpadContacts(String query) async {
    final requestId = ++_dialpadSearchRequestId;
    setState(() {
      _isSearchingDialpad = true;
      _dialpadSearchError = null;
      _dialpadSearchResults = const [];
    });

    try {
      final response = await _apiClient.callProvisionedEndpoint(
        endpoint: 'ldap/contacts',
        params: {'sn': query},
        includeExtension: false,
      );

      if (!mounted) return;
      if (requestId != _dialpadSearchRequestId) return;

      if (!response.isOk) {
        setState(() {
          _dialpadSearchError = response.message.isEmpty ? 'Erreur API' : response.message;
          _isSearchingDialpad = false;
        });
        return;
      }

      final decodedData = response.decodedData;
      final contacts = <Map<String, dynamic>>[];
      if (decodedData is List) {
        for (final item in decodedData) {
          if (item is Map) {
            contacts.add(item.map((k, v) => MapEntry(k.toString(), v)));
          }
        }
      }

      setState(() {
        _dialpadSearchResults = contacts;
        _isSearchingDialpad = false;
      });
    } catch (e) {
      if (!mounted) return;
      if (requestId != _dialpadSearchRequestId) return;
      setState(() {
        _dialpadSearchError = 'Erreur: $e';
        _isSearchingDialpad = false;
      });
    }
  }

  void _selectDialpadContact(Map<String, dynamic> contact) {
    final number = contact['telephoneNumber']?.toString().trim() ?? '';
    if (number.isNotEmpty) {
      _controller.text = number;
      _controller.selection = TextSelection.fromPosition(
        TextPosition(offset: _controller.text.length),
      );
    }
    FocusScope.of(context).unfocus();
    setState(() {
      _dialpadSearchResults = const [];
      _dialpadSearchError = null;
      _isSearchingDialpad = false;
    });
  }

  void _toggleContactSearch() {
    setState(() {
      _showContactSearch = !_showContactSearch;
      if (!_showContactSearch) {
        _contactSearchDebounce?.cancel();
        _contactSearchController.clear();
        _contactsError = null;
        _contactResults = const [];
      }
    });
  }

  void _onContactSearchChanged(String value) {
    _contactSearchDebounce?.cancel();
    _contactSearchDebounce = Timer(const Duration(milliseconds: 300), () {
      final query = value.trim();
      if (query.length < 3) {
        if (!mounted) return;
        setState(() {
          _isSearchingContacts = false;
          _contactsError = null;
          _contactResults = const [];
        });
        return;
      }
      _searchContacts(queryOverride: query);
    });
  }

  Future<void> _searchContacts({String? queryOverride}) async {
    final query = (queryOverride ?? _contactSearchController.text).trim();
    if (query.isEmpty) {
      _showMessage('Entrez une valeur pour sn.');
      return;
    }

    if (query.length < 3) {
      if (!mounted) return;
      setState(() {
        _isSearchingContacts = false;
        _contactsError = null;
        _contactResults = const [];
      });
      return;
    }

    final requestId = ++_contactSearchRequestId;

    setState(() {
      _isSearchingContacts = true;
      _contactsError = null;
      _contactResults = const [];
    });

    try {
      final response = await _apiClient.callProvisionedEndpoint(
        endpoint: 'ldap/contacts',
        params: {
          'sn': query,
        },
        includeExtension: false,
      );

      if (!mounted) return;
      if (requestId != _contactSearchRequestId) return;

      if (!response.isOk) {
        setState(() {
          _contactsError = response.message.isNotEmpty
              ? response.message
              : 'Erreur API';
          _isSearchingContacts = false;
        });
        return;
      }

      final decodedData = response.decodedData;
      final contacts = <Map<String, dynamic>>[];
      if (decodedData is List) {
        for (final item in decodedData) {
          if (item is Map) {
            contacts.add(item.map((k, v) => MapEntry(k.toString(), v)));
          }
        }
      }

      setState(() {
        _contactResults = contacts;
        _isSearchingContacts = false;
      });
    } catch (e) {
      if (!mounted) return;
      if (requestId != _contactSearchRequestId) return;
      setState(() {
        _contactsError = 'Erreur: $e';
        _isSearchingContacts = false;
      });
    }
  }

  Widget _buildContactsTab() {
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Align(
            alignment: Alignment.centerRight,
            child: FilledButton.icon(
              onPressed: _toggleContactSearch,
              icon: const Icon(Icons.add),
              label: const Text('ADD'),
            ),
          ),
          if (_showContactSearch) ...[
            const SizedBox(height: 12),
            TextField(
              controller: _contactSearchController,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                labelText: 'sn',
                hintText: 'Ex: goug',
              ),
              textInputAction: TextInputAction.search,
              onChanged: _onContactSearchChanged,
              onSubmitted: (_) => _searchContacts(),
            ),
          ],
          const SizedBox(height: 12),
          if (_contactsError != null)
            Text(
              _contactsError!,
              style: TextStyle(
                color: Theme.of(context).colorScheme.error,
              ),
            ),
          Expanded(
            child: _contactResults.isEmpty
                ? const Center(
                    child: Text('Aucun contact'),
                  )
                : ListView.separated(
                    itemCount: _contactResults.length,
                    separatorBuilder: (_, __) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      final contact = _contactResults[index];
                      final name = contact['name']?.toString() ?? 'Sans nom';
                      final number = contact['telephoneNumber']?.toString() ?? '';
                      final ou = contact['ou']?.toString() ?? '';
                      return ListTile(
                        leading: const Icon(Icons.person),
                        title: Text(name),
                        subtitle: ou.isEmpty ? null : Text(ou),
                        trailing: Text(number),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final displayName = _username ?? 'Compte';
    final statusColor = _isRegistered ? Colors.green : Colors.red;

    final appBarTitle = switch (_selectedIndex) {
      0 => const Text('Favoris'),
      1 => const Text('Historique d\'appel'),
      2 => Row(
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
      3 => const Text('Contacts'),
      _ => const Text('Avancé'),
    };

    return Scaffold(
      appBar: AppBar(
        title: appBarTitle,
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
      body: switch (_selectedIndex) {
        0 => const _MenuPlaceholder(
            icon: Icons.star,
            title: 'Favoris',
          ),
        1 => const _MenuPlaceholder(
            icon: Icons.history,
            title: 'Historique d\'appel',
          ),
        2 => Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                TextField(
                  controller: _controller,
                  decoration: const InputDecoration(
                    border: OutlineInputBorder(),
                    hintText: 'saisir numéro ou click pour recherche.',
                  ),
                  keyboardType: TextInputType.text,
                  onChanged: _onDialpadSearchChanged,
                ),
                if (_isSearchingDialpad)
                  const Padding(
                    padding: EdgeInsets.only(top: 8),
                    child: LinearProgressIndicator(minHeight: 2),
                  ),
                if (_dialpadSearchError != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: Align(
                      alignment: Alignment.centerLeft,
                      child: Text(
                        _dialpadSearchError!,
                        style: TextStyle(color: Theme.of(context).colorScheme.error),
                      ),
                    ),
                  ),
                if (_dialpadSearchResults.isNotEmpty)
                  Container(
                    margin: const EdgeInsets.only(top: 8),
                    constraints: const BoxConstraints(maxHeight: 180),
                    decoration: BoxDecoration(
                      border: Border.all(
                        color: Theme.of(context).colorScheme.outlineVariant,
                      ),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: ListView.separated(
                      itemCount: _dialpadSearchResults.length,
                      shrinkWrap: true,
                      separatorBuilder: (_, __) => const Divider(height: 1),
                      itemBuilder: (context, index) {
                        final contact = _dialpadSearchResults[index];
                        final name = contact['name']?.toString() ?? 'Sans nom';
                        final number = contact['telephoneNumber']?.toString() ?? '';
                        return ListTile(
                          dense: true,
                          title: Text(name),
                          subtitle: number.isEmpty ? null : Text(number),
                          onTap: () => _selectDialpadContact(contact),
                        );
                      },
                    ),
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
                const SizedBox(height: 8),
              ],
            ),
          ),
        3 => _buildContactsTab(),
        _ => const _MenuPlaceholder(
            icon: Icons.tune,
            title: 'Avancé',
          ),
      },
      bottomNavigationBar: NavigationBar(
        selectedIndex: _selectedIndex,
        height: 72,
        labelBehavior: NavigationDestinationLabelBehavior.alwaysHide,
        onDestinationSelected: (index) => setState(() => _selectedIndex = index),
        destinations: const [
          NavigationDestination(
            icon: Tooltip(
              message: 'Favoris',
              child: Icon(Icons.star, size: 30),
            ),
            label: 'Favoris',
          ),
          NavigationDestination(
            icon: Tooltip(
              message: 'Historique',
              child: Icon(Icons.history, size: 30),
            ),
            label: 'Historique',
          ),
          NavigationDestination(
            icon: Tooltip(
              message: 'Dial pad',
              child: Icon(Icons.dialpad, size: 30),
            ),
            label: 'Dial pad',
          ),
          NavigationDestination(
            icon: Tooltip(
              message: 'Contacts',
              child: Icon(Icons.contacts, size: 30),
            ),
            label: 'Contacts',
          ),
          NavigationDestination(
            icon: Tooltip(
              message: 'Avancé',
              child: Icon(Icons.tune, size: 30),
            ),
            label: 'Avancé',
          ),
        ],
      ),
    );
  }
}

class _MenuPlaceholder extends StatelessWidget {
  const _MenuPlaceholder({required this.icon, required this.title});

  final IconData icon;
  final String title;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 64),
          const SizedBox(height: 12),
          Text(
            title,
            style: Theme.of(context).textTheme.titleLarge,
          ),
        ],
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
