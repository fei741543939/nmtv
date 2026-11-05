package top.yogiczy.mytv.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import top.yogiczy.mytv.activities.LeanbackActivity
import top.yogiczy.mytv.data.entities.Iptv
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState

/**
 * 讯飞语点 + 夏杰语音 + 通用蓝牙语音遥控
 * - App 在前台：直接换台
 * - App 未启动/在后台：拉起 App 并携带频道关键词，进入后自动换台
 */
object VoiceCommandHandler {
    private val log = Logger.create("Voice")

    const val EXTRA_VOICE_CHANNEL = "voice_channel_keyword"
    const val ACTION_VOICE_SWITCH = "top.yogiczy.mytv.ACTION_VOICE_SWITCH"

    @Volatile var onSwitchTo: ((Iptv) -> Unit)? = null
    @Volatile var onNext: (() -> Unit)? = null
    @Volatile var onPrev: (() -> Unit)? = null
    @Volatile var iptvListProvider: (() -> List<Iptv>)? = null

    /** 待处理的语音换台关键词（App 冷启动时使用） */
    @Volatile var pendingKeyword: String? = null

    private var registered = false
    private var appContext: Context? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val text = extractText(intent) ?: return
            log.i("语音指令: $text (action=${intent.action})")
            handle(context.applicationContext, text)
        }
    }

    private val ACTIONS = arrayOf(
        "com.iflytek.xiri.action.VOICE",
        "com.iflytek.xiri2.app.action",
        "com.iflytek.xiri.action.EXECUTE",
        "com.iflytek.xiri.openview",
        "android.intent.action.VIEW_XIRI",
        "com.xiajie.voice.ACTION_RESULT",
        "com.xiajie.smart.VOICE",
        "com.xj.voice.RESULT",
        "android.intent.action.VOICE_COMMAND_RESULT",
        "com.android.tv.action.VOICE_SEARCH",
        "com.android.tv.action.SPEECH_RESULT",
        "tv.remote.voice.RESULT",
        "com.voice.assistant.ACTION_RESULT",
        "com.swan.voice.RESULT",
        "com.aml.voice.command",
        "com.xgimi.voice.ACTION",
        "com.dangbei.voice",
        "com.coocaa.voice.RESULT",
    )

    private val EXTRA_KEYS = arrayOf(
        "text", "query", "result", "voice_result", "voiceResult",
        "asr_result", "speech", "content", "keyword", "command",
        "_command", "msg", "data", "recognizeresult",
    )

    /** 口语别名 → 搜索关键词 */
    private val ALIASES = mapOf(
        "中央一台" to "CCTV1", "中央一" to "CCTV1", "央视一" to "CCTV1", "央视一台" to "CCTV1",
        "中央二台" to "CCTV2", "中央二" to "CCTV2", "央视二" to "CCTV2",
        "中央三台" to "CCTV3", "中央三" to "CCTV3", "央视三" to "CCTV3",
        "中央四台" to "CCTV4", "中央四" to "CCTV4", "央视四" to "CCTV4",
        "中央五台" to "CCTV5", "中央五" to "CCTV5", "央视五" to "CCTV5", "体育频道" to "CCTV5",
        "中央五加" to "CCTV5+", "中央5+" to "CCTV5+", "体育赛事" to "CCTV5+",
        "中央六台" to "CCTV6", "中央六" to "CCTV6", "央视六" to "CCTV6", "电影频道" to "CCTV6",
        "中央七台" to "CCTV7", "中央七" to "CCTV7",
        "中央八台" to "CCTV8", "中央八" to "CCTV8",
        "中央九台" to "CCTV9", "中央九" to "CCTV9",
        "中央十台" to "CCTV10", "中央十" to "CCTV10",
        "中央十一" to "CCTV11", "中央十二" to "CCTV12", "中央十三" to "CCTV13",
        "中央十四" to "CCTV14", "中央十五" to "CCTV15", "中央十六" to "CCTV16", "中央十七" to "CCTV17",
        "一套" to "CCTV1", "综合频道" to "CCTV1",
    )

    fun register(context: Context) {
        if (registered) return
        appContext = context.applicationContext
        val filter = IntentFilter()
        ACTIONS.forEach { filter.addAction(it) }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.applicationContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.applicationContext.registerReceiver(receiver, filter)
            }
            registered = true
            log.i("语音广播已注册")
        } catch (e: Exception) {
            log.e("注册语音广播失败: ${e.message}")
        }
    }

    fun unregister(context: Context) {
        if (!registered) return
        try { context.applicationContext.unregisterReceiver(receiver) } catch (_: Exception) {}
        registered = false
    }

    fun handle(context: Context, raw: String) {
        var text = raw.trim()
            .replace(Regex("[。，、！？\\s]+"), "")
        if (text.isBlank()) return

        val lower = text.lowercase()

        if (lower.contains("下一") || lower.contains("下个") || lower == "下一个" || lower == "下台") {
            if (isBound()) { onNext?.invoke(); toast("下一个频道") }
            else launchApp(context, null)
            return
        }
        if (lower.contains("上一") || lower.contains("上个") || lower == "上一个" || lower == "上台") {
            if (isBound()) { onPrev?.invoke(); toast("上一个频道") }
            else launchApp(context, null)
            return
        }

        // 去掉口语前缀
        var keyword = text
            .replace(Regex("^(打开|收看|切换到|切换|看|播放|我要看|帮我打开|我想看)"), "")
            .replace(Regex("(频道|台)$"), "")
            .trim()
        if (keyword.isBlank()) keyword = text

        // 别名映射
        val mapped = ALIASES.entries.firstOrNull { keyword.contains(it.key) || keyword == it.key }
        if (mapped != null) keyword = mapped.value

        // CCTV 中文数字
        keyword = expandCctvChinese(keyword)

        if (isBound()) {
            switchByKeyword(keyword)
        } else {
            // 拉起 App 并带上关键词
            pendingKeyword = keyword
            launchApp(context, keyword)
        }
    }

    /** 主界面就绪后调用，处理 pending 或 Intent 带来的关键词 */
    fun consumePendingKeyword() {
        val kw = pendingKeyword ?: return
        pendingKeyword = null
        // 稍等列表加载：由 MainContent 在 list 非空时再调 trySwitchKeyword
        trySwitchKeyword(kw)
    }

    fun trySwitchKeyword(keyword: String): Boolean {
        val list = iptvListProvider?.invoke() ?: emptyList()
        if (list.isEmpty()) {
            pendingKeyword = keyword
            return false
        }
        val found = matchChannel(keyword, list)
        return if (found != null) {
            onSwitchTo?.invoke(found)
            toast("切换到 ${found.name}")
            true
        } else {
            toast("未找到频道: $keyword")
            false
        }
    }

    private fun switchByKeyword(keyword: String) {
        if (!trySwitchKeyword(keyword) && iptvListProvider?.invoke().isNullOrEmpty()) {
            pendingKeyword = keyword
        }
    }

    private fun isBound(): Boolean = onSwitchTo != null && iptvListProvider != null

    private fun launchApp(context: Context, keyword: String?) {
        try {
            val intent = Intent(context, LeanbackActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                if (!keyword.isNullOrBlank()) {
                    putExtra(EXTRA_VOICE_CHANNEL, keyword)
                }
                action = ACTION_VOICE_SWITCH
            }
            context.startActivity(intent)
            log.i("已拉起 App, keyword=$keyword")
        } catch (e: Exception) {
            log.e("拉起 App 失败: ${e.message}")
        }
    }

    private fun expandCctvChinese(keyword: String): String {
        val map = mapOf(
            "一" to "1", "二" to "2", "三" to "3", "四" to "4", "五" to "5",
            "六" to "6", "七" to "7", "八" to "8", "九" to "9", "十" to "10",
            "十一" to "11", "十二" to "12", "十三" to "13", "十四" to "14",
            "十五" to "15", "十六" to "16", "十七" to "17",
        )
        var k = keyword
        // 中央一 / 央视五 → CCTV1
        val m = Regex("^(?:中央|央视|cctv)\\s*([一二三四五六七八九十]+)(\\+)?$", RegexOption.IGNORE_CASE).find(k)
        if (m != null) {
            val num = map[m.groupValues[1]] ?: m.groupValues[1]
            val plus = if (m.groupValues[2] == "+") "+" else ""
            return "CCTV$num$plus"
        }
        return k
    }

    private fun matchChannel(keyword: String, list: List<Iptv>): Iptv? {
        val kw = normalize(keyword)
        list.firstOrNull { normalize(it.name) == kw }?.let { return it }
        list.firstOrNull {
            val n = normalize(it.name)
            n.contains(kw) || kw.contains(n)
        }?.let { return it }
        val cctvNum = Regex("(?:cctv|中央|央视)\\s*(\\d+\\+?)", RegexOption.IGNORE_CASE).find(keyword)
        if (cctvNum != null) {
            val num = cctvNum.groupValues[1].lowercase()
            list.firstOrNull {
                normalize(it.name).replace("cctv", "cctv").contains("cctv$num") ||
                    normalize(it.name).contains("cctv$num")
            }?.let { return it }
            list.firstOrNull { normalize(it.name).contains(num) && normalize(it.name).contains("cctv") }?.let { return it }
        }
        list.firstOrNull {
            val n = normalize(it.name)
            n.contains(kw) && n.contains("卫视")
        }?.let { return it }
        return null
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[\\s\\-·]+"), "")

    private fun extractText(intent: Intent): String? {
        for (key in EXTRA_KEYS) {
            val v = intent.getStringExtra(key)
            if (!v.isNullOrBlank()) return v.trim()
        }
        intent.extras?.keySet()?.forEach { k ->
            val v = intent.extras?.get(k)
            if (v is String && v.length in 1..40 && !k.contains("package", true)) {
                return v.trim()
            }
        }
        return null
    }

    private fun toast(msg: String) {
        try { LeanbackToastState.I.showToast(msg) } catch (_: Exception) {}
    }

    fun bind(
        listProvider: () -> List<Iptv>,
        switchTo: (Iptv) -> Unit,
        next: () -> Unit,
        prev: () -> Unit,
    ) {
        iptvListProvider = listProvider
        onSwitchTo = switchTo
        onNext = next
        onPrev = prev
        // 列表绑定后尝试消费 pending
        pendingKeyword?.let { kw ->
            if (listProvider().isNotEmpty()) {
                pendingKeyword = null
                trySwitchKeyword(kw)
            }
        }
    }

    fun unbind() {
        iptvListProvider = null
        onSwitchTo = null
        onNext = null
        onPrev = null
    }
}
