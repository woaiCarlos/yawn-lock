# Proposal: yawn-lock-bugfix-2 — 基于实际根因的运行时 Bug 修复

## Why

打完哈欠 v1.1 归档 + bugfix-1 修复后,用户测试发现:
- Bug 1(状态栏遮挡)— ✅ 已修
- Bug 2(无悬浮窗)— ❌ **仍不显示**
- Bug 3(5 秒不锁屏)— ❌ **仍不锁**

bugfix-1 的修复不生效。需要进一步定位根因并修复。

经网络调研(Stack Overflow, Android 官方文档, CSDN/掘金) + 当前代码审计,发现 3 个独立的真实根因(在 bugfix-1 中被错误归因或未触及):

## What Changes

### 根因 1: 气泡不显示(ComposeView 缺 ViewTreeLifecycleOwner)

**bugfix-1 的错误诊断**:"setContent 在未 attach 时调,composition 缺 parent context"

**真正的根因**:`FloatingBubbleController` 通过 `LayoutInflater.inflate(R.layout.floating_bubble)` 拿到一个 `ComposeView`,然后 `wm.addView()` 到 WindowManager。WindowManager 视图**不在任何 ViewTree 中**(没有 Activity,没有 Fragment,没有 Window),因此 `setContent` 内部 `setParentCompositionContext()` 调用 `findViewTreeLifecycleOwner()` 返回 null,Compose 抛 `IllegalStateException: ViewTreeLifecycleOwner not found`。即使 view 被 addView 成功挂到 WindowManager,composition 状态错乱,Composable 永远不渲染。

**证据**:
- `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt:73-76` init 块只设策略和 listener
- `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt:88-104` show() 调 addView → setContent
- 即使 addView 成功(日志没异常),气泡仍不可见

**修复**:
- 在 `FloatingBubbleController.init` 块里,给 `bubbleView` 显式注入 `ViewTreeLifecycleOwner`(自定义 `LifecycleRegistry`, `currentState = RESUMED`)
- 同样注入 `ViewTreeSavedStateRegistryOwner`(用 `SavedStateRegistryController.createHandle()`)
- 把 `bubbleView` 嵌入一个 `FrameLayout` 包装(给 bubble view 固定 size + 一个 fixed background color,即使 Compose 失败也能看到一个"实体"方块,方便诊断)

### 根因 2: 5 秒后不锁屏(用户授权未完成 OR LockReceiver intent-filter 缺失)

**bugfix-1 的错误诊断**:"setAndAllowWhileIdle 有 15 分钟批处理窗口"

**真正的根因(2 个,任一触发都导致锁屏失败)**:

**(a) 用户授权未完成**:用户说"授权成功后点击开始计时",但 Android 设备管理员授权需要用户在系统弹窗中**明确点"激活"**才算完成。若用户取消弹窗或被弹窗外的某事件中断,`DevicePolicyManager.isAdminActive()` 返回 false。

`CountdownService.triggerLockNow` 检查 `isAdminActive` — 若 false,**跳到 `LockedFallbackActivity`**(`v1.1 polish` 时是 stub,无 UI)。用户看到黑屏,以为"没锁屏",**实际上是走了 fallback 路径,锁屏代码根本没执行**。

**证据**:
- `app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt:37-48` triggerLockNow 检查 isAdminActive,降级到 LockedFallbackActivity
- `app/src/main/kotlin/com/example/yawnlock/service/LockedFallbackActivity.kt` 是 stub 无 UI
- `app/src/main/kotlin/com/example/yawnlock/data/PermissionChecker.kt` 检查 isAdminActive 用于 UI 显示

**(b) LockReceiver 无 `<intent-filter>`**:即使 (a) 没问题,AlarmManager 调度也可能在某些 Android 版本上失效。

**证据**:
- `app/src/main/AndroidManifest.xml:51-53` LockReceiver 仅 `<receiver android:name=".service.LockReceiver" android:exported="false" />` 无 intent-filter
- 虽然显式 Intent(带 ComponentName)在大多数设备上 OK,但部分 Android 8+ ROM 对无 intent-filter 的 manifest receiver 有过滤行为

**修复**:
- **(a)** 在 `CountdownService.handleStart` 启动时,加详细诊断日志:`Log.d(TAG, "isAdminActive=" + dpm.isAdminActive(admin) + ", canDrawOverlays=" + Settings.canDrawOverlays(this))` — 让用户能通过 `adb logcat` 看到授权状态
- **(a)** 若 `!isAdminActive`,改 fallback 行为:发一个**系统 Toast** 或**通知**告诉用户"设备管理员未授权,锁屏将无法执行,请前往权限页重新授权"
- **(a)** 实现 `LockedFallbackActivity` 实际 UI(显示"已经锁屏"全屏),不再黑屏
- **(b)** 给 `LockReceiver` 加 `<intent-filter>` 声明(防御性,显式 Intent 仍优先)

### 根因 3(跨切面):缺少诊断日志

**前 2 个根因中,我的 bugfix-1 修复失败的根本原因是:缺乏详细的运行时日志,导致无法确诊。**

**修复**(贯穿所有文件):
- 给关键路径加 `Log.d/w`:PermissionChecker.check、TimerViewModel.setSeconds、CountdownService.onStartCommand/handleStart/endRunnable/triggerLockNow、FloatingBubbleController.show、LockReceiver.onReceive
- 关键 throw 点加 `Log.e` 而非 `try { } catch (_) {}` 静默吞

## Capabilities

### New Capabilities

无

### Modified Capabilities

无(spec 行为契约不变;这是行为修复)

## Impact

- **修改文件**:
  - `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt` (LifecycleOwner 注入)
  - `app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt` (诊断日志 + fallback Toast)
  - `app/src/main/kotlin/com/example/yawnlock/service/LockedFallbackActivity.kt` (实现 UI,替换 stub)
  - `app/src/main/kotlin/com/example/yawnlock/data/PermissionChecker.kt` (Log 状态)
  - `app/src/main/AndroidManifest.xml` (LockReceiver intent-filter)
  - `app/src/main/res/values/strings.xml` (新增 fallback 文案)

- **新依赖**:无
- **API 变更**:无
- **风险**:
  - LifecycleRegistry 注入到 ComposeView 是非标准用法,需实测稳定性
  - Toast 在 Service 上下文中可能不显示(系统限制)→ 用 NotificationChannel 替代
  - Log.d 输出可能包含 PII(无敏感数据,只有状态 flag)

## Non-Goals

- 不改 spec 契约
- 不动现有功能(状态栏、UI 调整、preset/slider/CTA)
- 不做真机验证(留给用户)
- 不改 Polish 阶段的 polish 建议项(沿用 v1.1 verify 的 follow-up)
