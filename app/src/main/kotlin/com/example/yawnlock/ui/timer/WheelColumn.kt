package com.example.yawnlock.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yawnlock.ui.theme.Purple500
import com.example.yawnlock.ui.theme.Purple900
import kotlin.math.abs

private val ITEM_HEIGHT = 48.dp
private val VISIBLE_ITEMS = 5  // 5 行,中间行(item 0)视觉中心在 y=120 可见区中心
private val FADE_ROWS = 1       // 上下各 1 行渐变,选中 + 1 个邻居可见,保留 wheel 感

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun WheelColumn(
    range: IntRange,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = range.last - range.first + 1
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = (selected - range.first).coerceAtLeast(0)
    )
    // 程序性滚动标记:animateScrollToItem 期间为 true,scroll listener 跳过 onSelectedChange
    val isProgrammaticScroll = remember { mutableStateOf(false) }

    // 滚轮 → state: 吸附完成后回调(程序性滚动期间跳过,避免动画中间值回传)
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (isProgrammaticScroll.value) return@LaunchedEffect
        if (abs(listState.firstVisibleItemScrollOffset) < 8) {
            val newValue = range.first + listState.firstVisibleItemIndex
            if (newValue != selected && newValue in range) {
                onSelectedChange(newValue)
            }
        }
    }

    // state → 滚轮: 外部 selected 变化时滚过去(标记为程序性滚动)
    LaunchedEffect(selected) {
        val target = (selected - range.first).coerceAtLeast(0)
        if (target != listState.firstVisibleItemIndex) {
            isProgrammaticScroll.value = true
            try {
                listState.animateScrollToItem(target)
            } finally {
                isProgrammaticScroll.value = false
            }
        }
    }

    Box(
        modifier = modifier
            .height(ITEM_HEIGHT * VISIBLE_ITEMS),
        contentAlignment = Alignment.Center,
    ) {
        // 1. 中央胶囊高亮(底层):带横向渐变
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ITEM_HEIGHT)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Purple500.copy(alpha = 0.04f),
                            Purple500.copy(alpha = 0.14f),
                            Purple500.copy(alpha = 0.04f),
                        )
                    )
                )
        )

        // 2. 列表(中间层)
        // contentPadding(vertical = 96.dp) 让 item 0 顶在 y=96、视觉中心在 y=120,
        // 正好在可见区 240dp 中心,跟「:」分隔符垂直对齐
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = ITEM_HEIGHT * 2),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
        ) {
            items(count) { i ->
                val value = range.first + i
                val isSelected = value == selected
                val fontSize = if (isSelected) 30.sp else 20.sp
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ITEM_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    // 关键:lineHeight = fontSize 显式相等,消除 Compose 默认 lineHeight padding
                    // (默认 1.2-1.5x),让数字视觉中心跟 Box 几何中心精确对齐
                    // 再加 Modifier.offset(y = -2.dp) 补偿字体 baseline 偏移(Compose 字体
                    // ascent 约 75% / descent 约 25%,字形视觉中心比 bounding box 中心低 ~2dp)
                    Text(
                        text = value.toString().padStart(2, '0'),
                        fontSize = fontSize,
                        lineHeight = fontSize,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Purple900 else Purple900.copy(alpha = 0.3f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = -2.dp),
                    )
                }
            }
        }

        // 3. 顶部渐变遮罩(顶层):线性 white → transparent,1 行高
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ITEM_HEIGHT * FADE_ROWS)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White,
                        1f to Color.White.copy(alpha = 0f),
                    )
                )
        )

        // 4. 底部渐变遮罩(顶层):线性 transparent → white,1 行高
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ITEM_HEIGHT * FADE_ROWS)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0f),
                        1f to Color.White,
                    )
                )
        )
    }
}
