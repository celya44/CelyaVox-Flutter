package fr.celya.celyavox

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class FullScreenIntentSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            AlertDialog.Builder(this)
                .setTitle("Autorisation obligatoire")
                .setMessage(
                    "L'autorisation plein ecran est obligatoire pour recevoir les appels. " +
                        "Veuillez l'activer dans les reglages."
                )
                .setCancelable(false)
                .setPositiveButton("Activer") { _, _ ->
                    val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                        data = android.net.Uri.fromParts("package", packageName, null)
                    }
                    startActivity(intent)
                    finish()
                }
                .setOnDismissListener { finish() }
                .show()
            return
        }
        finish()
    }
}
