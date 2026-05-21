package com.example.aiwidget.data

/** 快捷按钮：展示名 + 填入的 message 全文。 */
data class MessagePreset(
    val label: String,
    val hint: String,
    val message: String,
)

/**
 * 测试页默认值与内置 prompt（对齐 widget-agent-server `/test` 页）。
 */
object Presets {
    /**
     * 模拟器访问本机 uvicorn 的默认 baseUrl。
     * `10.0.2.2` 指向宿主机；真机请改局域网 IP 或配合 adb reverse 用 127.0.0.1。
     */
    const val DEFAULT_BASE_URL = "http://10.0.2.2:8000"

    /** 与后端 `.env` 中 `API_KEY` 一致。 */
    const val DEFAULT_API_KEY = "local-dev-key"

    const val DEFAULT_USER_ID = "u1"
    const val DEFAULT_MESSAGE = "今天 AI 圈有什么大事？"

    /** AI HOT Skill 快捷 message（aihot）。 */
    val aihotPresets = listOf(
        MessagePreset(
            label = "24h 精选",
            hint = "过去 24 小时 · ai_hot_daily.py",
            message =
                "请使用 aihot Skill：查询过去 24 小时 AI 圈精选大新闻。先 load_skill_context(\"aihot\")，再 run_skill_script(\"aihot\", \"ai_hot_daily.py\")。终局有且仅有一行 JSON（title+content），content 为 3～8 条要点，勿编造，勿先写长文说明。",
        ),
        MessagePreset(
            label = "1h 速报",
            hint = "最近 1 小时 · ai_hot_hourly.py",
            message =
                "请使用 aihot Skill：生成最近 1 小时 AI 速报。先 load_skill_context(\"aihot\")，再 run_skill_script(\"aihot\", \"ai_hot_hourly.py\")。终局有且仅有一行 JSON，content 3～8 条，勿编造，勿先写说明。",
        ),
        MessagePreset(
            label = "今日日报",
            hint = "GET /api/public/daily",
            message =
                "请使用 aihot Skill：拉取今日（或最新可用）AI 日报。先 load_skill_context(\"aihot\")，再 http_request(url=\"https://aihot.virxact.com/api/public/daily\")，按 SKILL 日报版块格式整理为 Markdown，勿编造。",
        ),
    )

    /** 指定日期日报的 message（YYYY-MM-DD）。 */
    fun messageForDailyDate(dateStr: String): String =
        "请使用 aihot Skill：拉取 $dateStr 的 AI 日报。先 load_skill_context(\"aihot\")，再 " +
            "http_request(url=\"https://aihot.virxact.com/api/public/daily/$dateStr\")，" +
            "按 SKILL 日报格式整理为 Markdown；若 404 说明该日尚未生成，勿编造。"
}
