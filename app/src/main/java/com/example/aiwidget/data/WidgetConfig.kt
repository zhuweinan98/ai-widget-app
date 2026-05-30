package com.example.aiwidget.data

/**
 * Widget 内置默认值与 UI 截断常量。
 *
 * 运行时可调间隔/TTL 在 [WidgetTask] 上，由设置页编辑。
 */
object WidgetConfig {
    /** 默认任务的 cache slot 名。 */
    const val CACHE_SLOT = "aihot"

    /** 默认缓存 TTL：1 小时（秒）。 */
    const val DEFAULT_CACHE_TTL_SECONDS = 3600

    /** 默认定时刷新间隔：60 分钟。 */
    const val DEFAULT_PERIODIC_INTERVAL_MINUTES = 60L

    const val HEADLINE_MAX_ITEMS = 5
    const val TITLE_MAX_CHARS = 28
}
