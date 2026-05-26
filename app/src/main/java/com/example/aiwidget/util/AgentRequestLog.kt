package com.example.aiwidget.util

import com.example.aiwidget.data.ChatRequest
import com.example.aiwidget.network.RetrofitClient

/** 发往 widget-agent-server 的对话请求（入参摘要，不含 API Key）。 */
object AgentRequestLog {
    private const val TAG = "AgentRequest"
    private const val MSG_PREVIEW = 200

    fun log(
        source: String,
        baseUrl: String,
        request: ChatRequest,
        stream: Boolean,
    ) {
        val path = if (stream) "api/v1/agent/chat/stream" else "api/v1/agent/chat"
        val message = request.message.trim()
        AppLog.i(
            TAG,
            "[$source] → POST $path user_id=${request.userId.trim()} msgLen=${message.length}",
        )
        AppLog.d(
            TAG,
            "[$source] url=${RetrofitClient.normalizeBaseUrl(baseUrl)}$path",
        )
        if (message.isNotEmpty()) {
            AppLog.d(TAG, "[$source] message=${preview(message)}")
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
