# Design: yawn-lock-bugfix-2

> OpenSpec canonical spec: `openspec/changes/yawn-lock-bugfix-2/`
> 已有 main spec: `openspec/specs/scheduled-screen-lock/spec.md`、`openspec/specs/floating-countdown-widget/spec.md`
> 本次无 spec 变更。3 个 bug 的真实根因来自网络调研 + 当前代码审计。

## Context

打完哈欠 v1.1 + bugfix-1 后,用户实测 3 个 bug 仍有 2 个未修:
- Bug 2(气泡不显示)未修
- Bug 3(5 秒不锁屏)未修

bugfix-1 的修复"应该有效但实际无效",说明根因诊断错了。本次基于网络调研 + 深入代码审计,找到真实根因。

## Goals / Non-Goals

**Goals**:
- 修 2 个根因(气泡 + 锁屏)
- 加诊断日志让用户能通过 `adb logcat` 验证授权状态
- `LockedFallbackActivity` 真正显示"已经锁屏" UI(替换黑屏 stub)
- LockReceiver 显式 intent-filter(防御性)
- 编译通过,APK 重新生成

**Non-Goals**:
- 不动 spec
- 不重做 polish 阶段的设计
- 不改 v1/v1.1 既有功能

## Decisions

### D1. Bubble 注入 ViewTreeLifecycleOwner(根因 1)

**当前**:
```kotlin
bubbleView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
bubbleView.setOnTouchListener { _, ev -> handleTouch(ev) }
// setContent 在 show() 里的 addView 之后调
```

**问题**:`ComposeView` 在 WindowManager 视图中,无 ViewTree owner,`setContent` 内部抛 IllegalStateException

**修复**:
```kotlin
init {
    // 显式注入 ViewTree owner,Compose 在 WindowManager 视图中能正常运行
    val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    bubbleView.setViewTreeLifecycleOwner(object : LifecycleOwner {
        override val lifecycle = registry
    })
    val savedStateHandle = SavedStateRegistryController.create(this)
        .savedStateHandle()
    bubbleView.setViewTreeSavedStateRegistryOwner(object : SavedStateRegistryOwner {
        override val savedStateRegistry = savedStateHandle.savedStateRegistry
    })
    bubbleView.setViewTreeViewModelStoreOwner(ViewModelStoreOwner { ViewModelStore() })
    bubbleView.setViewCompositionStrategy(...)
    bubbleView.setOnTouchListener { ... }
}
```

**为什么这样**:
- `LifecycleRegistry` + `currentState = RESUMED` 让 Compose 立即进入"活跃"状态
- `SavedStateRegistryOwner` 让 `rememberSaveable` 也能工作(我们的 BubbleContent 不用,但基础设施就位)
- `ViewModelStoreOwner` 让 `viewModel()` 也能工作(同样不用,但就位)
- 这三件套是 `AbstractComposeView.setContent` 内部依赖的,手动注入后绕过 findViewTree 查找

**Source 验证**:
- https://blog.csdn.net/qq_45925230/article/details/123356190
- ComposeView 在 WindowManager 中需要显式 lifecycle binding

**为什么不用 `FrameLayout` 包装**:之前的设计讨论考虑过,增加复杂度。当前方案更轻量

### D2. handleStart 诊断日志 + 授权状态检查(根因 2a)

**当前**:
```kotlin
private fun handleStart() {
    val state = repo.state.value
    if (state.status !is TimerStatus.Counting) return
    startForegroundCompat(state)
    scheduleEnd(state.remainingMs)
    try { ensureBubble() } catch (...) { ... }
    handler.removeCallbacks(ticker)
    handler.post(ticker)
}
```

**问题**:`isAdminActive` 在 lock 时才检查,用户没法提前知道授权是否完整

