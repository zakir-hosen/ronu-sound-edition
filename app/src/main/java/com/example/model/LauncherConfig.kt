package com.example.model

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class EmuEyeColorPreset(
    val name: String,
    val hex: String,
    val color: Color
)

object EmuEyeColorPresets {
    val all: List<EmuEyeColorPreset> = listOf(
        EmuEyeColorPreset("Classic White", "#FFFFFF", Color(0xFFFFFFFF)),
        EmuEyeColorPreset("CMF Orange", "#FF4500", Color(0xFFFF4500)),
        EmuEyeColorPreset("Electric Cyan", "#00E5FF", Color(0xFF00E5FF)),
        EmuEyeColorPreset("Neon Mint", "#00FF88", Color(0xFF00FF88)),
        EmuEyeColorPreset("Cyber Violet", "#BB86FC", Color(0xFFBB86FC)),
        EmuEyeColorPreset("Warm Amber", "#FFB300", Color(0xFFFFB300))
    )

    fun parseHexColor(hex: String, fallback: Color = Color.White): Color {
        return runCatching {
            Color(android.graphics.Color.parseColor(hex))
        }.getOrDefault(fallback)
    }
}

enum class LauncherAlignment(val label: String, val bias: Float) {
    LEFT("Left", -1f),
    CENTER("Center", 0f),
    RIGHT("Right", 1f);

    fun toHorizontalAlignment(): Alignment.Horizontal = when (this) {
        LEFT -> Alignment.Start
        CENTER -> Alignment.CenterHorizontally
        RIGHT -> Alignment.End
    }

    fun toBiasAlignment(animatedBias: Float): Alignment.Horizontal =
        BiasAlignment.Horizontal(animatedBias)

    fun toTextAlign(): TextAlign = when (this) {
        LEFT -> TextAlign.Start
        CENTER -> TextAlign.Center
        RIGHT -> TextAlign.End
    }
}

@Immutable
data class LauncherConfig(
    val clockSensitivity: Float = DEFAULT_CLOCK_SENSITIVITY,
    val menuSensitivity: Float = DEFAULT_MENU_SENSITIVITY,
    val clockAlignment: LauncherAlignment = DEFAULT_CLOCK_ALIGNMENT,
    val menuAlignment: LauncherAlignment = DEFAULT_MENU_ALIGNMENT,
    val emuAnimationStyle: EmuAnimationStyle = DEFAULT_ANIMATION_STYLE,
    val eyeColorHex: String = DEFAULT_EYE_COLOR_HEX,
    val isLiveLockEnabled: Boolean = false,
    val isClockShadowEnabled: Boolean = DEFAULT_CLOCK_SHADOW_ENABLED,
    val selectedEmotionName: String = DEFAULT_SELECTED_EMOTION_NAME,
    val isShakeReactionEnabled: Boolean = DEFAULT_SHAKE_REACTION_ENABLED,
    val shakeRecoveryDurationSec: Float = DEFAULT_SHAKE_RECOVERY_DURATION_SEC,
    val isHomeSoundEnabled: Boolean = DEFAULT_HOME_SOUND_ENABLED,
    val isLockScreenSoundEnabled: Boolean = DEFAULT_LOCK_SCREEN_SOUND_ENABLED,
    val isEyeFollowSoundEnabled: Boolean = DEFAULT_EYE_FOLLOW_SOUND_ENABLED
) {
    companion object {
        const val DEFAULT_CLOCK_SENSITIVITY = 1.0f
        const val DEFAULT_MENU_SENSITIVITY = 0.6f
        val DEFAULT_CLOCK_ALIGNMENT = LauncherAlignment.LEFT
        val DEFAULT_MENU_ALIGNMENT = LauncherAlignment.LEFT
        val DEFAULT_ANIMATION_STYLE = EmuAnimationStyle.BOUNCY_PLAYFUL
        const val DEFAULT_EYE_COLOR_HEX = "#FFFFFF"
        const val DEFAULT_CLOCK_SHADOW_ENABLED = true
        const val DEFAULT_SELECTED_EMOTION_NAME = "ALERT"
        const val DEFAULT_SHAKE_REACTION_ENABLED = true
        const val DEFAULT_SHAKE_RECOVERY_DURATION_SEC = 4.5f
        const val MIN_SHAKE_RECOVERY_DURATION_SEC = 3.0f
        const val MAX_SHAKE_RECOVERY_DURATION_SEC = 6.0f
        const val DEFAULT_HOME_SOUND_ENABLED = true
        const val DEFAULT_LOCK_SCREEN_SOUND_ENABLED = false
        const val DEFAULT_EYE_FOLLOW_SOUND_ENABLED = false
    }
}

