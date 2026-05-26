package com.example.aiwidget.util

import android.util.Log

/**
 * 统一日志前缀，Logcat 过滤：`AiWidget` 或各模块 TAG。
 */
object AppLog {
    const val PREFIX = "AiWidget"

    fun d(tag: String, message: String) {
        Log.d(tag, format(message))
    }

    fun i(tag: String, message: String) {
        Log.i(tag, format(message))
    }

    fun w(tag: String, message: String) {
        Log.w(tag, format(message))
    }

    fun e(tag: String, message: String, tr: Throwable? = null) {
        if (tr != null) {
            Log.e(tag, format(message), tr)
        } else {
            Log.e(tag, format(message))
        }
    }

    private fun format(message: String): String = "[$PREFIX] $message"
}
