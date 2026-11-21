package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.LauncherPreferencesRepository
import com.example.model.AppEntry
import com.example.model.EmuAnimationStyle
import com.example.model.LauncherAlignment
import com.example.model.LauncherConfig
import com.example.model.LauncherConfigManager
import com.example.model.LauncherPreferences
import com.example.model.LauncherUiState
import com.example.model.SurfaceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val appRepository = AppRepository(application)
    private val preferencesRepository = LauncherPreferencesRepository(application)
    private val configManager = LauncherConfigManager(application)

    private val _allApps = MutableStateFlow<List<AppEntry>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _isDrawerOpen = MutableStateFlow(false)
    private val _isSettingsOpen = MutableStateFlow(false)
    private val _isAnimationsOpen = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _pendingMindfulApp = MutableStateFlow<AppEntry?>(null)
    private val _isDefaultLauncher = MutableStateFlow(false)

    val uiState: StateFlow<LauncherUiState> = combine(
        _isLoading,
        _allApps,
        preferencesRepository.preferencesFlow,
        configManager.configFlow,
        _isDrawerOpen,
        _isSettingsOpen,
        _isAnimationsOpen,
        _searchQuery,
        _pendingMindfulApp,
        _isDefaultLauncher
    ) { values ->
        val isLoading = values[0] as Boolean
        @Suppress("UNCHECKED_CAST")
        val allApps = values[1] as List<AppEntry>
        val prefs = values[2] as LauncherPreferences
        val config = values[3] as LauncherConfig
        val isDrawerOpen = values[4] as Boolean
        val isSettingsOpen = values[5] as Boolean
        val isAnimationsOpen = values[6] as Boolean
        val searchQuery = values[7] as String
        val pendingMindfulApp = values[8] as AppEntry?
        val isDefaultLauncher = values[9] as Boolean

        val appsByPackage = allApps.associateBy { it.packageName }
        val pinnedApps = prefs.pinnedPackageNames.mapNotNull { pkg -> appsByPackage[pkg] }

        LauncherUiState(
            isLoading = isLoading,
            allApps = allApps,
            pinnedApps = pinnedApps,
            preferences = prefs,
            config = config,
            isDrawerOpen = isDrawerOpen,
            isSettingsOpen = isSettingsOpen,
            isAnimationsOpen = isAnimationsOpen,
            searchQuery = searchQuery,
            pendingMindfulApp = pendingMindfulApp,
            isDefaultLauncher = isDefaultLauncher
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LauncherUiState(config = configManager.currentConfig)
    )

    init {
        refreshInstalledApps()
    }

    fun refreshInstalledApps() {
        viewModelScope.launch {
            val apps = appRepository.loadLaunchableApps()
            _allApps.value = apps
            _isDefaultLauncher.value = appRepository.isDefaultHomeLauncher()

            val defaultPins = appRepository.selectDefaultPinnedPackages(apps)
            preferencesRepository.initializeDefaultPinnedAppsIfNeeded(defaultPins)
            _isLoading.value = false
        }
    }

    fun openDrawer() {
        _searchQuery.value = ""
        _isDrawerOpen.value = true
    }

    fun closeDrawer() {
        _isDrawerOpen.value = false
        _searchQuery.value = ""
    }

    fun openSettings() {
        _isDefaultLauncher.value = appRepository.isDefaultHomeLauncher()
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun openAnimations() {
        _isSettingsOpen.value = false
        _isAnimationsOpen.value = true
    }

    fun closeAnimations() {
        _isAnimationsOpen.value = false
    }

    fun collapseAllOverlays() {
        _pendingMindfulApp.value = null
        _isAnimationsOpen.value = false
        _isSettingsOpen.value = false
        _isDrawerOpen.value = false
        _searchQuery.value = ""
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onAppClicked(app: AppEntry) {
        val prefs = uiState.value.preferences
        val shouldPause = prefs.mindfulPauseEnabled &&
            (app.isKnownDistracting || app.packageName in prefs.customPausedPackages)

        if (shouldPause) {
            _pendingMindfulApp.value = app
        } else {
            launchAndReset(app)
        }
    }

    fun confirmMindfulLaunch() {
        val app = _pendingMindfulApp.value ?: return
        _pendingMindfulApp.value = null
        launchAndReset(app)
    }

    fun cancelMindfulLaunch() {
        _pendingMindfulApp.value = null
    }

    private fun launchAndReset(app: AppEntry) {
        appRepository.launchApp(app)
        _isDrawerOpen.value = false
        _searchQuery.value = ""
    }

    fun openAppInfo(app: AppEntry) {
        appRepository.openAppInfo(app.packageName)
    }

    fun openDefaultHomeSettings() {
        appRepository.openDefaultHomeSettings()
    }

    fun togglePinnedApp(packageName: String) {
        viewModelScope.launch {
            preferencesRepository.togglePinnedPackage(packageName)
        }
    }

    fun setSurfaceMode(mode: SurfaceMode) {
        viewModelScope.launch {
            preferencesRepository.setSurfaceMode(mode)
        }
    }

    fun setClockSensitivity(value: Float) {
        viewModelScope.launch {
            configManager.setClockSensitivity(value)
        }
    }

    fun setMenuSensitivity(value: Float) {
        viewModelScope.launch {
            configManager.setMenuSensitivity(value)
        }
    }

    fun setClockAlignment(alignment: LauncherAlignment) {
        viewModelScope.launch {
            configManager.setClockAlignment(alignment)
        }
    }

    fun setMenuAlignment(alignment: LauncherAlignment) {
        viewModelScope.launch {
            configManager.setMenuAlignment(alignment)
        }
    }

    fun setEmuAnimationStyle(style: EmuAnimationStyle) {
        viewModelScope.launch {
            configManager.setEmuAnimationStyle(style)
        }
    }

    fun setEyeColorHex(hex: String) {
        viewModelScope.launch {
            configManager.setEyeColorHex(hex)
        }
    }

    fun setLiveLockEnabled(enabled: Boolean) {
        viewModelScope.launch {
            configManager.setLiveLockEnabled(enabled)
        }
    }

    fun setClockShadowEnabled(enabled: Boolean) {
        viewModelScope.launch {
            configManager.setClockShadowEnabled(enabled)
        }
    }

    fun setSelectedEmotionName(name: String) {
        viewModelScope.launch {
            configManager.setSelectedEmotionName(name)
        }
    }

    fun setShakeReactionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            configManager.setShakeReactionEnabled(enabled)
        }
    }

    fun setShakeRecoveryDurationSec(seconds: Float) {
        viewModelScope.launch {
            configManager.setShakeRecoveryDurationSec(seconds)
        }
    }

    fun setHomeSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            configManager.setHomeSoundEnabled(enabled)
        }
    }

    fun setLockScreenSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            configManager.setLockScreenSoundEnabled(enabled)
        }
    }

    fun setEyeFollowSoundEnabled(enabled: Boolean) {
        viewModelScope.launch {
            configManager.setEyeFollowSoundEnabled(enabled)
        }
    }

    fun resetConfigToDefaults() {
        viewModelScope.launch {
            configManager.resetToDefaults()
        }
    }

    fun setShowTiltDebugOverlay(show: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setShowTiltDebugOverlay(show)
        }
    }

    fun setMindfulPauseEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setMindfulPauseEnabled(enabled)
        }
    }

    fun toggleCustomPausedPackage(packageName: String) {
        viewModelScope.launch {
            preferencesRepository.toggleCustomPausedPackage(packageName)
        }
    }
}