/**
 * SharedPreferences-backed reactive configuration manager for Clock/Menu 3D sensitivities,
 * Left/Center/Right alignments, Emu Lock Screen styles, eye colors, facial expressions,
 * shake recovery controls, and strictly isolated Home/Lock/Eye-Follow sound toggles.
 *
 * Uses a process-wide reactive [StateFlow] and Compose [State] synchronized immediately on every
 * mutation so Home, Settings, Animations Preview, CustomLockScreenActivity, and
 * LockScreenAccessibilityService stay in instant sync with zero async lag.
 */
class LauncherConfigManager(context: Context) {

    private val appContext: Context = context.applicationContext ?: context
    private val store: SharedStore = getOrCreateStore(appContext)

    val configState: StateFlow<LauncherConfig> = store.stateFlow.asStateFlow()
    val configFlow: Flow<LauncherConfig> = configState
    val configComposeState: State<LauncherConfig> = store.composeState
    val currentConfig: LauncherConfig
        get() = store.stateFlow.value

    fun setClockSensitivity(value: Float) {
        val clamped = value.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
        updateSetting(
            write = { putFloat(KEY_CLOCK_SENSITIVITY, clamped) },
            transform = { it.copy(clockSensitivity = clamped) }
        )
    }

    fun setMenuSensitivity(value: Float) {
        val clamped = value.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
        updateSetting(
            write = { putFloat(KEY_MENU_SENSITIVITY, clamped) },
            transform = { it.copy(menuSensitivity = clamped) }
        )
    }

    fun setClockAlignment(alignment: LauncherAlignment) {
        updateSetting(
            write = { putString(KEY_CLOCK_ALIGNMENT, alignment.name) },
            transform = { it.copy(clockAlignment = alignment) }
        )
    }

    fun setMenuAlignment(alignment: LauncherAlignment) {
        updateSetting(
            write = { putString(KEY_MENU_ALIGNMENT, alignment.name) },
            transform = { it.copy(menuAlignment = alignment) }
        )
    }

    fun setEmuAnimationStyle(style: EmuAnimationStyle) {
        updateSetting(
            write = { putString(KEY_EMU_ANIMATION_STYLE, style.name) },
            transform = { it.copy(emuAnimationStyle = style) }
        )
    }

    fun setEyeColorHex(hex: String) {
        val normalized = hex.uppercase()
        updateSetting(
            write = { putString(KEY_EYE_COLOR_HEX, normalized) },
            transform = { it.copy(eyeColorHex = normalized) }
        )
    }

    fun setLiveLockEnabled(enabled: Boolean) {
        updateSetting(
            write = { putBoolean(KEY_IS_LIVE_LOCK_ENABLED, enabled) },
            transform = { it.copy(isLiveLockEnabled = enabled) }
        )
    }

    fun setClockShadowEnabled(enabled: Boolean) {
        updateSetting(
            write = { putBoolean(KEY_CLOCK_SHADOW_ENABLED, enabled) },
            transform = { it.copy(isClockShadowEnabled = enabled) }
        )
    }

    fun setSelectedEmotionName(name: String) {
        val normalized = name.uppercase()
        updateSetting(
            write = { putString(KEY_SELECTED_EMOTION_NAME, normalized) },
            transform = { it.copy(selectedEmotionName = normalized) }
        )
    }

    fun setShakeReactionEnabled(enabled: Boolean) {
        updateSetting(
            write = { putBoolean(KEY_SHAKE_REACTION_ENABLED, enabled) },
            transform = { it.copy(isShakeReactionEnabled = enabled) }
        )
    }

    fun setShakeRecoveryDurationSec(seconds: Float) {
        val clamped = seconds.coerceIn(
            LauncherConfig.MIN_SHAKE_RECOVERY_DURATION_SEC,
            LauncherConfig.MAX_SHAKE_RECOVERY_DURATION_SEC
        )
        updateSetting(
            write = { putFloat(KEY_SHAKE_RECOVERY_DURATION_SEC, clamped) },
            transform = { it.copy(shakeRecoveryDurationSec = clamped) }
        )
    }

    fun setHomeSoundEnabled(enabled: Boolean) {
        updateSetting(
            write = { putBoolean(KEY_HOME_SOUND, enabled) },
            transform = { it.copy(isHomeSoundEnabled = enabled) }
        )
    }

    fun setLockScreenSoundEnabled(enabled: Boolean) {
        updateSetting(
            write = { putBoolean(KEY_LOCK_SOUND, enabled) },
            transform = { it.copy(isLockScreenSoundEnabled = enabled) }
        )
    }

    fun setEyeFollowSoundEnabled(enabled: Boolean) {
        updateSetting(
            write = { putBoolean(KEY_FOLLOW_SOUND, enabled) },
            transform = { it.copy(isEyeFollowSoundEnabled = enabled) }
        )
    }

