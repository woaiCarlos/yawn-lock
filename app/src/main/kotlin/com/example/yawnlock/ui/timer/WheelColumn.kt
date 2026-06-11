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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yawnlock.ui.theme.Purple500
import com.example.yawnlock.ui.theme.Purple900
import kotlin.math.abs

private val ITEM_HEIGHT = 48.dp
private val VISIBLE_ITEMS = 5  // 5 行,中间那行正好在 y=120 中心
private val FADE_ROWS = 2       // 上下各 2 行渐变隐藏

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
    // 避免动画中间值污染 state、再触发 selected LaunchedEffect 把原动画取消
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
        // 1. 中央胶囊高亮(底层):带横向渐变,中间稍亮两边淡
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
        // contentPadding(vertical = 96.dp = 2 * ITEM_HEIGHT) 让 item 0 顶在 y=96,
        // 视觉中心在 y=120 (可见区 240dp 的中心),跟「:」分隔符垂直对齐
        // 同时 firstVisibleItemIndex 直接对应「中心选中的值」(无需偏移补偿)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = ITEM_HEIGHT * 2),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
        ) {
            items(count) { i ->
                val value = range.first + i
                val isSelected = value == selected
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ITEM_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = value.toString().padStart(2, '0'),
                        fontSize = if (isSelected) 32.sp else 20.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Purple900 else Purple900.copy(alpha = 0.35f),
                    )
                }
            }
        }

        // 3. 顶部渐变遮罩(顶层):白色到透明,把顶部 2 行隐藏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ITEM_HEIGHT * FADE_ROWS)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White,
                        0.5f to Color.White,
                        1f to Color.White.copy(alpha = 0f),
                    )
                )
        )

        // 4. 底部渐变遮罩(顶层):透明到白色,把底部 2 行隐藏
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ITEM_HEIGHT * FADE_ROWS)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0f),
                        0.5f to Color.White,
                        1f to Color.White,
                    )
                )
        )
    }
}
