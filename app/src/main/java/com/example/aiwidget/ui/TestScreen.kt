package com.example.aiwidget.ui

/**
 * 后端联调测试 UI（Compose），功能对齐 widget-agent-server `/test` 页。
 *
 * 无业务逻辑：仅订阅 [TestViewModel.uiState] 并回调 ViewModel 方法。
 * 架构说明见项目根目录 ARCHITECTURE.md。
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiwidget.data.Presets
import com.example.aiwidget.data.WidgetResult

/** 测试页根 composable：配置区 + 快捷芯片 + 发送 + 结果卡片。 */
@Composable
fun TestScreen(viewModel: TestViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val scroll = rememberScrollState()

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Widget Agent 测试", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "对接 POST /api/v1/agent/chat。模拟器默认 http://10.0.2.2:8000；真机请填电脑局域网 IP。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::updateBaseUrl,
                label = { Text("API 地址") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
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
                label = { Text("user_id") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            SectionLabel("AI HOT 快捷（aihot Skill）")
            PresetChips(Presets.aihotPresets.map { it.label to it.message }) { viewModel.setMessage(it) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.dailyDate,
                    onValueChange = viewModel::updateDailyDate,
                    label = { Text("日报日期 YYYY-MM-DD") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedButton(onClick = viewModel::applyDailyDateMessage) {
                    Text("查该日")
                }
            }

            SectionLabel("其它 Skill（点击填入 message）")
            if (state.skillsLoading) {
                Text("加载中…", style = MaterialTheme.typography.bodySmall)
            } else if (state.skillsError != null) {
                Text(state.skillsError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else {
                PresetChips(state.skills.map { s -> s.name to "请使用 Skill「${s.name}」：${s.description}" }) {
                    viewModel.setMessage(it)
                }
            }

            OutlinedTextField(
                value = state.message,
                onValueChange = viewModel::updateMessage,
                label = { Text("message") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                minLines = 3,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = state.useStream,
                    onCheckedChange = viewModel::updateUseStream,
                )
                Text("使用 SSE 流式（/agent/chat/stream）")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        viewModel.onConfigChanged()
                        viewModel.send()
                    },
                    enabled = !state.isSending,
                ) {
                    Text(if (state.isSending) "发送中…" else "发送")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.onConfigChanged()
                        viewModel.refreshSkills()
                    },
                ) {
                    Text("刷新 Skills")
                }
            }

            state.statusLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            state.errorLine?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            state.result?.let { ResultPanel(it, state.liveTrace) }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** 分区小标题。 */
@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 快捷芯片：点击后将第二项（完整 message）写入输入框。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetChips(items: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { (label, message) ->
            FilterChip(
                selected = false,
                onClick = { onSelect(message) },
                label = { Text(label) },
            )
        }
    }
}

/** 展示 [WidgetResult] 与 debug_trace（SSE 时用 liveTrace 实时列表）。 */
@Composable
private fun ResultPanel(result: WidgetResult, liveTrace: List<String>) {
    val traces = if (liveTrace.isNotEmpty()) liveTrace else result.debugTrace
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(result.title.ifBlank { "(无标题)" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            val statusColor =
                when (result.status) {
                    "ok" -> MaterialTheme.colorScheme.primary
                    "error" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            Text("status: ${result.status}", color = statusColor, style = MaterialTheme.typography.labelMedium)
            val body =
                when {
                    result.content.isNotBlank() -> result.content
                    result.errorMsg.isNotBlank() -> result.errorMsg
                    else -> ""
                }
            Text(body, style = MaterialTheme.typography.bodyMedium)
            if (traces.isNotEmpty()) {
                Text("debug_trace", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .verticalScroll(rememberScrollState()),
                ) {
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
