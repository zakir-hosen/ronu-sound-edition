package com.example.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.R
import com.example.model.EmuAnimationStyle
import com.example.model.EmuEyeColorPresets
import com.example.model.LauncherAlignment
import com.example.model.LauncherConfig
import com.example.model.LauncherConfigManager
import com.example.sensor.rememberBatteryStatus
import com.example.sensor.rememberDeviceTilt
import com.example.sensor.rememberShakeTracker
import com.example.ui.components.EmuEyeClock
import com.example.ui.components.EmuFacialExpression
import com.example.ui.theme.PureAmoledBlack
import com.example.ui.theme.PureWhite
import com.example.ui.theme.StillLauncherTheme
import com.example.ui.theme.SubtleLightGray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Unkillable AccessibilityService that:
 * 1. Hosts the Jetpack Compose [EmuEyeClock] lock screen directly inside a
 *    [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] window so Android 14/15
 *    (including Nothing OS) renders it above the secure keyguard without background kills.
 * 2. Performs global double-tap screen locking ([GLOBAL_ACTION_LOCK_SCREEN]) on Android 9+.
 */
class LockScreenAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var serviceScope: CoroutineScope? = null
    private var configJob: Job? = null
    private var configManager: LauncherConfigManager? = null

    private val _configState = MutableStateFlow(LauncherConfig())
    private var hasLoadedConfig = false

    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null
    private var overlayLifecycleOwner: OverlayLifecycleOwner? = null
    private var isReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handleScreenBroadcast(intent.action)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        initializeOverlaySession()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally no-op: window content retrieval is disabled for privacy & battery efficiency
    }

    override fun onInterrupt() {
        // No-op
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardownOverlaySession()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardownOverlaySession()
        super.onDestroy()
    }

    @VisibleForTesting
    internal fun initializeOverlaySession() {
        instance = this
        windowManager = getSystemService(WindowManager::class.java)
        if (configManager == null) {
            configManager = LauncherConfigManager(applicationContext)
        }

        if (serviceScope == null) {
            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        }

        configJob?.cancel()
        configJob = serviceScope?.launch {
            configManager?.configFlow?.collect { config ->
                _configState.value = config
                hasLoadedConfig = true
                if (!config.isLiveLockEnabled && overlayView != null) {
                    removeLockScreenOverlay()
                }
            }
        }

        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        }
    }

    @VisibleForTesting
    internal fun teardownOverlaySession() {
        removeLockScreenOverlay()
        if (isReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            isReceiverRegistered = false
        }
        configJob?.cancel()
        configJob = null
        serviceScope?.cancel()
        serviceScope = null
        if (instance === this) {
            instance = null
        }
    }

    @VisibleForTesting
    internal fun handleScreenBroadcast(action: String?) {
        when (action) {
            Intent.ACTION_SCREEN_OFF -> {
                if (hasLoadedConfig) {
                    if (_configState.value.isLiveLockEnabled) {
                        showLockScreenOverlay()
                    }
                } else {
                    serviceScope?.launch {
                        val loaded = runCatching {
                            configManager?.configFlow?.first()
                        }.getOrNull() ?: _configState.value
                        _configState.value = loaded
                        hasLoadedConfig = true
                        if (loaded.isLiveLockEnabled) {
                            showLockScreenOverlay()
                        }
                    }
                }
            }

            Intent.ACTION_SCREEN_ON -> {
                val keyguardLocked = getSystemService(KeyguardManager::class.java)?.isKeyguardLocked == true
                if (_configState.value.isLiveLockEnabled && keyguardLocked && overlayView == null) {
                    showLockScreenOverlay()
                }
            }

            Intent.ACTION_USER_PRESENT -> {
                removeLockScreenOverlay()
            }
        }
    }

    @VisibleForTesting
    internal fun showLockScreenOverlay() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { showLockScreenOverlay() }
            return
        }

        if (overlayView != null) return
        val wm = windowManager ?: getSystemService(WindowManager::class.java).also { windowManager = it }
            ?: return

        val lifecycleOwner = OverlayLifecycleOwner().apply {
            onCreate()
            onStartAndResume()
        }

        val themedContext = ContextThemeWrapper(this, R.style.Theme_MyApplication)
        val composeView = ComposeView(themedContext).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setContent {
                val config by _configState.collectAsStateWithLifecycle()
                StillLauncherTheme(amoledBlack = true) {
                    AccessibilityEmuLockOverlay(
                        config = config,
                        onSwipeUpToUnlock = { removeLockScreenOverlay() }
                    )
                }
            }
        }

        val params = createOverlayLayoutParams()
        val added = runCatching {
            wm.addView(composeView, params)
            true
        }.getOrDefault(false)

        if (added) {
            overlayLifecycleOwner = lifecycleOwner
            overlayView = composeView
            _isOverlayShowing.value = true
        } else {
            lifecycleOwner.onDestroy()
        }
    }

    @VisibleForTesting
    internal fun removeLockScreenOverlay() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { removeLockScreenOverlay() }
            return
        }

        val view = overlayView ?: return
        val owner = overlayLifecycleOwner
        overlayView = null
        overlayLifecycleOwner = null
        _isOverlayShowing.value = false

        runCatching {
            windowManager?.removeView(view)
        }
        owner?.onDestroy()
    }

    @VisibleForTesting
    internal fun currentOverlayView(): View? = overlayView

    @VisibleForTesting
    internal fun isReceiverRegisteredForTest(): Boolean = isReceiverRegistered

    @VisibleForTesting
    internal fun updateConfigForTest(config: LauncherConfig) {
        _configState.value = config
        hasLoadedConfig = true
    }

    @Suppress("DEPRECATION")
    private fun createOverlayLayoutParams(): WindowManager.LayoutParams {
        val flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    fun performLockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
        } else {
            false
        }
    }

    private class OverlayLifecycleOwner :
        LifecycleOwner,
        SavedStateRegistryOwner,
        ViewModelStoreOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        override val viewModelStore: ViewModelStore = ViewModelStore()

        override val lifecycle: Lifecycle
            get() = lifecycleRegistry

        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry

        fun onCreate() {
            savedStateController.performAttach()
            savedStateController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        }

        fun onStartAndResume() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onDestroy() {
            if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            }
            if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            }
            viewModelStore.clear()
        }
    }

    companion object {
        @Volatile
        private var instance: LockScreenAccessibilityService? = null

        private val _isOverlayShowing = MutableStateFlow(false)
        val isOverlayShowing: StateFlow<Boolean> = _isOverlayShowing.asStateFlow()

        fun isEnabled(context: Context): Boolean {
            if (instance != null) return true

            val accessibilityEnabled = runCatching {
                Settings.Secure.getInt(
                    context.contentResolver,
                    Settings.Secure.ACCESSIBILITY_ENABLED,
                    0
                )
            }.getOrDefault(0) == 1

            if (!accessibilityEnabled) return false

            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()

            val expectedName = ComponentName(
                context,
                LockScreenAccessibilityService::class.java
            ).flattenToString()

            return enabledServices
                .split(':')
                .any { it.equals(expectedName, ignoreCase = true) }
        }

        fun showOverlayNow(): Boolean {
            val service = instance ?: return false
            service.showLockScreenOverlay()
            return true
        }

        fun dismissOverlayNow() {
            instance?.removeLockScreenOverlay()
        }

        fun lockScreen(context: Context): Boolean {
            val service = instance ?: return false
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                service.performLockScreen()
            } else {
                false
            }
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
        }
    }
}

