package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.rememberEmuSoundEngine
import com.example.model.EmuAnimationStyle
import com.example.model.EmuEyeColorPreset
import com.example.model.EmuEyeColorPresets
import com.example.model.LauncherAlignment
import com.example.model.LauncherConfig
import com.example.sensor.rememberBatteryStatus
import com.example.sensor.rememberDeviceTilt
import com.example.sensor.rememberShakeTracker
import com.example.ui.components.BatteryOptimizationNotice
import com.example.ui.components.EmuEyeClock
import com.example.ui.components.EmuFacialExpression
import com.example.ui.components.LockScreenEnableCard
import com.example.ui.theme.DeepCharcoal
import com.example.ui.theme.ElevatedCharcoal
import com.example.ui.theme.HairlineCharcoal
import com.example.ui.theme.PureAmoledBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.SubtleLightGray
import com.example.util.LockScreenPermissionStatus
import java.util.Locale

@Composable
fun LockScreenAnimationsScreen(
    selectedStyle: EmuAnimationStyle,
    clockSensitivity: Float,
    onSelectStyle: (EmuAnimationStyle) -> Unit,
    onPreviewLockScreen: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    selectedEyeColorHex: String = LauncherConfig.DEFAULT_EYE_COLOR_HEX,
    onSelectEyeColor: (String) -> Unit = {},
    isClockShadowEnabled: Boolean = LauncherConfig.DEFAULT_CLOCK_SHADOW_ENABLED,
    onSetClockShadowEnabled: (Boolean) -> Unit = {},
    isLiveLockEnabled: Boolean = false,
    onSetLiveLockEnabled: (Boolean) -> Unit = {},
    selectedEmotionName: String = LauncherConfig.DEFAULT_SELECTED_EMOTION_NAME,
    onSelectEmotion: (String) -> Unit = {},
    isShakeReactionEnabled: Boolean = LauncherConfig.DEFAULT_SHAKE_REACTION_ENABLED,
    onSetShakeReactionEnabled: (Boolean) -> Unit = {},
    shakeRecoveryDurationSec: Float = LauncherConfig.DEFAULT_SHAKE_RECOVERY_DURATION_SEC,
    onSetShakeRecoveryDurationSec: (Float) -> Unit = {},
    isHomeSoundEnabled: Boolean = LauncherConfig.DEFAULT_HOME_SOUND_ENABLED,
    onSetHomeSoundEnabled: (Boolean) -> Unit = {},
    isLockScreenSoundEnabled: Boolean = LauncherConfig.DEFAULT_LOCK_SCREEN_SOUND_ENABLED,
    onSetLockScreenSoundEnabled: (Boolean) -> Unit = {},
    isEyeFollowSoundEnabled: Boolean = LauncherConfig.DEFAULT_EYE_FOLLOW_SOUND_ENABLED,
    onSetEyeFollowSoundEnabled: (Boolean) -> Unit = {},
    permissionStatusOverride: LockScreenPermissionStatus? = null
) {
    val haptic = LocalHapticFeedback.current
    val soundEngine = rememberEmuSoundEngine(enabled = true)
    val effectiveSensitivity = clockSensitivity.coerceAtLeast(1.0f)
    val tilt by rememberDeviceTilt(enabled = true)
    val batteryStatus by rememberBatteryStatus()
    val shakeState by rememberShakeTracker(enabled = isShakeReactionEnabled)
    var testBlinkCount by remember { mutableIntStateOf(0) }
    var shakeSpinCount by remember { mutableIntStateOf(0) }
    var selectedEmotion by remember(selectedEmotionName) {
        mutableStateOf(EmuFacialExpression.fromName(selectedEmotionName))
    }

    val selectedEyeColor = remember(selectedEyeColorHex) {
        EmuEyeColorPresets.parseHexColor(selectedEyeColorHex)
    }

    BackHandler { onBack() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureAmoledBlack)
            .testTag("lock_screen_animations_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .align(Alignment.Center)
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onBack()
                        },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("animations_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.close_drawer_cd),
                            tint = PureWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.lock_screen_animations_title),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = PureWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPreviewLockScreen()
                    },
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("preview_lock_screen_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = PureWhite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.preview_lock_screen),
                        style = MaterialTheme.typography.labelLarge,
                        color = PureWhite,
                        maxLines = 1
                    )
                }
            }

            // Unified scrollable content: Setup Card -> Live Stage -> Quick Expression Chips -> Swatches -> Animation Styles
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("animation_styles_list"),
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item(key = "enable_card") {
                    LockScreenEnableCard(
                        isLiveLockEnabled = isLiveLockEnabled,
                        onSetLiveLockEnabled = onSetLiveLockEnabled,
                        onPreviewLockScreen = onPreviewLockScreen,
                        permissionStatusOverride = permissionStatusOverride
                    )
                }

                item(key = "battery_optimization_notice") {
                    BatteryOptimizationNotice(
                        isIgnoringOverride = permissionStatusOverride?.isIgnoringBatteryOptimizations
                    )
                }

                item(key = "live_preview_stage") {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = PureAmoledBlack,
                        border = BorderStroke(1.dp, HairlineCharcoal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("animations_live_preview_stage")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LIVE STAGE",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        letterSpacing = 1.4.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = SubtleLightGray
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ElevatedCharcoal,
                                    border = BorderStroke(1.dp, HairlineCharcoal)
                                ) {
                                    Text(
                                        text = if (selectedEmotion == EmuFacialExpression.ALERT) {
                                            selectedStyle.displayName
                                        } else {
                                            selectedEmotion.label
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = PureWhite,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            EmuEyeClock(
                                tiltPitch = tilt.first * effectiveSensitivity,
                                tiltRoll = tilt.second * effectiveSensitivity,
                                batteryStatus = batteryStatus,
                                alignment = LauncherAlignment.CENTER,
                                animationStyle = selectedStyle,
                                eyeColor = selectedEyeColor,
                                showShadow = isClockShadowEnabled,
                                selectedEmotion = selectedEmotion,
                                isActivelyShaking = shakeState.isActivelyShaking,
                                shakeIntensity = shakeState.shakeIntensity,
                                isShakeReactionEnabled = isShakeReactionEnabled,
                                shakeRecoveryDurationSec = shakeRecoveryDurationSec,
                                isSoundEnabled = true,
                                isEyeFollowSoundEnabled = isEyeFollowSoundEnabled,
                                testBlinkTrigger = testBlinkCount,
                                shakeTrigger = shakeSpinCount
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        testBlinkCount++
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, HairlineCharcoal),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = DeepCharcoal,
                                        contentColor = PureWhite
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("trigger_test_blink_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Visibility,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.trigger_test_blink),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                OutlinedButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        shakeSpinCount++
                                    },
                                    enabled = isShakeReactionEnabled,
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, HairlineCharcoal),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = DeepCharcoal,
                                        contentColor = PureWhite
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = 48.dp)
                                        .testTag("test_shake_spin_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.RotateRight,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.test_shake_spin),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "quick_expression_chips") {
                    QuickExpressionPreviewChipsRow(
                        selectedExpression = selectedEmotion,
                        onSelectExpression = { expression ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedEmotion = expression
                            onSelectEmotion(expression.name)
                        }
                    )
                }

                item(key = "eye_color_swatches") {
                    EyeColorSwatchRow(
                        presets = EmuEyeColorPresets.all,
                        selectedHex = selectedEyeColorHex,
                        onSelect = { preset ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectEyeColor(preset.hex)
                        }
                    )
                }

                item(key = "clock_shadow_toggle") {
                    ClockShadowToggleRow(
                        checked = isClockShadowEnabled,
                        onCheckedChange = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSetClockShadowEnabled(enabled)
                        }
                    )
                }

                item(key = "shake_reaction_controls") {
                    ShakeReactionControlCard(
                        enabled = isShakeReactionEnabled,
                        recoveryDurationSec = shakeRecoveryDurationSec,
                        onEnabledChange = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSetShakeReactionEnabled(enabled)
                        },
                        onRecoveryDurationChange = onSetShakeRecoveryDurationSec
                    )
                }

                item(key = "sound_effects_controls") {
                    SoundEffectsControlCard(
                        isHomeSoundEnabled = isHomeSoundEnabled,
                        isLockScreenSoundEnabled = isLockScreenSoundEnabled,
                        isEyeFollowSoundEnabled = isEyeFollowSoundEnabled,
                        onHomeSoundChange = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSetHomeSoundEnabled(enabled)
                        },
                        onLockScreenSoundChange = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSetLockScreenSoundEnabled(enabled)
                        },
                        onEyeFollowSoundChange = { enabled ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSetEyeFollowSoundEnabled(enabled)
                        },
                        onTestSoundClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            soundEngine.playHappyChirp()
                        }
                    )
                }

                items(
                    items = EmuAnimationStyle.entries,
                    key = { it.name }
                ) { style ->
                    val isSelected = style == selectedStyle
                    AnimationStyleCard(
                        style = style,
                        isSelected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelectStyle(style)
                            testBlinkCount++
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickExpressionPreviewChipsRow(
    selectedExpression: EmuFacialExpression?,
    onSelectExpression: (EmuFacialExpression) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.expression_preview_label),
            style = MaterialTheme.typography.labelMedium.copy(
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = SubtleLightGray
        )

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("expression_preview_chips_row"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = EmuFacialExpression.entries,
                key = { it.name }
            ) { expression ->
                val isSelected = expression == selectedExpression
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectExpression(expression) },
                    label = {
                        Text(
                            text = expression.label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = DeepCharcoal,
                        labelColor = SubtleLightGray,
                        selectedContainerColor = PureWhite,
                        selectedLabelColor = PureAmoledBlack
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = HairlineCharcoal,
                        selectedBorderColor = PureWhite,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp
                    ),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("expression_chip_${expression.name.lowercase()}")
                )
            }
        }
    }
}

