package fr.celya.celyavox

import android.content.Context
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
        sipEngine.init()
    }

    fun dispose() {
        eventChannel?.setStreamHandler(null)
        eventSink = null
        sipEngine.setCallback(null)
    }

    fun register(username: String, password: String, domain: String, proxy: String) {
        sipEngine.register(username, password, domain, proxy)
    }

    fun unregister() {
        sipEngine.unregister()
    }

    fun startCall(callee: String) {
        sipEngine.makeCall(callee)
    }

    fun endCall(callId: String) {
        sipEngine.hangupCall(callId)
    }

    fun acceptCall(callId: String) {
        sipEngine.acceptCall(callId)
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
