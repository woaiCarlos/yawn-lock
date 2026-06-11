---
comet_change: yawn-lock-polish
role: technical-design
canonical_spec: openspec
archived-with: 2026-06-11-yawn-lock-polish
status: final
---

# Design: yawn-lock-polish — UI 调整 + Bug 修复

> OpenSpec canonical spec: `openspec/changes/yawn-lock-polish/`
> 已有 spec(从 v1 归档): `openspec/specs/scheduled-screen-lock/spec.md`、`openspec/specs/floating-countdown-widget/spec.md`
> 本次只 delta 修改 `scheduled-screen-lock`;`floating-countdown-widget` 实现修复不改动 spec。
> 已有设计参考(高维): `openspec/changes/yawn-lock-polish/design.md`

## 0. 决策摘要(已确认)

| # | 决策 | 选择 | 备注 |
|---|------|------|------|
| D1 | 滑块精度 | 1 秒,范围 5-7200 秒 | 用户明确"精确到秒" |
| D2 | 预设值 | 10 分 / 30 分 / 1 时 / 2 时 | 用户明确列表 |
| D3 | CTA 文字 | "开始计时" | 用户明确 |
| D4 | 权限入口位置 | HeroCard 右上角 IconButton | 用户确认 |
| D5 | 实时刷新机制 | `DefaultLifecycleObserver` + `onResume` | 绑定到 Activity,解耦 Composable 重组 |
| D6 | startDest 锁定 | `remember { }` 一次性计算 | 避免 recompose 副作用 |
| D7 | 气泡异常 | `catch (e: Exception)` 扩大 + detach 策略 | 适配 WindowManager 视图 |
| D8 | Service 防御 | `ensureBubble` try/catch 隔离 | ticker 必须跑,气泡失败不影响倒计时 |

## 1. 上下文(Context)

打完哈欠 v1 归档后,真实使用暴露了 3 个 bug:
- Bug 1 — 页面切换: Timer 屏幕是死胡同,无法回到 Permissions
- Bug 2 — 权限页不实时刷新: 用户从系统设置返回 app,chip 仍显示"未授权"
- Bug 3 — 气泡/倒计时不见: Service 启动后 `ensureBubble` 抛异常,被吞,ticker 一起死

同时 UX 反馈要求 3 个调整:
- 滑块精确到秒(用户睡前定闹钟场景)
- 预设值覆盖中长时间段
- CTA 文字"开始锁屏" → "开始计时"

## 2. 目标 / 非目标(Goals / Non-Goals)

**Goals**:
- 3 个 UI 调整按用户明确要求落地
- 3 个 bug 修复,每个用根因 + 方案说明
- 滑块 5 秒 - 2 小时,1 秒步进;单位随阈值自动切(< 60 秒显示 "X 秒",否则 "X 分钟")
- 预设被点选后 CustomDial 同步
- Timer 屏幕 HeroCard 右上角"权限"图标可跳转 Permissions
- Permissions 屏幕从系统设置返回后 ≤ 100ms 内显示新状态
- Service 启动后气泡失败不再拖累 ticker

**Non-Goals**:
- 不做"取消设备管理员"入口(可选,留待 v1.2)
- 不做单位选择(秒/分钟)显式切换
- 不做滑块触觉反馈
- 不做精度自适应
- 不动 DND、统计、Widget 等非本 change 范围

## 3. 决策(Decisions)

### D1. 滑块精度:1 秒,范围 5-7200 秒

**选型**: `Slider(value = seconds.toFloat(), valueRange = 5f..7200f)`,内部状态 `seconds: Long`。
显示逻辑:
```kotlin
val big = if (seconds < 60) seconds.toString() else (seconds / 60).toString()
val unit = if (seconds < 60) "秒" else "分钟"
```

**为什么**:
- 5 秒下限避免无意义快速计时
- 7200 上限与 2 小时预设吻合
- 秒级精度对"睡前 5 分钟"和"醒来前 30 分钟"都友好

**替代方案对比**:
- 双滑块(秒/分钟):用户已要求单滑块精确,弃
- 数字输入框:UI 太重,prototype 没这个,弃
- 1-60 分钟范围:用户明确"精确到秒",弃

### D2. 预设:10m / 30m / 1h / 2h

| 旧 | 新 |
|----|-----|
| 30 秒 | 10 分钟 (600 秒) |
| 1 分钟 | 30 分钟 (1800 秒) |
| 5 分钟 | 1 小时 (3600 秒) |
| 10 分钟 | 2 小时 (7200 秒) |

**为什么**: 短时长(30s/1m)对"提醒自己放下手机"场景太激进,真实使用是 10 分钟起。

