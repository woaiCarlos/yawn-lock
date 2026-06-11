# Comet Design Handoff

- Change: yawn-lock-polish
- Phase: design
- Mode: compact
- Context hash: dcb21a6a1194ea534fa8932e899de605ee3c30e1cc8ac26760742c78043d1486

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/yawn-lock-polish/proposal.md

- Source: openspec/changes/yawn-lock-polish/proposal.md
- Lines: 1-61
- SHA256: 44dfe814eb4ea71d6bd14aac3b23fa45336eae4dcd8ef5de964100139a610ed4

```md
# Proposal: yawn-lock-polish — UI 调整 + Bug 修复

## Why

打完哈欠 v1 上手指出了 3 个交互问题和 3 个 UX 微调,需在归档前修掉:
1. **UI 精度不足** — 滑块只能调分钟,无法精确到秒;预设值太短(30s/1m/5m/10m),对"睡前定个 1 小时提醒"这种典型场景不友好
2. **页面导航有死路** — 授权后只能停在 timer 屏幕,无法回到权限页查看/调整
3. **授权后状态不刷新** — 用户从系统设置返回 app,权限状态仍显示"未授权"
4. **授权后点开始没反应** — 气泡和倒计时都不出现,Service 启动后异常被吞掉,ticker 也不跑

## What Changes

### UI 微调

- **预设值变更** — 改成长时段:`10 分钟 / 30 分钟 / 1 小时 / 2 小时`(`web-prototype/timer.html` 原型的中长时段是真实用户场景)
- **滑块精度** — 滑块从 1-120 分钟改为 5 秒 - 2 小时,以秒为单位(单位随阈值自动切换)
- **CTA 文字** — "开始锁屏" → "开始计时"(产品上计时器是手段,锁屏是结果,改文案更友好)

### Bug 修复

- **页面切换** — Timer 屏幕右上角加"权限/设置"图标,可随时回到 Permissions 屏幕;同时修复 `startDestination` 响应式重算导致 NavHost 状态混乱的 code smell
- **权限实时刷新** — `MainActivity` 监听 `Lifecycle.Event.ON_RESUME`,在 `onResume` 时调用 `PermissionChecker.check()` 推送新状态;Permissions 屏幕订阅该状态自动重组
- **气泡/倒计时修复** — `FloatingBubbleController.show()` 扩大异常捕获(`SecurityException`/`RuntimeException`);`CountdownService.handleStart` 用 try/catch 包 `ensureBubble()` 防止气泡失败时 ticker 一起死;`ViewCompositionStrategy` 改为 `DisposeOnDetachedFromWindowOrReleasedFromPool`(适配 WindowManager 视图无 lifecycle owner)

## Capabilities

### New Capabilities

- 无 — 本次变更不新增 capability,均为对现有 capability 的调整

### Modified Capabilities

- `scheduled-screen-lock`:
  - `Select Lock Duration` 改:预设值列表(30s/1m/5m/10m → 10m/30m/1h/2h),滑块精度(分钟 → 秒)
  - `Start Countdown` 改:依赖权限实时刷新后,新增 navigation 从 timer 屏幕到 permissions 屏幕的入口
  - 新增 Scenario:"用户从 timer 屏幕主动查看/调整权限"

- `floating-countdown-widget`:
  - 行为不变,只是实现更健壮(异常处理 + lifecycle 策略)。**不修改 spec**——bug 修复不改变 spec 行为契约

## Impact

- **修改文件**(预计 5-6 个):
  - `app/src/main/kotlin/com/example/yawnlock/MainActivity.kt`(实时刷新 + startDest 修复)
  - `app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt`(滑块/预设/CTA/导航图标)
  - `app/src/main/kotlin/com/example/yawnlock/ui/permissions/PermissionsScreen.kt`(无改动,消费实时状态)
  - `app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt`(try/catch 包 ensureBubble)
  - `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt`(扩大异常 + lifecycle 策略)
  - `app/src/main/res/values/strings.xml`(可能新增 nav 按钮 contentDescription)

- **新依赖**: 无
- **API 变更**: 无(纯 UI + 健壮性)
- **风险**:
  - 滑块精度改秒,需要重新调 Tap + Slider 的步进交互,可能引入新的精度问题(比如 5 秒步进的边界)
  - 实时权限刷新依赖 Lifecycle 观察者,需测试 onResume 在不同 navigation 状态下的触发
  - ViewCompositionStrategy 切换在 WindowManager 视图上的实际行为需要实测验证
- **范围外**:
  - 预设值记不记忆(仍每次重置)
  - 多语言(保持 zh-CN)
  - DND/统计等(下次迭代)
  - 单元测试补强(留待下次)
```

## openspec/changes/yawn-lock-polish/design.md

