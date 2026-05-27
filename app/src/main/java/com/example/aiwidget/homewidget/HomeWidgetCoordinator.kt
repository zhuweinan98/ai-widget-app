package com.example.aiwidget.homewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.aiwidget.R
import com.example.aiwidget.data.WidgetCache
import com.example.aiwidget.data.WidgetConfig
import com.example.aiwidget.data.WidgetDisplayState
import com.example.aiwidget.data.WidgetTask
import com.example.aiwidget.data.WidgetTaskStore
import com.example.aiwidget.util.AppLog
import java.util.concurrent.TimeUnit

/**
 * 桌面小组件的 WorkManager 调度与 RemoteViews 渲染。
 *
 * 单页 RemoteViews + 上一页/下一页循环切换（兼容 MIUI 等 Launcher）；
 * ↻ 仅刷新当前页对应任务。
 */
object HomeWidgetCoordinator {
    private const val TAG = "HomeWidgetCoordinator"

    const val ACTION_MANUAL_REFRESH = "com.example.aiwidget.action.MANUAL_REFRESH"
    const val ACTION_OPEN_FROM_WIDGET = "com.example.aiwidget.action.OPEN_FROM_WIDGET"
    const val ACTION_PAGE_PREV = "com.example.aiwidget.action.PAGE_PREV"
    const val ACTION_PAGE_NEXT = "com.example.aiwidget.action.PAGE_NEXT"

    const val EXTRA_APPWIDGET_ID = "app_widget_id"

    fun immediateWorkName(taskId: String): String = "widget_immediate_$taskId"

    const val WORK_DATA_TASK_ID = "task_id"
    const val WORK_DATA_TRIGGER = "trigger"

    const val TRIGGER_PERIODIC = "periodic"
    const val TRIGGER_IMMEDIATE = "immediate"

