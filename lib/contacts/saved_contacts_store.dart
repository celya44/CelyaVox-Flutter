import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

class SavedContact {
  const SavedContact({
    required this.name,
    required this.number,
    required this.ou,
  });

  final String name;
  final String number;
  final String ou;

  factory SavedContact.fromMap(Map<String, dynamic> map) {
    return SavedContact(
      name: map['name']?.toString() ?? '',
      number: map['number']?.toString() ?? '',
      ou: map['ou']?.toString() ?? '',
    );
  }

  Map<String, dynamic> toMap() {
    return {
      'name': name,
      'number': number,
      'ou': ou,
    };
  }
}

class SavedContactsStore {
  static const String _storageKey = 'saved_contacts_v1';

  static Future<List<SavedContact>> load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_storageKey);
    if (raw == null || raw.trim().isEmpty) return const [];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return const [];
      return decoded
          .whereType<Map>()
          .map((item) => item.map((k, v) => MapEntry(k.toString(), v)))
          .map(SavedContact.fromMap)
          .where((contact) => contact.number.trim().isNotEmpty)
          .toList();
    } catch (_) {
      return const [];
    }
  }

  static Future<void> saveAll(List<SavedContact> contacts) async {
    final prefs = await SharedPreferences.getInstance();
    final encoded = jsonEncode(contacts.map((c) => c.toMap()).toList());
    await prefs.setString(_storageKey, encoded);
  }

  static Future<List<SavedContact>> add(SavedContact contact) async {
    final normalizedNumber = contact.number.trim();
    if (normalizedNumber.isEmpty) return load();
    final current = await load();
    final exists = current.any((c) => c.number.trim() == normalizedNumber);
    if (exists) return current;
    final updated = List<SavedContact>.from(current)..add(
        SavedContact(
          name: contact.name.trim(),
          number: normalizedNumber,
          ou: contact.ou.trim(),
        ),
      );
    await saveAll(updated);
    return updated;
  }

  static Future<List<SavedContact>> removeByNumber(String number) async {
    final normalizedNumber = number.trim();
    if (normalizedNumber.isEmpty) return load();
    final current = await load();
    final updated = current
        .where((contact) => contact.number.trim() != normalizedNumber)
        .toList();
    await saveAll(updated);
    return updated;
  }
}
