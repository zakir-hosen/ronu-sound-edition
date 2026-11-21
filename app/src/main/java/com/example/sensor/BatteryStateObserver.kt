package com.example.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

enum class BatteryExpression {
    NORMAL,
    CHARGING_HAPPY,
    LOW_SLEEPY
}

@Immutable
data class BatteryStatus(
    val levelPercent: Int = 100,
    val isCharging: Boolean = false,
    val expression: BatteryExpression = BatteryExpression.NORMAL
)

/**
 * Lifecycle-aware observer for system battery state (charging, percentage, and facial expression).
 * Registers on ON_RESUME and unregisters on ON_PAUSE / onDispose to prevent leaks or battery drain.
 */
@Composable
fun rememberBatteryStatus(): State<BatteryStatus> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val state = remember {
        mutableStateOf(readStickyBatteryStatus(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val appContext = context.applicationContext
        var isRegistered = false

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent != null) {
                    state.value = parseBatteryIntent(intent)
                }
            }
        }

        fun register() {
            if (!isRegistered) {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val sticky = runCatching {
                    appContext.registerReceiver(receiver, filter)
                }.getOrNull()
                if (sticky != null) {
                    state.value = parseBatteryIntent(sticky)
                }
                isRegistered = true
            }
        }

        fun unregister() {
            if (isRegistered) {
                runCatching { appContext.unregisterReceiver(receiver) }
                isRegistered = false
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> register()
                Lifecycle.Event.ON_PAUSE -> unregister()
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            register()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            unregister()
        }
    }

    return state
}

private fun readStickyBatteryStatus(context: Context): BatteryStatus {
    val sticky = runCatching {
        context.applicationContext.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
    }.getOrNull()
    return if (sticky != null) parseBatteryIntent(sticky) else BatteryStatus()
}

internal fun parseBatteryIntent(intent: Intent): BatteryStatus {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val percent = if (level >= 0 && scale > 0) {
        ((level * 100f) / scale).toInt().coerceIn(0, 100)
    } else {
        100
    }

    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
    val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        (status == BatteryManager.BATTERY_STATUS_FULL && plugged != 0) ||
        plugged != 0

    val expression = when {
        isCharging -> BatteryExpression.CHARGING_HAPPY
        percent <= 20 -> BatteryExpression.LOW_SLEEPY
        else -> BatteryExpression.NORMAL
    }

    return BatteryStatus(
        levelPercent = percent,
        isCharging = isCharging,
        expression = expression
    )
}
