package com.example.aiwidget.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable

/**
 * 外层 Scaffold 内容区：顶栏 inset 交给内容；底栏由 [NavigationBar] 处理；键盘由对话输入区 imePadding。
 */
@Composable
fun AppShellContentWindowInsets(): WindowInsets =
    WindowInsets.safeDrawing
        .exclude(WindowInsets.navigationBars)
        .exclude(WindowInsets.ime)

/** 内层页 Scaffold：不再叠加系统栏 inset，仅保留 topBar 占位。 */
val NestedScaffoldContentInsets = WindowInsets(0, 0, 0, 0)
