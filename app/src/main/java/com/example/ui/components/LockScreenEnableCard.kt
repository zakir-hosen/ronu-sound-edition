package com.example.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.service.LockScreenAccessibilityService
import com.example.service.LockScreenService
import com.example.ui.theme.DeepCharcoal
import com.example.ui.theme.HairlineCharcoal
import com.example.ui.theme.PureAmoledBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SubtleLightGray
import com.example.util.LockScreenPermissionStatus
import com.example.util.PermissionHelper

private val CmfOrangeAccent = Color(0xFFFF4500)
private val ElectricCyanAccent = Color(0xFF00E5FF)
private val ActiveGreen = Color(0xFF34D399)

@Composable
fun LockScreenEnableCard(
    isLiveLockEnabled: Boolean,
    onSetLiveLockEnabled: (Boolean) -> Unit,
    onPreviewLockScreen: () -> Unit,
    modifier: Modifier = Modifier,
    permissionStatusOverride: LockScreenPermissionStatus? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var status by remember(permissionStatusOverride) {
        mutableStateOf(permissionStatusOverride ?: PermissionHelper.getStatus(context))
    }

    DisposableEffect(lifecycleOwner, permissionStatusOverride) {
        if (permissionStatusOverride != null) {
            status = permissionStatusOverride
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    status = PermissionHelper.getStatus(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    val overlayLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        status = permissionStatusOverride ?: PermissionHelper.getStatus(context)
    }

    val notificationSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        status = permissionStatusOverride ?: PermissionHelper.getStatus(context)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        status = permissionStatusOverride ?: PermissionHelper.getStatus(context)
        if (!granted && !PermissionHelper.areNotificationsEnabled(context)) {
            runCatching {
                notificationSettingsLauncher.launch(
                    PermissionHelper.buildNotificationSettingsIntent(context)
                )
            }
        }
    }

    val batteryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        status = permissionStatusOverride ?: PermissionHelper.getStatus(context)
    }

    val fullScreenIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        status = permissionStatusOverride ?: PermissionHelper.getStatus(context)
    }

    val cardBorderColor = when {
        isLiveLockEnabled -> ActiveGreen
        status.allRequiredGranted -> ElectricCyanAccent
        else -> CmfOrangeAccent
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = DeepCharcoal,
        border = BorderStroke(1.2.dp, cardBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .testTag("lock_screen_enable_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.enable_lock_card_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = PureWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.enable_lock_card_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = SubtleLightGray
                    )
                }

                if (isLiveLockEnabled) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = ActiveGreen.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, ActiveGreen.copy(alpha = 0.65f)),
                        modifier = Modifier.testTag("emu_lock_active_badge")
                    ) {
                        Text(
                            text = stringResource(R.string.emu_lock_active_badge),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = ActiveGreen,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = HairlineCharcoal)

            // Step 1: Accessibility Lock Overlay (Unkillable on Android 14/15)
            PermissionStepRow(
                icon = Icons.Rounded.AccessibilityNew,
                title = stringResource(R.string.step_accessibility_title),
                subtitle = stringResource(R.string.step_accessibility_sub),
                isGranted = status.isAccessibilityServiceEnabled,
                actionLabel = stringResource(R.string.action_enable),
                accentColor = ElectricCyanAccent,
                buttonTag = "enable_accessibility_overlay_button",
                onAction = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    LockScreenAccessibilityService.openAccessibilitySettings(context)
                }
            )

            if (!status.isAccessibilityServiceEnabled) {
                // Fallback Overlay Permission
                PermissionStepRow(
                    icon = Icons.Rounded.Layers,
                    title = stringResource(R.string.step_overlay_title),
                    subtitle = stringResource(R.string.step_overlay_sub),
                    isGranted = status.canDrawOverlays,
                    actionLabel = stringResource(R.string.action_grant),
                    accentColor = CmfOrangeAccent,
                    buttonTag = "grant_overlay_button",
                    onAction = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        runCatching {
                            overlayLauncher.launch(PermissionHelper.buildOverlayPermissionIntent(context))
                        }
                    }
                )

                // Fallback Full-Screen Intent (Android 14+)
                PermissionStepRow(
                    icon = Icons.Rounded.Fullscreen,
                    title = stringResource(R.string.step_full_screen_title),
                    subtitle = stringResource(R.string.step_full_screen_sub),
                    isGranted = status.canUseFullScreenIntent,
                    actionLabel = stringResource(R.string.action_grant),
                    accentColor = CmfOrangeAccent,
                    buttonTag = "grant_full_screen_intent_button",
                    onAction = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        runCatching {
                            fullScreenIntentLauncher.launch(
                                PermissionHelper.buildFullScreenIntentSettingsIntent(context)
                            )
                        }
                    }
                )

                // Fallback Notification Permission
                PermissionStepRow(
                    icon = Icons.Rounded.NotificationsActive,
                    title = stringResource(R.string.step_notification_title),
                    subtitle = stringResource(R.string.step_notification_sub),
                    isGranted = status.areNotificationsEnabled,
                    actionLabel = stringResource(R.string.action_allow),
                    accentColor = ElectricCyanAccent,
                    buttonTag = "allow_notification_button",
                    onAction = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            runCatching {
                                notificationSettingsLauncher.launch(
                                    PermissionHelper.buildNotificationSettingsIntent(context)
                                )
                            }
                        }
                    }
                )
            }

            // Battery Optimization Status Check
            PermissionStepRow(
                icon = Icons.Rounded.BatteryChargingFull,
                title = stringResource(R.string.step_battery_title),
                subtitle = if (status.isIgnoringBatteryOptimizations) {
                    stringResource(R.string.step_battery_unrestricted)
                } else {
                    stringResource(R.string.step_battery_restricted)
                },
                isGranted = status.isIgnoringBatteryOptimizations,
                actionLabel = stringResource(R.string.action_optimize),
                accentColor = SubtleLightGray,
                buttonTag = "optimize_battery_button",
                onAction = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    runCatching {
                        batteryLauncher.launch(PermissionHelper.buildBatteryOptimizationSettingsIntent())
                    }
                }
            )

            // Step 3: Master Enable Emu Lock Screen / Active Controls
            if (!isLiveLockEnabled) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (!status.isAccessibilityServiceEnabled) {
                            runCatching { LockScreenService.start(context) }
                        }
                        onSetLiveLockEnabled(true)
                    },
                    enabled = status.allRequiredGranted,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyanAccent,
                        contentColor = PureAmoledBlack,
                        disabledContainerColor = HairlineCharcoal,
                        disabledContentColor = SubtleLightGray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("enable_lock_screen_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.action_enable_lock),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPreviewLockScreen()
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureWhite,
                            contentColor = PureAmoledBlack
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("preview_lock_screen_now_button")
                    ) {
                        Text(
                            text = stringResource(R.string.preview_lock_screen_now),
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }

                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            LockScreenAccessibilityService.dismissOverlayNow()
                            runCatching { LockScreenService.stop(context) }
                            onSetLiveLockEnabled(false)
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("disable_lock_screen_button")
                    ) {
                        Text(
                            text = stringResource(R.string.action_disable_lock),
                            style = MaterialTheme.typography.labelLarge,
                            color = SubtleLightGray
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionStepRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String,
    accentColor: Color,
    buttonTag: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isGranted) ActiveGreen else accentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = PureWhite
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SubtleLightGray
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isGranted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = "$title granted",
                tint = ActiveGreen,
                modifier = Modifier
                    .size(22.dp)
                    .testTag("${buttonTag}_checked")
            )
        } else {
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, accentColor),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier
                    .defaultMinSize(minWidth = 64.dp, minHeight = 48.dp)
                    .testTag(buttonTag)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = accentColor
                )
            }
        }
    }
}
