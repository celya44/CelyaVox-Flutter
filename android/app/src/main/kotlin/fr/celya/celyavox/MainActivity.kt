package fr.celya.celyavox

import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : FlutterActivity() {

    private var voipEngine: VoipEngine? = null
    private var methodChannel: VoipMethodChannel? = null
    private var provisioningChannel: ProvisioningMethodChannel? = null
    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            val held = roleManager.isRoleHeld(RoleManager.ROLE_SELF_MANAGED_CALLS)
            Log.i(TAG, "ROLE_SELF_MANAGED_CALLS granted=$held")
            if (held) {
                VoipConnectionService.registerSelfManaged(this)
            }
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val app = FirebaseApp.initializeApp(this)
        Log.i(TAG, "FirebaseApp initialized: ${app != null}")
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        val engine = VoipEngine(flutterEngine.dartExecutor.binaryMessenger)
        voipEngine = engine
        methodChannel = VoipMethodChannel(this, flutterEngine.dartExecutor.binaryMessenger, engine)
        provisioningChannel = ProvisioningMethodChannel(this, flutterEngine.dartExecutor.binaryMessenger)
        engine.initialize(this)
        requestSelfManagedRoleIfNeeded()
    }

    override fun onDestroy() {
        provisioningChannel?.dispose()
        methodChannel?.dispose()
        voipEngine?.dispose()
        super.onDestroy()
    }

    private fun requestSelfManagedRoleIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val roleManager = getSystemService(RoleManager::class.java)
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_SELF_MANAGED_CALLS)) {
            Log.w(TAG, "ROLE_SELF_MANAGED_CALLS not available on this device")
            return
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_SELF_MANAGED_CALLS)) {
            Log.i(TAG, "ROLE_SELF_MANAGED_CALLS already granted")
            VoipConnectionService.registerSelfManaged(this)
            return
        }
        Log.i(TAG, "Requesting ROLE_SELF_MANAGED_CALLS")
        val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SELF_MANAGED_CALLS)
        roleRequestLauncher.launch(intent)
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
