# Comet Design Handoff

- Change: yawn-lock
- Phase: design
- Mode: compact
- Context hash: 6b49d8be6aa16b7e67ac01bb6727a813d5d49ca0c747bd1673395e46cf542387

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/yawn-lock/proposal.md

- Source: openspec/changes/yawn-lock/proposal.md
- Lines: 1-55
- SHA256: 2a9fbf708069d4834453e63b92a93f55f31d8ba0fdd6373c1105344224e3c00c

```md
# Proposal: 打哈欠 (yawn-lock) — Android 定时锁屏应用

## Why

深夜用手机时一抬头已经凌晨 2 点,再抬头已经 4 点——这是几乎所有重度手机用户的痛点。系统自带的"定时开关机"和"健康使用手机"功能要么粗粒度(整机关闭)、要么需要绕开家长控制(未成年人模式),无法满足成年人"我只想在指定时长后强制锁屏让自己去睡"这一窄而真实的需求。

我们要构建的 **打哈欠** 是一个极简的 Android 应用:选一个时长(30 秒到 120 分钟),到点强制锁屏,期间可以在其他 App 上方显示一个可拖动的倒计时气泡。整个交互控制在 3 个屏幕以内,UI 借鉴原型的紫色"晚安"主题。

## What Changes

- **新增** Android 应用 `yawn-lock` (Kotlin + Jetpack Compose),包名 `com.example.yawnlock`
- **新增** 主计时器屏幕:快速预设(30s/1m/5m/10m)+ 自定义面板(±键 + 1-120min 滑块)
- **新增** 倒计时执行:通过 `WorkManager` 调度 + `AlarmManager` 精确触发;前台服务保持计时
- **新增** 到点锁屏:通过 `BIND_DEVICE_ADMIN` (设备管理员) 权限调用 `DevicePolicyManager.lockNow()`
- **新增** 跨 App 悬浮气泡:通过 `SYSTEM_ALERT_WINDOW` 权限,显示迷你倒计时 + 暂停/停止
- **新增** 权限引导页:列出"悬浮窗""设备管理员"两项必授权限,引导用户跳转系统设置
- **修改** N/A (全新项目,无既有代码)

## Capabilities

### New Capabilities

- `scheduled-screen-lock`: 选时长、倒计时、到点强制锁屏,以及相关权限管理(设备管理员)
- `floating-countdown-widget`: 跨 App 显示的可拖动/可收起悬浮倒计时气泡(系统级悬浮窗)

### Modified Capabilities

- 无 (本目录为全新项目,`openspec/specs/` 不存在)

## Impact

- **新建项目**:无既有代码影响
- **API/依赖**:
  - Android SDK 26+ (minSdk 26, targetSdk 34)
  - `androidx.work:work-runtime-ktx` (前台服务)
  - `androidx.compose.*` (UI)
  - `androidx.lifecycle:lifecycle-viewmodel-compose` (ViewModel)
  - 无第三方三方依赖 (不引入 Hilt/Room/Retrofit 等,保持 v1 极简)
- **权限**:
  - `SYSTEM_ALERT_WINDOW` — 悬浮窗
  - `BIND_DEVICE_ADMIN` — 设备管理员(锁屏)
  - `POST_NOTIFICATIONS` — 前台服务通知 (Android 13+)
  - `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — 倒计时前台服务
- **首次安装路径**:用户开 App → 看到"必授权限"提示 → 分别跳转系统设置授予 → 返回后可见计时器
- **边界场景**:
  - 锁屏后用户按电源键唤醒:屏幕确实被锁(电源键开屏需要解锁)
  - 倒计时中用户卸载 App:卸载需先取消设备管理员
  - 设备重启:AlarmManager 会被清空,目前 v1 不做持久化恢复(下次启动重新开始)
- **非目标**:
  - 勿扰模式自动静音
  - 自定义预设记忆
  - 多语言(仅 zh-CN)
  - 锁屏密码/指纹保护(系统自带即可)
  - 熄屏曲线 / 智能省电联动
  - iOS / 鸿蒙版本
```

## openspec/changes/yawn-lock/design.md

- Source: openspec/changes/yawn-lock/design.md
- Lines: 1-159
- SHA256: d861a2dc0d61aeef86a59b9afe4909179f9246d63167d71fc86d16fc07d687cf

[TRUNCATED]

```md
# Design: 打哈欠 (yawn-lock) — Android 定时锁屏

