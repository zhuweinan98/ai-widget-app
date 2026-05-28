package com.example.aiwidget.homewidget

import android.view.View
import android.widget.RemoteViews
import com.example.aiwidget.R
import com.example.aiwidget.data.WidgetConfig
import com.example.aiwidget.data.WidgetListItem

/** 绑定 [R.layout.widget_layout_icon_title]：icon + title 行。 */
object HomeWidgetIconTitleBinder {
    private data class RowIds(
        val row: Int,
        val icon: Int,
        val title: Int,
    )

    private val rowIds =
        listOf(
            RowIds(R.id.widget_row_0, R.id.widget_row_0_icon, R.id.widget_row_0_title),
            RowIds(R.id.widget_row_1, R.id.widget_row_1_icon, R.id.widget_row_1_title),
            RowIds(R.id.widget_row_2, R.id.widget_row_2_icon, R.id.widget_row_2_title),
            RowIds(R.id.widget_row_3, R.id.widget_row_3_icon, R.id.widget_row_3_title),
            RowIds(R.id.widget_row_4, R.id.widget_row_4_icon, R.id.widget_row_4_title),
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
        }
    }
}
