package com.example.aiwidget.homewidget

import com.example.aiwidget.data.WidgetConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 将 Agent 返回的正文格式化为 Widget RemoteViews 可展示的标题/摘要/时间。
 *
 * 当前从 Markdown `**标题**` 启发式抽取；终局应优先用服务端专用字段（见 TODO）。
 */
object HomeWidgetDisplayFormatter {
    /** 行内 `**标题**` 片段（速报条目主特征）。 */
    private val BOLD_SEGMENT = Regex("""\*\*(.+?)\*\*""")

    fun formatTitle(raw: String, fallback: String = "AI 快讯"): String {
        val t = raw.trim().ifBlank { fallback }
        return if (t.length <= WidgetConfig.TITLE_MAX_CHARS) {
            t
        } else {
            t.take(WidgetConfig.TITLE_MAX_CHARS) + "…"
        }
    }

    /**
     * 从 Agent 终局 [content] 提取加粗标题行，供桌面 Widget（不含每条新闻正文）。
     * 假设要闻标题含 `**…**`；抽不到则回退为截断原文。
     *
     * TODO: 与服务端格式对齐——终局 JSON 增加 widget_summary/headlines，此处优先用服务端字段。
     */
    fun formatHeadlinesFromContent(raw: String): String {
        val text = raw.trim()
        if (text.isEmpty()) return "暂无要闻"

        val headlines = LinkedHashSet<String>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) continue
            if (isReportHeaderLine(trimmed)) continue

            val title = extractBoldHeadline(trimmed) ?: continue
            if (title.length > WidgetConfig.HEADLINE_MAX_CHARS_PER_ITEM) continue
            headlines.add(title)
            if (headlines.size >= WidgetConfig.HEADLINE_MAX_ITEMS) break
        }

        if (headlines.isEmpty()) {
            return formatSummaryFallback(text)
        }
        return trimToMaxChars(headlines.joinToString("\n") { "· $it" })
    }

    /** 读取缓存：已是 `· 标题` 列表则直接用，否则按正文再抽一次。 */
    fun normalizeWidgetSummary(cached: String): String {
        val text = cached.trim()
        if (text.isEmpty()) return ""
        if (text.lines().all { it.trim().startsWith("· ") }) return text
        return formatHeadlinesFromContent(text)
    }

    /**
     * Widget 右上角「HH:mm 更新」。
     * 使用本地任务成功写入缓存的时刻，而非 API 响应里的 [com.example.aiwidget.data.WidgetResult.updatedAt]。
     */
    fun formatRefreshTime(finishedAtMs: Long = System.currentTimeMillis()): String {
        val time =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(finishedAtMs))
        return "$time 更新"
    }

    private fun extractBoldHeadline(line: String): String? {
        val segments =
            BOLD_SEGMENT
                .findAll(line)
                .map { it.groupValues[1].trim() }
                .filter { it.length >= 2 && !it.contains("截至") }
                .toList()
        if (segments.isEmpty()) return null
        // 同一行多个加粗时取最长一段（多为完整标题）
        return segments.maxByOrNull { it.length }
    }

    private fun isReportHeaderLine(line: String): Boolean =
        line.contains("截至") && BOLD_SEGMENT.containsMatchIn(line)

    private fun formatSummaryFallback(raw: String): String {
        val text = raw.trim().ifBlank { "暂无摘要" }
        if (text.length <= WidgetConfig.SUMMARY_MAX_CHARS) return text
        return text.take(WidgetConfig.SUMMARY_MAX_CHARS).trimEnd() + "…"
    }

    private fun trimToMaxChars(joined: String): String {
        if (joined.length <= WidgetConfig.SUMMARY_MAX_CHARS) return joined
        return joined.take(WidgetConfig.SUMMARY_MAX_CHARS).trimEnd() + "…"
    }
}
