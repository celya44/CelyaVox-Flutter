package com.celya.voip.security

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {
    private val prefsName = "secure_prefs"
    private val appContext = context.applicationContext
    
    private var _prefs: EncryptedSharedPreferences? = null
    private var _initFailed = false
    
    private fun getPrefs(): EncryptedSharedPreferences? {
        if (_initFailed) return null
        if (_prefs != null) return _prefs
        
        return try {
            val keyAlias = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                appContext,
                prefsName,
                keyAlias,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            _prefs = prefs
            prefs
        } catch (e: Exception) {
            Log.e("SecureStorage", "Failed to initialize EncryptedSharedPreferences", e)
            _initFailed = true
            null
        }
    }

    fun saveSipPassword(value: String) {
        getPrefs()?.edit()?.putString(KEY_SIP_PASSWORD, value)?.apply()
    }

    fun getSipPassword(): String? = safeGetString(KEY_SIP_PASSWORD)

    fun saveApiKey(value: String) {
        getPrefs()?.edit()?.putString(KEY_API_KEY, value)?.apply()
    }

    fun getApiKey(): String? = safeGetString(KEY_API_KEY)

    fun saveLdapPassword(value: String) {
        getPrefs()?.edit()?.putString(KEY_LDAP_PASSWORD, value)?.apply()
    }

    fun getLdapPassword(): String? = safeGetString(KEY_LDAP_PASSWORD)

    private fun safeGetString(key: String): String? = try {
        getPrefs()?.getString(key, null)
    } catch (e: Exception) {
        Log.e("SecureStorage", "Failed to read $key from secure storage", e)
        clearAll()
        null
    }

    fun clearAll() {
        try {
            getPrefs()?.edit()
                ?.remove(KEY_SIP_PASSWORD)
                ?.remove(KEY_API_KEY)
                ?.remove(KEY_LDAP_PASSWORD)
                ?.apply()
        } catch (e: Exception) {
            Log.e("SecureStorage", "Failed to clear secure storage", e)
        }
    }

    companion object {
        private const val KEY_SIP_PASSWORD = "sip_password"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_LDAP_PASSWORD = "ldap_password"
    }
}
