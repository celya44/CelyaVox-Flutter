package fr.celya.celyavox

import android.content.Context

object FcmTokenStore {
    private const val PREFS = "fcm_token_store"
    private const val KEY_TOKEN = "fcm_token"
    private const val KEY_UPDATED_AT = "fcm_token_updated_at"

    fun saveToken(context: Context, token: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TOKEN, token)
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun getToken(context: Context): String? {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)
    }

    fun getUpdatedAt(context: Context): Long {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_UPDATED_AT, 0L)
    }
}