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

    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (isProgrammaticScroll.value) return@LaunchedEffect
        if (abs(listState.firstVisibleItemScrollOffset) < 8) {
            val newValue = range.first + listState.firstVisibleItemIndex
            if (newValue != selected && newValue in range) {
                onSelectedChange(newValue)
            }
        }
    }

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
            contentPadding = PaddingValues(vertical = ITEM_HEIGHT * 2),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
        ) {
            items(count) { i ->
                val value = range.first + i
                val isSelected = value == selected
                val fontSize = if (isSelected) 30.sp else 20.sp
                val fontSizeDp = if (isSelected) 30.dp else 20.dp
                // 关键:外层 Box (48dp) → 内层 Box (height = fontSizeDp) → Text
                // 内层 Box 显式高度 = fontSize,Text 严格居中于内层 Box 中心,
                // 完全消除 Compose Text baseline 偏移的影响
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ITEM_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .height(fontSizeDp)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = value.toString().padStart(2, '0'),
                            fontSize = fontSize,
                            lineHeight = fontSize,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Purple900 else Purple900.copy(alpha = 0.3f),
                            textAlign = TextAlign.Center,
                        )
                    }
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
