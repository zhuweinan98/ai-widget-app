package com.example.aiwidget.data

/**
 * Widget 定时任务：标题 + 用户自然语言 prompt + 定时间隔 + 独立缓存槽。
 * 执行时 Worker 将 [prompt] 作为 `/widget/run` 的 message（格式后缀由后端拼接）。
 */
data class WidgetTask(
    val id: String,
    /** 桌面展示标题；API 无 title 时的回退。 */
    val title: String,
    /** 用户自然语言任务描述，原样作为 `/widget/run` 的 message。 */
    val prompt: String,
    /** [WidgetCache] 分区键；多任务可共用或独立 slot。 */
    val cacheSlot: String,
    /** false 时不登记 WorkManager 定时。 */
    val enabled: Boolean = true,
    val intervalMinutes: Long = WidgetConfig.DEFAULT_PERIODIC_INTERVAL_MINUTES,
    val cacheTtlSeconds: Int = WidgetConfig.DEFAULT_CACHE_TTL_SECONDS,
) {
    companion object {
        const val HOLDINGS_TASK_ID = "widget_holdings"
    }
}
