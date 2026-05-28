package com.example.aiwidget.app

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.stringResource
import com.example.aiwidget.R
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiwidget.data.Presets

/**
 * App 外壳：底部导航（消息 / 原文 / 我的）；二级对话页隐藏底栏；输入区 imePadding。
 */
@Composable
fun AppShellScreen(viewModel: AppShellViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.refreshChatFromServer()
                    viewModel.refreshWidgetArticles()
                    viewModel.refreshWidgetStatusPanel()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val showBottomBar =
            !(state.selectedTab == AppDestination.Chat && state.chatInConversation)
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = AppShellContentWindowInsets(),
            bottomBar = {
                if (showBottomBar) {
                    AppBottomBar(
                        selectedTab = state.selectedTab,
                        articleAvailable = state.widgetArticles.any { it.hasContent },
                        onSelectTab = viewModel::selectTab,
                    )
                }
            },
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
            ) {
                Crossfade(
                    targetState = state.selectedTab,
                    modifier = Modifier.fillMaxSize(),
                    animationSpec = tween(durationMillis = 200),
                    label = "app_tab_content",
                ) { tab ->
                    when (tab) {
                        AppDestination.Article ->
                            if (state.widgetArticles.isNotEmpty()) {
                                ArticleScreen(
                                    articles = state.widgetArticles,
                                    selectedTaskId = state.selectedWidgetArticleTaskId,
                                    onSelectTask = viewModel::selectWidgetArticleTask,
                                    onLinkClick = viewModel::openBrowserLink,
                                )
                            } else {
                                ChatTabContent(viewModel)
                            }
                        AppDestination.Chat -> ChatTabContent(viewModel)
                        AppDestination.Mine -> SettingsScreen(viewModel)
                    }
                }
            }
        }

        state.browserUrl?.let { url ->
            InAppBrowserScreen(
                url = url,
                onClose = viewModel::closeBrowser,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun AppBottomBar(
    selectedTab: AppDestination,
    articleAvailable: Boolean,
    onSelectTab: (AppDestination) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == AppDestination.Chat,
            onClick = { onSelectTab(AppDestination.Chat) },
            icon = { Icon(Icons.Outlined.Chat, contentDescription = null) },
            label = { Text("消息") },
        )
        NavigationBarItem(
            selected = selectedTab == AppDestination.Article,
            onClick = { onSelectTab(AppDestination.Article) },
            enabled = articleAvailable,
            icon = { Icon(Icons.Outlined.Article, contentDescription = null) },
            label = { Text("原文") },
        )
        NavigationBarItem(
            selected = selectedTab == AppDestination.Mine,
            onClick = { onSelectTab(AppDestination.Mine) },
            icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            label = { Text("我的") },
        )
    }
}

@Composable
private fun ChatTabContent(viewModel: AppShellViewModel) {
    val state by viewModel.uiState.collectAsState()
    if (state.chatInConversation) {
        key(state.activeChatSessionId ?: "__new_chat__") {
            ChatConversationScreen(viewModel)
        }
    } else {
        ChatSessionListScreen(viewModel)
    }
}

/** 二级对话页：气泡列表 + trace + 输入区。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatConversationScreen(viewModel: AppShellViewModel) {
    val state by viewModel.uiState.collectAsState()

    BackHandler(onBack = viewModel::backToChatSessionList)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = NestedScaffoldContentInsets,
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                navigationIcon = {
                    IconButton(onClick = viewModel::backToChatSessionList) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.chat_back_to_sessions),
                        )
                    }
                },
                title = {},
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
        ) {
            ChatMessageList(
                messages = state.chatMessages,
                expandedMessageIds = state.expandedChatMessageIds,
                onToggleExpand = viewModel::toggleChatMessageExpanded,
                onLinkClick = viewModel::openBrowserLink,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds(),
            )
            ChatInputBar(
                state = state,
                viewModel = viewModel,
            )
        }
    }
}

/** 底部：SSE trace、快捷芯片、输入框与发送按钮。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatInputBar(
    state: AppShellUiState,
    viewModel: AppShellViewModel,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TracePanel(traces = state.agentTraceLines, isSending = state.isSending)
            FlowRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Presets.chatPresets().forEach { preset ->
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.sendChatPreset(preset.message, preset.label) },
                        label = { Text(preset.label) },
                        enabled = !state.isSending,
                    )
                }
            }
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Checkbox(checked = state.useStream, onCheckedChange = viewModel::updateUseStream)
                Text("SSE", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = state.message,
                    onValueChange = viewModel::updateMessage,
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp, max = 96.dp),
                    placeholder = { Text("输入 prompt…") },
                    minLines = 1,
                    maxLines = 3,
                    enabled = !state.isSending,
                )
                Button(
                    onClick = {
                        viewModel.persistSessionSettings()
                        viewModel.sendChatMessage()
                    },
                    enabled = !state.isSending,
                ) {
                    Text(if (state.isSending) "…" else "发送")
                }
            }
        }
    }
}
