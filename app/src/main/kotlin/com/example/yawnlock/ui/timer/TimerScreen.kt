package com.example.yawnlock.ui.timer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
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
import com.example.yawnlock.domain.DurationFormatter
import com.example.yawnlock.domain.TimerState
import com.example.yawnlock.domain.TimerStatus
import com.example.yawnlock.service.CountdownService
import com.example.yawnlock.ui.components.ProgressRing
import com.example.yawnlock.ui.theme.Night900
import com.example.yawnlock.ui.theme.Purple50
import com.example.yawnlock.ui.theme.Purple500
import com.example.yawnlock.ui.theme.Purple700
import com.example.yawnlock.ui.theme.Purple900
import com.example.yawnlock.ui.theme.Rose
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
                .verticalScroll(rememberScrollState())
                .padding(bottom = 140.dp),
        ) {
            HeroCard()
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
            if (state.isActive || state.status is TimerStatus.Finished) {
                StatusCard(state = state, vm = vm)
            }
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
private fun HeroCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Purple900, Purple700, Purple500)))
            .padding(22.dp),
    ) {
        Column {
            Text("准备好休息了吗?",
                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("选个时长,到点自动锁屏",
                color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
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
        600L to ("10" to "分钟"),
        1800L to ("30" to "分钟"),
        3600L to ("1" to "小时"),
        7200L to ("2" to "小时"),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        presets.forEach { (s, labels) ->
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

@Composable
private fun CustomDial(seconds: Long, onChange: (Long) -> Unit) {
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val big = if (seconds < 60) seconds.toString() else (seconds / 60).toString()
            val unit = if (seconds < 60) "秒" else "分钟"
            Row(verticalAlignment = Alignment.Bottom) {
                Text(big, fontSize = 88.sp, fontWeight = FontWeight.Bold, color = Purple900)
                Spacer(Modifier.width(6.dp))
                Text(unit, fontSize = 22.sp, color = Color(0xFF6B6B6B), fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp))
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        val step = when {
                            seconds < 60 -> 5L
                            seconds < 300 -> 30L
                            else -> 60L
                        }
                        onChange((seconds - step).coerceAtLeast(5L))
                    },
                    modifier = Modifier.size(48.dp),
                ) { Text("−", fontSize = 22.sp, color = Purple500, fontWeight = FontWeight.Bold) }
                FilledTonalIconButton(
                    onClick = {
                        val step = when {
                            seconds < 60 -> 5L
                            seconds < 300 -> 30L
                            else -> 60L
                        }
                        onChange((seconds + step).coerceAtMost(7200L))
                    },
                    modifier = Modifier.size(48.dp),
                ) { Text("+", fontSize = 22.sp, color = Purple500, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("5秒", fontSize = 12.sp, color = Color(0xFF6B6B6B))
                Spacer(Modifier.width(14.dp))
                Slider(
                    value = seconds.toFloat(),
                    onValueChange = { onChange(it.toLong()) },
                    valueRange = 5f..7200f,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(14.dp))
                Text("2时", fontSize = 12.sp, color = Color(0xFF6B6B6B))
            }
        }
    }
}

@Composable
private fun StatusCard(state: TimerState, vm: TimerViewModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Purple50, Rose)))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("已经锁屏倒计时…", fontSize = 13.sp, color = Color(0xFF6B6B6B))
            Spacer(Modifier.height(14.dp))
            Box(contentAlignment = Alignment.Center) {
                ProgressRing(progress = state.progress, size = 200.dp)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(DurationFormatter.toMmSs(state.remainingMs),
                        fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Purple900)
                    Text("剩余", fontSize = 12.sp, color = Color(0xFF6B6B6B))
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { vm.pause() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(50),
                ) { Text(if (state.status is TimerStatus.Paused) "继续" else "暂停") }
                OutlinedButton(
                    onClick = { vm.stop() },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                ) { Text("停止") }
            }
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
