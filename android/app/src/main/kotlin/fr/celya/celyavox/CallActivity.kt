package fr.celya.celyavox

import android.os.Build
import android.os.Bundle
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        val subtitle = TextView(this).apply {
            text = if (callId.isNotEmpty()) "Call ID: $callId" else ""
            textSize = 14f
        }

        val accept = Button(this).apply {
            text = "Répondre"
            setOnClickListener {
                // TODO: integrate with ConnectionService answer flow
                finish()
            }
        }

        val decline = Button(this).apply {
            text = "Raccrocher"
            setOnClickListener {
                // TODO: integrate with ConnectionService reject/hangup flow
                finish()
            }
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(accept)
        root.addView(decline)
        return root
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

    companion object {
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_CALLER_ID = "callerId"
    }
}
