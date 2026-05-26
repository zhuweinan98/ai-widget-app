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
import com.example.aiwidget.MainActivity
import com.example.aiwidget.R
import com.example.aiwidget.data.AppPrefs
import com.example.aiwidget.data.WidgetCache
import com.example.aiwidget.data.WidgetConfig
import com.example.aiwidget.data.WidgetTask
import com.example.aiwidget.data.WidgetTaskStore
import com.example.aiwidget.util.AppLog
import java.util.concurrent.TimeUnit

/**
 * 桌面小组件的 WorkManager 调度与 RemoteViews 渲染。
 *
 * 不负责调用 Agent API（见 [HomeWidgetRefreshWorker]）；
 * 只负责：登记/取消定时、立即刷新入队、把 [WidgetCache] 内容画到桌面。
 */
object HomeWidgetCoordinator {
    private const val TAG = "HomeWidgetCoordinator"

    /** 用户点击 Widget 右上角 ↻ 时发送的 Broadcast action。 */
    const val ACTION_MANUAL_REFRESH = "com.example.aiwidget.action.MANUAL_REFRESH"

    /** 手动刷新使用的 OneTimeWork 唯一名（全局一条，避免并发多次刷新）。 */
    const val WORK_IMMEDIATE = "widget_aihot_immediate"

    const val WORK_DATA_TASK_ID = "task_id"
    const val WORK_DATA_TRIGGER = "trigger"

    /** WorkManager 输入：定时触发。 */
    const val TRIGGER_PERIODIC = "periodic"

    /** WorkManager 输入：手动 ↻ 或首添立即刷新。 */
    const val TRIGGER_IMMEDIATE = "immediate"

    /** 取消全部任务的 WorkManager 登记（Widget 从桌面移除时）。 */
    fun hasAppWidgets(context: Context): Boolean {
        val ids =
            AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, HomeWidgetProvider::class.java))
        return ids.isNotEmpty()
    }

    /**
     * 为 [WidgetTaskStore.loadEnabledTasks] 中每条任务登记 WorkManager。
     * 无 Widget 实例时不做任何事（避免空跑 Worker）。
     */
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

    /** 间隔 &lt; 15 分钟时用链式 OneTimeWork；否则用 PeriodicWork。 */
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

    /** 链式定时：单次 Worker 结束后再次排队下一触发。 */
    fun rescheduleTaskChain(context: Context, task: WidgetTask) {
        if (!WidgetConfig.usesPeriodicChain(WidgetTaskStore(context).intervalMinutes(task))) return
        scheduleTaskPeriodic(context, task)
    }

    /** 取消全部任务的 WorkManager 登记（Widget 从桌面移除时）。 */
    fun cancelAllWidgetTaskSchedules(context: Context) {
        val appContext = context.applicationContext
        cancelLegacyPeriodicWorks(appContext)
        WidgetTaskStore(appContext).loadTasks().forEach { task ->
            val wm = WorkManager.getInstance(appContext)
            wm.cancelUniqueWork(taskPeriodicWorkName(task.id))
            wm.cancelUniqueWork(taskChainWorkName(task.id))
        }
        AppLog.i(TAG, "已取消全部任务定时")
    }

    private fun cancelLegacyPeriodicWorks(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("widget_aihot_periodic")
        wm.cancelUniqueWork("widget_aihot_periodic_chain")
    }

    /**
     * 入队一次立即刷新（手动 ↻ 或首添 Widget）。
     * @param forceRefresh true 时 Worker 忽略缓存 TTL
     */
    fun enqueueImmediateRefresh(context: Context, forceRefresh: Boolean) {
        val store = WidgetTaskStore(context)
        val task = store.primaryDisplayTask() ?: return
        val request =
            OneTimeWorkRequestBuilder<HomeWidgetRefreshWorker>()
                .setInputData(buildWorkerInputData(task.id, forceRefresh = forceRefresh))
                .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            request,
        )
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

    /**
     * 根据 [WidgetTaskStore.primaryDisplayTask] 与 [WidgetCache] 刷新所有 Widget 实例。
     */
    fun renderAllWidgets(context: Context) {
        val store = WidgetTaskStore(context)
        val task = store.primaryDisplayTask()
        if (task == null) {
            renderErrorPlaceholder(context)
            return
        }
        val cache = WidgetCache(context)
        val slot = task.cacheSlot
        if (cache.isRefreshing(slot)) {
            updateAllWidgetInstances(
                context,
                title = task.title,
                summary = "",
                timeLabel = "更新中…",
                showLoading = true,
            )
            return
        }
        if (cache.hasCachedContent(slot)) {
            val title = HomeWidgetDisplayFormatter.formatTitle(cache.getTitle(slot) ?: "", task.title)
            val summary =
                HomeWidgetDisplayFormatter.normalizeWidgetSummary(cache.getContent(slot) ?: "")
            val time = cache.getTimeLabel(slot) ?: "--:--"
            updateAllWidgetInstances(context, title, summary, time, showLoading = false)
        } else {
            updateAllWidgetInstances(
                context,
                title = task.title,
                summary = "等待首次刷新…",
                timeLabel = "--:--",
                showLoading = false,
            )
        }
    }

    /** 绑定单个 appWidgetId 的 RemoteViews（点击打开 App、↻ 手动刷新）。 */
    fun bindWidgetRemoteViews(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        title: String,
        summary: String,
        timeLabel: String,
        showLoading: Boolean,
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setTextViewText(R.id.widget_title, title)
        views.setTextViewText(R.id.widget_summary, summary)
        views.setTextViewText(R.id.widget_time, timeLabel)
        views.setTextViewText(
            R.id.widget_hint,
            if (showLoading) context.getString(R.string.widget_loading) else context.getString(R.string.widget_tap_open),
        )
        views.setViewVisibility(
            R.id.widget_loading,
            if (showLoading) View.VISIBLE else View.GONE,
        )
        views.setViewVisibility(
            R.id.widget_refresh,
            if (showLoading) View.INVISIBLE else View.VISIBLE,
        )

        val openIntent = Intent(context, MainActivity::class.java)
        val openPending =
            PendingIntent.getActivity(
                context,
                appWidgetId,
                openIntent,
                pendingIntentFlags(),
            )
        views.setOnClickPendingIntent(R.id.widget_body, openPending)

        val refreshIntent =
            Intent(context, HomeWidgetProvider::class.java).apply {
                action = ACTION_MANUAL_REFRESH
            }
        val refreshPending =
            PendingIntent.getBroadcast(
                context,
                appWidgetId + 10_000,
                refreshIntent,
                pendingIntentFlags(),
            )
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)

        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /** 无缓存且请求失败时的占位 UI。 */
    fun renderErrorPlaceholder(context: Context, title: String = "AI 快讯") {
        updateAllWidgetInstances(
            context,
            title = title,
            summary = "暂无数据\n请检查网络与 API 地址",
            timeLabel = "--:--",
            showLoading = false,
        )
    }

    private fun updateAllWidgetInstances(
        context: Context,
        title: String,
        summary: String,
        timeLabel: String,
        showLoading: Boolean,
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val ids =
            manager.getAppWidgetIds(
                ComponentName(context, HomeWidgetProvider::class.java),
            )
        for (id in ids) {
            bindWidgetRemoteViews(context, manager, id, title, summary, timeLabel, showLoading)
        }
    }

    private fun pendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
}
