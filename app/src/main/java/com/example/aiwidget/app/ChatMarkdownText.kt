package com.example.aiwidget.app

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * 将 Markdown 渲染为可点击链接的文本（用于 Agent 回复气泡）。
 * trace 等仍用纯 [Text]。
 */
@Composable
fun ChatMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    if (markdown.isBlank()) {
        Text("(空)", style = textStyle, color = color, modifier = modifier)
        return
    }

    val context = LocalContext.current
    val markwon =
        remember(context) {
            Markwon.builder(context)
                .usePlugin(LinkifyPlugin.create())
                .build()
        }
    val textColor = color.toArgb()
    val fontSizeSp =
        if (textStyle.fontSize.type == TextUnitType.Sp) {
            textStyle.fontSize.value
        } else {
            MaterialTheme.typography.bodyMedium.fontSize.value
        }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(true)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.textSize = fontSizeSp
            markwon.setMarkdown(textView, markdown)
        },
    )
}
