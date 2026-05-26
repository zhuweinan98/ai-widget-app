package com.example.aiwidget.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Widget 速报原文：顶部横向 Tab 切换 enabled 任务，下方 Markdown 正文。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleScreen(
    articles: List<WidgetArticleSnapshot>,
    selectedTaskId: String?,
    onSelectTask: (String) -> Unit,
    onLinkClick: (String) -> Unit,
) {
    val selectedIndex =
        articles.indexOfFirst { it.taskId == selectedTaskId }.let { if (it >= 0) it else 0 }
    val article = articles.getOrNull(selectedIndex) ?: return
    val scroll = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(title = { Text("速报") })
                if (articles.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        edgePadding = 12.dp,
                    ) {
                        articles.forEachIndexed { index, item ->
                            Tab(
                                selected = index == selectedIndex,
                                onClick = { onSelectTask(item.taskId) },
                                text = {
                                    Text(
                                        text = item.taskTitle,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = article.timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (article.hasContent) {
                ChatMarkdownText(
                    markdown = article.rawContent,
                    modifier = Modifier.fillMaxWidth(),
                    onLinkClick = onLinkClick,
                )
            } else {
                Text(
                    text = "暂无缓存，请在桌面 Widget 点 ↻ 刷新",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
