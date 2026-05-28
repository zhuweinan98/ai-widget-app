package com.example.aiwidget.data

import com.squareup.moshi.Json

/** 服务端 `render_card` 的 `template` 取值（与 widget-agent-server 约定）。 */
object WidgetTemplate {
    /** 列表：icon + title + value */
    const val LIST_CARD = "list_card"

    /** 列表：icon + title */
    const val ICON_TITLE = "icon_title"

    /** 卡片：headline + subtitle + image */
    const val IMAGE_CARD = "image_card"

    val SUPPORTED = setOf(LIST_CARD, ICON_TITLE, IMAGE_CARD)
}

/** `list_card` / `icon_title` 的 `items[]` 元素。 */
data class WidgetListItem(
    @Json(name = "title") val title: String = "",
    @Json(name = "value") val value: String = "",
    @Json(name = "icon") val icon: String = "",
)

/** `list_card` / `icon_title` item 的 `icon` 码表。 */
object WidgetListItemIcon {
    const val UP = "up"
    const val DOWN = "down"
    const val HIGH = "high"
    const val MEDIUM = "medium"
    const val LOW = "low"
}

private val BAD_TERMINAL = Regex("need more steps", RegexOption.IGNORE_CASE)

/** 校验 Widget 终局是否可展示；返回 null 表示通过。 */
fun WidgetResult.validateForWidget(): String? {
    if (status != "ok") {
        return errorMsg.trim().ifBlank { "status=${status}" }
    }
    if (title.trim().isBlank()) {
        return "title 为空"
    }
    if (BAD_TERMINAL.containsMatchIn(title) ||
        BAD_TERMINAL.containsMatchIn(content) ||
        BAD_TERMINAL.containsMatchIn(headline)
    ) {
        return "终局不可用: need more steps"
    }
    val templateKey = template.trim()
    if (templateKey.isBlank()) {
        return "template 为空"
    }
    if (templateKey !in WidgetTemplate.SUPPORTED) {
        return "未知 template: $templateKey"
    }
    return when (templateKey) {
        WidgetTemplate.LIST_CARD,
        WidgetTemplate.ICON_TITLE,
        ->
            if (items.isEmpty()) "$templateKey 需要非空 items" else null
        WidgetTemplate.IMAGE_CARD ->
            if (headline.trim().isBlank()) "image_card 需要非空 headline" else null
        else -> null // templateKey 为 String，需 else；SUPPORTED 校验后不应到达
    }
}
