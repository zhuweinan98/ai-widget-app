package com.example.aiwidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.aiwidget.data.AppPrefs
import com.example.aiwidget.app.AppShellScreen
import com.example.aiwidget.app.theme.AIWidgetTheme
import com.example.aiwidget.homewidget.HomeWidgetCoordinator

/**
 * 应用唯一 Activity：挂载 [com.example.aiwidget.app.AppShellScreen]。
 *
 * 启动时尝试登记 Widget 定时（无 Widget 实例时 [HomeWidgetCoordinator] 内部会跳过）。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppPrefs(this).getOrCreateUserId()
        HomeWidgetCoordinator.scheduleEnabledWidgetTasks(this)
        enableEdgeToEdge()
        setContent {
            AIWidgetTheme {
                AppShellScreen()
            }
        }
    }
}
