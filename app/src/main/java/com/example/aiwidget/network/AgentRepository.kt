package com.example.aiwidget.network

import com.example.aiwidget.data.ChatRequest
import com.example.aiwidget.data.SkillMetadata
import com.example.aiwidget.data.SseErrorData
import com.example.aiwidget.data.TraceEventData
import com.example.aiwidget.data.WidgetResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 网络仓库：封装对 widget-agent-server 的调用。
 *
 * ViewModel 只依赖本类，不直接操作 Retrofit/OkHttp。
 */
class AgentRepository {
    /** 拉取 Skill 列表。 */
    suspend fun getSkills(baseUrl: String, apiKey: String): List<SkillMetadata> =
        RetrofitClient.createApi(baseUrl, apiKey).getSkills()

    /** 一次性 JSON 对话（`POST /api/v1/agent/chat`）。 */
    suspend fun chat(baseUrl: String, apiKey: String, request: ChatRequest): WidgetResult =
        RetrofitClient.createApi(baseUrl, apiKey).chat(request)

    /**
     * SSE 流式对话（`POST /api/v1/agent/chat/stream`）。
     *
     * @param onTrace 每收到 `event: trace` 回调一行，用于 UI 实时追加
     * @return 终局 `event: result` 解析为 [WidgetResult]
     */
    suspend fun chatStream(
        baseUrl: String,
        apiKey: String,
        request: ChatRequest,
        onTrace: (String) -> Unit,
    ): WidgetResult =
        withContext(Dispatchers.IO) {
            val bodyJson =
                RetrofitClient.moshiInstance.adapter(ChatRequest::class.java).toJson(request)
            val url = "${RetrofitClient.normalizeBaseUrl(baseUrl)}api/v1/agent/chat/stream"
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
                        val result =
                            RetrofitClient.moshiInstance
                                .adapter(WidgetResult::class.java)
                                .fromJson(data)
                                ?: throw ApiException("无法解析 result 事件")
                        throw StreamComplete(
                            result.copy(
                                debugTrace =
                                    if (result.debugTrace.isEmpty()) traces else result.debugTrace,
                            ),
                        )
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
                e.result
            } finally {
                reader.close()
                response.close()
            }
        }

    /** 从 FastAPI 错误 JSON 中提取 `detail` 字段。 */
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

/** 后端或网络层返回的业务错误。 */
class ApiException(message: String) : Exception(message)

/** 内部：SSE 收到 result 事件后携带终局数据。 */
private class StreamComplete(val result: WidgetResult) : Exception()
