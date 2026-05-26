package com.example.aiwidget.data

/** 对话页快捷芯片：展示名 + 完整 prompt。 */
data class MessagePreset(
    val label: String,
    val hint: String,
    val message: String,
)

/** 默认后端地址、API Key 与对话快捷 prompt。 */
object Presets {
    /** 本机；真机联调配合 `adb reverse tcp:8000 tcp:8000`。模拟器请改为 `10.0.2.2`。 */
    const val LOCAL_BASE_URL = "http://127.0.0.1:8000"

    /** 团队联调服务器。 */
    const val SERVER_BASE_URL = "http://47.116.40.195:8000"

    const val DEFAULT_BASE_URL = LOCAL_BASE_URL
    const val DEFAULT_API_KEY = "local-dev-key"

    /** 仅当尚未有 [AppPrefs.userId] 时的占位；实际首次启动会生成 UUID。 */
    const val DEFAULT_USER_ID = "u1"

    /** 对话页底部快捷芯片；不写入 Widget 定时任务。 */
    val chatPresets =
        listOf(
            MessagePreset(
                label = "1h 速报",
                hint = "与 Widget 默认定时相同",
                message = WidgetConfig.REFRESH_MESSAGE,
            ),
            MessagePreset(
                label = "24h 精选",
                hint = "仅手动试发",
                message =
                    "请使用 aihot Skill：查询过去 24 小时 AI 圈精选大新闻。先 load_skill_context(\"aihot\")，再 run_skill_script(\"aihot\", \"ai_hot_daily.py\")。终局有且仅有一行 JSON（title+content），content 为 3～8 条要点，勿编造，勿先写长文说明。",
            ),
            MessagePreset(
                label = "今日日报",
                hint = "仅手动试发",
                message =
                    "请使用 aihot Skill：拉取今日（或最新可用）AI 日报。先 load_skill_context(\"aihot\")，再 http_request(url=\"https://aihot.virxact.com/api/public/daily\")，按 SKILL 日报版块格式整理为 Markdown，勿编造。",
            ),
        )
}
