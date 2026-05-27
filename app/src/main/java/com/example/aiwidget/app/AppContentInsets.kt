package com.example.aiwidget.app

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable

/**
 * 外层 Scaffold 内容区：statusBars 由顶部 TabRow.statusBarsPadding() 处理；底部由输入区 navigationBarsPadding + imePadding。
 */
@Composable
fun AppShellContentWindowInsets(): WindowInsets =
    WindowInsets.safeDrawing
        .exclude(WindowInsets.statusBars)
        .exclude(WindowInsets.navigationBars)
        .exclude(WindowInsets.ime)

/** 内层页 Scaffold：不再叠加系统栏 inset，仅保留 topBar 占位。 */
val NestedScaffoldContentInsets = WindowInsets(0, 0, 0, 0)
