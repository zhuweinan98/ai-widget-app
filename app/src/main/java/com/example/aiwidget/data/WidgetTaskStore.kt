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
                id = WidgetTask.HOLDINGS_TASK_ID,
                title = "持仓盈亏",
                prompt = Presets.holdingsUserMessage(),
                cacheSlot = "holdings",
                intervalMinutes = WidgetConfig.DEFAULT_PERIODIC_INTERVAL_MINUTES,
                cacheTtlSeconds = WidgetConfig.DEFAULT_CACHE_TTL_SECONDS,
            ),
        )

    fun loadTasks(): List<WidgetTask> {
        val raw = prefs.getString(KEY_TASKS_JSON, null) ?: return defaultTasks()
        return try {
            tasksAdapter.fromJson(raw)
                ?.filter { it.id.isNotBlank() }
                ?.takeIf { it.isNotEmpty() }
                ?: defaultTasks()
        } catch (_: Exception) {
            defaultTasks()
        }
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
    }
}
