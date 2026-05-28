package com.example.aiwidget.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val listTimeFormat =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

private val messageBubbleTimeFormat =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/** 对话消息 `created_at`（ISO UTC）与 UI `timestampMs` 互转。 */
object ChatTimeFormat {
    private val utc: TimeZone = TimeZone.getTimeZone("UTC")

    private val writeFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = utc }

    private val readFormats =
        listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
        ).map { pattern ->
            SimpleDateFormat(pattern, Locale.US)
        }

    fun formatCreatedAt(epochMs: Long = System.currentTimeMillis()): String = writeFormat.format(Date(epochMs))

    /** 会话列表行右侧/副标题用的时间文案。 */
    fun formatSessionListLabel(updatedAt: String): String {
        val ms = toEpochMillis(updatedAt, fallbackMs = 0L)
        if (ms <= 0L) return ""
        return listTimeFormat.format(Date(ms))
    }

    /** 对话气泡旁：年月日 + 时分。 */
    fun formatMessageBubbleTime(epochMs: Long): String {
        if (epochMs <= 0L) return ""
        return messageBubbleTimeFormat.format(Date(epochMs))
    }

    fun toEpochMillis(createdAt: String, fallbackMs: Long = System.currentTimeMillis()): Long {
        val raw = createdAt.trim()
        if (raw.isEmpty()) return fallbackMs
        raw.toLongOrNull()?.let { return it }
        for (format in readFormats) {
            format.timeZone = utc
            try {
                val parsed = format.parse(raw)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return fallbackMs
    }
}
