package com.example.model

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.launcherConfigDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "still_launcher_config"
)

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
 * Jetpack DataStore persistence manager for independent Clock/Menu 3D sensitivities,
 * dynamic Left/Center/Right alignments, Emu Lock Screen animation styles, eye colors,
 * facial expression preset selection, and shake recovery settings.
 */
class LauncherConfigManager(private val context: Context) {

    val configFlow: Flow<LauncherConfig> = context.launcherConfigDataStore.data.map { prefs ->
        val clockSens = (prefs[KEY_CLOCK_SENSITIVITY] ?: LauncherConfig.DEFAULT_CLOCK_SENSITIVITY)
            .coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
        val menuSens = (prefs[KEY_MENU_SENSITIVITY] ?: LauncherConfig.DEFAULT_MENU_SENSITIVITY)
            .coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)

        val clockAlign = prefs[KEY_CLOCK_ALIGNMENT]?.let { name ->
            runCatching { LauncherAlignment.valueOf(name) }.getOrNull()
        } ?: LauncherConfig.DEFAULT_CLOCK_ALIGNMENT

        val menuAlign = prefs[KEY_MENU_ALIGNMENT]?.let { name ->
            runCatching { LauncherAlignment.valueOf(name) }.getOrNull()
        } ?: LauncherConfig.DEFAULT_MENU_ALIGNMENT

        val animationStyle = prefs[KEY_EMU_ANIMATION_STYLE]?.let { name ->
            runCatching { EmuAnimationStyle.valueOf(name) }.getOrNull()
        } ?: LauncherConfig.DEFAULT_ANIMATION_STYLE

        val eyeColorHex = prefs[KEY_EYE_COLOR_HEX]?.takeIf { hex ->
            runCatching { android.graphics.Color.parseColor(hex) }.isSuccess
        } ?: LauncherConfig.DEFAULT_EYE_COLOR_HEX

        val isLiveLockEnabled = prefs[KEY_IS_LIVE_LOCK_ENABLED] ?: false
        val isClockShadowEnabled =
            prefs[KEY_CLOCK_SHADOW_ENABLED] ?: LauncherConfig.DEFAULT_CLOCK_SHADOW_ENABLED
        val selectedEmotionName =
            prefs[KEY_SELECTED_EMOTION_NAME] ?: LauncherConfig.DEFAULT_SELECTED_EMOTION_NAME
        val isShakeReactionEnabled =
            prefs[KEY_SHAKE_REACTION_ENABLED] ?: LauncherConfig.DEFAULT_SHAKE_REACTION_ENABLED
        val shakeRecoveryDurationSec = (
            prefs[KEY_SHAKE_RECOVERY_DURATION_SEC]
                ?: LauncherConfig.DEFAULT_SHAKE_RECOVERY_DURATION_SEC
            ).coerceIn(
            LauncherConfig.MIN_SHAKE_RECOVERY_DURATION_SEC,
            LauncherConfig.MAX_SHAKE_RECOVERY_DURATION_SEC
        )
        val isHomeSoundEnabled =
            prefs[KEY_HOME_SOUND_ENABLED] ?: LauncherConfig.DEFAULT_HOME_SOUND_ENABLED
        val isLockScreenSoundEnabled =
            prefs[KEY_LOCK_SCREEN_SOUND_ENABLED] ?: LauncherConfig.DEFAULT_LOCK_SCREEN_SOUND_ENABLED
        val isEyeFollowSoundEnabled =
            prefs[KEY_EYE_FOLLOW_SOUND_ENABLED] ?: LauncherConfig.DEFAULT_EYE_FOLLOW_SOUND_ENABLED

