package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.CustomLockScreenActivity
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that listens for [Intent.ACTION_SCREEN_OFF] and posts a high-priority
 * [NotificationCompat.CATEGORY_ALARM] full-screen-intent notification so Android 14/15 (Nothing OS)
 * launches [CustomLockScreenActivity] directly over the secure Keyguard without Background Activity
 * Launch restrictions.
 */
class LockScreenService : Service() {

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (!LockScreenAccessibilityService.isEnabled(context)) {
                        triggerFullScreenLockScreen()
                    }
                }
                Intent.ACTION_USER_PRESENT -> cancelFullScreenTriggerNotification(context)
            }
        }
    }

    private var isReceiverRegistered = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        isReceiverRegistered = true
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                cancelFullScreenTriggerNotification(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRIGGER_FULL_SCREEN -> {
                triggerFullScreenLockScreen()
            }
        }

        val notification = buildForegroundServiceNotification()
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, fgsType)
        _isRunning.value = true
        return START_STICKY
    }

    override fun onDestroy() {
        cancelFullScreenTriggerNotification(this)
        if (isReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            isReceiverRegistered = false
        }
        _isRunning.value = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun triggerFullScreenLockScreen() {
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return

        val lockScreenIntent = Intent(this, CustomLockScreenActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            )
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            FULL_SCREEN_REQUEST_CODE,
            lockScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val fsiNotification = NotificationCompat.Builder(this, FULL_SCREEN_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.emu_lock_screen_title))
            .setContentText(getString(R.string.swipe_up_to_unlock))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setSilent(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        notificationManager.cancel(FULL_SCREEN_NOTIFICATION_ID)
        runCatching {
            notificationManager.notify(FULL_SCREEN_NOTIFICATION_ID, fsiNotification)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.lock_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.lock_service_channel_desc)
                setShowBadge(false)
            }
            manager.createNotificationChannel(serviceChannel)

            val fullScreenChannel = NotificationChannel(
                FULL_SCREEN_CHANNEL_ID,
                getString(R.string.lock_service_fsi_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.lock_service_fsi_channel_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(fullScreenChannel)
        }
    }

    private fun buildForegroundServiceNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val previewLockIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, CustomLockScreenActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.lock_service_notif_title))
            .setContentText(getString(R.string.lock_service_notif_text))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.preview_lock_screen), previewLockIntent)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "emu_lock_screen_channel"
        const val FULL_SCREEN_CHANNEL_ID = "emu_lock_screen_fsi_channel"
        private const val NOTIFICATION_ID = 2026
        const val FULL_SCREEN_NOTIFICATION_ID = 2027
        private const val FULL_SCREEN_REQUEST_CODE = 2028

        const val ACTION_STOP = "com.example.service.LockScreenService.STOP"
        const val ACTION_TRIGGER_FULL_SCREEN =
            "com.example.service.LockScreenService.TRIGGER_FULL_SCREEN"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun cancelFullScreenTriggerNotification(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.cancel(FULL_SCREEN_NOTIFICATION_ID)
        }

        fun start(context: Context) {
            val intent = Intent(context, LockScreenService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LockScreenService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
            cancelFullScreenTriggerNotification(context)
            _isRunning.value = false
        }
    }
}
