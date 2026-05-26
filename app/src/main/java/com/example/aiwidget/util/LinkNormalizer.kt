package com.example.aiwidget.util

/** 规范化 Markdown / 纯文本里的 URL，便于 WebView 或 Intent 打开。 */
object LinkNormalizer {
    fun normalize(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> "https://$trimmed"
        }
    }
}