## Context

全新项目,工作目录 `打哈欠/` 当前仅有 `Web-Prototype/` 子目录下的 3 个 HTML 视觉原型(`timer.html` / `overlay.html` / `floating.html`)。本次构建首个可运行的 Android 应用版本(v1)。

**约束**:
- 工作环境 macOS (开发机),无 Android Studio 本地配置,需用 CLI 工具链(Gradle Wrapper + JDK 17)生成 APK
- 包名占位 `com.example.yawnlock`(上线前可改)
- v1 极简原则:不引入 DI 框架、不引入数据库、不引入网络层
- 目标用户:Android 8.0+ (API 26)

**利益相关方**:
- 终端用户:重度手机用户,需要"强制自己放下手机"的心理钩子
- 开发:本项目作者(单兵)
- 系统:Android 权限系统(尤其 Device Admin、悬浮窗的强提示)

## Goals / Non-Goals

**Goals**:
- v1 在真机/模拟器上完整跑通"选时长 → 倒计时 → 到点锁屏 → 唤醒"主流程
- 悬浮气泡可在其他 App 上拖动/收起,符合原型交互
- 权限引导路径清晰:用户首次启动即可在 ≤3 次跳转内授予所有必选权限
- APK 体积 < 5 MB
- 单模块、零三方业务依赖(仅 Compose + WorkManager 等 AndroidX)

**Non-Goals**:
- 勿扰模式自动静音、熄屏曲线、自定义预设记忆(已写进 proposal 的非目标)
- 多语言(仅 zh-CN)
- 后台统计、远程推送、应用商店发布
- 单元/UI 测试覆盖度目标(本版本不写测试,但代码结构应利于后期补测)

## Decisions

### D1. 锁屏机制: DevicePolicyManager.lockNow()

**选型**: 通过 `BIND_DEVICE_ADMIN` 注册为设备管理员后调用 `DevicePolicyManager.lockNow()`。

**为什么**:
- 这是 Android 唯一**任何普通 App 都能调用且能立即锁屏**的官方 API
- 不需要 root、不需要 Accessibility 权限(后者需要用户额外信任"无障碍服务"敏感权限)
- 用户可随时在系统设置 → 安全 → 设备管理应用中撤销,符合"用户可控"

**替代方案对比**:
- AccessibilityService:可模拟电源键,但 1) 用户授权门槛极高,2) Play Store 审核严格
- KeyguardManager.createConfirmDeviceCredentialIntent:仅弹解锁界面,不真锁屏
- Root 命令:不可行,产品面向普通用户

**约束**:
- 卸载前必须先 `DevicePolicyManager.removeActiveAdmin()`(Android 系统强制),需在 App 内提供"取消管理员"入口

### D2. 倒计时执行: Foreground Service + AlarmManager.setExactAndAllowWhileIdle

**选型**: 倒计时开始时启动前台服务(`Service.startForeground()`),服务内 `AlarmManager.setExactAndAllowWhileIdle()` 在指定时刻触发 `PendingIntent` 调用 `lockNow()`。

**为什么**:
- 后台服务在 Android 8+ 限制下,只有"前台服务"类型能长时间运行
- `setExactAndAllowWhileIdle` 比 `set` 精度高,且能绕过 Doze 模式(必要,因为我们需要"到点"的硬保证)
- Android 14+ 要求声明 `FOREGROUND_SERVICE_SPECIAL_USE` 权限

**替代方案对比**:
- WorkManager.OneTimeWorkRequest:不够精确(分钟级),不满足"30 秒"等短时长
- 纯 Handler.postDelayed:进程被杀立即失效
- JobScheduler:同 WorkManager 问题

**注意**: Android 14+ `USE_EXACT_ALARM` 权限受 Play Store 审核约束。我们用 `setExactAndAllowWhileIdle` + `SCHEDULE_EXACT_ALARM`(用户可拒绝)。若拒绝,降级到 `setAndAllowWhileIdle`(允许 ±5 分钟误差)。

### D3. 悬浮气泡: WindowManager + TYPE_APPLICATION_OVERLAY

**选型**: 启动前台服务后,通过 `WindowManager.addView()` 添加 `TYPE_APPLICATION_OVERLAY` 类型的自定义 View。`onTouchListener` 处理拖动手势,接近右边缘自动收起为小药丸(`width=36dp`)。