    /** @deprecated 保留旧名，新代码请用 [immediateWorkName]。 */
    const val WORK_IMMEDIATE = "widget_aihot_immediate"

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
        cancelLegacyPeriodicWorks(appContext)
        val store = WidgetTaskStore(appContext)
        val enabledTasks = store.loadEnabledTasks()
        enabledTasks.forEach { scheduleTaskPeriodic(appContext, it) }
        AppLog.i(TAG, "已为 ${enabledTasks.size} 条任务登记定时")
        if (showScheduleToast) {
            Toast.makeText(
                appContext,
                appContext.getString(R.string.widget_tasks_scheduled, enabledTasks.size),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun taskPeriodicWorkName(taskId: String): String = "widget_task_periodic_$taskId"

    fun taskChainWorkName(taskId: String): String = "widget_task_chain_$taskId"

    private fun scheduleTaskPeriodic(context: Context, task: WidgetTask) {
        val wm = WorkManager.getInstance(context)
        val interval = WidgetTaskStore(context).intervalMinutes(task)
        val input = buildWorkerInputData(task.id, forceRefresh = false, trigger = TRIGGER_PERIODIC)
        if (WidgetConfig.usesPeriodicChain(interval)) {
            wm.cancelUniqueWork(taskPeriodicWorkName(task.id))
            val delay = interval.coerceAtLeast(1L)
            val request =
                OneTimeWorkRequestBuilder<HomeWidgetRefreshWorker>()
                    .setInitialDelay(delay, TimeUnit.MINUTES)
                    .setInputData(input)
                    .build()
            wm.enqueueUniqueWork(
                taskChainWorkName(task.id),
                ExistingWorkPolicy.REPLACE,
                request,
            )
            AppLog.i(TAG, "链式定时 task=${task.id} ${delay}min")
        } else {
            wm.cancelUniqueWork(taskChainWorkName(task.id))
            val minutes =
                interval.coerceAtLeast(WidgetConfig.WORK_MANAGER_MIN_PERIODIC_MINUTES)
            val request =
                PeriodicWorkRequestBuilder<HomeWidgetRefreshWorker>(minutes, TimeUnit.MINUTES)
                    .setInputData(input)
                    .build()
            wm.enqueueUniquePeriodicWork(
                taskPeriodicWorkName(task.id),
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
            AppLog.i(TAG, "周期定时 task=${task.id} ${minutes}min")
        }
    }

    fun rescheduleTaskChain(context: Context, task: WidgetTask) {
        if (!WidgetConfig.usesPeriodicChain(WidgetTaskStore(context).intervalMinutes(task))) return
        scheduleTaskPeriodic(context, task)
    }

    fun cancelAllWidgetTaskSchedules(context: Context) {
        val appContext = context.applicationContext
        cancelLegacyPeriodicWorks(appContext)
        WidgetTaskStore(appContext).loadTasks().forEach { task ->
            val wm = WorkManager.getInstance(appContext)
            wm.cancelUniqueWork(taskPeriodicWorkName(task.id))
            wm.cancelUniqueWork(taskChainWorkName(task.id))
            wm.cancelUniqueWork(immediateWorkName(task.id))
        }
        wmCancelLegacyImmediate(appContext)
        AppLog.i(TAG, "已取消全部任务定时")
    }

    private fun wmCancelLegacyImmediate(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_IMMEDIATE)
    }

    private fun cancelLegacyPeriodicWorks(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("widget_aihot_periodic")
        wm.cancelUniqueWork("widget_aihot_periodic_chain")
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
        AppLog.i(TAG, "enqueueImmediateRefresh task=${task.id} force=$forceRefresh")
        val request =
            OneTimeWorkRequestBuilder<HomeWidgetRefreshWorker>()
                .setInputData(buildWorkerInputData(task.id, forceRefresh = forceRefresh))
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            immediateWorkName(task.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun enqueueInitialRefreshForAllEnabledTasks(context: Context) {
        WidgetTaskStore(context).loadEnabledTasks().forEach { task ->
            enqueueImmediateRefresh(context, task.id, forceRefresh = true)
        }
    }

    fun buildWorkerInputData(
        taskId: String,
        forceRefresh: Boolean,
        trigger: String = TRIGGER_IMMEDIATE,
    ): Data =
        workDataOf(
            WORK_DATA_TASK_ID to taskId,
            "force_refresh" to forceRefresh,
            WORK_DATA_TRIGGER to trigger,
        )

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
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        val tasks = WidgetTaskStore(context).loadEnabledTasks()
        val displayState = WidgetDisplayState(context)

        if (tasks.isEmpty()) {
            views.setViewVisibility(R.id.widget_body, View.GONE)
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
            views.setViewVisibility(R.id.widget_page_prev, View.GONE)
            views.setViewVisibility(R.id.widget_page_next, View.GONE)
            views.setViewVisibility(R.id.widget_page_label, View.GONE)
            views.setViewVisibility(R.id.widget_refresh, View.GONE)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            return
        }

        views.setViewVisibility(R.id.widget_body, View.VISIBLE)
        views.setViewVisibility(R.id.widget_empty, View.GONE)

        val pageIndex = displayState.getPageIndex(appWidgetId, tasks.size)
        val task = tasks[pageIndex]
        val slot = task.cacheSlot
        val cache = WidgetCache(context)
        val refreshing = cache.isRefreshing(slot)
        val hasContent = cache.hasCachedContent(slot)

        val title = HomeWidgetDisplayFormatter.formatTitle(cache.getTitle(slot) ?: "", task.title)
        // 刷新中保留上一版缓存，仅显示 ProgressBar；无缓存时才展示占位文案。
        val summary =
            when {
                hasContent ->
                    HomeWidgetDisplayFormatter.normalizeWidgetSummary(cache.getSummary(slot) ?: "")
                refreshing -> context.getString(R.string.widget_loading)
                else -> context.getString(R.string.widget_waiting_first_refresh)
            }
        val timeLabel =
            when {
                hasContent -> cache.getTimeLabel(slot) ?: "--:--"
                refreshing -> context.getString(R.string.widget_loading)
                else -> "--:--"
            }
        val hint = context.getString(R.string.widget_swipe_hint)

        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_summary, summary)
        views.setTextViewText(R.id.widget_time, timeLabel)
        views.setTextViewText(R.id.widget_hint, hint)
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
                taskId?.let { putExtra(WORK_DATA_TASK_ID, it) }
            }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            pendingIntentFlags(),
        )
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
