package com.example.aiwidget.data

/**
 * Widget 定时任务：标题 + 用户自然语言 prompt + 定时间隔 + 独立缓存槽。
 * 执行时 Worker 用 [Presets.buildWidgetTaskPrompt] 追加 Widget 输出格式后再请求 Agent。
 */
data class WidgetTask(
    val id: String,
    /** 桌面展示标题；API 无 title 时的回退。 */
    val title: String,
    /** 用户自然语言任务描述；发送前会拼接 [Presets.WIDGET_RESPONSE_FORMAT]。 */
    val prompt: String,
    /** [WidgetCache] 分区键；多任务可共用或独立 slot。 */
    val cacheSlot: String,
    /** false 时不登记 WorkManager 定时。 */
    val enabled: Boolean = true,
    val intervalMinutes: Long = WidgetConfig.DEFAULT_PERIODIC_INTERVAL_MINUTES,
    val cacheTtlSeconds: Int = WidgetConfig.DEFAULT_CACHE_TTL_SECONDS,
)

/** 兼容旧 JSON 字段 `label` / `message`。 */
internal data class WidgetTaskDto(
    val id: String,
    val title: String? = null,
    val label: String? = null,
    val prompt: String? = null,
    val message: String? = null,
    val cacheSlot: String = "",
    val enabled: Boolean = true,
    val intervalMinutes: Long = WidgetConfig.DEFAULT_PERIODIC_INTERVAL_MINUTES,
    val cacheTtlSeconds: Int = WidgetConfig.DEFAULT_CACHE_TTL_SECONDS,
) {
    fun toTask(): WidgetTask {
        val resolvedTitle = title?.trim().orEmpty().ifBlank { label?.trim().orEmpty() }.ifBlank { id }
        val resolvedPrompt = prompt?.trim().orEmpty().ifBlank { message?.trim().orEmpty() }
        return WidgetTask(
            id = id,
            title = resolvedTitle,
            prompt = resolvedPrompt,
            cacheSlot = cacheSlot.ifBlank { id },
            enabled = enabled,
            intervalMinutes = intervalMinutes,
            cacheTtlSeconds = cacheTtlSeconds,
        )
    }
}
