package fr.celya.celyavox

import android.os.Bundle
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

class MainActivity : FlutterActivity() {

    private var voipEngine: VoipEngine? = null
    private var methodChannel: VoipMethodChannel? = null
    private var provisioningChannel: ProvisioningMethodChannel? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val engine = VoipEngine(flutterEngine.dartExecutor.binaryMessenger)
        voipEngine = engine
        methodChannel = VoipMethodChannel(this, flutterEngine.dartExecutor.binaryMessenger, engine)
        provisioningChannel = ProvisioningMethodChannel(this, flutterEngine.dartExecutor.binaryMessenger)
        engine.initialize(this)
    }

    override fun onDestroy() {
        provisioningChannel?.dispose()
        methodChannel?.dispose()
        voipEngine?.dispose()
        super.onDestroy()
    }
}
