package com.example.aiwidget.homewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.aiwidget.R
import com.example.aiwidget.data.WidgetCache
import com.example.aiwidget.data.WidgetDisplayState
import com.example.aiwidget.data.WidgetTask
import com.example.aiwidget.data.WidgetTaskStore
import com.example.aiwidget.util.AppLog

/**
 * 桌面小组件的闹钟调度、前台刷新服务与 RemoteViews 渲染。
 *
 * 定时： [HomeWidgetAlarmScheduler] 精确唤醒 → [HomeWidgetRefreshService]
 * 手动 ↻：直接启动 [HomeWidgetRefreshService]
 */
object HomeWidgetCoordinator {
    private const val TAG = "HomeWidgetCoordinator"

    const val ACTION_MANUAL_REFRESH = "com.example.aiwidget.action.MANUAL_REFRESH"
    const val ACTION_OPEN_FROM_WIDGET = "com.example.aiwidget.action.OPEN_FROM_WIDGET"
    const val ACTION_PAGE_PREV = "com.example.aiwidget.action.PAGE_PREV"
    const val ACTION_PAGE_NEXT = "com.example.aiwidget.action.PAGE_NEXT"

    const val EXTRA_APPWIDGET_ID = "app_widget_id"

    const val EXTRA_TASK_ID = "task_id"
    const val EXTRA_TRIGGER = "trigger"

    const val TRIGGER_PERIODIC = "periodic"
    const val TRIGGER_IMMEDIATE = "immediate"

