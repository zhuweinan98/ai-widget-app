package com.example.aiwidget.homewidget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 开机完成后，若桌面仍有 Widget，重新登记 [HomeWidgetCoordinator.scheduleEnabledWidgetTasks]。 */
class HomeWidgetBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            HomeWidgetCoordinator.scheduleEnabledWidgetTasks(context.applicationContext)
        }
    }
}
