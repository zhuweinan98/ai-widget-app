package com.example.aiwidget.data

import android.content.Context
import com.example.aiwidget.network.RetrofitClient
import com.squareup.moshi.Types

/**
 * Widget 定时任务执行日志（JSON 数组，存于 [AppPrefs.PREFS_NAME]）。
 * 仅闹钟定时（periodic）经 [com.example.aiwidget.homewidget.HomeWidgetRefreshRunner] 触发时 append；手动 ↻ 不写入。
 */
class WidgetRunLogStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(AppPrefs.PREFS_NAME, Context.MODE_PRIVATE)

    private val adapter =
        RetrofitClient.moshiInstance.adapter<List<WidgetRunLogEntry>>(
            Types.newParameterizedType(List::class.java, WidgetRunLogEntry::class.java),
        )

    fun append(entry: WidgetRunLogEntry) {
        val updated = (loadAllChronological() + entry).takeLast(MAX_ENTRIES)
        prefs.edit().putString(KEY_LOG_JSON, adapter.toJson(updated)).apply()
    }

    /** 最近条目在前（按时间倒序）。 */
    fun loadRecent(limit: Int = MAX_ENTRIES): List<WidgetRunLogEntry> =
        loadAllChronological().sortedByDescending { it.finishedAtMs }.take(limit)

    private fun loadAllChronological(): List<WidgetRunLogEntry> {
        val raw = prefs.getString(KEY_LOG_JSON, null) ?: return emptyList()
        return try {
            adapter.fromJson(raw) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY_LOG_JSON = "widget_periodic_run_log_json"
        const val MAX_ENTRIES = 30
    }
}
