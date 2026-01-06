package com.example.voip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class VoipForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val callerId = intent?.getStringExtra(EXTRA_CALLER_ID).orEmpty()
        startForeground(NOTIFICATION_ID, buildNotification(callId, callerId))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(callId: String, callerId: String): Notification {
        createChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, CallActivity::class.java).apply {
                putExtra(CallActivity.EXTRA_CALL_ID, callId)
                putExtra(CallActivity.EXTRA_CALLER_ID, callerId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or pendingIntentImmutableFlag()
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (callerId.isNotEmpty()) callerId else "Appel en cours")
            .setContentText(if (callId.isNotEmpty()) "ID: $callId" else "")
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentIntent(contentIntent)
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VoIP Calls",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Foreground service for ongoing VoIP calls"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun pendingIntentImmutableFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
    }

    companion object {
        private const val CHANNEL_ID = "voip_call_channel"
        private const val NOTIFICATION_ID = 2001
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_CALLER_ID = "callerId"

        fun start(context: Context, callId: String?, callerId: String?) {
            val intent = Intent(context, VoipForegroundService::class.java).apply {
                putExtra(EXTRA_CALL_ID, callId)
                putExtra(EXTRA_CALLER_ID, callerId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoipForegroundService::class.java))
        }
    }
}
