package fr.celya.celyavox

import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : FlutterActivity() {

    private var voipEngine: VoipEngine? = null
    private var methodChannel: VoipMethodChannel? = null
    private var provisioningChannel: ProvisioningMethodChannel? = null
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
        requestNotificationPermissionIfNeeded()
        requestFullScreenIntentIfNeeded()
        requestMicrophonePermissionIfNeeded()
        requestPhonePermissionIfNeeded()
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
            showRoleStatus("Rôle SELF_MANAGED_CALLS non disponible sur cet appareil")
            return
        }
        if (roleManager.isRoleHeld(ROLE_SELF_MANAGED_CALLS)) {
            Log.i(TAG, "ROLE_SELF_MANAGED_CALLS already granted")
            showRoleStatus("Rôle SELF_MANAGED_CALLS déjà accordé")
            VoipConnectionService.registerSelfManaged(this)
            return
        }
        Log.i(TAG, "Requesting ROLE_SELF_MANAGED_CALLS")
        showRoleStatus("Demande du rôle SELF_MANAGED_CALLS…")
        val intent = roleManager.createRequestRoleIntent(ROLE_SELF_MANAGED_CALLS)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_SELF_MANAGED_ROLE)
    }

    private fun showRoleStatus(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            )
        ) {
            showRoleStatus("Autorise les notifications pour recevoir les appels")
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
            REQ_NOTIFICATION_PERMISSION
        )
    }

    private fun requestMicrophonePermissionIfNeeded() {
        val granted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                android.Manifest.permission.RECORD_AUDIO
            )
        ) {
            showRoleStatus("Autorise le micro pour les appels")
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.RECORD_AUDIO),
            REQ_MIC_PERMISSION
        )
    }

    private fun requestPhonePermissionIfNeeded() {
        val granted = checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                android.Manifest.permission.READ_PHONE_STATE
            )
        ) {
            showRoleStatus("Autorise l’accès au téléphone pour gérer les appels")
        }
        ActivityCompat.requestPermissions(
            this,
            arrayOf(android.Manifest.permission.READ_PHONE_STATE),
            REQ_PHONE_PERMISSION
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_NOTIFICATION_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                showRoleStatus("Notifications autorisées")
                return
            }
            showRoleStatus("Notifications refusées — ouvre les réglages")
            openNotificationSettings()
            return
        }
        if (requestCode == REQ_MIC_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                showRoleStatus("Micro autorisé")
                return
            }
            showRoleStatus("Micro refusé — ouvre les réglages")
            openAppSettings()
            return
        }
        if (requestCode == REQ_PHONE_PERMISSION) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                showRoleStatus("Téléphone autorisé")
                return
            }
            showRoleStatus("Téléphone refusé — ouvre les réglages")
            openAppSettings()
        }
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun requestFullScreenIntentIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val notificationManager = getSystemService(NotificationManager::class.java)
        val canUse = notificationManager?.canUseFullScreenIntent() == true
        if (canUse) return
        showRoleStatus("Autorise l’affichage plein écran des appels")
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQ_SELF_MANAGED_ROLE = 9001
        private const val REQ_NOTIFICATION_PERMISSION = 9002
        private const val REQ_MIC_PERMISSION = 9003
        private const val REQ_PHONE_PERMISSION = 9004
        private const val ROLE_SELF_MANAGED_CALLS = "android.app.role.SELF_MANAGED_CALLS"
    }
}