**为什么**:
- `TYPE_APPLICATION_OVERLAY` 是 API 26+ 唯一合法的"跨 App 悬浮窗"窗口类型
- 需用户先授予 `SYSTEM_ALERT_WINDOW`(在系统设置 → 特殊应用权限 → 显示在其他应用上方)
- Compose 暂未原生支持嵌入 WindowManager,采用 `ComposeView` + AndroidView 包装

**手势设计**(沿用原型):
- 拖动距离 < 3px:视为点击
- 距离右边缘 < 28px:展开"贴边提示"
- 释放时若 right < 36px:收起为小药丸
```

Full source: openspec/changes/yawn-lock/design.md

## openspec/changes/yawn-lock/tasks.md

- Source: openspec/changes/yawn-lock/tasks.md
- Lines: 1-74
- SHA256: e1350ff507993e6d2dc4f5dde0dcdd7937e5ab9c7ca15fe99a384dd497aff263

```md
# Tasks: 打哈欠 (yawn-lock) v1

> 任务清单 — 顺序按依赖排列,每个任务可在一个 session 内完成。
> 全部任务在 Android Studio / Gradle CLI 环境 (JDK 17, AGP 8.2+) 下执行。

## 1. Project Scaffold

- [ ] 1.1 用 `gradle init` 或手写生成 Android Gradle 项目结构,包名 `com.example.yawnlock`,`minSdk 26`, `targetSdk 34`
- [ ] 1.2 配置 `gradle/libs.versions.toml`:Compose BOM、Lifecycle、Navigation Compose、WorkManager、Material 3
- [ ] 1.3 写 `app/src/main/AndroidManifest.xml`:声明 `MainActivity`、`CountdownService`、`DeviceAdminReceiver`、`LockReceiver`,声明权限 `SYSTEM_ALERT_WINDOW` / `BIND_DEVICE_ADMIN` / `POST_NOTIFICATIONS` / `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `SCHEDULE_EXACT_ALARM`
- [ ] 1.4 写 Material 3 主题:复用原型的紫色调(`#6750A4` accent),浅色主题,定义 `Color.kt`/`Theme.kt`/`Type.kt`
- [ ] 1.5 准备资源:启动图标(🌙 占位即可)、月/星/锁的 vector drawable、`strings.xml` 中文文案

## 2. Permission Module

- [ ] 2.1 实现 `PermissionChecker`:查询 `Settings.canDrawOverlays()` / `DevicePolicyManager.isAdminActive()` / 通知权限(API 33+)
- [ ] 2.2 实现 `PermissionsScreen` Composable:参照 `overlay.html` 的列表样式(icon + name + desc + 状态徽章),点击行跳转系统设置/激活设备管理员
- [ ] 2.3 实现 `PermissionsViewModel`:暴露 3 个权限的当前状态,`refresh()` 在 `onResume` 调用
- [ ] 2.4 配置 `NavHost`:timer 屏幕与 permissions 屏幕的路由
- [ ] 2.5 设备管理员激活:写 `DeviceAdminReceiver` 子类,在 manifest 中声明 `BIND_DEVICE_ADMIN` 和 `device-admin` 资源文件 `res/xml/device_admin_policies.xml`
- [ ] 2.6 悬浮窗引导:`ACTION_MANAGE_OVERLAY_PERMISSION` 跳转 + 启动 Intent,返回后 `refresh()` 状态

## 3. Timer Core (Domain)

- [ ] 3.1 实现 `TimerState` data class:`durationMs`、`remainingMs`、`status` (Idle/Counting/Paused/Finished) + 派生属性 `progress: Float`
- [ ] 3.2 实现 `TimerRepository` 单例(放在 `YawnApplication` 里):`StateFlow<TimerState>`,方法 `start(durationMs)` / `pause()` / `resume()` / `stop()` / `tick()`
- [ ] 3.3 在 `YawnApplication.onCreate()` 初始化 Repository,持有 `CountdownService` 的 binder 引用

## 4. Timer Screen (UI)

