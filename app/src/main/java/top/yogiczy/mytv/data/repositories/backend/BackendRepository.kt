package top.yogiczy.mytv.data.repositories.backend

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import top.yogiczy.mytv.utils.DeviceUtils
import top.yogiczy.mytv.utils.Logger
import java.util.concurrent.TimeUnit

class BackendRepository(
    private val context: Context,
    private val client: OkHttpClient = defaultClient,
) {
    private val log = Logger.create("BackendRepository")

    data class CheckResult(
        val needAuth: Boolean,
        val authorized: Boolean,
        val account: String,
        val mac: String,
        val snid: String,
        val province: String,
        val isp: String,
        val provinceMode: String,
        val dialogTitle: String,
        val dialogTip: String,
        val provinces: List<String>?,
        val isps: List<String>,
        val expireAt: String?,
        val isFirst: Boolean,
        val appName: String = "",
        val errorMsg: String? = null,
    )

    data class AuthResult(
        val success: Boolean,
        val msg: String,
        val expireAt: String?,
    )

    data class ConfigResult(
        val authorized: Boolean,
        val m3uUrl: String,
        val epgUrl: String,
        val ua: String,
        val cacheTime: Long,
        val sourceName: String,
        val account: String,
        val expireAt: String?,
        val province: String,
        val isp: String,
        val playerType: String = "exo_hard",
        val loadTimeout: Int = 10,
        val channelRev: Boolean = true,
        val splashUrl: String = "",
        val marqueeEnable: Boolean = false,
        val marqueeText: String = "",
        val marqueeSize: Int = 16,
        val marqueeColor: String = "#FFD700",
        val marqueeMode: String = "always",
        val marqueeIntervalMin: Int = 30,
        val marqueeShowSec: Int = 30,
        val upgradeVersion: String = "",
        val upgradeUrl: String = "",
        val upgradeForce: Boolean = false,
        val upgradeMsg: String = "",
    )

    private fun devicePayload(province: String? = null, isp: String? = null): JSONObject {
        return JSONObject().apply {
            val mac = DeviceUtils.getMacAddress(context)
            val serial = DeviceUtils.getSerial()
            val androidId = DeviceUtils.getAndroidId(context)
            // 无 MAC/序列号时用 AndroidID 作为设备标识
            put("mac", mac)
            put("snid", serial.ifBlank { androidId })
            put("android_id", androidId)
            put("model", DeviceUtils.getModel())
            put("brand", DeviceUtils.getBrand())
            put("os_version", DeviceUtils.getOsVersion())
            put("app_version", try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (_: Exception) { "" })
            if (!province.isNullOrBlank()) put("province", province)
            if (!isp.isNullOrBlank()) put("isp", isp)
        }
    }

    private fun postJson(baseUrl: String, action: String, payload: JSONObject): JSONObject? {
        val clean = baseUrl.trim().trimEnd('/')
        val sep = if (clean.contains("?")) "&" else "?"
        val url = "${clean}${sep}action=$action"
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url(url)
            .post(body)
            .header("Accept", "application/json")
            .build()
        log.i("POST $url")
        client.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            log.i("HTTP ${resp.code} body=${text.take(300)}")
            if (text.isBlank()) return null
            return try {
                JSONObject(text)
            } catch (e: Exception) {
                log.e("JSON 解析失败: ${text.take(200)}")
                null
            }
        }
    }

    suspend fun checkDevice(province: String? = null, isp: String? = null): CheckResult? =
        withContext(Dispatchers.IO) {
            ApiEndpointManager.withFailover { base ->
                val json = postJson(base, "device/check", devicePayload(province, isp))
                    ?: return@withFailover null
                val code = json.optInt("code", -1)
                val msg = json.optString("msg", "")
                // 能解析出 JSON 说明网络是通的
                if (code != 0) {
                    log.e("checkDevice business error: $msg")
                    return@withFailover CheckResult(
                        needAuth = true,
                        authorized = false,
                        account = "",
                        mac = DeviceUtils.getMacAddress(context),
                        snid = DeviceUtils.getSerial().ifBlank { DeviceUtils.getAndroidId(context) },
                        province = "",
                        isp = "",
                        provinceMode = "auto",
                        dialogTitle = "柠檬TV",
                        dialogTip = "请输入授权码",
                        provinces = null,
                        isps = listOf("联通", "移动", "电信", "广电", "通用"),
                        expireAt = null,
                        isFirst = false,
                        appName = "",
                        errorMsg = msg.ifBlank { "服务器返回错误($code)" },
                    )
                }
                val data = json.optJSONObject("data") ?: return@withFailover null
                val provincesArr = data.optJSONArray("provinces")
                val provinces = if (provincesArr != null) {
                    (0 until provincesArr.length()).map { provincesArr.getString(it) }
                } else null
                val ispsArr = data.optJSONArray("isps")
                val isps = if (ispsArr != null) {
                    (0 until ispsArr.length()).map { ispsArr.getString(it) }
                } else listOf("联通", "移动", "电信", "广电", "通用")
                CheckResult(
                    needAuth = data.optBoolean("need_auth", true),
                    authorized = data.optBoolean("authorized", false),
                    account = data.optString("account", ""),
                    mac = data.optString("mac", ""),
                    snid = data.optString("snid", ""),
                    province = data.optString("province", ""),
                    isp = data.optString("isp", ""),
                    provinceMode = data.optString("province_mode", "auto"),
                    dialogTitle = data.optString("dialog_title", "设备未授权"),
                    dialogTip = data.optString("dialog_tip", "请输入授权码"),
                    provinces = provinces,
                    isps = isps,
                    expireAt = data.optString("expire_at", null),
                    isFirst = data.optBoolean("is_first", false),
                    appName = data.optString("app_name", ""),
                    errorMsg = null,
                )
            }
        }

    suspend fun submitAuthCode(code: String): AuthResult = withContext(Dispatchers.IO) {
        val result = ApiEndpointManager.withFailover { base ->
            val payload = devicePayload().apply { put("code", code.trim()) }
            val json = postJson(base, "device/auth", payload) ?: return@withFailover null
            if (json.optInt("code") != 0) {
                return@withFailover AuthResult(false, json.optString("msg", "授权失败"), null)
            }
            val data = json.optJSONObject("data")
            AuthResult(true, data?.optString("msg") ?: "授权成功", data?.optString("expire_at"))
        }
        result ?: AuthResult(false, "无法连接服务器（请检查 iptv.json 与网络）", null)
    }

    suspend fun fetchConfig(): ConfigResult? = withContext(Dispatchers.IO) {
        ApiEndpointManager.withFailover { base ->
            val json = postJson(base, "device/config", devicePayload()) ?: return@withFailover null
            if (json.optInt("code") != 0) {
                log.e("fetchConfig error: ${json.optString("msg")}")
                return@withFailover null
            }
            val data = json.getJSONObject("data")
            val uaVal = data.optString("player_ua", data.optString("ua", ""))
            ConfigResult(
                authorized = data.optBoolean("authorized", false),
                m3uUrl = data.optString("m3u_url", ""),
                epgUrl = data.optString("epg_url", ""),
                ua = uaVal,
                cacheTime = data.optLong("cache_time", 0),
                sourceName = data.optString("source_name", "默认源"),
                account = data.optString("account", ""),
                expireAt = data.optString("expire_at", null),
                province = data.optString("province", ""),
                isp = data.optString("isp", ""),
                playerType = data.optString("player_type", "exo_hard"),
                loadTimeout = data.optInt("load_timeout", 10),
                channelRev = data.optBoolean("channel_rev", true),
                splashUrl = data.optString("splash_url", ""),
                marqueeEnable = data.optBoolean("marquee_enable", false),
                marqueeText = data.optString("marquee_text", ""),
                marqueeSize = data.optInt("marquee_size", 16),
                marqueeColor = data.optString("marquee_color", "#FFD700"),
                marqueeMode = data.optString("marquee_mode", "always"),
                marqueeIntervalMin = data.optInt("marquee_interval_min", 30),
                marqueeShowSec = data.optInt("marquee_show_sec", 30),
                upgradeVersion = data.optString("upgrade_version", ""),
                upgradeUrl = data.optString("upgrade_url", ""),
                upgradeForce = data.optBoolean("upgrade_force", false),
                upgradeMsg = data.optString("upgrade_msg", ""),
            )
        }
    }

    companion object {
        private val defaultClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
