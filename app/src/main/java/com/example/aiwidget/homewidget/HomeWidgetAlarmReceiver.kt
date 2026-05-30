package com.example.aiwidget.homewidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.aiwidget.util.AppLog

/** [AlarmManager] 到点唤醒 → 启动 [HomeWidgetRefreshService] 执行刷新。 */
class HomeWidgetAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != HomeWidgetAlarmScheduler.ACTION_ALARM) return
        val taskId = intent.getStringExtra(HomeWidgetCoordinator.EXTRA_TASK_ID)
        AppLog.i(TAG, "闹钟触发 task=$taskId")
        HomeWidgetCoordinator.startRefreshService(
            context = context,
            taskId = taskId,
            forceRefresh = false,
            trigger = HomeWidgetCoordinator.TRIGGER_PERIODIC,
        )
    }

    companion object {
        private const val TAG = "HomeWidgetAlarmReceiver"
    }
}
