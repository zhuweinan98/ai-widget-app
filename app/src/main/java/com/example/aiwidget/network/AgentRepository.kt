package com.example.aiwidget.network

import com.example.aiwidget.data.ChatResponse
import com.example.aiwidget.data.ChatSessionSummary
import com.example.aiwidget.data.ChatMessageItem
import com.example.aiwidget.data.ChatTurnRequest
import com.example.aiwidget.data.SseErrorData
import com.example.aiwidget.data.TraceEventData
import com.example.aiwidget.data.WidgetResult
import com.example.aiwidget.data.WidgetRunRequest
import com.example.aiwidget.util.AgentRequestLog
import com.example.aiwidget.util.ChatResponseLog
import com.example.aiwidget.util.ChatSyncLog
import com.example.aiwidget.util.WidgetResultLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 网络仓库：聊天与 Widget 分轨调用 widget-agent-server。
 */
class AgentRepository {
    /** `POST /api/v1/widget/run` */
    suspend fun widgetRun(
        baseUrl: String,
        apiKey: String,
        request: WidgetRunRequest,
        source: String = "widget",
    ): WidgetResult {
        AgentRequestLog.logWidget(source, baseUrl, request, stream = false)
        return RetrofitClient.createApi(baseUrl, apiKey).widgetRun(request).also {
            WidgetResultLog.log("$source/json", it)
        }
    }

    /** `POST /api/v1/chat` */
    suspend fun chatTurn(
        baseUrl: String,
        apiKey: String,
        request: ChatTurnRequest,
        source: String = "chat",
    ): ChatResponse {
        AgentRequestLog.logChat(source, baseUrl, request, stream = false)
        return RetrofitClient.createApi(baseUrl, apiKey).chatTurn(request).also {
            ChatResponseLog.log("$source/json", it)
        }
    }

    /** `POST /api/v1/chat/stream` */
    suspend fun chatStream(
        baseUrl: String,
        apiKey: String,
        request: ChatTurnRequest,
        onTrace: (String) -> Unit,
        source: String = "chat",
    ): ChatResponse =
        streamSse(
            baseUrl = baseUrl,
            apiKey = apiKey,
            path = "api/v1/chat/stream",
            bodyJson =
                RetrofitClient.moshiInstance
                    .adapter(ChatTurnRequest::class.java)
                    .toJson(request),
            source = source,
            onTrace = onTrace,
            onLogRequest = { AgentRequestLog.logChat(source, baseUrl, request, stream = true) },
            parseResult = { data ->
                RetrofitClient.moshiInstance
                    .adapter(ChatResponse::class.java)
                    .fromJson(data)
                    ?: throw ApiException("无法解析 result 事件")
            },
            onResult = { ChatResponseLog.log("$source/stream", it) },
        )

    suspend fun listChatSessions(
        baseUrl: String,
        apiKey: String,
        userId: String,
        limit: Int = 50,
        source: String = "chat/sync",
    ): List<ChatSessionSummary> {
        ChatSyncLog.logSessionsRequest(source, baseUrl, userId, limit)
        return RetrofitClient.createApi(baseUrl, apiKey).listChatSessions(userId, limit).also {
            ChatSyncLog.logSessionsResponse(source, it)
        }
    }

    suspend fun listChatMessages(
        baseUrl: String,
        apiKey: String,
        sessionId: String,
        userId: String,
        limit: Int = 200,
        source: String = "chat/sync",
    ): List<ChatMessageItem> {
        ChatSyncLog.logMessagesRequest(source, baseUrl, sessionId, userId, limit)
        return RetrofitClient.createApi(baseUrl, apiKey).listChatMessages(sessionId, userId, limit).also {
            ChatSyncLog.logMessagesResponse(source, sessionId, it)
        }
    }

    suspend fun deleteChatSession(
        baseUrl: String,
        apiKey: String,
        sessionId: String,
        userId: String,
    ) {
        RetrofitClient.createApi(baseUrl, apiKey).deleteChatSession(sessionId, userId)
    }

    private suspend fun <T> streamSse(
        baseUrl: String,
        apiKey: String,
        path: String,
        bodyJson: String,
        source: String,
        onTrace: (String) -> Unit,
        onLogRequest: () -> Unit,
        parseResult: (String) -> T,
        onResult: (T) -> Unit,
    ): T =
        withContext(Dispatchers.IO) {
            onLogRequest()
            val url = "${RetrofitClient.normalizeBaseUrl(baseUrl)}$path"
            val httpRequest =
                Request.Builder()
                    .url(url)
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .header("Content-Type", "application/json")
                    .header("Accept", "text/event-stream")
                    .header("X-API-Key", apiKey.trim())
                    .build()

            val client = RetrofitClient.createOkHttp(apiKey)
            val response = client.newCall(httpRequest).execute()
            val responseBody = response.body ?: throw ApiException("空响应体")
            if (!response.isSuccessful) {
                val errText = responseBody.string()
                throw ApiException(parseHttpError(errText, response.code))
            }

            val reader = BufferedReader(InputStreamReader(responseBody.byteStream()))
            val traces = mutableListOf<String>()
            var buffer = StringBuilder()

            fun processBlock(block: String) {
                var event = "message"
                val dataLines = mutableListOf<String>()
                for (line in block.split("\n")) {
                    when {
                        line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                        line.startsWith("data:") -> dataLines.add(line.removePrefix("data:").trim())
                    }
                }
                val data = dataLines.joinToString("")
                if (data.isEmpty()) return

                when (event) {
                    "trace" -> {
                        val parsed =
                            RetrofitClient.moshiInstance
                                .adapter(TraceEventData::class.java)
                                .fromJson(data)
                        parsed?.line?.let { line ->
                            traces.add(line)
                            onTrace(line)
                        }
                    }
                    "result" -> {
                        val result = parseResult(data)
                        val withTrace: T =
                            if (result is ChatResponse && result.debugTrace.isEmpty()) {
                                @Suppress("UNCHECKED_CAST")
                                result.copy(debugTrace = traces) as T
                            } else {
                                result
                            }
                        throw StreamComplete(withTrace as Any)
                    }
                    "error" -> {
                        val err =
                            RetrofitClient.moshiInstance
                                .adapter(SseErrorData::class.java)
                                .fromJson(data)
                        throw ApiException(err?.detail ?: "SSE 流错误")
                    }
                }
            }

            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line.isNullOrBlank()) {
                        if (buffer.isNotEmpty()) {
                            processBlock(buffer.toString())
                            buffer = StringBuilder()
                        }
                    } else {
                        buffer.append(line).append('\n')
                    }
                }
                if (buffer.isNotEmpty()) {
                    processBlock(buffer.toString())
                }
                throw ApiException("流结束但未收到 result 事件")
            } catch (e: StreamComplete) {
                @Suppress("UNCHECKED_CAST")
                val result = e.result as T
                onResult(result)
                result
            } finally {
                reader.close()
                response.close()
            }
        }

    private fun parseHttpError(body: String, code: Int): String {
        if (body.isBlank()) return "HTTP $code"
        val detailKey = "\"detail\":"
        val idx = body.indexOf(detailKey)
        if (idx >= 0) {
            val rest = body.substring(idx + detailKey.length).trim().removePrefix("\"").trim()
            val end = rest.indexOf('"')
            if (end > 0) return rest.substring(0, end)
        }
        return body.take(400)
    }
}

class ApiException(message: String) : Exception(message)

private class StreamComplete(val result: Any) : Exception()
