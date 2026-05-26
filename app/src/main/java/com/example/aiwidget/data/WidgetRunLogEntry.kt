package com.example.aiwidget.data

/**
 * 单条 Widget **定时（periodic）** 任务执行记录，供设置页查看历史。
 */
data class WidgetRunLogEntry(
    val finishedAtMs: Long,
    val taskId: String,
    /** 当时发给 Agent 的 prompt（展示用，可能截断）。 */
    val prompt: String,
    /** 见 [WidgetRunOutcome]。 */
    val outcome: String,
    /** API 返回的 status；跳过时可为空。 */
    val status: String = "",
    val errorMsg: String = "",
    val title: String = "",
)

/** 定时任务执行结果分类。 */
object WidgetRunOutcome {
    const val CACHE_SKIPPED = "cache_skipped"
    const val API_OK = "api_ok"
    const val API_ERROR = "api_error"
    const val API_FAILURE = "api_failure"
}
