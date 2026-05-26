package com.example.aiwidget.data

import android.content.Context
import com.example.aiwidget.network.RetrofitClient
import com.squareup.moshi.Types

/**
 * Widget 定时任务列表的读写（JSON 存在 [AppPrefs] 同一 SharedPreferences 文件）。
 *
 * 桌面 Widget 仅展示 [loadEnabledTasks]；设置页可编辑全部 [loadTasks]。
 */
class WidgetTaskStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)

    private val tasksAdapter =
        RetrofitClient.moshiInstance.adapter<List<WidgetTask>>(
            Types.newParameterizedType(List::class.java, WidgetTask::class.java),
        )

    private val tasksDtoAdapter =
        RetrofitClient.moshiInstance.adapter<List<WidgetTaskDto>>(
            Types.newParameterizedType(List::class.java, WidgetTaskDto::class.java),
        )

    init {
        seedDefaultsIfEmpty()
    }

    /** 首次安装时写入 [defaultTasks]。 */
    fun seedDefaultsIfEmpty() {
        if (prefs.contains(KEY_TASKS_JSON)) return
        saveTasks(defaultTasks())
    }

    fun defaultTasks(): List<WidgetTask> =
        listOf(
            WidgetTask(
                id = "widget_main",
                title = "AI 1h 速报",
                prompt = Presets.AI_1H_USER_MESSAGE,
                cacheSlot = WidgetConfig.CACHE_SLOT,
                intervalMinutes = WidgetConfig.DEFAULT_PERIODIC_INTERVAL_MINUTES,
                cacheTtlSeconds = WidgetConfig.DEFAULT_CACHE_TTL_SECONDS,
            ),
            WidgetTask(
                id = "widget_holdings",
                title = "持仓盈亏",
                prompt = Presets.holdingsUserMessage(),
                cacheSlot = "holdings",
                intervalMinutes = WidgetConfig.DEFAULT_PERIODIC_INTERVAL_MINUTES,
                cacheTtlSeconds = WidgetConfig.DEFAULT_CACHE_TTL_SECONDS,
            ),
        )

    fun loadTasks(): List<WidgetTask> {
        val raw = prefs.getString(KEY_TASKS_JSON, null) ?: return defaultTasks()
        val parsed =
            try {
                tasksAdapter.fromJson(raw)
                    ?.filter { it.id.isNotBlank() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: parseTasksLegacy(raw)
            } catch (_: Exception) {
                parseTasksLegacy(raw)
            }
        return migrateLegacyTasks(parsed)
    }

    /** 旧版「aihot_1h + aihot_24h」双任务；升级后合并为单条 widget_main（保留用户配置）。 */
    private fun migrateLegacyTasks(tasks: List<WidgetTask>): List<WidgetTask> {
        if (!tasks.any { it.id == LEGACY_TASK_24H_ID }) {
            return tasks
        }
        val primary =
            tasks.find { it.id == LEGACY_TASK_1H_ID || it.id == "widget_main" }
                ?: tasks.first()
        val migrated =
            listOf(
                WidgetTask(
                    id = "widget_main",
                    title = primary.title.ifBlank { "AI 1h 速报" },
                    prompt = primary.prompt.ifBlank { Presets.AI_1H_USER_MESSAGE },
                    cacheSlot = WidgetConfig.CACHE_SLOT,
                    intervalMinutes = primary.intervalMinutes,
                    cacheTtlSeconds = primary.cacheTtlSeconds,
                    enabled = primary.enabled,
                ),
            )
        saveTasks(migrated)
        return migrated
    }

    private fun parseTasksLegacy(raw: String): List<WidgetTask> =
        try {
            tasksDtoAdapter.fromJson(raw)
                ?.map { it.toTask() }
                ?.filter { it.id.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?: defaultTasks()
        } catch (_: Exception) {
            defaultTasks()
        }

    fun saveTasks(tasks: List<WidgetTask>) {
        if (tasks.isEmpty()) return
        prefs.edit().putString(KEY_TASKS_JSON, tasksAdapter.toJson(tasks)).apply()
    }

    fun updateTask(taskId: String, transform: (WidgetTask) -> WidgetTask): Boolean {
        val tasks = loadTasks().toMutableList()
        val index = tasks.indexOfFirst { it.id == taskId }
        if (index < 0) return false
        tasks[index] = transform(tasks[index])
        saveTasks(tasks)
        return true
    }

    /** 参与 WorkManager 定时的任务（enabled = true）。 */
    fun loadEnabledTasks(): List<WidgetTask> = loadTasks().filter { it.enabled }

    fun findTask(taskId: String): WidgetTask? = loadTasks().find { it.id == taskId }

    fun cacheTtlSeconds(task: WidgetTask): Int = task.cacheTtlSeconds.coerceAtLeast(0)

    fun intervalMinutes(task: WidgetTask): Long = task.intervalMinutes.coerceAtLeast(1L)

    companion object {
        private const val KEY_TASKS_JSON = "widget_tasks_json"
        private const val LEGACY_TASK_1H_ID = "aihot_1h"
        private const val LEGACY_TASK_24H_ID = "aihot_24h"
    }
}
