package fr.celya.celyavox

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Handler
import android.os.Looper
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
    private var mediaPlayer: MediaPlayer? = null
    private var ringFocusRequest: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null
    private var isRinging = false
    private var titleView: TextView? = null
    private var subtitleView: TextView? = null
    private var acceptButton: Button? = null
    private var declineButton: Button? = null
    private var currentCallId: String = ""
    private var currentCallerId: String = ""
    private var waitingNativeCallIdForAccept = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var nativeCallIdTimeoutRunnable: Runnable? = null
    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                VoipEngine.ACTION_CALL_ENDED -> {
                    Log.i(TAG, "Received ACTION_CALL_ENDED; closing CallActivity")
                    stopRinging()
                    finish()
                }
                VoipEngine.ACTION_CALL_TERMINATE_REQUESTED -> {
                    Log.i(TAG, "Received ACTION_CALL_TERMINATE_REQUESTED; closing CallActivity")
                    stopRinging()
                    finish()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentCallId = intent?.getStringExtra(EXTRA_CALL_ID).orEmpty()
        currentCallerId = intent?.getStringExtra(EXTRA_CALLER_ID).orEmpty()
        lastLaunchAtMs = System.currentTimeMillis()
        Log.i(TAG, "onCreate callId=$currentCallId")
        registerCallEndedReceiver()
        applyLockScreenFlags()
        setContentView(buildContentView())
        startRinging()
        // Start timeout if we have a placeholder callId (waiting for native SIP call)
        if (isPushPlaceholderCallId(currentCallId)) {
            startNativeCallIdTimeout()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val nextCallId = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        val nextCallerId = intent.getStringExtra(EXTRA_CALLER_ID).orEmpty()
        Log.i(
            TAG,
            "onNewIntent received: prevCallId=$currentCallId nextCallId=$nextCallId prevCallerId=$currentCallerId nextCallerId=$nextCallerId"
        )
        if (nextCallId.isNotEmpty() && nextCallId == currentCallId) {
            Log.i(TAG, "onNewIntent duplicate callId=$nextCallId ignored")
            return
        }
        Log.i(TAG, "onNewIntent switching callId=$nextCallId")
        if (nextCallId.isNotEmpty()) currentCallId = nextCallId
        if (nextCallerId.isNotEmpty()) currentCallerId = nextCallerId
        // Cancel timeout when we receive the native callId
        if (!isPushPlaceholderCallId(nextCallId)) {
            cancelNativeCallIdTimeout()
        }
        acceptButton?.visibility = View.VISIBLE
        declineButton?.text = "Raccrocher"
        titleView?.text = if (currentCallerId.isNotEmpty()) currentCallerId else "Appel entrant"
        subtitleView?.text = ""  // No subtitle needed - CallerID in title
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
        // Don't stop ringing on stop - let it continue in background
        // Only stop when the activity is destroyed
        Log.d(TAG, "onStop")
    }

    override fun onPause() {
        // Don't stop ringing on pause - only on destroy
        // The activity may go to background but we want the ringing to continue
        super.onPause()
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy called, stopping ringing")
        stopRinging()
        isRinging = false
        cancelNativeCallIdTimeout()
        unregisterCallEndedReceiver()
        super.onDestroy()
    }

    private fun registerCallEndedReceiver() {
        val filter = IntentFilter().apply {
            addAction(VoipEngine.ACTION_CALL_ENDED)
            addAction(VoipEngine.ACTION_CALL_TERMINATE_REQUESTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callEndedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(callEndedReceiver, filter)
        }
    }

    private fun unregisterCallEndedReceiver() {
        try {
            unregisterReceiver(callEndedReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Call ended receiver already unregistered", e)
        }
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
            text = ""  // Empty subtitle - CallerID already shown in title
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
                stopRinging()
                tryAcceptAndOpenMain(callId)
            }
        }
        acceptButton = accept
        updateAcceptButtonState()

        val decline = Button(this).apply {
            text = "Raccrocher"
            setOnClickListener {
                val callId = currentCallId
                val hasNativeCallId = callId.isNotEmpty() && !isPushPlaceholderCallId(callId)
                if (hasNativeCallId) {
                    Log.i(TAG, "Hangup clicked, ending callId=$callId")
                    stopRinging()
                    val ok = PjsipEngine.instance.hangupCall(callId)
                    Log.i(TAG, "Hangup action result callId=$callId ok=$ok")
                    sendBroadcast(
                        Intent(VoipEngine.ACTION_CALL_TERMINATE_REQUESTED)
                            .setPackage(packageName)
                    )
                    Log.i(TAG, "Broadcasted ACTION_CALL_TERMINATE_REQUESTED from CallActivity")
                } else {
                    Log.i(TAG, "Hangup clicked before native callId; closing CallActivity only")
                    stopRinging()
                    launchMainActivityInBackground()
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
        if (isRinging) {
            return
        }
        isRinging = true
        val audioManager = getSystemService(AudioManager::class.java)
        val ringerMode = audioManager.ringerMode
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            return
        }

        // Set audio mode for incoming call (use NORMAL to allow speaker)
        audioManager.mode = AudioManager.MODE_NORMAL
        
        // Ensure stream volume is at maximum
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolume, 0)
        
        // Request audio focus before playing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { }
                .build()
            ringFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_RING,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }

        // Try MediaPlayer first
        try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
            
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setDataSource(this, uri)
            player.isLooping = true
            player.setOnErrorListener { mp, what, extra ->
                false
            }
            player.setOnPreparedListener {
                it.start()
            }
            player.prepareAsync()
            mediaPlayer = player
        } catch (e: Exception) {
            // Fallback to Ringtone if MediaPlayer fails
            try {
                val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE)
                val ring = RingtoneManager.getRingtone(this, uri)
                if (ring != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        ring.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ring.isLooping = true
                    }
                    ringtone = ring
                    ring.play()
                }
            } catch (e2: Exception) {
                Log.e(TAG, "Fallback Ringtone failed: ${e2.message}", e2)
            }
        }
        
        // Always vibrate on incoming call for better UX
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

    private fun stopRinging() {
        if (!isRinging) {
            return
        }
        isRinging = false
        
        // Stop MediaPlayer
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer!!.isPlaying) {
                    mediaPlayer!!.stop()
                }
                mediaPlayer!!.release()
                mediaPlayer = null
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping MediaPlayer: ${e.message}")
            }
        }
        
        // Stop Ringtone
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        
        // Abandon audio focus
        val audioManager = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (ringFocusRequest != null) {
                audioManager.abandonAudioFocusRequest(ringFocusRequest!!)
            }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        ringFocusRequest = null
    }

    private fun tryAcceptAndOpenMain(callId: String) {
        Log.i(TAG, "Accept clicked, accepting callId=$callId")
        val ok = PjsipEngine.instance.acceptCall(callId)
        Log.i(TAG, "Accept action result callId=$callId ok=$ok")
        if (!ok) {
            Log.w(TAG, "Accept failed; keeping CallActivity open for retry")
            // Restart ringing if accept failed
            startRinging()
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

    private fun launchMainActivityInBackground() {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_BACKGROUND_LAUNCH, true)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to launch MainActivity in background", e)
        }
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
            Log.i(
                TAG,
                "Accept disabled: waiting native SIP callId (currentCallId=$currentCallId placeholder=${isPushPlaceholderCallId(currentCallId)})"
            )
            return
        }
        button.isEnabled = true
        button.text = "Répondre"
        Log.i(TAG, "Accept enabled with native callId=$currentCallId")
    }

    private fun startNativeCallIdTimeout() {
        cancelNativeCallIdTimeout()
        nativeCallIdTimeoutRunnable = Runnable {
            Log.w(TAG, "Native callId timeout reached (5s) with placeholder callId=$currentCallId; closing CallActivity")
            stopRinging()
            finish()
        }
        mainHandler.postDelayed(nativeCallIdTimeoutRunnable!!, NATIVE_CALL_ID_TIMEOUT_MS)
        Log.i(TAG, "Started native callId timeout (${NATIVE_CALL_ID_TIMEOUT_MS}ms)")
    }

    private fun cancelNativeCallIdTimeout() {
        nativeCallIdTimeoutRunnable?.let { runnable ->
            mainHandler.removeCallbacks(runnable)
            Log.i(TAG, "Cancelled native callId timeout")
        }
        nativeCallIdTimeoutRunnable = null
    }

    companion object {
        private const val TAG = "CallActivity"
        private const val NATIVE_CALL_ID_TIMEOUT_MS = 5000L  // 5 seconds
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
