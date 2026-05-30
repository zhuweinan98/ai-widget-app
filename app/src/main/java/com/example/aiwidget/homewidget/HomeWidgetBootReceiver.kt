package com.example.aiwidget.homewidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机完成后重新登记闹钟，并触发一次全量刷新。 */
class HomeWidgetBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val appContext = context.applicationContext
        if (!HomeWidgetCoordinator.hasAppWidgets(appContext)) return
        HomeWidgetCoordinator.scheduleEnabledWidgetTasks(appContext)
        HomeWidgetCoordinator.enqueueInitialRefreshForAllEnabledTasks(appContext)
    }
}
