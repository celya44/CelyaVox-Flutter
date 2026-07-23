package fr.celya.celyavox

import android.content.ComponentName
import android.content.Context
import android.app.role.RoleManager
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
        Log.i(TAG, ">>> TELECOM_CONNECTION: onCreateIncomingConnection() called")
        if (!isSupportedSdk()) {
            Log.w(TAG, ">>> TELECOM_CONNECTION: SDK not supported, returning failedConnection")
            return failedConnection(DisconnectCause.ERROR)
        }
        if (!isOurAccount(request.accountHandle)) {
            Log.w(TAG, ">>> TELECOM_CONNECTION: Account handle mismatch, returning failedConnection")
            return failedConnection(DisconnectCause.ERROR)
        }
        val callId = request.extras?.getString(EXTRA_CALL_ID)
        val callerId = request.extras?.getString(EXTRA_CALLER_ID)
        Log.i(TAG, ">>> TELECOM_CONNECTION: Creating managed connection, callId=$callId callerId=$callerId")
        val connection = createManagedConnection(callId, callerId, incoming = true)
        Log.i(TAG, ">>> TELECOM_CONNECTION: Calling connection.markRinging()")
        connection.markRinging()
        registerConnection(callId, connection)
        Log.i(TAG, ">>> TELECOM_CONNECTION: Registered connection, returning")
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
        return object : VoipConnection(applicationContext, callId = callId, callerId = callerId) {
            override fun onAnswer() {
                val id = callId ?: ""
                Log.i(TAG, "ConnectionService onAnswer callId=$id")
                val ok = engine.acceptCall(id)
                Log.i(TAG, "ConnectionService onAnswer acceptCall result callId=$id ok=$ok")
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

        fun registerSelfManaged(context: Context): Boolean {
            Log.i(TAG, ">>> TELECOM_REGISTER: registerSelfManaged() called")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.w(TAG, ">>> TELECOM_REGISTER: Self-managed requires Android 10+, returning false")
                return false
            }
            if (!isSelfManagedRoleGranted(context)) {
                Log.w(TAG, ">>> TELECOM_REGISTER: ROLE_SELF_MANAGED_CALLS not granted, returning false")
                return false
            }
            Log.i(TAG, ">>> TELECOM_REGISTER: Role granted, registering PhoneAccount")
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
                Log.i(TAG, ">>> TELECOM_REGISTER: Successfully registered PhoneAccount: ${account.accountHandle.id}")
                return true
            } catch (sec: SecurityException) {
                // Devices will throw if MANAGE_OWN_CALLS/role not granted; avoid crashing app.
                Log.e(TAG, ">>> TELECOM_REGISTER: Failed to register PhoneAccount (missing permission/role)", sec)
                return false
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

        fun startIncomingCall(context: Context, callId: String, callerId: String?): Boolean {
            Log.i(TAG, ">>> TELECOM: startIncomingCall() callId=$callId callerId=$callerId")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.w(TAG, ">>> TELECOM: SDK version < Q, returning false")
                return false
            }
            if (!isSelfManagedRoleGranted(context)) {
                Log.w(TAG, ">>> TELECOM: Skipping Telecom incoming call: role not granted")
                return false
            }
            Log.i(TAG, ">>> TELECOM: Role granted, proceeding with addNewIncomingCall()")
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            val handle = PhoneAccountHandle(
                ComponentName(context, VoipConnectionService::class.java),
                PHONE_ACCOUNT_ID
            )
            val extras = android.os.Bundle().apply {
                putString(EXTRA_CALL_ID, callId)
                putString(EXTRA_CALLER_ID, callerId)
            }
            try {
                Log.i(TAG, ">>> TELECOM: Calling telecomManager.addNewIncomingCall()")
                telecomManager.addNewIncomingCall(handle, extras)
                Log.i(TAG, ">>> TELECOM: addNewIncomingCall() succeeded, returning true")
                return true
            } catch (sec: SecurityException) {
                Log.e(TAG, ">>> TELECOM: PhoneAccount not registered; attempting re-register", sec)
                try {
                    Log.i(TAG, ">>> TELECOM: Attempting to register self-managed")
                    if (registerSelfManaged(context)) {
                        Log.i(TAG, ">>> TELECOM: Re-registration succeeded, retrying addNewIncomingCall()")
                        telecomManager.addNewIncomingCall(handle, extras)
                        Log.i(TAG, ">>> TELECOM: addNewIncomingCall() succeeded after re-register, returning true")
                        return true
                    } else {
                        Log.w(TAG, ">>> TELECOM: Re-registration failed")
                    }
                } catch (inner: SecurityException) {
                    Log.e(TAG, ">>> TELECOM: Failed to add incoming call after re-register", inner)
                }
            }
            Log.w(TAG, ">>> TELECOM: startIncomingCall() returning false")
            return false
        }

        private fun isSelfManagedRoleGranted(context: Context): Boolean {
            Log.i(TAG, ">>> TELECOM: isSelfManagedRoleGranted() called")
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Log.w(TAG, ">>> TELECOM: SDK version < Q, role not available")
                return false
            }
            return try {
                val roleManager = context.getSystemService(RoleManager::class.java)
                val isHeld = roleManager.isRoleHeld("android.app.role.SELF_MANAGED_CALLS")
                Log.i(TAG, ">>> TELECOM: isSelfManagedRoleGranted result=$isHeld")
                isHeld
            } catch (e: Exception) {
                Log.w(TAG, ">>> TELECOM: RoleManager check failed", e)
                false
            }
        }

        fun markCallActive(callId: String) {
            connections[callId]?.apply {
                onCallConnected()
                setActive()
            }
            PjsipEngine.instance.refreshAudio()
        }

        fun markCallEnded(callId: String) {
            connections[callId]?.apply {
                stopRingingNow()
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
