package com.example.aiwidget.app

/**
 * 对话：消息模型、列表 UI、SSE trace 面板。
 *
 * Agent 回复 Markdown 见 [ChatMarkdownText]；
 * 将 [com.example.aiwidget.data.ChatResponse] 转为气泡见 [chatMessageFromChatResponse]。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aiwidget.data.ChatResponse
import com.example.aiwidget.data.StoredChatMessage
import com.example.aiwidget.util.ChatTimeFormat
import java.util.UUID

enum class ChatRole {
    /** 用户发出的 prompt。 */
    User,

    /** Agent 返回的结果或错误。 */
    Agent,
}

/** 气泡样式/语义类型。 */
enum class ChatKind {
    UserPrompt,
    Result,
    Error,
}

/** 对话区单条消息。 */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val kind: ChatKind,
    /** 气泡主文案（用户侧常为摘要）。 */
    val summary: String,
    /** 用户 prompt 全文；Agent 可与 summary 相同。 */
    val fullText: String = summary,
    val timestampMs: Long = System.currentTimeMillis(),
)

/** 截断 prompt 首行，用于用户气泡摘要或快捷芯片标题。 */
fun summarizePrompt(text: String, maxLen: Int = 48): String {
    val line = text.lineSequence().firstOrNull()?.trim().orEmpty()
    if (line.isEmpty()) return "(空)"
    return if (line.length <= maxLen) line else line.take(maxLen) + "…"
}

private val ChatBackground = Color(0xFFEDEDED)
private val UserBubble = Color(0xFF95EC69)
private val AgentBubble = Color.White
private val TraceExpandedHeight = 120.dp

@Composable
fun ChatMessageList(
    messages: List<ChatMessage>,
    expandedMessageIds: Set<String>,
    onToggleExpand: (String) -> Unit,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    if (messages.isEmpty()) {
        Box(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(ChatBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "发送 prompt 或点快捷芯片开始",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        state = listState,
        modifier =
            modifier
                .fillMaxSize()
                .background(ChatBackground)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items = messages, key = { it.id }) { msg ->
            when (msg.role) {
                ChatRole.User -> UserMessageRow(msg, expandedMessageIds.contains(msg.id), onToggleExpand)
                ChatRole.Agent -> AgentMessageRow(msg, onLinkClick)
            }
        }
    }
}

@Composable
fun TracePanel(
    traces: List<String>,
    isSending: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val latestLine = traces.lastOrNull()
    val lineText =
        when {
            latestLine != null -> latestLine
            isSending -> "等待 trace…"
            else -> "暂无 trace（勾选 SSE 可实时查看）"
        }
    val canExpand = traces.isNotEmpty() || isSending

    LaunchedEffect(traces.size, latestLine, expanded) {
        if (expanded && traces.isNotEmpty()) {
            scroll.animateScrollTo(scroll.maxValue)
        }
    }

    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                ),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = lineText,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { if (canExpand) expanded = !expanded },
                    enabled = canExpand,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector =
                            if (expanded) {
                                Icons.Filled.KeyboardArrowDown
                            } else {
                                Icons.Filled.KeyboardArrowUp
                            },
                        contentDescription = if (expanded) "收起 trace" else "展开 trace",
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (expanded && canExpand) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(TraceExpandedHeight)
                            .verticalScroll(scroll)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    if (traces.isEmpty()) {
                        Text(
                            "等待 trace…",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        traces.forEach { line ->
                            Text(
                                line,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 将 [ChatResponse] 转为 Agent 侧聊天气泡（含错误态）。 */
fun chatMessageFromChatResponse(result: ChatResponse): ChatMessage {
    val fullText = agentTurnDisplayText(result.title, result.content, result.errorMsg)
    val summary =
        if (fullText.length <= 200) {
            fullText
        } else {
            fullText.take(200) + "…"
        }
    return ChatMessage(
        id = UUID.randomUUID().toString(),
        role = ChatRole.Agent,
        kind = if (result.status == "error") ChatKind.Error else ChatKind.Result,
        summary = summary,
        fullText = fullText,
        timestampMs = ChatTimeFormat.toEpochMillis(result.updatedAt),
    )
}

/** 本地缓存消息 → 对话 UI。 */
fun StoredChatMessage.toChatMessage(): ChatMessage {
    val timestampMs = ChatTimeFormat.toEpochMillis(createdAt)
    return when (role.lowercase()) {
        "user" ->
            ChatMessage(
                id = localId,
                role = ChatRole.User,
                kind = ChatKind.UserPrompt,
                summary = summarizePrompt(content),
                fullText = content,
                timestampMs = timestampMs,
            )
        else ->
            ChatMessage(
                id = localId,
                role = ChatRole.Agent,
                kind = ChatKind.Result,
                summary =
                    if (content.length <= 200) {
                        content
                    } else {
                        content.take(200) + "…"
                    },
                fullText = content,
                timestampMs = timestampMs,
            )
    }
}

@Composable
private fun UserMessageRow(
    message: ChatMessage,
    expanded: Boolean,
    onToggleExpand: (String) -> Unit,
) {
    val canExpand = message.fullText.length > message.summary.length
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            ChatBubble(background = UserBubble) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(message.summary, style = MaterialTheme.typography.bodyMedium)
                    if (expanded && canExpand) {
                        Text(
                            message.fullText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (canExpand) {
                        TextButton(
                            onClick = { onToggleExpand(message.id) },
                            modifier = Modifier.padding(0.dp),
                        ) {
                            Text(if (expanded) "收起" else "展开全文", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Text(
                ChatTimeFormat.formatMessageBubbleTime(message.timestampMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ChatAvatar(label = "我", modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun AgentMessageRow(
    message: ChatMessage,
    onLinkClick: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        ChatAvatar(label = "AI", modifier = Modifier.padding(end = 6.dp))
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                agentSenderLabel(message.kind),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChatBubble(background = AgentBubble) {
                AgentBubbleBody(message, onLinkClick)
            }
        }
    }
}

@Composable
private fun AgentBubbleBody(
    message: ChatMessage,
    onLinkClick: (String) -> Unit,
) {
    val color =
        when (message.kind) {
            ChatKind.Error -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurface
        }
    when (message.kind) {
        ChatKind.Result, ChatKind.Error ->
            ChatMarkdownText(
                markdown = message.summary,
                color = color,
                textStyle = MaterialTheme.typography.bodyMedium,
                onLinkClick = onLinkClick,
            )
        else ->
            Text(
                message.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
    }
}

@Composable
private fun ChatBubble(
    background: Color,
    content: @Composable () -> Unit,
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            content()
        }
    }
}

@Composable
private fun ChatAvatar(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun agentSenderLabel(kind: ChatKind): String =
    when (kind) {
        ChatKind.Result -> "Agent · 结果"
        ChatKind.Error -> "Agent · 错误"
        ChatKind.UserPrompt -> "Agent"
    }

private fun agentTurnDisplayText(title: String, content: String, errorMsg: String): String {
    val t = title.trim()
    val c = content.trim()
    val error = errorMsg.trim()
    return when {
        c.isNotBlank() && t.isNotBlank() -> "$t\n\n$c"
        c.isNotBlank() -> c
        error.isNotBlank() && t.isNotBlank() -> "$t\n\n$error"
        error.isNotBlank() -> error
        t.isNotBlank() -> t
        else -> "(无正文)"
    }
}
