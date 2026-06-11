# Design: yawn-lock-polish — UI 调整 + Bug 修复

> OpenSpec canonical spec: `openspec/changes/yawn-lock-polish/`
> 已有 spec: `openspec/specs/scheduled-screen-lock/spec.md`、`openspec/specs/floating-countdown-widget/spec.md`
> 本次仅修改 `scheduled-screen-lock`,`floating-countdown-widget` 实现修复不改动 spec。

## Context

打完哈欠 v1 归档后,真实使用暴露了 3 个 bug:
1. Timer 屏幕是死胡同(无返回权限页的入口)
2. 授权返回 app 后状态不刷新
3. 授权后点开始,Service 内部异常被吞,气泡和 ticker 都不出现

同时 UX 反馈要求:
- 滑块要支持秒级精度(用户睡前定闹钟场景)
- 预设值要覆盖中长时间段(10m/30m/1h/2h)
- CTA 文字"开始锁屏"太硬,改"开始计时"更平和

## Goals / Non-Goals

**Goals**:
- 3 个 UI 调整(滑块精度/预设/文案)落地
- 3 个 bug 修复,每个都用根因说明 + 修复方案
- 滑块在 5 秒 - 2 小时范围内,步进 1 秒;显示时单位随阈值自动切(秒 < 60 → 显示 "X 秒",否则 "X 分钟")
- 预设时长被点选后,CustomDial 同步更新
- Timer 屏幕右上角加"权限"图标,点击跳转 Permissions
- Permissions 屏幕从系统设置返回后 100ms 内显示新状态
- Service 启动后气泡失败不再拖累 ticker

**Non-Goals**:
- 不做"取消设备管理员"入口(可选,留待 v1.2)
- 不做单位选择(秒/分钟)显式切换
- 不做滑块的触觉反馈
- 不做精度自适应(始终秒)

## Decisions

### D1. 滑块精度:1 秒,范围 5 秒 - 7200 秒(2 小时)

**选型**: 滑块 `valueRange = 5f..7200f`,步进 1 秒。显示时:
- `seconds < 60` → 显示 "X 秒"
- `seconds >= 60` → 显示 "X 分钟"(整数,四舍五入)

**为什么**:
- 5 秒下限避免无意义的快速计时
- 7200 上限与 2 小时预设吻合
- 秒级精度对"睡前 5 分钟"和"醒来前 30 分钟"都友好

**替代方案**:
- 双滑块(秒/分钟):用户已反馈要做单滑块精确,弃
- 数字输入框:UI 太重,prototype 没这个,弃

### D2. 预设: 10m / 30m / 1h / 2h(从 30s/1m/5m/10m 替换)

| 旧 | 新 |
|----|-----|
| 30 秒 | 10 分钟 |
| 1 分钟 | 30 分钟 |
| 5 分钟 | 1 小时 |
| 10 分钟 | 2 小时 |

**为什么**: 短时长(30s/1m)对"提醒自己放下手机"场景太激进,真实使用是 10 分钟起。

**替代方案**: 保留 5 个预设(再增加一个):UI 太挤,prototype 原型就是 4 个,弃。

### D3. CTA 文字:"开始锁屏" → "开始计时"

产品定位:计时器是手段,锁屏是结果。"开始计时"更平和,符合"我自己选的时长"的人设。

### D4. 页面切换: Timer 屏幕加权限图标(右上角)

**选型**: 在 `TimerScreen` 的 `HeroCard` 右侧(或顶栏)加一个 `IconButton`,图标用 `Icons.Default.Settings` 或 `Icons.Default.Lock`,点击调用 `onNavigatePermissions` 回调。

**为什么**:
- 不需要重建 TopAppBar(当前 Timer 屏幕没有 TopAppBar,只有 HeroCard 块)
- HeroCard 内部加 IconButton 与现有设计风格一致(都在 22dp padding 圆角容器内)
- 不引入新素材,复用 Material Icons

**替代方案**:
- TopAppBar + nav button:需要在 HeroCard 之上再加一个组件,prototype 没这层,弃
- 右下角 FAB:已被"开始锁屏"占用,弃

### D5. 权限实时刷新: Lifecycle 观察者 + StateFlow

**当前问题**: `LaunchedEffect(Unit) { vm.refresh() }` 只在首次 composition 时跑,系统设置返回后 MainActivity.onResume 没有重新查询。

**修复方案**:
- `MainActivity` 实现 `DefaultLifecycleObserver`,在 `onResume` 调用 `permissionsViewModel.refresh()`
- `register` / `unregister` 在 `onCreate` / `onDestroy` 完成
- `PermissionsViewModel.refresh()` 已经是无副作用方法,直接调用即可

**为什么 Lifecycle 观察者而不是 DisposableEffect**:
- DisposableEffect 只在 Composable 生命周期内有效,Activity 切到后台再回来时 Composable 不一定被销毁重建
- Lifecycle 观察者绑定到 Activity 本身,与 Composable 重组解耦

**替代方案**:
- `ActivityResultLauncher` 处理权限请求回调:API 26+ 的 overlay 权限用 `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` 跳系统设置,不是标准 `requestPermissions` 流程,无法用 ActivityResultLauncher 干净处理
- `OnSharedPreferenceChangeListener`:不适用,我们不持久化权限状态

### D6. startDest 修复: 用 `remember` 锁定

