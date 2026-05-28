package com.example.aiwidget.util

import com.example.aiwidget.data.ChatMessageItem
import com.example.aiwidget.data.ChatSessionSummary
import com.example.aiwidget.network.RetrofitClient

/** `GET /chat/sessions` 与 `GET /chat/sessions/{id}/messages` 的请求与响应摘要。 */
object ChatSyncLog {
    private const val TAG = "ChatSync"
    private const val PREVIEW_CHARS = 200
    private const val MESSAGE_HEAD = 8

    fun logSessionsRequest(
        source: String,
        baseUrl: String,
        userId: String,
        limit: Int,
    ) {
        AppLog.i(
            TAG,
            "[$source] → GET chat/sessions user_id=${userId.trim()} limit=$limit",
        )
        AppLog.d(
            TAG,
            "[$source] url=${RetrofitClient.normalizeBaseUrl(baseUrl)}api/v1/chat/sessions",
        )
    }

    fun logSessionsResponse(
        source: String,
        sessions: List<ChatSessionSummary>,
    ) {
        AppLog.i(TAG, "[$source] ← sessions count=${sessions.size}")
        sessions.forEachIndexed { index, session ->
            AppLog.d(
                TAG,
                "[$source] sessions[$index] session_id=${session.sessionId} " +
                    "title=${preview(session.title)} updated_at=${session.updatedAt}",
            )
        }
    }

    fun logMessagesRequest(
        source: String,
        baseUrl: String,
        sessionId: String,
        userId: String,
        limit: Int,
    ) {
        AppLog.i(
            TAG,
            "[$source] → GET chat/sessions/{id}/messages session_id=$sessionId " +
                "user_id=${userId.trim()} limit=$limit",
        )
        AppLog.d(
            TAG,
            "[$source] url=${RetrofitClient.normalizeBaseUrl(baseUrl)}api/v1/chat/sessions/$sessionId/messages",
        )
    }

    fun logMessagesResponse(
        source: String,
        sessionId: String,
        messages: List<ChatMessageItem>,
    ) {
        AppLog.i(
            TAG,
            "[$source] ← messages session_id=$sessionId count=${messages.size}",
        )
        messages.take(MESSAGE_HEAD).forEachIndexed { index, item ->
            AppLog.d(
                TAG,
                "[$source] messages[$index] id=${item.id} role=${item.role} " +
                    "created_at=${item.createdAt} content=${preview(item.content)}",
            )
        }
        if (messages.size > MESSAGE_HEAD) {
            AppLog.d(TAG, "[$source] messages[…]=${messages.size - MESSAGE_HEAD} more")
        }
    }

    fun logFailure(
        source: String,
        phase: String,
        error: Throwable,
    ) {
        AppLog.w(TAG, "[$source] $phase failed: ${error.message ?: error.toString()}")
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
