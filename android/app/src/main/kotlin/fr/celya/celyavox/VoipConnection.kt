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
        setRinging()
        startRinging()
    }

    fun markDialing() {
        setDialing()
    }

    private fun startRinging() {
        if (ringtone != null) return
        val ringerMode = audioManager.ringerMode
        if (ringerMode == AudioManager.RINGER_MODE_SILENT) return

        val ring: Ringtone? = if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(
                context,
                RingtoneManager.TYPE_RINGTONE
            )
            RingtoneManager.getRingtone(context, uri)?.also { r ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    r.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    r.isLooping = true
                }
            }
        } else {
            null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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

        if (ring != null) {
            ringtone = ring
            ring.play()
        }

        if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
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
            }
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
