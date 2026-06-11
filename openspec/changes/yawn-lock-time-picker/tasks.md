# Tasks: yawn-lock-time-picker

> 时钟式滚轮选择器。3 个代码任务 + 1 个 spec 同步 + 1 个验证。

## 1. 新建 `WheelColumn` 组件

**File:** `app/src/main/kotlin/com/example/yawnlock/ui/timer/WheelColumn.kt`(新建)

- [ ] **Step 1: 写 WheelColumn 通用 Composable**

```kotlin
package com.example.yawnlock.ui.timer

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yawnlock.ui.theme.Purple900
import kotlin.math.abs

private val ITEM_HEIGHT = 48.dp
private val VISIBLE_ITEMS = 3

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

    // 滚轮 → state: 吸附完成后回调
    LaunchedEffect(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset) {
        if (abs(listState.firstVisibleItemScrollOffset) < 8) {
            val newValue = range.first + listState.firstVisibleItemIndex
            if (newValue != selected && newValue in range) {
                onSelectedChange(newValue)
            }
        }
    }

    // state → 滚轮: 外部 selected 变化时滚过去
    LaunchedEffect(selected) {
        val target = (selected - range.first).coerceAtLeast(0)
        if (target != listState.firstVisibleItemIndex) {
            listState.animateScrollToItem(target)
        }
    }

    Box(
        modifier = modifier.height(ITEM_HEIGHT * VISIBLE_ITEMS),
        contentAlignment = Alignment.Center,
    ) {
        // 中央高亮条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ITEM_HEIGHT)
                .background(Purple900.copy(alpha = 0.08f))
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = ITEM_HEIGHT),
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
                        fontSize = if (isSelected) 28.sp else 18.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Purple900 else Purple900.copy(alpha = 0.4f),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/kotlin/com/example/yawnlock/ui/timer/WheelColumn.kt
git commit -m "feat(time): add WheelColumn composable for time picker"
```

## 2. 重写 `CustomDial` 使用 WheelColumn

**File:** `app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt`

- [ ] **Step 1: 替换 CustomDial 函数体**

删除 `CustomDial` 整个函数(含 `Slider` / `± 按钮` / `big`/`unit` 变量),替换为:

```kotlin
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
```

- [ ] **Step 2: 清理无用 import**

在 `TimerScreen.kt` 顶部 import 区,删除:
- `androidx.compose.material3.Slider`(若不再用)
- `androidx.compose.material3.FilledTonalIconButton`(若不再用)

(编译会警告但不会失败,可选清理)

- [ ] **Step 3: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt
git commit -m "feat(time): replace CustomDial slider with 3-column wheel picker"
```

## 3. 同步 delta spec

**File:** `openspec/changes/yawn-lock-time-picker/specs/scheduled-screen-lock/spec.md`(新建)

- [ ] **Step 1: 写 delta spec**

```markdown
## ADDED Requirements

### Requirement: Custom Time Picker

The Timer screen SHALL provide a clock-style time picker with three independent wheel columns for hour, minute, and second.

#### Scenario: Three-column wheel picker

- **WHEN** the user navigates to the custom time section
- **THEN** the UI MUST display three vertical wheel columns side by side, separated by ":"
- **AND** the first column ranges over hours (0..2), the second over minutes (0..59), the third over seconds (0..59)
- **AND** the currently selected value in each column MUST be visually highlighted (larger font + bold)

#### Scenario: Scroll-to-select behavior

- **WHEN** the user scrolls any column
- **THEN** the column MUST snap to integer values when scrolling stops
- **AND** the snap MUST trigger a state update with the newly selected value

#### Scenario: Composed duration clamp

- **WHEN** any column's value changes
- **THEN** the system MUST recompute total seconds as `hours * 3600 + minutes * 60 + seconds`
- **AND** the resulting duration MUST be clamped to the range [5, 7200] seconds
- **AND** the wheel positions MUST be coerced back to the clamped duration's column values
```

- [ ] **Step 2: 提交**

```bash
git add openspec/changes/yawn-lock-time-picker/specs/
git commit -m "docs(spec): add delta for Custom Time Picker"
```

## 4. 端到端验证

- [ ] **Step 1: 装 APK 到设备**

```bash
. ./.env.sh && ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 2: 手测清单**

- 启动 app → 自定义区出现 3 列滚轮(h:m:s)
- 滚小时列 → 中央值高亮 + 滑块消失 + 数字补 0
- 滚分钟列 → 类似
- 滚秒列 → 类似
- 点 30 分钟 preset chip → 滚轮自动滚到 0:30:00
- 点 1 小时 chip → 滚轮滚到 1:00:00
- 滚到 2:30:00 → 自动 clamp 回 2:00:00(显示滚回去)
- 滚到 0:0:5 → 最小值 5 秒
- 点开始计时 → 启动成功

## Self-Review

- 3 个代码 commit(任务 1-2 + 1 文档)+ 1 验证
- 1 个新文件(WheelColumn.kt)+ 2 个文件改动(TimerScreen.kt + delta spec)
- 无新依赖
- 走完 build 后转 verify → archive
