package fr.celya.celyavox

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
        sipEngine.hangupCall(callId)
    }

    fun acceptCall(callId: String) {
        sipEngine.acceptCall(callId)
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

        // Emit cached token if present.
        val cached = FcmTokenStore.getToken(ctx)
        if (!cached.isNullOrBlank()) {
            emit(
                mapOf(
                    "type" to "fcm_token",
                    "token" to cached,
                    "updatedAt" to FcmTokenStore.getUpdatedAt(ctx),
                )
            )
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
                    VoipConnectionService.startIncomingCall(ctx, message, null)
                }
                incomingCall(message, null)
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
        emit(
            mapOf(
                "type" to "call_connected",
                "callId" to callId,
            )
        )
    }

    fun callEnded(callId: String, reason: String? = null) {
        emit(
            mapOf(
                "type" to "call_ended",
                "callId" to callId,
                "reason" to reason,
            )
        )
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
    }

    companion object {
        private const val TAG = "VoipEngine"
    }
}
