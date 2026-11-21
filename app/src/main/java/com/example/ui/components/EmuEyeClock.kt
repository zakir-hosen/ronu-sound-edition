package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.example.R
import com.example.audio.rememberEmuSoundEngine
import com.example.model.EmuAnimationStyle
import com.example.model.LauncherAlignment
import com.example.model.LauncherConfig
import com.example.sensor.BatteryExpression
import com.example.sensor.BatteryStatus
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.PureWhite
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

private val EyeEmerald = Color(0xFF34D399)

private enum class ShakeRecoveryPhase {
    IDLE,
    SHAKING,
    DIZZY,
    RECOVERY_BLINK,
    HAPPY
}

/**
 * Emu 3D Eye-Clock powered by the Parametric Canvas Facial Expression Engine.
 *
 * Displays `HH` (Hour) inside the Left Eye and `MM` (Minute) inside the Right Eye,
 * with `AM/PM`, Date, and Battery % cleanly positioned underneath inside a unified
 * 3D gyroscope container (`TransformOrigin.Center`).
 */
@Composable
fun EmuEyeClock(
    tiltPitch: Float,
    tiltRoll: Float,
    batteryStatus: BatteryStatus,
    alignment: LauncherAlignment = LauncherAlignment.CENTER,
    animationStyle: EmuAnimationStyle = EmuAnimationStyle.BOUNCY_PLAYFUL,
    eyeColor: Color = PureWhite,
    showShadow: Boolean = true,
    selectedEmotion: EmuFacialExpression = EmuFacialExpression.ALERT,
    previewExpressionOverride: EmuFacialExpression? = null,
    isActivelyShaking: Boolean = false,
    shakeIntensity: Float = 0f,
    isShakeReactionEnabled: Boolean = LauncherConfig.DEFAULT_SHAKE_REACTION_ENABLED,
    shakeRecoveryDurationSec: Float = LauncherConfig.DEFAULT_SHAKE_RECOVERY_DURATION_SEC,
    isSoundEnabled: Boolean = LauncherConfig.DEFAULT_HOME_SOUND_ENABLED,
    testBlinkTrigger: Int = 0,
    shakeTrigger: Int = 0,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val soundEngine = rememberEmuSoundEngine(enabled = isSoundEnabled)

    var currentTimeMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTimeMillis = System.currentTimeMillis()
            val millisUntilNextSecond = 1000L - (currentTimeMillis % 1000L)
            delay(millisUntilNextSecond.coerceAtLeast(100L))
        }
    }

    val currentDate = remember(currentTimeMillis / 60_000L) { Date(currentTimeMillis) }
    val hourText = remember(currentDate) {
        SimpleDateFormat("hh", Locale.getDefault()).format(currentDate)
    }
    val minuteText = remember(currentDate) {
        SimpleDateFormat("mm", Locale.getDefault()).format(currentDate)
    }
    val amPmText = remember(currentDate) {
        SimpleDateFormat("a", Locale.getDefault()).format(currentDate).uppercase(Locale.getDefault())
    }
    val dateText = remember(currentDate) {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(currentDate)
    }

    // Eye squash & stretch blink Animatables
    val leftScaleY = remember { Animatable(1f) }
    val leftScaleX = remember { Animatable(1f) }
    val rightScaleY = remember { Animatable(1f) }
    val rightScaleX = remember { Animatable(1f) }
    val bounceOffsetY = remember { Animatable(0f) }
    val glitchJitterX = remember { Animatable(0f) }
    val shakeJitterX = remember { Animatable(0f) }
    val shakeJitterY = remember { Animatable(0f) }
    val spinRotationZ = remember { Animatable(0f) }
    val spinWobbleY = remember { Animatable(0f) }
    val dizzySwirlPhase = remember { Animatable(0f) }

    var glitchIntensity by remember { mutableFloatStateOf(0f) }
    var tapHappyBurst by remember { mutableStateOf(false) }
    var shakePhase by remember { mutableStateOf(ShakeRecoveryPhase.IDLE) }
    var isSimulatedShaking by remember { mutableStateOf(false) }
    var wasActivelyShaking by remember { mutableStateOf(false) }
    var activeWinkLeft by remember { mutableStateOf<Boolean?>(null) }

    // Curious Explorer autonomous random 3D focal points
    var explorerTargetX by remember { mutableFloatStateOf(0f) }
    var explorerTargetY by remember { mutableFloatStateOf(0f) }

    // Sentinel: idle curious observer states
    var sentinelTargetGazeX by remember { mutableFloatStateOf(0f) }
    var sentinelTargetGazeY by remember { mutableFloatStateOf(0f) }
    var sentinelTargetHeadPitch by remember { mutableFloatStateOf(0f) }
    var sentinelTargetHeadRoll by remember { mutableFloatStateOf(0f) }
    var lastMotionTimestampMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastObservedPitch by remember { mutableFloatStateOf(tiltPitch) }
    var lastObservedRoll by remember { mutableFloatStateOf(tiltRoll) }
    var isSentinelScanning by remember { mutableStateOf(false) }

    LaunchedEffect(tiltPitch, tiltRoll, animationStyle) {
        val delta = abs(tiltPitch - lastObservedPitch) + abs(tiltRoll - lastObservedRoll)
        lastObservedPitch = tiltPitch
        lastObservedRoll = tiltRoll
        if (delta >= 0.02f) {
            lastMotionTimestampMs = System.currentTimeMillis()
            if (isSentinelScanning) {
                isSentinelScanning = false
                sentinelTargetGazeX = 0f
                sentinelTargetGazeY = 0f
                sentinelTargetHeadPitch = 0f
                sentinelTargetHeadRoll = 0f
            }
        }
    }

    LaunchedEffect(animationStyle) {
        if (animationStyle == EmuAnimationStyle.CURIOUS_EXPLORER) {
            while (true) {
                explorerTargetX = Random.nextFloat() * 2f - 1f
                explorerTargetY = Random.nextFloat() * 2f - 1f
                delay(2200L)
            }
        } else {
            explorerTargetX = 0f
            explorerTargetY = 0f
        }
    }

    val smoothExplorerX by animateFloatAsState(
        targetValue = explorerTargetX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "explorer_gaze_x"
    )
    val smoothExplorerY by animateFloatAsState(
        targetValue = explorerTargetY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "explorer_gaze_y"
    )

    val smoothSentinelGazeX by animateFloatAsState(
        targetValue = sentinelTargetGazeX,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "sentinel_gaze_x"
    )
    val smoothSentinelGazeY by animateFloatAsState(
        targetValue = sentinelTargetGazeY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "sentinel_gaze_y"
    )
    val smoothSentinelPitch by animateFloatAsState(
        targetValue = sentinelTargetHeadPitch,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "sentinel_head_pitch"
    )
    val smoothSentinelRoll by animateFloatAsState(
        targetValue = sentinelTargetHeadRoll,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
        label = "sentinel_head_roll"
    )

    val isBatteryLowSleepy = batteryStatus.expression == BatteryExpression.LOW_SLEEPY
    val isChargingHappy = batteryStatus.expression == BatteryExpression.CHARGING_HAPPY
    val isStyleSleepy = animationStyle == EmuAnimationStyle.SLEEPY_CHILL
    val isZen = animationStyle == EmuAnimationStyle.SMOOTH_ZEN
    val isCyber = animationStyle == EmuAnimationStyle.HYPER_CYBER_GLITCH
    val isCurious = animationStyle == EmuAnimationStyle.CURIOUS_EXPLORER
    val isSentinel = animationStyle == EmuAnimationStyle.SENTINEL

    val effectivePhysics = animationStyle.physics

    val openSpring = remember(animationStyle, isBatteryLowSleepy) {
        if (isBatteryLowSleepy) {
            spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)
        } else {
            spring<Float>(
                dampingRatio = effectivePhysics.openDampingRatio,
                stiffness = effectivePhysics.openStiffness
            )
        }
    }

    suspend fun triggerCyberGlitchBurst() {
        if (!isCyber) return
        glitchIntensity = 1f
        val shifts = listOf(-6f, 7f, -4f, 3f, 0f)
        for (shift in shifts) {
            glitchJitterX.snapTo(shift)
            delay(28L)
        }
        glitchIntensity = 0f
    }

    suspend fun blinkEye(
        scaleY: Animatable<Float, *>,
        scaleX: Animatable<Float, *>,
        onClosedPeak: () -> Unit = {}
    ) {
        val closeDuration = if (isBatteryLowSleepy) 150 else effectivePhysics.closeDurationMs
        coroutineScope {
            if (isZen) {
                launch { scaleY.animateTo(0.08f, tween(closeDuration, easing = FastOutSlowInEasing)) }
                launch { scaleX.animateTo(1.04f, tween(closeDuration, easing = FastOutSlowInEasing)) }
            } else {
                launch { scaleY.animateTo(0.05f, tween(closeDuration)) }
                launch { scaleX.animateTo(1.14f, tween(closeDuration)) }
            }
        }
        onClosedPeak()
        coroutineScope {
            if (isZen) {
                launch { scaleY.animateTo(1f, tween(340, easing = FastOutSlowInEasing)) }
                launch { scaleX.animateTo(1f, tween(340, easing = FastOutSlowInEasing)) }
            } else {
                launch { scaleY.animateTo(1f, openSpring) }
                launch { scaleX.animateTo(1f, openSpring) }
            }
        }
    }

    suspend fun blinkBothEyes(onClosedPeak: () -> Unit = {}) {
        soundEngine.playBlinkSound()
        coroutineScope {
            launch { blinkEye(leftScaleY, leftScaleX, onClosedPeak) }
            launch { blinkEye(rightScaleY, rightScaleX) }
            launch { triggerCyberGlitchBurst() }
        }
    }

    fun triggerSingleEyeWink(isLeft: Boolean) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        soundEngine.playBlinkSound()
        val expressionsDisabled = (previewExpressionOverride ?: selectedEmotion) == EmuFacialExpression.NONE
        scope.launch {
            if (!expressionsDisabled) {
                activeWinkLeft = isLeft
            }
            launch {
                bounceOffsetY.animateTo(-6f, tween(90))
                bounceOffsetY.animateTo(0f, openSpring)
            }
            if (!expressionsDisabled) {
                delay(480L)
                if (activeWinkLeft == isLeft) {
                    activeWinkLeft = null
                }
            }
        }
    }

    suspend fun performStyleSampleBlink() {
        if (isCurious || isSentinel) {
            blinkBothEyes()
            delay(80L)
            blinkBothEyes()
        } else {
            blinkBothEyes()
        }
    }

    // Sentinel idle curious observer loop
    LaunchedEffect(animationStyle) {
        if (!isSentinel) {
            isSentinelScanning = false
            sentinelTargetGazeX = 0f
            sentinelTargetGazeY = 0f
            sentinelTargetHeadPitch = 0f
            sentinelTargetHeadRoll = 0f
            return@LaunchedEffect
        }

        val scanPoints = listOf(
            -0.85f to 0.05f,
            0.78f to -0.65f,
            -0.72f to 0.62f,
            0.0f to 0.0f,
            0.82f to 0.2f,
            -0.45f to -0.6f
        )
        var scanStep = 0
        while (true) {
            val stableElapsedMs = System.currentTimeMillis() - lastMotionTimestampMs
            if (stableElapsedMs >= 1_200L && shakePhase == ShakeRecoveryPhase.IDLE) {
                isSentinelScanning = true
                val target = scanPoints[scanStep % scanPoints.size]
                sentinelTargetGazeX = target.first
                sentinelTargetGazeY = target.second
                sentinelTargetHeadRoll = target.first * 0.28f
                sentinelTargetHeadPitch = target.second * 0.22f
                scanStep++
                if (scanStep % 2 == 0) {
                    launch {
                        blinkBothEyes()
                        delay(75L)
                        blinkBothEyes()
                    }
                }
                delay(Random.nextLong(1_200L, 2_201L))
            } else {
                isSentinelScanning = false
                sentinelTargetGazeX = 0f
                sentinelTargetGazeY = 0f
                sentinelTargetHeadPitch = 0f
                sentinelTargetHeadRoll = 0f
                delay(180L)
            }
        }
    }

    // "Test Shake Spin" button simulates a 1-second active shake before running the recovery sequence
    LaunchedEffect(shakeTrigger, isShakeReactionEnabled) {
        if (shakeTrigger > 0 && isShakeReactionEnabled) {
            lastMotionTimestampMs = System.currentTimeMillis()
            isSimulatedShaking = true
            delay(1_000L)
            isSimulatedShaking = false
        } else {
            isSimulatedShaking = false
        }
    }

    val effectiveShaking = isShakeReactionEnabled && (isActivelyShaking || isSimulatedShaking)
    val currentShakeIntensity by rememberUpdatedState(
        if (isActivelyShaking) {
            shakeIntensity.coerceIn(0.25f, 1.0f)
        } else if (isSimulatedShaking) {
            0.85f
        } else {
            0f
        }
    )
    val currentRecoveryDurationSec by rememberUpdatedState(
        shakeRecoveryDurationSec.coerceIn(
            LauncherConfig.MIN_SHAKE_RECOVERY_DURATION_SEC,
            LauncherConfig.MAX_SHAKE_RECOVERY_DURATION_SEC
        )
    )

    // Multi-Stage Shake-to-Happy Recovery State Machine:
    // Stage 1 (Active Shake): SURPRISED + real-time rubbery jitter
    // Stage 2 (55% duration): DIZZY + opposing swirls + decaying pendulum wobble
    // Stage 3 (15% duration): Crisp double-blink + quick head-shake nudge
    // Stage 4 (30% duration): HAPPY (^ ^) upward bounce -> smooth return to selectedEmotion
    LaunchedEffect(effectiveShaking, isShakeReactionEnabled) {
        if (!isShakeReactionEnabled) {
            wasActivelyShaking = false
            shakePhase = ShakeRecoveryPhase.IDLE
            shakeJitterX.snapTo(0f)
            shakeJitterY.snapTo(0f)
            spinRotationZ.snapTo(0f)
            spinWobbleY.snapTo(0f)
            dizzySwirlPhase.snapTo(0f)
            return@LaunchedEffect
        }

        if (effectiveShaking) {
            wasActivelyShaking = true
            lastMotionTimestampMs = System.currentTimeMillis()
            shakePhase = ShakeRecoveryPhase.SHAKING
            var tick = 0
            while (isActive) {
                soundEngine.playShakeDizzySound()
                val intensity = currentShakeIntensity.coerceAtLeast(0.25f)
                val signX = if (tick % 2 == 0) 1f else -1f
                val signY = if ((tick / 2) % 2 == 0) 1f else -1f
                val targetX = signX * (8f + Random.nextFloat() * 7f) * intensity
                val targetY = signY * (5f + Random.nextFloat() * 5f) * intensity
                val targetRot = signX * (6f + Random.nextFloat() * 6f) * intensity
                coroutineScope {
                    launch { shakeJitterX.animateTo(targetX, tween(38)) }
                    launch { shakeJitterY.animateTo(targetY, tween(38)) }
                    launch { spinRotationZ.animateTo(targetRot, tween(38)) }
                    launch { bounceOffsetY.animateTo(-4f * intensity, tween(42)) }
                }
                tick++
            }
        } else if (wasActivelyShaking) {
            wasActivelyShaking = false
            val totalDurationMs = (currentRecoveryDurationSec * 1000f).toLong()
            val dizzyMs = (totalDurationMs * 0.55f).toLong()
            val blinkMs = (totalDurationMs * 0.15f).toLong()
            val happyMs = (totalDurationMs - dizzyMs - blinkMs).coerceAtLeast(1_000L)

            // Stage 2: Dizzy / Dazed phase (55% of duration) with decaying pendulum wobble
            shakePhase = ShakeRecoveryPhase.DIZZY
            dizzySwirlPhase.snapTo(0f)
            coroutineScope {
                launch {
                    shakeJitterX.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 280f))
                }
                launch {
                    shakeJitterY.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 280f))
                }
                launch {
                    bounceOffsetY.animateTo(0f, spring(dampingRatio = 0.65f, stiffness = 280f))
                }
                launch {
                    dizzySwirlPhase.animateTo(
                        targetValue = 4f * PI.toFloat(),
                        animationSpec = tween(durationMillis = dizzyMs.toInt(), easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    val swings = listOf(16f, -12f, 8.5f, -5f, 2.5f, 0f)
                    val stepMs = (dizzyMs / swings.size).toInt().coerceAtLeast(80)
                    for (angle in swings) {
                        spinRotationZ.animateTo(
                            targetValue = angle,
                            animationSpec = tween(durationMillis = stepMs, easing = FastOutSlowInEasing)
                        )
                    }
                }
                launch {
                    val halfMs = (dizzyMs / 4).toInt().coerceAtLeast(100)
                    spinWobbleY.animateTo(12f, tween(halfMs, easing = FastOutSlowInEasing))
                    spinWobbleY.animateTo(-9f, tween(halfMs, easing = FastOutSlowInEasing))
                    spinWobbleY.animateTo(4f, tween(halfMs, easing = FastOutSlowInEasing))
                    spinWobbleY.animateTo(0f, tween(halfMs, easing = FastOutSlowInEasing))
                }
            }

            // Stage 3: Crisp double-blink + quick head-shake nudge (15% of duration)
            shakePhase = ShakeRecoveryPhase.RECOVERY_BLINK
            val startBlinkMs = System.currentTimeMillis()
            coroutineScope {
                launch {
                    blinkBothEyes()
                    delay(65L)
                    blinkBothEyes()
                }
                launch {
                    val nudgeMs = (blinkMs / 4).toInt().coerceAtLeast(45)
                    spinRotationZ.animateTo(5f, tween(nudgeMs))
                    spinRotationZ.animateTo(-5f, tween(nudgeMs))
                    spinRotationZ.animateTo(2.5f, tween(nudgeMs))
                    spinRotationZ.animateTo(0f, tween(nudgeMs))
                }
                launch {
                    val nudgeMs = (blinkMs / 3).toInt().coerceAtLeast(45)
                    shakeJitterX.animateTo(4f, tween(nudgeMs))
                    shakeJitterX.animateTo(-4f, tween(nudgeMs))
                    shakeJitterX.animateTo(0f, tween(nudgeMs))
                }
            }
            val elapsedBlinkMs = System.currentTimeMillis() - startBlinkMs
            if (elapsedBlinkMs < blinkMs) {
                delay(blinkMs - elapsedBlinkMs)
            }

            // Stage 4: Happy Smile (^ ^) with upward bounce -> Return to user's base emotion
            shakePhase = ShakeRecoveryPhase.HAPPY
            soundEngine.playHappyChirp()
            coroutineScope {
                launch {
                    bounceOffsetY.animateTo(-10f, tween(140, easing = FastOutSlowInEasing))
                    bounceOffsetY.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
            }
            delay(happyMs)

            shakePhase = ShakeRecoveryPhase.IDLE
            spinRotationZ.snapTo(0f)
            spinWobbleY.snapTo(0f)
            dizzySwirlPhase.snapTo(0f)
        }
    }

    LaunchedEffect(testBlinkTrigger) {
        if (testBlinkTrigger > 0) {
            tapHappyBurst = true
            performStyleSampleBlink()
            delay(550L)
            tapHappyBurst = false
        }
    }

    LaunchedEffect(animationStyle, batteryStatus.expression) {
        while (true) {
            val intervalMs = if (isBatteryLowSleepy) {
                12_000L
            } else {
                effectivePhysics.idleBlinkIntervalMs
            }
            delay(intervalMs)
            blinkBothEyes()
        }
    }

    val animatedAlignmentBias by animateFloatAsState(
        targetValue = alignment.bias,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "clock_alignment_bias"
    )

    val isPerkedUp = isCurious && (abs(tiltPitch) + abs(tiltRoll) > 0.25f)
    val perkScale by animateFloatAsState(
        targetValue = if (isPerkedUp) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "curious_perk_scale"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "emu_ambient_loops")
    val chargingPulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "charging_pulse_alpha"
    )
    val joyfulHoverY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "charging_joy_hover"
    )
    val idleBreathScale by infiniteTransition.animateFloat(
        initialValue = 0.988f,
        targetValue = 1.018f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_breath_scale"
    )
    val previewDizzyPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "preview_dizzy_phase"
    )

    val lazyPitch by animateFloatAsState(
        targetValue = tiltPitch,
        animationSpec = if (isStyleSleepy) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        },
        label = "style_pitch"
    )
    val lazyRoll by animateFloatAsState(
        targetValue = tiltRoll,
        animationSpec = if (isStyleSleepy) {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessVeryLow)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        },
        label = "style_roll"
    )

    val scaledPitch = (lazyPitch + (if (isSentinel) smoothSentinelPitch else 0f)) * effectivePhysics.tiltScale
    val scaledRoll = (lazyRoll + (if (isSentinel) smoothSentinelRoll else 0f)) * effectivePhysics.tiltScale

    val gazeRoll = scaledRoll * effectivePhysics.gazeScale +
        (if (isCurious) smoothExplorerX * 0.55f else 0f) +
        (if (isSentinel) smoothSentinelGazeX * 0.76f else 0f)
    val gazePitch = scaledPitch * effectivePhysics.gazeScale +
        (if (isCurious) smoothExplorerY * 0.42f else 0f) +
        (if (isSentinel) smoothSentinelGazeY * 0.60f else 0f)

    var hasSeededFollowSfx by remember { mutableStateOf(false) }
    var lastFollowSfxX by remember { mutableFloatStateOf(0f) }
    var lastFollowSfxY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(gazeRoll, gazePitch, isSoundEnabled, shakePhase) {
        if (!isSoundEnabled) {
            hasSeededFollowSfx = false
            return@LaunchedEffect
        }
        if (!hasSeededFollowSfx) {
            lastFollowSfxX = gazeRoll
            lastFollowSfxY = gazePitch
            hasSeededFollowSfx = true
            return@LaunchedEffect
        }
        if (shakePhase != ShakeRecoveryPhase.IDLE) return@LaunchedEffect

        val delta = hypot((gazeRoll - lastFollowSfxX).toDouble(), (gazePitch - lastFollowSfxY).toDouble())
            .toFloat()
        if (delta >= 0.28f && soundEngine.playEyeFollowSound()) {
            lastFollowSfxX = gazeRoll
            lastFollowSfxY = gazePitch
        }
    }

    val pupilTargetXPx = with(density) { (gazeRoll * 16f).dp.toPx() }
    val pupilTargetYPx = with(density) { (gazePitch * 12f).dp.toPx() }

    val activeJumpY = bounceOffsetY.value + if (isChargingHappy) joyfulHoverY else 0f

    val unifiedRotX = scaledPitch * 22f
    val unifiedRotY = scaledRoll * 22f + spinWobbleY.value
    val unifiedRotZ = spinRotationZ.value
    val unifiedTransX = with(density) {
        (scaledRoll * 16f + glitchJitterX.value + shakeJitterX.value).dp.toPx()
    }
    val unifiedTransY = with(density) {
        (scaledPitch * 12f + activeJumpY + shakeJitterY.value).dp.toPx()
    }

    val shadowOffsetX = (-scaledRoll * 14f).dp
    val shadowOffsetY = (8f - scaledPitch * 10f).dp

    val animatedEyeColor by animateColorAsState(
        targetValue = eyeColor,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "emu_eye_color"
    )
    val eyeLuminance = ColorUtils.calculateLuminance(animatedEyeColor.toArgb())
    val ambientGlowAlpha = if (eyeLuminance > 0.90) 0.16f else 0.34f
    val shadowAlpha by animateFloatAsState(
        targetValue = if (showShadow) 1f else 0f,
        animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
        label = "emu_clock_shadow_alpha"
    )

    // Determine target facial expression pair; shake stages take priority when Shake Reaction is ON
    val isSharpTilt = abs(tiltPitch) + abs(tiltRoll) > 0.55f
    val requestedEmotion = previewExpressionOverride ?: selectedEmotion
    val isExpressionsDisabled = requestedEmotion == EmuFacialExpression.NONE
    val isShakeSequenceActive = isShakeReactionEnabled && shakePhase != ShakeRecoveryPhase.IDLE
    val activeExpression = when {
        isShakeReactionEnabled && shakePhase == ShakeRecoveryPhase.SHAKING -> EmuFacialExpression.SURPRISED
        isShakeReactionEnabled && shakePhase == ShakeRecoveryPhase.DIZZY -> EmuFacialExpression.DIZZY
        isShakeReactionEnabled && shakePhase == ShakeRecoveryPhase.RECOVERY_BLINK -> EmuFacialExpression.ALERT
        isShakeReactionEnabled && shakePhase == ShakeRecoveryPhase.HAPPY -> EmuFacialExpression.HAPPY
        isExpressionsDisabled -> EmuFacialExpression.NONE
        previewExpressionOverride != null -> previewExpressionOverride
        activeWinkLeft != null -> EmuFacialExpression.WINK
        isChargingHappy || tapHappyBurst -> EmuFacialExpression.HAPPY
        isBatteryLowSleepy || isStyleSleepy -> EmuFacialExpression.SLEEPY
        isCurious || (isSentinel && isSentinelScanning) || isSharpTilt -> EmuFacialExpression.CURIOUS
        else -> selectedEmotion
    }

    val targetPair = remember(activeExpression, activeWinkLeft) {
        EmuExpressionPresets.forExpression(
            expression = activeExpression,
            winkLeftEye = activeWinkLeft ?: true
        )
    }
    val leftExpression = rememberAnimatedEyeExpression(targetPair.left, "emu_left_expr")
    val rightExpression = rememberAnimatedEyeExpression(targetPair.right, "emu_right_expr")

    val effectiveDizzy = (isShakeReactionEnabled && shakePhase == ShakeRecoveryPhase.DIZZY) ||
        (!isExpressionsDisabled && previewExpressionOverride == EmuFacialExpression.DIZZY)
    val effectiveDizzyPhase =
        if (isShakeReactionEnabled && shakePhase == ShakeRecoveryPhase.DIZZY) {
            dizzySwirlPhase.value
        } else {
            previewDizzyPhase
        }

    // Opposing dizzy pupil offsets so eyes circle inversely while recovering balance
    val dizzyPupilRadiusPx = with(density) { 6.dp.toPx() }
    val leftDizzyOffsetX = if (effectiveDizzy) cos(effectiveDizzyPhase) * dizzyPupilRadiusPx else 0f
    val leftDizzyOffsetY = if (effectiveDizzy) sin(effectiveDizzyPhase) * dizzyPupilRadiusPx else 0f
    val rightDizzyOffsetX = if (effectiveDizzy) -cos(effectiveDizzyPhase) * dizzyPupilRadiusPx else 0f
    val rightDizzyOffsetY = if (effectiveDizzy) -sin(effectiveDizzyPhase) * dizzyPupilRadiusPx else 0f

    val ambientScale = (
        if (isZen || activeExpression == EmuFacialExpression.ALERT || activeExpression == EmuFacialExpression.NONE) {
            idleBreathScale
        } else {
            1f
        }
        ) * perkScale

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("emu_eye_clock")
            .semantics {
                contentDescription =
                    "$hourText $minuteText $amPmText, $dateText, Battery ${batteryStatus.levelPercent} percent"
            },
        contentAlignment = BiasAlignment(horizontalBias = animatedAlignmentBias, verticalBias = 0f)
    ) {
        Column(
            horizontalAlignment = BiasAlignment.Horizontal(animatedAlignmentBias),
            modifier = Modifier
                .graphicsLayer {
                    transformOrigin = TransformOrigin.Center
                    cameraDistance = 12f * density.density
                    rotationX = unifiedRotX
                    rotationY = unifiedRotY
                    rotationZ = unifiedRotZ
                    translationX = unifiedTransX
                    translationY = unifiedTransY
                    scaleX = ambientScale
                    scaleY = ambientScale
                }
                .testTag("emu_clock_3d_unit")
        ) {
            // Eye pair with optional ambient glow silhouette behind
            Box(contentAlignment = Alignment.Center) {
                if (shadowAlpha > 0.01f) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .offset(x = shadowOffsetX, y = shadowOffsetY)
                            .testTag("emu_clock_shadow_layer")
                    ) {
                        EyeGlowSilhouette(
                            eyeColor = animatedEyeColor,
                            glowAlpha = ambientGlowAlpha * shadowAlpha,
                            scaleX = leftScaleX.value * leftExpression.eyeScale,
                            scaleY = leftScaleY.value * leftExpression.eyeScale
                        )
                        EyeGlowSilhouette(
                            eyeColor = animatedEyeColor,
                            glowAlpha = ambientGlowAlpha * shadowAlpha,
                            scaleX = rightScaleX.value * rightExpression.eyeScale,
                            scaleY = rightScaleY.value * rightExpression.eyeScale
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.testTag("emu_eye_pair_pivot")
                ) {
                    ExpressiveEmuEye(
                        numerals = hourText,
                        eyeColor = animatedEyeColor,
                        isLeftEye = true,
                        expression = leftExpression,
                        blinkScaleX = leftScaleX.value,
                        blinkScaleY = leftScaleY.value,
                        pupilTargetOffsetXPx = pupilTargetXPx + leftDizzyOffsetX,
                        pupilTargetOffsetYPx = pupilTargetYPx + leftDizzyOffsetY,
                        isDizzy = effectiveDizzy,
                        dizzyPhase = effectiveDizzyPhase,
                        isCharging = (isShakeSequenceActive || !isExpressionsDisabled) &&
                            activeExpression == EmuFacialExpression.HAPPY,
                        chargingPulseAlpha = chargingPulseAlpha,
                        glitchIntensity = glitchIntensity,
                        contentDescriptionText = stringResource(R.string.emu_left_eye_cd),
                        eyeTestTag = "emu_left_eye",
                        numeralTestTag = "emu_left_eye_numerals",
                        onTapWink = { triggerSingleEyeWink(isLeft = true) },
                        currentEmotion = activeExpression,
                        isBlinking = leftScaleY.value < 0.96f
                    )

                    ExpressiveEmuEye(
                        numerals = minuteText,
                        eyeColor = animatedEyeColor,
                        isLeftEye = false,
                        expression = rightExpression,
                        blinkScaleX = rightScaleX.value,
                        blinkScaleY = rightScaleY.value,
                        pupilTargetOffsetXPx = pupilTargetXPx + rightDizzyOffsetX,
                        pupilTargetOffsetYPx = pupilTargetYPx + rightDizzyOffsetY,
                        isDizzy = effectiveDizzy,
                        dizzyPhase = effectiveDizzyPhase,
                        isCharging = (isShakeSequenceActive || !isExpressionsDisabled) &&
                            activeExpression == EmuFacialExpression.HAPPY,
                        chargingPulseAlpha = chargingPulseAlpha,
                        glitchIntensity = glitchIntensity,
                        contentDescriptionText = stringResource(R.string.emu_right_eye_cd),
                        eyeTestTag = "emu_right_eye",
                        numeralTestTag = "emu_right_eye_numerals",
                        onTapWink = { triggerSingleEyeWink(isLeft = false) },
                        currentEmotion = activeExpression,
                        isBlinking = rightScaleY.value < 0.96f
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Clean AM/PM, Date, and Battery % underneath the eyes
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("emu_clock_date_row")
            ) {
                Text(
                    text = amPmText,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        letterSpacing = 1.6.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                Text(
                    text = dateText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        letterSpacing = 0.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.testTag("emu_battery_indicator")
                ) {
                    if (isChargingHappy) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = "Charging",
                            tint = EyeEmerald,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                    }
                    Text(
                        text = "${batteryStatus.levelPercent}%",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp
                        ),
                        color = when (batteryStatus.expression) {
                            BatteryExpression.CHARGING_HAPPY -> EyeEmerald
                            BatteryExpression.LOW_SLEEPY -> Color(0xFFF59E0B)
                            BatteryExpression.NORMAL -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun EyeGlowSilhouette(
    eyeColor: Color,
    glowAlpha: Float,
    scaleX: Float,
    scaleY: Float
) {
    Box(
        modifier = Modifier
            .size(94.dp)
            .graphicsLayer {
                this.scaleX = scaleX
                this.scaleY = scaleY
            }
            .clip(RoundedCornerShape(28.dp))
            .background(eyeColor.copy(alpha = glowAlpha))
            .blur(16.dp)
    )
}
