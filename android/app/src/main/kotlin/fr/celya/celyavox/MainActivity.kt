package fr.celya.celyavox

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import java.util.ArrayList
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : FlutterActivity() {

    private var voipEngine: VoipEngine? = null
    private var methodChannel: VoipMethodChannel? = null
    private var provisioningChannel: ProvisioningMethodChannel? = null
    private var isOverlayDialogVisible = false
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_SELF_MANAGED_ROLE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            val held = roleManager.isRoleHeld(ROLE_SELF_MANAGED_CALLS)
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
        requestStartupPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        if (!isFullScreenIntentAllowed()) {
            Log.d(TAG, "Full-screen intent not allowed; launching gate")
            launchFullScreenIntentGate()
            return
        }
        requestOverlayPermissionIfNeeded()
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
        if (!roleManager.isRoleAvailable(ROLE_SELF_MANAGED_CALLS)) {
            Log.w(TAG, "ROLE_SELF_MANAGED_CALLS not available on this device")
            return
        }
        if (roleManager.isRoleHeld(ROLE_SELF_MANAGED_CALLS)) {
            Log.i(TAG, "ROLE_SELF_MANAGED_CALLS already granted")
            VoipConnectionService.registerSelfManaged(this)
            return
        }
        Log.i(TAG, "Requesting ROLE_SELF_MANAGED_CALLS")
        val intent = roleManager.createRequestRoleIntent(ROLE_SELF_MANAGED_CALLS)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_SELF_MANAGED_ROLE)
    }

    private fun requestStartupPermissionsIfNeeded() {
        val missingPermissions = ArrayList<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val notificationsGranted =
                checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            if (!notificationsGranted) {
                missingPermissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val micGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (!micGranted) {
            missingPermissions.add(android.Manifest.permission.RECORD_AUDIO)
        }

        val readPhoneGranted = checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        if (!readPhoneGranted) {
            missingPermissions.add(android.Manifest.permission.READ_PHONE_STATE)
        }

        val callPhoneGranted = checkSelfPermission(android.Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        if (!callPhoneGranted) {
            missingPermissions.add(android.Manifest.permission.CALL_PHONE)
        }

        Log.i(
            TAG,
            "Startup permissions status: POST_NOTIFICATIONS=${Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED} RECORD_AUDIO=$micGranted READ_PHONE_STATE=$readPhoneGranted CALL_PHONE=$callPhoneGranted"
        )

        if (missingPermissions.isEmpty()) {
            Log.i(TAG, "All startup permissions already granted")
            return
        }

        Log.i(TAG, "Requesting startup permissions: ${missingPermissions.joinToString()}")
        ActivityCompat.requestPermissions(
            this,
            missingPermissions.toTypedArray(),
            REQ_STARTUP_PERMISSIONS
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_STARTUP_PERMISSIONS) {
            if (grantResults.isEmpty()) {
                Log.w(TAG, "Startup permission result empty")
                return
            }
            val resultsByPermission = permissions.indices.joinToString { index ->
                val permission = permissions[index]
                val granted = grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
                "$permission=$granted"
            }
            Log.i(TAG, "Startup permission result: $resultsByPermission")
            return
        }
    }

    private fun requestFullScreenIntentIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (isFullScreenIntentAllowed()) return
        Log.d(TAG, "requestFullScreenIntentIfNeeded: launching gate")
        launchFullScreenIntentGate()
    }

    private fun isFullScreenIntentAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val notificationManager = getSystemService(NotificationManager::class.java)
        val allowed = notificationManager?.canUseFullScreenIntent() == true
        Log.d(TAG, "canUseFullScreenIntent=$allowed")
        return allowed
    }

    private fun launchFullScreenIntentGate() {
        Log.d(TAG, "Launching full-screen intent gate")
        val intent = Intent(this, FullScreenIntentSettingsActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun requestOverlayPermissionIfNeeded() {
        val canDraw = Settings.canDrawOverlays(this)
        if (canDraw) return
        if (isOverlayDialogVisible) return
        isOverlayDialogVisible = true
        AlertDialog.Builder(this)
            .setTitle("Autorisation obligatoire")
            .setMessage(
                "L'autorisation d'affichage par-dessus les autres applis est requise. " +
                    "Veuillez l'activer dans les reglages."
            )
            .setCancelable(false)
            .setPositiveButton("Activer") { _, _ ->
                isOverlayDialogVisible = false
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    data = Uri.fromParts("package", packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            .setOnDismissListener { isOverlayDialogVisible = false }
            .show()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQ_SELF_MANAGED_ROLE = 9001
        private const val REQ_STARTUP_PERMISSIONS = 9002
        private const val ROLE_SELF_MANAGED_CALLS = "android.app.role.SELF_MANAGED_CALLS"
    }
}
