package com.example.aiwidget.data

import android.content.Context

/**
 * 测试页配置持久化（SharedPreferences）。
 *
 * 保存 API 地址、Key、user_id、message、是否 SSE，下次打开 App 自动恢复。
 */
class TestPrefs(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 后端根地址，如 `http://10.0.2.2:8000`。 */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, Presets.DEFAULT_BASE_URL) ?: Presets.DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value).apply()

    /** 请求头 `X-API-Key`。 */
    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, Presets.DEFAULT_API_KEY) ?: Presets.DEFAULT_API_KEY
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var userId: String
        get() = prefs.getString(KEY_USER_ID, Presets.DEFAULT_USER_ID) ?: Presets.DEFAULT_USER_ID
        set(value) = prefs.edit().putString(KEY_USER_ID, value).apply()

    var message: String
        get() = prefs.getString(KEY_MESSAGE, Presets.DEFAULT_MESSAGE) ?: Presets.DEFAULT_MESSAGE
        set(value) = prefs.edit().putString(KEY_MESSAGE, value).apply()

    /** 是否走 `POST /agent/chat/stream`（SSE Trace）。 */
    var useStream: Boolean
        get() = prefs.getBoolean(KEY_USE_STREAM, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_STREAM, value).apply()

    companion object {
        private const val PREFS_NAME = "widget_agent_test"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_MESSAGE = "message"
        private const val KEY_USE_STREAM = "use_stream"
    }
}
