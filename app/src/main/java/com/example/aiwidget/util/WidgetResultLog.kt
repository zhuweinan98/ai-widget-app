package com.example.aiwidget.util

import com.example.aiwidget.data.WidgetResult

/** 打印 Agent 终局 [WidgetResult] 各字段（与 Moshi 映射一致，不做 UI 拼接推测）。 */
object WidgetResultLog {
    private const val TAG = "WidgetResult"
    private const val PREVIEW_CHARS = 320

    fun log(source: String, result: WidgetResult) {
        val title = result.title
        val content = result.content
        val errorMsg = result.errorMsg

        AppLog.i(
            TAG,
            "[$source] status=${result.status} template=${result.template.ifBlank { "-" }} " +
                "updated_at=${result.updatedAt.ifBlank { "-" }} " +
                "can_follow_up=${result.canFollowUp} " +
                "titleLen=${title.length} contentLen=${content.length} " +
                "items=${result.items.size} headlineLen=${result.headline.length} " +
                "image_url=${result.imageUrl.isNotBlank()} " +
                "errorLen=${errorMsg.length} traceLines=${result.debugTrace.size}",
        )
        if (title.isNotBlank()) {
            AppLog.d(TAG, "[$source] title=${preview(title)}")
        }
        if (result.headline.isNotBlank()) {
            AppLog.d(TAG, "[$source] headline=${preview(result.headline)}")
        }
        if (result.subtitle.isNotBlank()) {
            AppLog.d(TAG, "[$source] subtitle=${preview(result.subtitle)}")
        }
        if (content.isNotBlank()) {
            AppLog.d(TAG, "[$source] content=${preview(content)}")
        }
        if (errorMsg.isNotBlank()) {
            AppLog.w(TAG, "[$source] error_msg=${preview(errorMsg)}")
        }
        result.items.forEachIndexed { index, item ->
            AppLog.d(
                TAG,
                "[$source] item[$index] title=${item.title.trim()} " +
                    "value=${item.value.trim()} icon=${item.icon.trim()}",
            )
        }
        DebugTraceLog.logAll(TAG, source, result.debugTrace)
    }

    private fun preview(text: String): String {
        val oneLine = text.trim().replace('\n', '↵')
        return if (oneLine.length <= PREVIEW_CHARS) {
            oneLine
        } else {
            oneLine.take(PREVIEW_CHARS) + "…(${oneLine.length} chars)"
        }
    }
}
