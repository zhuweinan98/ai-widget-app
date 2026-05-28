package com.example.aiwidget.homewidget

import android.view.View
import android.widget.RemoteViews
import com.example.aiwidget.R
import com.example.aiwidget.data.WidgetConfig
import com.example.aiwidget.data.WidgetListItem

/** 将 [WidgetListItem] 绑定到 [R.layout.widget_layout_list] 的固定行。 */
object HomeWidgetListBinder {
    private data class RowIds(
        val row: Int,
        val icon: Int,
        val title: Int,
        val value: Int,
    )

    private val rowIds =
        listOf(
            RowIds(R.id.widget_item_0, R.id.widget_item_0_icon, R.id.widget_item_0_title, R.id.widget_item_0_value),
            RowIds(R.id.widget_item_1, R.id.widget_item_1_icon, R.id.widget_item_1_title, R.id.widget_item_1_value),
            RowIds(R.id.widget_item_2, R.id.widget_item_2_icon, R.id.widget_item_2_title, R.id.widget_item_2_value),
            RowIds(R.id.widget_item_3, R.id.widget_item_3_icon, R.id.widget_item_3_title, R.id.widget_item_3_value),
            RowIds(R.id.widget_item_4, R.id.widget_item_4_icon, R.id.widget_item_4_title, R.id.widget_item_4_value),
        )

    fun bindItems(views: RemoteViews, items: List<WidgetListItem>) {
        val visible = items.take(WidgetConfig.HEADLINE_MAX_ITEMS)
        rowIds.forEachIndexed { index, ids ->
            val item = visible.getOrNull(index)
            if (item == null) {
                views.setViewVisibility(ids.row, View.GONE)
                return@forEachIndexed
            }
            views.setViewVisibility(ids.row, View.VISIBLE)
            val prefix = HomeWidgetDisplayFormatter.formatIconPrefix(item.icon)
            views.setTextViewText(ids.icon, prefix)
            views.setViewVisibility(ids.icon, if (prefix.isEmpty()) View.INVISIBLE else View.VISIBLE)
            views.setTextViewText(ids.title, item.title.trim())
            val value = item.value.trim()
            if (value.isEmpty()) {
                views.setViewVisibility(ids.value, View.GONE)
            } else {
                views.setViewVisibility(ids.value, View.VISIBLE)
                views.setTextViewText(ids.value, value)
            }
        }
    }
}