    fun hasAppWidgets(context: Context): Boolean {
        val ids =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, HomeWidgetProvider::class.java))
        return ids.isNotEmpty()
    }

    fun scheduleEnabledWidgetTasks(context: Context, showScheduleToast: Boolean = false) {
        val appContext = context.applicationContext
        if (!hasAppWidgets(appContext)) {
            AppLog.d(TAG, "无 Widget 实例，跳过定时登记")
            return
        }
        HomeWidgetAlarmScheduler.scheduleEnabledTasks(appContext)
        val enabledCount = WidgetTaskStore(appContext).loadEnabledTasks().size
        AppLog.i(TAG, "已为 $enabledCount 条任务登记闹钟")
        if (showScheduleToast) {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.widget_tasks_scheduled, enabledCount),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** 仅重登记 [taskId] 对应闹钟（保存单条任务时用，不影响其它任务）。 */
    fun scheduleWidgetTask(context: Context, taskId: String) {
        val appContext = context.applicationContext
        if (!hasAppWidgets(appContext)) {
            AppLog.d(TAG, "无 Widget 实例，跳过单任务定时登记 task=$taskId")
            return
        }
        val task = WidgetTaskStore(appContext).findTask(taskId)
        if (task == null) {
            AppLog.w(TAG, "任务不存在，跳过定时登记 task=$taskId")
            return
        }
        if (task.enabled) {
            HomeWidgetAlarmScheduler.scheduleNext(appContext, task)
            AppLog.i(TAG, "已为任务登记闹钟 task=$taskId")
        } else {
            HomeWidgetAlarmScheduler.cancel(appContext, task.id)
            AppLog.i(TAG, "已取消任务闹钟 task=$taskId")
        }
    }

    fun cancelAllWidgetTaskSchedules(context: Context) {
        val appContext = context.applicationContext
        HomeWidgetAlarmScheduler.cancelAll(appContext)
        AppLog.i(TAG, "已取消全部任务定时")
    }

    fun startRefreshService(
        context: Context,
        taskId: String?,
        forceRefresh: Boolean,
        trigger: String = TRIGGER_IMMEDIATE,
        runAllEnabled: Boolean = false,
    ) {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !HomeWidgetSystemPermissions.canPostNotifications(appContext)
        ) {
            AppLog.w(TAG, "未授予通知权限，跳过前台刷新")
            if (trigger == TRIGGER_PERIODIC && taskId != null) {
                WidgetTaskStore(appContext).findTask(taskId)?.let { task ->
                    if (task.enabled) {
                        HomeWidgetAlarmScheduler.scheduleNext(appContext, task)
                    }
                }
            }
            Toast.makeText(
                appContext,
                appContext.getString(R.string.widget_refresh_need_notification),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val intent =
            Intent(appContext, HomeWidgetRefreshService::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(HomeWidgetRefreshService.EXTRA_FORCE_REFRESH, forceRefresh)
                putExtra(EXTRA_TRIGGER, trigger)
                putExtra(HomeWidgetRefreshService.EXTRA_RUN_ALL_ENABLED, runAllEnabled)
            }
        try {
            ContextCompat.startForegroundService(appContext, intent)
            AppLog.i(TAG, "startRefreshService task=$taskId force=$forceRefresh trigger=$trigger")
        } catch (e: Exception) {
            AppLog.e(TAG, "启动刷新服务失败 task=$taskId", e)
        }
    }

    /**
     * 刷新前立即更新桌面：切到 [taskId] 对应页并显示 loading（须在启动 API 前调用）。
     * @return 是否至少有一个 Widget 实例被更新
     */
    fun prepareRefreshUiForTask(context: Context, taskId: String): Boolean {
        val appContext = context.applicationContext
        val tasks = WidgetTaskStore(appContext).loadEnabledTasks()
        val pageIndex = tasks.indexOfFirst { it.id == taskId }
        if (pageIndex < 0) return false
        val task = tasks[pageIndex]
        WidgetCache(appContext).setRefreshing(task.cacheSlot, true)
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, HomeWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return false
        val displayState = WidgetDisplayState(appContext)
        ids.forEach { widgetId ->
            displayState.setPageIndex(widgetId, pageIndex)
            bindWidget(appContext, manager, widgetId)
        }
        return true
    }

    fun enqueueImmediateRefresh(
        context: Context,
        taskId: String?,
        forceRefresh: Boolean,
    ) {
        val store = WidgetTaskStore(context)
        val task =
            taskId?.let { store.findTask(it) }
                ?: store.loadEnabledTasks().firstOrNull()
                ?: return
        startRefreshService(context, task.id, forceRefresh, TRIGGER_IMMEDIATE)
    }

    fun enqueueInitialRefreshForAllEnabledTasks(context: Context) {
        val store = WidgetTaskStore(context)
        if (store.loadEnabledTasks().isEmpty()) return
        startRefreshService(
            context = context,
            taskId = null,
            forceRefresh = true,
            trigger = TRIGGER_IMMEDIATE,
            runAllEnabled = true,
        )
    }

    fun renderAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids =
            manager.getAppWidgetIds(
                ComponentName(context, HomeWidgetProvider::class.java),
            )
        for (id in ids) {
            bindWidget(context, manager, id)
        }
    }

    fun bindWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        val tasks = WidgetTaskStore(context).loadEnabledTasks()
        val displayState = WidgetDisplayState(context)

        if (tasks.isEmpty()) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout_text)
            views.setViewVisibility(R.id.widget_body, View.GONE)
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setViewVisibility(R.id.widget_page_prev, View.GONE)
            views.setViewVisibility(R.id.widget_page_next, View.GONE)
            views.setViewVisibility(R.id.widget_page_label, View.GONE)
            views.setViewVisibility(R.id.widget_refresh, View.GONE)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        val pageIndex = displayState.getPageIndex(appWidgetId, tasks.size)
        val task = tasks[pageIndex]
        val slot = task.cacheSlot
        val cache = WidgetCache(context)
        val refreshing = cache.isRefreshing(slot)
        val hasContent = cache.hasCachedContent(slot)
        val template = cache.getTemplate(slot).orEmpty()
        val layoutRes = HomeWidgetLayoutSelector.layoutRes(template, cache, slot, hasContent)
        val views = RemoteViews(context.packageName, layoutRes)

        views.setViewVisibility(R.id.widget_body, View.VISIBLE)
        views.setViewVisibility(R.id.widget_empty, View.GONE)

        val title = HomeWidgetDisplayFormatter.formatTitle(cache.getTitle(slot) ?: "", task.title)
        val timeLabel =
            when {
                hasContent -> cache.getTimeLabel(slot) ?: "--:--"
                refreshing -> context.getString(R.string.widget_loading)
                else -> "--:--"
            }
        val bodyText =
            when {
                refreshing -> context.getString(R.string.widget_loading)
                !hasContent -> context.getString(R.string.widget_waiting_first_refresh)
                layoutRes == R.layout.widget_layout_text ->
                    cache.getRawContent(slot)?.trim().orEmpty()
                else -> ""
            }
        val hint = context.getString(R.string.widget_swipe_hint)

        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_time, timeLabel)
        views.setTextViewText(R.id.widget_hint, hint)
        HomeWidgetRemoteBinder.bindBody(views, layoutRes, cache, slot, bodyText)
        val showPager = tasks.size > 1
        views.setViewVisibility(
            R.id.widget_page_label,
            if (showPager) View.VISIBLE else View.GONE,
        )
        if (showPager) {
            views.setTextViewText(R.id.widget_page_label, "${pageIndex + 1}/${tasks.size}")
        }
        views.setViewVisibility(
            R.id.widget_loading,
            if (refreshing) View.VISIBLE else View.GONE,
        )
        views.setViewVisibility(
            R.id.widget_refresh,
            if (refreshing) View.INVISIBLE else View.VISIBLE,
        )
        views.setViewVisibility(
            R.id.widget_page_prev,
            if (showPager) View.VISIBLE else View.GONE,
        )
        views.setViewVisibility(
            R.id.widget_page_next,
            if (showPager) View.VISIBLE else View.GONE,
        )

        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            broadcastPendingIntent(
                context,
                requestCode = appWidgetId * 100 + 1,
                action = ACTION_MANUAL_REFRESH,
                appWidgetId = appWidgetId,
                taskId = task.id,
            ),
        )
        views.setOnClickPendingIntent(
            R.id.widget_page_prev,
            broadcastPendingIntent(
                context,
                requestCode = appWidgetId * 100 + 2,
                action = ACTION_PAGE_PREV,
                appWidgetId = appWidgetId,
            ),
        )
        views.setOnClickPendingIntent(
            R.id.widget_page_next,
            broadcastPendingIntent(
                context,
                requestCode = appWidgetId * 100 + 3,
                action = ACTION_PAGE_NEXT,
                appWidgetId = appWidgetId,
            ),
        )
        views.setOnClickPendingIntent(
            R.id.widget_body,
            broadcastPendingIntent(
                context,
                requestCode = appWidgetId * 100 + 4,
                action = ACTION_OPEN_FROM_WIDGET,
                appWidgetId = appWidgetId,
                taskId = task.id,
            ),
        )

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    fun handlePagePrev(context: Context, appWidgetId: Int) {
        val tasks = WidgetTaskStore(context).loadEnabledTasks()
        if (tasks.size <= 1) return
        WidgetDisplayState(context).pagePrev(appWidgetId, tasks.size)
        bindWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
    }

    fun handlePageNext(context: Context, appWidgetId: Int) {
        val tasks = WidgetTaskStore(context).loadEnabledTasks()
        if (tasks.size <= 1) return
        WidgetDisplayState(context).pageNext(appWidgetId, tasks.size)
        bindWidget(context, AppWidgetManager.getInstance(context), appWidgetId)
    }

    private fun broadcastPendingIntent(
        context: Context,
        requestCode: Int,
        action: String,
        appWidgetId: Int,
        taskId: String? = null,
    ): PendingIntent {
        val intent =
            Intent(context, HomeWidgetProvider::class.java).apply {
                this.action = action
                setPackage(context.packageName)
                putExtra(EXTRA_APPWIDGET_ID, appWidgetId)
                taskId?.let { putExtra(EXTRA_TASK_ID, it) }
            }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
