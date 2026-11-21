package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.SurfaceMode
import com.example.ui.LauncherSettingsSheet
import com.example.ui.LockScreenAnimationsScreen
import com.example.ui.MinimalHomeScreen
import com.example.ui.TextAppDrawer
import com.example.ui.components.MindfulPauseDialog
import com.example.ui.theme.StillLauncherTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            BackHandler(
                enabled = uiState.isDefaultLauncher &&
                    !uiState.isDrawerOpen &&
                    !uiState.isSettingsOpen &&
                    !uiState.isAnimationsOpen &&
                    uiState.pendingMindfulApp == null
            ) {
                // Intentionally no-op on root Home screen
            }

            BackHandler(enabled = uiState.isDrawerOpen) {
                viewModel.closeDrawer()
            }

            BackHandler(enabled = uiState.isSettingsOpen) {
                viewModel.closeSettings()
            }

            BackHandler(enabled = uiState.isAnimationsOpen) {
                viewModel.closeAnimations()
            }

            StillLauncherTheme(
                amoledBlack = uiState.preferences.surfaceMode == SurfaceMode.AMOLED_BLACK
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    MinimalHomeScreen(
                        pinnedApps = uiState.pinnedApps,
                        surfaceMode = uiState.preferences.surfaceMode,
                        config = uiState.config,
                        showTiltDebugOverlay = uiState.preferences.showTiltDebugOverlay,
                        onClockSensitivityChange = viewModel::setClockSensitivity,
                        onMenuSensitivityChange = viewModel::setMenuSensitivity,
                        onToggleTiltDebugOverlay = viewModel::setShowTiltDebugOverlay,
                        onAppClick = viewModel::onAppClicked,
                        onOpenDrawer = viewModel::openDrawer,
                        onOpenSettings = viewModel::openSettings
                    )

                    AnimatedVisibility(
                        visible = uiState.isDrawerOpen,
                        enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 5 },
                        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 5 }
                    ) {
                        TextAppDrawer(
                            allApps = uiState.allApps,
                            pinnedPackages = uiState.preferences.pinnedPackageNames,
                            customPausedPackages = uiState.preferences.customPausedPackages,
                            searchQuery = uiState.searchQuery,
                            onSearchQueryChange = viewModel::onSearchQueryChanged,
                            onAppClick = viewModel::onAppClicked,
                            onTogglePin = viewModel::togglePinnedApp,
                            onToggleCustomPause = viewModel::toggleCustomPausedPackage,
                            onOpenAppInfo = viewModel::openAppInfo,
                            onClose = viewModel::closeDrawer
                        )
                    }

                    if (uiState.isSettingsOpen) {
                        LauncherSettingsSheet(
                            allApps = uiState.allApps,
                            preferences = uiState.preferences,
                            config = uiState.config,
                            isDefaultLauncher = uiState.isDefaultLauncher,
                            onTogglePinnedApp = viewModel::togglePinnedApp,
                            onSurfaceModeChange = viewModel::setSurfaceMode,
                            onClockSensitivityChange = viewModel::setClockSensitivity,
                            onMenuSensitivityChange = viewModel::setMenuSensitivity,
                            onClockAlignmentChange = viewModel::setClockAlignment,
                            onMenuAlignmentChange = viewModel::setMenuAlignment,
                            onResetToDefaults = viewModel::resetConfigToDefaults,
                            onShowTiltDebugOverlayChange = viewModel::setShowTiltDebugOverlay,
                            onMindfulPauseChange = viewModel::setMindfulPauseEnabled,
                            onOpenAnimations = viewModel::openAnimations,
                            onOpenLockScreenMode = {
                                viewModel.closeSettings()
                                startActivity(Intent(this@MainActivity, CustomLockScreenActivity::class.java))
                            },
                            onOpenDefaultLauncherSettings = viewModel::openDefaultHomeSettings,
                            onDismiss = viewModel::closeSettings,
                            onClockShadowEnabledChange = viewModel::setClockShadowEnabled,
                            onHomeSoundEnabledChange = viewModel::setHomeSoundEnabled,
                            onLockScreenSoundEnabledChange = viewModel::setLockScreenSoundEnabled
                        )
                    }

                    AnimatedVisibility(
                        visible = uiState.isAnimationsOpen,
                        enter = fadeIn(tween(180)) + slideInVertically(tween(240)) { it / 6 },
                        exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 6 }
                    ) {
                        LockScreenAnimationsScreen(
                            selectedStyle = uiState.config.emuAnimationStyle,
                            clockSensitivity = uiState.config.clockSensitivity,
                            selectedEyeColorHex = uiState.config.eyeColorHex,
                            isClockShadowEnabled = uiState.config.isClockShadowEnabled,
                            isLiveLockEnabled = uiState.config.isLiveLockEnabled,
                            selectedEmotionName = uiState.config.selectedEmotionName,
                            isShakeReactionEnabled = uiState.config.isShakeReactionEnabled,
                            shakeRecoveryDurationSec = uiState.config.shakeRecoveryDurationSec,
                            isHomeSoundEnabled = uiState.config.isHomeSoundEnabled,
                            isLockScreenSoundEnabled = uiState.config.isLockScreenSoundEnabled,
                            onSelectStyle = viewModel::setEmuAnimationStyle,
                            onSelectEyeColor = viewModel::setEyeColorHex,
                            onSetClockShadowEnabled = viewModel::setClockShadowEnabled,
                            onSetLiveLockEnabled = viewModel::setLiveLockEnabled,
                            onSelectEmotion = viewModel::setSelectedEmotionName,
                            onSetShakeReactionEnabled = viewModel::setShakeReactionEnabled,
                            onSetShakeRecoveryDurationSec = viewModel::setShakeRecoveryDurationSec,
                            onSetHomeSoundEnabled = viewModel::setHomeSoundEnabled,
                            onSetLockScreenSoundEnabled = viewModel::setLockScreenSoundEnabled,
                            onPreviewLockScreen = {
                                startActivity(Intent(this@MainActivity, CustomLockScreenActivity::class.java))
                            },
                            onBack = viewModel::closeAnimations
                        )
                    }

                    uiState.pendingMindfulApp?.let { mindfulApp ->
                        MindfulPauseDialog(
                            app = mindfulApp,
                            onContinue = viewModel::confirmMindfulLaunch,
                            onDismiss = viewModel::cancelMindfulLaunch
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshInstalledApps()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            viewModel.collapseAllOverlays()
        }
    }
}
