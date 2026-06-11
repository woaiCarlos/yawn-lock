# Design: yawn-lock-bugfix-3

> OpenSpec canonical spec: `openspec/changes/yawn-lock-bugfix-3/`
> 已有 main spec: `openspec/specs/floating-countdown-widget/spec.md`(spec 已规定前台不显示,后台显示 — 实现未做)

## Context

打完哈欠 v1.1 + bugfix-1 + bugfix-2 后,锁屏 ✅。但悬浮气泡未按 spec 工作:
- v1 实现时 `handleStart` 无条件 `ensureBubble` → 在 app 前台也显示气泡 → 与原型/spec 矛盾
- v1.1 polish / bugfix-1 / bugfix-2 都没碰这条逻辑,只解决了气泡"能否渲染"和"service 是否崩溃"

## Goals / Non-Goals

**Goals**:
- 气泡仅在 app 进后台(ON_STOP)时显示
- 气泡在 app 回到前台(ON_START)时隐藏
- 倒计时结束/停止时,气泡隐藏(无论 app 在哪)
- 拖动到边缘/展开的现有行为不变
- 倒计时时间/暂停/停止按钮的 UI 不变

**Non-Goals**:
- 不动 spec
- 不重构 CountdownService 架构
- 不动锁屏逻辑(已修)

## Decisions

### D1. 使用 `ProcessLifecycleOwner` 监听 app 前后台

**选型**:`androidx.lifecycle:lifecycle-process:2.8.2` 提供的 `ProcessLifecycleOwner.get()` 监听整个进程的 foreground/background 状态(整合所有 Activity 的 lifecycle)。

**为什么**:
- AndroidX 官方库,无需自己实现 ActivityLifecycleCallbacks 计数
- 通过 `ON_START` / `ON_STOP` 事件精准标记"app 进入前台/后台"(考虑 Activity 栈的复杂性,比如多 Activity 切换)
- `lifecycle-process` 已通过 `lifecycle-runtime-ktx` 间接被 Lifecycle 2.8.x 自动依赖,显式声明只确保版本一致

**替代方案**:
- 自己实现 `ActivityLifecycleCallbacks` 计数:需要注册 onActivityStarted/onActivityStopped,代码量多、易出错
- 用 `ProcessLifecycleOwner` 是标准做法

### D2. 订阅生命周期 + 显隐气泡

**当前**:
```kotlin
private fun handleStart() {
    ...
    try { ensureBubble() } catch (e: Exception) { ... }
    handler.removeCallbacks(ticker)
    handler.post(ticker)
}
```

`ensureBubble()` 立即调 `show()`,不管 app 状态。

**修复**:
```kotlin
private val processLifecycleObserver = LifecycleEventObserver { _, event ->
    when (event) {
        Lifecycle.Event.ON_STOP -> bubble?.show()    // app 进后台 → 显示
        Lifecycle.Event.ON_START -> bubble?.hide()  // app 回前台 → 隐藏
        else -> { /* ON_CREATE / ON_RESUME / ON_PAUSE / ON_DESTROY 忽略 */ }
    }
}

override fun onCreate() {
    super.onCreate()
    NotificationCenter.ensureChannel(this)
    ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
}

override fun onDestroy() {
    ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
    handler.removeCallbacks(ticker)
    handler.removeCallbacks(endRunnable)
    bubble?.hide()
    bubble = null
    NotificationCenter.cancel(this)
    super.onDestroy()
}

private fun handleStart() {
    val state = repo.state.value
    if (state.status !is TimerStatus.Counting) return

    // 诊断(沿用 bugfix-2)
    ...
    
    startForegroundCompat(state)
    scheduleEnd(state.remainingMs)
    // 不再立即 ensureBubble — 改由 ON_STOP 触发
    handler.removeCallbacks(ticker)
    handler.post(ticker)
}

private fun handlePause() {
    ...
    repo.pause()
    handler.removeCallbacks(endRunnable)
    cancelAlarm()
    NotificationCenter.update(this, state.remainingMs, isPaused = true)
    // 进后台时,气泡继续显示(用户期望能看到"已暂停"状态)
    // 状态变化由 bubble.updateTime(remainingMs) + bubble 的 isPaused 字段驱动
}

private fun handleStop() {
    // ACTION_STOP 分支
    handler.removeCallbacks(endRunnable)
    cancelAlarm()
    repo.stop()
    stopSelf()
    // 停止时,bubble 在 onDestroy 中通过 bubble?.hide() 清理
    // 不需要在 stopSelf 之前显式 hide,因为 Service 销毁流程会处理
}
```