**当前问题**:
```kotlin
val startDest = if (perms.canStartCountdown) "timer" else "permissions"
NavHost(navController = nav, startDestination = startDest) { ... }
```

`startDest` 是响应式 `val`,每次 `perms` 变化时 `NavHost` 收到新的 `startDestination`。虽然 NavHost 文档说"startDestination 只读一次",但这本身是 code smell,且会在日志中产生 `IllegalStateException`(Compose Navigation 2.7+ 严格模式)。

**修复方案**:
```kotlin
val startDest = remember { if (perms.canStartCountdown) "timer" else "permissions" }
```

`remember` 锁定初始值,后续 `perms` 变化不影响 `NavHost` 的 `startDestination`。导航通过显式 `nav.navigate("xxx")` 切换。

**为什么不能用 key**: `remember(key)` 在 key 变化时重置,Navigation Compose 不支持中途换 startDestination。

### D7. 气泡异常处理: 扩大 catch + Lifecycle 策略

**当前问题**:
```kotlin
fun show() {
    try { wm.addView(bubbleView, params) }
    catch (e: WindowManager.BadTokenException) { /* 静默 */ }
    ...
}
```

只 catch `BadTokenException`。其他异常(如 `SecurityException` 当 overlay 权限刚被撤销、`RuntimeException` 当 `ComposeView` 初始化失败)会导致 `ensureBubble()` 抛出,后续 `handler.post(ticker)` 不执行 → ticker 死。

**修复方案**:
```kotlin
fun show() {
    try { wm.addView(bubbleView, params) }
    catch (e: Exception) { /* 包括 BadTokenException + SecurityException + RuntimeException */ }
    // 同步状态...
}
```

`ViewCompositionStrategy` 改为 `DisposeOnDetachedFromWindowOrReleasedFromPool`:
- 当前是 `DisposeOnViewTreeLifecycleDestroyed`,需要 ViewTree 找 lifecycle owner
- WindowManager 视图不在 ViewTree 中,`findViewTreeLifecycleOwner()` 返回 null,策略无效
- 改为 `DisposeOnDetachedFromWindowOrReleasedFromPool`,在 view 从 window 移除时 dispose,与 `wm.removeView` 行为对齐

### D8. Service 防御: ensureBubble 失败不影响 ticker

**当前问题**: `handleStart` 中 `ensureBubble()` 抛出则 `handler.post(ticker)` 不执行,Service 死锁。

**修复方案**:
```kotlin
private fun handleStart() {
    val state = repo.state.value
    if (state.status !is TimerStatus.Counting) return
    startForegroundCompat(state)  // 必须在前(N 秒内 startForeground 是 Android 14+ 的硬要求)
    scheduleAlarm(state)
    try { ensureBubble() }
    catch (e: Exception) { Log.w(TAG, "bubble show failed; ticker continues", e) }
    handler.removeCallbacks(ticker)
    handler.post(ticker)  // 无论气泡如何,ticker 必须跑
}
```

**为什么 ticker 必须在气泡失败时仍跑**:
- ticker 驱动通知文字更新(每秒刷新剩余时间)
- ticker 驱动 `repo.tick()` 让 `state.remainingMs` 实时更新,否则倒计时显示卡死
- ticker 驱动 Finished 状态传播(到点时 ticker 调 `stopSelf`)

## Risks / Trade-offs

- **[风险] Lifecycle 观察者与 Compose state 同步** → `permissionsViewModel.refresh()` 已在 viewModelScope 内同步执行,StateFlow 推送是原子的;Compose 重组在主线程,无 race
- **[风险] 滑块秒级精度在低 DPI 设备上拖动困难** → 提供 ± 按钮作为 fallback(原代码已有);考虑步进可调,后续 polish
- **[风险] 实时刷新在某些 ROM 上 onResume 触发时延** → 100ms 内是经验值,实测后调整
- **[风险] remember 锁定 startDest 后,从 permissions 改回 timer 的 navigation pop 行为** → `popUpTo("permissions") { inclusive = true }` 已经在 onBack 里写好,remember 不影响
- **[权衡] 滑块从分钟改秒后,`± 按钮步进` 也要改**:1-60 区间步 5 秒,60-300 步 30 秒,>300 步 60 秒,避免调一次 ± 跳很大
- **[权衡] ViewCompositionStrategy 切换的细节**:需要实测确认 `DisposeOnDetachedFromWindowOrReleasedFromPool` 在 WindowManager 视图上的 dispose 时机正确(应该是 `wm.removeView` 触发 detach)

## Migration Plan

无 — 全部是同分支修改,无需迁移。

## Open Questions

- **Q1**: 预设值选 10m/30m/1h/2h 后,CustomDial 同步逻辑?当前实现是"点击 preset → 同步到 dial",沿用即可,确认
- **Q2**: Timer 屏幕的"权限"图标放在 HeroCard 右上角还是另起一个 TopAppBar?HeroCard 内更轻量,倾向 HeroCard 内
- **Q3**: 实时权限刷新时,Permissions 屏幕正在显示的"未授权"chip 切到"已授权"会闪一下吗?StateFlow 推送是原子的,理论上不闪,实测
- **Q4**: `ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool` 兼容性?AndroidX Compose 1.6+ 提供,Compose BOM 2024.06 已包含,兼容
