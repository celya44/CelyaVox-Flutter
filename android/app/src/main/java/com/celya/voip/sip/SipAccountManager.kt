package com.celya.voip.sip

import android.content.Context

class SipAccountManager(private val context: Context) {
    private var initialized = false
    private val adapter: SipStackAdapter = PjsipStackAdapter()

    fun initializePjsip() {
        if (initialized) return
        adapter.initialize(context)
        initialized = true
    }

    fun configureAccount(
        username: String,
        password: String,
        domain: String,
        wssPort: Int
    ) {
        initializePjsip()
        val config = SipAccountConfig(
            username = username,
            password = password,
            domain = domain,
            wssPort = wssPort
        )
        adapter.createAccount(config)
    }
}

data class SipAccountConfig(
    val username: String,
    val password: String,
    val domain: String,
    val wssPort: Int
)

private interface SipStackAdapter {
    fun initialize(context: Context)
    fun createAccount(config: SipAccountConfig)
}

private class PjsipStackAdapter : SipStackAdapter {
    override fun initialize(context: Context) {
        if (!isPjsipAvailable()) {
            throw IllegalStateException("PJSIP is not available on the classpath.")
        }
        // Initialize PJSIP stack here when library is linked.
    }

    override fun createAccount(config: SipAccountConfig) {
        if (!isPjsipAvailable()) {
            throw IllegalStateException("PJSIP is not available on the classpath.")
        }
        // Configure WSS transport and create the SIP account with the provided credentials.
    }

    private fun isPjsipAvailable(): Boolean {
        return try {
            Class.forName("org.pjsip.pjsua2.Account")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }
}
