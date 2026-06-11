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

private val ITEM_HEIGHT = 44.dp
private val VISIBLE_ITEMS = 5
private val FADE_ROWS = 1

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
    val isProgrammaticScroll = remember { mutableStateOf(false) }

    // 滚轮 → state: 当 firstVisibleItemIndex 变化且 wheel 停止滚动时,更新 state
    // 关键:isScrollInProgress=true (用户正在滑) 时**不更新 state** —— 否则 state→wheel
    // 反馈循环会持续打断用户手滑,造成疯狂闪烁。让手滑自然完成,停下后再 sync。
    LaunchedEffect(listState.firstVisibleItemIndex) {
        if (isProgrammaticScroll.value) return@LaunchedEffect
        if (listState.isScrollInProgress) return@LaunchedEffect
        val newValue = range.first + listState.firstVisibleItemIndex
        if (newValue != selected && newValue in range) {
            onSelectedChange(newValue)
        }
    }

    // state → 滚轮: 外部 selected 变化时,scrollToItem 把目标放在最顶端
    // 这样 firstVisibleItemIndex = 选中值,state 跟高亮自动同步
    LaunchedEffect(selected) {
        val target = (selected - range.first).coerceAtLeast(0)
        if (target != listState.firstVisibleItemIndex) {
            isProgrammaticScroll.value = true
            try {
                listState.scrollToItem(target, 0)
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
        // 중앙胶囊高亮
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

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // 关键:padding = 2 * itemHeight 让 firstVisibleItemIndex(index 0)顶在 y = 88,
            // 可见区中心 y = 110。index 0 顶在 y=88、视觉中心 y=110 = 中心
            // 这样 firstVisibleItemIndex 直接对应"中心选中的值",无 race
            contentPadding = PaddingValues(vertical = ITEM_HEIGHT * 2),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
        ) {
            items(count) { i ->
                val value = range.first + i
                val isSelected = value == selected
                val fontSize = if (isSelected) 26.sp else 18.sp
                val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                val color = if (isSelected) Purple900 else Purple900.copy(alpha = 0.35f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ITEM_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = value.toString().padStart(2, '0'),
                        fontSize = fontSize,
                        fontWeight = fontWeight,
                        color = color,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // 顶部渐变遮罩
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

        // 底部渐变遮罩
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
