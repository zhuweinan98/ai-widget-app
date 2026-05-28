package com.example.aiwidget.homewidget

import android.widget.RemoteViews
import com.example.aiwidget.R
import com.example.aiwidget.data.WidgetCache

/** 将缓存数据绑定到已选定的 Widget 布局正文区域。 */
object HomeWidgetRemoteBinder {
    fun bindBody(
        views: RemoteViews,
        layoutRes: Int,
        cache: WidgetCache,
        slot: String,
        placeholderText: String,
    ) {
        when (layoutRes) {
            R.layout.widget_layout_list ->
                HomeWidgetListBinder.bindItems(views, cache.getListItems(slot))
            R.layout.widget_layout_icon_title ->
                HomeWidgetIconTitleBinder.bindItems(views, cache.getListItems(slot))
            R.layout.widget_layout_image ->
                HomeWidgetImageBinder.bind(views, cache, slot)
            else ->
                views.setTextViewText(R.id.widget_summary, placeholderText)
        }
    }
}
