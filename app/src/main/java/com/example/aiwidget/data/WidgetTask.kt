package com.example.aiwidget.data

/**
 * Widget 定时任务：标题 + prompt + 定时间隔 + 独立缓存槽。
 * 执行时由 Agent 自行决定是否/如何调用 Skill，客户端不绑定 Skill。
 */
data class WidgetTask(
    val id: String,
    /** 桌面展示标题；API 无 title 时的回退。 */
    val title: String,
    /** 发给 `POST /agent/chat` 的完整 prompt。 */
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
