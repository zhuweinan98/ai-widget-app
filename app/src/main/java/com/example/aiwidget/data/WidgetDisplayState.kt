package com.example.aiwidget.data

import android.content.Context

/** 每个桌面 Widget 实例当前展示的任务页索引（对应 [WidgetTaskStore.loadEnabledTasks]）。 */
class WidgetDisplayState(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPageIndex(appWidgetId: Int, taskCount: Int): Int {
        if (taskCount <= 0) return 0
        val raw = prefs.getInt(key(appWidgetId), 0)
        return ((raw % taskCount) + taskCount) % taskCount
    }

    fun setPageIndex(appWidgetId: Int, index: Int) {
        prefs.edit().putInt(key(appWidgetId), index).apply()
    }

    fun pagePrev(appWidgetId: Int, taskCount: Int): Int {
        val next = (getPageIndex(appWidgetId, taskCount) - 1 + taskCount) % taskCount
        setPageIndex(appWidgetId, next)
        return next
    }

    fun pageNext(appWidgetId: Int, taskCount: Int): Int {
        val next = (getPageIndex(appWidgetId, taskCount) + 1) % taskCount
        setPageIndex(appWidgetId, next)
        return next
    }

    fun remove(appWidgetId: Int) {
        prefs.edit().remove(key(appWidgetId)).apply()
    }

    private fun key(appWidgetId: Int): String = "page_$appWidgetId"

    companion object {
        const val PREFS_NAME = "widget_display_state"
    }
}