    fun resetToDefaults() {
        val defaults = LauncherConfig()
        synchronized(storeLock) {
            store.prefs.edit()
                .putFloat(KEY_CLOCK_SENSITIVITY, defaults.clockSensitivity)
                .putFloat(KEY_MENU_SENSITIVITY, defaults.menuSensitivity)
                .putString(KEY_CLOCK_ALIGNMENT, defaults.clockAlignment.name)
                .putString(KEY_MENU_ALIGNMENT, defaults.menuAlignment.name)
                .putString(KEY_EMU_ANIMATION_STYLE, defaults.emuAnimationStyle.name)
                .putString(KEY_EYE_COLOR_HEX, defaults.eyeColorHex)
                .putBoolean(KEY_IS_LIVE_LOCK_ENABLED, defaults.isLiveLockEnabled)
                .putBoolean(KEY_CLOCK_SHADOW_ENABLED, defaults.isClockShadowEnabled)
                .putString(KEY_SELECTED_EMOTION_NAME, defaults.selectedEmotionName)
                .putBoolean(KEY_SHAKE_REACTION_ENABLED, defaults.isShakeReactionEnabled)
                .putFloat(KEY_SHAKE_RECOVERY_DURATION_SEC, defaults.shakeRecoveryDurationSec)
                .putBoolean(KEY_HOME_SOUND, defaults.isHomeSoundEnabled)
                .putBoolean(KEY_LOCK_SOUND, defaults.isLockScreenSoundEnabled)
                .putBoolean(KEY_FOLLOW_SOUND, defaults.isEyeFollowSoundEnabled)
                .apply()
            store.publish(defaults)
        }
    }

    private inline fun updateSetting(
        write: SharedPreferences.Editor.() -> Unit,
        transform: (LauncherConfig) -> LauncherConfig
    ) {
        synchronized(storeLock) {
            val editor = store.prefs.edit()
            editor.write()
            editor.apply()
            val updated = transform(store.stateFlow.value)
            store.publish(updated)
        }
    }

    private class SharedStore(
        val appContext: Context,
        val prefs: SharedPreferences,
        initialConfig: LauncherConfig
    ) {
        val stateFlow: MutableStateFlow<LauncherConfig> = MutableStateFlow(initialConfig)
        val composeState: MutableState<LauncherConfig> = mutableStateOf(initialConfig)

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPrefs, _ ->
            publish(readConfigFromPrefs(sharedPrefs))
        }

        init {
            prefs.registerOnSharedPreferenceChangeListener(listener)
        }

