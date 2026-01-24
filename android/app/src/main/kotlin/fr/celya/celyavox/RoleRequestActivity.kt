package fr.celya.celyavox

import android.app.role.RoleManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class RoleRequestActivity : AppCompatActivity() {

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
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            finish()
            return
        }
        val roleManager = getSystemService(RoleManager::class.java)
        if (!roleManager.isRoleAvailable(ROLE_SELF_MANAGED_CALLS)) {
            finish()
            return
        }
        if (roleManager.isRoleHeld(ROLE_SELF_MANAGED_CALLS)) {
            Log.i(TAG, "ROLE_SELF_MANAGED_CALLS already granted")
            VoipConnectionService.registerSelfManaged(this)
            finish()
            return
        }
        Log.i(TAG, "Requesting ROLE_SELF_MANAGED_CALLS")
        val intent = roleManager.createRequestRoleIntent(ROLE_SELF_MANAGED_CALLS)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_SELF_MANAGED_ROLE)
    }

    companion object {
        private const val TAG = "RoleRequestActivity"
        private const val REQ_SELF_MANAGED_ROLE = 9101
        private const val ROLE_SELF_MANAGED_CALLS = "android.app.role.SELF_MANAGED_CALLS"
    }
}