@Composable
private fun AnimationStyleCard(
    style: EmuAnimationStyle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) ElevatedCharcoal else DeepCharcoal,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) PureWhite else HairlineCharcoal
        ),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .testTag("animation_style_${style.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = style.displayName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = PureWhite
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSelected) PureWhite.copy(alpha = 0.14f) else PureAmoledBlack,
                        border = BorderStroke(1.dp, HairlineCharcoal)
                    ) {
                        Text(
                            text = style.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSelected) PureWhite else SubtleLightGray,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = style.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SubtleLightGray
                )
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = PureWhite,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun EyeColorSwatchRow(
    presets: List<EmuEyeColorPreset>,
    selectedHex: String,
    onSelect: (EmuEyeColorPreset) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("eye_color_swatch_row"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        presets.forEach { preset ->
            val isSelected = preset.hex.equals(selectedHex, ignoreCase = true)
            val tagSuffix = preset.hex.removePrefix("#").lowercase()

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clip(CircleShape)
                    .clickable(
                        role = Role.RadioButton,
                        onClick = { onSelect(preset) }
                    )
                    .semantics {
                        contentDescription = "${preset.name} eye color"
                    }
                    .testTag("eye_color_swatch_$tagSuffix"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .then(
                            if (isSelected) {
                                Modifier.border(
                                    width = 2.dp,
                                    color = PureWhite,
                                    shape = CircleShape
                                )
                            } else {
                                Modifier.border(
                                    width = 1.dp,
                                    color = HairlineCharcoal,
                                    shape = CircleShape
                                )
                            }
                        )
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(preset.color)
                )
            }
        }
    }
}

