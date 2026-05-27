package com.example.aiwidget.network

import com.example.aiwidget.data.ChatMessageItem
import com.example.aiwidget.data.ChatResponse
import com.example.aiwidget.data.ChatSessionSummary
import com.example.aiwidget.data.ChatTurnRequest
import com.example.aiwidget.data.WidgetResult
import com.example.aiwidget.data.WidgetRunRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 后端 REST 接口。
 *
 * - 聊天：`/api/v1/chat`（带 session_id）
 * - Widget：`/api/v1/widget/run`（无 session_id）
 *
 * SSE 由 [AgentRepository] 用 OkHttp 直接请求。
 */
interface ApiService {
    @POST("api/v1/chat")
    suspend fun chatTurn(@Body body: ChatTurnRequest): ChatResponse

    @GET("api/v1/chat/sessions")
    suspend fun listChatSessions(
        @Query("user_id") userId: String,
        @Query("limit") limit: Int = 50,
    ): List<ChatSessionSummary>

    @GET("api/v1/chat/sessions/{sessionId}/messages")
    suspend fun listChatMessages(
        @Path("sessionId") sessionId: String,
        @Query("user_id") userId: String,
        @Query("limit") limit: Int = 200,
    ): List<ChatMessageItem>

    @DELETE("api/v1/chat/sessions/{sessionId}")
    suspend fun deleteChatSession(
        @Path("sessionId") sessionId: String,
        @Query("user_id") userId: String,
    )

    @POST("api/v1/widget/run")
    suspend fun widgetRun(@Body body: WidgetRunRequest): WidgetResult
}