**替代方案**: 保留 5 个预设(再增加): UI 太挤,prototype 原型就是 4 个,弃。

### D3. CTA 文字:"开始锁屏" → "开始计时"

产品定位: 计时器是手段,锁屏是结果。"开始计时"更平和,符合"我自己选的时长"的人设。

### D4. 页面切换:Timer 屏幕 HeroCard 右上角权限入口

**实现**:
```kotlin
@Composable
private fun HeroCard(onPermissionsClick: () -> Unit) {
    Box(...) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 原有标题 + 副标题,占左侧 weight(1f)
            Column(modifier = Modifier.weight(1f)) { ... }
            // 右侧权限 IconButton
            IconButton(onClick = onPermissionsClick) {
                Icon(Icons.Default.Settings, contentDescription = "权限", tint = Color.White)
            }
        }
    }
}
```

`TimerScreen` 顶层签名加 `onNavigatePermissions: () -> Unit`(已有,直接传),`HeroCard` 私有签名加新参数。

**为什么 HeroCard 内**:
- 不引入新 TopAppBar,与原型 hero 设计一致
- 不与底部 StartCta 抢视觉
- 复用 Material Icons,无新素材

### D5. 权限实时刷新:Lifecycle 观察者

**当前问题**:
```kotlin
LaunchedEffect(Unit) { vm.refresh() }
```
只在首次 composition 时跑。用户从系统设置返回,MainActivity.onResume 没重新查询,chip 状态陈旧。

**修复**:
```kotlin
class MainActivity : ComponentActivity(), DefaultLifecycleObserver {
    private val permsVm by lazy { PermissionsViewModel(this) }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(this)
        ...
    }
    
    override fun onResume() {
        super.onResume()
        permsVm.refresh()
    }
    
    override fun onDestroy() {
        lifecycle.removeObserver(this)
        super.onDestroy()
    }
}
```

**为什么 Lifecycle 观察者而不是 DisposableEffect**:
- DisposableEffect 只在 Composable 生命周期内有效,Activity 切到后台再回来时 Composable 不一定被销毁重建
- Lifecycle 观察者绑定到 Activity 本身,与 Composable 重组解耦

**为什么直接读 viewModel 不通过 `viewModel()`**:
- 避免 AppNavHost 内的 `viewModel<PermissionsViewModel>()` 与 Activity 内自己持有的实例不一致
- 通过 `YawnApplication` 持有 `permissionsViewModel` 共享实例? 不,这里直接 `MainActivity` 自己持有也 OK,因为 Activity 重建时也会重新创建

**考虑**:也可以通过 `LocalLifecycleOwner.current.lifecycle.addObserver` 在 Compose 端实现,效果相同。我选 Activity 级实现,显式易追踪。

### D6. startDest 锁定:remember

**当前问题**:
```kotlin
val startDest = if (perms.canStartCountdown) "timer" else "permissions"
NavHost(navController = nav, startDestination = startDest) { ... }
```
`startDest` 是响应式 `val`,每次 `perms` 变化时 `NavHost` 收到新的 `startDestination`。Compose Navigation 2.7+ 严格模式下会抛 `IllegalStateException`。

**修复**:
```kotlin
val startDest = remember {
    if (perms.canStartCountdown) "timer" else "permissions"
}
```

`remember` 锁定初始值,后续 `perms` 变化不影响 `NavHost` 的 `startDestination`。导航通过显式 `nav.navigate("xxx")` 切换。

### D7. 气泡异常 + Lifecycle 策略

**当前问题**:
```kotlin
fun show() {
    try { wm.addView(bubbleView, params) }
    catch (e: WindowManager.BadTokenException) { /* 静默 */ }
    ...
}
```
只 catch `BadTokenException`。其他异常会导致 `ensureBubble()` 抛出,后续 `handler.post(ticker)` 不执行。

**修复**:
```kotlin
fun show() {
    try { wm.addView(bubbleView, params) }
    catch (e: Exception) { /* 包括 BadToken/Security/RuntimeException */ }
    // 同步状态...
}

init {
    bubbleView.setViewCompositionStrategy(
        ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
    )
    ...
}
```

**为什么 `DisposeOnDetachedFromWindowOrReleasedFromPool`**:
- 当前用 `DisposeOnViewTreeLifecycleDestroyed`,需 `findViewTreeLifecycleOwner()`,但 WindowManager 视图不在 ViewTree 中
- 改为 `DisposeOnDetachedFromWindowOrReleasedFromPool`,在 view 从 window 移除时 dispose,与 `wm.removeView` 行为对齐

### D8. Service 防御:ensureBubble 失败不影响 ticker

