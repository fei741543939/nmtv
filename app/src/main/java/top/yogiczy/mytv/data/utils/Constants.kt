package top.yogiczy.mytv.data.utils

/**
 * 常量
 */
object Constants {
    const val APP_TITLE = "柠檬TV"
    const val APP_REPO = "https://github.com/jia070310/lemonTV"

    /**
     * 动态域名 JSON（每次打开 App 拉一次）
     * 文件内容示例:
     * {"api":["http://192.168.8.88:44944/api/index.php"]}
     */
    const val BOOTSTRAP_JSON_URL = "http://192.168.8.88:44944/iptv.json"

    /**
     * 直连后台 API（JSON 失败时的兜底，必填可用地址）
     */
    const val BACKEND_API_URL = ""  // 纯动态域名：只从 BOOTSTRAP_JSON_URL 读取，留空即可

    const val ENABLE_BACKEND = true

    const val IPTV_SOURCE_URL =
        "https://gh-proxy.org/https://github.com/jia070310/lemonTV/blob/main/iptv-fe.m3u"

    val IPTV_SOURCE_DEFAULT_LIST = listOf(
        IPTV_SOURCE_URL,
        "https://gh-proxy.org/github.com/ioptu/migu_video/raw/refs/heads/main/migu.m3u",
    )

    const val IPTV_SOURCE_CACHE_TIME = 0L
    const val EPG_XML_URL = "http://epg.51zmt.top:8000/e1.xml.gz"
    const val EPG_XML_URL_BACKUP = "https://epg.zsdc.eu.org/t.xml.gz"
    const val EPG_REFRESH_TIME_THRESHOLD = 2
    const val GIT_RELEASE_LATEST_URL =
        "https://api.github.com/repos/jia070310/lemonTV/releases/latest"
    const val GITHUB_PROXY = "https://hk.gh-proxy.org/"
    const val HTTP_RETRY_COUNT = 10L
    const val HTTP_RETRY_INTERVAL = 3000L
    const val VIDEO_PLAYER_USER_AGENT = "ExoPlayer"
    const val VIDEO_PLAYER_USER_AGENT_APTV =
        "AptvPlayer/1.4.25 (iPhone; CPU iPhone OS 26.3.1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148"
    const val LOG_HISTORY_MAX_SIZE = 50
    const val VIDEO_PLAYER_LOAD_TIMEOUT = 1000L * 10
    const val UI_SCREEN_AUTO_CLOSE_DELAY = 1000L * 15
    const val UI_TIME_SHOW_RANGE = 1000L * 30
    const val UI_TEMP_PANEL_SCREEN_SHOW_DURATION = 1500L
}
