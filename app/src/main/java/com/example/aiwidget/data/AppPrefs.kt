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

    init {
        migrateFromLegacySessionPrefs()
        migrateUserIdFromLegacyKeys()
    }

    /** 从旧版 SessionPrefs 文件迁移 baseUrl/apiKey（仅首次）。 */
    private fun migrateFromLegacySessionPrefs() {
        if (prefs.contains(KEY_BASE_URL)) return
        val legacy = appContext.getSharedPreferences(SessionPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        legacy.getString("base_url", null)?.let { prefs.edit().putString(KEY_BASE_URL, it).apply() }
        legacy.getString("api_key", null)?.let { prefs.edit().putString(KEY_API_KEY, it).apply() }
    }

    /** 合并旧 `user_uuid` / SessionPrefs `user_id` 到统一 [KEY_USER_ID]。 */
    private fun migrateUserIdFromLegacyKeys() {
        if (prefs.getString(KEY_USER_ID, null)?.isNotBlank() == true) return
        val fromUuid = prefs.getString(KEY_USER_UUID_LEGACY, null)?.trim().orEmpty()
        if (fromUuid.isNotBlank()) {
            prefs.edit().putString(KEY_USER_ID, fromUuid).apply()
            return
        }
        val session =
            appContext.getSharedPreferences(SessionPrefs.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_USER_ID_LEGACY_SESSION, null)
                ?.trim()
                .orEmpty()
        if (session.isNotBlank()) {
            prefs.edit().putString(KEY_USER_ID, session).apply()
        }
    }

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
        /** 旧 Widget 专用字段，迁移后不再写入。 */
        private const val KEY_USER_UUID_LEGACY = "user_uuid"
        private const val KEY_USER_ID_LEGACY_SESSION = "user_id"
    }
}
