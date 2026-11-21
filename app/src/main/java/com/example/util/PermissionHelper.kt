package com.example.util

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

import com.example.service.LockScreenAccessibilityService

@Immutable
data class LockScreenPermissionStatus(
    val isAccessibilityServiceEnabled: Boolean = false,
    val canDrawOverlays: Boolean = false,
    val areNotificationsEnabled: Boolean = false,
    val canUseFullScreenIntent: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false
) {
    val allRequiredGranted: Boolean
        get() = isAccessibilityServiceEnabled || (areNotificationsEnabled && canUseFullScreenIntent)
}

object PermissionHelper {

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return LockScreenAccessibilityService.isEnabled(context)
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun areNotificationsEnabled(context: Context): Boolean {
        val systemEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val runtimeGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            return systemEnabled && runtimeGranted
        }
        return systemEnabled
    }

    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    ?: return false
            return notificationManager.canUseFullScreenIntent()
        }
        return true
    }

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun getStatus(context: Context): LockScreenPermissionStatus {
        return LockScreenPermissionStatus(
            isAccessibilityServiceEnabled = isAccessibilityServiceEnabled(context),
            canDrawOverlays = canDrawOverlays(context),
            areNotificationsEnabled = areNotificationsEnabled(context),
            canUseFullScreenIntent = canUseFullScreenIntent(context),
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context)
        )
    }

    fun buildOverlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun buildFullScreenIntentSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            buildNotificationSettingsIntent(context)
        }
    }

    fun buildNotificationSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
        }
    }

    fun buildBatteryOptimizationSettingsIntent(): Intent {
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
}
