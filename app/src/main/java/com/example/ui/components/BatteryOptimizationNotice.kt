package com.example.ui.components

import android.content.Context
import android.os.PowerManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.example.ui.theme.DeepCharcoal
import com.example.ui.theme.PureAmoledBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SubtleLightGray
import com.example.util.PermissionHelper

private val AmberWarningAccent = Color(0xFFF59E0B)

/**
 * Checks [PowerManager.isIgnoringBatteryOptimizations] lifecycle-aware and surfaces an action
 * banner when battery optimization could terminate [com.example.service.LockScreenService].
 */
@Composable
fun BatteryOptimizationNotice(
    modifier: Modifier = Modifier,
    isIgnoringOverride: Boolean? = null,
    onOpenSettingsClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isIgnoringOptimization by remember(isIgnoringOverride) {
        mutableStateOf(isIgnoringOverride ?: checkIgnoringBatteryOptimization(context))
    }

    DisposableEffect(lifecycleOwner, isIgnoringOverride) {
        if (isIgnoringOverride != null) {
            isIgnoringOptimization = isIgnoringOverride
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isIgnoringOptimization = checkIgnoringBatteryOptimization(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    val batterySettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isIgnoringOptimization = isIgnoringOverride ?: checkIgnoringBatteryOptimization(context)
    }

    AnimatedVisibility(
        visible = !isIgnoringOptimization,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = DeepCharcoal,
            border = BorderStroke(1.2.dp, AmberWarningAccent.copy(alpha = 0.75f)),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("battery_optimization_notice")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Rounded.BatteryAlert,
                        contentDescription = null,
                        tint = AmberWarningAccent,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.battery_notice_title),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = PureWhite
                        )
                        Text(
                            text = stringResource(R.string.battery_notice_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = SubtleLightGray,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (onOpenSettingsClick != null) {
                            onOpenSettingsClick()
                        } else {
                            runCatching {
                                batterySettingsLauncher.launch(
                                    PermissionHelper.buildBatteryOptimizationSettingsIntent()
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, AmberWarningAccent),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = PureAmoledBlack,
                        contentColor = AmberWarningAccent
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("battery_optimization_notice_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.battery_notice_action),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
    }
}

internal fun checkIgnoringBatteryOptimization(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        ?: return false
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}
