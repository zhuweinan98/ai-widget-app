package com.example.aiwidget.homewidget

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.example.aiwidget.data.AppPrefs
import com.example.aiwidget.data.WidgetRunRequest
import com.example.aiwidget.data.WidgetCache
import com.example.aiwidget.data.WidgetResult
import com.example.aiwidget.data.WidgetRunLogEntry
import com.example.aiwidget.data.WidgetRunLogStore
import com.example.aiwidget.data.WidgetRunOutcome
import com.example.aiwidget.data.WidgetTask
import com.example.aiwidget.data.WidgetTaskStore
import com.example.aiwidget.data.validateForWidget
import com.example.aiwidget.network.AgentRepository
import com.example.aiwidget.util.AppLog

/** 拉取 Agent 结果并写入 [WidgetCache]；由 [HomeWidgetRefreshService] 在前台执行。 */
object HomeWidgetRefreshRunner {
    private const val TAG = "HomeWidgetRefreshRunner"
    private const val PROMPT_LOG_MAX = 500
    private const val ERROR_LOG_MAX = 300
    private const val TITLE_LOG_MAX = 120

    private val agentRepository = AgentRepository()

    suspend fun run(
        context: Context,
        taskId: String?,
        forceRefresh: Boolean,
        trigger: String,
        runAllEnabled: Boolean = false,
    ) {
        val appContext = context.applicationContext
        if (runAllEnabled) {
            runAllEnabledTasks(appContext, forceRefresh)
            return
        }

        val taskStore = WidgetTaskStore(appContext)
        val task =
            taskId?.let { taskStore.findTask(it) }
                ?: taskStore.loadEnabledTasks().firstOrNull()
        if (task == null) {
            AppLog.w(TAG, "无可用任务 task_id=$taskId")
            HomeWidgetCoordinator.renderAllWidgets(appContext)
            return
        }

        val isPeriodicTrigger = trigger == HomeWidgetCoordinator.TRIGGER_PERIODIC
        val cacheTtlSeconds = taskStore.cacheTtlSeconds(task)

        if (isPeriodicTrigger) {
            AppLog.i(
                TAG,
                "定时 task=${task.id} interval=${taskStore.intervalMinutes(task)}min TTL=${cacheTtlSeconds}s",
            )
        }

        try {
            if (!isNetworkAvailable(appContext)) {
                AppLog.w(TAG, "无网络，跳过刷新 task=${task.id}")
                if (isPeriodicTrigger) {
                    appendPeriodicLog(
                        appContext,
                        task,
                        outcome = WidgetRunOutcome.API_FAILURE,
                        status = "error",
                        errorMsg = "无网络连接",
                    )
                }
                return
            }
            executeRefresh(appContext, task, cacheTtlSeconds, forceRefresh, isPeriodicTrigger)
        } finally {
            if (isPeriodicTrigger && task.enabled) {
                HomeWidgetAlarmScheduler.scheduleNext(appContext, task)
            }
        }
    }

    private suspend fun runAllEnabledTasks(
        context: Context,
        forceRefresh: Boolean,
    ) {
        val taskStore = WidgetTaskStore(context)
        val tasks = taskStore.loadEnabledTasks()
        if (tasks.isEmpty()) {
            AppLog.w(TAG, "无已启用任务，跳过批量刷新")
            HomeWidgetCoordinator.renderAllWidgets(context)
            return
        }
        if (!isNetworkAvailable(context)) {
            AppLog.w(TAG, "无网络，跳过批量刷新")
            return
        }
        AppLog.i(TAG, "批量刷新 ${tasks.size} 条已启用任务")
        tasks.forEach { task ->
            executeRefresh(
                context,
                task,
                taskStore.cacheTtlSeconds(task),
                forceRefresh,
                isPeriodicTrigger = false,
            )
        }
    }

