package com.example.aiwidget.homewidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.aiwidget.MainActivity
import com.example.aiwidget.R
import com.example.aiwidget.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 前台服务：在后台/熄屏时稳定执行 Widget API 刷新。
 *
 * 由闹钟或手动 ↻ 触发；运行期间显示通知，完成后自动停止。
 */
class HomeWidgetRefreshService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureNotificationChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: SecurityException) {
            AppLog.e(TAG, "startForeground 失败（通常因未授予通知权限）", e)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val runAllEnabled = intent?.getBooleanExtra(EXTRA_RUN_ALL_ENABLED, false) ?: false
        val taskId = intent?.getStringExtra(HomeWidgetCoordinator.EXTRA_TASK_ID)
        val forceRefresh = intent?.getBooleanExtra(EXTRA_FORCE_REFRESH, false) ?: false
        val trigger =
            intent?.getStringExtra(HomeWidgetCoordinator.EXTRA_TRIGGER)
                ?: HomeWidgetCoordinator.TRIGGER_IMMEDIATE

        serviceScope.launch {
            refreshMutex.withLock {
                try {
                    HomeWidgetRefreshRunner.run(
                        context = applicationContext,
                        taskId = taskId,
                        forceRefresh = forceRefresh,
                        trigger = trigger,
                        runAllEnabled = runAllEnabled,
                    )
                } catch (e: Exception) {
                    AppLog.e(TAG, "刷新服务异常", e)
                } finally {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    stopSelf(startId)
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.widget_refresh_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.widget_refresh_channel_desc)
            }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_refresh)
            .setContentTitle(getString(R.string.widget_refresh_notification_title))
            .setContentText(getString(R.string.widget_refresh_notification_text))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "HomeWidgetRefreshService"
        private const val CHANNEL_ID = "widget_refresh"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_FORCE_REFRESH = "force_refresh"
        const val EXTRA_RUN_ALL_ENABLED = "run_all_enabled"

        private val refreshMutex = Mutex()
    }
}
