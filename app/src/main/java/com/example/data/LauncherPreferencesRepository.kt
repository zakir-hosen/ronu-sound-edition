package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.GyroSensitivity
import com.example.model.LauncherPreferences
import com.example.model.SurfaceMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "still_launcher_prefs"
)

class LauncherPreferencesRepository(private val context: Context) {

    val preferencesFlow: Flow<LauncherPreferences> = context.launcherDataStore.data.map { prefs ->
        val rawPinned = prefs[KEY_PINNED_PACKAGES].orEmpty()
        val pinnedList = if (rawPinned.isBlank()) {
            emptyList()
        } else {
            rawPinned.split(PIN_DELIMITER).filter { it.isNotBlank() }
        }

        val surfaceMode = prefs[KEY_SURFACE_MODE]?.let { name ->
            runCatching { SurfaceMode.valueOf(name) }.getOrNull()
        } ?: SurfaceMode.MATTE_CHARCOAL

        val gyroSensitivity = prefs[KEY_GYRO_SENSITIVITY]?.let { name ->
            runCatching { GyroSensitivity.valueOf(name) }.getOrNull()
        } ?: GyroSensitivity.SUBTLE

        LauncherPreferences(
            pinnedPackageNames = pinnedList,
            hasInitializedDefaults = prefs[KEY_DEFAULTS_INITIALIZED] ?: false,
            surfaceMode = surfaceMode,
            gyroSensitivity = gyroSensitivity,
            tiltIntensity = (prefs[KEY_TILT_INTENSITY] ?: 1.2f).coerceIn(1.0f, 2.5f),
            showTiltDebugOverlay = prefs[KEY_SHOW_TILT_DEBUG] ?: false,
            mindfulPauseEnabled = prefs[KEY_MINDFUL_PAUSE_ENABLED] ?: true,
            customPausedPackages = prefs[KEY_CUSTOM_PAUSED_PACKAGES] ?: emptySet()
        )
    }

    suspend fun initializeDefaultPinnedAppsIfNeeded(defaultPackages: List<String>) {
        context.launcherDataStore.edit { prefs ->
            val alreadyInitialized = prefs[KEY_DEFAULTS_INITIALIZED] ?: false
            if (!alreadyInitialized) {
                prefs[KEY_PINNED_PACKAGES] = defaultPackages
                    .distinct()
                    .take(MAX_PINNED_APPS)
                    .joinToString(PIN_DELIMITER)
                prefs[KEY_DEFAULTS_INITIALIZED] = true
            }
        }
    }

    suspend fun setPinnedPackages(packages: List<String>) {
        context.launcherDataStore.edit { prefs ->
            prefs[KEY_PINNED_PACKAGES] = packages
                .distinct()
                .take(MAX_PINNED_APPS)
                .joinToString(PIN_DELIMITER)
            prefs[KEY_DEFAULTS_INITIALIZED] = true
        }
    }

    suspend fun togglePinnedPackage(packageName: String) {
        context.launcherDataStore.edit { prefs ->
            val current = prefs[KEY_PINNED_PACKAGES]
                .orEmpty()
                .split(PIN_DELIMITER)
                .filter { it.isNotBlank() }
                .toMutableList()

            if (packageName in current) {
                current.remove(packageName)
            } else if (current.size < MAX_PINNED_APPS) {
                current.add(packageName)
            }

            prefs[KEY_PINNED_PACKAGES] = current.joinToString(PIN_DELIMITER)
            prefs[KEY_DEFAULTS_INITIALIZED] = true
        }
    }

    suspend fun setSurfaceMode(mode: SurfaceMode) {
        context.launcherDataStore.edit { prefs ->
            prefs[KEY_SURFACE_MODE] = mode.name
        }
    }

    suspend fun setGyroSensitivity(sensitivity: GyroSensitivity) {
        context.launcherDataStore.edit { prefs ->
            prefs[KEY_GYRO_SENSITIVITY] = sensitivity.name
        }
    }

    suspend fun setTiltIntensity(intensity: Float) {
        context.launcherDataStore.edit { prefs ->
            prefs[KEY_TILT_INTENSITY] = intensity.coerceIn(1.0f, 2.5f)
        }
    }

    suspend fun setShowTiltDebugOverlay(show: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[KEY_SHOW_TILT_DEBUG] = show
        }
    }

    suspend fun setMindfulPauseEnabled(enabled: Boolean) {
        context.launcherDataStore.edit { prefs ->
            prefs[KEY_MINDFUL_PAUSE_ENABLED] = enabled
        }
    }

    suspend fun toggleCustomPausedPackage(packageName: String) {
        context.launcherDataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_PAUSED_PACKAGES]?.toMutableSet() ?: mutableSetOf()
            if (packageName in current) {
                current.remove(packageName)
            } else {
                current.add(packageName)
            }
            prefs[KEY_CUSTOM_PAUSED_PACKAGES] = current
        }
    }

    companion object {
        const val MIN_RECOMMENDED_PINNED = 5
        const val MAX_PINNED_APPS = 7

        private const val PIN_DELIMITER = "|"
        private val KEY_PINNED_PACKAGES = stringPreferencesKey("pinned_packages")
        private val KEY_DEFAULTS_INITIALIZED = booleanPreferencesKey("defaults_initialized")
        private val KEY_SURFACE_MODE = stringPreferencesKey("surface_mode")
        private val KEY_GYRO_SENSITIVITY = stringPreferencesKey("gyro_sensitivity")
        private val KEY_TILT_INTENSITY = floatPreferencesKey("tilt_intensity")
        private val KEY_SHOW_TILT_DEBUG = booleanPreferencesKey("show_tilt_debug_overlay")
        private val KEY_MINDFUL_PAUSE_ENABLED = booleanPreferencesKey("mindful_pause_enabled")
        private val KEY_CUSTOM_PAUSED_PACKAGES = stringSetPreferencesKey("custom_paused_packages")
    }
}
