package com.example.aiwidget.data

import com.squareup.moshi.Json

/** `POST /api/v1/widget/run` 请求体（无 session_id）。 */
data class WidgetRunRequest(
    @Json(name = "user_id") val userId: String,
    @Json(name = "message") val message: String,
)

/** `POST /api/v1/chat` 请求体。 */
data class ChatTurnRequest(
    @Json(name = "user_id") val userId: String,
    @Json(name = "session_id") val sessionId: String? = null,
    @Json(name = "message") val message: String,
)

/**
 * Widget 终局返回（`POST /api/v1/widget/run`）。
 *
 * 终局 JSON 形状由 widget-agent-server 约束；App 用 Moshi 映射本 data class。
 *
 * @param status `"ok"` 成功，`"error"` 失败
 */
data class WidgetResult(
    @Json(name = "title") val title: String,
    @Json(name = "content") val content: String,
    @Json(name = "template") val template: String = "",
    @Json(name = "headline") val headline: String = "",
    @Json(name = "subtitle") val subtitle: String = "",
    @Json(name = "image_url") val imageUrl: String = "",
    @Json(name = "items") val items: List<WidgetListItem> = emptyList(),
    @Json(name = "status") val status: String = "ok",
    @Json(name = "error_msg") val errorMsg: String = "",
    @Json(name = "can_follow_up") val canFollowUp: Boolean = true,
    @Json(name = "updated_at") val updatedAt: String = "",
    @Json(name = "debug_trace") val debugTrace: List<String> = emptyList(),
)

/**
 * 聊天终局返回（`POST /api/v1/chat` / SSE `result`）。
 */
data class ChatResponse(
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "title") val title: String,
    @Json(name = "content") val content: String,
    @Json(name = "status") val status: String = "ok",
    @Json(name = "error_msg") val errorMsg: String = "",
    @Json(name = "can_follow_up") val canFollowUp: Boolean = true,
    @Json(name = "updated_at") val updatedAt: String = "",
    @Json(name = "debug_trace") val debugTrace: List<String> = emptyList(),
)

/** `GET /api/v1/chat/sessions` 列表项。 */
data class ChatSessionSummary(
    @Json(name = "session_id") val sessionId: String,
    @Json(name = "title") val title: String,
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "updated_at") val updatedAt: String,
)

/** `GET /api/v1/chat/sessions/{id}/messages` 列表项。 */
data class ChatMessageItem(
    @Json(name = "id") val id: Long,
    @Json(name = "role") val role: String,
    @Json(name = "content") val content: String,
    @Json(name = "created_at") val createdAt: String = "",
)

/** SSE `event: trace` 的 data 载荷。 */
data class TraceEventData(
    @Json(name = "line") val line: String? = null,
)

/** SSE `event: error` 的 data 载荷。 */
data class SseErrorData(
    @Json(name = "detail") val detail: String? = null,
)
