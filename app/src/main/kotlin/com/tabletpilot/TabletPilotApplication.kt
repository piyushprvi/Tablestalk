package com.tabletpilot

import android.app.Application
import android.util.Log
import com.tabletpilot.BuildConfig
import com.tabletpilot.data.AppContainer
import com.tabletpilot.data.SecureKeyStore

private const val TAG = "TabletPilotApp"

class TabletPilotApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "TabletPilotApplication starting")

        // Initialize DI container
        AppContainer.initialize(this)

        // Seed the API key from BuildConfig if none is saved yet
        seedApiKeyFromBuildConfig()
    }

    private fun seedApiKeyFromBuildConfig() {
        val buildConfigKey = BuildConfig.GEMINI_API_KEY
        if (buildConfigKey.isNotBlank() && !SecureKeyStore.hasApiKey(this)) {
            SecureKeyStore.saveApiKey(this, buildConfigKey)
            Log.i(TAG, "Seeded Gemini API key from BuildConfig")
        }
    }
}
