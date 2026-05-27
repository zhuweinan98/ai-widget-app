package com.example.aiwidget.homewidget

import android.widget.Toast
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.aiwidget.R
import com.example.aiwidget.data.AppPrefs
import com.example.aiwidget.data.WidgetRunRequest
import com.example.aiwidget.data.WidgetCache
import com.example.aiwidget.data.WidgetResult
import com.example.aiwidget.data.WidgetRunLogEntry
import com.example.aiwidget.data.Presets
import com.example.aiwidget.data.WidgetRunLogStore
import com.example.aiwidget.data.WidgetRunOutcome
import com.example.aiwidget.data.WidgetTask
import com.example.aiwidget.data.WidgetTaskStore
import com.example.aiwidget.network.AgentRepository
import com.example.aiwidget.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager Worker：拉取 Agent 结果并写入 [WidgetCache]。
 *
 * 定时（periodic）触发时会追加 [WidgetRunLogStore] 记录；手动 ↻ 不写日志。
 */
class HomeWidgetRefreshWorker(
    appContext: android.content.Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    private val agentRepository = AgentRepository()

    override suspend fun doWork(): Result {
        val taskStore = WidgetTaskStore(applicationContext)
        val taskId = inputData.getString(HomeWidgetCoordinator.WORK_DATA_TASK_ID)
        val task =
            taskId?.let { taskStore.findTask(it) }
                ?: taskStore.loadEnabledTasks().firstOrNull()
        if (task == null) {
            AppLog.w(TAG, "无可用任务 task_id=$taskId")
            HomeWidgetCoordinator.renderAllWidgets(applicationContext)
            return Result.failure()
        }

        val forceRefresh = inputData.getBoolean("force_refresh", false)
        val isPeriodicTrigger =
            inputData.getString(HomeWidgetCoordinator.WORK_DATA_TRIGGER) == HomeWidgetCoordinator.TRIGGER_PERIODIC
        val cacheTtlSeconds = taskStore.cacheTtlSeconds(task)

        if (isPeriodicTrigger) {
            AppLog.i(
                TAG,
                "定时 task=${task.id} interval=${taskStore.intervalMinutes(task)}min TTL=${cacheTtlSeconds}s",
            )
            showToast(R.string.widget_periodic_started)
        }

        return try {
            executeRefresh(task, cacheTtlSeconds, forceRefresh, isPeriodicTrigger)
        } finally {
            if (isPeriodicTrigger) {
                HomeWidgetCoordinator.rescheduleTaskChain(applicationContext, task)
            }
        }
    }

    private suspend fun executeRefresh(
        task: WidgetTask,
        cacheTtlSeconds: Int,
        forceRefresh: Boolean,
        isPeriodicTrigger: Boolean,
    ): Result {
        val cacheSlot = task.cacheSlot
        val widgetCache = WidgetCache(applicationContext)
        val appPrefs = AppPrefs(applicationContext)
        val userId = appPrefs.getOrCreateUserId()
        val now = System.currentTimeMillis()
        val lastSuccessAt = widgetCache.getLastSuccessTimestamp(cacheSlot)
        val cacheExpiryMs = cacheTtlSeconds * 1000L

        if (!forceRefresh && lastSuccessAt > 0 && now - lastSuccessAt < cacheExpiryMs) {
            AppLog.d(TAG, "缓存未过期，跳过 API task=${task.id} slot=$cacheSlot")
            if (isPeriodicTrigger) {
                appendPeriodicLog(
                    task,
                    outcome = WidgetRunOutcome.CACHE_SKIPPED,
                    status = "skipped",
                )
                showToast(R.string.widget_periodic_skipped)
            } else {
                HomeWidgetCoordinator.renderAllWidgets(applicationContext)
            }
            return Result.success()
        }

        widgetCache.setRefreshing(cacheSlot, true)
        HomeWidgetCoordinator.renderAllWidgets(applicationContext)

        return try {
            val trigger =
                inputData.getString(HomeWidgetCoordinator.WORK_DATA_TRIGGER) ?: HomeWidgetCoordinator.TRIGGER_IMMEDIATE
            val agentMessage = Presets.buildWidgetTaskPrompt(task.prompt)
            val result =
                agentRepository.widgetRun(
                    baseUrl = appPrefs.baseUrl,
                    apiKey = appPrefs.apiKey,
                    request = WidgetRunRequest(userId = userId, message = agentMessage),
                    source = "widget/${task.id}/$trigger",
                )
            if (isPeriodicTrigger) {
                appendPeriodicLog(
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
                val finishedAtMs = System.currentTimeMillis()
                val title = HomeWidgetDisplayFormatter.formatTitle(result.title, task.title)
                val summary = HomeWidgetDisplayFormatter.formatHeadlinesFromContent(result.content)
                val timeLabel = HomeWidgetDisplayFormatter.formatRefreshTime(finishedAtMs)
                AppLog.d(
                    TAG,
                    "widget展示 task=${task.id} title=$title | summary=${summary.take(120)}",
                )
                widgetCache.saveSuccess(
                    cacheSlot,
                    title,
                    summary,
                    result.content,
                    timeLabel,
                    finishedAtMs,
                )
                HomeWidgetCoordinator.renderAllWidgets(applicationContext)
                AppLog.d(TAG, "刷新成功 task=${task.id} slot=$cacheSlot")
                Result.success()
            } else {
                AppLog.w(TAG, "API status=${result.status} ${result.errorMsg}")
                handleRefreshFailure(widgetCache, task)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "刷新失败 task=${task.id}", e)
            if (isPeriodicTrigger) {
                appendPeriodicLog(
                    task,
                    outcome = WidgetRunOutcome.API_FAILURE,
                    status = "error",
                    errorMsg = e.message ?: e.toString(),
                )
            }
            handleRefreshFailure(widgetCache, task)
        }
    }

    private fun appendPeriodicLog(
        task: WidgetTask,
        outcome: String,
        status: String,
        errorMsg: String = "",
        title: String = "",
        result: WidgetResult? = null,
    ) {
        WidgetRunLogStore(applicationContext).append(
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

    private fun handleRefreshFailure(widgetCache: WidgetCache, task: WidgetTask): Result {
        widgetCache.setRefreshing(task.cacheSlot, false)
        return if (widgetCache.hasCachedContent(task.cacheSlot)) {
            HomeWidgetCoordinator.renderAllWidgets(applicationContext)
            Result.success()
        } else {
            AppLog.w(TAG, "刷新失败且无缓存 task=${task.id}")
            HomeWidgetCoordinator.renderAllWidgets(applicationContext)
            Result.retry()
        }
    }

    private suspend fun showToast(messageResId: Int) {
        withContext(Dispatchers.Main) {
            Toast.makeText(applicationContext, messageResId, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "HomeWidgetRefreshWorker"
        private const val PROMPT_LOG_MAX = 500
        private const val ERROR_LOG_MAX = 300
        private const val TITLE_LOG_MAX = 120
    }
}
