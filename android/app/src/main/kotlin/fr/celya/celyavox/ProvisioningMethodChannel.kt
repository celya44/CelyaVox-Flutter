package fr.celya.celyavox

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.celya.voip.provisioning.ProvisioningManager
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class ProvisioningMethodChannel(
    context: Context,
    messenger: BinaryMessenger
) : MethodChannel.MethodCallHandler {

    private val appContext: Context = context.applicationContext
    private val channel = MethodChannel(messenger, "com.celya.voip/provisioning")
    private val manager = ProvisioningManager(appContext)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "start" -> {
                val url = requireArgument<String>(call, "url")
                Thread {
                    try {
                        manager.start(url)
                        mainHandler.post { result.success(null) }
                    } catch (e: Exception) {
                        mainHandler.post {
                            result.error(
                                "PROVISIONING",
                                e.message ?: "Provisioning error",
                                null
                            )
                        }
                    }
                }.start()
            }
            else -> result.notImplemented()
        }
    }

    fun dispose() {
        channel.setMethodCallHandler(null)
    }
}

private fun <T> requireArgument(call: MethodCall, key: String): T {
    val value = call.argument<T>(key)
    return value ?: throw IllegalArgumentException("Missing required argument: $key")
}
