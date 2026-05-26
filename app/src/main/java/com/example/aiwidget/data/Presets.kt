package com.example.aiwidget.data

/** 对话页快捷芯片：展示名 + 发给 Agent 的 message（自然语言）。 */
data class MessagePreset(
    val label: String,
    val hint: String,
    val message: String,
)

/**
 * 默认 API 配置，以及**全部发给 Agent 的自然语言**（对话芯片与 Widget 定时共用）。
 *
 * - 对话页：芯片直接发 [message]
 * - Widget：`WidgetTask.prompt` 存用户语；发送时用 [buildWidgetTaskPrompt] 追加 [WIDGET_RESPONSE_FORMAT]
 */
object Presets {
    /** 本机；真机联调配合 `adb reverse tcp:8000 tcp:8000`。模拟器请改为 `10.0.2.2`。 */
    const val LOCAL_BASE_URL = "http://127.0.0.1:8000"

    /** 团队联调服务器。 */
    const val SERVER_BASE_URL = "http://47.116.40.195:8000"

    const val DEFAULT_BASE_URL = LOCAL_BASE_URL
    const val DEFAULT_API_KEY = "local-dev-key"

    /** 仅当尚未有 [AppPrefs.userId] 时的占位；实际首次启动会生成 UUID。 */
    const val DEFAULT_USER_ID = "u1"

    // ── 用户自然语言（芯片 + Widget 任务 prompt 共用）────────────────────────

    /** 最近一小时 AI 要闻（芯片「1h 速报」/ 默认定时「AI 1h 速报」）。 */
    const val AI_1H_USER_MESSAGE = "最近一小时 AI 圈有什么重要新闻？"

    /** 过去 24 小时精选（芯片「24h 精选」）。 */
    const val AI_24H_USER_MESSAGE = "过去 24 小时 AI 圈有哪些值得关注的精选要闻？"

    /** 当日日报（芯片「今日日报」）。 */
    const val AI_DAILY_USER_MESSAGE = "今天的 AI 日报有哪些主要内容？"

    /** 持仓盈亏（芯片 + 默认定时「持仓盈亏」）；改持仓直接改此处。 */
    fun holdingsUserMessage(): String =
        """
        以下是我的持仓清单，请帮我测算其中公募基金与 ETF/联接的当前盈亏表现（如最新净值、涨跌幅、估算浮盈浮亏或近期收益；数据不足请说明缺少什么，勿编造未给出的标的或数字）：

        【公募基金】000979、019004
        【重仓股参考】中际旭创（300308·sz）、紫金矿业(H)（02899·hk）、新易盛（300502·sz）、工业富联（601138·sh）、腾讯控股（00700·hk）、天孚通信（300394·sz）、美的集团（000333·sz）、渣打集团（02888·hk）、阿里巴巴-W（09988·hk）、沪电股份（002463·sz）等
        【ETF/联接】华夏国证半导体芯片ETF联接C（008888）、招商中证消费电子主题ETF联接C（016008）、南方储能电池ETF联接C（018927）、融通产业趋势臻选股票C（018495）、华宝创业板人工智能ETF联接C（023408）
        """.trimIndent()

    // ── Widget 专用：输出格式后缀（拼在用户语后面）──────────────────────────

    /**
     * Widget 定时/手动刷新时追加的 JSON/Markdown 格式要求。
     * 对应响应 [WidgetResult]：`title`、`content`、`status`。
     */
    val WIDGET_RESPONSE_FORMAT: String =
        """
        请直接回答上述问题。终局有且仅有一行 JSON，字段：title、content（3～8 条要点，Markdown）、status 固定 ok；勿先写长文说明，勿编造。
        title：供桌面 Widget 首行，≤20 字，写整体结论或主题；若回答含汇总/统计，务必把核心数字写进 title（如涨跌概况、条数）。
        content：每条要点独立一行；**加粗须含该条最关键信息**（桌面卡片只展示加粗片段，勿把数字或标题写在加粗外）。叙述类要点：**标题** 后接说明，有可靠来源 URL 时同行附 [查看原文](https://...) 或 [媒体名](https://...)，有链接务必保留、无链接勿编造；数据/列表类要点：**标识 简称 +核心指标**（如 **000979 景顺长城 +2.32%**），说明放加粗后。
        """.trimIndent().replace('\n', ' ')

    /** 用户自然语言 + Widget 输出格式，发给 `POST /agent/chat`。 */
    fun buildWidgetTaskPrompt(userMessage: String): String {
        val intent = userMessage.trim()
        require(intent.isNotEmpty()) { "Widget task prompt cannot be blank" }
        return "$intent $WIDGET_RESPONSE_FORMAT"
    }

    // ── 对话页快捷芯片 ────────────────────────────────────────────────────

    fun chatPresets(): List<MessagePreset> =
        listOf(
            MessagePreset(
                label = "1h 速报",
                hint = "举例：最近一小时要闻",
                message = AI_1H_USER_MESSAGE,
            ),
            MessagePreset(
                label = "24h 精选",
                hint = "举例：过去一天精选",
                message = AI_24H_USER_MESSAGE,
            ),
            MessagePreset(
                label = "今日日报",
                hint = "举例：当日 AI 日报",
                message = AI_DAILY_USER_MESSAGE,
            ),
            MessagePreset(
                label = "持仓盈亏",
                hint = "举例：基金当前盈亏",
                message = holdingsUserMessage(),
            ),
        )
}
