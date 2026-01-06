package com.example.voip

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

private const val TAG = "VoipFirebaseService"

class VoipFirebaseService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val data = remoteMessage.data
        if (data.isEmpty()) {
            Log.w(TAG, "Received FCM message without data")
            return
        }

        val type = data["type"] ?: "unknown"
        val callId = data["callId"] ?: ""
        val callerId = data["callerId"] ?: ""
        Log.i(
            TAG,
            "Data push received: type=$type priority=${remoteMessage.priority} callId=$callId callerId=$callerId"
        )

        if (type == "incoming_call") {
            Log.i(TAG, "Incoming call push received (callId=$callId, callerId=$callerId)")
            // TODO: Trigger native incoming-call handling / wake mechanisms when added.
        }
    }

    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM token refreshed: $token")
        // TODO: send token to backend when available.
    }
}
