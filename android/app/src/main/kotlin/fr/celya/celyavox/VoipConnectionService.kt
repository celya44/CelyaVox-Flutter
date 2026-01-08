package fr.celya.celyavox

import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.telecom.Connection
import android.telecom.ConnectionRequest
import android.telecom.ConnectionService
import android.telecom.DisconnectCause
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import java.util.concurrent.ConcurrentHashMap

class VoipConnectionService : ConnectionService() {

    private val engine = PjsipEngine.instance

    override fun onCreateIncomingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest
    ): Connection {
        if (!isSupportedSdk()) {
            return failedConnection(DisconnectCause.ERROR)
        }
        if (!isOurAccount(request.accountHandle)) {
            return failedConnection(DisconnectCause.ERROR)
        }
        val callId = request.extras?.getString(EXTRA_CALL_ID)
        val callerId = request.extras?.getString(EXTRA_CALLER_ID)
        val connection = createManagedConnection(callId, callerId, incoming = true)
        connection.markRinging()
        registerConnection(callId, connection)
        return connection
    }

    override fun onCreateOutgoingConnection(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest
    ): Connection {
        if (!isSupportedSdk()) {
            return failedConnection(DisconnectCause.ERROR)
        }
        if (!isOurAccount(request.accountHandle)) {
            return failedConnection(DisconnectCause.ERROR)
        }
        val callId = request.extras?.getString(EXTRA_CALL_ID)
        val connection = createManagedConnection(callId, null, incoming = false)
        connection.markDialing()
        registerConnection(callId, connection)
        return connection
    }

    override fun onCreateOutgoingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest
    ) {
        super.onCreateOutgoingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.w(TAG, "Outgoing connection failed for request: ${request.extras}")
    }

    override fun onCreateIncomingConnectionFailed(
        connectionManagerPhoneAccount: PhoneAccountHandle?,
        request: ConnectionRequest
    ) {
        super.onCreateIncomingConnectionFailed(connectionManagerPhoneAccount, request)
        Log.w(TAG, "Incoming connection failed for request: ${request.extras}")
    }

    private fun failedConnection(code: Int): Connection {
        return Connection.createFailedConnection(DisconnectCause(code))
    }

    private fun createManagedConnection(callId: String?, callerId: String?, incoming: Boolean): VoipConnection {
        return object : VoipConnection(callId = callId, callerId = callerId) {
            override fun onAnswer() {
                engine.acceptCall(callId ?: "")
                super.onAnswer()
            }

            override fun onReject() {
                engine.hangupCall(callId ?: "")
                super.onReject()
                unregisterConnection(callId)
            }

            override fun onDisconnect() {
                engine.hangupCall(callId ?: "")
                super.onDisconnect()
                unregisterConnection(callId)
            }

            override fun onAbort() {
                engine.hangupCall(callId ?: "")
                super.onAbort()
                unregisterConnection(callId)
            }
        }
    }

    private fun isSupportedSdk(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private fun isOurAccount(handle: PhoneAccountHandle?): Boolean {
        return handle?.id == PHONE_ACCOUNT_ID
    }

    companion object {
        private const val TAG = "VoipConnectionService"
        private const val PHONE_ACCOUNT_LABEL = "VoIP Self-Managed"
        const val PHONE_ACCOUNT_ID = "voip-self-managed"
        const val EXTRA_CALL_ID = "callId"
        const val EXTRA_CALLER_ID = "callerId"

        private val connections = ConcurrentHashMap<String, VoipConnection>()

        fun registerSelfManaged(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.w(TAG, "Self-managed ConnectionService requires Android 10+")
                return
            }
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val handle = PhoneAccountHandle(
                ComponentName(context, VoipConnectionService::class.java),
                PHONE_ACCOUNT_ID
            )
            val account = PhoneAccount.builder(handle, PHONE_ACCOUNT_LABEL)
                .setCapabilities(PhoneAccount.CAPABILITY_SELF_MANAGED)
                .setSupportedUriSchemes(listOf(PhoneAccount.SCHEME_SIP, PhoneAccount.SCHEME_TEL))
                .build()
            try {
                telecomManager.registerPhoneAccount(account)
                Log.i(TAG, "Registered self-managed PhoneAccount: ${account.accountHandle.id}")
            } catch (sec: SecurityException) {
                // Devices will throw if MANAGE_OWN_CALLS/role not granted; avoid crashing app.
                Log.e(TAG, "Failed to register self-managed PhoneAccount (missing permission/role)", sec)
            }
        }

        fun unregisterSelfManaged(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val handle = PhoneAccountHandle(
                ComponentName(context, VoipConnectionService::class.java),
                PHONE_ACCOUNT_ID
            )
            telecomManager.unregisterPhoneAccount(handle)
            Log.i(TAG, "Unregistered self-managed PhoneAccount: ${handle.id}")
        }

        fun startIncomingCall(context: Context, callId: String, callerId: String?) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val handle = PhoneAccountHandle(
                ComponentName(context, VoipConnectionService::class.java),
                PHONE_ACCOUNT_ID
            )
            val extras = android.os.Bundle().apply {
                putString(EXTRA_CALL_ID, callId)
                putString(EXTRA_CALLER_ID, callerId)
            }
            telecomManager.addNewIncomingCall(handle, extras)
        }

        fun markCallActive(callId: String) {
            connections[callId]?.setActive()
        }

        fun markCallEnded(callId: String) {
            connections[callId]?.apply {
                setDisconnected(DisconnectCause(DisconnectCause.LOCAL))
                destroy()
            }
            connections.remove(callId)
        }

        @JvmStatic
        fun registerConnection(callId: String?, connection: VoipConnection) {
            if (callId != null) connections[callId] = connection
        }

        @JvmStatic
        fun unregisterConnection(callId: String?) {
            if (callId != null) connections.remove(callId)
        }
    }
}
