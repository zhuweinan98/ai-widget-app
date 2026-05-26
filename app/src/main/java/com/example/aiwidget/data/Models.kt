package com.example.aiwidget.data

import com.squareup.moshi.Json

/**
 * 与后端 `POST /api/v1/agent/chat` 请求体一致（技术方案 §五）。
 */
data class ChatRequest(
    @Json(name = "user_id") val userId: String,
    @Json(name = "message") val message: String,
)

/**
 * Agent 终局返回；对话页与 Widget 展示共用（技术方案 §5.1）。
 *
 * @param status `"ok"` 成功，`"error"` 失败；流式进行中 UI 可能临时用 `"running"`
 */
data class WidgetResult(
    @Json(name = "title") val title: String,
    @Json(name = "content") val content: String,
    @Json(name = "status") val status: String = "ok",
    @Json(name = "error_msg") val errorMsg: String = "",
    @Json(name = "can_follow_up") val canFollowUp: Boolean = true,
    @Json(name = "updated_at") val updatedAt: String = "",
    @Json(name = "debug_trace") val debugTrace: List<String> = emptyList(),
)

/** SSE `event: trace` 的 data 载荷。 */
data class TraceEventData(
    @Json(name = "line") val line: String? = null,
)

/** SSE `event: error` 的 data 载荷。 */
data class SseErrorData(
    @Json(name = "detail") val detail: String? = null,
)
