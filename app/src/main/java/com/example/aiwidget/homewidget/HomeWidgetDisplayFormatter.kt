package com.example.aiwidget.homewidget

import com.example.aiwidget.data.WidgetConfig
import com.example.aiwidget.data.WidgetListItemIcon
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object HomeWidgetDisplayFormatter {
    fun formatTitle(raw: String, fallback: String = "AI 快讯"): String {
        val t = raw.trim().ifBlank { fallback }
        return if (t.length <= WidgetConfig.TITLE_MAX_CHARS) {
            t
        } else {
            t.take(WidgetConfig.TITLE_MAX_CHARS) + "…"
        }
    }

    fun formatIconPrefix(icon: String): String =
        when (icon.trim().lowercase()) {
            WidgetListItemIcon.UP -> "↑"
            WidgetListItemIcon.DOWN -> "↓"
            WidgetListItemIcon.HIGH -> "‼"
            WidgetListItemIcon.MEDIUM -> "!"
            WidgetListItemIcon.LOW, "" -> ""
            else -> ""
        }

    fun formatRefreshTime(finishedAtMs: Long = System.currentTimeMillis()): String {
        val time =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(finishedAtMs))
        return "$time 更新"
    }
}
