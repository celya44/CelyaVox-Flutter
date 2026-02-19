package fr.celya.celyavox

import android.app.ActivityManager
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioManager
import android.media.AudioDeviceInfo
import android.os.Build
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import com.google.firebase.messaging.FirebaseMessaging

/**
 * Bridges Flutter to native PJSIP and ConnectionService, relaying events via EventChannel.
 */
class VoipEngine(
    messenger: BinaryMessenger? = null
) : EventChannel.StreamHandler, PjsipEngine.Callback {

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var eventSink: EventChannel.EventSink? = null
    private var eventChannel: EventChannel? = null
    private val sipEngine = PjsipEngine.instance
    @Volatile
    private var appContext: Context? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null
    private var bluetoothAvailable: Boolean = false
    private var fcmReceiver: BroadcastReceiver? = null
    private var pendingFcmToken: String? = null
    private var pendingConnectedCallId: String? = null

    init {
        messenger?.let { bindEventChannel(it) }
        sipEngine.setCallback(this)
    }

    fun bindEventChannel(messenger: BinaryMessenger) {
        synchronized(this) {
            if (eventChannel == null) {
                eventChannel = EventChannel(messenger, "voip_events").also { channel ->
                    channel.setStreamHandler(this)
                }
            }
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        // Registration can fail if MANAGE_OWN_CALLS role/permission is not granted; keep app alive.
        VoipConnectionService.registerSelfManaged(appContext!!)
        val ok = sipEngine.init()
        if (!ok) {
            Log.e(TAG, "PJSIP init failed (native lib missing or init error); continuing without SIP")
        }
        startAudioDeviceMonitoring()
        startFcmTokenMonitoring()
    }

    fun dispose() {
        eventChannel?.setStreamHandler(null)
        eventSink = null
        sipEngine.setCallback(null)
        stopAudioDeviceMonitoring()
        stopFcmTokenMonitoring()
    }

    fun register(username: String, password: String, domain: String, proxy: String) {
        sipEngine.register(username, password, domain, proxy)
    }

    fun unregister() {
        sipEngine.unregister()
    }

    fun startCall(callee: String): Boolean {
        return sipEngine.makeCall(callee)
    }

    fun endCall(callId: String) {
        val ok = sipEngine.hangupCall(callId)
        Log.i(TAG, "VoipEngine.endCall callId=$callId ok=$ok")
        appContext?.let { ctx ->
            try {
                ctx.sendBroadcast(Intent(ACTION_CALL_TERMINATE_REQUESTED).setPackage(ctx.packageName))
                Log.i(TAG, "Broadcasted ACTION_CALL_TERMINATE_REQUESTED")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to broadcast ACTION_CALL_TERMINATE_REQUESTED", e)
            }
            try {
                ctx.startActivity(
                    Intent(ctx, MainActivity::class.java).apply {
                        action = ACTION_CALL_TERMINATE_REQUESTED
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
                Log.i(TAG, "Dispatched ACTION_CALL_TERMINATE_REQUESTED intent to MainActivity")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to dispatch ACTION_CALL_TERMINATE_REQUESTED intent", e)
            }
        }
    }

    fun acceptCall(callId: String) {
        Log.i(TAG, "VoipEngine.acceptCall callId=$callId")
        val ok = sipEngine.acceptCall(callId)
        Log.i(TAG, "VoipEngine.acceptCall result callId=$callId ok=$ok")
    }

    fun refreshAudio(): Boolean {
        return sipEngine.refreshAudio()
    }

    fun setSpeakerphone(enabled: Boolean) {
        val ctx = appContext ?: return
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isSpeakerphoneOn = enabled
        if (enabled && audioManager.isBluetoothScoOn) {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        }
    }

    fun setBluetooth(enabled: Boolean) {
        val ctx = appContext ?: return
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (enabled) {
            audioManager.isSpeakerphoneOn = false
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        } else {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        }
    }

    fun setMuted(enabled: Boolean) {
        val ctx = appContext ?: return
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.isMicrophoneMute = enabled
    }

    fun isBluetoothAvailable(): Boolean {
        val ctx = appContext ?: return false
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return devices.any { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
        }
        return audioManager.isBluetoothScoAvailableOffCall
    }

    private fun startAudioDeviceMonitoring() {
        val ctx = appContext ?: return
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            publishBluetoothAvailability(isBluetoothAvailable(), null)
            return
        }
        if (audioDeviceCallback != null) return
        audioDeviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                publishBluetoothAvailability(isBluetoothAvailable(), getBluetoothDeviceName())
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                publishBluetoothAvailability(isBluetoothAvailable(), getBluetoothDeviceName())
            }
        }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        publishBluetoothAvailability(isBluetoothAvailable(), getBluetoothDeviceName())
    }

    private fun stopAudioDeviceMonitoring() {
        val ctx = appContext ?: return
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioDeviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
        audioDeviceCallback = null
    }

    private fun publishBluetoothAvailability(available: Boolean, name: String?) {
        if (available == bluetoothAvailable && (name == null || name.isBlank())) return
        bluetoothAvailable = available
        emit(
            mapOf(
                "type" to "bluetooth_available",
                "available" to available,
                "name" to (name ?: ""),
            )
        )
    }

    private fun startFcmTokenMonitoring() {
        val ctx = appContext ?: return
        if (fcmReceiver != null) return
        fcmReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != VoipFirebaseService.ACTION_FCM_TOKEN) return
                val token = intent.getStringExtra(VoipFirebaseService.EXTRA_TOKEN) ?: return
                emit(
                    mapOf(
                        "type" to "fcm_token",
                        "token" to token,
                        "updatedAt" to System.currentTimeMillis(),
                    )
                )
            }
        }
        val filter = IntentFilter(VoipFirebaseService.ACTION_FCM_TOKEN)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(fcmReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ctx.registerReceiver(fcmReceiver, filter)
        }

        // Ensure we have a token even if onNewToken hasn't fired yet.
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (!token.isNullOrBlank()) {
                    FcmTokenStore.saveToken(ctx, token)
                    Log.i(TAG, "FCM token fetched: ${token.take(8)}…")
                    if (eventSink != null) {
                        emit(
                            mapOf(
                                "type" to "fcm_token",
                                "token" to token,
                                "updatedAt" to System.currentTimeMillis(),
                            )
                        )
                    } else {
                        pendingFcmToken = token
                    }
                } else {
                    Log.w(TAG, "FCM token fetch returned empty")
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Failed to fetch FCM token: ${e.message}", e)
            }

        // Emit cached token if present.
        val cached = FcmTokenStore.getToken(ctx)
        if (!cached.isNullOrBlank()) {
            if (eventSink != null) {
                emit(
                    mapOf(
                        "type" to "fcm_token",
                        "token" to cached,
                        "updatedAt" to FcmTokenStore.getUpdatedAt(ctx),
                    )
                )
            } else {
                pendingFcmToken = cached
            }
        }
    }

    private fun stopFcmTokenMonitoring() {
        val ctx = appContext ?: return
        fcmReceiver?.let { ctx.unregisterReceiver(it) }
        fcmReceiver = null
    }

    private fun getBluetoothDeviceName(): String? {
        val ctx = appContext ?: return null
        val audioManager = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val device = devices.firstOrNull { d ->
            d.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                d.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
        return device?.productName?.toString()
    }

    fun sendDtmf(callId: String, digits: String): Boolean {
        return sipEngine.sendDtmf(callId, digits)
    }

    private fun emit(event: Map<String, Any?>) {
        val sink = eventSink ?: return
        mainHandler.post { sink.success(event) }
    }

    override fun onEvent(type: String, message: String) {
        Log.d(TAG, "Native event $type | $message")
        when (type) {
            "incoming_call" -> {
                val ctx = appContext
                if (ctx != null) {
                    val ok = VoipConnectionService.startIncomingCall(ctx, message, null)
                    if (!ok) {
                        if (CallActivity.isVisible) {
                            if (message.isNotBlank() && CallActivity.visibleCallId != message) {
                                Log.i(
                                    TAG,
                                    "CallActivity visible with callId=${CallActivity.visibleCallId}; updating to native callId=$message"
                                )
                                startIncomingCallActivity(ctx, message, null)
                            } else {
                                Log.i(
                                    TAG,
                                    "CallActivity already visible for callId=${CallActivity.visibleCallId}; no update needed"
                                )
                            }
                            return
                        }
                        if (isAppInForeground(ctx) && !CallActivity.isVisible) {
                            Log.i(TAG, "App in foreground; routing incoming call to Flutter UI")
                            incomingCall(message, null)
                            return
                        }
                        val now = System.currentTimeMillis()
                        if (now - CallActivity.lastLaunchAtMs < 5000L) {
                            Log.i(TAG, "Recent CallActivity launch detected; skip VoipEngine relaunch")
                            return
                        }
                        if (CallActivity.isVisible) {
                            Log.i(TAG, "CallActivity already visible for callId=${CallActivity.visibleCallId}; skip relaunch")
                            return
                        }
                        val launched = startIncomingCallActivity(ctx, message, null)
                        if (!launched) {
                            incomingCall(message, null)
                        }
                    }
                } else {
                    incomingCall(message, null)
                }
            }
            "call_connected" -> {
                VoipConnectionService.markCallActive(message)
                callConnected(message)
            }
            "call_ended" -> {
                VoipConnectionService.markCallEnded(message)
                callEnded(message, null)
            }
            else -> emit(mapOf("type" to type, "message" to message))
        }
    }

    private fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val running = activityManager.runningAppProcesses ?: return false
        val current = running.firstOrNull { it.processName == context.packageName } ?: return false
        return current.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
            current.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
    }

    fun incomingCall(callId: String, callerId: String?) {
        emit(
            mapOf(
                "type" to "incoming_call",
                "callId" to callId,
                "callerId" to (callerId ?: ""),
            )
        )
    }

    fun callConnected(callId: String) {
        if (eventSink == null) {
            Log.w(TAG, "call_connected queued (no Flutter listener) callId=$callId")
            pendingConnectedCallId = callId
            return
        }
        Log.i(TAG, "Emitting call_connected to Flutter callId=$callId")
        emit(
            mapOf(
                "type" to "call_connected",
                "callId" to callId,
            )
        )
    }

    fun callEnded(callId: String, reason: String? = null) {
        appContext?.let { ctx ->
            try {
                ctx.sendBroadcast(Intent(ACTION_CALL_ENDED).setPackage(ctx.packageName))
                Log.i(TAG, "Broadcasted ACTION_CALL_ENDED")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to broadcast ACTION_CALL_ENDED", e)
            }
            try {
                ctx.startActivity(
                    Intent(ctx, MainActivity::class.java).apply {
                        action = ACTION_CALL_ENDED
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                )
                Log.i(TAG, "Dispatched ACTION_CALL_ENDED intent to MainActivity")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to dispatch ACTION_CALL_ENDED intent", e)
            }
        }
        emit(
            mapOf(
                "type" to "call_ended",
                "callId" to callId,
                "reason" to reason,
            )
        )
    }

    private fun startIncomingCallActivity(context: Context, callId: String, callerId: String?): Boolean {
        val intent = Intent(context, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CallActivity.EXTRA_CALL_ID, callId)
            putExtra(CallActivity.EXTRA_CALLER_ID, callerId)
        }
        return try {
            context.startActivity(intent)
            Log.i(TAG, "CallActivity launched from VoipEngine incoming event")
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch CallActivity from VoipEngine", e)
            false
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        eventSink = events
        val ctx = appContext
        val pending = pendingFcmToken
        if (ctx != null && !pending.isNullOrBlank()) {
            emit(
                mapOf(
                    "type" to "fcm_token",
                    "token" to pending,
                    "updatedAt" to FcmTokenStore.getUpdatedAt(ctx),
                )
            )
            pendingFcmToken = null
        }
        val pendingConnected = pendingConnectedCallId
        if (!pendingConnected.isNullOrBlank()) {
            Log.i(TAG, "Replaying pending call_connected to Flutter callId=$pendingConnected")
            emit(
                mapOf(
                    "type" to "call_connected",
                    "callId" to pendingConnected,
                )
            )
            pendingConnectedCallId = null
        }
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    companion object {
        private const val TAG = "VoipEngine"
        const val ACTION_CALL_ENDED = "fr.celya.celyavox.ACTION_CALL_ENDED"
        const val ACTION_CALL_TERMINATE_REQUESTED = "fr.celya.celyavox.ACTION_CALL_TERMINATE_REQUESTED"
    }
}