- Source: openspec/changes/yawn-lock-polish/design.md
- Lines: 1-187
- SHA256: 9db1f17e34561af14c0665dd7c06e2dea1378e1a96f1505baca5237d93726b01

[TRUNCATED]

```md
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
```

Full source: openspec/changes/yawn-lock-polish/design.md

## openspec/changes/yawn-lock-polish/tasks.md

- Source: openspec/changes/yawn-lock-polish/tasks.md
- Lines: 1-98
- SHA256: 78632f9a30f90277e809a2a831343db42fd5d86cd0bb26f910c29ab66642091f

[TRUNCATED]

```md
# Tasks: yawn-lock-polish

> 任务清单 — 3 UI 调整 + 3 bug 修复,顺序按依赖排列。
> 全部任务在 Android Studio / Gradle CLI 环境 (JDK 17, AGP 8.5+) 下执行。

## 1. UI: 滑块 / 预设 / CTA 文字

- [ ] 1.1 修改 `TimerScreen.kt` 的 `PresetChips`:preset 列表从 `0.5/1.0/5.0/10.0` 改为 `600/1800/3600/7200`(秒),label 文案从 "30/1/5/10 + 秒/分钟" 改为 "10/30/1/2 + 分钟/小时"
- [ ] 1.2 修改 `CustomDial`:
  - 内部状态从 `minutes: Double` 改为 `seconds: Long`(精确到秒)
  - 滑块 `valueRange` 从 `1f..120f` 改为 `5f..7200f`
  - 滑块首尾标签从 "1分/120分" 改为 "5秒/2时"
  - ± 按钮步进改为: `< 60` 秒步 5,`60-299` 秒步 30,`>= 300` 秒步 60
  - 显示逻辑:`seconds < 60` → "X 秒",否则 → "X 分钟"
- [ ] 1.3 `TimerViewModel.setMinutes(m: Double)` 改为 `setSeconds(s: Long)`,内部转 ms 后 `repo.preview()`
- [ ] 1.4 改 `StartCta` 文案 "开始锁屏" → "开始计时"
- [ ] 1.5 提交

```bash
git add app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerViewModel.kt
git commit -m "feat(timer): rewrite preset/slider/CTA for second precision"
```

## 2. Bug: Timer 屏幕 → Permissions 屏幕导航入口

- [ ] 2.1 在 `HeroCard` 内右上角加 `IconButton`(图标 `Icons.Default.Settings` 或 `Lock`),`onClick = onNavigatePermissions`
- [ ] 2.2 把 `onNavigatePermissions` 回调从 `TimerScreen` 顶层签名传到 `HeroCard`
- [ ] 2.3 提交

```bash
git add app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt
git commit -m "feat(timer): add permissions entry icon in hero card"
```

## 3. Bug: 权限实时刷新

- [ ] 3.1 `MainActivity` 实现 `DefaultLifecycleObserver`,在 `onResume` 调用 `(application as YawnApplication).timerRepository` 的扩展或直接 `permissionsViewModel.refresh()`
- [ ] 3.2 `onCreate` 中 `lifecycle.addObserver(observer)`;`onDestroy` 中 `removeObserver(observer)`
- [ ] 3.3 提交

```bash
git add app/src/main/kotlin/com/example/yawnlock/MainActivity.kt
git commit -m "fix(perm): refresh permission state on activity resume"
```

## 4. Bug: AppNavHost startDest 响应式 code smell

- [ ] 4.1 `AppNavHost`:`val startDest = ...` 改为 `val startDest = remember { if (perms.canStartCountdown) "timer" else "permissions" }`
- [ ] 4.2 提交

```bash
git add app/src/main/kotlin/com/example/yawnlock/MainActivity.kt
git commit -m "fix(nav): lock startDest with remember to avoid recompose side-effects"
```

## 5. Bug: 气泡/倒计时 — FloatingBubbleController 健壮性

- [ ] 5.1 `FloatingBubbleController.show()`:`catch (e: BadTokenException)` 改为 `catch (e: Exception)`(含 SecurityException / RuntimeException)
- [ ] 5.2 `init` 块的 `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` 改为 `DisposeOnDetachedFromWindowOrReleasedFromPool`(WindowManager 视图无 lifecycle owner)
- [ ] 5.3 提交

```bash
git add app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt
git commit -m "fix(bubble): broaden exception catch + use detach-based dispose strategy"
```

## 6. Bug: 气泡失败 → ticker 死防御

- [ ] 6.1 `CountdownService.handleStart`:`ensureBubble()` 用 try/catch 包住,失败 log warning 但不让 ticker 死
- [ ] 6.2 提交

```bash
git add app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt
git commit -m "fix(service): decouple ticker from bubble success in handleStart"
```

## 7. Build + 冒烟

- [ ] 7.1 `./gradlew :app:assembleDebug` 通过
- [ ] 7.2 APK 重新生成
```

