package com.example.aiwidget.data

/**
 * Widget 内置默认值与 UI 截断常量。
 *
 * 运行时可调间隔/TTL 在 [WidgetTask] 上，由设置页编辑。
 */
object WidgetConfig {
    /** 默认任务的 cache slot 名。 */
    const val CACHE_SLOT = "aihot"

    /** 默认缓存 TTL：30 分钟（设置页以分钟编辑，持久化仍为秒）。 */
    const val DEFAULT_CACHE_TTL_MINUTES = 30

    const val DEFAULT_CACHE_TTL_SECONDS = DEFAULT_CACHE_TTL_MINUTES * 60

    /** 默认定时刷新间隔：60 分钟。 */
    const val DEFAULT_PERIODIC_INTERVAL_MINUTES = 60L

    /** 用户可添加的 Widget 任务数量上限。 */
    const val MAX_WIDGET_TASKS = 8

    const val HEADLINE_MAX_ITEMS = 5
    const val TITLE_MAX_CHARS = 28
}
