package com.example.voip

import android.content.Context
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class VoipMethodChannel(
    context: Context,
    messenger: BinaryMessenger,
    private val engine: VoipEngine = VoipEngine()
) : MethodChannel.MethodCallHandler {

    private val appContext: Context = context.applicationContext
    private val channel = MethodChannel(messenger, "voip_engine")

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "init" -> {
                    engine.initialize(appContext)
                    result.success(null)
                }
                "register" -> {
                    engine.register()
                    result.success(null)
                }
                "unregister" -> {
                    engine.unregister()
                    result.success(null)
                }
                "makeCall" -> {
                    val callee = requireArgument<String>(call, "callee")
                    engine.makeCall(callee)
                    result.success(null)
                }
                "acceptCall" -> {
                    val callId = requireArgument<String>(call, "callId")
                    engine.acceptCall(callId)
                    result.success(null)
                }
                "hangupCall" -> {
                    val callId = requireArgument<String>(call, "callId")
                    engine.hangupCall(callId)
                    result.success(null)
                }
                else -> result.notImplemented()
            }
        } catch (e: IllegalArgumentException) {
            result.error("ARGUMENT", e.message, null)
        } catch (e: Exception) {
            result.error("ERROR", e.message ?: "Unknown error", null)
        }
    }

    fun dispose() {
        channel.setMethodCallHandler(null)
        engine.dispose()
    }
}

private fun <T> requireArgument(call: MethodCall, key: String): T {
    val value = call.argument<T>(key)
    return value ?: throw IllegalArgumentException("Missing required argument: $key")
}

// Lightweight engine extensions to keep MethodChannel handling compilable without SIP logic yet.
private fun VoipEngine.register() {}

private fun VoipEngine.unregister() {}

private fun VoipEngine.makeCall(callee: String) {
    startCall(callee)
}

private fun VoipEngine.acceptCall(callId: String) {
    // Hook into native accept call flow when implemented
}

private fun VoipEngine.hangupCall(callId: String) {
    endCall(callId)
}