- [ ] 4.1 实现 `TimerScreen` Composable:原型 `timer.html` 的布局(hero + 预设 chips + 自定义 dial + 状态 card)
- [ ] 4.2 预设 chips(30s / 1m / 5m / 10m),`+`/`−` 圆形按钮,1-120min 滑块,数字与单位同步
- [ ] 4.3 状态 card:200dp 圆形 SVG ring + 倒计时数字 + `+5 分钟` / `暂停` / `停止` 按钮(在原型基础上,**删除** `+5 分钟` 按钮,见 design Q2 决策)
- [ ] 4.4 FAB 按钮:点击切换悬浮气泡显示/隐藏(通过 `FloatingBubbleController`)
- [ ] 4.5 "开始锁屏" CTA:必选权限未授权时点击跳转 permissions 屏幕,已授权时调用 `TimerRepository.start()`
- [ ] 4.6 `TimerViewModel`:包装 Repository,提供 UI 状态和事件回调

## 5. Countdown Service & Alarm

- [ ] 5.1 实现 `CountdownService` (前台服务):`onStartCommand` 启动前台 + 启动 `AlarmManager.setExactAndAllowWhileIdle`
- [ ] 5.2 实现 `LockReceiver` (`BroadcastReceiver`):收到闹钟后调用 `DevicePolicyManager.lockNow()`,然后停服务、停悬浮窗
- [ ] 5.3 实现服务内部倒计时 ticker(`Handler` 每 100ms `tick()`),同步更新 Repository 状态
- [ ] 5.4 暂停/恢复:取消旧 PendingIntent,重设 `setExactAndAllowWhileIdle` 用剩余时间
- [ ] 5.5 停止:取消 PendingIntent + `stopSelf()` + 通知消失
- [ ] 5.6 配置 Android 14 的 `FOREGROUND_SERVICE_SPECIAL_USE` + 在 manifest 提供 `<property>` 解释 service 用途

## 6. Foreground Notification

- [ ] 6.1 创建通知 channel (`YawnLockChannel`, IMPORTANCE_LOW,不响铃不震动)
- [ ] 6.2 构建通知:小图标(月亮)、`setContentTitle("打哈欠")`、内容 "剩余 03:45"、Stop action
- [ ] 6.3 每秒更新通知文本(`NotificationManagerCompat.notify` with same id)
- [ ] 6.4 Stop action 触发 `ACTION_STOP` PendingIntent,Receiver 调 `TimerRepository.stop()`

## 7. Floating Bubble

- [ ] 7.1 实现 `FloatingBubbleController`(单例,持有 `WindowManager.LayoutParams` 和 bubble view)
- [ ] 7.2 bubble 布局文件 `res/layout/floating_bubble.xml`:`ComposeView` 根,内含 200dp 圆角紫色面板、handle、moon icon、ring、time、两个按钮
- [ ] 7.3 通过 `AndroidView` 在 `ComposeView` 中渲染,`setContent { FloatingBubbleContent(...) }`
- [ ] 7.4 `onTouchListener`:实现 pointerdown/move/up 拖动逻辑(原型 `floating.html` 的算法)
- [ ] 7.5 边缘自动收起:在 `onTouch UP` 时若 `right < 36px` 切换为 collapsed 状态
- [ ] 7.6 collapsed 状态:宽度 36dp、仅显示 moon icon、点击展开
- [ ] 7.7 按钮事件:暂停/继续 → `TimerRepository.pause()/resume()`,停止 → `TimerRepository.stop()` 并 `removeView()`
- [ ] 7.8 与 `CountdownService` 联动:服务 `onCreate` 时 `bubbleController.show()`,`onDestroy` 时 `bubbleController.hide()`

## 8. Wire Up & Polish

- [ ] 8.1 `MainActivity`:`setContent { YawnLockTheme { NavHost(...) } }`,`onResume` 调用 `PermissionChecker.refresh()`
- [ ] 8.2 启动路径:首次启动时若权限缺失,自动跳到 permissions 屏幕;否则直接 timer
- [ ] 8.3 倒计时归零的"已经锁屏"全屏覆盖:在 `LockReceiver` 触发后,服务弹一个 `Activity` 全屏显示"按电源键唤醒"
- [ ] 8.4 设置页入口(可选,v1 最小):`MainActivity` 提供"取消设备管理员"按钮,长按/隐藏
- [ ] 8.5 关闭悬浮窗后再开 App:Repository 检测到状态 Counting 但服务没运行 → 优雅降级为停止状态(避免 service 漏挂)
- [ ] 8.6 ProGuard 规则:保留 Compose 反射元数据、保留 Receiver/Service 类名
- [ ] 8.7 构建 `assembleDebug` 通过,生成 APK
- [ ] 8.8 真机/Pixel 模拟器冒烟测试主流程(选时长→倒计时→悬浮窗→到点锁屏→电源键唤醒)
```

## openspec/changes/yawn-lock/specs/floating-countdown-widget/spec.md

- Source: openspec/changes/yawn-lock/specs/floating-countdown-widget/spec.md
- Lines: 1-120
- SHA256: c44985ffe4d8ef3ccb5d78494deb1886ba61a1f8fa535cd68c409e272c7a4dea

[TRUNCATED]

```md
# Spec: floating-countdown-widget (打哈欠悬浮倒计时气泡)

