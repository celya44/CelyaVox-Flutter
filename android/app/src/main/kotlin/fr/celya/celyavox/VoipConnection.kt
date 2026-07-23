package fr.celya.celyavox

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.telecom.Connection
import android.telecom.DisconnectCause
import android.telecom.TelecomManager
import android.util.Log

open class VoipConnection(
    private val context: Context,
    private val callId: String? = null,
    private val callerId: String? = null
) : Connection() {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var previousMode: Int? = null
    private var previousSpeakerphone: Boolean? = null
    private var previousMicMute: Boolean? = null
    private var ringtone: Ringtone? = null
    private var ringFocusRequest: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null

    init {
        // Self-managed connection per Telecom requirements.
        connectionProperties = connectionProperties or PROPERTY_SELF_MANAGED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setAudioModeIsVoip(true)
        }
        if (!callerId.isNullOrEmpty()) {
            setCallerDisplayName(callerId, TelecomManager.PRESENTATION_ALLOWED)
        }
        setInitializing()
    }

    override fun onAnswer() {
        stopRinging()
        startAudio()
        setActive()
    }

    override fun onReject() {
        stopRinging()
        stopAudio()
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    override fun onDisconnect() {
        stopRinging()
        stopAudio()
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    override fun onAbort() {
        stopRinging()
        stopAudio()
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        destroy()
    }

    fun onCallConnected() {
        stopRinging()
        startAudio()
    }

    fun stopRingingNow() {
        stopRinging()
    }

    private fun startAudio() {
        if (previousMode != null) return
        previousMode = audioManager.mode
        previousSpeakerphone = audioManager.isSpeakerphoneOn
        previousMicMute = audioManager.isMicrophoneMute

        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = false

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
            audioFocusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    private fun stopAudio() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        audioFocusRequest = null

        previousMode?.let { audioManager.mode = it }
        previousSpeakerphone?.let { audioManager.isSpeakerphoneOn = it }
        previousMicMute?.let { audioManager.isMicrophoneMute = it }
        previousMode = null
        previousSpeakerphone = null
        previousMicMute = null
    }

    fun markRinging() {
        Log.i("VoipConnection", ">>> VOIP_CONN_RING: markRinging() called, callId=$callId")
        setRinging()
        startRinging()
    }

    fun markDialing() {
        setDialing()
    }

    private fun startRinging() {
        Log.i("VoipConnection", ">>> VOIP_CONN_RING: startRinging() called, callId=$callId")
        if (ringtone != null) {
            Log.i("VoipConnection", ">>> VOIP_CONN_RING: Ringtone already active, skipping")
            return
        }
        val ringerMode = audioManager.ringerMode
        Log.i("VoipConnection", ">>> VOIP_CONN_RING: ringerMode=$ringerMode (0=SILENT, 1=VIBRATE, 2=NORMAL)")
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) {
            Log.i("VoipConnection", ">>> VOIP_CONN_RING: Phone is in SILENT mode")
            return
        }

        val ring: Ringtone? = if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            Log.i("VoipConnection", ">>> VOIP_CONN_RING: Attempting to get ringtone")
            val uri = RingtoneManager.getActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE
            )
            Log.i("VoipConnection", ">>> VOIP_CONN_RING: Ringtone URI=$uri")
            RingtoneManager.getRingtone(context, uri)?.also { r ->
                Log.i("VoipConnection", ">>> VOIP_CONN_RING: Ringtone object created")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    r.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    r.isLooping = true
                }
            }
        } else {
            Log.i("VoipConnection", ">>> VOIP_CONN_RING: Not NORMAL mode, ring=null")
            null
        }

        // Set audio mode for incoming call
        Log.i("VoipConnection", ">>> VOIP_CONN_RING: Setting audio mode to RINGTONE")
        audioManager.mode = AudioManager.MODE_RINGTONE
        
        // Ensure stream volume is at maximum
        Log.i("VoipConnection", ">>> VOIP_CONN_RING: Setting STREAM_RING volume to maximum")
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
        audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolume, 0)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.i("VoipConnection", ">>> VOIP_CONN_RING: Requesting audio focus (VOICE_COMMUNICATION)")
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
            Log.i("VoipConnection", ">>> VOIP_CONN_RING: Audio focus requested")
        } else {
            Log.i("VoipConnection", ">>> VOIP_CONN_RING: Requesting audio focus (pre-O, STREAM_RING)")
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                null,
                AudioManager.STREAM_RING,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }

        if (ring != null) {
            Log.i("VoipConnection", ">>> VOIP_CONN_RING: Starting ringtone playback")
            ringtone = ring
            
            // Ensure stream volume is at maximum before playing
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_RING)
            Log.i("VoipConnection", ">>> VOIP_CONN_RING: Setting STREAM_RING volume to $maxVolume (max)")
            audioManager.setStreamVolume(AudioManager.STREAM_RING, maxVolume, 0)
            
            ring.play()
            Log.i("VoipConnection", ">>> VOIP_CONN_RING: Ringtone playback started")
            
            // Always vibrate on incoming call for better UX
            Log.i("VoipConnection", ">>> VOIP_CONN_RING: Starting vibration pattern")
            val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(VibratorManager::class.java)
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
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
                Log.i("VoipConnection", ">>> VOIP_CONN_RING: Vibration started")
            }
            return
        } else {
            Log.w("VoipConnection", ">>> VOIP_CONN_RING: ring is null, no ringtone to play")
        }

        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
            Log.i("VoipConnection", ">>> VOIP_CONN_RING: Phone in VIBRATE mode (vibration already activated above)")
        }
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
        vibrator?.cancel()
        vibrator = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ringFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        ringFocusRequest = null
    }
}
