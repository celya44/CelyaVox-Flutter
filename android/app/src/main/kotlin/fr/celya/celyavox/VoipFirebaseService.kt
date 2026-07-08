package fr.celya.celyavox

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
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
            if (isAppInForeground()) {
                Log.i(TAG, "App in foreground; ignoring incoming_call push and waiting for SIP invite")
                return
            }
            handleIncomingCallPush(callId, callerId)
        }
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val running = activityManager.runningAppProcesses ?: return false
        val current = running.firstOrNull { it.processName == packageName } ?: return false
        return current.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            current.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }

    private fun handleIncomingCallPush(callId: String, callerId: String) {
        if (shouldShowSimpleNotificationOnPush()) {
            Log.i(TAG, "Device idle and app not in recents; showing simple incoming call notification")
            showSimpleIncomingCallNotification(callId, callerId)
            registerSipInBackground()
            return
        }
        registerSipInBackground()
        Log.i(TAG, "Delaying incoming call UI by ${FULL_SCREEN_DELAY_MS}ms to allow SIP register")
        Handler(Looper.getMainLooper()).postDelayed(
            {
                try {
                    val registered = VoipConnectionService.registerSelfManaged(this)
                    val ok = if (registered) {
                        VoipConnectionService.startIncomingCall(this, callId, callerId)
                    } else {
                        false
                    }
                    if (!ok) {
                        Log.w(TAG, "Telecom incoming call not available; launching CallActivity directly")
                        openIncomingCallActivity(callId, callerId)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to start ConnectionService incoming call", e)
                    openIncomingCallActivity(callId, callerId)
                }
            },
            FULL_SCREEN_DELAY_MS
        )
    }

    private fun shouldShowSimpleNotificationOnPush(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val interactive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            powerManager.isInteractive
        } else {
            @Suppress("DEPRECATION")
            powerManager.isScreenOn
        }
        if (interactive) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return false
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val hasTaskInRecents = activityManager.appTasks?.isNotEmpty() == true
        return !hasTaskInRecents
    }

    private fun showSimpleIncomingCallNotification(callId: String, callerId: String) {
        ensureIncomingCallChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(CallActivity.EXTRA_CALL_ID, callId)
                putExtra(CallActivity.EXTRA_CALLER_ID, callerId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )

        val title = if (callerId.isNotEmpty()) callerId else "Appel entrant"
        val notification = NotificationCompat.Builder(this, INCOMING_CALL_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Appel recu")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(INCOMING_CALL_NOTIFICATION_ID, notification)
    }

    private fun ensureIncomingCallChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            INCOMING_CALL_CHANNEL_ID,
            "VoIP Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Incoming VoIP calls"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    private fun pendingIntentImmutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    private fun openIncomingCallActivity(callId: String, callerId: String) {
        if (CallActivity.isVisible) {
            Log.i(TAG, "CallActivity already visible for callId=${CallActivity.visibleCallId}; skip FCM relaunch")
            return
        }
        CallActivity.lastLaunchAtMs = System.currentTimeMillis()
        val intent = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CallActivity.EXTRA_CALL_ID, callId)
            putExtra(CallActivity.EXTRA_CALLER_ID, callerId)
        }
        try {
            startActivity(intent)
            Log.i(TAG, "CallActivity launched from FCM push")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch CallActivity from FCM push", e)
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
        private const val INCOMING_CALL_CHANNEL_ID = "voip_call_channel"
        private const val INCOMING_CALL_NOTIFICATION_ID = 2101
        private const val FULL_SCREEN_DELAY_MS = 500L
    }
}
