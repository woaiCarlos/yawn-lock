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

    // 中心 item 检测 — 用 visibleItemsInfo 找离可见中心最近的 item
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.toList() }
            .collect { items ->
                if (items.isEmpty()) return@collect
                if (isProgrammaticScroll.value) return@collect
                if (listState.isScrollInProgress) return@collect
                val layoutInfo = listState.layoutInfo
                val centerY = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                val centerItem = items.minByOrNull { abs((it.offset + it.size / 2) - centerY) }
                if (centerItem != null) {
                    val newValue = range.first + centerItem.index
                    if (newValue != selected && newValue in range) {
                        onSelectedChange(newValue)
                    }
                }
            }
    }

    // state → 滚轮:瞬时跳到目标
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
        // 中央胶囊高亮
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
            // 关键:padding = 2 * itemHeight 让中心 item(index 0)顶在 y = 88(可见区中心 y = 110)
            // centerY = (startOffset + endOffset) / 2 = 0 + 5*44*density / 2 = 110*density
            // item 0 top = 2 * itemHeight * density = 88 * density, center = 88 + 22 = 110
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
