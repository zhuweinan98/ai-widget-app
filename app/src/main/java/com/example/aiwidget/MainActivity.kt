package com.example.aiwidget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.aiwidget.ui.TestScreen
import com.example.aiwidget.ui.theme.AIWidgetTheme

/**
 * 应用唯一 Activity 入口。
 *
 * 当前挂载 [com.example.aiwidget.ui.TestScreen]（后端联调测试页）。
 * 完整产品形态见 ARCHITECTURE.md：对话页 + 桌面 Widget + WorkManager。
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIWidgetTheme {
                TestScreen()
            }
        }
    }
}
