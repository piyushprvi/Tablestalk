package com.tabletpilot.ui

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tabletpilot.BuildConfig
import com.tabletpilot.data.ActionLogEntry
import com.tabletpilot.data.ActionLogRepository
import com.tabletpilot.data.AppContainer
import com.tabletpilot.data.SecureKeyStore
import com.tabletpilot.service.AssistantForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PermissionsState(
    val accessibilityGranted: Boolean = false,
    val overlayGranted: Boolean = false,
    val batteryOptIgnored: Boolean = false,
    val notificationsGranted: Boolean = false,
    val microphoneGranted: Boolean = false
) {
    val allGranted: Boolean get() =
        accessibilityGranted && overlayGranted && batteryOptIgnored &&
                notificationsGranted && microphoneGranted
}

class MainViewModel : ViewModel() {

    private val _permissionsState = MutableStateFlow(PermissionsState())
    val permissionsState: StateFlow<PermissionsState> = _permissionsState.asStateFlow()

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _selectedModel = MutableStateFlow("gemini-2.0-flash-lite")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _fastPathEnabled = MutableStateFlow(true)
    val fastPathEnabled: StateFlow<Boolean> = _fastPathEnabled.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    val actionLog: StateFlow<List<ActionLogEntry>> = ActionLogRepository.getLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Stored context for async ops (application context only — no leak)
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = AppContainer.getPreferencesManager(context)

        viewModelScope.launch {
            prefs.selectedModel.collect { _selectedModel.value = it }
        }
        viewModelScope.launch {
            prefs.localFastPathEnabled.collect { _fastPathEnabled.value = it }
        }
        viewModelScope.launch {
            prefs.serviceRunning.collect { _serviceRunning.value = it }
        }

        // Load API key from secure store
        val saved = SecureKeyStore.getApiKey(context)
            ?: BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }
            ?: ""
        _apiKey.value = saved
    }

    fun checkPermissions(context: Context) {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )
        val accessibilityOn = enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }

        val overlayOn = Settings.canDrawOverlays(context)

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val batteryIgnored = pm.isIgnoringBatteryOptimizations(context.packageName)

        val notificationsOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true

        val micOn = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        _permissionsState.value = PermissionsState(
            accessibilityGranted = accessibilityOn,
            overlayGranted = overlayOn,
            batteryOptIgnored = batteryIgnored,
            notificationsGranted = notificationsOn,
            microphoneGranted = micOn
        )
    }

    fun updateApiKey(key: String) {
        _apiKey.value = key
        viewModelScope.launch {
            val ctx = appContext ?: return@launch
            SecureKeyStore.saveApiKey(ctx, key)
            AppContainer.getPreferencesManager(ctx).saveApiKey(key)
        }
    }

    fun updateModel(model: String) {
        _selectedModel.value = model
        viewModelScope.launch {
            val ctx = appContext ?: return@launch
            AppContainer.getPreferencesManager(ctx).saveSelectedModel(model)
        }
    }

    fun toggleFastPath(enabled: Boolean) {
        _fastPathEnabled.value = enabled
        viewModelScope.launch {
            val ctx = appContext ?: return@launch
            AppContainer.getPreferencesManager(ctx).saveLocalFastPathEnabled(enabled)
        }
    }

    fun startService(context: Context) {
        AssistantForegroundService.startService(context)
        _serviceRunning.value = true
        viewModelScope.launch {
            AppContainer.getPreferencesManager(context).saveServiceRunning(true)
        }
    }

    fun stopService(context: Context) {
        AssistantForegroundService.stopService(context)
        _serviceRunning.value = false
        viewModelScope.launch {
            AppContainer.getPreferencesManager(context).saveServiceRunning(false)
        }
    }

    fun clearLog() {
        ActionLogRepository.clearLog()
    }
}
