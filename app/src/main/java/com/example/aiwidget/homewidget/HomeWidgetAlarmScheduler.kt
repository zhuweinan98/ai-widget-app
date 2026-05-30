package com.example.aiwidget.homewidget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.aiwidget.data.WidgetTask
import com.example.aiwidget.data.WidgetTaskStore
import com.example.aiwidget.util.AppLog

/** 用 [AlarmManager] 精确唤醒 Widget 定时刷新。 */
object HomeWidgetAlarmScheduler {
    private const val TAG = "HomeWidgetAlarmScheduler"

    const val ACTION_ALARM = "com.example.aiwidget.action.WIDGET_REFRESH_ALARM"

    fun scheduleEnabledTasks(context: Context) {
        val appContext = context.applicationContext
        val store = WidgetTaskStore(appContext)
        store.loadTasks().forEach { task ->
            if (task.enabled) {
                scheduleNext(appContext, task)
            } else {
                cancel(appContext, task.id)
            }
        }
    }

    /** 在 [delayMinutes] 后触发下一次刷新；默认用任务配置的 interval。 */
    fun scheduleNext(
        context: Context,
        task: WidgetTask,
        delayMinutes: Long? = null,
    ) {
        val appContext = context.applicationContext
        if (!task.enabled) {
            cancel(appContext, task.id)
            return
        }
        val minutes =
            delayMinutes ?: WidgetTaskStore(appContext).intervalMinutes(task).coerceAtLeast(1L)
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = alarmPendingIntent(appContext, task.id)

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                    canUseExactAlarms(appContext) ->
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ->
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
                else ->
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent,
                    )
            }
            AppLog.i(TAG, "已登记闹钟 task=${task.id} ${minutes}min 后触发")
        } catch (e: SecurityException) {
            AppLog.e(TAG, "精确闹钟失败，降级 task=${task.id}", e)
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent,
            )
        }
    }

    fun cancel(context: Context, taskId: String) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(alarmPendingIntent(appContext, taskId))
    }

    fun cancelAll(context: Context) {
        WidgetTaskStore(context.applicationContext).loadTasks().forEach { task ->
            cancel(context, task.id)
        }
    }

    fun canUseExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return true
        return alarmManager.canScheduleExactAlarms()
    }

    private fun alarmPendingIntent(context: Context, taskId: String): PendingIntent {
        val intent =
            Intent(context, HomeWidgetAlarmReceiver::class.java).apply {
                action = ACTION_ALARM
                setPackage(context.packageName)
                putExtra(HomeWidgetCoordinator.EXTRA_TASK_ID, taskId)
            }
        return PendingIntent.getBroadcast(
            context,
            alarmRequestCode(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun alarmRequestCode(taskId: String): Int = taskId.hashCode()
}