@Composable
private fun ClockShadowToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(16.dp),
        color = DeepCharcoal,
        border = BorderStroke(1.dp, HairlineCharcoal),
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .testTag("clock_shadow_toggle_row")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.clock_3d_shadow_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = PureWhite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.clock_3d_shadow_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = SubtleLightGray
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PureAmoledBlack,
                    checkedTrackColor = PureWhite,
                    uncheckedThumbColor = SubtleLightGray,
                    uncheckedTrackColor = PureAmoledBlack,
                    uncheckedBorderColor = HairlineCharcoal
                ),
                modifier = Modifier.testTag("clock_shadow_switch")
            )
        }
    }
}

@Composable
private fun ShakeReactionControlCard(
    enabled: Boolean,
    recoveryDurationSec: Float,
    onEnabledChange: (Boolean) -> Unit,
    onRecoveryDurationChange: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DeepCharcoal,
        border = BorderStroke(1.dp, HairlineCharcoal),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("shake_reaction_control_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onEnabledChange(!enabled) }
                    .testTag("shake_reaction_toggle_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.shake_reaction_title),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = PureWhite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.shake_reaction_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = SubtleLightGray
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PureAmoledBlack,
                        checkedTrackColor = PureWhite,
                        uncheckedThumbColor = SubtleLightGray,
                        uncheckedTrackColor = PureAmoledBlack,
                        uncheckedBorderColor = HairlineCharcoal
                    ),
                    modifier = Modifier.testTag("shake_reaction_switch")
                )
            }

            AnimatedVisibility(visible = enabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val formattedDuration = String.format(
                        Locale.getDefault(),
                        "%.1fs",
                        recoveryDurationSec.coerceIn(
                            LauncherConfig.MIN_SHAKE_RECOVERY_DURATION_SEC,
                            LauncherConfig.MAX_SHAKE_RECOVERY_DURATION_SEC
                        )
                    )
                    Text(
                        text = stringResource(
                            R.string.shake_recovery_duration_label,
                            formattedDuration
                        ),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = PureWhite,
                        modifier = Modifier.testTag("shake_recovery_duration_label")
                    )
                    Slider(
                        value = recoveryDurationSec.coerceIn(
                            LauncherConfig.MIN_SHAKE_RECOVERY_DURATION_SEC,
                            LauncherConfig.MAX_SHAKE_RECOVERY_DURATION_SEC
                        ),
                        onValueChange = onRecoveryDurationChange,
                        valueRange = LauncherConfig.MIN_SHAKE_RECOVERY_DURATION_SEC..
                            LauncherConfig.MAX_SHAKE_RECOVERY_DURATION_SEC,
                        colors = SliderDefaults.colors(
                            thumbColor = PureWhite,
                            activeTrackColor = PureWhite,
                            inactiveTrackColor = HairlineCharcoal
                        ),
                        modifier = Modifier.testTag("shake_recovery_duration_slider")
                    )
                }
            }
        }
    }
}

