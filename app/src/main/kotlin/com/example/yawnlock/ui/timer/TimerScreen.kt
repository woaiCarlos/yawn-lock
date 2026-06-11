package com.example.yawnlock.ui.timer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.yawnlock.service.CountdownService
import com.example.yawnlock.ui.theme.Night900
import com.example.yawnlock.ui.theme.Purple500
import com.example.yawnlock.ui.theme.Purple700
import com.example.yawnlock.ui.theme.Purple900
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    onNavigatePermissions: () -> Unit,
    vm: TimerViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    var seconds by remember { mutableStateOf(300L) }

    // 同步 seconds 跟 state.durationMs(state 变化时)
    LaunchedEffect(state.durationMs) {
        if (state.durationMs > 0 && !state.isActive) {
            seconds = state.durationMs / 1000L
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(
        Brush.verticalGradient(listOf(Color(0xFFFAFAFA), Color(0xFFF3EDFF)))
    )) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 20.dp, bottom = 108.dp),
        ) {
            HeroCard(onPermissionsClick = onNavigatePermissions)
            SectionHeader("快速预设", "点击切换")
            PresetChips(
                selected = seconds,
                onSelect = { s -> seconds = s; vm.setSeconds(s) },
            )
            SectionHeader("自定义", "± 键微调")
            CustomDial(
                seconds = seconds,
                onChange = { s -> seconds = s; vm.setSeconds(s) },
            )
            // 倒计时进行中不在 Timer 屏幕显示任何 UI(仅悬浮窗显示)
        }
        StartCta(
            enabled = !state.isActive && state.durationMs > 0L,
            onStart = {
                val perms = vm.checkPermissions()
                if (!perms.canStartCountdown) {
                    onNavigatePermissions()
                    return@StartCta
                }
                vm.start()
                val intent = Intent(context, CountdownService::class.java)
                    .setAction(CountdownService.ACTION_START)
                context.startForegroundService(intent)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun HeroCard(onPermissionsClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Purple900, Purple700, Purple500)))
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("准备好休息了吗?",
                    color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("选个时长,到点自动锁屏",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            }
            IconButton(onClick = onPermissionsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "权限",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, hint: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Purple900)
        Text(hint, fontSize = 12.sp, color = Color(0xFF6B6B6B))
    }
}

@Composable
private fun PresetChips(selected: Long, onSelect: (Long) -> Unit) {
    val presets = listOf(
        300L to ("5" to "分钟"),
        600L to ("10" to "分钟"),
        1200L to ("20" to "分钟"),
        1800L to ("30" to "分钟"),
        3600L to ("1" to "小时"),
        7200L to ("2" to "小时"),
    )
    // 2 行 × 3 列,每个 chip 约 100dp 宽,比单行 6 个(44dp)更舒展
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        presets.chunked(3).forEach { rowPresets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rowPresets.forEach { (s, labels) ->
                    val (label, unit) = labels
                    val isSelected = selected == s
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) Purple500 else Color.White)
                            .clickable { onSelect(s) }
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(label,
                            color = if (isSelected) Color.White else Purple900,
                            fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(unit,
                            color = if (isSelected) Color.White.copy(alpha = 0.85f) else Purple900.copy(alpha = 0.7f),
                            fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomDial(seconds: Long, onChange: (Long) -> Unit) {
    val hours = (seconds / 3600L).toInt()
    val minutes = ((seconds % 3600L) / 60L).toInt()
    val secs = (seconds % 60L).toInt()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WheelColumn(
                    range = 0..2,
                    selected = hours,
                    onSelectedChange = { h ->
                        val newSec = h * 3600L + minutes * 60L + secs
                        onChange(newSec.coerceIn(5L, 7200L))
                    },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    ":", fontSize = 28.sp, color = Purple900, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                WheelColumn(
                    range = 0..59,
                    selected = minutes,
                    onSelectedChange = { m ->
                        val newSec = hours * 3600L + m * 60L + secs
                        onChange(newSec.coerceIn(5L, 7200L))
                    },
                    modifier = Modifier.weight(1f),
                )
                Text(
                    ":", fontSize = 28.sp, color = Purple900, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                WheelColumn(
                    range = 0..59,
                    selected = secs,
                    onSelectedChange = { s ->
                        val newSec = hours * 3600L + minutes * 60L + s
                        onChange(newSec.coerceIn(5L, 7200L))
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "上下滚动调整 · 最多 2 小时",
                fontSize = 11.sp,
                color = Color(0xFF6B6B6B),
            )
        }
    }
}

@Composable
private fun BoxScope.StartCta(enabled: Boolean, onStart: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFFFAFAFA))))
            .padding(20.dp),
    ) {
        Button(
            onClick = onStart,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Night900,
                contentColor = Color.White,
                disabledContainerColor = Night900.copy(alpha = 0.4f),
            ),
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("开始计时", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