@Composable
internal fun AccessibilityEmuLockOverlay(
    config: LauncherConfig,
    onSwipeUpToUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val effectiveSensitivity = config.clockSensitivity.coerceAtLeast(0.8f)
    val tilt by rememberDeviceTilt(enabled = config.clockSensitivity > 0f)
    val batteryStatus by rememberBatteryStatus()
    val shakeState by rememberShakeTracker(enabled = config.isShakeReactionEnabled)
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PureAmoledBlack)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragAccumulator = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        dragAccumulator += dragAmount
                        if (dragAccumulator < -110f) {
                            dragAccumulator = 0f
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSwipeUpToUnlock()
                        }
                    }
                )
            }
            .testTag("accessibility_emu_overlay_root")
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            EmuEyeClock(
                tiltPitch = tilt.first * effectiveSensitivity,
                tiltRoll = tilt.second * effectiveSensitivity,
                batteryStatus = batteryStatus,
                alignment = LauncherAlignment.CENTER,
                animationStyle = config.emuAnimationStyle,
                eyeColor = EmuEyeColorPresets.parseHexColor(config.eyeColorHex),
                showShadow = config.isClockShadowEnabled,
                selectedEmotion = EmuFacialExpression.fromName(config.selectedEmotionName),
                isActivelyShaking = shakeState.isActivelyShaking,
                shakeIntensity = shakeState.shakeIntensity,
                isShakeReactionEnabled = config.isShakeReactionEnabled,
                shakeRecoveryDurationSec = config.shakeRecoveryDurationSec,
                isSoundEnabled = config.isLockScreenSoundEnabled,
                isEyeFollowSoundEnabled = config.isEyeFollowSoundEnabled
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .testTag("accessibility_overlay_unlock_hint"),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSwipeUpToUnlock()
                },
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .testTag("accessibility_overlay_unlock_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Fingerprint,
                    contentDescription = stringResource(R.string.unlock_with_fingerprint_cd),
                    tint = PureWhite.copy(alpha = 0.8f),
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = null,
                    tint = SubtleLightGray.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.swipe_up_to_unlock),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        letterSpacing = 0.4.sp
                    ),
                    color = SubtleLightGray.copy(alpha = 0.72f)
                )
            }
        }
    }
}