**关键点**:
- `ensureBubble()` 完全移除
- 气泡的显示/隐藏由 ProcessLifecycle 事件驱动
- 进后台时,**首次 ON_STOP 触发**会调 `bubble?.show()` — 但此时 `bubble` 可能是 null(还没创建过)

**D3. 处理"首次 ON_STOP 时 bubble 为 null"的情况**:

`ensureBubble()` 原本在 handleStart 创建 FloatingBubbleController。改为 ProcessLifecycle 驱动后,bubble 可能未创建:

```kotlin
private fun ensureBubble(): FloatingBubbleController {
    if (bubble == null) {
        bubble = FloatingBubbleController(this).also { it.show() }
    }
    return bubble!!
}

private val processLifecycleObserver = LifecycleEventObserver { _, event ->
    when (event) {
        Lifecycle.Event.ON_STOP -> {
            if (repo.state.value.isActive) {  // 倒计时还在跑
                ensureBubble()  // 内部调 show()
            }
        }
        Lifecycle.Event.ON_START -> bubble?.hide()  // 进前台,隐藏即可
        else -> { }
    }
}
```

**为什么**:
- 倒计时开始后,首次进后台时 `bubble == null`,需要创建 + show
- 进前台时,如果 bubble == null,什么都不做(没必要创建)
- `repo.state.value.isActive` 检查确保只在倒计时中显示气泡(停止后即使没收到 ON_START 也不显示)
- `handleStart` 不再调 ensureBubble — 整个创建和首次显示延迟到首次 ON_STOP

### D4. 拖动到边缘/展开 + 倒计时 UI — 已 v1.1 实现,验证

**当前代码已支持**:
- `FloatingBubbleController.handleTouch`:
  - `ACTION_UP`: `if (moved && params.x < dp(36)) collapse()` — 拖到右边缘 36dp 内就收起
  - `else if (!moved && collapsed) expand()` — 点击收起态就展开
- `BubbleContent`:
  - 倒计时 `DurationFormatter.toMmSs(remainingMs)`
  - 暂停/继续按钮 → `repo.pause/resume`
  - 停止按钮 → `repo.stop`
  - "Sleepy Lock" 标题
  - 紫色渐变背景

**修复**:仅在 `CountdownService` 集成,**不修改 FloatingBubbleController.kt**

## Risks / Trade-offs

- **[风险] `ProcessLifecycleOwner` 必须在 Application context 中订阅** → CountdownService 的 application 已经是 Application,可直接用
- **[风险] Service 销毁后,ProcessLifecycleOwner 的 observer 自动取消?** → 不会,需要手动在 onDestroy 取消(标准模式)
- **[风险] 多个 CountdownService 实例叠加 observer 重复** → Service 用 `startForegroundService` 启动,`stopSelf` 后销毁,通常只有 1 个实例
- **[权衡] 倒计时期间 app 一直前台,气泡永不显示** → 用户看到的是 Timer 屏幕的 StatusCard(已有),信息不丢
- **[权衡] 倒计时期间 app 频繁前后台切换** → 每次 ON_STOP 调 show()(若 bubble==null 创建),每次 ON_START 调 hide() — 开销小,接受

## Migration Plan

无 — 同分支修改。

## Open Questions

- Q1: 倒计时暂停时(用户主动暂停),气泡应否继续显示?当前设计:是(只要 `state.isActive` true,ON_STOP 就 show)
- Q2: 倒计时结束(到点锁屏),气泡应否在 lock 触发时主动 hide?当前:不主动 hide,但 Service 进入 stopSelf/onDestroy 流程,会在 onDestroy 调 `bubble?.hide()` 清理
- Q3: 用户在主 Timer 屏幕主动按"停止"按钮(→ ACTION_STOP)→ `repo.stop()` → 状态变 Idle。ProcessLifecycle 后续 ON_STOP 仍会触发 show 吗?会 — 需要在 observer 中检查 `state.isActive`
