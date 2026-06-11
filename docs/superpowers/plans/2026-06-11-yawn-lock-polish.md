---
change: yawn-lock-polish
design-doc: docs/superpowers/specs/2026-06-11-yawn-lock-polish-design.md
base-ref: 7a8a5eb1b8531eebcbdbff9d395323cf64aa6a7f
archived-with: 2026-06-11-yawn-lock-polish
---

# yawn-lock-polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 v1 暴露的 3 个 bug(页面切换/权限实时刷新/气泡倒计时不见)+ 落地 3 个 UI 调整(滑块秒级/预设中长时段/CTA 文案)。

**Architecture:** 单 Activity + Jetpack Compose 沿用 v1 架构。修改 5 个文件:TimerScreen.kt(Timer UI + ViewModel)、MainActivity.kt(Lifecycle + NavHost)、FloatingBubbleController.kt(异常 + lifecycle 策略)、CountdownService.kt(Service 防御)。无新依赖,无新 capability。

**Tech Stack:** Kotlin 1.9.24, AGP 8.5.0, Jetpack Compose BOM 2024.06.00, Material 3, AndroidX Lifecycle 2.8.2, Navigation Compose 2.7.7, minSdk 26, targetSdk 34, JDK 17。

**测试策略:** 沿用 v1 冒烟测试 8 步,新增 3 个场景(滑块秒级 / Timer→Permissions 导航 / 授权后能正常倒计时)。

archived-with: 2026-06-11-yawn-lock-polish
---

## File Map

修改 5 个 Kotlin 文件(无新文件,无资源变更):

| 路径 | 修改范围 |
|------|---------|
| `app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt` | 1.x UI 调整 + 2.3 HeroCard 权限图标 |
| `app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerViewModel.kt` | 1.3 `setSeconds(Long)` 替换 `setMinutes(Double)` |
| `app/src/main/kotlin/com/example/yawnlock/MainActivity.kt` | 3.x Lifecycle 观察者 + 4.x remember 锁定 startDest |
| `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt` | 5.x 异常扩大 catch + lifecycle 策略 |
| `app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt` | 6.x try/catch 包 ensureBubble |

archived-with: 2026-06-11-yawn-lock-polish
---

## Task 1: UI — 滑块/预设/CTA 文字(秒级精度)

**Files:**
- Modify: `app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt`
- Modify: `app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerViewModel.kt`

- [ ] **Step 1: 修改 `TimerViewModel.kt`,`setMinutes` 改为 `setSeconds`**

Replace the `setMinutes` method in `TimerViewModel.kt`:

```kotlin
    fun setSeconds(s: Long) {
        if (state.value.isActive) return
        val clamped = s.coerceIn(5L, 7200L)
        repo.preview(clamped * 1000L)
    }
```

Remove the `selectedMinutes()` method (no longer used).

- [ ] **Step 2: 修改 `TimerScreen.kt` 顶部,`minutes` 状态改为 `seconds`**

Replace line 45:
```kotlin
    var minutes by remember { mutableStateOf(5.0) }
```
With:
```kotlin
    var seconds by remember { mutableStateOf(300L) }
```

- [ ] **Step 3: 替换 `LaunchedEffect` 同步逻辑**

Replace lines 47-52:
```kotlin
    // 同步 minutes 跟 state.durationMs(state 变化时)
    LaunchedEffect(state.durationMs) {
        if (state.durationMs > 0 && !state.isActive) {
            minutes = state.durationMs / 60_000.0
        }
    }
```
With:
```kotlin
    // 同步 seconds 跟 state.durationMs(state 变化时)
    LaunchedEffect(state.durationMs) {
        if (state.durationMs > 0 && !state.isActive) {
            seconds = state.durationMs / 1000L
        }
    }
```

- [ ] **Step 4: 修改 `PresetChips`(替换 lines 131-159 整个 Composable)**

```kotlin
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
```

- [ ] **Step 5: 修改 `PresetChips` 调用点(line 65-68)**

```kotlin
            PresetChips(
                selected = seconds,
                onSelect = { s -> seconds = s; vm.setSeconds(s) },
            )
```

- [ ] **Step 6: 修改 `CustomDial`(替换 lines 162-211 整个 Composable)**

```kotlin
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
```

- [ ] **Step 7: 修改 `CustomDial` 调用点(line 70-73)**

