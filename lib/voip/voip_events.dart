import 'dart:async';

import 'package:flutter/services.dart';

const EventChannel _eventChannel = EventChannel('voip_events');

/// Base class for all VoIP platform events.
sealed class VoipEvent {
  const VoipEvent();

  static VoipEvent fromMap(Map<dynamic, dynamic> map) {
    final type = map['type'] as String?;
    switch (type) {
      case 'incoming_call':
        return IncomingCallEvent(
          callId: map['callId'] as String? ?? '',
          callerId: map['callerId'] as String? ?? '',
        );
      case 'registration':
        return RegistrationEvent(
          statusText: map['message'] as String? ?? '',
        );
      case 'call_connected':
        return CallConnectedEvent(
          callId: map['callId'] as String? ?? '',
        );
      case 'call_ended':
        return CallEndedEvent(
          callId: map['callId'] as String? ?? '',
          reason: map['reason'] as String?,
        );
      default:
        throw PlatformException(
          code: 'UNKNOWN_EVENT',
          message: 'Unknown VoIP event type: $type',
        );
    }
  }
}

class IncomingCallEvent extends VoipEvent {
  final String callId;
  final String callerId;

  const IncomingCallEvent({required this.callId, required this.callerId});
}

class CallConnectedEvent extends VoipEvent {
  final String callId;

  const CallConnectedEvent({required this.callId});
}

class CallEndedEvent extends VoipEvent {
  final String callId;
  final String? reason;

  const CallEndedEvent({required this.callId, this.reason});
}

class RegistrationEvent extends VoipEvent {
  final String statusText;

  const RegistrationEvent({required this.statusText});
}

/// Exposes a broadcast stream of platform VoIP events.
class VoipEvents {
  VoipEvents._();

  static Stream<VoipEvent>? _cached;

  static Stream<VoipEvent> get stream => _cached ??= _eventChannel
      .receiveBroadcastStream()
      .map((event) => VoipEvent.fromMap(event as Map<dynamic, dynamic>))
      .handleError((error) {
    // Propagate as PlatformException for consistency.
    if (error is PlatformException) throw error;
    throw PlatformException(code: 'VOIP_EVENT_ERROR', message: '$error');
  });
}
