package top.yogiczy.mytv.data.repositories.backend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.utils.Logger
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * 多域名故障转移
 * 仅在 App 本次启动后首次请求时拉取一次公网 JSON，之后进程内复用；
 * 下次冷启动/重新打开 App 再重新拉取。
 */
object ApiEndpointManager {
    private val log = Logger.create("ApiEndpoint")
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val endpoints = AtomicReference<List<String>>(emptyList())
    private val currentIndex = AtomicInteger(0)
    private val mutex = Mutex()
    private var loadedThisSession = false

    /**
     * 确保本会话已加载域名列表（仅启动后拉一次）
     * @param force true 时强制重新拉取（一般不必）
     */
    suspend fun ensureLoaded(force: Boolean = false) = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!force && loadedThisSession && endpoints.get().isNotEmpty()) {
                return@withLock
            }
            val fromJson = fetchFromJson()
            val fallback = Constants.BACKEND_API_URL.trim().takeIf {
                it.isNotBlank() && !it.contains("your-domain") && it.startsWith("http")
            }
            val list = (fromJson + listOfNotNull(fallback)).distinct()
            if (list.isNotEmpty()) {
                endpoints.set(list)
                if (currentIndex.get() >= list.size) currentIndex.set(0)
                loadedThisSession = true
                log.i("API 域名列表(本次启动): $list")
            } else {
                log.w("未获取到任何 API 域名")
            }
        }
    }

    private fun fetchFromJson(): List<String> {
        val url = Constants.BOOTSTRAP_JSON_URL
        if (url.isBlank() || url.contains("your-cdn.com")) {
            log.w("BOOTSTRAP_JSON_URL 未配置")
            return emptyList()
        }
        return try {
            val req = Request.Builder()
                .url(url)
                .get()
                .header("Cache-Control", "no-cache")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                parseJson(body)
            }
        } catch (e: Exception) {
            log.e("拉取 bootstrap JSON 失败: ${e.message}")
            emptyList()
        }
    }

    /**
     * 支持：
     * {"api":["url1","url2","url3"]}
     * {"api1":"url1","api2":"url2","api3":"url3"}
     * ["url1","url2","url3"]
     */
    fun parseJson(body: String): List<String> {
        val out = mutableListOf<String>()
        val trimmed = body.trim()
        try {
            when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    for (i in 0 until minOf(arr.length(), 3)) {
                        arr.optString(i).trim().takeIf { it.startsWith("http") }?.let { out.add(it) }
                    }
                }
                else -> {
                    val obj = JSONObject(trimmed)
                    val arr = obj.optJSONArray("api")
                    if (arr != null) {
                        for (i in 0 until minOf(arr.length(), 3)) {
                            arr.optString(i).trim().takeIf { it.startsWith("http") }?.let { out.add(it) }
                        }
                    } else {
                        listOf("api1", "api2", "api3", "api_1", "api_2", "api_3").forEach { key ->
                            obj.optString(key).trim().takeIf { it.startsWith("http") }?.let {
                                if (out.size < 3) out.add(it)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.e("解析 bootstrap JSON 失败: ${e.message}")
        }
        return out.distinct()
    }

    fun currentBaseUrl(): String? {
        val list = endpoints.get()
        if (list.isEmpty()) {
            val fallback = Constants.BACKEND_API_URL
            return fallback.takeIf { it.isNotBlank() && !it.contains("your-domain") }
        }
        val idx = currentIndex.get().coerceIn(0, list.lastIndex)
        return list[idx]
    }

    fun allEndpoints(): List<String> = endpoints.get()

    fun markFailed() {
        val list = endpoints.get()
        if (list.size <= 1) return
        val next = (currentIndex.get() + 1) % list.size
        currentIndex.set(next)
        log.w("切换 API 域名 -> [${next}] ${list[next]}")
    }

    /**
     * 按顺序尝试各域名；全部失败时再强制重新拉一次 JSON 重试
     */
    suspend fun <T> withFailover(block: suspend (baseUrl: String) -> T?): T? {
        ensureLoaded()
        var list = endpoints.get()
        if (list.isEmpty()) {
            val fb = Constants.BACKEND_API_URL
            if (fb.isNotBlank() && !fb.contains("your-domain")) list = listOf(fb)
        }
        if (list.isEmpty()) return null

        val start = currentIndex.get().coerceIn(0, list.lastIndex)
        for (offset in list.indices) {
            val idx = (start + offset) % list.size
            val base = list[idx]
            try {
                val result = block(base)
                if (result != null) {
                    currentIndex.set(idx)
                    return result
                }
            } catch (e: Exception) {
                log.w("API 失败 [$base]: ${e.message}")
            }
        }
        // 本会话列表全挂：再拉一次 JSON 重试（应对启动后才改了 JSON 的情况）
        ensureLoaded(force = true)
        list = endpoints.get()
        for (base in list) {
            try {
                val result = block(base)
                if (result != null) return result
            } catch (_: Exception) {
            }
        }
        return null
    }
}
