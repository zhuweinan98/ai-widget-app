package com.example.aiwidget.app

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwidget.data.AppPrefs
import com.example.aiwidget.data.ChatRequest
import com.example.aiwidget.data.Presets
import com.example.aiwidget.data.SessionPrefs
import com.example.aiwidget.data.WidgetResult
import com.example.aiwidget.data.WidgetRunLogEntry
import com.example.aiwidget.data.WidgetRunLogStore
import com.example.aiwidget.data.WidgetTaskStore
import com.example.aiwidget.network.AgentRepository
import com.example.aiwidget.network.ApiException
import com.example.aiwidget.homewidget.HomeWidgetCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * App 内界面导航目的地：对话页 / 设置页。
 * 不含业务逻辑，仅作 [AppShellUiState.route] 的枚举值。
 */
enum class AppDestination {
    /** Agent 对话（发 prompt、看 SSE trace）。 */
    Chat,

    /** API 环境、Widget 定时任务、定时执行记录。 */
    Settings,
}

/**
 * App 内界面（对话 + 设置）的全部状态。
 *
 * 对话相关字段只在 [AppDestination.Chat] 使用；
 * 桌面 Widget 任务编辑字段主要在 [AppDestination.Settings] 使用。
 */
data class AppShellUiState(
    /** 后端 baseUrl，与 [AppPrefs] 同步。 */
    val baseUrl: String = Presets.DEFAULT_BASE_URL,
    /** X-API-Key，与 [AppPrefs] 同步。 */
    val apiKey: String = Presets.DEFAULT_API_KEY,
    /** 对话与 Widget 共用的 user_id，与 [AppPrefs] 同步；首次启动为 UUID。 */
    val userId: String = "",
    /** 输入框当前 prompt，不持久化。 */
    val message: String = "",
    /** true 时使用 SSE 流式接口，trace 写入 [agentTraceLines]。 */
    val useStream: Boolean = true,
    /** 设置页「高级」区是否展开（手填 URL / Key）。 */
    val advancedExpanded: Boolean = false,
    /** 是否正在等待 Agent 响应。 */
    val isSending: Boolean = false,
    /** 保存 Widget 任务时的校验错误，显示在设置页。 */
    val widgetTaskSaveError: String? = null,
    val chatMessages: List<ChatMessage> = emptyList(),
    /** 已展开全文的用户消息 id。 */
    val expandedChatMessageIds: Set<String> = emptySet(),
    /** 当前或最近一次请求的 Agent trace 行（SSE 实时追加）。 */
    val agentTraceLines: List<String> = emptyList(),
    /** 设置页 Widget 任务编辑表单，与 [WidgetTaskStore] 对应。 */
    val widgetTaskEditorRows: List<WidgetTaskEditorRow> = emptyList(),
    /** Widget 定时任务执行历史（仅 periodic），供设置页展示。 */
    val widgetPeriodicRunLogs: List<WidgetRunLogEntry> = emptyList(),
    val route: AppDestination = AppDestination.Chat,
)

/**
 * 设置页中单条 Widget 任务的编辑态。
 * 间隔/TTL 用 [String] 以便绑定 TextField；保存时再解析为数字。
 */
data class WidgetTaskEditorRow(
    val id: String,
    val title: String,
    val prompt: String,
    val intervalMinutes: String,
    val cacheTtlSeconds: String,
    /** false 时不登记 WorkManager 定时。 */
    val enabled: Boolean = true,
)

/**
 * App 内界面状态机：对话发送、会话配置、桌面 Widget 任务编辑。
 *
 * 依赖：
 * - [SessionPrefs]：SSE 开关；API / userId 代理到 [AppPrefs]
 * - [AppPrefs]：统一 user_id
 * - [AgentRepository]：HTTP / SSE
 * - [HomeWidgetCoordinator]：保存任务后刷新定时与桌面展示
 */
class AppShellViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionPrefs = SessionPrefs(application)
    private val appPrefs = AppPrefs(application)
    private val agentRepository = AgentRepository()

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<AppShellUiState> = _uiState.asStateFlow()

    private fun loadInitialState(): AppShellUiState =
        AppShellUiState(
            baseUrl = sessionPrefs.baseUrl,
            apiKey = sessionPrefs.apiKey,
            userId = sessionPrefs.userId,
            useStream = sessionPrefs.useStream,
            widgetTaskEditorRows = loadWidgetTaskEditorRows(),
            widgetPeriodicRunLogs = loadWidgetPeriodicRunLogs(),
        )

    /** 进入设置页；刷新 Widget 任务与定时执行记录。 */
    fun openSettings() {
        refreshWidgetStatusPanel()
        _uiState.update { it.copy(route = AppDestination.Settings) }
    }

    /** 返回对话页；持久化环境配置，并丢弃未保存的任务编辑错误提示。 */
    fun closeSettings() {
        saveSessionSettings()
        _uiState.update {
            it.copy(
                route = AppDestination.Chat,
                widgetTaskEditorRows = loadWidgetTaskEditorRows(),
                widgetTaskSaveError = null,
            )
        }
    }

    /** 从磁盘重载 Widget 任务列表与定时执行记录。 */
    fun refreshWidgetStatusPanel() {
        _uiState.update {
            it.copy(
                widgetPeriodicRunLogs = loadWidgetPeriodicRunLogs(),
                widgetTaskEditorRows = loadWidgetTaskEditorRows(),
            )
        }
    }

    private fun loadWidgetPeriodicRunLogs(): List<WidgetRunLogEntry> =
        WidgetRunLogStore(getApplication()).loadRecent()

    private fun loadWidgetTaskEditorRows(): List<WidgetTaskEditorRow> {
        val store = WidgetTaskStore(getApplication())
        return store.loadTasks().map { task ->
            WidgetTaskEditorRow(
                id = task.id,
                title = task.title,
                prompt = task.prompt,
                intervalMinutes = store.intervalMinutes(task).toString(),
                cacheTtlSeconds = store.cacheTtlSeconds(task).toString(),
                enabled = task.enabled,
            )
        }
    }

    fun updateTaskTitle(taskId: String, value: String) {
        updateWidgetTaskEditorRow(taskId) { it.copy(title = value) }
    }

    fun updateTaskPrompt(taskId: String, value: String) {
        updateWidgetTaskEditorRow(taskId) { it.copy(prompt = value) }
    }

    fun updateTaskEnabled(taskId: String, enabled: Boolean) {
        updateWidgetTaskEditorRow(taskId) { it.copy(enabled = enabled) }
    }

    fun updateTaskInterval(taskId: String, value: String) {
        updateWidgetTaskEditorRow(taskId) { it.copy(intervalMinutes = value) }
    }

    fun updateTaskCacheTtl(taskId: String, value: String) {
        updateWidgetTaskEditorRow(taskId) { it.copy(cacheTtlSeconds = value) }
    }

    private fun updateWidgetTaskEditorRow(
        taskId: String,
        transform: (WidgetTaskEditorRow) -> WidgetTaskEditorRow,
    ) {
        _uiState.update { state ->
            state.copy(
                widgetTaskEditorRows =
                    state.widgetTaskEditorRows.map { row ->
                        if (row.id == taskId) transform(row) else row
                    },
            )
        }
    }

    /** 将 API / 会话字段写入 [SessionPrefs]（及 [AppPrefs] 中的 baseUrl/apiKey）。 */
    private fun saveSessionSettings() {
        val s = _uiState.value
        sessionPrefs.baseUrl = s.baseUrl
        sessionPrefs.apiKey = s.apiKey
        sessionPrefs.userId = s.userId
        sessionPrefs.useStream = s.useStream
    }

    fun toggleAdvancedExpanded() =
        _uiState.update { it.copy(advancedExpanded = !it.advancedExpanded) }

    fun updateBaseUrl(value: String) {
        _uiState.update { it.copy(baseUrl = value) }
        saveSessionSettings()
    }

    fun updateApiKey(value: String) {
        _uiState.update { it.copy(apiKey = value) }
        saveSessionSettings()
    }

    fun updateUserId(value: String) {
        _uiState.update { it.copy(userId = value) }
        saveSessionSettings()
    }

    fun updateMessage(value: String) = _uiState.update { it.copy(message = value) }

    fun updateUseStream(value: Boolean) {
        _uiState.update { it.copy(useStream = value) }
        saveSessionSettings()
    }

    fun toggleChatMessageExpanded(messageId: String) {
        _uiState.update { state ->
            val expanded = state.expandedChatMessageIds.toMutableSet()
            if (!expanded.add(messageId)) {
                expanded.remove(messageId)
            }
            state.copy(expandedChatMessageIds = expanded)
        }
    }

    fun clearChat() {
        _uiState.update {
            it.copy(
                chatMessages = emptyList(),
                expandedChatMessageIds = emptySet(),
                agentTraceLines = emptyList(),
            )
        }
    }

    /**
     * 校验并持久化一条 Widget 任务，并按需重新登记 WorkManager。
     * @param showToast 保存成功时 Toast
     * @param reschedule 是否刷新定时与桌面 Widget
     */
    fun saveWidgetTask(taskId: String) {
        commitWidgetTaskEditorRow(taskId, showToast = true, reschedule = true)
    }

    /** 恢复为内置默认任务列表（一条 1h 速报）。 */
    fun resetWidgetTasksToDefaults() {
        val store = WidgetTaskStore(getApplication())
        store.saveTasks(store.defaultTasks())
        _uiState.update {
            it.copy(
                widgetTaskEditorRows = loadWidgetTaskEditorRows(),
                widgetTaskSaveError = null,
            )
        }
        HomeWidgetCoordinator.scheduleEnabledWidgetTasks(getApplication(), showScheduleToast = true)
        HomeWidgetCoordinator.renderAllWidgets(getApplication())
        Toast.makeText(getApplication(), "已恢复任务默认配置", Toast.LENGTH_SHORT).show()
    }

    /** 发送前持久化会话配置（与 [saveSessionSettings] 相同，供 UI 显式调用）。 */
    fun persistSessionSettings() = saveSessionSettings()

    fun switchToLocalBackend() = applyBackendBaseUrl(Presets.LOCAL_BASE_URL, "本地")

    fun switchToRemoteBackend() = applyBackendBaseUrl(Presets.SERVER_BASE_URL, "服务器")

    /** 快捷芯片：填入预设 prompt 并立即发送。 */
    fun sendChatPreset(message: String, chipLabel: String) {
        val summary = "【$chipLabel】${summarizePrompt(message, 36)}"
        sendChatMessage(overrideMessage = message, userMessageSummary = summary)
    }

    /**
     * 向 Agent 发送一条 message。
     * @param overrideMessage 非空时忽略输入框（快捷芯片）
     * @param userMessageSummary 用户气泡摘要；默认取 prompt 首行截断
     */
    fun sendChatMessage(
        overrideMessage: String? = null,
        userMessageSummary: String? = null,
    ) {
        val state = _uiState.value
        val message = (overrideMessage ?: state.message).trim()
        if (message.isEmpty()) {
            appendAgentErrorMessage("message 不能为空")
            return
        }
        if (overrideMessage == null) {
            saveSessionSettings()
        }
        _uiState.update {
            it.copy(message = if (overrideMessage == null) "" else it.message)
        }
        appendUserChatMessage(message, userMessageSummary)
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, agentTraceLines = emptyList()) }
            try {
                val request =
                    ChatRequest(
                        userId = state.userId.trim().ifEmpty { appPrefs.getOrCreateUserId() },
                        message = message,
                    )
                val result =
                    if (state.useStream) {
                        agentRepository.chatStream(
                            baseUrl = state.baseUrl,
                            apiKey = state.apiKey,
                            request = request,
                            source = "chat",
                            onTrace = { line -> appendAgentTraceLine(line) },
                        )
                    } else {
                        agentRepository.chat(
                            state.baseUrl,
                            state.apiKey,
                            request,
                            source = "chat",
                        )
                    }
                val traceLines =
                    if (result.debugTrace.isNotEmpty()) {
                        result.debugTrace
                    } else {
                        _uiState.value.agentTraceLines
                    }
                setAgentTraceLines(traceLines)
                appendChat(chatMessageFromWidgetResult(result))
            } catch (e: ApiException) {
                appendAgentErrorMessage(e.message ?: "请求失败")
            } catch (e: Exception) {
                appendAgentErrorMessage(e.message ?: e.toString())
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    private fun applyBackendBaseUrl(url: String, envLabel: String) {
        _uiState.update { it.copy(baseUrl = url) }
        saveSessionSettings()
        Toast.makeText(getApplication(), "已切换到$envLabel", Toast.LENGTH_SHORT).show()
    }

    private fun commitWidgetTaskEditorRow(
        taskId: String,
        showToast: Boolean,
        reschedule: Boolean,
    ): Boolean {
        val store = WidgetTaskStore(getApplication())
        val tasks = store.loadTasks().toMutableList()
        val index = tasks.indexOfFirst { it.id == taskId }
        val row = _uiState.value.widgetTaskEditorRows.find { it.id == taskId }
        if (index < 0 || row == null) {
            _uiState.update { it.copy(widgetTaskSaveError = "任务不存在") }
            return false
        }
        val existing = tasks[index]
        val interval = row.intervalMinutes.trim().toLongOrNull()
        val ttl = row.cacheTtlSeconds.trim().toIntOrNull()
        val title = row.title.trim().ifBlank { existing.title }
        val prompt = row.prompt.trim()
        if (prompt.isEmpty()) {
            if (showToast || reschedule) {
                _uiState.update { it.copy(widgetTaskSaveError = "「$title」prompt 不能为空") }
            }
            return false
        }
        if (interval == null || interval < 1) {
            if (showToast || reschedule) {
                _uiState.update {
                    it.copy(widgetTaskSaveError = "「$title」间隔须为 ≥1 的整数（分钟）")
                }
            }
            return false
        }
        if (ttl == null || ttl < 0) {
            if (showToast || reschedule) {
                _uiState.update {
                    it.copy(widgetTaskSaveError = "「$title」缓存 TTL 须为 ≥0 的整数（秒）")
                }
            }
            return false
        }
        tasks[index] =
            existing.copy(
                title = title,
                prompt = prompt,
                intervalMinutes = interval,
                cacheTtlSeconds = ttl,
                enabled = row.enabled,
            )
        store.saveTasks(tasks)
        _uiState.update {
            it.copy(
                widgetTaskSaveError = null,
                widgetTaskEditorRows = loadWidgetTaskEditorRows(),
            )
        }
        if (reschedule) {
            HomeWidgetCoordinator.scheduleEnabledWidgetTasks(getApplication(), showScheduleToast = false)
            HomeWidgetCoordinator.renderAllWidgets(getApplication())
        }
        if (showToast) {
            Toast.makeText(getApplication(), "已保存：$title", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun appendChat(message: ChatMessage) {
        _uiState.update { it.copy(chatMessages = it.chatMessages + message) }
    }

    private fun appendUserChatMessage(fullPrompt: String, summary: String? = null) {
        val displaySummary = summary ?: summarizePrompt(fullPrompt)
        appendChat(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.User,
                kind = ChatKind.UserPrompt,
                summary = displaySummary,
                fullText = fullPrompt,
            ),
        )
    }

    private fun appendAgentTraceLine(line: String) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        _uiState.update { it.copy(agentTraceLines = it.agentTraceLines + trimmed) }
    }

    private fun setAgentTraceLines(lines: List<String>) {
        _uiState.update {
            it.copy(agentTraceLines = lines.map { line -> line.trim() }.filter { line -> line.isNotEmpty() })
        }
    }

    private fun appendAgentErrorMessage(text: String) {
        appendChat(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.Agent,
                kind = ChatKind.Error,
                summary = text,
            ),
        )
    }
}
