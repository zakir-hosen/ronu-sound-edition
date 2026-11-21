package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.atan2
import kotlin.math.sqrt

private const val LOW_PASS_ALPHA = 0.15f
private const val BASELINE_DRIFT_ALPHA = 0.01f
private const val MAX_TILT_DEGREES = 24f
private const val SHAKE_THRESHOLD_G = 1.8f
private const val SHAKE_COOLDOWN_MS = 1_500L
private const val SHAKE_STOP_TIMEOUT_MS = 350L
private const val HIGH_PASS_GRAVITY_ALPHA = 0.8f

/**
 * Real-time shake state emitted by [rememberShakeTracker].
 * [isActivelyShaking] stays `true` while linear acceleration exceeds 1.8g and drops back
 * to `false` 350ms after shaking stops. [shakeIntensity] is normalized in `[0f, 1f]`.
 */
@androidx.compose.runtime.Immutable
data class ShakeTrackingState(
    val isActivelyShaking: Boolean = false,
    val shakeIntensity: Float = 0f
)

/**
 * Lifecycle-aware Composable helper returning smoothed (pitch, roll) normalized in [-1f, 1f],
 * auto-calibrated around the user's comfortable holding angle.
 */
@Composable
fun rememberDeviceTilt(enabled: Boolean = true): State<Pair<Float, Float>> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var rawPitch by remember { mutableFloatStateOf(0f) }
    var rawRoll by remember { mutableFloatStateOf(0f) }

    DisposableEffect(lifecycleOwner, enabled) {
        if (!enabled) {
            rawPitch = 0f
            rawRoll = 0f
            return@DisposableEffect onDispose {}
        }

        val sensorManager =
            context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val activeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensorManager == null || activeSensor == null) {
            return@DisposableEffect onDispose {}
        }

        val rotationMatrix = FloatArray(9)
        val orientationAngles = FloatArray(3)
        var hasBaseline = false
        var baselinePitchDeg = 0f
        var baselineRollDeg = 0f
        var filteredPitch = 0f
        var filteredRoll = 0f

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val pitchDeg: Float
                val rollDeg: Float

                when (event.sensor.type) {
                    Sensor.TYPE_GAME_ROTATION_VECTOR,
                    Sensor.TYPE_ROTATION_VECTOR -> {
                        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                        SensorManager.getOrientation(rotationMatrix, orientationAngles)
                        pitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
                        rollDeg = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()
                    }
                    Sensor.TYPE_ACCELEROMETER -> {
                        val ax = event.values[0]
                        val ay = event.values[1]
                        val az = event.values[2]
                        val normYZ = sqrt((ay * ay + az * az).toDouble()).toFloat().coerceAtLeast(0.01f)
                        val normXZ = sqrt((ax * ax + az * az).toDouble()).toFloat().coerceAtLeast(0.01f)
                        pitchDeg = Math.toDegrees(atan2(ay.toDouble(), normXZ.toDouble())).toFloat()
                        rollDeg = Math.toDegrees(atan2(-ax.toDouble(), normYZ.toDouble())).toFloat()
                    }
                    else -> return
                }

                if (!hasBaseline) {
                    baselinePitchDeg = pitchDeg
                    baselineRollDeg = rollDeg
                    hasBaseline = true
                } else {
                    baselinePitchDeg +=
                        normalizeDegreeDelta(pitchDeg - baselinePitchDeg) * BASELINE_DRIFT_ALPHA
                    baselineRollDeg +=
                        normalizeDegreeDelta(rollDeg - baselineRollDeg) * BASELINE_DRIFT_ALPHA
                }

                val deltaPitchDeg = normalizeDegreeDelta(pitchDeg - baselinePitchDeg)
                val deltaRollDeg = normalizeDegreeDelta(rollDeg - baselineRollDeg)

                val targetPitch = (deltaPitchDeg / MAX_TILT_DEGREES).coerceIn(-1f, 1f)
                val targetRoll = (deltaRollDeg / MAX_TILT_DEGREES).coerceIn(-1f, 1f)

                filteredPitch += LOW_PASS_ALPHA * (targetPitch - filteredPitch)
                filteredRoll += LOW_PASS_ALPHA * (targetRoll - filteredRoll)

                rawPitch = filteredPitch
                rawRoll = filteredRoll
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        var isRegistered = false

        fun register() {
            if (!isRegistered) {
                hasBaseline = false
                isRegistered = sensorManager.registerListener(
                    listener,
                    activeSensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }
        }

        fun unregister() {
            if (isRegistered) {
                sensorManager.unregisterListener(listener)
                isRegistered = false
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> {
                    unregister()
                    rawPitch = 0f
                    rawRoll = 0f
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            register()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            unregister()
        }
    }

    val animatedPitch by animateFloatAsState(
        targetValue = if (enabled) rawPitch else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "tilt_pitch"
    )
    val animatedRoll by animateFloatAsState(
        targetValue = if (enabled) rawRoll else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "tilt_roll"
    )

    return remember {
        derivedStateOf { animatedPitch to animatedRoll }
    }
}

/**
 * Continuous lifecycle-aware shake tracker exposing [ShakeTrackingState] (`isActivelyShaking`
 * and normalized `shakeIntensity`). Uses `Sensor.TYPE_LINEAR_ACCELERATION` when available,
 * falling back to a high-pass filtered `Sensor.TYPE_ACCELEROMETER`, and automatically clears
 * `isActivelyShaking` 350ms after shaking stops.
 */
@Composable
fun rememberShakeTracker(
    enabled: Boolean = true
): State<ShakeTrackingState> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val stateFlow = remember {
        kotlinx.coroutines.flow.MutableStateFlow(ShakeTrackingState())
    }
    var currentIntensity by remember { mutableFloatStateOf(0f) }
    var activelyShaking by remember { androidx.compose.runtime.mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, enabled) {
        if (!enabled) {
            activelyShaking = false
            currentIntensity = 0f
            stateFlow.value = ShakeTrackingState()
            return@DisposableEffect onDispose {}
        }

        val sensorManager =
            context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val shakeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensorManager == null || shakeSensor == null) {
            return@DisposableEffect onDispose {}
        }

        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val gravity = FloatArray(3)
        var hasGravitySeed = false
        var smoothedIntensity = 0f

        val stopRunnable = Runnable {
            activelyShaking = false
            currentIntensity = 0f
            smoothedIntensity = 0f
            stateFlow.value = ShakeTrackingState(isActivelyShaking = false, shakeIntensity = 0f)
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val ax = event.values.getOrNull(0) ?: return
                val ay = event.values.getOrNull(1) ?: return
                val az = event.values.getOrNull(2) ?: return
                val isLinear = event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION

                val linearG = if (isLinear) {
                    computeMagnitudeG(ax, ay, az)
                } else {
                    if (!hasGravitySeed) {
                        gravity[0] = ax
                        gravity[1] = ay
                        gravity[2] = az
                        hasGravitySeed = true
                        0f
                    } else {
                        gravity[0] = HIGH_PASS_GRAVITY_ALPHA * gravity[0] +
                            (1f - HIGH_PASS_GRAVITY_ALPHA) * ax
                        gravity[1] = HIGH_PASS_GRAVITY_ALPHA * gravity[1] +
                            (1f - HIGH_PASS_GRAVITY_ALPHA) * ay
                        gravity[2] = HIGH_PASS_GRAVITY_ALPHA * gravity[2] +
                            (1f - HIGH_PASS_GRAVITY_ALPHA) * az
                        computeMagnitudeG(ax - gravity[0], ay - gravity[1], az - gravity[2])
                    }
                }

                if (linearG > SHAKE_THRESHOLD_G) {
                    val rawIntensity = normalizeShakeIntensity(linearG)
                    smoothedIntensity = smoothedIntensity * 0.35f + rawIntensity * 0.65f
                    activelyShaking = true
                    currentIntensity = smoothedIntensity
                    stateFlow.value = ShakeTrackingState(
                        isActivelyShaking = true,
                        shakeIntensity = smoothedIntensity
                    )
                    mainHandler.removeCallbacks(stopRunnable)
                    mainHandler.postDelayed(stopRunnable, SHAKE_STOP_TIMEOUT_MS)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        var isRegistered = false

        fun register() {
            if (!isRegistered) {
                hasGravitySeed = false
                isRegistered = sensorManager.registerListener(
                    listener,
                    shakeSensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }
        }

        fun unregister() {
            mainHandler.removeCallbacks(stopRunnable)
            if (isRegistered) {
                sensorManager.unregisterListener(listener)
                isRegistered = false
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> {
                    unregister()
                    activelyShaking = false
                    currentIntensity = 0f
                    smoothedIntensity = 0f
                    stateFlow.value = ShakeTrackingState()
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            register()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            unregister()
        }
    }

    return remember {
        derivedStateOf {
            ShakeTrackingState(
                isActivelyShaking = if (enabled) activelyShaking else false,
                shakeIntensity = if (enabled) currentIntensity else 0f
            )
        }
    }
}

/**
 * Legacy one-shot shake detector retained for compatibility.
 */
@Composable
fun rememberShakeDetector(
    enabled: Boolean = true,
    onPhoneShaken: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnPhoneShaken by rememberUpdatedState(onPhoneShaken)

    DisposableEffect(lifecycleOwner, enabled) {
        if (!enabled) {
            return@DisposableEffect onDispose {}
        }

        val sensorManager =
            context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val shakeSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensorManager == null || shakeSensor == null) {
            return@DisposableEffect onDispose {}
        }

        var lastShakeTimestampMs = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val ax = event.values.getOrNull(0) ?: return
                val ay = event.values.getOrNull(1) ?: return
                val az = event.values.getOrNull(2) ?: return
                val isLinear = event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION

                if (isShakeImpulse(ax, ay, az, isLinear)) {
                    val now = System.currentTimeMillis()
                    if (now - lastShakeTimestampMs >= SHAKE_COOLDOWN_MS) {
                        lastShakeTimestampMs = now
                        currentOnPhoneShaken()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        var isRegistered = false

        fun register() {
            if (!isRegistered) {
                isRegistered = sensorManager.registerListener(
                    listener,
                    shakeSensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
            }
        }

        fun unregister() {
            if (isRegistered) {
                sensorManager.unregisterListener(listener)
                isRegistered = false
            }
        }

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            register()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            unregister()
        }
    }
}

internal fun computeMagnitudeG(ax: Float, ay: Float, az: Float): Float {
    return sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat() /
        SensorManager.GRAVITY_EARTH
}

internal fun normalizeShakeIntensity(magnitudeG: Float): Float {
    if (magnitudeG <= SHAKE_THRESHOLD_G) return 0f
    return ((magnitudeG - SHAKE_THRESHOLD_G) / 2.2f).coerceIn(0.2f, 1.0f)
}

internal fun isShakeImpulse(
    ax: Float,
    ay: Float,
    az: Float,
    isLinearAcceleration: Boolean
): Boolean {
    val magnitudeG = computeMagnitudeG(ax, ay, az)
    return if (isLinearAcceleration) {
        magnitudeG > SHAKE_THRESHOLD_G
    } else {
        // Total accelerometer magnitude includes 1g gravity at rest (~1.0g), so > 1.8g rejects rest
        magnitudeG > SHAKE_THRESHOLD_G
    }
}

private fun normalizeDegreeDelta(delta: Float): Float {
    var d = delta
    while (d > 180f) d -= 360f
    while (d < -180f) d += 360f
    return d
}
