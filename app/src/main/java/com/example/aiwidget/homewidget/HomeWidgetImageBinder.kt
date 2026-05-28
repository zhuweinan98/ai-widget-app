package com.example.aiwidget.homewidget

import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.example.aiwidget.R
import com.example.aiwidget.data.WidgetCache
import java.io.File

/** 绑定 [R.layout.widget_layout_image]：headline + subtitle + image。 */
object HomeWidgetImageBinder {
    fun bind(views: RemoteViews, cache: WidgetCache, slot: String) {
        val headline = cache.getHeadline(slot)?.trim().orEmpty()
        val subtitle = cache.getSubtitle(slot)?.trim().orEmpty()
        views.setTextViewText(R.id.widget_headline, headline)
        if (subtitle.isEmpty()) {
            views.setViewVisibility(R.id.widget_subtitle, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_subtitle, View.VISIBLE)
            views.setTextViewText(R.id.widget_subtitle, subtitle)
        }
        val imagePath = cache.getImagePath(slot)
        if (imagePath.isNullOrBlank() || !File(imagePath).exists()) {
            views.setViewVisibility(R.id.widget_image, View.GONE)
        } else {
            views.setViewVisibility(R.id.widget_image, View.VISIBLE)
            views.setImageViewUri(R.id.widget_image, Uri.fromFile(File(imagePath)))
        }
    }
}
