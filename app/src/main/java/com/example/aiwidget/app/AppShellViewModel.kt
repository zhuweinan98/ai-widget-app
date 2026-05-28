package com.example.aiwidget.app

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.aiwidget.R
import com.example.aiwidget.data.AppPrefs
import com.example.aiwidget.data.ChatLocalStore
import com.example.aiwidget.data.ChatResponse
import com.example.aiwidget.data.ChatSessionSummary
import com.example.aiwidget.data.ChatTurnRequest
import com.example.aiwidget.data.Presets
import com.example.aiwidget.data.SessionPrefs
import com.example.aiwidget.data.StoredChatMessage
import com.example.aiwidget.data.WidgetRunLogEntry
import com.example.aiwidget.data.WidgetRunLogStore
import com.example.aiwidget.data.WidgetCache
import com.example.aiwidget.data.WidgetTaskStore
import com.example.aiwidget.network.AgentRepository
import com.example.aiwidget.network.ApiException
import com.example.aiwidget.homewidget.HomeWidgetCoordinator
import com.example.aiwidget.util.ChatSyncLog
import com.example.aiwidget.util.ChatTimeFormat
import com.example.aiwidget.util.LinkNormalizer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * 底部 Tab 目的地：原文 / 消息 / 我的。
 */
enum class AppDestination {
    /** Widget 速报原文（Markdown）。 */
    Article,

    /** Agent 对话。 */
    Chat,

    /** 设置（环境、Widget 任务等）。 */
    Mine,
}

/** 单条 Widget 任务的原文 Tab 数据，来自 [com.example.aiwidget.data.WidgetCache]。 */
data class WidgetArticleSnapshot(
    val taskId: String,
    /** Tab 标签，一般用任务标题。 */
    val taskTitle: String,
    val title: String,
    val timeLabel: String,
    val rawContent: String,
) {
    val hasContent: Boolean get() = rawContent.isNotBlank()
}

/** 会话列表一行（本地缓存 + 最后一条消息摘要）。 */
data class ChatSessionRowUi(
    val sessionId: String,
    val title: String,
    val updatedAtLabel: String,
    val preview: String,
)