## ADDED Requirements

### Requirement: Display Floating Bubble During Countdown

The system SHALL display a floating bubble above other applications during an active countdown, provided the `SYSTEM_ALERT_WINDOW` (overlay) permission has been granted.

The bubble SHALL appear automatically when the countdown starts and SHALL be removed when the countdown ends, is stopped, or the screen is locked.

The bubble SHALL contain:
- A drag handle bar at the top (4dp tall, 36dp wide, semi-transparent)
- An app icon (moon) and "Sleepy Lock" label
- A circular progress ring indicating countdown progress
- The remaining time in `MM:SS` format (monospace digits)
- A "暂停 / 继续" (Pause / Resume) button
- A "停止" (Stop) button

#### Scenario: Bubble appears on countdown start
- **WHEN** a countdown starts and overlay permission is granted
- **THEN** a floating bubble is added via `WindowManager.addView()` with `LayoutParams.TYPE_APPLICATION_OVERLAY`
- **AND** the bubble is positioned in the upper-right region (top 35%, right 40dp)

#### Scenario: Bubble disappears on lock
- **WHEN** the device screen is locked by the app
- **THEN** the floating bubble is removed from the window manager within 200ms
- **AND** the bubble SHALL NOT be visible on the lock screen

### Requirement: Permission Gating for Floating Bubble

The system SHALL request the `SYSTEM_ALERT_WINDOW` (overlay) permission before allowing the user to start a countdown.

The permissions screen SHALL list the overlay permission with a state badge ("已授权" / "未授权") and a description "在其他 App 上方显示倒计时与控制气泡".

Tapping the overlay row SHALL open the system settings page for the app, where the user can toggle "显示在其他应用的上层".

#### Scenario: User grants overlay permission
- **WHEN** the user taps the overlay row, opens system settings, and enables the toggle
- **AND** the user returns to the app
- **THEN** the row state badge updates to "已授权"
- **AND** the user can return to the timer screen and start a countdown (the bubble will appear)

#### Scenario: User attempts to start countdown without overlay permission
- **WHEN** the user taps "开始锁屏" with overlay permission not granted
- **THEN** the system navigates to the permissions screen
- **AND** SHALL NOT start a countdown

### Requirement: Drag the Floating Bubble

The system SHALL allow the user to drag the floating bubble anywhere within the screen bounds, via a pointer-down and pointer-move gesture.

During a drag:
- The bubble's position SHALL update on each `pointermove` event
- The bubble SHALL NOT move if the total pointer displacement is < 3px (treated as a click, not a drag)
- The bubble's right edge SHALL be clamped to `[0, screenWidth - bubbleWidth]`
- The bubble's top edge SHALL be clamped to `[0, screenHeight - bubbleHeight]`

#### Scenario: User drags bubble from right to center
- **WHEN** the user pointer-downs on the bubble at right=40, top=35%-of-screen
- **AND** the user drags left by 200px and down by 100px
- **THEN** the bubble's right position becomes `screenWidth - bubbleWidth - 200px` (clamped if needed)
- **AND** the bubble's top position becomes `35%-of-screen + 100px` (clamped if needed)

### Requirement: Auto-Collapse on Right Edge

The system SHALL auto-collapse the floating bubble into a small pill (36dp wide, showing only a moon icon) when the user releases the bubble within 36dp of the right screen edge.

When collapsed:
- The handle, time, ring, and control buttons SHALL be hidden
- A moon icon SHALL be visible
- The bubble width SHALL be 36dp

Tapping the collapsed pill (without dragging) SHALL expand the bubble back to its full size.

#### Scenario: User releases bubble near right edge
- **WHEN** the user releases the bubble at right < 36px from the screen right edge
- **THEN** the bubble collapses to the pill state
- **AND** its right position is snapped to 6px from the screen right edge

