package com.example.aiwidget.homewidget

import com.example.aiwidget.R
import com.example.aiwidget.data.WidgetCache
import com.example.aiwidget.data.WidgetTemplate

/** 按服务端 [template] 选择 Widget [RemoteViews] 布局资源。 */
object HomeWidgetLayoutSelector {
    fun layoutRes(
        template: String,
        cache: WidgetCache,
        slot: String,
        hasContent: Boolean,
    ): Int {
        if (!hasContent) {
            return R.layout.widget_layout_text
        }
        return when (template.trim()) {
            WidgetTemplate.LIST_CARD ->
                if (cache.getListItems(slot).isNotEmpty()) {
                    R.layout.widget_layout_list
                } else {
                    R.layout.widget_layout_text
                }
            WidgetTemplate.ICON_TITLE ->
                if (cache.getListItems(slot).isNotEmpty()) {
                    R.layout.widget_layout_icon_title
                } else {
                    R.layout.widget_layout_text
                }
            WidgetTemplate.IMAGE_CARD ->
                if (!cache.getHeadline(slot).isNullOrBlank()) {
                    R.layout.widget_layout_image
                } else {
                    R.layout.widget_layout_text
                }
            else -> R.layout.widget_layout_text
        }
    }
}