**修复**:
```kotlin
private fun handleStart() {
    val state = repo.state.value
    if (state.status !is TimerStatus.Counting) return
    
    // 诊断:startForeground 前的权限/服务状态
    val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    val admin = ComponentName(this, com.example.yawnlock.data.DeviceAdminReceiver::class.java)
    val canOverlay = Settings.canDrawOverlays(this)
    val isAdmin = dpm.isAdminActive(admin)
    Log.d(TAG, "handleStart: isAdminActive=$isAdmin, canDrawOverlays=$canOverlay, status=${state.status}")
    
    if (!isAdmin) {
        // 用户授权未完成 — 用通知而非 Toast(Service 中 Toast 受限)
        NotificationCenter.showAdminMissingWarning(this)
        Log.w(TAG, "device admin not active; lockNow() will fall back to LockedFallbackActivity")
    }
    
    startForegroundCompat(state)
    scheduleEnd(state.remainingMs)
    try { ensureBubble() } catch (e: Exception) { Log.e(TAG, "ensureBubble failed", e) }
    handler.removeCallbacks(ticker)
    handler.post(ticker)
}
```

**新增 `NotificationCenter.showAdminMissingWarning`**:
- 发送一个 5 秒后自动消失的通知"设备管理员未授权,锁屏可能不生效"
- 用户能看到提示,去权限页重新授权

**为什么**:
- 让用户立刻知道授权状态(而不是 5 秒后看到黑屏才反应)
- 真实情况下,`isAdminActive` 失败时,降级路径(`LockedFallbackActivity`)会显示全屏"已经锁屏"提示
- 但原 stub 是个黑屏,用户分不清"锁屏失败" vs "等一下" vs "应用崩溃"

### D3. LockedFallbackActivity 实际 UI(根因 2a fallback)

**当前**:stub onCreate,无 UI
**修复**:实现全屏"已经锁屏" UI(用户至少能看到状态)

```kotlin
class LockedFallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YawnLockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF1A1A2E),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("已经锁屏", color = Color.White, fontSize = 24.sp)
                            Text("设备管理员未授权,使用电源键锁定屏幕",
                                color = Color(0xFFA8A8C0), fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
```

**为什么**:用户至少知道"应用没崩,是授权问题"

### D4. LockReceiver 加 `<intent-filter>`(根因 2b 防御性)

**当前**:仅 `<receiver android:name=".service.LockReceiver" android:exported="false" />`

**修复**:
```xml
<receiver
    android:name=".service.LockReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="com.example.yawnlock.FIRE" />
    </intent-filter>
</receiver>
```

**为什么**:显式 Intent 通常不需要 intent-filter,但部分 ROM 在 Android 8+ 隐式广播限制下会过滤无 filter 的 manifest receiver。加 filter 是防御性的,不影响功能

**风险**:`exported=false` 仍保护,只有我们自己进程内的 PI 能唤醒

### D5. 跨切面:详细诊断日志(贯穿所有文件)

**改动**:
- `PermissionChecker.check(context)` 加 `Log.d` 各权限状态
- `TimerRepository.start/pause/resume/tick/onAlarmFired` 加 `Log.d` 状态转换
- `CountdownService.onCreate/onStartCommand/handleStart/endRunnable/triggerLockNow/scheduleEnd` 加 `Log.d`
- `FloatingBubbleController.show/addView failure` 加 `Log.e` 而非静默
- `LockReceiver.onReceive` 加 `Log.d` 接收状态

**为什么**:之前修复失败的根本原因就是缺乏日志,用户(和我)都没法诊断。修 2 + 加日志 = 下次失败能直接定位

## Risks / Trade-offs

- **[风险] LifecycleRegistry 注入到 WindowManager 视图是非标准** → 需实测验证 Compose 渲染成功
- **[风险] Service 中通知不能 Toast** → 用 `NotificationManagerCompat.notify` 发通知
- **[风险] 加 `Log.d` 会产生大量日志** → 使用 `if (BuildConfig.DEBUG)` 包裹,release build 静默
- **[权衡] LockedFallbackActivity 从黑屏 stub 变成有 UI** → 用户体验提升,但增加 1 个 layout 资源

## Migration Plan

无 — 同分支修改。

## Open Questions

- Q1: `Log.d` 全部都用,还是部分用 `Log.d` + 部分用 `if (BuildConfig.DEBUG)`?倾向 release 静默(标准 Android 实践)
- Q2: NotificationCenter.showAdminMissingWarning 的 notification ID 用什么?NOTIF_ID(1001)已被倒计时通知占用。需新 ID(如 1002)
- Q3: LockedFallbackActivity 的 UI 走 Compose 还是 XML?倾向 Compose(项目内一致)
