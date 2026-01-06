package com.example.voip

import android.os.Build
import android.telecom.Connection
import android.telecom.DisconnectCause

class VoipConnection(
    private val callId: String? = null,
    private val callerId: String? = null
) : Connection() {

    init {
        // Self-managed connection per Telecom requirements.
        connectionProperties = connectionProperties or PROPERTY_SELF_MANAGED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setAudioModeIsVoip(true)
        }
        if (!callerId.isNullOrEmpty()) {
            setCallerDisplayName(callerId, PRESENTATION_ALLOWED)
        }
        setInitializing()
    }

    override fun onAnswer() {
        setActive()
    }

    override fun onReject() {
        setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
        destroy()
    }

    override fun onDisconnect() {
        setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
        destroy()
    }

    override fun onAbort() {
        setDisconnected(DisconnectCause(DisconnectCause.CANCELED))
        destroy()
    }

    fun markRinging() {
        setRinging()
    }

    fun markDialing() {
        setDialing()
    }
}
