package com.example.model

import androidx.compose.runtime.Immutable

@Immutable
data class AppEntry(
    val label: String,
    val packageName: String,
    val activityName: String,
    val isKnownDistracting: Boolean = false
) {
    val id: String get() = "$packageName/$activityName"
}

enum class SurfaceMode {
    MATTE_CHARCOAL,
    AMOLED_BLACK
}

enum class GyroSensitivity(val multiplier: Float) {
    OFF(0f),
    SUBTLE(0.65f),
    DYNAMIC(1.35f)
}

@Immutable
data class LauncherPreferences(
    val pinnedPackageNames: List<String> = emptyList(),
    val hasInitializedDefaults: Boolean = false,
    val surfaceMode: SurfaceMode = SurfaceMode.MATTE_CHARCOAL,
    val gyroSensitivity: GyroSensitivity = GyroSensitivity.SUBTLE,
    val tiltIntensity: Float = 1.2f,
    val showTiltDebugOverlay: Boolean = false,
    val mindfulPauseEnabled: Boolean = true,
    val customPausedPackages: Set<String> = emptySet()
)

@Immutable
data class LauncherUiState(
    val isLoading: Boolean = true,
    val allApps: List<AppEntry> = emptyList(),
    val pinnedApps: List<AppEntry> = emptyList(),
    val preferences: LauncherPreferences = LauncherPreferences(),
    val config: LauncherConfig = LauncherConfig(),
    val isDrawerOpen: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val isAnimationsOpen: Boolean = false,
    val searchQuery: String = "",
    val pendingMindfulApp: AppEntry? = null,
    val isDefaultLauncher: Boolean = false
)
