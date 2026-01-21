package fr.celya.celyavox

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
        startAudio()
        setActive()
    }

    override fun onReject() {
        stopAudio()
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    override fun onDisconnect() {
        stopAudio()
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    override fun onAbort() {
        stopAudio()
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        destroy()
    }

    fun onCallConnected() {
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
    }

    fun markDialing() {
        setDialing()
    }
}
