package com.example.aiwidget.app

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import com.example.aiwidget.util.LinkNormalizer

/** App 内 WebView：Markdown / 对话里的链接在此打开，不跳出 App。 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InAppBrowserScreen(
    url: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalizedUrl = remember(url) { LinkNormalizer.normalize(url) }
    var pageTitle by remember(normalizedUrl) { mutableStateOf(normalizedUrl.toUri().host ?: "链接") }

    // 与右上角关闭一致：返回 = 退出内置浏览器（避免 x.com 等重定向导致 canGoBack 但 goBack 无效）
    BackHandler(onBack = onClose)

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = pageTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "关闭")
                        }
                    },
                )
            },
        ) { innerPadding ->
            AndroidView(
                modifier =
                    Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient =
                            object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: WebResourceRequest,
                                ): Boolean {
                                    if (request.isForMainFrame) {
                                        view.loadUrl(request.url.toString())
                                    }
                                    return true
                                }

                                override fun onPageFinished(view: WebView, url: String?) {
                                    pageTitle = view.title?.takeIf { !it.isNullOrBlank() } ?: pageTitle
                                }
                            }
                        loadUrl(normalizedUrl)
                    }
                },
            )
        }
    }
}
