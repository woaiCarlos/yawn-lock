# Comet Design Handoff

- Change: yawn-lock-bugfix-4
- Phase: design
- Mode: compact
- Context hash: 953e79a82239013d966607052147ec4c32b5eab4fb2ae26c03cb08b7f8fef48e

Generated-by: comet-handoff.sh

OpenSpec remains the canonical capability spec. This handoff is a deterministic, source-traceable context pack, not an agent-authored summary.

## openspec/changes/yawn-lock-bugfix-4/proposal.md

- Source: openspec/changes/yawn-lock-bugfix-4/proposal.md
- Lines: 1-107
- SHA256: 6daef3dfa90fd3a05e3117780cd9f1ff796a10d89c62df68a09ef321c36822e3

[TRUNCATED]

```md
# Proposal: yawn-lock-bugfix-4 — Timer 屏零 UI + 气泡 XML 化 + app 不被系统杀

## Why

打完哈欠 v1.1 polish + bugfix-1/2/3 后,用户实测发现 3 个未解决问题:

1. **Timer 屏幕仍显示倒计时 + 暂停/停止按钮**(用户**要求前台零 UI**,只让悬浮窗显示)
2. **悬浮窗仍不出现**在桌面/其他 app 上(用户已确认授权)
3. **app 在后台被自动关闭** — 用户按 home → 开其他 app → 我们的 app 消失

经网络调研 + 代码审计,定位 3 个独立根因。

## What Changes

### 修复 1: 移除 Timer 屏幕的 StatusCard

**根因**:`TimerScreen.kt:75-77` 在 `state.isActive || state.status is Finished` 时显示 `StatusCard`,包含倒计时数字 + 暂停/停止按钮 + ProgressRing。用户希望前台**完全无倒计时 UI** — 倒计时信息只在悬浮窗里。

**修复**:
- 移除 `TimerScreen.kt:75-77` 的 `if (state.isActive...) StatusCard(...)` 调用
- 移除整个 `StatusCard` Composable(行 242-280)— 已无用
- 移除未用 import `StatusCard`,但 `ProgressRing` 仍可能用(检查)
- **spec 同步**:`scheduled-screen-lock` 的 `Start Countdown` Requirement 改 — 把"Display a full-screen status card"删除(用户已不再需要)

### 修复 2: 气泡用 XML/FrameLayout 替代 Compose(根治 WindowManager+Compose 顽疾)

**根因**:即便 bugfix-2 注入了 `ViewTreeLifecycleOwner` 等,ComposeView 在 WindowManager 视图中仍极不稳定(根据网络调研:cnblogs 2023/2025 多份证据):
- `setContent` 内部 `setParentCompositionContext` 在 ViewTree 无 owner 时抛 `IllegalStateException`
- 即便显式注入 LifecycleOwner,`AbstractComposeView` 还要 `setViewTreeViewModelStoreOwner(this)`,这里 `this` 是 WindowManager-addView 的 view,**`this` 不是 LifecycleOwner**(因为我们让 controller 类自己实现 LifecycleOwner,不是 view 自己)
- 各种 ViewTree 缺失的异常在 `FloatingBubbleController.show()` 内的 try/catch 被吞掉,无法诊断

**修复**:**完全废弃 ComposeView**,改用纯 XML 布局:
- `res/layout/floating_bubble.xml`:从 `ComposeView` 改为 `FrameLayout` 内嵌 `LinearLayout` + `TextView` (倒计时) + 2 个 `ImageButton` (暂停/停止)
- `FloatingBubbleController.kt`:不再用 `setContent { BubbleContent(...) }`,直接 `LayoutInflater.inflate(R.layout.floating_bubble)`,操作 `TextView.setText()` 更新倒计时,`setOnClickListener` 接按钮
- 视觉:用 `GradientDrawable`(TL→BR 渐变 `Purple900 → Purple500`)作为 background
- 拖动 / 折叠 / 展开逻辑不变(在 View 上监听 onTouch)

**为什么 XML 方案更可靠**:
- 不依赖 Compose runtime,无 ViewTree owner 需求
- `FrameLayout` + `TextView` + `ImageButton` 是标准 Android 视图,在 WindowManager 下表现 100% 可预测
- `GradientDrawable` 程序化设置即可,无需 9-patch
- 触摸 / 点击 / 拖动 / 状态变化全部走标准 Android 事件流,无黑魔法

### 修复 3: ProcessLifecycleOwner observer 移到 Application.onCreate

**根因**:`CountdownService.onCreate` 注册 observer 有 race condition:
- 用户开 app → 进 Timer 屏幕 → 点"开始计时" → Service.onCreate 跑(注册 observer)
- 如果 Service.onCreate 在 `ON_STOP` 之前,observer 会收到
- 但如果 observer 注册时 `ON_STOP` 已经派发(用户快速按 home),observer 错过该事件,**气泡永不显示**
- 网络调研:cnblogs/掘金/知乎 多份证据建议 observer 应在 `Application.onCreate` 注册

**修复**:
- `YawnApplication.onCreate` 调 `ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)`
- `appLifecycleObserver` 是单例,在应用生命周期内存在,**早于任何 Service 启动**
- 移除 `CountdownService` 里的 ProcessLifecycle 订阅/取消(改由 Application 全程管理)
- `appLifecycleObserver` 根据 `repo.state.value.isActive` + `repo.state.value.status` 决定 show/hide

### 修复 4: app 不被系统杀(Android 14 FGS + OEM)

**根因 A**:Android 14 严格 FGS 规则 — 用户在通知中心 swipe 掉前台服务通知 → 服务 5 秒内被杀
**根因 B**:国产 ROM(小米 MIUI、华为 EMUI、OPPO ColorOS、Vivo OriginOS) 激进杀后台 — 必须开启"自启动"才不被杀

**修复**:
- 通知固定不可滑除: `NotificationCompat.Builder.setOngoing(true)` + `setPriority(NotificationCompat.PRIORITY_LOW)`(已部分存在,补全)
- 通知加 Stop action 按钮:`addAction(ic_stop, "停止", stopPI)` — 让用户能方便地停止
- **OEM 检测 + 引导**:首次启动时检测制造商,如果是已知激进 ROM(小米/华为/OPPO/Vivo),显示一次性 Dialog 引导用户去自启动设置页
  - 用 `Build.MANUFACTURER` 检测
  - 不深链到特定 ROM 设置 Activity(因版本差异大且不可靠),改用 `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` Intent + 通用建议

## Impact

- **修改文件**:
  - `app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt`(移除 StatusCard 调用 + 函数)
  - `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt`(完全重写,XML 化)
  - `app/src/main/res/layout/floating_bubble.xml`(FrameLayout 布局)
  - `app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt`(移除 ProcessLifecycle 订阅,加 Log.e 诊断,加 notification stop action)
  - `app/src/main/kotlin/com/example/yawnlock/YawnApplication.kt`(加 ProcessLifecycle observer)
  - `app/src/main/kotlin/com/example/yawnlock/MainActivity.kt`(OEM 检测 + 一次性 Dialog)
  - `app/src/main/kotlin/com/example/yawnlock/data/NotificationCenter.kt`(补全 setOngoing + stop action)
  - `app/src/main/res/values/strings.xml`(新文案)
```

