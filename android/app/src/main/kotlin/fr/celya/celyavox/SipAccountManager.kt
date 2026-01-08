package fr.celya.celyavox

import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages SIP account lifecycle with PJSIP native engine.
 * All sensitive values (password) are provided at runtime by Flutter.
 */
class SipAccountManager(private val engine: PjsipEngine = PjsipEngine.instance) {

    interface Listener {
        fun onRegistered()
        fun onRegistrationFailed(reason: String)
        fun onUnregistered()
    }

    private val currentAccount = AtomicReference<SipAccount?>()
    @Volatile
    var listener: Listener? = null

    fun register(account: SipAccount): Boolean {
        val safeUser = account.username.ifBlank { "(empty)" }
        val safeDomain = account.domain.ifBlank { "(empty)" }
        Log.i(TAG, "Registering SIP account user=$safeUser domain=$safeDomain")

        // Password must not be logged.
        val ok = engine.register(account.username, account.password, account.domain, account.proxy)
        if (ok) {
            currentAccount.set(account)
            listener?.onRegistered()
        } else {
            listener?.onRegistrationFailed("native register failed")
        }
        return ok
    }

    fun unregister() {
        engine.unregister()
        currentAccount.set(null)
        listener?.onUnregistered()
    }

    /**
     * Attempt to re-register using last known credentials.
     */
    fun reRegister(): Boolean {
        val account = currentAccount.get() ?: return false
        return register(account)
    }

    /**
     * Hook for native events to bubble registration results. Should be wired from PjsipEngine callback layer.
     */
    fun handleNativeEvent(type: String, message: String) {
        when (type) {
            "registration" -> {
                if (message.contains("200")) {
                    listener?.onRegistered()
                } else {
                    listener?.onRegistrationFailed(message)
                }
            }
        }
    }

    companion object {
        private const val TAG = "SipAccountManager"
    }
}
