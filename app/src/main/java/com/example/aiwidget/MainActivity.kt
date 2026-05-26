package com.example.aiwidget

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.aiwidget.app.AppShellScreen
import com.example.aiwidget.app.AppShellViewModel
import com.example.aiwidget.app.theme.AIWidgetTheme
import com.example.aiwidget.data.AppPrefs
import com.example.aiwidget.homewidget.HomeWidgetCoordinator

/**
 * 应用唯一 Activity：挂载 [com.example.aiwidget.app.AppShellScreen]。
 *
 * - Launcher 打开：默认「消息」Tab
 * - 点击桌面 Widget 主体：[EXTRA_FROM_WIDGET] → 有缓存进「原文」，否则进「消息」
 */
class MainActivity : ComponentActivity() {
    var widgetOpenHandler: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs(this).getOrCreateUserId()
        HomeWidgetCoordinator.scheduleEnabledWidgetTasks(this)
        enableEdgeToEdge()
        setContent {
            AIWidgetTheme {
                val viewModel: AppShellViewModel = viewModel()
                val activity = LocalContext.current as MainActivity

                DisposableEffect(viewModel) {
                    activity.widgetOpenHandler = { viewModel.onLaunch(fromWidget = true) }
                    onDispose { activity.widgetOpenHandler = null }
                }

                LaunchedEffect(Unit) {
                    viewModel.onLaunch(intent.getBooleanExtra(EXTRA_FROM_WIDGET, false))
                }

                AppShellScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_FROM_WIDGET, false)) {
            widgetOpenHandler?.invoke()
        }
    }

    companion object {
        const val EXTRA_FROM_WIDGET = "extra_from_widget"
    }
}
