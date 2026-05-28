package com.example.aiwidget.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.aiwidget.R
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** 消息 Tab 一级页：会话列表。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatSessionListScreen(viewModel: AppShellViewModel) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = NestedScaffoldContentInsets,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(stringResource(R.string.chat_sessions_title)) },
                actions = {
                    IconButton(onClick = viewModel::startNewConversation) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = stringResource(R.string.chat_new_conversation),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
        ) {
            when {
                state.chatSessionsLoading && state.chatSessions.isEmpty() -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                state.chatSessions.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.chat_sessions_empty),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(items = state.chatSessions, key = { it.sessionId }) { session ->
                            ChatSessionSwipeItem(
                                session = session,
                                onOpen = { viewModel.openChatSession(session.sessionId) },
                                onDelete = { viewModel.deleteChatSession(session.sessionId) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

private enum class SessionRevealAnchor {
    Closed,
    Open,
}

/**
 * 左滑露出右侧「删除」；仅点击删除才执行，滑到底不会自动删。
 */
@Composable
private fun ChatSessionSwipeItem(
    session: ChatSessionRowUi,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val deleteWidth = 80.dp
    val deleteWidthPx = with(density) { deleteWidth.toPx() }
    val anchors =
        remember(deleteWidthPx) {
            DraggableAnchors {
                SessionRevealAnchor.Closed at 0f
                SessionRevealAnchor.Open at -deleteWidthPx
            }
        }
    val dragState =
        remember(anchors) {
            AnchoredDraggableState(
                initialValue = SessionRevealAnchor.Closed,
                anchors = anchors,
            )
        }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(deleteWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .clickable {
                        onDelete()
                        scope.launch { dragState.animateTo(SessionRevealAnchor.Closed) }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = stringResource(R.string.chat_session_delete),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        ListItem(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(dragState.requireOffset().roundToInt(), 0) }
                    .anchoredDraggable(
                        state = dragState,
                        orientation = Orientation.Horizontal,
                    )
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable {
                        if (dragState.currentValue == SessionRevealAnchor.Closed) {
                            onOpen()
                        } else {
                            scope.launch { dragState.animateTo(SessionRevealAnchor.Closed) }
                        }
                    },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            headlineContent = {
                Text(
                    text =
                        session.title.ifBlank {
                            stringResource(R.string.chat_session_untitled)
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (session.preview.isNotBlank()) {
                        Text(
                            text = session.preview,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (session.updatedAtLabel.isNotBlank()) {
                        Text(
                            text = session.updatedAtLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
        )
    }
}
