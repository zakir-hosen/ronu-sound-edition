package com.example.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.AppEntry
import com.example.model.EmuAnimationStyle
import com.example.model.EmuEyeColorPresets
import com.example.model.LauncherAlignment
import com.example.model.LauncherConfig
import com.example.model.LauncherConfigManager
import com.example.model.SurfaceMode
import com.example.sensor.rememberBatteryStatus
import com.example.sensor.rememberDeviceTilt
import com.example.sensor.rememberShakeTracker
import com.example.service.LockScreenAccessibilityService
import com.example.ui.components.EmuEyeClock
import com.example.ui.components.EmuFacialExpression
import java.util.Locale

@Composable
fun MinimalHomeScreen(
    pinnedApps: List<AppEntry>,
    surfaceMode: SurfaceMode,
    config: LauncherConfig,
    showTiltDebugOverlay: Boolean,
    onClockSensitivityChange: (Float) -> Unit,
    onMenuSensitivityChange: (Float) -> Unit,
    onToggleTiltDebugOverlay: (Boolean) -> Unit,
    onAppClick: (AppEntry) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current.density

    val gyroEnabled = config.clockSensitivity > 0f || config.menuSensitivity > 0f
    val tilt by rememberDeviceTilt(enabled = gyroEnabled)
    val batteryStatus by rememberBatteryStatus()
    val shakeState by rememberShakeTracker(enabled = config.isShakeReactionEnabled)

    val smoothedClockSensitivity by animateFloatAsState(
        targetValue = config.clockSensitivity,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "smoothed_clock_sensitivity"
    )
    val smoothedMenuSensitivity by animateFloatAsState(
        targetValue = config.menuSensitivity,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "smoothed_menu_sensitivity"
    )

    val clockPitch = tilt.first * smoothedClockSensitivity
    val clockRoll = tilt.second * smoothedClockSensitivity
    val menuPitch = tilt.first * smoothedMenuSensitivity
    val menuRoll = tilt.second * smoothedMenuSensitivity
    val bgFactor = maxOf(smoothedClockSensitivity, smoothedMenuSensitivity)

    val animatedMenuBias by animateFloatAsState(
        targetValue = config.menuAlignment.bias,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "menu_alignment_bias"
    )

    var showAccessibilityPrompt by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                            LockScreenAccessibilityService.isEnabled(context)
                        ) {
                            LockScreenAccessibilityService.lockScreen(context)
                        } else {
                            showAccessibilityPrompt = true
                        }
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onOpenSettings()
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                        if (dragAccumulator < -90f) {
                            dragAccumulator = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenDrawer()
                        }
                    }
                )
            }
            .testTag("minimal_home_screen")
    ) {
        // Layer 1 (Background): Subtle micro-dots shifting opposite to device tilt
        AmbientDepthCanvas(
            surfaceMode = surfaceMode,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    cameraDistance = 14f * density
                    translationX = -tilt.second * 12f * bgFactor * density
                    translationY = -tilt.first * 12f * bgFactor * density
                }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            // Layer 2: Unified 3D Emu Eye-Clock (HH/MM inside eyes + AM/PM & Date below)
            EmuEyeClock(
                tiltPitch = clockPitch,
                tiltRoll = clockRoll,
                batteryStatus = batteryStatus,
                alignment = config.clockAlignment,
                animationStyle = config.emuAnimationStyle,
                eyeColor = EmuEyeColorPresets.parseHexColor(config.eyeColorHex),
                showShadow = config.isClockShadowEnabled,
                selectedEmotion = EmuFacialExpression.fromName(config.selectedEmotionName),
                isActivelyShaking = shakeState.isActivelyShaking,
                shakeIntensity = shakeState.shakeIntensity,
                isShakeReactionEnabled = config.isShakeReactionEnabled,
                shakeRecoveryDurationSec = config.shakeRecoveryDurationSec,
                isSoundEnabled = config.isHomeSoundEnabled,
                isEyeFollowSoundEnabled = config.isEyeFollowSoundEnabled
            )

            // Layer 3: Pinned Text App Menu with independent sensitivity & alignment
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            cameraDistance = 14f * density
                            rotationX = menuPitch * 20f
                            rotationY = menuRoll * 20f
                            translationX = menuRoll * 24f * density
                            translationY = menuPitch * 20f * density
                        }
                        .testTag("pinned_apps_list"),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalAlignment = BiasAlignment.Horizontal(animatedMenuBias)
                ) {
                    pinnedApps.forEach { app ->
                        MinimalAppTextItem(
                            app = app,
                            alignment = config.menuAlignment,
                            bias = animatedMenuBias,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onAppClick(app)
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onOpenSettings()
                            }
                        )
                    }
                }
            }

            // Expandable quick sensitivity debug panel for Clock & App Menu
            AnimatedVisibility(visible = showTiltDebugOverlay) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("tilt_debug_panel")
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = String.format(
                                Locale.getDefault(),
                                "Clock: %.1fx  |  Menu: %.1fx  (P %.2f, R %.2f)",
                                config.clockSensitivity,
                                config.menuSensitivity,
                                tilt.first,
                                tilt.second
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Slider(
                            value = config.clockSensitivity,
                            onValueChange = onClockSensitivityChange,
                            valueRange = LauncherConfigManager.MIN_SENSITIVITY..LauncherConfigManager.MAX_SENSITIVITY,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("home_clock_sensitivity_slider")
                        )
                        Slider(
                            value = config.menuSensitivity,
                            onValueChange = onMenuSensitivityChange,
                            valueRange = LauncherConfigManager.MIN_SENSITIVITY..LauncherConfigManager.MAX_SENSITIVITY,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.secondary,
                                activeTrackColor = MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.testTag("home_menu_sensitivity_slider")
                        )
                    }
                }
            }

            // Bottom Section: page indicator dots + Q Search trigger + subtle tilt debug toggle
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f))
                    )
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Box(modifier = Modifier.fillMaxWidth()) {
                    SearchDrawerTrigger(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onOpenDrawer()
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )

                    IconButton(
                        onClick = { onToggleTiltDebugOverlay(!showTiltDebugOverlay) },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .testTag("tilt_debug_toggle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = stringResource(R.string.tilt_debug_toggle),
                            tint = if (showTiltDebugOverlay) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            },
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAccessibilityPrompt) {
        AlertDialog(
            onDismissRequest = { showAccessibilityPrompt = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = stringResource(R.string.double_tap_lock_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            },
            text = {
                Text(
                    text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        stringResource(R.string.double_tap_lock_message)
                    } else {
                        stringResource(R.string.double_tap_lock_unsupported)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    TextButton(
                        onClick = {
                            showAccessibilityPrompt = false
                            LockScreenAccessibilityService.openAccessibilitySettings(context)
                        },
                        modifier = Modifier.testTag("open_accessibility_settings_button")
                    ) {
                        Text(
                            text = stringResource(R.string.double_tap_lock_open_settings),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityPrompt = false }) {
                    Text(
                        text = stringResource(R.string.done),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}

@Composable
private fun AmbientDepthCanvas(
    surfaceMode: SurfaceMode,
    modifier: Modifier = Modifier
) {
    val dotColor = MaterialTheme.colorScheme.onBackground
    val dotAlpha = if (surfaceMode == SurfaceMode.AMOLED_BLACK) 0.03f else 0.045f

    Canvas(modifier = modifier) {
        val spacing = 36.dp.toPx()
        val radius = 0.9.dp.toPx()
        var x = spacing / 2f
        while (x < size.width) {
            var y = spacing / 2f
            while (y < size.height) {
                drawCircle(
                    color = dotColor.copy(alpha = dotAlpha),
                    radius = radius,
                    center = Offset(x, y)
                )
                y += spacing
            }
            x += spacing
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MinimalAppTextItem(
    app: AppEntry,
    alignment: LauncherAlignment,
    bias: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "app_item_scale"
    )

    val textAlign: TextAlign = alignment.toTextAlign()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .scale(pressScale)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp)
            .testTag("pinned_app_${app.packageName}"),
        contentAlignment = BiasAlignment(horizontalBias = bias, verticalBias = 0f)
    ) {
        Text(
            text = app.label,
            textAlign = textAlign,
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            ),
            color = if (isPressed) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onBackground
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchDrawerTrigger(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "search_trigger_scale"
    )

    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 120.dp, minHeight = 48.dp)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .testTag("search_drawer_trigger"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = stringResource(R.string.search_apps_cd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.search_apps_trigger),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
                letterSpacing = 0.3.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