/**
 * App 内界面（原文 + 对话 + 设置）的全部状态。
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
    val chatSessions: List<ChatSessionRowUi> = emptyList(),
    val chatSessionsLoading: Boolean = false,
    /** true 时显示二级对话页；false 为会话列表。 */
    val chatInConversation: Boolean = false,
    val chatMessages: List<ChatMessage> = emptyList(),
    /** 已展开全文的用户消息 id。 */
    val expandedChatMessageIds: Set<String> = emptySet(),
    /** 当前或最近一次请求的 Agent trace 行（SSE 实时追加）。 */
    val agentTraceLines: List<String> = emptyList(),
    /** 设置页 Widget 任务编辑表单，与 [WidgetTaskStore] 对应。 */
    val widgetTaskEditorRows: List<WidgetTaskEditorRow> = emptyList(),
    /** Widget 定时任务执行历史（仅 periodic），供设置页展示。 */
    val widgetPeriodicRunLogs: List<WidgetRunLogEntry> = emptyList(),
    /** 各 enabled 任务的原文（有/无缓存均列出，供顶部 Tab 切换）。 */
    val widgetArticles: List<WidgetArticleSnapshot> = emptyList(),
    /** 原文 Tab 当前选中的 [WidgetArticleSnapshot.taskId]。 */
    val selectedWidgetArticleTaskId: String? = null,
    val selectedTab: AppDestination = AppDestination.Chat,
    /** 非空时在 App 内 WebView 打开该链接。 */
    val browserUrl: String? = null,
    /** 当前续聊的 session_id；空表示下一条将开新会话（省 token）。 */
    val activeChatSessionId: String? = null,
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
    private val chatLocalStore = ChatLocalStore(application)
    private val agentRepository = AgentRepository()

    private val _uiState = MutableStateFlow(loadInitialState())
    val uiState: StateFlow<AppShellUiState> = _uiState.asStateFlow()

    private var chatMessagesSyncJob: Job? = null

    init {
        reloadChatSessionsFromLocal()
        viewModelScope.launch { syncChatSessions() }
    }

    private fun loadInitialState(): AppShellUiState =
        AppShellUiState(
            baseUrl = sessionPrefs.baseUrl,
            apiKey = sessionPrefs.apiKey,
            userId = sessionPrefs.userId,
            useStream = sessionPrefs.useStream,
            widgetTaskEditorRows = loadWidgetTaskEditorRows(),
            widgetPeriodicRunLogs = loadWidgetPeriodicRunLogs(),
            chatSessions = buildChatSessionRows(),
        )

    /** 处理启动：Launcher 默认消息；Widget 有缓存进原文并定位到对应任务 Tab。 */
    fun onLaunch(fromWidget: Boolean, widgetTaskId: String? = null) {
        refreshWidgetArticles(preferredTaskId = widgetTaskId)
        val hasReadable = _uiState.value.widgetArticles.any { it.hasContent }
        val tab =
            when {
                fromWidget && hasReadable -> AppDestination.Article
                else -> AppDestination.Chat
            }
        selectTab(tab)
    }

    /** 切换底部 Tab；原文无任何可读缓存时自动落到消息。 */
    fun selectTab(tab: AppDestination) {
        if (tab == AppDestination.Article && !_uiState.value.widgetArticles.any { it.hasContent }) {
            _uiState.update { it.copy(selectedTab = AppDestination.Chat) }
            return
        }
        if (tab == AppDestination.Mine) {
            refreshWidgetStatusPanel()
        }
        _uiState.update { it.copy(selectedTab = tab, widgetTaskSaveError = null) }
        if (tab == AppDestination.Chat && !_uiState.value.chatInConversation) {
            refreshChatSessions()
        }
    }

    /** 在 App 内 WebView 打开 Markdown / 对话中的链接。 */
    fun openBrowserLink(rawUrl: String) {
        val normalized = LinkNormalizer.normalize(rawUrl)
        if (normalized.isBlank()) return
        _uiState.update { it.copy(browserUrl = normalized) }
    }

    fun closeBrowser() {
        _uiState.update { it.copy(browserUrl = null) }
    }

    /** 拉取会话列表；若在二级对话页则同时拉当前会话消息。 */
    fun refreshChatFromServer() {
        viewModelScope.launch {
            syncChatSessions()
            val viewing = _uiState.value
            val sessionId = viewing.activeChatSessionId?.trim().orEmpty()
            if (viewing.chatInConversation && sessionId.isNotEmpty()) {
                syncChatMessagesForSession(sessionId)
            }
        }
    }

    fun refreshChatSessions() {
        viewModelScope.launch { syncChatSessions() }
    }

    /** 从 [WidgetCache] 重载全部 enabled 任务原文；保留或切换 [selectedWidgetArticleTaskId]。 */
    fun refreshWidgetArticles(preferredTaskId: String? = null) {
        _uiState.update { state ->
            val articles = loadWidgetArticles()
            val selected =
                resolveSelectedArticleTaskId(
                    articles = articles,
                    preferredTaskId = preferredTaskId ?: state.selectedWidgetArticleTaskId,
                )
            state.copy(
                widgetArticles = articles,
                selectedWidgetArticleTaskId = selected,
            )
        }
    }

    fun selectWidgetArticleTask(taskId: String) {
        _uiState.update { it.copy(selectedWidgetArticleTaskId = taskId) }
    }

    private fun loadWidgetArticles(): List<WidgetArticleSnapshot> {
        val store = WidgetTaskStore(getApplication())
        val cache = WidgetCache(getApplication())
        return store.loadEnabledTasks().map { task ->
            val slot = task.cacheSlot
            val raw =
                cache.getRawContent(slot)?.trim().orEmpty()
                    .ifBlank { cache.getSummary(slot)?.trim().orEmpty() }
            val title = cache.getTitle(slot)?.trim().orEmpty().ifBlank { task.title }
            val timeLabel = cache.getTimeLabel(slot)?.trim().orEmpty().ifBlank { "--:--" }
            WidgetArticleSnapshot(
                taskId = task.id,
                taskTitle = task.title,
                title = title,
                timeLabel = timeLabel,
                rawContent = raw,
            )
        }
    }

    private fun resolveSelectedArticleTaskId(
        articles: List<WidgetArticleSnapshot>,
        preferredTaskId: String?,
    ): String? {
        if (articles.isEmpty()) return null
        preferredTaskId?.let { id ->
            if (articles.any { it.taskId == id }) return id
        }
        return articles.firstOrNull { it.hasContent }?.taskId ?: articles.first().taskId
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

    /** 从会话列表进入新对话（二级页，尚无 session_id）。 */
    fun startNewConversation() {
        chatMessagesSyncJob?.cancel()
        bindActiveChatSession(null)
        _uiState.update {
            it.copy(
                chatInConversation = true,
                chatMessages = emptyList(),
                expandedChatMessageIds = emptySet(),
                agentTraceLines = emptyList(),
            )
        }
    }

    /** 打开已有会话的对话页，并拉取该会话消息。 */
    fun openChatSession(sessionId: String) {
        val id = sessionId.trim()
        if (id.isEmpty()) return
        chatMessagesSyncJob?.cancel()
        bindActiveChatSession(id)
        _uiState.update {
            it.copy(
                chatInConversation = true,
                chatMessages = loadChatMessagesFromLocal(id),
                expandedChatMessageIds = emptySet(),
                agentTraceLines = emptyList(),
            )
        }
        chatMessagesSyncJob =
            viewModelScope.launch {
                syncChatMessagesForSession(id)
            }
    }

    /** 删除会话：服务端成功后再清本地；失败则保留本地并 Toast。 */
    fun deleteChatSession(sessionId: String) {
        val id = sessionId.trim()
        if (id.isEmpty()) return
        chatMessagesSyncJob?.cancel()
        viewModelScope.launch {
            val state = _uiState.value
            val userId = state.userId.trim().ifEmpty { appPrefs.getOrCreateUserId() }
            val app = getApplication<Application>()
            try {
                agentRepository.deleteChatSession(
                    baseUrl = state.baseUrl,
                    apiKey = state.apiKey,
                    sessionId = id,
                    userId = userId,
                )
                chatLocalStore.deleteSession(id)
                if (_uiState.value.activeChatSessionId == id) {
                    bindActiveChatSession(null)
                    _uiState.update {
                        it.copy(
                            chatInConversation = false,
                            chatMessages = emptyList(),
                            expandedChatMessageIds = emptySet(),
                            agentTraceLines = emptyList(),
                        )
                    }
                }
                reloadChatSessionsFromLocal()
                Toast.makeText(
                    app,
                    app.getString(R.string.chat_session_delete_success),
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (_: Exception) {
                Toast.makeText(
                    app,
                    app.getString(R.string.chat_session_delete_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    /** 从对话页返回会话列表（清空当前 session 指针）。 */
    fun backToChatSessionList() {
        chatMessagesSyncJob?.cancel()
        bindActiveChatSession(null)
        _uiState.update {
            it.copy(
                chatInConversation = false,
                chatMessages = emptyList(),
                expandedChatMessageIds = emptySet(),
                agentTraceLines = emptyList(),
            )
        }
        refreshChatSessions()
    }

    /**
     * 二级页「新对话」：清空续聊 session，下次发送开新 thread。
     */
    fun clearChat() {
        chatMessagesSyncJob?.cancel()
        bindActiveChatSession(null)
        _uiState.update {
            it.copy(
                chatMessages = emptyList(),
                expandedChatMessageIds = emptySet(),
                agentTraceLines = emptyList(),
            )
        }
        val app = getApplication<Application>()
        Toast.makeText(
            app,
            app.getString(R.string.chat_new_conversation_toast),
            Toast.LENGTH_SHORT,
        ).show()
    }

    /**
     * 校验并持久化一条 Widget 任务，并按需重新登记 WorkManager。
     * @param showToast 保存成功时 Toast
     * @param reschedule 是否刷新定时与桌面 Widget
     */
    fun saveWidgetTask(taskId: String) {
        commitWidgetTaskEditorRow(taskId, showToast = true, reschedule = true)
    }

    /** 恢复为内置默认任务列表（1h 速报 + 持仓盈亏）。 */
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
        val userId = state.userId.trim().ifEmpty { appPrefs.getOrCreateUserId() }
        val sessionId = activeChatSessionIdForSend()
        val sentAtMs = System.currentTimeMillis()
        val sentAt = ChatTimeFormat.formatCreatedAt(sentAtMs)
        appendUserChatMessage(message, userMessageSummary, sentAtMs)
        if (sessionId != null) {
            chatLocalStore.appendMessage(
                StoredChatMessage(
                    localId = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "user",
                    content = message,
                    createdAt = sentAt,
                    localOnly = true,
                ),
            )
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, agentTraceLines = emptyList()) }
            try {
                val request =
                    ChatTurnRequest(
                        userId = userId,
                        sessionId = sessionId,
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
                        agentRepository.chatTurn(
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
                persistChatTurn(message, result)
                appendChat(chatMessageFromChatResponse(result))
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

    private fun appendUserChatMessage(
        fullPrompt: String,
        summary: String? = null,
        timestampMs: Long = System.currentTimeMillis(),
    ) {
        val displaySummary = summary ?: summarizePrompt(fullPrompt)
        appendChat(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.User,
                kind = ChatKind.UserPrompt,
                summary = displaySummary,
                fullText = fullPrompt,
                timestampMs = timestampMs,
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

    private fun bindActiveChatSession(sessionId: String?) {
        val id = sessionId?.trim()?.takeIf { it.isNotEmpty() }
        chatLocalStore.currentSessionId = id
        _uiState.update { it.copy(activeChatSessionId = id) }
    }

    private fun activeChatSessionIdForSend(): String? {
        val state = _uiState.value
        if (!state.chatInConversation) return null
        return state.activeChatSessionId?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun isViewingChatSession(sessionId: String): Boolean {
        val state = _uiState.value
        return state.chatInConversation && state.activeChatSessionId == sessionId.trim()
    }

    private fun loadChatMessagesFromLocal(sessionId: String): List<ChatMessage> {
        val id = sessionId.trim()
        if (id.isEmpty()) return emptyList()
        return chatLocalStore.loadMessages(id).map { it.toChatMessage() }
    }

    private fun reloadChatMessagesFromLocal(sessionId: String) {
        val id = sessionId.trim()
        if (id.isEmpty() || !isViewingChatSession(id)) return
        _uiState.update { it.copy(chatMessages = loadChatMessagesFromLocal(id)) }
    }

    private fun reloadChatSessionsFromLocal() {
        _uiState.update { it.copy(chatSessions = buildChatSessionRows()) }
    }

    private fun buildChatSessionRows(): List<ChatSessionRowUi> =
        chatLocalStore.loadSessions()
            .filter { it.sessionId.isNotBlank() }
            .map { session ->
                val lastMessage = chatLocalStore.loadMessages(session.sessionId).lastOrNull()
                val preview =
                    lastMessage?.let { msg ->
                        summarizePrompt(msg.content, maxLen = 56)
                    }.orEmpty()
                ChatSessionRowUi(
                    sessionId = session.sessionId,
                    title = session.title,
                    updatedAtLabel = ChatTimeFormat.formatSessionListLabel(session.updatedAt),
                    preview = preview,
                )
            }

    private suspend fun syncChatSessions(source: String = "chat/sync") {
        val state = _uiState.value
        val userId = state.userId.trim().ifEmpty { appPrefs.getOrCreateUserId() }
        _uiState.update { it.copy(chatSessionsLoading = true) }
        try {
            val sessions =
                agentRepository.listChatSessions(
                    baseUrl = state.baseUrl,
                    apiKey = state.apiKey,
                    userId = userId,
                    source = source,
                )
            chatLocalStore.mergeSessionsFromServer(sessions)
            reloadChatSessionsFromLocal()
        } catch (e: Exception) {
            ChatSyncLog.logFailure(source, "sessions", e)
            reloadChatSessionsFromLocal()
        } finally {
            _uiState.update { it.copy(chatSessionsLoading = false) }
        }
    }

    private suspend fun syncChatMessagesForSession(
        sessionId: String,
        source: String = "chat/sync",
    ) {
        val id = sessionId.trim()
        if (id.isEmpty() || !isViewingChatSession(id)) return
        val state = _uiState.value
        val userId = state.userId.trim().ifEmpty { appPrefs.getOrCreateUserId() }
        try {
            val remote =
                agentRepository.listChatMessages(
                    baseUrl = state.baseUrl,
                    apiKey = state.apiKey,
                    sessionId = id,
                    userId = userId,
                    source = source,
                )
            if (!isViewingChatSession(id)) return
            chatLocalStore.replaceMessagesFromServer(id, remote)
            reloadChatMessagesFromLocal(id)
            reloadChatSessionsFromLocal()
        } catch (e: Exception) {
            ChatSyncLog.logFailure(source, "messages/$id", e)
        }
    }

    private fun persistChatTurn(
        userMessage: String,
        response: ChatResponse,
    ) {
        val sessionId = response.sessionId.trim()
        if (sessionId.isEmpty()) return

        bindActiveChatSession(sessionId)
        chatLocalStore.upsertSession(
            ChatSessionSummary(
                sessionId = sessionId,
                title = response.title.ifBlank { summarizePrompt(userMessage, 32) },
                updatedAt = response.updatedAt,
            ),
        )

        val assistantCreatedAt =
            response.updatedAt.trim().ifBlank { ChatTimeFormat.formatCreatedAt() }
        val messages = chatLocalStore.loadMessages(sessionId).toMutableList()
        val userIndex =
            messages.indexOfLast { it.role == "user" && it.content == userMessage }
        if (userIndex >= 0) {
            val existing = messages[userIndex]
            messages[userIndex] =
                existing.copy(
                    localOnly = false,
                    createdAt =
                        existing.createdAt.trim().ifBlank {
                            ChatTimeFormat.formatCreatedAt()
                        },
                )
        } else {
            messages.add(
                StoredChatMessage(
                    localId = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "user",
                    content = userMessage,
                    createdAt = ChatTimeFormat.formatCreatedAt(),
                    localOnly = false,
                ),
            )
        }
        val assistantBody =
            response.content.trim().ifBlank {
                response.title.trim().ifBlank { response.errorMsg }
            }
        messages.add(
            StoredChatMessage(
                localId = UUID.randomUUID().toString(),
                sessionId = sessionId,
                role = "assistant",
                content = assistantBody,
                createdAt = assistantCreatedAt,
                localOnly = false,
            ),
        )
        chatLocalStore.saveMessages(sessionId, messages)
        reloadChatSessionsFromLocal()
    }
}
