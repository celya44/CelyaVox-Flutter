package fr.celya.celyavox

import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class FullScreenIntentSettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FullScreenGate"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")
        setContentView(R.layout.activity_full_screen_intent_gate)
        findViewById<Button>(R.id.full_screen_settings_button).setOnClickListener {
            Log.d(TAG, "Settings button clicked")
            openFullScreenIntentSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        if (isFullScreenIntentAllowed()) {
            Log.d(TAG, "Full-screen intent already allowed; finishing")
            finish()
        }
    }

    override fun onBackPressed() {
        Toast.makeText(this, "Autorisation obligatoire", Toast.LENGTH_SHORT).show()
    }

    private fun isFullScreenIntentAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val notificationManager = getSystemService(NotificationManager::class.java)
        val allowed = notificationManager?.canUseFullScreenIntent() == true
        Log.d(TAG, "canUseFullScreenIntent=$allowed")
        return allowed
    }

    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.fromParts("package", packageName, null)
        }
        Log.d(TAG, "Opening full-screen intent settings")
        startActivity(intent)
    }
}
