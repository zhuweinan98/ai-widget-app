package com.example.aiwidget.data

import android.content.Context
import com.example.aiwidget.network.RetrofitClient
import com.squareup.moshi.Types

/** Widget 展示内容本地缓存，按 [WidgetTask.cacheSlot] 分区存储。 */
class WidgetCache(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val listItemsAdapter =
        RetrofitClient.moshiInstance.adapter<List<WidgetListItem>>(
            Types.newParameterizedType(List::class.java, WidgetListItem::class.java),
        )

    fun getTitle(slot: String): String? = prefs.getString("${slot}_title", null)

    fun getTemplate(slot: String): String? = prefs.getString("${slot}_template", null)

    fun getListItems(slot: String): List<WidgetListItem> = decodeList(slot, KEY_ITEMS_SUFFIX)

    fun getHeadline(slot: String): String? = prefs.getString("${slot}_headline", null)

    fun getSubtitle(slot: String): String? = prefs.getString("${slot}_subtitle", null)

    /** 本地配图绝对路径（刷新时下载后写入）。 */
    fun getImagePath(slot: String): String? = prefs.getString("${slot}_image_path", null)

    fun getRawContent(slot: String): String? = prefs.getString("${slot}_raw_content", null)

    fun getTimeLabel(slot: String): String? = prefs.getString("${slot}_time_label", null)

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
        template: String,
        items: List<WidgetListItem>,
        headline: String,
        subtitle: String,
        imagePath: String?,
        rawContent: String,
        timeLabel: String,
        finishedAtMs: Long = System.currentTimeMillis(),
    ) {
        prefs.edit()
            .putString("${slot}_title", title)
            .putString("${slot}_template", template.trim())
            .putString("${slot}$KEY_ITEMS_SUFFIX", listItemsAdapter.toJson(items))
            .putString("${slot}_headline", headline.trim())
            .putString("${slot}_subtitle", subtitle.trim())
            .putString("${slot}_raw_content", rawContent)
            .putString("${slot}_time_label", timeLabel)
            .putLong("${slot}_timestamp", finishedAtMs)
            .putBoolean("${slot}_refreshing", false)
            .apply()
        if (imagePath.isNullOrBlank()) {
            prefs.edit().remove("${slot}_image_path").apply()
        } else {
            prefs.edit().putString("${slot}_image_path", imagePath).apply()
        }
        prefs.edit().remove("${slot}_content").remove("${slot}_summary").apply()
    }

    fun hasRawContent(slot: String): Boolean = !getRawContent(slot).isNullOrBlank()

    fun hasCachedContent(slot: String): Boolean {
        return !getTitle(slot).isNullOrBlank() ||
            getListItems(slot).isNotEmpty() ||
            !getHeadline(slot).isNullOrBlank()
    }

    /** 删除任务时清空该 [cacheSlot] 的展示缓存（不含其它 slot）。 */
    fun clearSlot(slot: String) {
        val editor = prefs.edit()
        editor
            .remove("${slot}_title")
            .remove("${slot}_template")
            .remove("${slot}$KEY_ITEMS_SUFFIX")
            .remove("${slot}_headline")
            .remove("${slot}_subtitle")
            .remove("${slot}_image_path")
            .remove("${slot}_raw_content")
            .remove("${slot}_time_label")
            .remove("${slot}_timestamp")
            .remove("${slot}_refreshing")
            .remove("${slot}_content")
            .remove("${slot}_summary")
        editor.apply()
    }

    private fun decodeList(slot: String, suffix: String): List<WidgetListItem> {
        val raw = prefs.getString("${slot}$suffix", null) ?: return emptyList()
        return try {
            listItemsAdapter.fromJson(raw) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        const val PREFS_NAME = "widget_cache"
        private const val KEY_ITEMS_SUFFIX = "_items_json"
    }
}
