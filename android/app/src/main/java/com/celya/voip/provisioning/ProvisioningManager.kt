package com.celya.voip.provisioning

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
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
            val errorBody = try {
                connection.errorStream?.use { stream ->
                    val raw = stream.readBytes().toString(Charsets.UTF_8)
                    raw.take(4000)
                }
            } catch (_: Exception) {
                null
            }
            connection.disconnect()
            val details = if (!errorBody.isNullOrBlank()) {
                " (body: $errorBody)"
            } else {
                ""
            }
            throw IllegalStateException(
                "Provisioning download failed with ${connection.responseCode} for $url$details"
            )
        }
        return connection.inputStream.use { it.readBytes() }
    }

    private fun parse(bytes: ByteArray): ProvisioningConfig {
        ByteArrayInputStream(bytes).use { stream ->
            return ProvisioningXmlParser().parse(BufferedInputStream(stream))
        }
    }

    private fun store(config: ProvisioningConfig) {
        getAny(config, "sip_password", "SipPassword")?.let { secureStorage.saveSipPassword(it) }
        config.get("api_key")?.let { secureStorage.saveApiKey(it) }
        config.get("ldap_password")?.let { secureStorage.saveLdapPassword(it) }
        getAny(config, "sip_username", "SipUsername")?.let { prefs.edit().putString(KEY_SIP_USERNAME, it).apply() }
        getAny(config, "sip_domain", "SipDomaine", "SipDomain")?.let { prefs.edit().putString(KEY_SIP_DOMAIN, it).apply() }
        getAny(config, "sip_proxy", "SipProxy")?.let { prefs.edit().putString(KEY_SIP_PROXY, it).apply() }
        val nonSensitive = config.entries.filterKeys { it !in SENSITIVE_KEYS }
        prefs.edit().putString(KEY_PROVISIONING_DUMP, toJson(nonSensitive)).apply()
    }

    private fun configureSip(config: ProvisioningConfig) {
        val username = getAny(config, "sip_username", "SipUsername") ?: return
        val password = getAny(config, "sip_password", "SipPassword") ?: return
        val domain = getAny(config, "sip_domain", "SipDomaine", "SipDomain") ?: return
        val port = config.get("sip_wss_port")?.toIntOrNull() ?: 443
        try {
            sipAccountManager.configureAccount(username, password, domain, port)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "PJSIP unavailable; skipping native provisioning register", e)
        }
    }

    fun isProvisioned(): Boolean = prefs.getBoolean(KEY_PROVISIONED, false)

    fun getSipUsername(): String? = prefs.getString(KEY_SIP_USERNAME, null)

    fun getSipDomain(): String? = prefs.getString(KEY_SIP_DOMAIN, null)

    fun getSipProxy(): String? = prefs.getString(KEY_SIP_PROXY, null)

    fun getSipPassword(): String? = secureStorage.getSipPassword()

    fun getApiKey(): String? = secureStorage.getApiKey()

    fun getProvisioningDump(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val raw = prefs.getString(KEY_PROVISIONING_DUMP, null)
        if (!raw.isNullOrBlank()) {
            result.putAll(fromJson(raw))
        }
        secureStorage.getSipPassword()?.let { result["sip_password"] = it }
        secureStorage.getApiKey()?.let { result["api_key"] = it }
        secureStorage.getLdapPassword()?.let { result["ldap_password"] = it }
        return result
    }

    fun resetProvisioning() {
        prefs.edit()
            .remove(KEY_PROVISIONED)
            .remove(KEY_SIP_USERNAME)
            .remove(KEY_SIP_DOMAIN)
            .remove(KEY_SIP_PROXY)
            .remove(KEY_PROVISIONING_DUMP)
            .apply()
        secureStorage.clearAll()
    }

    private fun toJson(map: Map<String, String>): String {
        val json = org.json.JSONObject()
        map.forEach { (key, value) -> json.put(key, value) }
        return json.toString()
    }

    private fun getAny(config: ProvisioningConfig, vararg keys: String): String? {
        for (key in keys) {
            val value = config.get(key)
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun fromJson(raw: String): Map<String, String> {
        val json = org.json.JSONObject(raw)
        val keys = json.keys()
        val result = mutableMapOf<String, String>()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = json.optString(key, "")
        }
        return result
    }

    companion object {
        private const val TAG = "ProvisioningManager"
        private const val KEY_PROVISIONED = "is_provisioned"
        private const val KEY_SIP_USERNAME = "sip_username"
        private const val KEY_SIP_DOMAIN = "sip_domain"
        private const val KEY_SIP_PROXY = "sip_proxy"
        private const val KEY_PROVISIONING_DUMP = "provisioning_dump"
        private val SENSITIVE_KEYS = setOf(
            "sip_password",
            "SipPassword",
            "api_key",
            "ldap_password"
        )
    }
}