Full source: openspec/changes/yawn-lock-bugfix-4/proposal.md

## openspec/changes/yawn-lock-bugfix-4/design.md

- Source: openspec/changes/yawn-lock-bugfix-4/design.md
- Lines: 1-286
- SHA256: ff2bd9a2e89d60afb678eb0c811fdadc664e0d9087d06270a68f1117ea23a708

[TRUNCATED]

```md
# Design: yawn-lock-bugfix-4

> OpenSpec canonical spec: `openspec/changes/yawn-lock-bugfix-4/`
> 已有 main spec: `openspec/specs/scheduled-screen-lock/spec.md`、`openspec/specs/floating-countdown-widget/spec.md`

## Context

打完哈欠 v1.1 + 3 个 bugfix 后,锁屏 ✅。但 3 个 UX 问题:
1. Timer 屏幕 StatusCard 不该显示(用户要前台零 UI)
2. 悬浮窗在桌面/其他 app 上仍不出现
3. app 在后台开其他 app 时被系统杀

经网络调研定位:
- StatusCard 是 TimerScreen.kt 显式渲染
- 气泡不出现是 ComposeView in WindowManager 的 ViewTree 协调问题
- app 被杀是 Android 14+ FGS 通知被 swipe + 国产 ROM 杀后台

## Goals / Non-Goals

**Goals**:
- Timer 屏幕在倒计时中**完全无 UI**(无倒计时数字、无暂停/停止按钮)
- 悬浮窗在桌面/其他 app 上**实际显示**(用 XML/FrameLayout 替代 ComposeView,消除 ViewTree 协调问题)
- ProcessLifecycleOwner observer 在 `YawnApplication.onCreate` 注册,避免 race
- app 在后台被系统杀的概率降低:通知加 stop action + ongoing + OEM 电池优化引导

**Non-Goals**:
- 不做 app 唤醒后自动检查(交给用户)
- 不做深链 OEM 自启动设置 Activity(因 ROM 差异大,用通用 Intent)

## Decisions

### D1. 气泡从 Compose 改为 XML(根治 A1)

**当前问题**:
- `FloatingBubbleController` 继承 `LifecycleOwner` 等接口,这是 bugfix-2 的妥协(让 controller 自身当 owner 传给 bubbleView)
- 但 `setContent` 内部仍可能失败:`setViewTreeViewModelStoreOwner(this)` 的 `this` 是 controller(实现 LifecycleOwner),不是 view,可能导致 ViewModelStore 创建失败
- 静默失败,bugfix-2 后的 try/catch 仍吞掉

**新方案:完全不用 Compose**

**`res/layout/floating_bubble.xml`**(完全重写):
```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/bubble_root"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:padding="14dp"
    android:background="@drawable/bubble_bg">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center_horizontal">

        <View
            android:id="@+id/bubble_handle"
            android:layout_width="36dp"
            android:layout_height="4dp"
            android:layout_marginBottom="10dp"
            android:background="#FFFFFF"
            android:alpha="0.3" />

        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <ImageView
                android:id="@+id/bubble_moon"
                android:layout_width="30dp"
                android:layout_height="30dp"
                android:src="@drawable/ic_moon"
                android:padding="7dp"
                android:background="@drawable/bubble_moon_bg" />

            <LinearLayout
                android:layout_width="wrap_content"
