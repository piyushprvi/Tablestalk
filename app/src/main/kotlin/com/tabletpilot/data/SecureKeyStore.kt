package com.tabletpilot.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecureKeyStore {

    private const val PREFS_FILE = "tabletpilot_secure_prefs"
    private const val KEY_API_KEY = "api_key"

    @Volatile
    private var encryptedPrefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return encryptedPrefs ?: synchronized(this) {
            encryptedPrefs ?: createEncryptedPrefs(context.applicationContext).also {
                encryptedPrefs = it
            }
        }
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_API_KEY, key).apply()
    }

    fun getApiKey(context: Context): String? {
        return try {
            val value = getPrefs(context).getString(KEY_API_KEY, null)
            if (value.isNullOrBlank()) null else value
        } catch (e: Exception) {
            null
        }
    }

    fun clearApiKey(context: Context) {
        getPrefs(context).edit().remove(KEY_API_KEY).apply()
    }

    fun hasApiKey(context: Context): Boolean = getApiKey(context) != null
}
