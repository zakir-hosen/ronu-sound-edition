package com.example

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.EmuAnimationStyle
import com.example.model.EmuEyeColorPresets
import com.example.model.LauncherAlignment
import com.example.model.LauncherConfig
import com.example.model.LauncherConfigManager
import com.example.sensor.rememberBatteryStatus
import com.example.sensor.rememberDeviceTilt
import com.example.sensor.rememberShakeTracker
import com.example.service.LockScreenService
import com.example.ui.components.EmuEyeClock
import com.example.ui.components.EmuFacialExpression
import com.example.ui.theme.PureAmoledBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.StillLauncherTheme
import com.example.ui.theme.SubtleLightGray

/**
 * Dedicated distraction-free Emu Lock Screen Mode rendered over the system keyguard
 * on a #000000 AMOLED canvas, preserving PIN/Pattern/Biometric security and unlocking
 * directly to Home without showing a double lock screen.
 */
class CustomLockScreenActivity : FragmentActivity() {

    private var biometricPrompt: BiometricPrompt? = null
    private var hasAutoPromptedOnResume: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        LockScreenService.cancelFullScreenTriggerNotification(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    dismissKeyguardAndFinish()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    if (errorCode == BiometricPrompt.ERROR_NO_BIOMETRICS ||
                        errorCode == BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                        errorCode == BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL
                    ) {
                        dismissKeyguardAndFinish()
                    }
                }
            }
        )

        val configManager = LauncherConfigManager(applicationContext)

        setContent {
            val config by configManager.configFlow.collectAsStateWithLifecycle(
                initialValue = LauncherConfig()
            )

            StillLauncherTheme(amoledBlack = true) {
                EmuLockScreenContent(
                    animationStyle = config.emuAnimationStyle,
                    eyeColor = EmuEyeColorPresets.parseHexColor(config.eyeColorHex),
                    showShadow = config.isClockShadowEnabled,
                    clockSensitivity = config.clockSensitivity,
                    selectedEmotion = EmuFacialExpression.fromName(config.selectedEmotionName),
                    isShakeReactionEnabled = config.isShakeReactionEnabled,
                    shakeRecoveryDurationSec = config.shakeRecoveryDurationSec,
                    isSoundEnabled = config.isLockScreenSoundEnabled,
                    onUnlockRequested = { dismissKeyguardAndFinish() },
                    onBiometricRequested = { triggerBiometricOrKeyguardUnlock() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        LockScreenService.cancelFullScreenTriggerNotification(this)
        hasAutoPromptedOnResume = false
    }

    override fun onResume() {
        super.onResume()
        LockScreenService.cancelFullScreenTriggerNotification(this)
        val powerManager = getSystemService(PowerManager::class.java)
        val isScreenInteractive = powerManager?.isInteractive != false
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (isScreenInteractive && !hasAutoPromptedOnResume && keyguardManager?.isKeyguardLocked == true) {
            hasAutoPromptedOnResume = true
            launchBiometricPromptIfAvailable(fallbackToKeyguardDismiss = false)
        }
    }

    override fun onStop() {
        super.onStop()
        hasAutoPromptedOnResume = false
    }

    private fun triggerBiometricOrKeyguardUnlock() {
        val keyguardManager = getSystemService(KeyguardManager::class.java)
        if (keyguardManager?.isKeyguardLocked == true) {
            launchBiometricPromptIfAvailable(fallbackToKeyguardDismiss = true)
        } else {
            finish()
        }
    }

    private fun launchBiometricPromptIfAvailable(fallbackToKeyguardDismiss: Boolean) {
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val canAuthenticate = BiometricManager.from(this).canAuthenticate(authenticators)

        if (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS) {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.unlock_prompt_title))
                .setSubtitle(getString(R.string.unlock_prompt_subtitle))
                .setConfirmationRequired(false)
                .setAllowedAuthenticators(authenticators)
                .build()

            runCatching {
                biometricPrompt?.authenticate(promptInfo)
            }.onFailure {
                if (fallbackToKeyguardDismiss) {
                    dismissKeyguardAndFinish()
                }
            }
        } else if (fallbackToKeyguardDismiss) {
            dismissKeyguardAndFinish()
        }
    }

    private fun dismissKeyguardAndFinish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(KeyguardManager::class.java)
            if (keyguardManager != null && keyguardManager.isKeyguardLocked) {
                keyguardManager.requestDismissKeyguard(
                    this,
                    object : KeyguardManager.KeyguardDismissCallback() {
                        override fun onDismissSucceeded() {
                            super.onDismissSucceeded()
                            finish()
                        }
                    }
                )
                return
            }
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        }
        finish()
    }
}

@Composable
internal fun EmuLockScreenContent(
    animationStyle: EmuAnimationStyle,
    clockSensitivity: Float,
    onUnlockRequested: () -> Unit,
    modifier: Modifier = Modifier,
    eyeColor: Color = Color.White,
    showShadow: Boolean = true,
    selectedEmotion: EmuFacialExpression = EmuFacialExpression.ALERT,
    isShakeReactionEnabled: Boolean = LauncherConfig.DEFAULT_SHAKE_REACTION_ENABLED,
    shakeRecoveryDurationSec: Float = LauncherConfig.DEFAULT_SHAKE_RECOVERY_DURATION_SEC,
    isSoundEnabled: Boolean = LauncherConfig.DEFAULT_LOCK_SCREEN_SOUND_ENABLED,
    onBiometricRequested: () -> Unit = onUnlockRequested
) {
    val haptic = LocalHapticFeedback.current
    val effectiveSensitivity = clockSensitivity.coerceAtLeast(0.8f)
    val tilt by rememberDeviceTilt(enabled = clockSensitivity > 0f)
    val batteryStatus by rememberBatteryStatus()
    val shakeState by rememberShakeTracker(enabled = isShakeReactionEnabled)
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    // Prevent bypassing the Emu lock screen via system back gesture without authenticating
    BackHandler(enabled = true) {
        // Intentionally disabled while locked
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureAmoledBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                        if (dragAccumulator < -110f) {
                            dragAccumulator = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onUnlockRequested()
                        }
                    }
                )
            }
            .testTag("emu_lock_screen_root")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            EmuEyeClock(
                tiltPitch = tilt.first * effectiveSensitivity,
                tiltRoll = tilt.second * effectiveSensitivity,
                batteryStatus = batteryStatus,
                alignment = LauncherAlignment.CENTER,
                animationStyle = animationStyle,
                eyeColor = eyeColor,
                showShadow = showShadow,
                selectedEmotion = selectedEmotion,
                isActivelyShaking = shakeState.isActivelyShaking,
                shakeIntensity = shakeState.shakeIntensity,
                isShakeReactionEnabled = isShakeReactionEnabled,
                shakeRecoveryDurationSec = shakeRecoveryDurationSec,
                isSoundEnabled = isSoundEnabled
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .testTag("swipe_up_unlock_hint"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onBiometricRequested()
                },
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("unlock_fingerprint_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = stringResource(R.string.unlock_with_fingerprint_cd),
                    tint = PureWhite.copy(alpha = 0.8f),
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    tint = SubtleLightGray.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.swipe_up_to_unlock),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        letterSpacing = 0.4.sp
                    ),
                    color = SubtleLightGray.copy(alpha = 0.72f)
                )
            }
        }
    }
}
