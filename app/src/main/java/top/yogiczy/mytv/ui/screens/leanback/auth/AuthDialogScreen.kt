package top.yogiczy.mytv.ui.screens.leanback.auth

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yogiczy.mytv.data.repositories.backend.BackendRepository
import top.yogiczy.mytv.data.utils.Constants
import top.yogiczy.mytv.utils.DeviceUtils

@Composable
fun AuthDialogScreen(
    title: String = Constants.APP_TITLE,
    tip: String = "请输入授权码",
    account: String = "",
    deviceId: String = "",
    mac: String = "",
    statusText: String = "未授权",
    onAuthSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val backend = remember { BackendRepository(context) }
    val config = LocalConfiguration.current
    val isNarrow = config.screenWidthDp < 600

    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val rootFocus = remember { FocusRequester() }

    val displayMac = mac.ifBlank { DeviceUtils.getMacAddress(context) }.ifBlank { "-" }
    val displayId = deviceId.ifBlank {
        DeviceUtils.getAndroidId(context).ifBlank { DeviceUtils.getSerial() }
    }.ifBlank { "-" }

    LaunchedEffect(Unit) {
        try { rootFocus.requestFocus() } catch (_: Exception) {}
    }

    fun submit() {
        if (code.isBlank() || loading) return
        loading = true
        errorMsg = null
        scope.launch {
            val result = backend.submitAuthCode(code)
            loading = false
            if (result.success) onAuthSuccess() else errorMsg = result.msg
        }
    }

    fun appendKey(k: String) {
        when (k) {
            "删除" -> if (code.isNotEmpty()) code = code.dropLast(1)
            "确认" -> submit()
            else -> if (code.length < 16) code += k
        }
    }

    @Composable
    fun DeviceInfoCard(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .background(Color(0xFF1A2332), RoundedCornerShape(12.dp))
                .padding(16.dp),
        ) {
            Text(
                "设备信息",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text("设备ID: $displayId", color = Color(0xFFD0D6E0), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("MAC地址: $displayMac", color = Color(0xFFD0D6E0), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Text("账号: ${account.ifBlank { "-" }}", color = Color(0xFFD0D6E0), fontSize = 13.sp)
            Spacer(Modifier.height(6.dp))
            Row {
                Text("状态: ", color = Color(0xFF9AA3B2), fontSize = 13.sp)
                Text(statusText, color = Color(0xFFFF5252), fontSize = 13.sp)
            }
        }
    }

    @Composable
    fun KeyPad(modifier: Modifier = Modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier,
        ) {
            Text(tip, color = Color(0xFFB0B8C4), fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A2332), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF3A4556), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Text(
                    text = if (code.isEmpty()) " " else code,
                    color = Color.White,
                    fontSize = 22.sp,
                    letterSpacing = 3.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(12.dp))
            listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("删除", "0", "确认"),
            ).forEach { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp),
                ) {
                    row.forEach { key ->
                        TouchKey(
                            text = key,
                            isPrimary = key == "确认",
                            modifier = Modifier.size(if (isNarrow) 64.dp else 72.dp, 46.dp),
                            onClick = { appendKey(key) },
                        )
                    }
                }
            }
            if (errorMsg != null) {
                Spacer(Modifier.height(8.dp))
                Text(errorMsg!!, color = Color(0xFFFF5252), fontSize = 12.sp)
            }
            if (loading) {
                Spacer(Modifier.height(6.dp))
                Text("正在验证...", color = Color(0xFF9AA3B2), fontSize = 12.sp)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10))
            .focusRequester(rootFocus)
            .focusable()
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (val kc = event.nativeKeyEvent.keyCode) {
                    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                        appendKey((kc - KeyEvent.KEYCODE_0).toString()); true
                    }
                    KeyEvent.KEYCODE_DEL -> { appendKey("删除"); true }
                    KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> { submit(); true }
                    KeyEvent.KEYCODE_BACK -> true
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Text(
                text = title.ifBlank { Constants.APP_TITLE },
                color = Color.White,
                fontSize = if (isNarrow) 28.sp else 32.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(20.dp))

            if (isNarrow) {
                // 手机竖屏：上信息、下键盘，居中协调
                DeviceInfoCard(Modifier.fillMaxWidth())
                Spacer(Modifier.height(20.dp))
                KeyPad(Modifier.widthIn(max = 280.dp).fillMaxWidth())
            } else {
                // 横屏/TV：左右分栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Top,
                ) {
                    DeviceInfoCard(Modifier.widthIn(max = 300.dp).weight(1f, fill = false))
                    Spacer(Modifier.width(24.dp))
                    KeyPad(Modifier.widthIn(max = 280.dp))
                }
            }
        }
    }
}

@Composable
private fun TouchKey(
    text: String,
    isPrimary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val bg = when {
        isPrimary && focused -> Color(0xFF4A90D9)
        isPrimary -> Color(0xFF3A7BC8)
        focused -> Color(0xFF3A7BC8)
        else -> Color(0xFF2A3544)
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .focusable(interactionSource = interaction),
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = if (text.length > 1) 13.sp else 18.sp,
            fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