Full source: openspec/changes/yawn-lock-polish/tasks.md

## openspec/changes/yawn-lock-polish/specs/scheduled-screen-lock/spec.md

- Source: openspec/changes/yawn-lock-polish/specs/scheduled-screen-lock/spec.md
- Lines: 1-97
- SHA256: 69e8016df7208cb9675575f02edc44fe378eaccd1fb417508a2fc1782d2a8367

[TRUNCATED]

```md
# Spec: scheduled-screen-lock (delta for yawn-lock-polish)

> 本 delta 修改 `openspec/specs/scheduled-screen-lock/spec.md`,记录 yawn-lock v1 → v1.1 之间的行为变更。
> 其他 capability (`floating-countdown-widget`) 行为不变,不在此 delta 范围。

## MODIFIED Requirements

### Requirement: Select Lock Duration

**变更**: 滑块从"分钟级"改为"秒级"精度,范围 5 秒 - 2 小时;预设从短时段(30s/1m/5m/10m)改为中长时段(10m/30m/1h/2h)。

The system SHALL allow the user to select a lock duration on the main timer screen.

The duration SHALL be selectable from four quick presets: **10 minutes, 30 minutes, 1 hour, 2 hours**.

The duration SHALL additionally be adjustable via a custom control with:
- Plus/minus buttons (each step = 5 seconds when duration < 60s, 30 seconds when 60s ≤ duration < 300s, 60 seconds when duration ≥ 300s)
- A range slider from **5 seconds to 7200 seconds (2 hours), with 1-second precision**
- Display of the current value with unit (seconds for < 60s, minutes for ≥ 60s)

#### Scenario: User selects a quick preset
- **WHEN** the user taps the "10 分钟" preset chip
- **THEN** the timer state updates to 10 minutes (600 seconds)
- **AND** the "10 分钟" chip becomes visually active
- **AND** the custom dial displays "10 分钟"

#### Scenario: User adjusts duration with plus button
- **WHEN** the current duration is 30 seconds and the user taps the plus button
- **THEN** the duration increases to 35 seconds (5-second step)
- **WHEN** the current duration is 5 minutes (300 seconds) and the user taps the plus button
- **THEN** the duration increases to 6 minutes (60-second step)

#### Scenario: User drags the slider
- **WHEN** the user drags the slider to position 1800 (seconds)
- **THEN** the duration updates to 1800 seconds (30 minutes)
- **AND** the dial displays "30 分钟"

#### Scenario: User adjusts duration to sub-minute value
- **WHEN** the user sets duration to 45 seconds
- **THEN** the dial displays "45 秒" (not "0 分钟")

---

### Requirement: Start Countdown

**变更**: 启动倒计时的"开始"按钮文案从"开始锁屏"改为"开始计时";Timer 屏幕现在有一个"权限"入口可主动跳转到 Permissions 屏幕(不再依赖"开始"按钮触发跳转)。

The system SHALL start a countdown when the user taps the "**开始计时**" (Start Countdown) button, provided that the device-admin permission and overlay permission have been granted.

The countdown SHALL:
- Run as a foreground service with a persistent notification showing remaining time
- Schedule an exact alarm via `AlarmManager.setExactAndAllowWhileIdle` at the lock deadline
- Display a full-screen status card on the timer screen with circular progress ring and remaining time
- The "开始计时" button SHALL be hidden while countdown is active

The timer screen SHALL provide a **permissions/settings entry point** (e.g., icon in the hero card or a top-right icon button) that navigates to the permissions screen regardless of whether permissions are currently granted.

#### Scenario: User starts a 10-minute countdown
- **WHEN** the user has granted both device-admin and overlay permissions, selected 10 minutes, and taps "开始计时"
- **THEN** the countdown begins
- **AND** a foreground service starts and shows a notification
- **AND** an exact alarm is scheduled for 10 minutes later
- **AND** the timer screen displays the status card with a 10:00 ring at 0% progress
- **AND** the floating bubble appears on the home screen / other apps

#### Scenario: User attempts to start without device-admin permission
- **WHEN** the user has not granted device-admin permission and taps "开始计时"
- **THEN** the system SHALL navigate to the permissions screen
- **AND** SHALL NOT start a countdown

#### Scenario: User navigates from timer to permissions via icon
- **WHEN** the user is on the timer screen with all permissions granted and taps the permissions icon
- **THEN** the system SHALL navigate to the permissions screen
- **AND** the permissions list SHALL show the current state of all permissions

---

### Requirement: Permission Gating on Timer Screen

**变更**: 权限状态从"启动时静态检查"改为"实时刷新"。`MainActivity` 监听 `ON_RESUME`,每次从后台返回都重新查询权限,推送给 UI。
```

Full source: openspec/changes/yawn-lock-polish/specs/scheduled-screen-lock/spec.md

