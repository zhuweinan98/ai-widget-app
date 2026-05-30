package com.example.aiwidget.app

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.aiwidget.R
import com.example.aiwidget.data.Presets
import com.example.aiwidget.data.WidgetConfig
import com.example.aiwidget.data.WidgetRunLogEntry
import com.example.aiwidget.data.WidgetRunOutcome
import com.example.aiwidget.homewidget.HomeWidgetSystemPermissions

/** 我的：后端环境、Widget 定时任务、定时任务执行记录。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppShellViewModel) {
    val state by viewModel.uiState.collectAsState()
    val scroll = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = NestedScaffoldContentInsets,
        topBar = {
            TopAppBar(
                title = { Text("我的") },
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
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
        WidgetTaskEditorSection(state, viewModel)
    }

    WidgetBackgroundRefreshSection()

    SettingsSection(
        title = "定时任务执行记录",
        trailing = { TextButton(onClick = viewModel::refreshWidgetStatusPanel) { Text("刷新") } },
    ) {
        if (state.widgetPeriodicRunLogs.isEmpty()) {
            Text(
                "暂无记录（仅记录闹钟定时触发，不含手动 ↻）",
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

@Composable
private fun WidgetTaskEditorSection(state: AppShellUiState, viewModel: AppShellViewModel) {
    val rows = state.widgetTaskEditorRows
    var taskMenuExpanded by remember { mutableStateOf(false) }
    var selectedTaskId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(rows.map { it.id }) {
        if (selectedTaskId == null || rows.none { it.id == selectedTaskId }) {
            selectedTaskId = rows.firstOrNull()?.id
        }
    }

    LaunchedEffect(state.widgetPendingSelectTaskId) {
        state.widgetPendingSelectTaskId?.let { pendingId ->
            selectedTaskId = pendingId
            viewModel.consumeWidgetPendingSelectTaskId()
        }
    }

    Text(
        "共 ${rows.size} 条（最多 ${WidgetConfig.MAX_WIDGET_TASKS} 条）· 可添加自定义任务 · 编辑后点保存",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.widgetTaskSaveError?.let { error ->
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    if (rows.isEmpty()) {
        Text("暂无任务，点「恢复默认」", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(
            onClick = viewModel::resetWidgetTasksToDefaults,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("恢复默认")
        }
        return
    }

    val selectedRow = rows.find { it.id == selectedTaskId }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedRow?.let { taskDropdownLabel(it) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text("选择任务") },
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clickable { taskMenuExpanded = !taskMenuExpanded },
        )
        DropdownMenu(
            expanded = taskMenuExpanded,
            onDismissRequest = { taskMenuExpanded = false },
        ) {
            rows.forEach { row ->
                DropdownMenuItem(
                    text = { Text(taskDropdownLabel(row)) },
                    onClick = {
                        selectedTaskId = row.id
                        taskMenuExpanded = false
                    },
                )
            }
        }
    }

    selectedRow?.let { row ->
        WidgetTaskEditorRowUi(
            row = row,
            onTitleChange = { viewModel.updateTaskTitle(row.id, it) },
            onPromptChange = { viewModel.updateTaskPrompt(row.id, it) },
            onEnabledChange = { viewModel.updateTaskEnabled(row.id, it) },
            onIntervalChange = { viewModel.updateTaskInterval(row.id, it) },
            onTtlChange = { viewModel.updateTaskCacheTtlMinutes(row.id, it) },
            onSave = { viewModel.saveWidgetTask(row.id) },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = viewModel::addWidgetTask,
            modifier = Modifier.weight(1f),
            enabled = rows.size < WidgetConfig.MAX_WIDGET_TASKS,
        ) {
            Text(stringResource(R.string.widget_task_add))
        }
        OutlinedButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.weight(1f),
            enabled = rows.size > 1 && selectedTaskId != null,
        ) {
            Text(stringResource(R.string.widget_task_delete))
        }
    }

    if (showDeleteConfirm && selectedRow != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.widget_task_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.widget_task_delete_confirm_message,
                        selectedRow.title.ifBlank { selectedRow.id },
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteWidgetTask(selectedRow.id)
                    },
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            },
        )
    }

    OutlinedButton(
        onClick = viewModel::resetWidgetTasksToDefaults,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("恢复默认")
    }
}

private fun taskDropdownLabel(row: WidgetTaskEditorRow): String {
    val status = if (row.enabled) "已启用" else "未启用"
    val title = row.title.ifBlank { row.id }
    return "$title · $status"
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
            label = { Text("任务内容（自然语言）") },
            supportingText = { Text("原样作为 /widget/run 的 message；展示格式由服务端决定") },
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
                value = row.cacheTtlMinutes,
                onValueChange = onTtlChange,
                label = { Text("缓存(分)") },
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

@Composable
private fun WidgetBackgroundRefreshSection() {
    val context = LocalContext.current
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationOk by remember {
        mutableStateOf(HomeWidgetSystemPermissions.canPostNotifications(context))
    }
    var exactAlarmOk by remember {
        mutableStateOf(HomeWidgetSystemPermissions.canScheduleExactAlarms(context))
    }
    var batteryOk by remember {
        mutableStateOf(HomeWidgetSystemPermissions.isIgnoringBatteryOptimizations(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    notificationOk = HomeWidgetSystemPermissions.canPostNotifications(context)
                    exactAlarmOk = HomeWidgetSystemPermissions.canScheduleExactAlarms(context)
                    batteryOk = HomeWidgetSystemPermissions.isIgnoringBatteryOptimizations(context)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsSection(title = stringResource(R.string.widget_background_refresh_title)) {
        Text(
            stringResource(R.string.widget_background_refresh_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (notificationOk) {
                stringResource(R.string.widget_notification_granted)
            } else {
                stringResource(R.string.widget_notification_denied)
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            if (exactAlarmOk) {
                stringResource(R.string.widget_exact_alarm_granted)
            } else {
                stringResource(R.string.widget_exact_alarm_denied)
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            if (batteryOk) {
                stringResource(R.string.widget_battery_opt_granted)
            } else {
                stringResource(R.string.widget_battery_opt_denied)
            },
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = {
                    if (notificationOk) {
                        HomeWidgetSystemPermissions.openNotificationSettings(context)
                    } else if (activity != null) {
                        HomeWidgetSystemPermissions.requestPostNotifications(activity)
                    } else {
                        HomeWidgetSystemPermissions.openNotificationSettings(context)
                    }
                },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(
                        if (notificationOk) {
                            R.string.widget_open_notification_settings
                        } else {
                            R.string.widget_open_notification
                        },
                    ),
                )
            }
            OutlinedButton(
                onClick = { HomeWidgetSystemPermissions.openExactAlarmSettings(context) },
                modifier = Modifier.weight(1f),
                enabled = !exactAlarmOk,
            ) {
                Text(stringResource(R.string.widget_open_exact_alarm))
            }
        }
        OutlinedButton(
            onClick = { HomeWidgetSystemPermissions.requestIgnoreBatteryOptimizations(context) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !batteryOk,
        ) {
            Text(stringResource(R.string.widget_open_battery_opt))
        }
    }
}