```

Full source: openspec/changes/yawn-lock-bugfix-4/design.md

## openspec/changes/yawn-lock-bugfix-4/tasks.md

- Source: openspec/changes/yawn-lock-bugfix-4/tasks.md
- Lines: 1-635
- SHA256: bea42a096e8484c331e85ceac1171946907f8e31009e9a8f55aa3aae827989d6

[TRUNCATED]

```md
# Tasks: yawn-lock-bugfix-4

> 5 个修复任务:Timer 屏零 UI、气泡 XML 化、ProcessLifecycle 移到 Application、通知固定 + stop action、OEM 引导。

## 1. 气泡 XML 化(根治 WindowManager+Compose 顽疾)

**Files:** `res/layout/floating_bubble.xml`, `res/drawable/bubble_*.xml`, `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt`

- [ ] **Step 1: 新增 4 个 drawable 资源**

`res/drawable/bubble_bg.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="315"
        android:startColor="#2D2466"
        android:endColor="#6750A4" />
    <corners android:radius="22dp" />
</shape>
```

`res/drawable/bubble_moon_bg.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#33FFD97A" />
    <corners android:radius="10dp" />
</shape>
```

`res/drawable/bubble_btn_dim.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#29FFFFFF" />
    <corners android:radius="11dp" />
</shape>
```

`res/drawable/bubble_btn_accent.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFD97A" />
    <corners android:radius="11dp" />
</shape>
```

- [ ] **Step 2: 重写 `floating_bubble.xml` 为 FrameLayout 布局**

