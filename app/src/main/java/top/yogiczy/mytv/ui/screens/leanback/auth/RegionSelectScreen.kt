package top.yogiczy.mytv.ui.screens.leanback.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RegionSelectScreen(
    provinces: List<String>,
    isps: List<String>,
    initialProvince: String = "河北",
    initialIsp: String = "通用",
    title: String = "选择地区与运营商",
    onConfirm: (province: String, isp: String) -> Unit,
) {
    val provList = provinces.ifEmpty {
        listOf(
            "北京","天津","河北","山西","内蒙古","辽宁","吉林","黑龙江",
            "上海","江苏","浙江","安徽","福建","江西","山东","河南",
            "湖北","湖南","广东","广西","海南","重庆","四川","贵州",
            "云南","西藏","陕西","甘肃","青海","宁夏","新疆"
        )
    }
    val ispList = isps.ifEmpty { listOf("联通", "移动", "电信", "广电", "通用") }
    var province by remember {
        mutableStateOf(initialProvince.ifBlank { "河北" }.let { if (it in provList) it else provList.first() })
    }
    var isp by remember {
        mutableStateOf(initialIsp.ifBlank { "通用" }.let { if (it in ispList) it else ispList.first() })
    }
    var openProv by remember { mutableStateOf(false) }
    var openIsp by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF0A0C10)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .padding(24.dp)
                .background(Color(0xFF1A2332), RoundedCornerShape(12.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            Text("省份", color = Color(0xFF9AA3B2), fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            DropdownBox(text = province, expanded = openProv, onToggle = {
                openProv = !openProv; openIsp = false
            })
            if (openProv) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState())
                        .background(Color(0xFF12151E), RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    provList.forEach { p ->
                        Text(
                            p,
                            color = if (p == province) Color(0xFF4A90D9) else Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { province = p; openProv = false }
                                .padding(10.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("运营商", color = Color(0xFF9AA3B2), fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            DropdownBox(text = isp, expanded = openIsp, onToggle = {
                openIsp = !openIsp; openProv = false
            })
            if (openIsp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF12151E), RoundedCornerShape(8.dp))
                        .padding(4.dp)
                ) {
                    ispList.forEach { i ->
                        Text(
                            i,
                            color = if (i == isp) Color(0xFF4A90D9) else Color.White,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isp = i; openIsp = false }
                                .padding(10.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionBtn("确认", primary = true) { onConfirm(province, isp) }
            }
        }
    }
}

@Composable
private fun DropdownBox(text: String, expanded: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF12151E), RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(text, color = Color.White, fontSize = 16.sp)
        Text(if (expanded) "▲" else "▼", color = Color(0xFF9AA3B2), modifier = Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun ActionBtn(text: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(44.dp)
            .widthIn(min = 120.dp)
            .background(if (primary) Color(0xFF3A7BC8) else Color(0xFF2A3544), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}
