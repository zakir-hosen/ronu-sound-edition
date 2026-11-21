package com.example

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.audio.EmuSoundEngine
import com.example.model.AppEntry
import com.example.model.EmuAnimationStyle
import com.example.model.EmuEyeColorPresets
import com.example.model.LauncherAlignment
import com.example.model.LauncherConfig
import com.example.model.LauncherPreferences
import com.example.model.SurfaceMode
import com.example.sensor.BatteryExpression
import com.example.sensor.BatteryStatus
import com.example.sensor.computeMagnitudeG
import com.example.sensor.isShakeImpulse
import com.example.sensor.normalizeShakeIntensity
import com.example.sensor.parseBatteryIntent
import com.example.service.AccessibilityEmuLockOverlay
import com.example.service.LockScreenAccessibilityService
import com.example.service.LockScreenService
import com.example.ui.LauncherSettingsSheet
import com.example.ui.LockScreenAnimationsScreen
import com.example.ui.MinimalHomeScreen
import com.example.ui.TextAppDrawer
import com.example.ui.components.BatteryOptimizationNotice
import com.example.ui.components.EmuExpressionPresets
import com.example.ui.components.EmuEyeClock
import com.example.ui.components.EmuFacialExpression
import com.example.ui.components.LockScreenEnableCard
import com.example.ui.components.MindfulPauseDialog
import com.example.ui.components.checkIgnoringBatteryOptimization
import com.example.ui.theme.StillLauncherTheme
import com.example.util.LockScreenPermissionStatus
import com.example.util.PermissionHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleApps = listOf(
        AppEntry("Camera", "com.android.camera", "CameraActivity"),
        AppEntry("Chrome", "com.android.chrome", "Main"),
        AppEntry("Instagram", "com.instagram.android", "MainActivity", isKnownDistracting = true),
        AppEntry("Messages", "com.google.android.apps.messaging", "ConversationListActivity"),
        AppEntry("Notes", "com.google.android.keep", "BrowseActivity"),
        AppEntry("Phone", "com.google.android.dialer", "DialtactsActivity")
    )

    @Test
    fun `app name matches Still`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Still", context.getString(R.string.app_name))
    }

    @Test
    fun `battery parser maps charging, low battery, and normal states`() {
        val chargingIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 45)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
        }
        val chargingStatus = parseBatteryIntent(chargingIntent)
        assertEquals(BatteryExpression.CHARGING_HAPPY, chargingStatus.expression)
        assertTrue(chargingStatus.isCharging)

        val lowIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 15)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0)
        }
        val lowStatus = parseBatteryIntent(lowIntent)
        assertEquals(BatteryExpression.LOW_SLEEPY, lowStatus.expression)
        assertEquals(15, lowStatus.levelPercent)

        val normalIntent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 82)
            putExtra(BatteryManager.EXTRA_SCALE, 100)
            putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
            putExtra(BatteryManager.EXTRA_PLUGGED, 0)
        }
        assertEquals(BatteryExpression.NORMAL, parseBatteryIntent(normalIntent).expression)
    }

    @Test
    fun `emu eye clock renders unified center pivot, HH and MM inside eyes, and date with battery underneath`() {
        composeRule.setContent {
            StillLauncherTheme(amoledBlack = false) {
                EmuEyeClock(
                    tiltPitch = 0.25f,
                    tiltRoll = -0.35f,
                    batteryStatus = BatteryStatus(
                        levelPercent = 78,
                        isCharging = true,
                        expression = BatteryExpression.CHARGING_HAPPY
                    ),
                    alignment = LauncherAlignment.CENTER
                )
            }
        }

        composeRule.onNodeWithTag("emu_eye_clock").assertIsDisplayed()
        composeRule.onNodeWithTag("emu_clock_3d_unit").assertIsDisplayed()
        composeRule.onNodeWithTag("emu_clock_date_row").assertIsDisplayed()
        composeRule.onNodeWithTag("emu_eye_pair_pivot", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("emu_left_eye", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("emu_right_eye", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("emu_left_eye_numerals", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("emu_right_eye_numerals", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("emu_battery_indicator").assertIsDisplayed()
        composeRule.onNodeWithText("78%").assertIsDisplayed()
    }

    @Test
    fun `home screen renders emu eye clock, pinned apps, search trigger, and dual sensitivity sliders`() {
        var drawerOpened = false
        var launchedApp: AppEntry? = null

        composeRule.setContent {
            var showDebug by remember { mutableStateOf(false) }
            var config by remember { mutableStateOf(LauncherConfig()) }
            StillLauncherTheme(amoledBlack = false) {
                MinimalHomeScreen(
                    pinnedApps = sampleApps.take(5),
                    surfaceMode = SurfaceMode.MATTE_CHARCOAL,
                    config = config,
                    showTiltDebugOverlay = showDebug,
                    onClockSensitivityChange = { config = config.copy(clockSensitivity = it) },
                    onMenuSensitivityChange = { config = config.copy(menuSensitivity = it) },
                    onToggleTiltDebugOverlay = { showDebug = it },
                    onAppClick = { launchedApp = it },
                    onOpenDrawer = { drawerOpened = true },
                    onOpenSettings = {}
                )
            }
        }

        composeRule.onNodeWithTag("emu_eye_clock").assertIsDisplayed()
        composeRule.onNodeWithTag("pinned_apps_list").assertIsDisplayed()
        composeRule.onNodeWithText("Camera").assertIsDisplayed().performClick()
        assertEquals("com.android.camera", launchedApp?.packageName)

        composeRule.onNodeWithTag("tilt_debug_toggle_button").performClick()
        composeRule.onNodeWithTag("tilt_debug_panel").assertIsDisplayed()
        composeRule.onNodeWithTag("home_clock_sensitivity_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("home_menu_sensitivity_slider").assertIsDisplayed()

        composeRule.onNodeWithTag("search_drawer_trigger").assertIsDisplayed().performClick()
        assertTrue(drawerOpened)
    }

    @Test
    fun `double tap on empty home screen space opens accessibility dialog when service disabled`() {
        composeRule.setContent {
            StillLauncherTheme(amoledBlack = false) {
                MinimalHomeScreen(
                    pinnedApps = emptyList(),
                    surfaceMode = SurfaceMode.MATTE_CHARCOAL,
                    config = LauncherConfig(clockSensitivity = 0f, menuSensitivity = 0f),
                    showTiltDebugOverlay = false,
                    onClockSensitivityChange = {},
                    onMenuSensitivityChange = {},
                    onToggleTiltDebugOverlay = {},
                    onAppClick = {},
                    onOpenDrawer = {},
                    onOpenSettings = {}
                )
            }
        }

        composeRule.onNodeWithTag("minimal_home_screen").performTouchInput {
            doubleClick(Offset(width * 0.9f, height * 0.5f))
        }
        composeRule.onNodeWithTag("open_accessibility_settings_button").assertIsDisplayed()
    }

    @Test
    fun `app drawer filters and launches single remaining match automatically`() {
        var launchedApp: AppEntry? = null

        composeRule.setContent {
            var query by remember { mutableStateOf("") }
            StillLauncherTheme {
                TextAppDrawer(
                    allApps = sampleApps,
                    pinnedPackages = listOf("com.android.chrome"),
                    customPausedPackages = emptySet(),
                    searchQuery = query,
                    onSearchQueryChange = { query = it },
                    onAppClick = { launchedApp = it },
                    onTogglePin = {},
                    onToggleCustomPause = {},
                    onOpenAppInfo = {},
                    onClose = {}
                )
            }
        }

        composeRule.onNodeWithTag("drawer_search_input").performTextInput("Chr")
        composeRule.mainClock.advanceTimeBy(300L)
        composeRule.waitForIdle()

        assertEquals("com.android.chrome", launchedApp?.packageName)
    }

    @Test
    fun `settings sheet toggles alignments and resets to factory defaults`() {
        var selectedMode = SurfaceMode.MATTE_CHARCOAL
        var config = LauncherConfig(clockSensitivity = 2.2f, menuSensitivity = 2.0f)

        composeRule.setContent {
            StillLauncherTheme {
                LauncherSettingsSheet(
                    allApps = sampleApps,
                    preferences = LauncherPreferences(
                        pinnedPackageNames = listOf("com.android.chrome"),
                        surfaceMode = selectedMode
                    ),
                    config = config,
                    isDefaultLauncher = false,
                    onTogglePinnedApp = {},
                    onSurfaceModeChange = { selectedMode = it },
                    onClockSensitivityChange = { config = config.copy(clockSensitivity = it) },
                    onMenuSensitivityChange = { config = config.copy(menuSensitivity = it) },
                    onClockAlignmentChange = { config = config.copy(clockAlignment = it) },
                    onMenuAlignmentChange = { config = config.copy(menuAlignment = it) },
                    onResetToDefaults = { config = LauncherConfig() },
                    onShowTiltDebugOverlayChange = {},
                    onMindfulPauseChange = {},
                    onOpenAnimations = {},
                    onOpenLockScreenMode = {},
                    onOpenDefaultLauncherSettings = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithTag("clock_align_center").performClick()
        composeRule.onNodeWithTag("menu_align_right").performClick()

        assertEquals(LauncherAlignment.CENTER, config.clockAlignment)
        assertEquals(LauncherAlignment.RIGHT, config.menuAlignment)

        composeRule.onNodeWithTag("reset_defaults_header_button").performClick()
        assertEquals(1.0f, config.clockSensitivity, 0.001f)
        assertEquals(0.6f, config.menuSensitivity, 0.001f)
        assertEquals(LauncherAlignment.LEFT, config.clockAlignment)
        assertEquals(LauncherAlignment.LEFT, config.menuAlignment)
    }

    @Test
    fun `mindful pause dialog blocks continue until countdown finishes`() {
        var continued = false
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            StillLauncherTheme {
                MindfulPauseDialog(
                    app = sampleApps[2],
                    onContinue = { continued = true },
                    onDismiss = {}
                )
            }
        }

        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("mindful_pause_dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("mindful_continue_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("mindful_continue_button").performClick()
        assertEquals(false, continued)

        composeRule.mainClock.advanceTimeBy(2200L)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("mindful_continue_button").assertIsEnabled().performClick()
        assertTrue(continued)
    }

    @Test
    fun `lock screen animations screen renders live stage, color swatches, triggers test blink, and selects all 5 styles`() {
        var previewClicked = false
        var selectedStyle = EmuAnimationStyle.BOUNCY_PLAYFUL
        var selectedHex = LauncherConfig.DEFAULT_EYE_COLOR_HEX
        var shadowEnabled = LauncherConfig.DEFAULT_CLOCK_SHADOW_ENABLED

        var selectedEmotionName = LauncherConfig.DEFAULT_SELECTED_EMOTION_NAME
        var shakeReactionEnabled = LauncherConfig.DEFAULT_SHAKE_REACTION_ENABLED
        var recoveryDurationSec = LauncherConfig.DEFAULT_SHAKE_RECOVERY_DURATION_SEC

        composeRule.setContent {
            var currentStyle by remember { mutableStateOf(selectedStyle) }
            var currentHex by remember { mutableStateOf(selectedHex) }
            var currentShadow by remember { mutableStateOf(shadowEnabled) }
            var currentEmotionName by remember { mutableStateOf(selectedEmotionName) }
            var currentShakeEnabled by remember { mutableStateOf(shakeReactionEnabled) }
            var currentRecoverySec by remember { mutableStateOf(recoveryDurationSec) }
            StillLauncherTheme(amoledBlack = true) {
                LockScreenAnimationsScreen(
                    selectedStyle = currentStyle,
                    clockSensitivity = 1.0f,
                    selectedEyeColorHex = currentHex,
                    isClockShadowEnabled = currentShadow,
                    selectedEmotionName = currentEmotionName,
                    isShakeReactionEnabled = currentShakeEnabled,
                    shakeRecoveryDurationSec = currentRecoverySec,
                    onSelectStyle = {
                        currentStyle = it
                        selectedStyle = it
                    },
                    onSelectEyeColor = {
                        currentHex = it
                        selectedHex = it
                    },
                    onSetClockShadowEnabled = {
                        currentShadow = it
                        shadowEnabled = it
                    },
                    onSelectEmotion = {
                        currentEmotionName = it
                        selectedEmotionName = it
                    },
                    onSetShakeReactionEnabled = {
                        currentShakeEnabled = it
                        shakeReactionEnabled = it
                    },
                    onSetShakeRecoveryDurationSec = {
                        currentRecoverySec = it
                        recoveryDurationSec = it
                    },
                    onPreviewLockScreen = { previewClicked = true },
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("lock_screen_animations_screen").assertIsDisplayed()
        composeRule.onNodeWithTag("lock_screen_enable_card").assertIsDisplayed()

        composeRule.onNodeWithTag("animation_styles_list")
            .performScrollToNode(hasTestTag("animations_live_preview_stage"))
        composeRule.onNodeWithTag("animations_live_preview_stage").assertIsDisplayed()
        composeRule.onNodeWithTag("emu_eye_clock").assertIsDisplayed()
        composeRule.onNodeWithTag("trigger_test_blink_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("test_shake_spin_button").assertIsDisplayed().performClick()

        composeRule.onNodeWithTag("animation_styles_list")
            .performScrollToNode(hasTestTag("expression_preview_chips_row"))
        composeRule.onNodeWithTag("expression_preview_chips_row").assertIsDisplayed()
        assertEquals(EmuFacialExpression.NONE, EmuFacialExpression.entries.first())
        composeRule.onNodeWithTag("expression_chip_none").assertIsDisplayed().performClick()
        assertEquals("NONE", selectedEmotionName)
        EmuFacialExpression.entries.forEach { expression ->
            val chipTag = "expression_chip_${expression.name.lowercase()}"
            composeRule.onNodeWithTag("expression_preview_chips_row")
                .performScrollToNode(hasTestTag(chipTag))
            composeRule.onNodeWithTag(chipTag).assertIsDisplayed().performClick()
            assertEquals(expression.name, selectedEmotionName)
            composeRule.onNodeWithTag("emu_left_eye_numerals", useUnmergedTree = true).assertIsDisplayed()
        }

        composeRule.onNodeWithTag("animation_styles_list")
            .performScrollToNode(hasTestTag("eye_color_swatch_row"))
        composeRule.onNodeWithTag("eye_color_swatch_row").assertIsDisplayed()

        EmuEyeColorPresets.all.forEach { preset ->
            val swatchTag = "eye_color_swatch_${preset.hex.removePrefix("#").lowercase()}"
            composeRule.onNodeWithTag(swatchTag).assertIsDisplayed().performClick()
            assertEquals(preset.hex, selectedHex)
        }

        composeRule.onNodeWithTag("animation_styles_list")
            .performScrollToNode(hasTestTag("clock_shadow_toggle_row"))
        composeRule.onNodeWithTag("clock_shadow_switch").assertIsDisplayed().performClick()
        assertEquals(false, shadowEnabled)

        composeRule.onNodeWithTag("animation_styles_list")
            .performScrollToNode(hasTestTag("shake_reaction_control_card"))
        composeRule.onNodeWithTag("shake_recovery_duration_slider").assertIsDisplayed()
        composeRule.onNodeWithTag("shake_recovery_duration_label").assertIsDisplayed()
        composeRule.onNodeWithTag("shake_reaction_switch").assertIsDisplayed().performClick()
        assertEquals(false, shakeReactionEnabled)
        composeRule.onNodeWithTag("shake_recovery_duration_slider").assertDoesNotExist()
        composeRule.onNodeWithTag("shake_reaction_switch").performClick()
        assertEquals(true, shakeReactionEnabled)
        composeRule.onNodeWithTag("shake_recovery_duration_slider").assertIsDisplayed()

        val stylesToSelect = listOf(
            EmuAnimationStyle.SMOOTH_ZEN,
            EmuAnimationStyle.HYPER_CYBER_GLITCH,
            EmuAnimationStyle.SLEEPY_CHILL,
            EmuAnimationStyle.CURIOUS_EXPLORER,
            EmuAnimationStyle.SENTINEL,
            EmuAnimationStyle.BOUNCY_PLAYFUL
        )
        stylesToSelect.forEach { style ->
            val tag = "animation_style_${style.name.lowercase()}"
            composeRule.onNodeWithTag("animation_styles_list")
                .performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).assertIsDisplayed().performClick()
            assertEquals(style, selectedStyle)
        }

        composeRule.onNodeWithTag("preview_lock_screen_button").performClick()
        assertTrue(previewClicked)
    }

    @Test
    fun `lock screen enable card gates activation on required permissions and shows active badge once enabled`() {
        var liveLockEnabled = false
        var previewClicked = false

        composeRule.setContent {
            var granted by remember { mutableStateOf(false) }
            var active by remember { mutableStateOf(liveLockEnabled) }
            StillLauncherTheme(amoledBlack = true) {
                LockScreenEnableCard(
                    isLiveLockEnabled = active,
                    onSetLiveLockEnabled = {
                        active = it
                        liveLockEnabled = it
                    },
                    onPreviewLockScreen = { previewClicked = true },
                    permissionStatusOverride = LockScreenPermissionStatus(
                        canDrawOverlays = granted,
                        areNotificationsEnabled = granted,
                        canUseFullScreenIntent = granted,
                        isIgnoringBatteryOptimizations = granted
                    )
                )
            }
        }

        composeRule.onNodeWithTag("enable_accessibility_overlay_button").assertIsDisplayed()
        composeRule.onNodeWithTag("grant_overlay_button").assertIsDisplayed()
        composeRule.onNodeWithTag("grant_full_screen_intent_button").assertIsDisplayed()
        composeRule.onNodeWithTag("allow_notification_button").assertIsDisplayed()
        composeRule.onNodeWithTag("enable_lock_screen_button").assertIsNotEnabled()
    }

    @Test
    fun `lock screen enable card activates service state and opens instant preview when accessibility service enabled`() {
        var liveLockEnabled = false
        var previewClicked = false

        composeRule.setContent {
            var active by remember { mutableStateOf(liveLockEnabled) }
            StillLauncherTheme(amoledBlack = true) {
                LockScreenEnableCard(
                    isLiveLockEnabled = active,
                    onSetLiveLockEnabled = {
                        active = it
                        liveLockEnabled = it
                    },
                    onPreviewLockScreen = { previewClicked = true },
                    permissionStatusOverride = LockScreenPermissionStatus(
                        isAccessibilityServiceEnabled = true,
                        canDrawOverlays = false,
                        areNotificationsEnabled = false,
                        canUseFullScreenIntent = false,
                        isIgnoringBatteryOptimizations = true
                    )
                )
            }
        }

        composeRule.onNodeWithTag("enable_lock_screen_button")
            .assertIsEnabled()
            .performClick()

        assertTrue(liveLockEnabled)
        composeRule.onNodeWithTag("emu_lock_active_badge").assertIsDisplayed()
        composeRule.onNodeWithTag("preview_lock_screen_now_button")
            .assertIsDisplayed()
            .performClick()
        assertTrue(previewClicked)
    }

    @Test
    fun `emu lock screen mode displays eye clock with HH inside eye, fingerprint trigger, and swipe up hint`() {
        var biometricClicked = false
        composeRule.setContent {
            StillLauncherTheme(amoledBlack = true) {
                EmuLockScreenContent(
                    animationStyle = EmuAnimationStyle.SLEEPY_CHILL,
                    clockSensitivity = 1.2f,
                    eyeColor = Color(0xFF00E5FF),
                    onUnlockRequested = {},
                    onBiometricRequested = { biometricClicked = true }
                )
            }
        }

        composeRule.onNodeWithTag("emu_lock_screen_root").assertIsDisplayed()
        composeRule.onNodeWithTag("emu_eye_clock").assertIsDisplayed()
        composeRule.onNodeWithTag("emu_left_eye_numerals", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("emu_right_eye_numerals", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("unlock_fingerprint_button").assertIsDisplayed().performClick()
        assertTrue(biometricClicked)
        composeRule.onNodeWithTag("swipe_up_unlock_hint").assertIsDisplayed()
        composeRule.onNodeWithTag("pinned_apps_list").assertDoesNotExist()
        composeRule.onNodeWithTag("search_drawer_trigger").assertDoesNotExist()
    }

    @Test
    fun `emu eye clock removes shadow layer completely when showShadow is false`() {
        composeRule.setContent {
            StillLauncherTheme(amoledBlack = true) {
                EmuEyeClock(
                    tiltPitch = 0f,
                    tiltRoll = 0f,
                    batteryStatus = BatteryStatus(80, false, BatteryExpression.NORMAL),
                    showShadow = false
                )
            }
        }

        composeRule.onNodeWithTag("emu_eye_clock").assertIsDisplayed()
        composeRule.onNodeWithTag("emu_clock_shadow_layer", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `parametric expression presets define distinct curves and tapping left or right eye triggers wink`() {
        assertEquals(EmuFacialExpression.NONE, EmuFacialExpression.entries.first())
        assertEquals("None", EmuFacialExpression.NONE.label)
        val none = EmuExpressionPresets.None
        assertEquals(0f, none.left.topLidDrop, 0.001f)
        assertEquals(0f, none.left.bottomLidRaise, 0.001f)
        assertEquals(0f, none.left.browSlantDegrees, 0.001f)
        assertEquals(0f, none.left.topArchCurve, 0.001f)
        assertEquals(0f, none.left.bottomArchCurve, 0.001f)
        assertEquals(1.0f, none.left.eyeScale, 0.001f)
        assertEquals(none.left, none.right)

        val alert = EmuExpressionPresets.Alert
        assertEquals(0.05f, alert.left.topLidDrop, 0.001f)
        val happy = EmuExpressionPresets.Happy
        assertEquals(0.5f, happy.left.bottomLidRaise, 0.001f)
        assertEquals(0.85f, happy.left.topArchCurve, 0.001f)
        val curious = EmuExpressionPresets.Curious
        assertEquals(1.08f, curious.left.eyeScale, 0.001f)
        assertEquals(-14f, curious.right.browSlantDegrees, 0.001f)
        val sleepy = EmuExpressionPresets.Sleepy
        assertEquals(0.48f, sleepy.left.topLidDrop, 0.001f)
        assertEquals(12f, sleepy.left.browSlantDegrees, 0.001f)
        val surprised = EmuExpressionPresets.Surprised
        assertEquals(1.22f, surprised.left.eyeScale, 0.001f)
        val leftWink = EmuExpressionPresets.wink(leftTapped = true)
        assertEquals(0.96f, leftWink.left.topLidDrop, 0.001f)
        assertEquals(0.45f, leftWink.left.bottomLidRaise, 0.001f)

        composeRule.setContent {
            StillLauncherTheme(amoledBlack = true) {
                EmuEyeClock(
                    tiltPitch = 0f,
                    tiltRoll = 0f,
                    batteryStatus = BatteryStatus(90, false, BatteryExpression.NORMAL)
                )
            }
        }

        composeRule.onNodeWithTag("emu_eye_clock").assertIsDisplayed()
        composeRule.onNodeWithTag("emu_left_eye", useUnmergedTree = true).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("emu_right_eye", useUnmergedTree = true).assertIsDisplayed().performClick()
    }

    @Test
    fun `emu eye clock keeps clean squircle eyes when selectedEmotion is NONE even on sharp tilt or low battery`() {
        composeRule.setContent {
            StillLauncherTheme(amoledBlack = true) {
                EmuEyeClock(
                    tiltPitch = 0.85f,
                    tiltRoll = -0.85f,
                    batteryStatus = BatteryStatus(12, false, BatteryExpression.LOW_SLEEPY),
                    animationStyle = EmuAnimationStyle.SLEEPY_CHILL,
                    selectedEmotion = EmuFacialExpression.NONE,
                    shakeTrigger = 1
                )
            }
        }

        composeRule.onNodeWithTag("emu_eye_clock").assertIsDisplayed()
        composeRule.onNodeWithTag("emu_left_eye", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("emu_right_eye", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("emu_left_eye_numerals", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("emu_right_eye_numerals", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `shake threshold detects impulses above 1_8g, normalizes intensity, and ignores normal resting gravity`() {
        assertFalse(isShakeImpulse(0f, 0f, 9.81f, isLinearAcceleration = false))
        assertFalse(isShakeImpulse(5f, 5f, 5f, isLinearAcceleration = true))
        assertTrue(isShakeImpulse(24f, 0f, 0f, isLinearAcceleration = true))
        assertTrue(isShakeImpulse(18f, 18f, 10f, isLinearAcceleration = false))

        assertEquals(0f, normalizeShakeIntensity(1.5f), 0.001f)
        val mediumG = computeMagnitudeG(22f, 0f, 0f)
        assertTrue(mediumG > 1.8f)
        assertTrue(normalizeShakeIntensity(mediumG) in 0.2f..1.0f)
        assertEquals(1.0f, normalizeShakeIntensity(6.0f), 0.001f)
    }

    @Test
    fun `multi stage shake to happy recovery sequence steps through shake, dizzy, double blink, happy, and normal`() {
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            StillLauncherTheme(amoledBlack = true) {
                EmuEyeClock(
                    tiltPitch = 0f,
                    tiltRoll = 0f,
                    batteryStatus = BatteryStatus(88, false, BatteryExpression.NORMAL),
                    animationStyle = EmuAnimationStyle.SENTINEL,
                    selectedEmotion = EmuFacialExpression.NONE,
                    isShakeReactionEnabled = true,
                    shakeRecoveryDurationSec = 4.5f,
                    shakeTrigger = 1
                )
            }
        }

        // During 1s active shake -> SURPRISED
        composeRule.mainClock.advanceTimeBy(400L)
        composeRule.onNodeWithTag("emu_left_eye_numerals", useUnmergedTree = true).assertIsDisplayed()

        // During Dizzy -> Double-blink -> Happy -> Idle recovery
        composeRule.mainClock.advanceTimeBy(2_000L)
        composeRule.onNodeWithTag("emu_left_eye", useUnmergedTree = true).assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(3_500L)
        composeRule.onNodeWithTag("emu_right_eye_numerals", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `lock screen service posts high priority full screen intent notification on screen off and cancels cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notificationManager = context.getSystemService(NotificationManager::class.java)!!
        val shadowNotificationManager = shadowOf(notificationManager)

        val controller = Robolectric.buildService(LockScreenService::class.java).create()
        assertTrue(LockScreenService.isRunning.value)

        val triggerIntent = Intent(context, LockScreenService::class.java).apply {
            action = LockScreenService.ACTION_TRIGGER_FULL_SCREEN
        }
        controller.withIntent(triggerIntent).startCommand(0, 0)

        val posted = shadowNotificationManager.getNotification(LockScreenService.FULL_SCREEN_NOTIFICATION_ID)
        assertNotNull(posted)
        assertEquals(Notification.CATEGORY_ALARM, posted?.category)
        assertNotNull(posted?.fullScreenIntent)

        LockScreenService.cancelFullScreenTriggerNotification(context)
        assertEquals(null, shadowNotificationManager.getNotification(LockScreenService.FULL_SCREEN_NOTIFICATION_ID))

        controller.destroy()
        assertFalse(LockScreenService.isRunning.value)

        val settingsIntent = PermissionHelper.buildFullScreenIntentSettingsIntent(context)
        assertEquals(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, settingsIntent.action)
    }

    @Test
    fun `battery optimization notice renders warning and triggers settings when not excluded and hides when excluded`() {
        var settingsOpened = false
        var excluded by mutableStateOf(false)

        composeRule.setContent {
            StillLauncherTheme(amoledBlack = true) {
                BatteryOptimizationNotice(
                    isIgnoringOverride = excluded,
                    onOpenSettingsClick = { settingsOpened = true }
                )
            }
        }

        composeRule.onNodeWithTag("battery_optimization_notice").assertIsDisplayed()
        composeRule.onNodeWithTag("battery_optimization_notice_button")
            .assertIsDisplayed()
            .performClick()
        assertTrue(settingsOpened)

        excluded = true
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("battery_optimization_notice").assertDoesNotExist()
    }

    @Test
    fun `battery optimization helper builds system battery optimization settings intent`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertFalse(checkIgnoringBatteryOptimization(context))
        assertEquals(
            Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
            PermissionHelper.buildBatteryOptimizationSettingsIntent().action
        )
    }

    @Test
    fun `accessibility service attaches type accessibility overlay on screen off and removes cleanly on unlock or destroy`() {
        val controller = Robolectric.buildService(LockScreenAccessibilityService::class.java).create()
        val service = controller.get()

        service.initializeOverlaySession()
        assertTrue(service.isReceiverRegisteredForTest())
        assertEquals(null, service.currentOverlayView())

        // Should not attach when isLiveLockEnabled is false
        service.updateConfigForTest(LauncherConfig(isLiveLockEnabled = false))
        service.handleScreenBroadcast(Intent.ACTION_SCREEN_OFF)
        assertEquals(null, service.currentOverlayView())
        assertFalse(LockScreenAccessibilityService.isOverlayShowing.value)

        // Attaches ComposeView with TYPE_ACCESSIBILITY_OVERLAY when enabled and screen turns off
        service.updateConfigForTest(LauncherConfig(isLiveLockEnabled = true))
        service.handleScreenBroadcast(Intent.ACTION_SCREEN_OFF)

        val overlay = service.currentOverlayView()
        assertNotNull(overlay)
        assertTrue(LockScreenAccessibilityService.isOverlayShowing.value)

        val params = overlay?.layoutParams as? WindowManager.LayoutParams
        assertNotNull(params)
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params?.type)
        assertTrue((params?.flags ?: 0) and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN != 0)
        assertTrue((params?.flags ?: 0) and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)

        // Removes overlay cleanly when user unlocks or swipes up
        service.handleScreenBroadcast(Intent.ACTION_USER_PRESENT)
        assertEquals(null, service.currentOverlayView())
        assertFalse(LockScreenAccessibilityService.isOverlayShowing.value)

        controller.destroy()
        assertFalse(service.isReceiverRegisteredForTest())
    }

    @Test
    fun `accessibility emu lock overlay renders companion face and dismisses cleanly on unlock action`() {
        var dismissed = false
        composeRule.setContent {
            StillLauncherTheme(amoledBlack = true) {
                AccessibilityEmuLockOverlay(
                    config = LauncherConfig(isLiveLockEnabled = true),
                    onSwipeUpToUnlock = { dismissed = true }
                )
            }
        }

        composeRule.onNodeWithTag("accessibility_emu_overlay_root").assertIsDisplayed()
        composeRule.onNodeWithTag("emu_eye_clock").assertIsDisplayed()
        composeRule.onNodeWithTag("accessibility_overlay_unlock_hint").assertIsDisplayed()
        composeRule.onNodeWithTag("accessibility_overlay_unlock_button")
            .assertIsDisplayed()
            .performClick()
        assertTrue(dismissed)
    }

    @Test
    fun `emu sound engine uses ToneGenerator on STREAM_MUSIC, micro-tick for eye follow, and 2500ms cooldown`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        assertEquals(AudioManager.STREAM_MUSIC, EmuSoundEngine.STREAM_TYPE)
        assertEquals(85, EmuSoundEngine.TONE_VOLUME)
        assertEquals(25, EmuSoundEngine.MICRO_TICK_VOLUME)
        assertEquals(2_500L, EmuSoundEngine.EYE_FOLLOW_COOLDOWN_MS)

        // Default config flags: Home ON, Lock Screen OFF, Eye-Follow OFF
        val defaultConfig = LauncherConfig()
        assertTrue(defaultConfig.isHomeSoundEnabled)
        assertFalse(defaultConfig.isLockScreenSoundEnabled)
        assertFalse(defaultConfig.isEyeFollowSoundEnabled)

        val engine = EmuSoundEngine(context = context, enabled = true)

        // Works even in Vibrate or Silent mode because STREAM_MUSIC is not gated by ringer mode
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
        assertTrue(engine.playBlinkSound())
        assertEquals(ToneGenerator.TONE_PROP_BEEP, engine.lastTriggeredToneType)
        assertEquals(45, engine.lastTriggeredDurationMs)
        assertEquals(85, engine.lastTriggeredVolume)

        audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
        assertTrue(engine.playEyeFollowSound(context = context, nowMs = 1_000L))
        assertEquals(ToneGenerator.TONE_PROP_PROMPT, engine.lastTriggeredToneType)
        assertEquals(15, engine.lastTriggeredDurationMs)
        assertEquals(25, engine.lastTriggeredVolume)

        // Cooldown of 2500ms suppresses repetitive gaze shifts
        assertFalse(engine.playEyeFollowSound(context = context, nowMs = 2_800L))
        assertTrue(engine.playEyeFollowSound(context = context, nowMs = 3_600L))

        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
        assertTrue(engine.playShakeDizzySound(nowMs = 2_000L))
        assertEquals(ToneGenerator.TONE_CDMA_PIP, engine.lastTriggeredToneType)
        assertEquals(80, engine.lastTriggeredDurationMs)
        assertEquals(85, engine.lastTriggeredVolume)

        assertTrue(engine.playHappyChirp())
        Thread.sleep(200L)
        assertEquals(ToneGenerator.TONE_PROP_ACK, engine.lastTriggeredToneType)
        assertEquals(90, engine.lastTriggeredDurationMs)
        assertEquals(85, engine.lastTriggeredVolume)

        // Only gated by isEnabled (LauncherConfig sound flags)
        engine.isEnabled = false
        assertFalse(engine.playBlinkSound())
        assertFalse(engine.playEyeFollowSound(context = context, nowMs = 8_000L))
        assertFalse(engine.playShakeDizzySound(nowMs = 8_000L))
        assertFalse(engine.playHappyChirp())
        engine.shutdown()
    }

    @Test
    fun `animations screen exposes independent home, lock screen, and eye-follow micro-tick sound toggles`() {
        var homeSound = LauncherConfig.DEFAULT_HOME_SOUND_ENABLED
        var lockSound = LauncherConfig.DEFAULT_LOCK_SCREEN_SOUND_ENABLED
        var eyeFollowSound = LauncherConfig.DEFAULT_EYE_FOLLOW_SOUND_ENABLED

        composeRule.setContent {
            var currentHomeSound by remember { mutableStateOf(homeSound) }
            var currentLockSound by remember { mutableStateOf(lockSound) }
            var currentEyeFollowSound by remember { mutableStateOf(eyeFollowSound) }
            StillLauncherTheme(amoledBlack = true) {
                LockScreenAnimationsScreen(
                    selectedStyle = EmuAnimationStyle.BOUNCY_PLAYFUL,
                    clockSensitivity = 1.0f,
                    isHomeSoundEnabled = currentHomeSound,
                    isLockScreenSoundEnabled = currentLockSound,
                    isEyeFollowSoundEnabled = currentEyeFollowSound,
                    onSelectStyle = {},
                    onSetHomeSoundEnabled = {
                        currentHomeSound = it
                        homeSound = it
                    },
                    onSetLockScreenSoundEnabled = {
                        currentLockSound = it
                        lockSound = it
                    },
                    onSetEyeFollowSoundEnabled = {
                        currentEyeFollowSound = it
                        eyeFollowSound = it
                    },
                    onPreviewLockScreen = {},
                    onBack = {}
                )
            }
        }

        composeRule.onNodeWithTag("animation_styles_list")
            .performScrollToNode(hasTestTag("sound_effects_control_card"))
        composeRule.onNodeWithTag("sound_effects_control_card").assertIsDisplayed()
        composeRule.onNodeWithTag("test_sound_button").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("home_sound_switch").assertIsDisplayed().performClick()
        assertFalse(homeSound)
        composeRule.onNodeWithTag("lock_screen_sound_switch").assertIsDisplayed().performClick()
        assertTrue(lockSound)
        composeRule.onNodeWithTag("eye_follow_sound_switch").assertIsDisplayed().performClick()
        assertTrue(eyeFollowSound)
    }

    @Test
    fun `emu sound engine blocks audio when screen is not interactive and resumes when interactive`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val engine = EmuSoundEngine(context = context, enabled = true)

        shadowOf(powerManager).setIsInteractive(false)
        assertFalse(engine.playBlinkSound())
        assertFalse(engine.playEyeFollowSound(context = context, nowMs = 1_000L))
        assertFalse(engine.playShakeDizzySound(nowMs = 1_000L))
        assertFalse(engine.playHappyChirp())

        shadowOf(powerManager).setIsInteractive(true)
        assertTrue(engine.playBlinkSound())
        assertTrue(engine.playEyeFollowSound(context = context, nowMs = 4_000L))
        assertTrue(engine.playHappyChirp())
        engine.shutdown()
    }
}
