package com.example.aiwidget.network

import com.example.aiwidget.data.ChatRequest
import com.example.aiwidget.data.SkillMetadata
import com.example.aiwidget.data.WidgetResult
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * 后端 REST 接口（技术方案 §八）。
 *
 * SSE 流式接口不在此声明，由 [AgentRepository.chatStream] 用 OkHttp 直接请求。
 */
interface ApiService {
    /** 统一对话入口：实时对话 / 预设 / Widget 刷新均用此接口。 */
    @POST("api/v1/agent/chat")
    suspend fun chat(@Body request: ChatRequest): WidgetResult

    /** Skill 元数据列表（测试页芯片、MainActivity 启动拉取）。 */
    @GET("api/v1/skills")
    suspend fun getSkills(): List<SkillMetadata>
}