        LauncherConfig(
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

    suspend fun setClockSensitivity(value: Float) {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_CLOCK_SENSITIVITY] = value.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
        }
    }

    suspend fun setMenuSensitivity(value: Float) {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_MENU_SENSITIVITY] = value.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
        }
    }

    suspend fun setClockAlignment(alignment: LauncherAlignment) {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_CLOCK_ALIGNMENT] = alignment.name
        }
    }

    suspend fun setMenuAlignment(alignment: LauncherAlignment) {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_MENU_ALIGNMENT] = alignment.name
        }
    }

    suspend fun setEmuAnimationStyle(style: EmuAnimationStyle) {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_EMU_ANIMATION_STYLE] = style.name
        }
    }

    suspend fun setEyeColorHex(hex: String) {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_EYE_COLOR_HEX] = hex.uppercase()
        }
    }

    suspend fun setLiveLockEnabled(enabled: Boolean) {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_IS_LIVE_LOCK_ENABLED] = enabled
        }
    }

    suspend fun setClockShadowEnabled(enabled: Boolean) {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_CLOCK_SHADOW_ENABLED] = enabled
        }
    }

    suspend fun setSelectedEmotionName(name: String) {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_SELECTED_EMOTION_NAME] = name.uppercase()
        }
    }

    suspend fun setShakeReactionEnabled(enabled: Boolean) {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_SHAKE_REACTION_ENABLED] = enabled
        }
    }

    suspend fun setShakeRecoveryDurationSec(seconds: Float) {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_SHAKE_RECOVERY_DURATION_SEC] = seconds.coerceIn(
                LauncherConfig.MIN_SHAKE_RECOVERY_DURATION_SEC,
                LauncherConfig.MAX_SHAKE_RECOVERY_DURATION_SEC
            )
        }
    }

    suspend fun setHomeSoundEnabled(enabled: Boolean) {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_HOME_SOUND_ENABLED] = enabled
        }
    }

    suspend fun setLockScreenSoundEnabled(enabled: Boolean) {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_LOCK_SCREEN_SOUND_ENABLED] = enabled
        }
    }

    suspend fun setEyeFollowSoundEnabled(enabled: Boolean) {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_EYE_FOLLOW_SOUND_ENABLED] = enabled
        }
    }

    suspend fun resetToDefaults() {
        context.launcherConfigDataStore.edit { prefs ->
            prefs[KEY_CLOCK_SENSITIVITY] = LauncherConfig.DEFAULT_CLOCK_SENSITIVITY
            prefs[KEY_MENU_SENSITIVITY] = LauncherConfig.DEFAULT_MENU_SENSITIVITY
            prefs[KEY_CLOCK_ALIGNMENT] = LauncherConfig.DEFAULT_CLOCK_ALIGNMENT.name
            prefs[KEY_MENU_ALIGNMENT] = LauncherConfig.DEFAULT_MENU_ALIGNMENT.name
            prefs[KEY_EMU_ANIMATION_STYLE] = LauncherConfig.DEFAULT_ANIMATION_STYLE.name
            prefs[KEY_EYE_COLOR_HEX] = LauncherConfig.DEFAULT_EYE_COLOR_HEX
            prefs[KEY_IS_LIVE_LOCK_ENABLED] = false
            prefs[KEY_CLOCK_SHADOW_ENABLED] = LauncherConfig.DEFAULT_CLOCK_SHADOW_ENABLED
            prefs[KEY_SELECTED_EMOTION_NAME] = LauncherConfig.DEFAULT_SELECTED_EMOTION_NAME
            prefs[KEY_SHAKE_REACTION_ENABLED] = LauncherConfig.DEFAULT_SHAKE_REACTION_ENABLED
            prefs[KEY_SHAKE_RECOVERY_DURATION_SEC] =
                LauncherConfig.DEFAULT_SHAKE_RECOVERY_DURATION_SEC
            prefs[KEY_HOME_SOUND_ENABLED] = LauncherConfig.DEFAULT_HOME_SOUND_ENABLED
            prefs[KEY_LOCK_SCREEN_SOUND_ENABLED] = LauncherConfig.DEFAULT_LOCK_SCREEN_SOUND_ENABLED
            prefs[KEY_EYE_FOLLOW_SOUND_ENABLED] = LauncherConfig.DEFAULT_EYE_FOLLOW_SOUND_ENABLED
        }
    }

    companion object {
        const val MIN_SENSITIVITY = 0.0f
        const val MAX_SENSITIVITY = 2.5f

        private val KEY_CLOCK_SENSITIVITY = floatPreferencesKey("clock_sensitivity")
        private val KEY_MENU_SENSITIVITY = floatPreferencesKey("menu_sensitivity")
        private val KEY_CLOCK_ALIGNMENT = stringPreferencesKey("clock_alignment")
        private val KEY_MENU_ALIGNMENT = stringPreferencesKey("menu_alignment")
        private val KEY_EMU_ANIMATION_STYLE = stringPreferencesKey("emu_animation_style")
        private val KEY_EYE_COLOR_HEX = stringPreferencesKey("eye_color_hex")
        private val KEY_IS_LIVE_LOCK_ENABLED = booleanPreferencesKey("is_live_lock_enabled")
        private val KEY_CLOCK_SHADOW_ENABLED = booleanPreferencesKey("is_clock_shadow_enabled")
        private val KEY_SELECTED_EMOTION_NAME = stringPreferencesKey("selected_emotion_name")
        private val KEY_SHAKE_REACTION_ENABLED = booleanPreferencesKey("is_shake_reaction_enabled")
        private val KEY_SHAKE_RECOVERY_DURATION_SEC =
            floatPreferencesKey("shake_recovery_duration_sec")
        private val KEY_HOME_SOUND_ENABLED = booleanPreferencesKey("is_home_sound_enabled")
        private val KEY_LOCK_SCREEN_SOUND_ENABLED =
            booleanPreferencesKey("is_lock_screen_sound_enabled")
        private val KEY_EYE_FOLLOW_SOUND_ENABLED =
            booleanPreferencesKey("is_eye_follow_sound_enabled")
    }
}
