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
                    val username = requireArgument<String>(call, "username")
                    val password = requireArgument<String>(call, "password")
                    val domain = requireArgument<String>(call, "domain")
                    val proxy = call.argument<String>("proxy") ?: ""
                    engine.register(username, password, domain, proxy)
                    result.success(null)
                }
                "unregister" -> {
                    engine.unregister()
                    result.success(null)
                }
                "makeCall" -> {
                    val callee = requireArgument<String>(call, "callee")
                    engine.startCall(callee)
                    result.success(null)
                }
                "acceptCall" -> {
                    val callId = requireArgument<String>(call, "callId")
                    engine.acceptCall(callId)
                    result.success(null)
                }
                "hangupCall" -> {
                    val callId = requireArgument<String>(call, "callId")
                    engine.endCall(callId)
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
