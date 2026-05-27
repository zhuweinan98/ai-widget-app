package com.example.aiwidget.util

import com.example.aiwidget.data.ChatResponse

/** 打印聊天终局 [ChatResponse]（与 Moshi 映射一致）。 */
object ChatResponseLog {
    private const val TAG = "ChatResponse"
    private const val PREVIEW_CHARS = 320

    fun log(source: String, result: ChatResponse) {
        val title = result.title
        val content = result.content
        val errorMsg = result.errorMsg

        AppLog.i(
            TAG,
            "[$source] session_id=${result.sessionId} status=${result.status} " +
                "updated_at=${result.updatedAt.ifBlank { "-" }} " +
                "can_follow_up=${result.canFollowUp} " +
                "titleLen=${title.length} contentLen=${content.length} " +
                "errorLen=${errorMsg.length} traceLines=${result.debugTrace.size}",
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
