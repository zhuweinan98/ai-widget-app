package com.example.aiwidget.data

import android.content.Context

/**
 * 对话页会话配置（SharedPreferences `widget_agent_test`）。
 *
 * [baseUrl] / [apiKey] / [userId] 均代理到 [AppPrefs]，与 Widget 共用。
 * 仅 [useStream] 存在本文件。
 */
class SessionPrefs(context: Context) {
    private val ctx = context.applicationContext
    private val sessionPrefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appPrefs = AppPrefs(ctx)

    var baseUrl: String
        get() = appPrefs.baseUrl
        set(value) {
            appPrefs.baseUrl = value
        }

    var apiKey: String
        get() = appPrefs.apiKey
        set(value) {
            appPrefs.apiKey = value
        }

    var userId: String
        get() = appPrefs.getOrCreateUserId()
        set(value) {
            appPrefs.userId = value
        }

    var useStream: Boolean
        get() = sessionPrefs.getBoolean(KEY_USE_STREAM, true)
        set(value) = sessionPrefs.edit().putBoolean(KEY_USE_STREAM, value).apply()

    companion object {
        const val PREFS_NAME = "widget_agent_test"
        private const val KEY_USE_STREAM = "use_stream"
    }
}
