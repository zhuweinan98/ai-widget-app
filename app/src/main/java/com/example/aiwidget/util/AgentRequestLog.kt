package com.example.aiwidget.util

import com.example.aiwidget.data.ChatTurnRequest
import com.example.aiwidget.data.WidgetRunRequest
import com.example.aiwidget.network.RetrofitClient

/** 发往 widget-agent-server 的请求摘要（不含 API Key）。 */
object AgentRequestLog {
    private const val TAG = "AgentRequest"
    private const val MSG_PREVIEW = 200

    fun logWidget(
        source: String,
        baseUrl: String,
        request: WidgetRunRequest,
        stream: Boolean,
    ) {
        val path = if (stream) "api/v1/widget/run/stream" else "api/v1/widget/run"
        logInternal(source, baseUrl, path, request.userId, request.message, sessionId = null)
    }

    fun logChat(
        source: String,
        baseUrl: String,
        request: ChatTurnRequest,
        stream: Boolean,
    ) {
        val path = if (stream) "api/v1/chat/stream" else "api/v1/chat"
        logInternal(source, baseUrl, path, request.userId, request.message, request.sessionId)
    }

    private fun logInternal(
        source: String,
        baseUrl: String,
        path: String,
        userId: String,
        message: String,
        sessionId: String?,
    ) {
        val trimmed = message.trim()
        val sessionPart = sessionId?.let { " session_id=$it" }.orEmpty()
        AppLog.i(
            TAG,
            "[$source] → POST $path user_id=${userId.trim()}$sessionPart msgLen=${trimmed.length}",
        )
        AppLog.d(TAG, "[$source] url=${RetrofitClient.normalizeBaseUrl(baseUrl)}$path")
        if (trimmed.isNotEmpty()) {
            AppLog.d(TAG, "[$source] message=${preview(trimmed)}")
        }
    }

    private fun preview(text: String): String {
        val oneLine = text.replace('\n', '↵')
        return if (oneLine.length <= MSG_PREVIEW) {
            oneLine
        } else {
            oneLine.take(MSG_PREVIEW) + "…"
        }
    }
}
