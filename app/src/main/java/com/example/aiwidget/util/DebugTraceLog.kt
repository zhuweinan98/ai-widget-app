package com.example.aiwidget.util

/** 将终局响应里的 [debug_trace] 逐行写入 Logcat。 */
object DebugTraceLog {
    private const val LINE_PREVIEW_CHARS = 320

    fun logAll(tag: String, source: String, traces: List<String>) {
        if (traces.isEmpty()) return
        AppLog.d(tag, "[$source] debug_trace lines=${traces.size}")
        traces.forEachIndexed { index, line ->
            AppLog.d(tag, "[$source] debug_trace[$index]=${previewLine(line)}")
        }
    }

    private fun previewLine(text: String): String {
        val oneLine = text.trim().replace('\n', '↵')
        return if (oneLine.length <= LINE_PREVIEW_CHARS) {
            oneLine
        } else {
            oneLine.take(LINE_PREVIEW_CHARS) + "…(${oneLine.length} chars)"
        }
    }
}
