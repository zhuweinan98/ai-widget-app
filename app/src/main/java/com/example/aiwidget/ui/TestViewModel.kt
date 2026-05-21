package com.example.aiwidget.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwidget.data.ChatRequest
import com.example.aiwidget.data.Presets
import com.example.aiwidget.data.SkillMetadata
import com.example.aiwidget.data.TestPrefs
import com.example.aiwidget.data.WidgetResult
import com.example.aiwidget.network.AgentRepository
import com.example.aiwidget.network.ApiException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 测试页 UI 状态（单向数据流：ViewModel → Compose）。 */
data class TestUiState(
    val baseUrl: String = Presets.DEFAULT_BASE_URL,
    val apiKey: String = Presets.DEFAULT_API_KEY,
    val userId: String = Presets.DEFAULT_USER_ID,
    val message: String = Presets.DEFAULT_MESSAGE,
    val useStream: Boolean = false,
    val dailyDate: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
    val skills: List<SkillMetadata> = emptyList(),
    val skillsLoading: Boolean = false,
    val skillsError: String? = null,
    val isSending: Boolean = false,
    val statusLine: String? = null,
    val errorLine: String? = null,
    val result: WidgetResult? = null,
    val liveTrace: List<String> = emptyList(),
)

/**
 * 测试页逻辑：对应浏览器 `/test` 的交互。
 *
 * 职责：读写 [TestPrefs]、调 [AgentRepository]、暴露 [uiState] 给 [TestScreen]。
 */
class TestViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = TestPrefs(application)
    private val repo = AgentRepository()

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<TestUiState> = _uiState.asStateFlow()

    init {
        refreshSkills()
    }

    /** 从 SharedPreferences 恢复上次配置。 */
    private fun loadInitialState(): TestUiState =
        TestUiState(
            baseUrl = prefs.baseUrl,
            apiKey = prefs.apiKey,
            userId = prefs.userId,
            message = prefs.message,
            useStream = prefs.useStream,
        )

    /** 将当前表单写入本地。 */
    private fun persist() {
        val s = _uiState.value
        prefs.baseUrl = s.baseUrl
        prefs.apiKey = s.apiKey
        prefs.userId = s.userId
        prefs.message = s.message
        prefs.useStream = s.useStream
    }

    fun updateBaseUrl(v: String) = _uiState.update { it.copy(baseUrl = v) }
    fun updateApiKey(v: String) = _uiState.update { it.copy(apiKey = v) }
    fun updateUserId(v: String) = _uiState.update { it.copy(userId = v) }
    fun updateMessage(v: String) = _uiState.update { it.copy(message = v) }
    fun updateUseStream(v: Boolean) = _uiState.update { it.copy(useStream = v) }
    fun updateDailyDate(v: String) = _uiState.update { it.copy(dailyDate = v) }

    /** 发送前调用，持久化 API 地址等。 */
    fun onConfigChanged() = persist()

    /** 快捷芯片 / 日报按钮：替换 message 文本。 */
    fun setMessage(text: String) = _uiState.update { it.copy(message = text) }

    /** 按 [TestUiState.dailyDate] 生成指定日期日报的 message。 */
    fun applyDailyDateMessage() {
        val date = _uiState.value.dailyDate
        if (date.isBlank()) {
            _uiState.update { it.copy(errorLine = "请先选择日期") }
            return
        }
        setMessage(Presets.messageForDailyDate(date))
    }

    /** `GET /api/v1/skills`；aihot 已有专用芯片故从列表排除。 */
    fun refreshSkills() {
        viewModelScope.launch {
            val s = _uiState.value
            persist()
            _uiState.update { it.copy(skillsLoading = true, skillsError = null) }
            try {
                val skills = repo.getSkills(s.baseUrl, s.apiKey)
                _uiState.update {
                    it.copy(
                        skills = skills.filter { sk -> sk.name != "aihot" },
                        skillsLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        skillsLoading = false,
                        skillsError = "加载 Skills 失败: ${e.message}",
                    )
                }
            }
        }
    }

    /**
     * 发起对话：根据 [TestUiState.useStream] 走 JSON 或 SSE。
     */
    fun send() {
        val s = _uiState.value
        val msg = s.message.trim()
        if (msg.isEmpty()) {
            _uiState.update { it.copy(errorLine = "message 不能为空") }
            return
        }
        persist()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSending = true,
                    errorLine = null,
                    statusLine = "请求中…（Ollama 可能需 1～3 分钟）",
                    result = null,
                    liveTrace = emptyList(),
                )
            }
            try {
                val request = ChatRequest(userId = s.userId.trim().ifEmpty { "u1" }, message = msg)
                val result =
                    if (s.useStream) {
                        repo.chatStream(s.baseUrl, s.apiKey, request) { line ->
                            _uiState.update { st ->
                                st.copy(
                                    liveTrace = st.liveTrace + line,
                                    statusLine = "SSE 进行中…（${st.liveTrace.size + 1} 条 trace）",
                                    result =
                                        WidgetResult(
                                            title = "处理中…",
                                            content = "等待 Agent 完成…",
                                            status = "running",
                                        ),
                                )
                            }
                        }
                    } else {
                        repo.chat(s.baseUrl, s.apiKey, request)
                    }
                _uiState.update {
                    it.copy(
                        isSending = false,
                        statusLine = "完成",
                        result = result,
                        liveTrace = result.debugTrace,
                    )
                }
            } catch (e: ApiException) {
                _uiState.update {
                    it.copy(isSending = false, errorLine = e.message, statusLine = null)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSending = false, errorLine = e.message ?: e.toString(), statusLine = null)
                }
            }
        }
    }
}
