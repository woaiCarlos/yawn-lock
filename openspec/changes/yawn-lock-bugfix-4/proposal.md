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
  - `openspec/changes/yawn-lock-bugfix-4/specs/scheduled-screen-lock/spec.md`(MODIFIED: 移除 status card requirement)

- **新依赖**:无
- **API 变更**:无
- **风险**:
  - XML 气泡视觉与 Compose 略不同(但功能等价)
  - OEM 引导 Dialog 一次性,需 SharedPreferences 标记"已显示"
  - ProcessLifecycle observer 在 Application 中常驻,需注意内存

## Capabilities

### New Capabilities

无

### Modified Capabilities

- `scheduled-screen-lock`:
  - `Start Countdown` Requirement:删除"Display a full-screen status card"行为(spec 简化为"bubble-only"展示)

## Non-Goals

- 不动 `floating-countdown-widget` spec 行为契约
- 不改锁屏(bugfix-2 已修)
- 不改权限管理(bugfix-2 已修)
- 不做 app 唤醒后自动检查(用户可手动开 app)
- 不做深链 OEM 自启动设置 Activity(用通用电池优化 Intent 替代,跨 ROM 兼容)
