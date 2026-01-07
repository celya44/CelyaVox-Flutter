package com.celya.voip.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {
    private val prefsName = "secure_prefs"
    private val keyAlias = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val prefs = EncryptedSharedPreferences.create(
        context,
        prefsName,
        keyAlias,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSipPassword(value: String) {
        prefs.edit().putString(KEY_SIP_PASSWORD, value).apply()
    }

    fun getSipPassword(): String? = prefs.getString(KEY_SIP_PASSWORD, null)

    fun saveApiKey(value: String) {
        prefs.edit().putString(KEY_API_KEY, value).apply()
    }

    fun getApiKey(): String? = prefs.getString(KEY_API_KEY, null)

    fun saveLdapPassword(value: String) {
        prefs.edit().putString(KEY_LDAP_PASSWORD, value).apply()
    }

    fun getLdapPassword(): String? = prefs.getString(KEY_LDAP_PASSWORD, null)

    companion object {
        private const val KEY_SIP_PASSWORD = "sip_password"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_LDAP_PASSWORD = "ldap_password"
    }
}