```kotlin
            CustomDial(
                seconds = seconds,
                onChange = { s -> seconds = s; vm.setSeconds(s) },
            )
```

- [ ] **Step 8: 改 CTA 文案("开始锁屏" → "开始计时")**

Find line 274: `Text("开始锁屏", ...)`
Replace with: `Text("开始计时", ...)`

- [ ] **Step 9: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 10: 提交**

```bash
git add app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerViewModel.kt
git commit -m "feat(timer): rewrite preset/slider/CTA for second precision"
```

archived-with: 2026-06-11-yawn-lock-polish
---

## Task 2: Bug — Timer 屏幕 HeroCard 权限入口

**Files:**
- Modify: `app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt`

- [ ] **Step 1: 改 `HeroCard` 签名 + 实现**

Replace lines 96-114 整个 `HeroCard` Composable:

```kotlin
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
```

Add import at top of file:
```kotlin
import androidx.compose.material.icons.filled.Settings
```

- [ ] **Step 2: 改 `HeroCard` 调用点(line 63)**

Find: `HeroCard()`
Replace with: `HeroCard(onPermissionsClick = onNavigatePermissions)`

- [ ] **Step 3: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 提交**

```bash
git add app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt
git commit -m "feat(timer): add permissions entry icon in hero card"
```

archived-with: 2026-06-11-yawn-lock-polish
---

## Task 3: Bug — 权限实时刷新(Lifecycle 观察者)

**Files:**
- Modify: `app/src/main/kotlin/com/example/yawnlock/MainActivity.kt`

- [ ] **Step 1: 改 `MainActivity` 实现 `DefaultLifecycleObserver`**

Replace the entire `MainActivity.kt`:

```kotlin
package com.example.yawnlock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.yawnlock.ui.permissions.PermissionsScreen
import com.example.yawnlock.ui.permissions.PermissionsViewModel
import com.example.yawnlock.ui.theme.YawnLockTheme
import com.example.yawnlock.ui.timer.TimerScreen

class MainActivity : ComponentActivity(), DefaultLifecycleObserver {
    private val permsVm: PermissionsViewModel by lazy { PermissionsViewModel(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycle.addObserver(this)
        setContent {
            YawnLockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(permsVm = permsVm)
                }
            }
        }
    }

    override fun onResume(owner: LifecycleOwner) {
        permsVm.refresh()
    }

    override fun onDestroy() {
        lifecycle.removeObserver(this)
        super.onDestroy()
    }
}

@Composable
private fun AppNavHost(permsVm: PermissionsViewModel) {
    val nav = rememberNavController()
    LaunchedEffect(Unit) { permsVm.refresh() }
    val perms by permsVm.state.collectAsState()

    val startDest = remember {
        if (perms.canStartCountdown) "timer" else "permissions"
    }

    NavHost(navController = nav, startDestination = startDest) {
        composable("timer") {
            TimerScreen(onNavigatePermissions = { nav.navigate("permissions") })
        }
        composable("permissions") {
            PermissionsScreen(
                onBack = {
                    if (perms.canStartCountdown) {
                        nav.navigate("timer") {
                            popUpTo("permissions") { inclusive = true }
                        }
                    } else {
                        nav.popBackStack()
                    }
                },
            )
        }
    }
}
```

Add import: `import androidx.compose.runtime.remember`

