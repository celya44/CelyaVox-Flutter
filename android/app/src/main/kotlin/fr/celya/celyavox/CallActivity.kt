package fr.celya.celyavox

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Minimal native call UI displayed over lock screen. No Flutter dependency.
 */
class CallActivity : AppCompatActivity() {

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var acceptButton: Button? = null
    private var declineButton: Button? = null
    private var isInCallMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate callId=${intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()}")
        applyLockScreenFlags()
        setContentView(buildContentView())
        startRinging()
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    private fun applyLockScreenFlags() {
        // Ensure we appear on top of the lock screen and wake the device.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
    }

    private fun buildContentView(): View {
        val callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val callerId = intent?.getStringExtra(EXTRA_CALLER_ID).orEmpty()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 72, 48, 72)
        }

        val title = TextView(this).apply {
            text = if (callerId.isNotEmpty()) callerId else "Appel entrant"
            textSize = 22f
        }
        titleView = title

        val subtitle = TextView(this).apply {
            text = if (callId.isNotEmpty()) "Call ID: $callId" else ""
            textSize = 14f
        }
        subtitleView = subtitle

        val accept = Button(this).apply {
            text = "Répondre"
            setOnClickListener {
                val callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
                if (callId.isNotEmpty()) {
                    Log.i(TAG, "Accept clicked, accepting callId=$callId")
                    PjsipEngine.instance.acceptCall(callId)
                }
                dismissKeyguardIfPossible()
                if (isDeviceLocked()) {
                    Log.i(TAG, "Device still locked after answer; staying in CallActivity in-call mode")
                    enterInCallMode(callId)
                    return@setOnClickListener
                }
                Log.i(TAG, "Device unlocked after answer; launching MainActivity")
                val appIntent = Intent(this@CallActivity, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MainActivity.EXTRA_FROM_ACCEPTED_CALL, true)
                    putExtra(MainActivity.EXTRA_ACCEPTED_CALL_ID, callId)
                }
                startActivity(appIntent)
                finish()
            }
        }
        acceptButton = accept

        val decline = Button(this).apply {
            text = "Raccrocher"
            setOnClickListener {
                val callId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
                if (callId.isNotEmpty()) {
                    Log.i(TAG, "Hangup clicked, ending callId=$callId")
                    PjsipEngine.instance.hangupCall(callId)
                }
                finish()
            }
        }
        declineButton = decline

        root.addView(title)
        root.addView(subtitle)
        root.addView(accept)
        root.addView(decline)
        return root
    }

    private fun enterInCallMode(callId: String) {
        if (isInCallMode) return
        isInCallMode = true
        stopRinging()
        titleView?.text = "Appel en cours"
        subtitleView?.text = if (callId.isNotEmpty()) "Call ID: $callId" else ""
        acceptButton?.visibility = View.GONE
        declineButton?.text = "Raccrocher"
    }

    private fun startRinging() {
        val audioManager = getSystemService(AudioManager::class.java)
        val ringerMode = audioManager.ringerMode
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) return

        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(
                this,
                RingtoneManager.TYPE_RINGTONE
            )
            val ring = RingtoneManager.getRingtone(this, uri) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                ring.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ring.isLooping = true
            }
            ringtone = ring
            ring.play()
        }

        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator = vib
            if (vib != null && vib.hasVibrator()) {
                val pattern = longArrayOf(0, 500, 500)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createWaveform(pattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(pattern, 0)
                }
            }
        }
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
    }

    private fun dismissKeyguardIfPossible() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            Log.d(TAG, "Requesting dismiss keyguard")
            keyguardManager?.requestDismissKeyguard(this, null)
            return
        }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
    }

    private fun isDeviceLocked(): Boolean {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        val locked = keyguardManager?.isKeyguardLocked == true
        Log.d(TAG, "isDeviceLocked=$locked")
        return locked
    }

    companion object {
        private const val TAG = "CallActivity"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_CALLER_ID = "callerId"
    }
}
