package com.celya.voip.telecom

import android.net.Uri
import android.os.Bundle
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

class TelecomConnectionService : ConnectionService() {
    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        return createVoipConnection(request, isIncoming = true)
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest?
    ): Connection {
        return createVoipConnection(request, isIncoming = false)
    }

    private fun createVoipConnection(request: ConnectionRequest?, isIncoming: Boolean): Connection {
        val connection = VoipConnection()
        connection.connectionProperties = Connection.PROPERTY_SELF_MANAGED
        connection.setAddress(request?.address ?: Uri.EMPTY, TelecomManager.PRESENTATION_ALLOWED)
        connection.setInitializing()
        connection.setActive()
        if (isIncoming) {
            connection.setRinging()
        }
        connection.extras = Bundle().apply {
            putBoolean(EXTRA_INCOMING, isIncoming)
        }
        return connection
    }

    private class VoipConnection : Connection() {
        override fun onAnswer() {
            setActive()
        }

        override fun onDisconnect() {
            setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
            destroy()
        }

        override fun onReject() {
            setDisconnected(DisconnectCause(DisconnectCause.REJECTED))
            destroy()
        }
    }

    companion object {
        private const val EXTRA_INCOMING = "extra_incoming"
    }
}
