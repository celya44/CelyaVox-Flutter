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
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "voip:fcm_message_wake_lock"
        ).apply {
            setReferenceCounted(false)
            acquire(10000) // 10s max pour traiter le message
        }

        try {
            val data = remoteMessage.data
            if (data.isEmpty()) {
                Log.w(TAG, "Received FCM message without data")
                return
            }

            val type = data["type"] ?: "unknown"
            val callId = data["callId"] ?: ""
            val callerId = data["callerId"] ?: ""
            Log.i(TAG, "FCM push received: type=$type callId=$callId callerId=$callerId")

            // Mark that app was woken up by FCM push and store callerId
            VoipFirebaseService.setFcmWakeup(true)
            VoipFirebaseService.setFcmCallerId(callerId)

            if (type == "incoming_call") {
                if (isAppInForeground()) {
                    Log.i(TAG, "App in foreground; ignoring incoming_call push and waiting for SIP invite")
                    return
                }
                handleIncomingCallPush(callId, callerId)
            }
        } finally {
            try {
                wakeLock.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing FCM wake lock", e)
            }
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
            Log.i(TAG, "Device idle; showing simple notification immediately")
            showSimpleIncomingCallNotification(callId, callerId)
            registerSipInBackground()
            return
        }
        registerSipInBackground()
        Handler(Looper.getMainLooper()).postDelayed(
            {
                try {
                    // Check if invite already arrived (race condition protection)
                    if (!VoipFirebaseService.isWaitingForInvite) {
                        Log.i(TAG, "Invite already received; skipping fallback")
                        return@postDelayed
                    }
                    
                    val sipEngine = PjsipEngine.instance
                    val isRegistered = sipEngine.isRegistered()
                    
                    if (!isRegistered) {
                        Log.w(TAG, "SIP not registered; showing simple notification")
                        showSimpleIncomingCallNotification(callId, callerId)
                        return@postDelayed
                    }
                    
                    if (VoipFirebaseService.fallbackRunnable != null) {
                        Log.i(TAG, "Fallback already scheduled; skipping duplicate")
                        return@postDelayed
                    }
                    
                    Log.i(TAG, "SIP registered; scheduling 3s fallback timeout")
                    scheduleIncomingCallFallback(callId, callerId)
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing push", e)
                    showSimpleIncomingCallNotification(callId, callerId)
                }
            },
            FULL_SCREEN_DELAY_MS
        )
    }

    private fun scheduleIncomingCallFallback(callId: String, callerId: String) {
        val runnable = Runnable {
            if (VoipFirebaseService.isWaitingForInvite) {
                Log.w(TAG, "Fallback timeout: showing simple notification")
                showSimpleIncomingCallNotification(callId, callerId)
            } else {
                Log.i(TAG, "Fallback timeout: invite already received, skipping")
            }
        }
        VoipFirebaseService.scheduleInviteWaitFallback(runnable)
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
                putExtra("navigate_to_call_history", true)
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
        Log.i(TAG, "Incoming call notification displayed")
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

    companion object {
        const val ACTION_FCM_TOKEN = "fr.celya.celyavox.FCM_TOKEN"
        const val EXTRA_TOKEN = "token"
        private const val INCOMING_CALL_CHANNEL_ID = "voip_call_channel"
        private const val INCOMING_CALL_NOTIFICATION_ID = 2101
        private const val CANCELLED_CALL_NOTIFICATION_ID = 2102
        private const val FULL_SCREEN_DELAY_MS = 500L
        private const val INVITE_WAIT_TIMEOUT_MS = 3000L
        private const val TAG = "VoipFirebaseService"
        private val fallbackHandler = Handler(Looper.getMainLooper())
        @Volatile var fallbackRunnable: Runnable? = null
        @Volatile private var isWaitingForInvite = false
        @Volatile private var isWakeupFromFcmPush = false
        @Volatile private var fcmCallerId: String = ""

        @JvmStatic
        fun setFcmWakeup(value: Boolean) {
            isWakeupFromFcmPush = value
        }

        @JvmStatic
        fun consumeFcmWakeup(): Boolean {
            val value = isWakeupFromFcmPush
            isWakeupFromFcmPush = false
            return value
        }

        @JvmStatic
        fun setFcmCallerId(callerId: String) {
            fcmCallerId = callerId
        }

        @JvmStatic
        fun getFcmCallerId(): String {
            return fcmCallerId
        }

        @JvmStatic
        fun cancelInviteWaitFallback() {
            fallbackRunnable?.let { fallbackHandler.removeCallbacks(it) }
            fallbackRunnable = null
            isWaitingForInvite = false
        }

        @JvmStatic
        fun scheduleInviteWaitFallback(runnable: Runnable) {
            cancelInviteWaitFallback()
            isWaitingForInvite = true
            fallbackRunnable = runnable
            fallbackHandler.postDelayed(runnable, INVITE_WAIT_TIMEOUT_MS)
        }

        @JvmStatic
        fun cancelSimpleIncomingNotification(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(INCOMING_CALL_NOTIFICATION_ID)
            Log.i(TAG, "Simple incoming call notification cancelled")
        }

        @JvmStatic
        fun showCancelledCallNotification(context: Context, message: String, callerId: String = "") {
            
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                INCOMING_CALL_CHANNEL_ID,
                "VoIP Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming VoIP calls"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
            
            val contentIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra("navigate_to_call_history", true)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
            )

            val title = "Appel reçu en absence"
            val notification = NotificationCompat.Builder(context, INCOMING_CALL_CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(callerId.ifEmpty { "L'appel a été annulé" })
                .setSmallIcon(android.R.drawable.sym_call_missed)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()

            manager.notify(CANCELLED_CALL_NOTIFICATION_ID, notification)
        }
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
}
