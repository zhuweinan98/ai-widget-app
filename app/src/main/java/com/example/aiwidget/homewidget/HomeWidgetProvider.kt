package com.example.aiwidget.homewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.Toast
import com.example.aiwidget.R
import com.example.aiwidget.data.AppPrefs
import com.example.aiwidget.data.WidgetTaskStore

/**
 * 桌面 AppWidget 生命周期：首添、更新、手动 ↻、移除。
 *
 * 展示内容来自 [WidgetCache]；刷新逻辑在 [HomeWidgetRefreshWorker]。
 */
class HomeWidgetProvider : AppWidgetProvider() {

    /** 用户首次把 Widget 拖到桌面。 */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetTaskStore(context).seedDefaultsIfEmpty()
        AppPrefs(context).getOrCreateUserId()
        HomeWidgetCoordinator.scheduleEnabledWidgetTasks(context, showScheduleToast = true)
        HomeWidgetCoordinator.enqueueImmediateRefresh(context, forceRefresh = true)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        HomeWidgetCoordinator.scheduleEnabledWidgetTasks(context)
        HomeWidgetCoordinator.renderAllWidgets(context)
    }

    override fun onReceive(context: Context, intent: android.content.Intent) {
        super.onReceive(context, intent)
        if (intent.action == HomeWidgetCoordinator.ACTION_MANUAL_REFRESH) {
            Toast.makeText(context, context.getString(R.string.widget_refresh_started), Toast.LENGTH_SHORT)
                .show()
            HomeWidgetCoordinator.enqueueImmediateRefresh(context, forceRefresh = true)
        }
    }

    /** 最后一个 Widget 实例被移除。 */
    override fun onDisabled(context: Context) {
        HomeWidgetCoordinator.cancelAllWidgetTaskSchedules(context)
        super.onDisabled(context)
    }
}
