package fr.celya.celyavox

import android.content.Intent
import android.util.Log
import com.celya.voip.provisioning.ProvisioningManager
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
            handleIncomingCallPush(callId, callerId)
        }
    }

    private fun handleIncomingCallPush(callId: String, callerId: String) {
        // Keep process alive + show notification/UX
        VoipForegroundService.start(this, callId, callerId)
        try {
            VoipConnectionService.registerSelfManaged(this)
            VoipConnectionService.startIncomingCall(this, callId, callerId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start ConnectionService incoming call", e)
        }
        startIncomingCallActivity(callId, callerId)
        registerSipInBackground()
    }

    private fun startIncomingCallActivity(callId: String, callerId: String) {
        val intent = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CallActivity.EXTRA_CALL_ID, callId)
            putExtra(CallActivity.EXTRA_CALLER_ID, callerId)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start CallActivity", e)
        }
    }

    private fun registerSipInBackground() {
        Thread {
            try {
                val manager = ProvisioningManager(applicationContext)
                val username = manager.getSipUsername()
                val password = manager.getSipPassword()
                val domain = manager.getSipDomain()
                val proxy = manager.getSipProxy() ?: ""
                if (username.isNullOrBlank() || password.isNullOrBlank() || domain.isNullOrBlank()) {
                    Log.w(TAG, "Skipping SIP register: missing provisioning data")
                    return@Thread
                }
                val ok = PjsipEngine.instance.register(username, password, domain, proxy)
                Log.i(TAG, "SIP register triggered from push: $ok")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register SIP from push", e)
            }
        }.start()
    }

    override fun onNewToken(token: String) {
        Log.i(TAG, "FCM token refreshed: $token")
        FcmTokenStore.saveToken(this, token)
        val intent = android.content.Intent(ACTION_FCM_TOKEN).apply {
            putExtra(EXTRA_TOKEN, token)
        }
        sendBroadcast(intent)
    }

    companion object {
        const val ACTION_FCM_TOKEN = "fr.celya.celyavox.FCM_TOKEN"
        const val EXTRA_TOKEN = "token"
    }
}
