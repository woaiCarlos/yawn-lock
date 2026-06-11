# Proposal: yawn-lock-bugfix-1 — 3 个运行时 Bug 修复

## Why

打完哈欠 v1.1 归档后真实使用暴露 3 个运行时 bug,均在 polish 阶段未被发现:

1. **状态栏遮挡** — 整个 APP 页面被状态栏挡住,内容应该下移
2. **授权后无悬浮窗** — 授权成功 → 点"开始计时" → 跳到桌面,**没有任何悬浮窗**(没有倒计时 UI 也没有控制按钮)
3. **5 秒计时后不锁屏** — 设 5 秒 → 点开始 → 5 秒过去,**屏幕没锁**

这 3 个 bug 严重影响可用性,必须修复后才能视为 v1.2 可用。

## What Changes

### Bug 1: 状态栏遮挡(insets 未消费)

**根因**:
- `MainActivity` 调 `enableEdgeToEdge()` 让内容延伸到 statusBar 区域
- `themes.xml` 配 `statusBarColor=@color/transparent` + `windowLightStatusBar=true`(透明状态栏)
- 但 `Surface(modifier = Modifier.fillMaxSize())` 或 `NavHost` **没消费 `WindowInsets.statusBars`**
- 结果:Timer/Permissions 屏的 HeroCard/StatusCard 顶到屏幕最上 0 像素,被状态栏文字/图标遮住

**修复**:
- 在 `MainActivity` 的 `setContent` 中,根 Composable 套 `Modifier.windowInsetsPadding(WindowInsets.statusBars)`(或 `WindowInsets.systemBars` 同时处理底部导航栏)
- 这让整个内容区域从 statusBar 下方开始绘制,符合 Android edge-to-edge 标准

**文件**:`MainActivity.kt` (1 处)

### Bug 2: 授权后点开始,桌面无悬浮窗

**根因**:
- `FloatingBubbleController.init` 在 view **未 attach** 到任何 window 时就调 `bubbleView.setContent { ... }`
- 此刻 `findViewTreeLifecycleOwner()` 返回 null,`setContent` 创建的 Composition 没有有效的 parent context
- 之后 `show()` 调 `wm.addView(bubbleView, params)` 触发 attach,理论上会调用 `setParentCompositionContext`,但 **这并不会触发已经在 init 中创建的 Composition 重新跑**
- 实际表现:`addView` 成功(view 添加到 WindowManager),但 **Composition 没真正渲染**(因为 setContent 时缺 parent,attach 时已创建的 Composition 状态错乱)
- 用户的 `try/catch (Exception)` 吞掉了所有异常,失败信息被吞,无 log

**修复**:
- 颠倒初始化顺序:
  - `init`: 只设 `setViewCompositionStrategy` 和 `setOnTouchListener`
  - `show()`: 先 `wm.addView`,再 `setContent`(此时 view 已 attach,parent context 可用)
- 增加 `Log.w` 日志(失败时能看到原因,不是只静默吞)
- 保留 `try/catch (Exception)` 但缩小范围为 `wm.addView` 单独,避免 setContent 失败被吞

**文件**:`FloatingBubbleController.kt` (1 处)

### Bug 3: 5 秒计时后不锁屏

**根因**:
- `CountdownService.scheduleAlarm` 使用 `setExactAndAllowWhileIdle`
- 当 `am.canScheduleExactAlarms()` 返回 false 时,**回退到 `setAndAllowWhileIdle`**
- **关键问题**:`setAndAllowWhileIdle` 在系统层面有 **15 分钟批处理窗口**(per app) — **5 秒不会触发,系统会延迟到下个批处理窗口**
- `canScheduleExactAlarms()` 返回 false 的原因可能是:
  - 设备 OEM 未自动授权 `USE_EXACT_ALARM`(虽然 manifest 声明了,但部分国产 ROM 不严格遵守)
  - 用户的 Android 12+ 设备在"特殊应用权限 → 闹钟和提醒"中未手动开启
  - **US 之外的某些 ROM 行为不一致**

**修复**:
- 引入**混合调度**:
  - **短时长(≤ 5 min)**: 用 `Handler.postDelayed` 在 Service 进程内调度,直接调 `dpm.lockNow()` — 跨进程可靠、无批处理延迟、无需权限
  - **长时长(> 5 min)**: 保留 `setExactAndAllowWhileIdle`(能精确触发的前提是 `canScheduleExactAlarms()` true;否则降级到 `setAndAllowWhileIdle`,长时长用户可容忍分钟级误差)
- 保留 `Handler.removeCallbacksAndMessages` 在 `handlePause`/`handleStop`/`onDestroy` 中清理
- 添加 `Log.d`/`Log.w` 日志,失败时可诊断

**为什么不用 `setAlarmClock`**:它会显示系统"已设置闹钟"状态栏 UI,适合闹钟 app,不适合定时器(混淆用户)

**文件**:`CountdownService.kt` (1 处,`scheduleAlarm` 改造)

## Impact

- **修改文件**:3 个(均已存在,无新文件)
  - `app/src/main/kotlin/com/example/yawnlock/MainActivity.kt`
  - `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt`
  - `app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt`
- **新依赖**:无
- **资源**:无
- **风险**:
  - Handler-based 短时长调度在用户强杀 Service 时不触发(用户主动杀进程=主动放弃锁屏),可接受
  - setContent 顺序颠倒需实测确认不引入新 bug(理论上更安全,因为 view 已 attach)
  - Insets 处理只对已 enableEdgeToEdge 的 Activity 有效(v1.1 已配置,无回归)

## Capabilities

### New Capabilities

无

### Modified Capabilities

无(spec 已有,但实现未到位;这是行为修复,不改 spec 契约)

## Non-Goals

- 不动 `floating-countdown-widget` spec 行为契约
- 不做 alarm 失败的用户引导(若 `canScheduleExactAlarms()` false 仍走 Handler 路径,长时长降级 `setAndAllowWhileIdle`,不打扰用户)
- 不做图标替换/字符串迁移(沿用 v1.1 留的 polish 任务)
- 不为 Service 添加前台唤醒锁(Handler.postDelayed 在主线程 tick,Service 自身已持有)
