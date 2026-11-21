package com.example.audio

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.PowerManager
import android.view.SoundEffectConstants
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Native robotic SFX controller powered by Android's [ToneGenerator] on [AudioManager.STREAM_MUSIC]
 * with an [AudioManager.playSoundEffect] fallback.
 *
 * - Blink, Shake, and Happy Chirp use volume `85` on `STREAM_MUSIC` for crisp, clear output.
 * - Eye-Follow uses a dedicated low-volume (`25`) `ToneGenerator` with `TONE_PROP_PROMPT` (`15ms`)
 *   throttled by a `2500ms` cooldown so gaze shifts feel like a delicate mechanical micro-tick.
 * - Does not block playback on `RINGER_MODE_NORMAL`; gated only by `isEnabled`.
 */
class EmuSoundEngine(
    context: Context,
    enabled: Boolean = true
) {
    private val appContext: Context = context.applicationContext
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val powerManager: PowerManager? =
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val toneLock = Any()
    private val chirpScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var toneGen: ToneGenerator? = createToneGenerator(TONE_VOLUME)

    @Volatile
    private var microTickToneGen: ToneGenerator? = createToneGenerator(MICRO_TICK_VOLUME)

    @Volatile
    private var happyChirpJob: Job? = null

    @Volatile
    private var isShutDown: Boolean = false

    @Volatile
    var isEnabled: Boolean = enabled

    @Volatile
    private var lastFollowSoundTimestampMs: Long = 0L

    @Volatile
    private var lastShakeWarbleTimestampMs: Long = 0L

    @Volatile
    internal var lastTriggeredToneType: Int? = null
        private set

    @Volatile
    internal var lastTriggeredDurationMs: Int? = null
        private set

    @Volatile
    internal var lastTriggeredVolume: Int? = null
        private set

    /**
     * Plays a crisp, short high-tech blink chirp (`TONE_PROP_BEEP`, 45ms, volume 85).
     */
    fun playBlinkSound(): Boolean {
        if (!isEnabled || !isScreenInteractive()) return false
        return playTone(ToneGenerator.TONE_PROP_BEEP, BLINK_DURATION_MS)
    }

    /**
     * Plays an ultra-soft mechanical micro-tick (`TONE_PROP_PROMPT`, 15ms, volume 25)
     * throttled by a `2500ms` cooldown so normal hand motion stays quiet.
     */
    @JvmOverloads
    fun playEyeFollowSound(
        context: Context = appContext,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (!isEnabled || !isScreenInteractive()) return false
        if (lastFollowSoundTimestampMs != 0L &&
            nowMs - lastFollowSoundTimestampMs < EYE_FOLLOW_COOLDOWN_MS
        ) {
            return false
        }
        lastFollowSoundTimestampMs = nowMs
        return playMicroTickTone(context)
    }

    /**
     * Plays a quick shake warble (`TONE_CDMA_PIP`, 80ms, volume 85) throttled during continuous shake loops.
     */
    fun playShakeDizzySound(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!isEnabled || !isScreenInteractive()) return false
        if (lastShakeWarbleTimestampMs != 0L &&
            nowMs - lastShakeWarbleTimestampMs < SHAKE_WARBLE_COOLDOWN_MS
        ) {
            return false
        }
        lastShakeWarbleTimestampMs = nowMs
        return playTone(ToneGenerator.TONE_CDMA_PIP, SHAKE_WARBLE_DURATION_MS)
    }

    /**
     * Plays a cheerful two-tone happy chirp (`TONE_PROP_BEEP2` 70ms -> 90ms delay -> `TONE_PROP_ACK` 90ms).
     */
    fun playHappyChirp(): Boolean {
        if (!isEnabled || !isScreenInteractive()) return false
        synchronized(toneLock) {
            if (isShutDown) return false
            happyChirpJob?.cancel()
            happyChirpJob = chirpScope.launch {
                playTone(ToneGenerator.TONE_PROP_BEEP2, HAPPY_CHIRP_FIRST_DURATION_MS)
                delay(HAPPY_CHIRP_GAP_DELAY_MS)
                if (isEnabled && !isShutDown && isScreenInteractive()) {
                    playTone(ToneGenerator.TONE_PROP_ACK, HAPPY_CHIRP_SECOND_DURATION_MS)
                }
            }
        }
        return true
    }

    private fun isScreenInteractive(): Boolean {
        return powerManager?.isInteractive != false
    }

    private fun playTone(toneType: Int, durationMs: Int): Boolean {
        if (!isEnabled || !isScreenInteractive()) return false
        lastTriggeredToneType = toneType
        lastTriggeredDurationMs = durationMs
        lastTriggeredVolume = TONE_VOLUME

        val started = synchronized(toneLock) {
            if (isShutDown) return false
            try {
                val generator = toneGen ?: createToneGenerator(TONE_VOLUME).also { toneGen = it }
                generator?.startTone(toneType, durationMs) == true
            } catch (_: Throwable) {
                false
            }
        }

        if (!started) {
            playFallbackSoundEffect(appContext)
        }
        return true
    }

    private fun playMicroTickTone(context: Context): Boolean {
        if (!isEnabled || !isScreenInteractive()) return false
        lastTriggeredToneType = ToneGenerator.TONE_PROP_PROMPT
        lastTriggeredDurationMs = EYE_FOLLOW_DURATION_MS
        lastTriggeredVolume = MICRO_TICK_VOLUME

        val started = synchronized(toneLock) {
            if (isShutDown) return false
            try {
                val generator = microTickToneGen
                    ?: createToneGenerator(MICRO_TICK_VOLUME).also { microTickToneGen = it }
                generator?.startTone(ToneGenerator.TONE_PROP_PROMPT, EYE_FOLLOW_DURATION_MS) == true
            } catch (_: Throwable) {
                false
            }
        }

        if (!started) {
            playFallbackSoundEffect(context)
        }
        return true
    }

    private fun playFallbackSoundEffect(context: Context) {
        try {
            val manager = (context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                ?: audioManager
            manager?.playSoundEffect(SoundEffectConstants.CLICK)
        } catch (_: Throwable) {
            // Safe no-op if system audio service rejects sound effect playback
        }
    }

    private fun createToneGenerator(volume: Int): ToneGenerator? {
        return try {
            ToneGenerator(STREAM_TYPE, volume)
        } catch (_: Throwable) {
            null
        }
    }

    fun release() {
        synchronized(toneLock) {
            happyChirpJob?.cancel()
            happyChirpJob = null
            try {
                toneGen?.stopTone()
            } catch (_: Throwable) {
            }
            try {
                toneGen?.release()
            } catch (_: Throwable) {
            }
            toneGen = null

            try {
                microTickToneGen?.stopTone()
            } catch (_: Throwable) {
            }
            try {
                microTickToneGen?.release()
            } catch (_: Throwable) {
            }
            microTickToneGen = null
        }
    }

    fun shutdown() {
        synchronized(toneLock) {
            isShutDown = true
        }
        release()
        chirpScope.cancel()
    }

    companion object {
        const val STREAM_TYPE = AudioManager.STREAM_MUSIC
        const val TONE_VOLUME = 85
        const val MICRO_TICK_VOLUME = 25
        const val BLINK_DURATION_MS = 45
        const val EYE_FOLLOW_DURATION_MS = 15
        const val SHAKE_WARBLE_DURATION_MS = 80
        const val HAPPY_CHIRP_FIRST_DURATION_MS = 70
        const val HAPPY_CHIRP_GAP_DELAY_MS = 90L
        const val HAPPY_CHIRP_SECOND_DURATION_MS = 90
        const val EYE_FOLLOW_COOLDOWN_MS = 2_500L
        const val SHAKE_WARBLE_COOLDOWN_MS = 220L
    }
}

/**
 * Lifecycle-aware Composable helper returning an [EmuSoundEngine] that releases audio
 * resources on `ON_PAUSE`, `ON_STOP`, `ON_DESTROY`, or composition disposal.
 */
@Composable
fun rememberEmuSoundEngine(enabled: Boolean): EmuSoundEngine {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val engine = remember(context.applicationContext) {
        EmuSoundEngine(context = context.applicationContext, enabled = enabled)
    }
    engine.isEnabled = enabled

    DisposableEffect(lifecycleOwner, engine) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY -> engine.release()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            engine.shutdown()
        }
    }

    return engine
}
