package com.example.aiwidget.data

import android.content.Context

/**
 * Widget 展示内容本地缓存，按 [WidgetTask.cacheSlot] 分区存储。
 *
 * Worker 成功拉取后 [saveSuccess]；刷新进行中时 [setRefreshing] 为 true。
 */
class WidgetCache(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getTitle(slot: String): String? = prefs.getString("${slot}_title", null)

    fun getContent(slot: String): String? = prefs.getString("${slot}_content", null)

    fun getTimeLabel(slot: String): String? = prefs.getString("${slot}_time_label", null)

    /** 上次成功写入的时间戳（毫秒），用于 TTL 判断；与 [timeLabel] 同源。 */
    fun getLastSuccessTimestamp(slot: String): Long =
        prefs.getLong("${slot}_timestamp", 0L)

    fun isRefreshing(slot: String): Boolean =
        prefs.getBoolean("${slot}_refreshing", false)

    fun setRefreshing(slot: String, refreshing: Boolean) {
        prefs.edit().putBoolean("${slot}_refreshing", refreshing).apply()
    }

    fun saveSuccess(
        slot: String,
        title: String,
        content: String,
        timeLabel: String,
        finishedAtMs: Long = System.currentTimeMillis(),
    ) {
        prefs.edit()
            .putString("${slot}_title", title)
            .putString("${slot}_content", content)
            .putString("${slot}_time_label", timeLabel)
            .putLong("${slot}_timestamp", finishedAtMs)
            .putBoolean("${slot}_refreshing", false)
            .apply()
    }

    fun hasCachedContent(slot: String): Boolean {
        val title = getTitle(slot)
        val content = getContent(slot)
        return !title.isNullOrBlank() || !content.isNullOrBlank()
    }

    companion object {
        const val PREFS_NAME = "widget_cache"
    }
}
