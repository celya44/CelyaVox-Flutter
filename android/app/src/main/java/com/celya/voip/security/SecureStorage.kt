package com.celya.voip.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.security.KeyStore

class SecureStorage(context: Context) {
    private val prefsName = "secure_prefs"
    private val appContext = context.applicationContext
    
    private var _prefs: SharedPreferences? = null
    private var _isEncrypted = true
    private var _initFailed = false
    
    private fun getPrefs(): SharedPreferences? {
        if (_initFailed) return null
        if (_prefs != null) return _prefs
        
        return try {
            Log.i("SecureStorage", "Starting encrypted storage initialization...")
            
            Log.i("SecureStorage", "Building MasterKey...")
            val keyAlias = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            Log.i("SecureStorage", "MasterKey built successfully")
            
            Log.i("SecureStorage", "Creating EncryptedSharedPreferences...")
            val prefs = EncryptedSharedPreferences.create(
                appContext,
                prefsName,
                keyAlias,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            Log.i("SecureStorage", "EncryptedSharedPreferences created successfully")
            
            _isEncrypted = true
            Log.i("SecureStorage", "Using encrypted SharedPreferences")
            _prefs = prefs
            prefs
        } catch (e: Exception) {
            // Vérifier le type d'exception EN PREMIER
            val isAEADBadTag = e is javax.crypto.AEADBadTagException || 
                               e.cause is javax.crypto.AEADBadTagException ||
                               e.message?.contains("AEAD") == true ||
                               e.message?.contains("Signature/MAC verification failed") == true
            
            Log.e("SecureStorage", "Exception type: ${e::class.simpleName}, isAEADBadTag=$isAEADBadTag")
            Log.e("SecureStorage", "Exception message: ${e.message}")
            
            // Problème spécifique S22 : le fichier de prefs existe mais ne peut pas être déchiffré
            // Solution : supprimer le fichier corrompu ET nettoyer le KeyStore, puis réessayer
            if (isAEADBadTag) {
                Log.w("SecureStorage", "AEADBadTagException detected - corrupted prefs file and/or KeyStore. Attempting cleanup...")
                try {
                    // Nettoyer le fichier
                    val prefsFile = File(appContext.filesDir.parent, "shared_prefs/$prefsName.xml")
                    Log.i("SecureStorage", "Looking for corrupted file at: ${prefsFile.absolutePath}")
                    
                    if (prefsFile.exists()) {
                        val deleted = prefsFile.delete()
                        Log.i("SecureStorage", "Corrupted prefs file deleted: $deleted at ${prefsFile.absolutePath}")
                    } else {
                        Log.i("SecureStorage", "Corrupted prefs file not found at: ${prefsFile.absolutePath}")
                    }
                    
                    // Nettoyer le KeyStore - bug S22 spécifique
                    Log.i("SecureStorage", "Attempting to clear KeyStore entries...")
                    try {
                        val keyStore = KeyStore.getInstance("AndroidKeyStore")
                        keyStore.load(null)
                        val aliases = keyStore.aliases()
                        var deletedCount = 0
                        while (aliases.hasMoreElements()) {
                            val alias = aliases.nextElement()
                            try {
                                keyStore.deleteEntry(alias)
                                deletedCount++
                                Log.i("SecureStorage", "Deleted KeyStore entry: $alias")
                            } catch (e: Exception) {
                                Log.w("SecureStorage", "Failed to delete KeyStore entry: $alias", e)
                            }
                        }
                        Log.i("SecureStorage", "KeyStore cleanup complete - deleted $deletedCount entries")
                    } catch (e: Exception) {
                        Log.w("SecureStorage", "Failed to clear KeyStore", e)
                    }
                    
                    // Réessayer après nettoyage
                    Log.i("SecureStorage", "Retrying EncryptedSharedPreferences creation after cleanup...")
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
                    Log.i("SecureStorage", "EncryptedSharedPreferences created successfully after cleanup")
                    _isEncrypted = true
                    _prefs = prefs
                    return prefs
                } catch (retryE: Exception) {
                    Log.e("SecureStorage", "Retry after cleanup failed: ${retryE::class.simpleName} - ${retryE.message}")
                }
            }
            
            Log.w("SecureStorage", "EncryptedSharedPreferences failed, using fallback unencrypted storage")
            // Fallback à SharedPreferences normal pour éviter la perte de données
            try {
                Log.i("SecureStorage", "Creating fallback unencrypted SharedPreferences...")
                val prefs = appContext.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
                _isEncrypted = false
                _prefs = prefs
                Log.i("SecureStorage", "Fallback unencrypted SharedPreferences created successfully")
                prefs
            } catch (fallbackE: Exception) {
                Log.e("SecureStorage", "Both encrypted and unencrypted storage failed", fallbackE)
                _initFailed = true
                null
            }
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
        Log.e("SecureStorage", "Failed to read $key from storage", e)
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
            Log.e("SecureStorage", "Failed to clear storage", e)
        }
    }

    fun isEncrypted(): Boolean = _isEncrypted

    companion object {
        private const val KEY_SIP_PASSWORD = "sip_password"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_LDAP_PASSWORD = "ldap_password"
    }
}