        fun publish(config: LauncherConfig) {
            stateFlow.value = config
            composeState.value = config
        }
    }

    companion object {
        const val MIN_SENSITIVITY = 0.0f
        const val MAX_SENSITIVITY = 2.5f

        const val PREFS_NAME = "still_launcher_config_prefs"

        // Strictly isolated sound keys
        private const val KEY_HOME_SOUND = "pref_home_screen_sound_enabled"
        private const val KEY_LOCK_SOUND = "pref_lock_screen_sound_enabled"
        private const val KEY_FOLLOW_SOUND = "pref_eye_follow_sound_enabled"

        // Strictly unique keys for all other visual, motion & lock settings
        private const val KEY_CLOCK_SENSITIVITY = "pref_clock_sensitivity"
        private const val KEY_MENU_SENSITIVITY = "pref_menu_sensitivity"
        private const val KEY_CLOCK_ALIGNMENT = "pref_clock_alignment"
        private const val KEY_MENU_ALIGNMENT = "pref_menu_alignment"
        private const val KEY_EMU_ANIMATION_STYLE = "pref_emu_animation_style"
        private const val KEY_EYE_COLOR_HEX = "pref_eye_color_hex"
        private const val KEY_IS_LIVE_LOCK_ENABLED = "pref_is_live_lock_enabled"
        private const val KEY_CLOCK_SHADOW_ENABLED = "pref_is_clock_shadow_enabled"
        private const val KEY_SELECTED_EMOTION_NAME = "pref_selected_emotion_name"
        private const val KEY_SHAKE_REACTION_ENABLED = "pref_is_shake_reaction_enabled"
        private const val KEY_SHAKE_RECOVERY_DURATION_SEC = "pref_shake_recovery_duration_sec"

        private val storeLock = Any()

        @Volatile
        private var sharedStore: SharedStore? = null

        private fun getOrCreateStore(appContext: Context): SharedStore {
            val existing = sharedStore
            if (existing != null && existing.appContext === appContext) {
                return existing
            }
            return synchronized(storeLock) {
                val doubleCheck = sharedStore
                if (doubleCheck != null && doubleCheck.appContext === appContext) {
                    doubleCheck
                } else {
                    doubleCheck?.prefs?.unregisterOnSharedPreferenceChangeListener(doubleCheck.listener)
                    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val initial = readConfigFromPrefs(prefs)
                    SharedStore(
                        appContext = appContext,
                        prefs = prefs,
                        initialConfig = initial
                    ).also { sharedStore = it }
                }
            }
        }

        private fun readConfigFromPrefs(prefs: SharedPreferences): LauncherConfig {
            val clockSens = prefs.getFloat(KEY_CLOCK_SENSITIVITY, LauncherConfig.DEFAULT_CLOCK_SENSITIVITY)
                .coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
            val menuSens = prefs.getFloat(KEY_MENU_SENSITIVITY, LauncherConfig.DEFAULT_MENU_SENSITIVITY)
                .coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)

            val clockAlign = prefs.getString(KEY_CLOCK_ALIGNMENT, null)?.let { name ->
                runCatching { LauncherAlignment.valueOf(name) }.getOrNull()
            } ?: LauncherConfig.DEFAULT_CLOCK_ALIGNMENT

            val menuAlign = prefs.getString(KEY_MENU_ALIGNMENT, null)?.let { name ->
                runCatching { LauncherAlignment.valueOf(name) }.getOrNull()
            } ?: LauncherConfig.DEFAULT_MENU_ALIGNMENT

            val animationStyle = prefs.getString(KEY_EMU_ANIMATION_STYLE, null)?.let { name ->
                runCatching { EmuAnimationStyle.valueOf(name) }.getOrNull()
            } ?: LauncherConfig.DEFAULT_ANIMATION_STYLE

            val eyeColorHex = prefs.getString(KEY_EYE_COLOR_HEX, null)?.takeIf { hex ->
                runCatching { android.graphics.Color.parseColor(hex) }.isSuccess
            } ?: LauncherConfig.DEFAULT_EYE_COLOR_HEX

            val isLiveLockEnabled = prefs.getBoolean(KEY_IS_LIVE_LOCK_ENABLED, false)
            val isClockShadowEnabled = prefs.getBoolean(
                KEY_CLOCK_SHADOW_ENABLED,
                LauncherConfig.DEFAULT_CLOCK_SHADOW_ENABLED
            )
            val selectedEmotionName = prefs.getString(
                KEY_SELECTED_EMOTION_NAME,
                LauncherConfig.DEFAULT_SELECTED_EMOTION_NAME
            ) ?: LauncherConfig.DEFAULT_SELECTED_EMOTION_NAME
            val isShakeReactionEnabled = prefs.getBoolean(
                KEY_SHAKE_REACTION_ENABLED,
                LauncherConfig.DEFAULT_SHAKE_REACTION_ENABLED
            )
            val shakeRecoveryDurationSec = prefs.getFloat(
                KEY_SHAKE_RECOVERY_DURATION_SEC,
                LauncherConfig.DEFAULT_SHAKE_RECOVERY_DURATION_SEC
            ).coerceIn(
                LauncherConfig.MIN_SHAKE_RECOVERY_DURATION_SEC,
                LauncherConfig.MAX_SHAKE_RECOVERY_DURATION_SEC
            )
            val isHomeSoundEnabled = prefs.getBoolean(
                KEY_HOME_SOUND,
                LauncherConfig.DEFAULT_HOME_SOUND_ENABLED
            )
            val isLockScreenSoundEnabled = prefs.getBoolean(
                KEY_LOCK_SOUND,
                LauncherConfig.DEFAULT_LOCK_SCREEN_SOUND_ENABLED
            )
            val isEyeFollowSoundEnabled = prefs.getBoolean(
                KEY_FOLLOW_SOUND,
                LauncherConfig.DEFAULT_EYE_FOLLOW_SOUND_ENABLED
            )

            return LauncherConfig(
                clockSensitivity = clockSens,
                menuSensitivity = menuSens,
                clockAlignment = clockAlign,
                menuAlignment = menuAlign,
                emuAnimationStyle = animationStyle,
                eyeColorHex = eyeColorHex,
                isLiveLockEnabled = isLiveLockEnabled,
                isClockShadowEnabled = isClockShadowEnabled,
                selectedEmotionName = selectedEmotionName,
                isShakeReactionEnabled = isShakeReactionEnabled,
                shakeRecoveryDurationSec = shakeRecoveryDurationSec,
                isHomeSoundEnabled = isHomeSoundEnabled,
                isLockScreenSoundEnabled = isLockScreenSoundEnabled,
                isEyeFollowSoundEnabled = isEyeFollowSoundEnabled
            )
        }
    }
}