替换整个 `res/layout/floating_bubble.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/bubble_root"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:padding="14dp"
    android:background="@drawable/bubble_bg">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center_horizontal">

        <View
            android:layout_width="36dp"
            android:layout_height="4dp"
            android:layout_marginBottom="10dp"
            android:background="#FFFFFF"
            android:alpha="0.3" />

        <LinearLayout
```

Full source: openspec/changes/yawn-lock-bugfix-4/tasks.md

## openspec/changes/yawn-lock-bugfix-4/specs/scheduled-screen-lock/spec.md

- Source: openspec/changes/yawn-lock-bugfix-4/specs/scheduled-screen-lock/spec.md
- Lines: 1-52
- SHA256: 6ac8d5c139475cd52c3aeecaf00f3005183d643d2c793433bed81b42a00729ba

```md
# Spec: scheduled-screen-lock (delta for yawn-lock-bugfix-4)

> 本 delta 修改 `openspec/specs/scheduled-screen-lock/spec.md`,记录 yawn-lock v1.1 → v1.2 之间的行为变更(从用户实测反馈调整)。
> 其他 capability (`floating-countdown-widget`) 行为契约不变,不在此 delta 范围。

## MODIFIED Requirements

### Requirement: Start Countdown

**变更**:**移除** "Display a full-screen status card on the timer screen" 行为 — 用户实测反馈 Timer 屏幕在前台时**不应该显示倒计时 UI**(无倒计时数字、无暂停/停止按钮);倒计时信息**仅**在悬浮窗里展示,前台的 Timer 屏幕只显示选时控件。

The system SHALL start a countdown when the user taps the "**开始计时**" (Start Countdown) button, provided that the device-admin permission and overlay permission have been granted.

The countdown SHALL:
- Run as a foreground service with a persistent notification showing remaining time (with `setOngoing(true)` to prevent user dismissal)
- Schedule an exact alarm via `AlarmManager.setExactAndAllowWhileIdle` at the lock deadline (for durations > 5 min) OR use a `Handler.postDelayed` (for durations ≤ 5 min)
- **NOT display any countdown UI on the timer screen** during the active countdown — countdown information is shown ONLY in the floating bubble when the user has left the app

The timer screen SHALL provide a permissions/settings entry point (e.g., icon in the hero card or a top-right icon button) that navigates to the permissions screen regardless of whether permissions are currently granted.

#### Scenario: User starts a 10-minute countdown
- **WHEN** the user has granted both device-admin and overlay permissions, selected 10 minutes, and taps "开始计时"
- **THEN** the countdown begins
- **AND** a foreground service starts and shows a notification
- **AND** an exact alarm (or Handler callback) is scheduled for 10 minutes later
- **AND** the timer screen continues to show the time-selection controls without displaying the countdown
- **AND** the floating bubble appears on the home screen / other apps (when user leaves the Timer screen)

#### Scenario: User attempts to start without device-admin permission
- **WHEN** the user has not granted device-admin permission and taps "开始计时"
- **THEN** the system SHALL navigate to the permissions screen
- **AND** SHALL NOT start a countdown

#### Scenario: User navigates from timer to permissions via icon
- **WHEN** the user is on the timer screen with all permissions granted and taps the permissions icon
- **THEN** the system SHALL navigate to the permissions screen
- **AND** the permissions list SHALL show the current state of all permissions

#### Scenario: Active countdown, user stays on timer screen
- **WHEN** the countdown is active and the user is on the timer screen
- **THEN** the timer screen SHALL NOT display a countdown status card, progress ring, or pause/stop buttons
- **AND** only the time-selection controls SHALL be visible

#### Scenario: Active countdown, user backgrounds the app
- **WHEN** the countdown is active and the user presses Home (or switches to another app)
- **THEN** the floating bubble SHALL appear on the home screen / other app
- **AND** the bubble SHALL show the remaining countdown time, a pause/resume button, and a stop button
- **AND** the user CAN drag the bubble to the screen edge to collapse it
- **AND** the user CAN drag it back to expand it
- **WHEN** the user returns to the timer screen
- **THEN** the bubble SHALL hide
- **AND** the timer screen SHALL show the time-selection controls (still no countdown UI)
```

