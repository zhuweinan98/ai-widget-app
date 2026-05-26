package com.example.aiwidget.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.aiwidget.data.Presets
import com.example.aiwidget.data.WidgetRunLogEntry
import com.example.aiwidget.data.WidgetRunOutcome

/** 我的：后端环境、Widget 定时任务、定时任务执行记录。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppShellViewModel) {
    val state by viewModel.uiState.collectAsState()
    val scroll = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("我的") })
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            SettingsSections(state, viewModel)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSections(state: AppShellUiState, viewModel: AppShellViewModel) {
    SettingsSection(title = "环境", showTopDivider = false) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.baseUrl.trim() == Presets.LOCAL_BASE_URL,
                onClick = { viewModel.switchToLocalBackend() },
                label = { Text("本地") },
            )
            FilterChip(
                selected = state.baseUrl.trim() == Presets.SERVER_BASE_URL,
                onClick = { viewModel.switchToRemoteBackend() },
                label = { Text("服务器") },
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::toggleAdvancedExpanded) {
                Text(if (state.advancedExpanded) "收起" else "高级")
            }
        }
        if (state.advancedExpanded) {
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = { Text("API 地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text(Presets.LOCAL_BASE_URL) },
            )
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::updateApiKey,
                label = { Text("X-API-Key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.userId,
                onValueChange = viewModel::updateUserId,
                label = { Text("user_id（对话与 Widget 共用）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }

    SettingsSection(title = "Widget 定时任务") {
        Text(
            "共 ${state.widgetTaskEditorRows.size} 条 · 取消启用则不定时 · 每条点保存生效",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.widgetTaskSaveError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (state.widgetTaskEditorRows.isEmpty()) {
            Text("暂无任务，点「恢复默认」", style = MaterialTheme.typography.bodySmall)
        } else {
            state.widgetTaskEditorRows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                WidgetTaskEditorRowUi(
                    row = row,
                    onTitleChange = { viewModel.updateTaskTitle(row.id, it) },
                    onPromptChange = { viewModel.updateTaskPrompt(row.id, it) },
                    onEnabledChange = { viewModel.updateTaskEnabled(row.id, it) },
                    onIntervalChange = { viewModel.updateTaskInterval(row.id, it) },
                    onTtlChange = { viewModel.updateTaskCacheTtl(row.id, it) },
                    onSave = { viewModel.saveWidgetTask(row.id) },
                )
            }
            OutlinedButton(
                onClick = viewModel::resetWidgetTasksToDefaults,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("恢复默认")
            }
        }
    }

    SettingsSection(
        title = "定时任务执行记录",
        trailing = { TextButton(onClick = viewModel::refreshWidgetStatusPanel) { Text("刷新") } },
    ) {
        if (state.widgetPeriodicRunLogs.isEmpty()) {
            Text(
                "暂无记录（仅记录 WorkManager 定时触发，不含手动 ↻）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            state.widgetPeriodicRunLogs.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 6.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                WidgetPeriodicRunLogCard(entry)
            }
        }
    }
}

/** 单条 Widget 任务在设置页的编辑表单。 */
@Composable
private fun WidgetTaskEditorRowUi(
    row: WidgetTaskEditorRow,
    onTitleChange: (String) -> Unit,
    onPromptChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onIntervalChange: (String) -> Unit,
    onTtlChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = row.enabled, onCheckedChange = onEnabledChange)
            Text("启用定时刷新", style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value = row.title,
            onValueChange = onTitleChange,
            label = { Text("标题") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = row.enabled,
        )
        OutlinedTextField(
            value = row.prompt,
            onValueChange = onPromptChange,
            label = { Text("发给 Agent 的 prompt") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 4,
            enabled = row.enabled,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = row.intervalMinutes,
                onValueChange = onIntervalChange,
                label = { Text("间隔(分)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = row.enabled,
            )
            OutlinedTextField(
                value = row.cacheTtlSeconds,
                onValueChange = onTtlChange,
                label = { Text("缓存(秒)") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                enabled = row.enabled,
            )
            Button(onClick = onSave) {
                Text("保存")
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    showTopDivider: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (showTopDivider) {
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
        )
    } else {
        Spacer(modifier = Modifier.height(4.dp))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        trailing?.invoke()
    }
    Spacer(modifier = Modifier.height(6.dp))
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
private fun WidgetPeriodicRunLogCard(entry: WidgetRunLogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "${formatTimestamp(entry.finishedAtMs, includeSeconds = true)} · ${entry.taskId} · ${outcomeLabel(entry.outcome)}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                entry.prompt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.title.isNotBlank()) {
                Text(entry.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
            if (entry.errorMsg.isNotBlank()) {
                Text(
                    entry.errorMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun outcomeLabel(outcome: String): String =
    when (outcome) {
        WidgetRunOutcome.CACHE_SKIPPED -> "跳过(缓存有效)"
        WidgetRunOutcome.API_OK -> "成功"
        WidgetRunOutcome.API_ERROR -> "API 错误"
        WidgetRunOutcome.API_FAILURE -> "请求失败"
        else -> outcome
    }

private fun formatTimestamp(ms: Long, includeSeconds: Boolean): String {
    if (ms <= 0L) return "--"
    val pattern = if (includeSeconds) "HH:mm:ss" else "HH:mm"
    return java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).format(java.util.Date(ms))
}
