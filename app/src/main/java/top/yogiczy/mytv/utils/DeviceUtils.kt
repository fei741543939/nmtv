package top.yogiczy.mytv.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import java.net.NetworkInterface
import java.util.Collections

/**
 * 设备信息工具（MAC + SNID）
 */
object DeviceUtils {
    private val log = Logger.create("DeviceUtils")

    /**
     * 获取 MAC 地址（尽力获取，Android 6+ 限制较多）
     */
    @SuppressLint("HardwareIds")
    fun getMacAddress(context: Context): String {
        // 1. 尝试从 NetworkInterface 获取
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in interfaces) {
                if (!nif.name.equals("wlan0", ignoreCase = true) &&
                    !nif.name.equals("eth0", ignoreCase = true)
                ) continue
                val macBytes = nif.hardwareAddress ?: continue
                val sb = StringBuilder()
                for (b in macBytes) {
                    sb.append(String.format("%02X:", b))
                }
                if (sb.isNotEmpty()) {
                    sb.deleteCharAt(sb.length - 1)
                    val mac = sb.toString()
                    if (mac != "02:00:00:00:00:00" && mac != "00:00:00:00:00:00") {
                        return mac
                    }
                }
            }
        } catch (e: Exception) {
            log.w("getMac from NetworkInterface failed: ${e.message}")
        }

        // 2. 尝试 wifi 管理（旧系统）
        try {
            @Suppress("DEPRECATION")
            val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            @Suppress("DEPRECATION")
            val info = wifi?.connectionInfo
            @Suppress("DEPRECATION")
            val mac = info?.macAddress
            if (!mac.isNullOrBlank() && mac != "02:00:00:00:00:00") {
                return mac.uppercase()
            }
        } catch (e: Exception) {
            log.w("getMac from WifiManager failed: ${e.message}")
        }

        return ""
    }

    /**
     * 获取设备序列号 / SNID
     */
    @SuppressLint("HardwareIds")
    fun getSerial(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    Build.getSerial()
                } catch (e: SecurityException) {
                    Build.SERIAL ?: ""
                }
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL ?: ""
            }.takeIf { it.isNotBlank() && it != Build.UNKNOWN } ?: ""
        } catch (e: Exception) {
            log.w("getSerial failed: ${e.message}")
            ""
        }
    }

    /**
     * Android ID（作为补充标识）
     */
    @SuppressLint("HardwareIds")
    fun getAndroidId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getModel(): String = Build.MODEL ?: ""
    fun getBrand(): String = Build.BRAND ?: ""
    fun getOsVersion(): String = "Android ${Build.VERSION.RELEASE}"
}