- [ ] **Step 2: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add app/src/main/kotlin/com/example/yawnlock/MainActivity.kt
git commit -m "fix(perm): refresh permission state on activity resume"
```

archived-with: 2026-06-11-yawn-lock-polish
---

## Task 4: Bug — startDest remember 锁定

> 已在 Task 3 Step 1 中包含(`val startDest = remember { ... }`),作为 Task 3 一并提交。
> 这里只追加一个独立 commit 让 history 清晰。

- [ ] **Step 1: 检查 Task 3 是否已经写了 `remember`**

`MainActivity.kt` 中 `AppNavHost` 的 `startDest` 应该是:
```kotlin
val startDest = remember {
    if (perms.canStartCountdown) "timer" else "permissions"
}
```

如果 Task 3 已经包含此变更,本 Task 4 无操作,跳过到 Task 5。

如果没包含,需要单独编辑 MainActivity.kt 改 `val startDest` 为 `remember { ... }`,然后:

```bash
git add app/src/main/kotlin/com/example/yawnlock/MainActivity.kt
git commit -m "fix(nav): lock startDest with remember to avoid recompose side-effects"
```

archived-with: 2026-06-11-yawn-lock-polish
---

## Task 5: Bug — 气泡异常 + Lifecycle 策略

**Files:**
- Modify: `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt`

- [ ] **Step 1: 改 `show()` 异常 catch**

Find line 90-92 in `FloatingBubbleController.kt`:
```kotlin
    fun show() {
        try {
            wm.addView(bubbleView, params)
        } catch (e: WindowManager.BadTokenException) {
            // token 失效,静默忽略
        }
```
Replace with:
```kotlin
    fun show() {
        try {
            wm.addView(bubbleView, params)
        } catch (e: Exception) {
            // 含 BadTokenException / SecurityException / RuntimeException,静默忽略
        }
```

- [ ] **Step 2: 改 `ViewCompositionStrategy`**

Find line 73 in `FloatingBubbleController.kt`:
```kotlin
        bubbleView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
```
Replace with:
```kotlin
        bubbleView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
```

- [ ] **Step 3: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 提交**

```bash
git add app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt
git commit -m "fix(bubble): broaden exception catch + use detach-based dispose strategy"
```

archived-with: 2026-06-11-yawn-lock-polish
---

## Task 6: Bug — Service ensureBubble 防御

**Files:**
- Modify: `app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt`

- [ ] **Step 1: 改 `handleStart` 包 try/catch**

Find lines 70-78 in `CountdownService.kt`:
```kotlin
    private fun handleStart() {
        val state = repo.state.value
        if (state.status !is TimerStatus.Counting) return
        startForegroundCompat(state)
        scheduleAlarm(state)
        ensureBubble()
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }
```
Replace with:
```kotlin
    private fun handleStart() {
        val state = repo.state.value
        if (state.status !is TimerStatus.Counting) return
        startForegroundCompat(state)
        scheduleAlarm(state)
        try {
            ensureBubble()
        } catch (e: Exception) {
            android.util.Log.w("CountdownService", "bubble show failed; ticker continues", e)
        }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }
```

- [ ] **Step 2: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: 提交**

```bash
git add app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt
git commit -m "fix(service): decouple ticker from bubble success in handleStart"
```

archived-with: 2026-06-11-yawn-lock-polish
---

## Task 7: Build + APK 验证

**Files:** 无(纯命令)

- [ ] **Step 1: 完整 assembleDebug**

```bash
. ./.env.sh && ./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` + APK at `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 2: 验证 APK 大小**

```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

Expected: file < 5MB(无新依赖,应该持平)

- [ ] **Step 3: 提交(若有 build 配置变更)**

```bash
git status
# 若有变更:
git add -A
git commit -m "build: assembleDebug after polish changes" || echo "no changes to commit"
```

archived-with: 2026-06-11-yawn-lock-polish
---

## Self-Review

- [x] **Spec coverage**:
  - `Select Lock Duration` 改 (秒级 + 新预设): Task 1
  - `Start Countdown` 改 (CTA 文案 + 权限入口): Task 1 + Task 2
  - `Permission Gating on Timer Screen` 改 (实时刷新): Task 3
  - "新增 Scenario: User navigates from timer to permissions via icon": Task 2
  - "新增 Scenario: User grants permission and returns to app": Task 3
  - "新增 Scenario: User adjusts duration to sub-minute value": Task 1
- [x] **Placeholder scan**: 无 TBD/TODO;所有代码块完整
- [x] **Type consistency**:
  - `TimerViewModel.setSeconds(Long)` 定义 Task 1,使用 Task 1
  - `TimerScreen.seconds: Long` 状态定义 Task 1 Step 2,使用 Task 1 Steps 4-7
  - `MainActivity.permsVm: PermissionsViewModel` 私有 lazy 初始化 Task 3,使用 Task 3
  - `FloatingBubbleController.show()` 改 catch Task 5,使用同文件
  - `CountdownService.handleStart()` 改 try/catch Task 6
- [x] **Scope check**: 6 个 task,每个 ≤ 50 行代码变更

archived-with: 2026-06-11-yawn-lock-polish
---

## End of Plan

实施完成后,运行:
```bash
. ./.env.sh && ./gradlew :app:assembleDebug
```

构建成功 + 冒烟测试(沿用 v1 8 步 + 3 个新场景)通过后,继续 `/comet-build` 退出流程。
