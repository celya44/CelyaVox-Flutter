package fr.celya.celyavox

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
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
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
    private var currentCallId: String = ""
    private var currentCallerId: String = ""
    private var waitingNativeCallIdForAccept = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentCallId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
        currentCallerId = intent?.getStringExtra(EXTRA_CALLER_ID).orEmpty()
        lastLaunchAtMs = System.currentTimeMillis()
        Log.i(TAG, "onCreate callId=$currentCallId")
        applyLockScreenFlags()
        setContentView(buildContentView())
        startRinging()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val nextCallId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val nextCallerId = intent.getStringExtra(EXTRA_CALLER_ID).orEmpty()
        if (nextCallId.isNotEmpty() && nextCallId == currentCallId) {
            Log.i(TAG, "onNewIntent duplicate callId=$nextCallId ignored")
            return
        }
        Log.i(TAG, "onNewIntent switching callId=$nextCallId")
        currentCallId = nextCallId
        currentCallerId = nextCallerId
        acceptButton?.visibility = View.VISIBLE
        declineButton?.text = "Raccrocher"
        titleView?.text = if (currentCallerId.isNotEmpty()) currentCallerId else "Appel entrant"
        subtitleView?.text = if (currentCallId.isNotEmpty()) "Call ID: $currentCallId" else ""
        updateAcceptButtonState()
        startRinging()
        if (waitingNativeCallIdForAccept && !isPushPlaceholderCallId(currentCallId)) {
            Log.i(TAG, "Native callId received after pending accept, auto-answering callId=$currentCallId")
            waitingNativeCallIdForAccept = false
            tryAcceptAndOpenMain(currentCallId)
        }
    }

    override fun onStart() {
        super.onStart()
        isVisible = true
        visibleCallId = currentCallId
        Log.d(TAG, "onStart visibleCallId=$visibleCallId")
    }

    override fun onStop() {
        super.onStop()
        isVisible = false
        visibleCallId = null
        Log.d(TAG, "onStop")
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
        val callId = currentCallId
        val callerId = currentCallerId

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 72, 48, 72)
        }

        val icon = ImageView(this).apply {
            setImageResource(android.R.drawable.sym_call_incoming)
            layoutParams = LinearLayout.LayoutParams(120, 120).apply {
                bottomMargin = 24
            }
        }

        val title = TextView(this).apply {
            text = if (callerId.isNotEmpty()) callerId else "Appel entrant"
            textSize = 22f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10
            }
        }
        titleView = title

        val subtitle = TextView(this).apply {
            text = if (callId.isNotEmpty()) "ID: $callId" else ""
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 28
            }
        }
        subtitleView = subtitle

        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val accept = Button(this).apply {
            text = "Répondre"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 16
            }
            setOnClickListener {
                val callId = currentCallId
                if (callId.isEmpty()) {
                    Log.w(TAG, "Accept clicked with empty callId")
                    return@setOnClickListener
                }
                if (isPushPlaceholderCallId(callId)) {
                    waitingNativeCallIdForAccept = true
                    isEnabled = false
                    text = "Connexion..."
                    Log.w(TAG, "Accept clicked with placeholder callId=$callId; waiting for native callId")
                    return@setOnClickListener
                }
                tryAcceptAndOpenMain(callId)
            }
        }
        acceptButton = accept
        updateAcceptButtonState()

        val decline = Button(this).apply {
            text = "Raccrocher"
            setOnClickListener {
                val callId = currentCallId
                if (callId.isNotEmpty()) {
                    Log.i(TAG, "Hangup clicked, ending callId=$callId")
                    val ok = PjsipEngine.instance.hangupCall(callId)
                    Log.i(TAG, "Hangup action result callId=$callId ok=$ok")
                    sendBroadcast(
                        Intent(VoipEngine.ACTION_CALL_TERMINATE_REQUESTED)
                            .setPackage(packageName)
                    )
                    Log.i(TAG, "Broadcasted ACTION_CALL_TERMINATE_REQUESTED from CallActivity")
                }
                finish()
            }
        }
        declineButton = decline

        actionsRow.addView(accept)
        actionsRow.addView(decline)

        root.addView(icon)
        root.addView(title)
        root.addView(subtitle)
        root.addView(actionsRow)
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

    private fun tryAcceptAndOpenMain(callId: String) {
        Log.i(TAG, "Accept clicked, accepting callId=$callId")
        val ok = PjsipEngine.instance.acceptCall(callId)
        Log.i(TAG, "Accept action result callId=$callId ok=$ok")
        if (!ok) {
            Log.w(TAG, "Accept failed; keeping CallActivity open for retry")
            updateAcceptButtonState()
            return
        }
        Log.i(TAG, "Launching MainActivity after answer")
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

    private fun isPushPlaceholderCallId(callId: String): Boolean {
        return callId.startsWith("wake_")
    }

    private fun updateAcceptButtonState() {
        val button = acceptButton ?: return
        val waitingNative = currentCallId.isBlank() || isPushPlaceholderCallId(currentCallId)
        if (waitingNative) {
            button.isEnabled = false
            button.text = "Connexion..."
            Log.i(TAG, "Accept disabled: waiting native SIP callId (currentCallId=$currentCallId)")
            return
        }
        button.isEnabled = true
        button.text = "Répondre"
        Log.i(TAG, "Accept enabled with native callId=$currentCallId")
    }

    companion object {
        private const val TAG = "CallActivity"
        @Volatile
        var isVisible: Boolean = false
        @Volatile
        var visibleCallId: String? = null
        @Volatile
        var lastLaunchAtMs: Long = 0L
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_CALLER_ID = "callerId"
    }
}
