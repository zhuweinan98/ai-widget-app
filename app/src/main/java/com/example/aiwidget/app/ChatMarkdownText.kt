package com.example.aiwidget.app

import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.view.MotionEvent
import android.view.View
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
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * 将 Markdown 渲染为可点击链接的文本。
 *
 * - `[查看原文](url)` 与纯文本 URL 均可点击
 * - 默认关闭文本选择，避免与链接点击冲突（在 [verticalScroll] 里尤其明显）
 */
@Composable
fun ChatMarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    onLinkClick: (String) -> Unit,
) {
    if (markdown.isBlank()) {
        Text("(空)", style = textStyle, color = color, modifier = modifier)
        return
    }

    val context = LocalContext.current
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val markwon =
        remember(context, onLinkClick) {
            Markwon.builder(context)
                .usePlugin(LinkifyPlugin.create())
                .usePlugin(
                    object : AbstractMarkwonPlugin() {
                        override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                            builder.linkResolver(
                                LinkResolver { _, link ->
                                    onLinkClick(link)
                                },
                            )
                        }
                    },
                )
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
                linksClickable = true
                setTextIsSelectable(false)
                setLinkTextColor(linkColor)
                setOnTouchListener(linkTouchListener)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.textSize = fontSizeSp
            textView.setLinkTextColor(linkColor)
            applyMarkdownWithLinkClicks(textView, markwon, markdown, onLinkClick)
        },
    )
}

/** 滚动容器内点击链接时，避免父级抢走手势。 */
private val linkTouchListener =
    View.OnTouchListener { view, event ->
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_DOWN) {
            view.parent?.requestDisallowInterceptTouchEvent(true)
        }
        false
    }

private fun applyMarkdownWithLinkClicks(
    textView: TextView,
    markwon: Markwon,
    markdown: String,
    onLinkClick: (String) -> Unit,
) {
    markwon.setMarkdown(textView, markdown)
    val spannable = textView.text as? Spannable ?: return
    spannable.getSpans(0, spannable.length, URLSpan::class.java).forEach { span ->
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        val flags = spannable.getSpanFlags(span)
        spannable.removeSpan(span)
        spannable.setSpan(
            object : ClickableSpan() {
                override fun onClick(widget: View) {
                    onLinkClick(span.url)
                }

                override fun updateDrawState(ds: TextPaint) {
                    super.updateDrawState(ds)
                    ds.isUnderlineText = true
                }
            },
            start,
            end,
            flags or Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
}
