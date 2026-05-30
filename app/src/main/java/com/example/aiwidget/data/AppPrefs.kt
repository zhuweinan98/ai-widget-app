package com.example.aiwidget.data

import android.content.Context
import java.util.UUID

/**
 * 全 App 共享的持久化配置（SharedPreferences `app_prefs`）。
 *
 * - [baseUrl] / [apiKey]：对话页与 Widget 共用
 * - [userId]：对话与 Widget 共用；首次启动 [getOrCreateUserId] 生成 UUID
 */
class AppPrefs(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString(KEY_BASE_URL, Presets.DEFAULT_BASE_URL) ?: Presets.DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(KEY_BASE_URL, value.trim()).apply()

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, Presets.DEFAULT_API_KEY) ?: Presets.DEFAULT_API_KEY
        set(value) = prefs.edit().putString(KEY_API_KEY, value).apply()

    var userId: String
        get() = prefs.getString(KEY_USER_ID, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_USER_ID, value.trim()).apply()

    /**
     * 返回已持久化的 user_id；若尚未设置则生成 UUID 并保存。
     * 对话页与 Widget 请求 Agent 时均应使用此方法（或读取 [userId]）。
     */
    fun getOrCreateUserId(): String {
        val existing = userId.trim()
        if (existing.isNotEmpty()) return existing
        val created = UUID.randomUUID().toString()
        userId = created
        return created
    }

    companion object {
        const val PREFS_NAME = "app_prefs"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_USER_ID = "user_id"
    }
}
