package com.example.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import androidx.compose.runtime.Immutable
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.model.GyroSensitivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI

@Immutable
data class TiltVector(
    val pitch: Float = 0f,
    val roll: Float = 0f
)

/**
 * Battery-aware 3D orientation sensor manager powered by Sensor.TYPE_ROTATION_VECTOR
 * with a low-pass filter and automatic lifecycle + screen-off unregistration.
 */
class GyroscopeSensorManager(context: Context) : SensorEventListener, DefaultLifecycleObserver {

    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val rotationSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)

    private val _tiltFlow = MutableStateFlow(TiltVector())
    val tiltFlow: StateFlow<TiltVector> = _tiltFlow.asStateFlow()

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var hasBaseline = false
    private var baselinePitch = 0f
    private var baselineRoll = 0f
    private var filteredPitch = 0f
    private var filteredRoll = 0f

    private var isResumed = false
    private var isScreenInteractive = true
    private var isRegistered = false
    private var sensitivity: GyroSensitivity = GyroSensitivity.SUBTLE
    private var receiverRegistered = false

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenInteractive = false
                    unregisterSensor()
                    resetToNeutral()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenInteractive = true
                    hasBaseline = false
                    updateRegistrationState()
                }
            }
        }
    }

    fun setSensitivity(newSensitivity: GyroSensitivity) {
        if (sensitivity == newSensitivity) return
        sensitivity = newSensitivity
        if (newSensitivity == GyroSensitivity.OFF) {
            unregisterSensor()
            resetToNeutral()
        } else {
            hasBaseline = false
            updateRegistrationState()
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        registerScreenReceiver()
    }

    override fun onResume(owner: LifecycleOwner) {
        isResumed = true
        isScreenInteractive = powerManager?.isInteractive ?: true
        hasBaseline = false
        updateRegistrationState()
    }

    override fun onPause(owner: LifecycleOwner) {
        isResumed = false
        unregisterSensor()
    }

    override fun onStop(owner: LifecycleOwner) {
        isResumed = false
        unregisterSensor()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        unregisterSensor()
        unregisterScreenReceiver()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR &&
            event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR
        ) {
            return
        }

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // orientationAngles[1] = pitch (radians), orientationAngles[2] = roll (radians)
        val rawPitch = orientationAngles[1]
        val rawRoll = orientationAngles[2]

        if (!hasBaseline) {
            baselinePitch = rawPitch
            baselineRoll = rawRoll
            hasBaseline = true
        } else {
            // Very slow drift compensation so any comfortable holding posture re-centers naturally
            baselinePitch += (rawPitch - baselinePitch) * BASELINE_DRIFT_ALPHA
            baselineRoll += (rawRoll - baselineRoll) * BASELINE_DRIFT_ALPHA
        }

        val deltaPitch = normalizeAngleDelta(rawPitch - baselinePitch)
        val deltaRoll = normalizeAngleDelta(rawRoll - baselineRoll)

        val normalizedPitch = (deltaPitch / MAX_TILT_RADIANS).coerceIn(-1f, 1f)
        val normalizedRoll = (deltaRoll / MAX_TILT_RADIANS).coerceIn(-1f, 1f)

        // Low-pass filter for silky, jitter-free 3D motion
        filteredPitch += LOW_PASS_ALPHA * (normalizedPitch - filteredPitch)
        filteredRoll += LOW_PASS_ALPHA * (normalizedRoll - filteredRoll)

        _tiltFlow.value = TiltVector(
            pitch = filteredPitch,
            roll = filteredRoll
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    private fun updateRegistrationState() {
        val shouldListen = isResumed &&
            isScreenInteractive &&
            sensitivity != GyroSensitivity.OFF &&
            rotationSensor != null

        if (shouldListen && !isRegistered) {
            isRegistered = sensorManager?.registerListener(
                this,
                rotationSensor,
                SensorManager.SENSOR_DELAY_GAME
            ) == true
        } else if (!shouldListen && isRegistered) {
            unregisterSensor()
        }
    }

    private fun unregisterSensor() {
        if (isRegistered) {
            sensorManager?.unregisterListener(this)
            isRegistered = false
        }
    }

    private fun resetToNeutral() {
        hasBaseline = false
        filteredPitch = 0f
        filteredRoll = 0f
        _tiltFlow.value = TiltVector(0f, 0f)
    }

    private fun registerScreenReceiver() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        runCatching {
            appContext.registerReceiver(screenStateReceiver, filter)
            receiverRegistered = true
        }
    }

    private fun unregisterScreenReceiver() {
        if (!receiverRegistered) return
        runCatching {
            appContext.unregisterReceiver(screenStateReceiver)
        }
        receiverRegistered = false
    }

    private fun normalizeAngleDelta(delta: Float): Float {
        var angle = delta
        while (angle > PI.toFloat()) angle -= (2f * PI.toFloat())
        while (angle < -PI.toFloat()) angle += (2f * PI.toFloat())
        return angle
    }

    companion object {
        private const val LOW_PASS_ALPHA = 0.14f
        private const val BASELINE_DRIFT_ALPHA = 0.008f
        private const val MAX_TILT_RADIANS = 0.42f // ~24 degrees for full parallax travel
    }
}
