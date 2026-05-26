package com.example.aiwidget.util

import com.example.aiwidget.data.WidgetResult

/** 打印 Agent 终局 [WidgetResult] 各字段（与 Moshi 映射一致，不做 UI 拼接推测）。 */
object WidgetResultLog {
    private const val TAG = "WidgetResult"
    private const val PREVIEW_CHARS = 320
    private const val TRACE_HEAD_LINES = 3

    fun log(source: String, result: WidgetResult) {
        val title = result.title
        val content = result.content
        val errorMsg = result.errorMsg
        val traces = result.debugTrace

        AppLog.i(
            TAG,
            "[$source] status=${result.status} updated_at=${result.updatedAt.ifBlank { "-" }} " +
                "can_follow_up=${result.canFollowUp} " +
                "titleLen=${title.length} contentLen=${content.length} " +
                "errorLen=${errorMsg.length} traceLines=${traces.size}",
        )
        if (title.isNotBlank()) {
            AppLog.d(TAG, "[$source] title=${preview(title)}")
        }
        if (content.isNotBlank()) {
            AppLog.d(TAG, "[$source] content=${preview(content)}")
        }
        if (errorMsg.isNotBlank()) {
            AppLog.w(TAG, "[$source] error_msg=${preview(errorMsg)}")
        }
        if (traces.isNotEmpty()) {
            traces.take(TRACE_HEAD_LINES).forEachIndexed { index, line ->
                AppLog.d(TAG, "[$source] debug_trace[$index]=${preview(line)}")
            }
            if (traces.size > TRACE_HEAD_LINES) {
                AppLog.d(TAG, "[$source] debug_trace[…]=${traces.size - TRACE_HEAD_LINES} more lines")
            }
        }
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
