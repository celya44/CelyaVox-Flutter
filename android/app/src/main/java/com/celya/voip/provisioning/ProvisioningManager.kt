package com.celya.voip.provisioning

import android.content.Context
import android.content.SharedPreferences
import com.celya.voip.security.SecureStorage
import com.celya.voip.sip.SipAccountManager
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL

class ProvisioningManager(
    private val context: Context,
    private val sipAccountManager: SipAccountManager = SipAccountManager(context),
    private val secureStorage: SecureStorage = SecureStorage(context)
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("provisioning_state", Context.MODE_PRIVATE)

    fun start(url: String) {
        val xmlBytes = download(url)
        val config = parse(xmlBytes)
        store(config)
        configureSip(config)
        prefs.edit().putBoolean(KEY_PROVISIONED, true).apply()
    }

    private fun download(url: String): ByteArray {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.requestMethod = "GET"
        connection.instanceFollowRedirects = true
        connection.doInput = true
        connection.connect()
        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            throw IllegalStateException("Provisioning download failed with ${connection.responseCode}")
        }
        return connection.inputStream.use { it.readBytes() }
    }

    private fun parse(bytes: ByteArray): ProvisioningConfig {
        ByteArrayInputStream(bytes).use { stream ->
            return ProvisioningXmlParser().parse(BufferedInputStream(stream))
        }
    }

    private fun store(config: ProvisioningConfig) {
        config.get("sip_password")?.let { secureStorage.saveSipPassword(it) }
        config.get("api_key")?.let { secureStorage.saveApiKey(it) }
        config.get("ldap_password")?.let { secureStorage.saveLdapPassword(it) }
    }

    private fun configureSip(config: ProvisioningConfig) {
        val username = config.get("sip_username") ?: return
        val password = config.get("sip_password") ?: return
        val domain = config.get("sip_domain") ?: return
        val port = config.get("sip_wss_port")?.toIntOrNull() ?: 443
        sipAccountManager.configureAccount(username, password, domain, port)
    }

    fun isProvisioned(): Boolean = prefs.getBoolean(KEY_PROVISIONED, false)

    companion object {
        private const val KEY_PROVISIONED = "is_provisioned"
    }
}
