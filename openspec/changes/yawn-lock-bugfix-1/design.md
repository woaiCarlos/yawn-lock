# Design: yawn-lock-bugfix-1

> OpenSpec canonical spec: `openspec/changes/yawn-lock-bugfix-1/`
> 已有 main spec(从 v1.1 polish 归档): `openspec/specs/scheduled-screen-lock/spec.md`、`openspec/specs/floating-countdown-widget/spec.md`
> 本次仅修复 3 个运行时 bug,不改 spec 契约。**无 delta spec**。

## Context

打完哈欠 v1.1 polish 归档后真实使用暴露 3 个 bug:

| Bug | 现象 | 严重度 |
|-----|------|--------|
| 1. 状态栏遮挡 | 整个 APP 被状态栏挡住 | UX 严重(影响所有屏幕) |
| 2. 无悬浮窗 | 授权后开始,跳桌面没看到任何气泡 | 核心功能缺失 |
| 3. 5 秒不锁屏 | 短时长定时器到点不锁屏 | 核心功能失效 |

## Goals / Non-Goals

**Goals**:
- 3 个 bug 全部修复,各 1 个文件改动
- 修复根因,不绕过症状
- 不引入新依赖、不改 spec
- 加日志便于诊断
- build SUCCESSFUL,APK 重新生成

**Non-Goals**:
- 不动 spec 行为契约
- 不做权限引导(短时长走 Handler 路径绕过 alarm 权限)
- 不做长时长的用户引导(降级为 inexact 是 v1 已知限制)

## Decisions

### D1. Insets 处理方式

**选型**: `Modifier.windowInsetsPadding(WindowInsets.systemBars)` 套在根 `Surface` 上。

**为什么**:
- `systemBars` 同时覆盖 status bar 和 navigation bar,Android 10+ 推荐
- 比手动 `padding(top = statusBarHeight)` 简洁,自动响应配置变化
- `MainActivity.enableEdgeToEdge()` 已配对(让内容延伸到 systemBars 区域,这个 modifier 把它"推回去")

**替代方案**:
- `Modifier.statusBarsPadding()`:只处理顶部,底部仍可能受 navigation bar 影响
- `Modifier.systemBarsPadding()` 同 `windowInsetsPadding(systemBars)`,只是 alias
- `Scaffold` 内 `Scaffold` 自动处理 — 但 NavHost 顶层不是 Scaffold

**位置**:`MainActivity.setContent` 内,`Surface(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars))`

### D2. Bubble setContent 顺序

**当前问题**:
```kotlin
init {
    bubbleView.setViewCompositionStrategy(...)
    bubbleView.setContent { BubbleContent(...) }  // 1. view 未 attach,parent context 为 null
    bubbleView.setOnTouchListener { ... }
}
fun show() {
    wm.addView(bubbleView, params)  // 2. attach,但 Composition 已"卡死"
}
```

**修复**:
```kotlin
init {
    bubbleView.setViewCompositionStrategy(...)
    bubbleView.setOnTouchListener { ... }
}
fun show() {
    try { wm.addView(bubbleView, params) }
    catch (e: Exception) { Log.w(TAG, "addView failed", e); return }
    // view 现在 attached,parent context 可用,setContent 是安全的
    bubbleView.setContent {
        BubbleContent(...)
    }
    // ... 同步状态
}
```

**为什么这样改**:
- `setContent` 内部 `setParentCompositionContext()` 在 attach 后能找到 `viewTreeLifecycleOwner` (虽然 WindowManager 视图的 owner 是 null,但 `setContent` 的 fallback 是 OK 的 — 关键是要在 attach 之后调)
- attach 后 `setContent` 创建的 Composition 有有效的 view tree,能正确 measure/layout/draw
- `try/catch` 缩小到 `addView` 单独,setContent 失败也会被 catch

**验证**: view 真的绘制出 gradient box,有倒计时数字和按钮

### D3. 短时长用 Handler,长时长保留 AlarmManager

**当前问题**:
```kotlin
am.setExactAndAllowWhileIdle(...)  // 精确(需 canScheduleExactAlarms)
am.setAndAllowWhileIdle(...)       // 降级(15 分钟批处理,5 秒不触发)
```

**修复**:
```kotlin
private fun scheduleEnd(durationMs: Long) {
    if (durationMs <= 5 * 60 * 1000L) {  // ≤ 5 min
        // Handler-based,进程内可靠
        handler.removeCallbacks(endRunnable)
        handler.postDelayed(endRunnable, durationMs)
    } else {
        // 长时长用 AlarmManager
        scheduleAlarmEnd(durationMs)
    }
}

private val endRunnable = Runnable {
    val state = repo.state.value
    if (state.status !is TimerStatus.Counting) return@Runnable
    triggerLockNow()
}
```

**为什么 5 分钟边界**:
- 5 min 是用户对"短时间定时器"的合理上限
- 短于 5 min 用户预期"立即"响应,Handler 完全够用(不依赖 OS scheduler)
- 超过 5 min 用户对"误差几秒"不敏感,AlarmManager 仍可用

**为什么不用 `setAlarmClock`**:
- `setAlarmClock` 会永久显示"已设置闹钟"在 status bar
- 适合"闹钟 app",不适合"定时器 app"
- 用户体验上,定时器不需要"系统已知道"指示

**Handler-based 风险**:
- 用户在锁屏后系统杀 Service:不触发(用户已锁屏,无需再锁)
- 用户强杀进程:不触发(用户主动放弃)
- 设备休眠:Service 是 foreground,系统不允许休眠期间被强杀,Handler 会触发

**为什么不用 WorkManager**:WorkManager 短任务(≤ 10 min)被限制,精度差

## Risks / Trade-offs

- **[风险] setContent 顺序颠倒可能引入新 bug** → 单元测试 + 冒烟测试验证(原 polish 8 步 + 1 步新:气泡可见)
- **[风险] Handler 在 Service 销毁时不触发** → `onDestroy` 显式 `handler.removeCallbacks(endRunnable)`,避免 stale callback
- **[风险] insets 处理影响 NavHost 切换动画** → 用 `windowInsetsPadding` 是 Compose 标准做法,无已知问题
- **[权衡] 5 min 边界是硬编码** → 后续可改为可配置,或根据 `canScheduleExactAlarms()` 动态选
- **[权衡] 短时长路径在 Service 死亡时丢失锁屏** → 已 v1 verify 报告中标记为已知限制,不在本 fix 范围

## Migration Plan

无 — 同分支修改,无破坏性变更。

## Open Questions

- Q1: 5 min 阈值是否合适?倾向 5 min 覆盖"睡前 5 分钟""会议 30 分钟""健身 1 小时"等典型场景
- Q2: insets 改为 `systemBars` 还是仅 `statusBars`?倾向 `systemBars` 完整
- Q3: 是否在 lock 失败时 toast 提示?本 fix 不加(避免打扰用户,真要诊断看 logcat)
