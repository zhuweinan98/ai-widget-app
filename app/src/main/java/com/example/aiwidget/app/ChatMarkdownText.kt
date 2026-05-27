package com.example.aiwidget.app

import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.text.method.LinkMovementMethod
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
 * - 默认关闭文本选择；仅在按下链接时拦截父级滚动，避免整页无法上下滑动
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
                setOnTouchListener(markdownLinkTouchListener)
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

/**
 * 仅在触摸落在链接上时禁止父级 [androidx.compose.foundation.verticalScroll] 拦截，
 * 否则原文页/长文 Markdown 区域无法上下滑动。
 */
private val markdownLinkTouchListener =
    View.OnTouchListener { view, event ->
        val textView = view as? TextView ?: return@OnTouchListener false
        val text = textView.text as? Spannable
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val onLink = text != null && isTouchOnClickableLink(textView, text, event)
                textView.parent?.requestDisallowInterceptTouchEvent(onLink)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                textView.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        false
    }

private fun isTouchOnClickableLink(
    textView: TextView,
    text: Spannable,
    event: MotionEvent,
): Boolean {
    val offset =
        try {
            textView.getOffsetForPosition(event.x, event.y)
        } catch (_: Exception) {
            -1
        }
    if (offset < 0) return false
    return text.getSpans(offset, offset, ClickableSpan::class.java).isNotEmpty()
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
