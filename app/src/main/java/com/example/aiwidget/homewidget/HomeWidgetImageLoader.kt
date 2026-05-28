package com.example.aiwidget.homewidget

import android.content.Context
import com.example.aiwidget.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/** 将 [imageUrl] 下载到本地，供 Widget [RemoteViews] 展示。 */
object HomeWidgetImageLoader {
    private const val TAG = "HomeWidgetImageLoader"

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

    suspend fun download(
        context: Context,
        cacheSlot: String,
        imageUrl: String,
    ): String? =
        withContext(Dispatchers.IO) {
            val url = imageUrl.trim()
            if (url.isBlank()) return@withContext null
            try {
                val request = Request.Builder().url(url).get().build()
                val response = client.newCall(request).execute()
                response.use {
                    if (!it.isSuccessful) {
                        AppLog.w(TAG, "下载失败 slot=$cacheSlot code=${it.code}")
                        return@withContext null
                    }
                    val body = it.body ?: return@withContext null
                    val dir = File(context.filesDir, "widget_images").apply { mkdirs() }
                    val out = File(dir, "$cacheSlot.jpg")
                    body.byteStream().use { input ->
                        out.outputStream().use { output -> input.copyTo(output) }
                    }
                    out.absolutePath
                }
            } catch (e: Exception) {
                AppLog.e(TAG, "下载异常 slot=$cacheSlot", e)
                null
            }
        }
}