#### Scenario: User taps collapsed pill
```

Full source: openspec/changes/yawn-lock/specs/floating-countdown-widget/spec.md

## openspec/changes/yawn-lock/specs/scheduled-screen-lock/spec.md

- Source: openspec/changes/yawn-lock/specs/scheduled-screen-lock/spec.md
- Lines: 1-143
- SHA256: e79ec812e16a072a07200dcfa9e73a5ccf1165f4d2b3f2dd57473f487d496cdf

[TRUNCATED]

```md
# Spec: scheduled-screen-lock (打哈欠定时锁屏)

## ADDED Requirements

### Requirement: Select Lock Duration

The system SHALL allow the user to select a lock duration on the main timer screen.

The duration SHALL be selectable from four quick presets: 30 seconds, 1 minute, 5 minutes, 10 minutes.

The duration SHALL additionally be adjustable via a custom control with:
- Plus/minus buttons (each step = 1 minute when duration < 10 minutes, 5 minutes otherwise)
- A range slider from 1 to 120 minutes
- Display of the current value with unit (seconds for < 1 min, minutes for ≥ 1 min)

#### Scenario: User selects a quick preset
- **WHEN** the user taps the "5 minutes" preset chip
- **THEN** the timer state updates to 5 minutes
- **AND** the "5 minutes" chip becomes visually active
- **AND** the custom dial displays "5 分钟"

#### Scenario: User adjusts duration with plus button
- **WHEN** the current duration is 5 minutes and the user taps the plus button
- **THEN** the duration increases to 6 minutes
- **WHEN** the current duration is 12 minutes and the user taps the plus button
- **THEN** the duration increases to 17 minutes

#### Scenario: User drags the slider
- **WHEN** the user drags the slider to position 30
- **THEN** the duration updates to 30 minutes
- **AND** the dial displays "30 分钟"

### Requirement: Start Countdown

The system SHALL start a countdown when the user taps the "开始锁屏" (Start Lock) button, provided that the device-admin permission has been granted.

The countdown SHALL:
- Run as a foreground service with a persistent notification showing remaining time
- Schedule an exact alarm via `AlarmManager.setExactAndAllowWhileIdle` at the lock deadline
- Display a full-screen status card on the timer screen with circular progress ring and remaining time
- The "开始锁屏" button SHALL be hidden while countdown is active

#### Scenario: User starts a 5-minute countdown
- **WHEN** the user has granted device-admin permission and taps "开始锁屏" with a 5-minute duration selected
- **THEN** the countdown begins
- **AND** a foreground service starts and shows a notification
- **AND** an exact alarm is scheduled for 5 minutes later
- **AND** the timer screen displays the status card with a 5:00 ring at 0% progress

#### Scenario: User attempts to start without device-admin permission
- **WHEN** the user has not granted device-admin permission and taps "开始锁屏"
- **THEN** the system SHALL navigate to the permissions screen
- **AND** SHALL NOT start a countdown

### Requirement: Pause and Resume Countdown

The system SHALL allow the user to pause and resume an active countdown from the timer screen status card and the floating bubble.

When paused, the alarm SHALL be cancelled and the countdown SHALL freeze; when resumed, a new alarm SHALL be scheduled for the remaining time.

#### Scenario: User pauses at 02:30 remaining
- **WHEN** the countdown is at 02:30 and the user taps "暂停"
- **THEN** the button label changes to "继续"
- **AND** the remaining time freezes at 02:30
- **AND** the scheduled alarm is cancelled

#### Scenario: User resumes the paused countdown
- **WHEN** the countdown is paused at 02:30 and the user taps "继续"
- **THEN** the button label changes to "暂停"
- **AND** a new exact alarm is scheduled 2 minutes 30 seconds in the future

### Requirement: Stop Countdown

The system SHALL allow the user to stop a countdown entirely via a "停止" (Stop) button on the timer screen and the floating bubble.

When stopped, the alarm SHALL be cancelled, the foreground service SHALL stop, and the timer screen SHALL return to the initial state with the "开始锁屏" button visible.

#### Scenario: User stops an active countdown
- **WHEN** the countdown is active and the user taps "停止"
- **THEN** the alarm is cancelled
```

Full source: openspec/changes/yawn-lock/specs/scheduled-screen-lock/spec.md