    private suspend fun executeRefresh(
        context: Context,
        task: WidgetTask,
        cacheTtlSeconds: Int,
        forceRefresh: Boolean,
        isPeriodicTrigger: Boolean,
    ) {
        val cacheSlot = task.cacheSlot
        val widgetCache = WidgetCache(context)
        val appPrefs = AppPrefs(context)
        val userId = appPrefs.getOrCreateUserId()
        val now = System.currentTimeMillis()
        val lastSuccessAt = widgetCache.getLastSuccessTimestamp(cacheSlot)
        val cacheExpiryMs = cacheTtlSeconds * 1000L

        if (!forceRefresh && lastSuccessAt > 0 && now - lastSuccessAt < cacheExpiryMs) {
            AppLog.d(TAG, "缓存未过期，跳过 API task=${task.id} slot=$cacheSlot")
            if (isPeriodicTrigger) {
                appendPeriodicLog(
                    context,
                    task,
                    outcome = WidgetRunOutcome.CACHE_SKIPPED,
                    status = "skipped",
                )
            } else {
                HomeWidgetCoordinator.renderAllWidgets(context)
            }
            return
        }

        widgetCache.setRefreshing(cacheSlot, true)
        HomeWidgetCoordinator.renderAllWidgets(context)

        try {
            val agentMessage = task.prompt.trim()
            if (agentMessage.isEmpty()) {
                AppLog.w(TAG, "任务 prompt 为空 task=${task.id}")
                handleRefreshFailure(context, widgetCache, task)
                return
            }
            val result =
                agentRepository.widgetRun(
                    baseUrl = appPrefs.baseUrl,
                    apiKey = appPrefs.apiKey,
                    request = WidgetRunRequest(userId = userId, message = agentMessage),
                    source = "widget/${task.id}/${
                        if (isPeriodicTrigger) {
                            HomeWidgetCoordinator.TRIGGER_PERIODIC
                        } else {
                            HomeWidgetCoordinator.TRIGGER_IMMEDIATE
                        }
                    }",
                )
            if (isPeriodicTrigger) {
                appendPeriodicLog(
                    context,
                    task,
                    outcome =
                        if (result.status == "ok") {
                            WidgetRunOutcome.API_OK
                        } else {
                            WidgetRunOutcome.API_ERROR
                        },
                    status = result.status,
                    errorMsg = result.errorMsg,
                    title = result.title,
                    result = result,
                )
            }
            if (result.status == "ok") {
                val validationError = result.validateForWidget()
                if (validationError != null) {
                    AppLog.w(TAG, "Widget 终局无效 task=${task.id}: $validationError")
                    if (isPeriodicTrigger) {
                        appendPeriodicLog(
                            context,
                            task,
                            outcome = WidgetRunOutcome.API_ERROR,
                            status = result.status,
                            errorMsg = validationError,
                            title = result.title,
                            result = result,
                        )
                    }
                    handleRefreshFailure(context, widgetCache, task)
                    return
                }
                val finishedAtMs = System.currentTimeMillis()
                val title = HomeWidgetDisplayFormatter.formatTitle(result.title, task.title)
                val timeLabel = HomeWidgetDisplayFormatter.formatRefreshTime(finishedAtMs)
                val imagePath =
                    HomeWidgetImageLoader.download(
                        context,
                        cacheSlot,
                        result.imageUrl,
                    )
                AppLog.d(
                    TAG,
                    "widget展示 task=${task.id} template=${result.template} title=$title " +
                        "items=${result.items.size} headline=${result.headline.take(40)}",
                )
                widgetCache.saveSuccess(
                    cacheSlot,
                    title,
                    result.template.trim(),
                    result.items,
                    result.headline,
                    result.subtitle,
                    imagePath,
                    result.content.trim(),
                    timeLabel,
                    finishedAtMs,
                )
                HomeWidgetCoordinator.renderAllWidgets(context)
                AppLog.d(TAG, "刷新成功 task=${task.id} slot=$cacheSlot")
            } else {
                AppLog.w(TAG, "API status=${result.status} ${result.errorMsg}")
                handleRefreshFailure(context, widgetCache, task)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "刷新失败 task=${task.id}", e)
            if (isPeriodicTrigger) {
                appendPeriodicLog(
                    context,
                    task,
                    outcome = WidgetRunOutcome.API_FAILURE,
                    status = "error",
                    errorMsg = e.message ?: e.toString(),
                )
            }
            handleRefreshFailure(context, widgetCache, task)
        }
    }

    private fun handleRefreshFailure(
        context: Context,
        widgetCache: WidgetCache,
        task: WidgetTask,
    ) {
        widgetCache.setRefreshing(task.cacheSlot, false)
        if (widgetCache.hasCachedContent(task.cacheSlot)) {
            HomeWidgetCoordinator.renderAllWidgets(context)
        } else {
            AppLog.w(TAG, "刷新失败且无缓存 task=${task.id}")
            HomeWidgetCoordinator.renderAllWidgets(context)
        }
    }

    private fun appendPeriodicLog(
        context: Context,
        task: WidgetTask,
        outcome: String,
        status: String,
        errorMsg: String = "",
        title: String = "",
        result: WidgetResult? = null,
    ) {
        WidgetRunLogStore(context).append(
            WidgetRunLogEntry(
                finishedAtMs = System.currentTimeMillis(),
                taskId = task.id,
                prompt = task.prompt.take(PROMPT_LOG_MAX),
                outcome = outcome,
                status = status,
                errorMsg = errorMsg.take(ERROR_LOG_MAX),
                title = (title.ifBlank { result?.title ?: "" }).take(TITLE_LOG_MAX),
            ),
        )
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
