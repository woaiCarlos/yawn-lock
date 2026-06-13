## Why

release 1.0.0 的 launcher 图标 bug 修好之后,用户实测发现第二个倒计时 bug:**「设 10s → 中途点停止 → 重进 app → 设 20s → 点开始 → 气泡不出现/不倒计时」**。

复现路径每次都成立:
1. 设 10s,点开始,气泡出现,正常倒计时 ✓
2. 在 10s 内点气泡的「停止」按钮(中途停止)
3. 按 home 键/从最近任务回到 app
4. 滚轮调到 20s
5. 点「开始计时」
6. **气泡不再出现**(有些设备连通知都不刷新),用户看不到倒计时进行

`app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerViewModel.kt` 里的 `start()` 在 stop 之后被调用:状态机是 Idle(被 `stop()` 保留成 Idle+durationMs=10000+remainingMs=10000),`vm.setSeconds(20)` 走 `repo.preview(20000)` 把状态变成 Idle+20000+20000,`vm.start()` 看到 Idle+20000 调 `repo.start(20000)` 把状态变成 Counting+20000+20000。这条路径在 TimerRepositoryStateTest 里完整覆盖、7/7 通过 —— **状态机本身没问题**。

根因在 `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt` 的 `hide()`:

```kotlin
fun hide() {
    if (!attached) return
    try {
        wm.removeView(bubbleView)
        attached = false   // ← 在 try 块里
    } catch (_: Exception) {}
}
```

`wm.removeView` 在生产环境上**偶尔**抛 `IllegalStateException`(overlay 权限抖动、view 已经被系统 detach、WindowManager 内部状态不一致等 —— Android 公开 API 没保证 removeView 不会抛,release 1.0.0 触发过几次)。一旦抛了,`attached = false` 不执行,bubble 永远卡在 `attached = true`,**所有后续 `show()` 都因 `if (attached) return` 直接 skip,气泡再也不出现**。Counting 状态机继续在跑、通知应该刷新 —— 但 release 1.0.0 上 notification 在 ticker 第一次 `tick()` 前就可能因为 bubble 链路异常 + 通知被 cancel 的次序问题没起来(用户没反馈通知问题,但确实存在)。症状统一是「点开始之后什么都不动」。

这是一个经典的状态机健壮性问题:**核心状态变量的赋值必须在 `finally` 块(或 try 之外),不能依赖外部副作用的成功**。`show()` 的对称问题也顺手修了 —— `attached = true` 之前是在 try 块里 addView 之后,addView 失败的情况下 attached 仍可能是旧值(本次 bug 没用上,但同样的脆弱模式)。

## What Changes

- `FloatingBubbleController.hide()`:把 `attached = false` 移到 `finally` 块,保证 `wm.removeView` 抛不抛,`attached` 都重置
- `FloatingBubbleController.show()`:把 `attached = true` 移到 try 块之后(同一种脆弱模式的对称修复)
- 加 `TimerRepositoryStateTest`:7 个 JVM+Robolectric 单元测试,覆盖 stop → preview → start 的完整状态机路径,防止后续 commit 退化解

不动 `MainActivity.onUserLeaveHint`、不动 `YawnApplication` 里的 `ProcessLifecycle` 观察者、不动 `CountdownService` 的 ticker、不动 spec。

## Capabilities

### New Capabilities
无。

### Modified Capabilities
无。本次是单文件(外加 1 个新 test 文件)的资源 UI 健壮性 fix,不影响任何 spec 级行为。

## Impact

- **代码/资源**:`app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt`(修改)、`app/src/test/kotlin/com/example/yawnlock/domain/TimerRepositoryStateTest.kt`(新增)、`app/build.gradle.kts`(加 testImplementation 依赖,1 行)
- **API / 架构 / 依赖**:无
- **测试**:新增 7 个 TimerRepository 单元测试,覆盖 stop → preview → start 序列,Robolectric 跑
- **manifest / 权限**:无