@Composable
private fun SoundEffectsControlCard(
    isHomeSoundEnabled: Boolean,
    isLockScreenSoundEnabled: Boolean,
    isEyeFollowSoundEnabled: Boolean,
    onHomeSoundChange: (Boolean) -> Unit,
    onLockScreenSoundChange: (Boolean) -> Unit,
    onEyeFollowSoundChange: (Boolean) -> Unit,
    onTestSoundClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DeepCharcoal,
        border = BorderStroke(1.dp, HairlineCharcoal),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sound_effects_control_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.sound_effects_section).uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 1.2.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = SubtleLightGray
                )

                OutlinedButton(
                    onClick = onTestSoundClick,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, HairlineCharcoal),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = ElevatedCharcoal,
                        contentColor = PureWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("test_sound_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.test_sound),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }

            SoundSwitchRow(
                title = stringResource(R.string.home_sound_title),
                subtitle = stringResource(R.string.home_sound_subtitle),
                checked = isHomeSoundEnabled,
                onCheckedChange = onHomeSoundChange,
                rowTag = "home_sound_toggle_row",
                switchTag = "home_sound_switch"
            )

            SoundSwitchRow(
                title = stringResource(R.string.lock_screen_sound_title),
                subtitle = stringResource(R.string.lock_screen_sound_subtitle),
                checked = isLockScreenSoundEnabled,
                onCheckedChange = onLockScreenSoundChange,
                rowTag = "lock_screen_sound_toggle_row",
                switchTag = "lock_screen_sound_switch"
            )

            SoundSwitchRow(
                title = stringResource(R.string.eye_follow_sound_title),
                subtitle = stringResource(R.string.eye_follow_sound_subtitle),
                checked = isEyeFollowSoundEnabled,
                onCheckedChange = onEyeFollowSoundChange,
                rowTag = "eye_follow_sound_toggle_row",
                switchTag = "eye_follow_sound_switch"
            )
        }
    }
}

@Composable
private fun SoundSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    rowTag: String,
    switchTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp)
            .testTag(rowTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = PureWhite
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = SubtleLightGray
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PureAmoledBlack,
                checkedTrackColor = PureWhite,
                uncheckedThumbColor = SubtleLightGray,
                uncheckedTrackColor = PureAmoledBlack,
                uncheckedBorderColor = HairlineCharcoal
            ),
            modifier = Modifier.testTag(switchTag)
        )
    }
}
