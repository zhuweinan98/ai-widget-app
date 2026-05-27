package com.example.aiwidget.data

import android.content.Context
import com.example.aiwidget.network.RetrofitClient
import com.squareup.moshi.Types

/**
 * 聊天会话与消息的本地缓存（SharedPreferences JSON）。
 *
 * 权威数据以服务端 `chat_messages` 为准；此处用于续聊 session_id 与离线只读。
 */
class ChatLocalStore(context: Context) {
    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val sessionsAdapter =
        RetrofitClient.moshiInstance.adapter<List<StoredChatSession>>(
            Types.newParameterizedType(List::class.java, StoredChatSession::class.java),
        )

    private val messagesAdapter =
        RetrofitClient.moshiInstance.adapter<List<StoredChatMessage>>(
            Types.newParameterizedType(List::class.java, StoredChatMessage::class.java),
        )

    var currentSessionId: String?
        get() = prefs.getString(KEY_CURRENT_SESSION_ID, null)?.takeIf { it.isNotBlank() }
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) remove(KEY_CURRENT_SESSION_ID) else putString(KEY_CURRENT_SESSION_ID, value)
            }.apply()
        }

    fun loadSessions(): List<StoredChatSession> {
        val raw = prefs.getString(KEY_SESSIONS_JSON, null) ?: return emptyList()
        return try {
            sessionsAdapter.fromJson(raw) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveSessions(sessions: List<StoredChatSession>) {
        prefs.edit().putString(KEY_SESSIONS_JSON, sessionsAdapter.toJson(sessions)).apply()
    }

    /**
     * 与服务端会话列表 merge：以 session_id 为键，服务端 updated_at 更新则覆盖 title/时间。
     */
    fun mergeSessionsFromServer(remote: List<ChatSessionSummary>) {
        if (remote.isEmpty()) return
        val byId = loadSessions().associateBy { it.sessionId }.toMutableMap()
        for (item in remote) {
            val existing = byId[item.sessionId]
            if (existing == null || item.updatedAt >= existing.updatedAt) {
                byId[item.sessionId] =
                    StoredChatSession(
                        sessionId = item.sessionId,
                        title = item.title,
                        createdAt = item.createdAt.ifBlank { existing?.createdAt.orEmpty() },
                        updatedAt = item.updatedAt,
                    )
            }
        }
        saveSessions(byId.values.sortedByDescending { it.updatedAt })
    }

    fun upsertSession(summary: ChatSessionSummary) {
        mergeSessionsFromServer(listOf(summary))
    }

    fun loadMessages(sessionId: String): List<StoredChatMessage> {
        val raw = prefs.getString(messagesKey(sessionId), null) ?: return emptyList()
        return try {
            messagesAdapter.fromJson(raw) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveMessages(sessionId: String, messages: List<StoredChatMessage>) {
        prefs.edit().putString(messagesKey(sessionId), messagesAdapter.toJson(messages)).apply()
    }

    /** 用服务端消息全量替换本地（保留末尾 localOnly 且服务端尚未包含的项）。 */
    fun replaceMessagesFromServer(
        sessionId: String,
        remote: List<ChatMessageItem>,
    ) {
        val localOnly =
            loadMessages(sessionId).filter { it.localOnly && remote.none { r -> r.content == it.content && r.role == it.role } }
        val merged =
            remote.map { item ->
                StoredChatMessage(
                    serverId = item.id,
                    localId = "srv_${item.id}",
                    sessionId = sessionId,
                    role = item.role,
                    content = item.content,
                    createdAt = item.createdAt,
                    localOnly = false,
                )
            } + localOnly
        saveMessages(sessionId, merged)
    }

    fun appendMessage(message: StoredChatMessage) {
        val list = loadMessages(message.sessionId).toMutableList()
        list.add(message)
        saveMessages(message.sessionId, list)
    }

    fun markUserMessagesSynced(sessionId: String) {
        val updated =
            loadMessages(sessionId).map { msg ->
                if (msg.role == "user" && msg.localOnly) msg.copy(localOnly = false) else msg
            }
        saveMessages(sessionId, updated)
    }

    fun deleteSession(sessionId: String) {
        val sessions = loadSessions().filterNot { it.sessionId == sessionId }
        saveSessions(sessions)
        prefs.edit().remove(messagesKey(sessionId)).apply()
        if (currentSessionId == sessionId) {
            currentSessionId = null
        }
    }

    fun clearCurrentConversation() {
        currentSessionId = null
    }

    private fun messagesKey(sessionId: String): String = "chat_messages_$sessionId"

    companion object {
        private const val PREFS_NAME = "chat_local"
        private const val KEY_CURRENT_SESSION_ID = "current_session_id"
        private const val KEY_SESSIONS_JSON = "chat_sessions_json"
    }
}

data class StoredChatSession(
    val sessionId: String,
    val title: String,
    val createdAt: String = "",
    val updatedAt: String,
)

data class StoredChatMessage(
    val serverId: Long? = null,
    val localId: String,
    val sessionId: String,
    val role: String,
    val content: String,
    val createdAt: String = "",
    val localOnly: Boolean = false,
)
