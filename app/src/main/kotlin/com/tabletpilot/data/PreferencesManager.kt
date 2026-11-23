package com.tabletpilot.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tabletpilot_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val KEY_GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val KEY_SELECTED_MODEL = stringPreferencesKey("selected_model")
        val KEY_LOCAL_FAST_PATH_ENABLED = booleanPreferencesKey("local_fast_path_enabled")
        val KEY_SERVICE_RUNNING = booleanPreferencesKey("service_running")

        const val DEFAULT_MODEL = "gemini-2.0-flash-lite"

        @Volatile
        private var INSTANCE: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PreferencesManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    val geminiApiKey: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_GEMINI_API_KEY] ?: ""
    }

    val selectedModel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_SELECTED_MODEL] ?: DEFAULT_MODEL
    }

    val localFastPathEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_LOCAL_FAST_PATH_ENABLED] ?: true
    }

    val serviceRunning: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_SERVICE_RUNNING] ?: false
    }

    suspend fun saveApiKey(key: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GEMINI_API_KEY] = key
        }
    }

    suspend fun saveSelectedModel(model: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SELECTED_MODEL] = model
        }
    }

    suspend fun saveLocalFastPathEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LOCAL_FAST_PATH_ENABLED] = enabled
        }
    }

    suspend fun saveServiceRunning(running: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SERVICE_RUNNING] = running
        }
    }
}
