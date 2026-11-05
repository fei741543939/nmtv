package top.yogiczy.mytv.ui

import androidx.annotation.IntRange
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import top.yogiczy.mytv.data.repositories.backend.BackendRepository
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.ui.screens.leanback.auth.AuthDialogScreen
import top.yogiczy.mytv.ui.screens.leanback.auth.RegionSelectScreen
import top.yogiczy.mytv.ui.screens.leanback.components.LeanbackPadding
import top.yogiczy.mytv.ui.screens.leanback.main.LeanbackMainScreen
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastScreen
import top.yogiczy.mytv.ui.screens.leanback.toast.LeanbackToastState
import top.yogiczy.mytv.ui.utils.SP
import top.yogiczy.mytv.utils.Logger

private enum class GateState {
    Checking, NeedAuth, NeedRegion, Ready
}

@Composable
fun LeanbackApp(
    modifier: Modifier = Modifier,
    onBackPressed: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val log = remember { Logger.create("LeanbackApp") }
    val backend = remember { BackendRepository(context) }
    val doubleBackPressedExitState = rememberLeanbackDoubleBackPressedExitState()

    var gate by remember { mutableStateOf(GateState.Checking) }
    var account by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    var dialogTitle by remember { mutableStateOf(Constants.APP_TITLE) }
    var dialogTip by remember { mutableStateOf("请输入授权码") }
    var statusText by remember { mutableStateOf("未授权") }
    var provinces by remember { mutableStateOf<List<String>>(emptyList()) }
    var isps by remember { mutableStateOf(listOf("联通", "移动", "电信", "广电", "通用")) }
    var checkMsg by remember { mutableStateOf("正在连接服务器...") }

    fun applyConfigAndReady() {
        scope.launch {
            if (Constants.ENABLE_BACKEND) {
                val config = backend.fetchConfig()
                if (config != null) {
                    if (config.m3uUrl.isNotBlank()) {
                        SP.iptvSourceUrl = config.m3uUrl
                    }
                    if (config.epgUrl.isNotBlank()) {
                        try { SP.epgXmlUrl = config.epgUrl } catch (_: Exception) {}
                    }
                    // 缓存秒数（0=不缓存），后台优先
                    SP.iptvSourceCacheTime = config.cacheTime * 1000L
                    if (config.ua.isNotBlank()) {
                        try { SP.videoPlayerUserAgent = config.ua } catch (_: Exception) {}
                    }
                    log.i("已应用后台配置: ${config.sourceName} ua=${config.ua} cache=${config.cacheTime}")
                }
            }
            gate = GateState.Ready
        }
    }

    fun doCheck(province: String? = null, isp: String? = null) {
        gate = GateState.Checking
        checkMsg = "正在连接服务器..."
        scope.launch {
            if (!Constants.ENABLE_BACKEND) {
                gate = GateState.Ready
                return@launch
            }
            val result = backend.checkDevice(province, isp)
            if (result == null) {
                checkMsg = "无法连接授权服务器（请检查 iptv.json 是否可访问）"
                gate = GateState.NeedAuth
                statusText = "网络异常"
                return@launch
            }
            account = result.account
            mac = result.mac
            deviceId = result.snid.ifBlank { result.account }
            dialogTitle = result.appName.ifBlank {
                result.dialogTitle.ifBlank { Constants.APP_TITLE }
            }
            if (result.appName.isNotBlank()) {
                dialogTitle = result.appName
            }
            dialogTip = result.dialogTip.ifBlank { "请输入授权码" }
            provinces = result.provinces ?: emptyList()
            isps = result.isps
            statusText = when {
                !result.errorMsg.isNullOrBlank() -> result.errorMsg!!
                result.authorized -> "已授权"
                else -> "未授权 / 免费用户"
            }

            when {
                result.needAuth || !result.errorMsg.isNullOrBlank() -> gate = GateState.NeedAuth
                result.provinceMode == "manual" && result.province.isBlank() -> gate = GateState.NeedRegion
                else -> applyConfigAndReady()
            }
        }
    }

    LaunchedEffect(Unit) { doCheck() }

    LeanbackToastScreen()

    when (gate) {
        GateState.Checking -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(checkMsg, color = Color(0xFF9AA3B2))
            }
        }
        GateState.NeedAuth -> {
            AuthDialogScreen(
                title = dialogTitle.ifBlank { Constants.APP_TITLE },
                tip = dialogTip,
                account = account,
                deviceId = deviceId,
                mac = mac,
                statusText = statusText,
                onAuthSuccess = {
                    scope.launch {
                        val result = backend.checkDevice()
                        if (result != null && result.provinceMode == "manual" && result.province.isBlank()) {
                            provinces = result.provinces ?: emptyList()
                            isps = result.isps
                            gate = GateState.NeedRegion
                        } else {
                            applyConfigAndReady()
                        }
                    }
                },
            )
        }
        GateState.NeedRegion -> {
            RegionSelectScreen(
                provinces = provinces.ifEmpty {
                    listOf(
                        "北京", "天津", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江",
                        "上海", "江苏", "浙江", "安徽", "福建", "江西", "山东", "河南",
                        "湖北", "湖南", "广东", "广西", "海南", "重庆", "四川", "贵州",
                        "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆"
                    )
                },
                isps = isps,
                onConfirm = { p, i -> doCheck(p, i) },
            )
        }
        GateState.Ready -> {
            LeanbackMainScreen(
                modifier = modifier,
                onBackPressed = {
                    if (doubleBackPressedExitState.allowExit) onBackPressed()
                    else {
                        doubleBackPressedExitState.backPress()
                        LeanbackToastState.I.showToast("再按一次退出")
                    }
                },
            )
        }
    }
}

/**
 * 退出应用二次确认
 */
class LeanbackDoubleBackPressedExitState internal constructor(
    @IntRange(from = 0)
    private val resetSeconds: Int,
) {
    private var _allowExit by mutableStateOf(false)
    val allowExit get() = _allowExit

    fun backPress() {
        _allowExit = true
        channel.trySend(resetSeconds)
    }

    private val channel = Channel<Int>(Channel.CONFLATED)

    @OptIn(FlowPreview::class)
    suspend fun observe() {
        channel.consumeAsFlow()
            .debounce { it.toLong() * 1000 }
            .collect { _allowExit = false }
    }
}

/**
 * 退出应用二次确认状态
 */
@Composable
fun rememberLeanbackDoubleBackPressedExitState(@IntRange(from = 0) resetSeconds: Int = 2) =
    remember { LeanbackDoubleBackPressedExitState(resetSeconds = resetSeconds) }
        .also { LaunchedEffect(it) { it.observe() } }

val LeanbackParentPadding = PaddingValues(vertical = 12.dp, horizontal = 24.dp)

@Composable
fun rememberLeanbackChildPadding(direction: LayoutDirection = LocalLayoutDirection.current) =
    remember {
        LeanbackPadding(
            start = LeanbackParentPadding.calculateStartPadding(direction) + 8.dp,
            top = LeanbackParentPadding.calculateTopPadding(),
            end = LeanbackParentPadding.calculateEndPadding(direction) + 8.dp,
            bottom = LeanbackParentPadding.calculateBottomPadding()
        )
    }
