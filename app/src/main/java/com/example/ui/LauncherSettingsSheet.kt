package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LauncherPreferencesRepository
import com.example.model.AppEntry
import com.example.model.LauncherAlignment
import com.example.model.LauncherConfig
import com.example.model.LauncherConfigManager
import com.example.model.LauncherPreferences
import com.example.model.SurfaceMode
import com.example.service.LockScreenAccessibilityService
import com.example.ui.theme.DeepCharcoal
import com.example.ui.theme.PureAmoledBlack
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LauncherSettingsSheet(
    allApps: List<AppEntry>,
    preferences: LauncherPreferences,
    config: LauncherConfig,
    isDefaultLauncher: Boolean,
    onTogglePinnedApp: (String) -> Unit,
    onSurfaceModeChange: (SurfaceMode) -> Unit,
    onClockSensitivityChange: (Float) -> Unit,
    onMenuSensitivityChange: (Float) -> Unit,
    onClockAlignmentChange: (LauncherAlignment) -> Unit,
    onMenuAlignmentChange: (LauncherAlignment) -> Unit,
    onResetToDefaults: () -> Unit,
    onShowTiltDebugOverlayChange: (Boolean) -> Unit,
    onMindfulPauseChange: (Boolean) -> Unit,
    onOpenAnimations: () -> Unit,
    onOpenLockScreenMode: () -> Unit,
    onOpenDefaultLauncherSettings: () -> Unit,
    onDismiss: () -> Unit,
    onClockShadowEnabledChange: (Boolean) -> Unit = {},
    onHomeSoundEnabledChange: (Boolean) -> Unit = {},
    onLockScreenSoundEnabledChange: (Boolean) -> Unit = {},
    onEyeFollowSoundEnabledChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var appFilter by remember { mutableStateOf("") }
    val isLockServiceEnabled = remember(preferences, config) {
        LockScreenAccessibilityService.isEnabled(context)
    }

    val filteredApps = remember(allApps, appFilter) {
        if (appFilter.isBlank()) {
            allApps
        } else {
            allApps.filter { it.label.contains(appFilter.trim(), ignoreCase = true) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepCharcoal.copy(alpha = 0.82f),
        contentColor = MaterialTheme.colorScheme.onBackground,
        scrimColor = PureAmoledBlack.copy(alpha = 0.25f),
        modifier = Modifier.testTag("launcher_settings_sheet")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .testTag("settings_sheet_list"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_title),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.settings_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onResetToDefaults()
                        },
                        modifier = Modifier.testTag("reset_defaults_header_button")
                    ) {
                        Text(
                            text = "Reset",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("settings_done_button")
                    ) {
                        Text(
                            text = stringResource(R.string.done),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Clock Alignment Segmented Selector (Left | Center | Right)
            item {
                SectionLabel(text = stringResource(R.string.clock_alignment_section))
                Spacer(modifier = Modifier.height(8.dp))
                AlignmentSegmentedRow(
                    selected = config.clockAlignment,
                    onSelect = onClockAlignmentChange,
                    tagPrefix = "clock_align"
                )
            }

            // App Menu Alignment Segmented Selector (Left | Center | Right)
            item {
                SectionLabel(text = stringResource(R.string.menu_alignment_section))
                Spacer(modifier = Modifier.height(8.dp))
                AlignmentSegmentedRow(
                    selected = config.menuAlignment,
                    onSelect = onMenuAlignmentChange,
                    tagPrefix = "menu_align"
                )
            }

            // Independent Clock 3D Sensitivity Slider (0.0f - 2.5f)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel(
                        text = stringResource(
                            R.string.clock_sensitivity_section,
                            formatSensitivityLabel(config.clockSensitivity)
                        )
                    )
                    Slider(
                        value = config.clockSensitivity,
                        onValueChange = onClockSensitivityChange,
                        valueRange = LauncherConfigManager.MIN_SENSITIVITY..LauncherConfigManager.MAX_SENSITIVITY,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("settings_clock_sensitivity_slider")
                    )
                }
            }

            // Independent App Menu 3D Sensitivity Slider (0.0f - 2.5f)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SectionLabel(
                        text = stringResource(
                            R.string.menu_sensitivity_section,
                            formatSensitivityLabel(config.menuSensitivity)
                        )
                    )
                    Slider(
                        value = config.menuSensitivity,
                        onValueChange = onMenuSensitivityChange,
                        valueRange = LauncherConfigManager.MIN_SENSITIVITY..LauncherConfigManager.MAX_SENSITIVITY,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("settings_menu_sensitivity_slider")
                    )
                }
            }

            // Background Surface Mode
            item {
                SectionLabel(text = stringResource(R.string.background_surface_section))
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SelectablePillOption(
                        label = stringResource(R.string.surface_charcoal),
                        selected = preferences.surfaceMode == SurfaceMode.MATTE_CHARCOAL,
                        onClick = { onSurfaceModeChange(SurfaceMode.MATTE_CHARCOAL) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("surface_charcoal_option")
                    )
                    SelectablePillOption(
                        label = stringResource(R.string.surface_amoled),
                        selected = preferences.surfaceMode == SurfaceMode.AMOLED_BLACK,
                        onClick = { onSurfaceModeChange(SurfaceMode.AMOLED_BLACK) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("surface_amoled_option")
                    )
                }
            }

            // Show Live Tilt Test Controls Toggle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onShowTiltDebugOverlayChange(!preferences.showTiltDebugOverlay)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.tilt_debug_toggle),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.tilt_debug_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = preferences.showTiltDebugOverlay,
                        onCheckedChange = onShowTiltDebugOverlayChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            }

            // Clock 3D Shadow / Glow Toggle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onClockShadowEnabledChange(!config.isClockShadowEnabled) }
                        .padding(vertical = 4.dp)
                        .testTag("settings_clock_shadow_row"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.clock_3d_shadow_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.clock_3d_shadow_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = config.isClockShadowEnabled,
                        onCheckedChange = onClockShadowEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier.testTag("settings_clock_shadow_switch")
                    )
                }
            }

            // Sound Effects Section (Home Screen, Lock Screen, and Eye-Follow Micro-Tick)
            item {
                val homeSoundChecked by rememberUpdatedState(config.isHomeSoundEnabled)
                val lockSoundChecked by rememberUpdatedState(config.isLockScreenSoundEnabled)
                val followSoundChecked by rememberUpdatedState(config.isEyeFollowSoundEnabled)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_sound_effects_section"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionLabel(text = stringResource(R.string.sound_effects_section))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onHomeSoundEnabledChange(!homeSoundChecked) }
                            .padding(vertical = 4.dp)
                            .testTag("settings_home_sound_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.home_sound_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.home_sound_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = homeSoundChecked,
                            onCheckedChange = onHomeSoundEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier.testTag("settings_home_sound_switch")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onLockScreenSoundEnabledChange(!lockSoundChecked)
                            }
                            .padding(vertical = 4.dp)
                            .testTag("settings_lock_screen_sound_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.lock_screen_sound_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.lock_screen_sound_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = lockSoundChecked,
                            onCheckedChange = onLockScreenSoundEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier.testTag("settings_lock_screen_sound_switch")
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                onEyeFollowSoundEnabledChange(!followSoundChecked)
                            }
                            .padding(vertical = 4.dp)
                            .testTag("settings_eye_follow_sound_row"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.eye_follow_sound_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.eye_follow_sound_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Switch(
                            checked = followSoundChecked,
                            onCheckedChange = onEyeFollowSoundEnabledChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.background
                            ),
                            modifier = Modifier.testTag("settings_eye_follow_sound_switch")
                        )
                    }
                }
            }

            // Mindful Pause Toggle
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onMindfulPauseChange(!preferences.mindfulPauseEnabled) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.mindful_pause_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.mindful_pause_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = preferences.mindfulPauseEnabled,
                        onCheckedChange = onMindfulPauseChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier.testTag("mindful_pause_switch")
                    )
                }
            }

            // Lock Screen Animations Page Trigger
            item {
                Surface(
                    onClick = onOpenAnimations,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("lock_screen_animations_setting_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.lock_screen_animations_title),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = config.emuAnimationStyle.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Dedicated Emu Lock Screen Mode Trigger
            item {
                Surface(
                    onClick = onOpenLockScreenMode,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("emu_lock_screen_setting_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.emu_lock_screen_title),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.emu_lock_screen_sub),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Double-Tap to Lock Screen Accessibility Status
            item {
                Surface(
                    onClick = { LockScreenAccessibilityService.openAccessibilitySettings(context) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("double_tap_lock_setting_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.double_tap_lock_setting_title),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (isLockServiceEnabled) {
                                    stringResource(R.string.double_tap_lock_enabled_sub)
                                } else {
                                    stringResource(R.string.double_tap_lock_disabled_sub)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (isLockServiceEnabled) {
                                Icons.Rounded.Check
                            } else {
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Default Launcher Shortcut
            item {
                Surface(
                    onClick = onOpenDefaultLauncherSettings,
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("default_launcher_button")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.set_default_launcher),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = if (isDefaultLauncher) {
                                    "Active default Home app"
                                } else {
                                    stringResource(R.string.set_default_launcher_sub)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (isDefaultLauncher) {
                                Icons.Rounded.Check
                            } else {
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            }

            // Pinned Home Apps Picker (5-7)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SectionLabel(
                        text = stringResource(
                            R.string.pinned_apps_section,
                            preferences.pinnedPackageNames.size
                        )
                    )
                    Text(
                        text = stringResource(R.string.pinned_apps_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = appFilter,
                        onValueChange = { appFilter = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search_apps_placeholder),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_pin_filter_input")
                    )
                }
            }

            items(
                items = filteredApps,
                key = { it.id }
            ) { app ->
                val isPinned = app.packageName in preferences.pinnedPackageNames
                val canAddMore =
                    preferences.pinnedPackageNames.size < LauncherPreferencesRepository.MAX_PINNED_APPS
                val enabled = isPinned || canAddMore

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled) {
                            onTogglePinnedApp(app.packageName)
                        }
                        .padding(vertical = 4.dp)
                        .testTag("settings_pin_row_${app.packageName}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Checkbox(
                        checked = isPinned,
                        enabled = enabled,
                        onCheckedChange = { onTogglePinnedApp(app.packageName) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }

            // One-click Reset to Default button at bottom as well
            item {
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onResetToDefaults()
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 48.dp)
                        .testTag("reset_defaults_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.reset_to_default),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun AlignmentSegmentedRow(
    selected: LauncherAlignment,
    onSelect: (LauncherAlignment) -> Unit,
    tagPrefix: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        LauncherAlignment.entries.forEach { option ->
            val label = when (option) {
                LauncherAlignment.LEFT -> stringResource(R.string.align_left)
                LauncherAlignment.CENTER -> stringResource(R.string.align_center)
                LauncherAlignment.RIGHT -> stringResource(R.string.align_right)
            }
            SelectablePillOption(
                label = label,
                selected = selected == option,
                onClick = { onSelect(option) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("${tagPrefix}_${option.name.lowercase()}")
            )
        }
    }
}

private fun formatSensitivityLabel(value: Float): String {
    return if (value <= 0.05f) {
        "Off"
    } else {
        String.format(Locale.getDefault(), "%.1fx", value)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun SelectablePillOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.background.copy(alpha = 0.72f)
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        modifier = modifier.defaultMinSize(minHeight = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
