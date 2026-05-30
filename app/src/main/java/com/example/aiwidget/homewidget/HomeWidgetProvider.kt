package com.example.aiwidget.homewidget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.example.aiwidget.MainActivity
import com.example.aiwidget.R
import com.example.aiwidget.data.AppPrefs
import com.example.aiwidget.data.WidgetDisplayState
import com.example.aiwidget.data.WidgetTaskStore
import com.example.aiwidget.util.AppLog

/**
 * 桌面 AppWidget 生命周期：首添、更新、手动 ↻、翻页、移除。
 */
class HomeWidgetProvider : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        AppPrefs(context).getOrCreateUserId()
        HomeWidgetCoordinator.scheduleEnabledWidgetTasks(context, showScheduleToast = true)
        HomeWidgetCoordinator.enqueueInitialRefreshForAllEnabledTasks(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        HomeWidgetCoordinator.renderAllWidgets(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val displayState = WidgetDisplayState(context)
        appWidgetIds.forEach { displayState.remove(it) }
        super.onDeleted(context, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        AppLog.d(TAG, "onReceive action=$action extras=${intent.extras?.keySet()}")
        when (action) {
            HomeWidgetCoordinator.ACTION_MANUAL_REFRESH -> {
                val taskId = intent.getStringExtra(HomeWidgetCoordinator.EXTRA_TASK_ID)
                AppLog.i(TAG, "手动刷新 task=$taskId")
                Toast.makeText(context, context.getString(R.string.widget_refresh_started), Toast.LENGTH_SHORT)
                    .show()
                HomeWidgetCoordinator.enqueueImmediateRefresh(context, taskId, forceRefresh = true)
                return
            }
            HomeWidgetCoordinator.ACTION_PAGE_PREV -> {
                val appWidgetId = intent.getIntExtra(HomeWidgetCoordinator.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    HomeWidgetCoordinator.handlePagePrev(context, appWidgetId)
                }
                return
            }
            HomeWidgetCoordinator.ACTION_PAGE_NEXT -> {
                val appWidgetId = intent.getIntExtra(HomeWidgetCoordinator.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                    HomeWidgetCoordinator.handlePageNext(context, appWidgetId)
                }
                return
            }
            HomeWidgetCoordinator.ACTION_OPEN_FROM_WIDGET -> {
                val taskId = intent.getStringExtra(HomeWidgetCoordinator.EXTRA_TASK_ID)
                AppLog.i(TAG, "打开 App task=$taskId")
                val open =
                    Intent(context, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_FROM_WIDGET, true)
                        putExtra(MainActivity.EXTRA_WIDGET_TASK_ID, taskId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                context.startActivity(open)
                return
            }
        }
        super.onReceive(context, intent)
    }

    override fun onDisabled(context: Context) {
        HomeWidgetCoordinator.cancelAllWidgetTaskSchedules(context)
        super.onDisabled(context)
    }

    companion object {
        private const val TAG = "HomeWidgetProvider"
    }
}