**当前问题**:
```kotlin
private fun handleStart() {
    val state = repo.state.value
    if (state.status !is TimerStatus.Counting) return
    startForegroundCompat(state)
    scheduleAlarm(state)
    ensureBubble()  // <-- 如果抛,ticker 不跑
    handler.removeCallbacks(ticker)
    handler.post(ticker)  // <-- 死锁点
}
```

**修复**:
```kotlin
private fun handleStart() {
    val state = repo.state.value
    if (state.status !is TimerStatus.Counting) return
    startForegroundCompat(state)  // 必须在前(Android 14+ 硬要求)
    scheduleAlarm(state)
    try { ensureBubble() }
    catch (e: Exception) { Log.w(TAG, "bubble show failed; ticker continues", e) }
    handler.removeCallbacks(ticker)
    handler.post(ticker)  // 无论气泡如何,ticker 必须跑
}
```

**为什么 ticker 必须在气泡失败时仍跑**:
- ticker 驱动通知文字更新(每秒刷新剩余时间)
- ticker 驱动 `repo.tick()` 让 `state.remainingMs` 实时更新
- ticker 驱动 Finished 状态传播(到点 ticker 调 `stopSelf`)

## 4. 风险 / 取舍(Risks / Trade-offs)

- **[风险] Lifecycle 观察者与 Compose state 同步** → `permissionsViewModel.refresh()` 已在 viewModelScope 同步执行,StateFlow 推送原子;Compose 重组在主线程,无 race
- **[风险] 滑块秒级精度在低 DPI 设备上拖动困难** → 提供 ± 按钮作为 fallback(原代码已有),步进分段
- **[风险] 实时刷新在某些 ROM 上 onResume 时延** → 100ms 内是经验值,实测后调整
- **[风险] remember 锁定 startDest 后,起点选择** → 首启根据 `perms.canStartCountdown` 选起点,后续手动 nav 切换
- **[权衡] 滑块秒级后,± 按钮步进也分段** → 详见 D1,避免调一次 ± 跳很大
- **[权衡] Bubble 异常 catch 扩大后,某些 RuntimeException 可能吞掉真 bug** → 加 `Log.w(TAG, ..., e)`,上线后看 logcat 监控

## 5. 迁移计划(Migration Plan)

无 — 全部是同分支修改,无需迁移。

## 6. 测试策略

**冒烟测试**(沿用 v1 路径,新增 3 个场景):
1. 全新安装 → 跳 Permissions
2. 授权悬浮窗 → 返回 → chip 变"已授权"
3. 授权设备管理员 → chip 变"已授权" → 自动跳回 Timer
4. **新**: 滑块调到 45 秒 → 显示 "45 秒" (验证秒级精度)
5. **新**: 在 Timer 屏幕点 HeroCard 右上角权限图标 → 跳 Permissions → 返回 → 验证 2 个 chip 都"已授权"
6. **新**: 完全授权后点"开始计时" → 验证气泡出现 + 通知文字每秒更新 + 倒计时归零锁屏
7. 选 10min 预设 → 开始 → 等 5 秒 → 状态卡显示 09:55
8. 拖动气泡到右边缘释放 → 收起为小药丸
9. 验证 v1 全部旧场景不回归

**build**: `./gradlew :app:assembleDebug` 必通

**回归**: v1 8 步冒烟全部必须仍通

## 7. 开放问题(已决议)

- Q1 预设 10/30/1h/2h 后 CustomDial 同步 → 沿用现有"点击 preset → 同步到 dial"逻辑,**已确认**
- Q2 Timer 屏幕权限入口位置 → **HeroCard 右上角**,**已确认**
- Q3 实时权限刷新 chip 切换是否闪烁 → StateFlow 推送原子,理论不闪,**实测验证**
- Q4 ViewCompositionStrategy 兼容性 → AndroidX Compose 1.6+ 提供,BOM 2024.06 已包含,**已确认**

## 8. 引用

- OpenSpec proposal: `openspec/changes/yawn-lock-polish/proposal.md`
- OpenSpec design (高维): `openspec/changes/yawn-lock-polish/design.md`
- OpenSpec delta spec: `openspec/changes/yawn-lock-polish/specs/scheduled-screen-lock/spec.md`
- OpenSpec tasks: `openspec/changes/yawn-lock-polish/tasks.md`
- Handoff context: `openspec/changes/yawn-lock-polish/.comet/handoff/design-context.md`
- 已有 v1 main spec: `openspec/specs/scheduled-screen-lock/spec.md`、`openspec/specs/floating-countdown-widget/spec.md`
- 已有 v1 Design Doc: `docs/superpowers/specs/2026-06-11-yawn-lock-design.md` (archived-with: 2026-06-11-yawn-lock)
