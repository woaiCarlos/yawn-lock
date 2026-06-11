---
comet_change: yawn-lock-bugfix-4
role: technical-design
canonical_spec: openspec
---

# Design: yawn-lock-bugfix-4 — Timer 屏零 UI + 气泡 XML 化 + app 不被系统杀

> OpenSpec canonical spec: `openspec/changes/yawn-lock-bugfix-4/`
> 已有 main spec: `openspec/specs/scheduled-screen-lock/spec.md`、`openspec/specs/floating-countdown-widget/spec.md`
> 本次改动:scheduled-screen-lock 删 StatusCard 要求(用户要前台零 UI),floating-countdown-widget 行为契约不变。

## 0. 决策摘要(已确认)

| # | 决策 | 选择 | 备注 |
|---|------|------|------|
| D1 | Timer 屏是否显示倒计时 UI | **不显示**(仅悬浮窗) | 用户明示:前台零 UI |
| D2 | 气泡技术栈 | **XML/FrameLayout**(替代 ComposeView) | 根治 WindowManager+Compose 顽疾 |
| D3 | ProcessLifecycleOwner 注册位置 | **`YawnApplication.onCreate`**(非 Service) | 避免 race condition |
| D4 | 通知防 swipe 删除 | `setOngoing(true)` + Stop action 按钮 | Android 14 适配 |
| D5 | OEM 电池优化引导 | `Build.MANUFACTURER` 检测 + 一次性 Dialog | 国产 ROM 必需 |

## 1. Context

打完哈欠 v1.1 + 3 个 bugfix 后,锁屏 ✅。但 3 个 UX 问题:
1. Timer 屏 StatusCard 仍显示倒计时数字 + 暂停/停止(用户要前台零 UI)
2. 气泡在桌面/其他 app 上仍不出现
3. app 在后台开其他 app 时被系统杀

经网络调研定位:
- StatusCard 是 TimerScreen.kt 显式渲染(`if (state.isActive || state.status is Finished) StatusCard(...)`)
- 气泡不出现 = ComposeView in WindowManager 的 ViewTree 协调问题(根因诊断错误的尝试)
- app 被杀 = Android 14+ FGS 通知被 swipe + 国产 ROM 激进杀后台

## 2. Goals / Non-Goals

**Goals**:
- Timer 屏前台完全无倒计时 UI(无倒计时数字、无暂停/停止按钮)
- 悬浮窗在桌面/其他 app 上实际显示(用 XML/FrameLayout 替代 ComposeView)
- ProcessLifecycleOwner observer 在 Application.onCreate 注册,避免 race
- app 在后台被杀概率降低:通知固定 + Stop action + OEM 引导

**Non-Goals**:
- 不做 app 唤醒后自动检查
- 不做深链 OEM 自启动设置 Activity(因 ROM 差异大,改用通用 Intent)

## 3. 决策详情

### D1. Timer 屏零 UI(改动 spec)

**当前**:`TimerScreen.kt:75-77` 在 `state.isActive || state.status is Finished` 时显示 `StatusCard`,含倒计时数字 + 暂停/停止 + ProgressRing
**新**:删除 `if (state.isActive...) StatusCard(...)` 调用 + 整个 `StatusCard` 函数 + 未用 imports
**Spec**:`scheduled-screen-lock` 的 `Start Countdown` Requirement 改 — 删除"Display a full-screen status card"行为

### D2. 气泡 XML 化(根治 WindowManager+Compose 顽疾)

**当前问题**:
- `FloatingBubbleController` 自身实现 `LifecycleOwner` 等接口(bugfix-2 妥协)
- `setContent` 内部 `setViewTreeViewModelStoreOwner(this)` 的 `this` 是 controller,不是 view,潜在 ViewModelStore 创建失败
- 静默失败,被 try/catch 吞

**新方案**:**完全废弃 Compose**,改纯 XML:
- `res/layout/floating_bubble.xml`:FrameLayout 嵌套 LinearLayout + TextView + Button + ImageView
- 4 个 drawable:bubble_bg (圆角渐变)、bubble_moon_bg、bubble_btn_dim、bubble_btn_accent
- `FloatingBubbleController.kt`:LayoutInflater.inflate + findViewById 拿子视图,直接 setOnClickListener / setOnTouchListener,setText 更新倒计时
- 渐变背景:`GradientDrawable` 不在 XML 中,改在 `root.background = GradientDrawable(...)` 程序化设置(可选 polish)
- 拖动 / 折叠 / 展开不变(作用于 View 而非 ComposeView)

**为什么 XML**:
- 无 Compose,无 ViewTree 协调
- 标准 Android 视图,WindowManager 行为 100% 可预测
- 性能更好(无重组开销)

### D3. ProcessLifecycleOwner 移到 Application

**当前问题**:`CountdownService.onCreate` 注册 observer 有 race — Service 启动较晚可能错过 `ON_STOP` 事件
**新**:
- `YawnApplication` 持有 `bubbleController: FloatingBubbleController` 单例
- `YawnApplication.onCreate` 调 `ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)`
- `appLifecycleObserver`:`ON_STOP` → `if (state.isActive) bubbleController.show()`;`ON_START` → `bubbleController.hide()`
- `CountdownService` 移除 ProcessLifecycle 相关代码(订阅/取消/observer 字段)
- `CountdownService.bubble` 字段从 `var null` 改为 `val bubble get() = (application as YawnApplication).bubbleController`(getter)

### D4. 通知固定 + Stop action

**当前**:`NotificationCenter.build` 已设 `setOngoing(true)`,但**没加 Stop action**
**新**:
- `addAction(R.drawable.ic_stop, "停止", stopPI)` — StopPI 触发 `StopReceiver` → `repo.stop()`
- `setOnlyAlertOnce(true)` — 不重复响铃
- `setCategory(NotificationCompat.CATEGORY_SERVICE)` — 标记为 service 类
- `setPriority(NotificationCompat.PRIORITY_LOW)` — 低优先级,系统更少主动 dismiss

### D5. OEM 电池优化引导

**当前**:无 OEM 处理
**新**:
- `MainActivity` 启动时 `sharedPreferences` 检查 `oem_warned_shown`
- 首次启动 + `Build.MANUFACTURER` ∈ {Xiaomi, Redmi, POCO, Huawei, Honor, OPPO, realme, vivo, OnePlus} → 显示一次性 Dialog
- "去设置"按钮 → `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` Intent + 包名 Uri
- "知道了"按钮 → 写 `oem_warned_shown = true`,不再显示
- 用 `androidx.appcompat.app.AlertDialog`(避免 Compose Dialog 复杂度)

## 4. 风险 / Trade-offs

- **[风险] XML 气泡视觉与 Compose 略不同** — 渐变、间距等微小差异
- **[风险] OEM 引导仅支持通用电池优化 Intent** — 国产 ROM"自启动"设置无法深链,需用户手动找
- **[权衡] ProcessLifecycle observer 永久常驻** — 单例,无内存影响
- **[权衡] 气泡折叠/展开用同一布局 + params.width 切换** — 简化代码,但折叠时无 moon icon 显示(显示同样渐变+空)

## 5. Migration Plan

无 — 同分支修改,向后兼容。

## 6. 测试策略

- 编译通过(`./gradlew assembleDebug`)
- 装机测试 4 步:
  1. 启动 app → 若在已知 OEM,见一次性 Dialog
  2. 选时长 → 点"开始计时" → Timer 屏**无任何倒计时 UI**(关键)
  3. 按 home → **桌面/其他 app 上看到悬浮气泡**(倒计时 + 暂停/停止按钮)
  4. 等到点 → 屏幕锁 + 气泡消失
- 气泡拖动 / 折叠 / 展开手动验证
- 通知中心 swipe 通知测试(若可滑除则 ongoing flag 失败)
- logcat 检查 `FloatingBubble` TAG 无 `addView failed` 异常

## 7. 开放问题(已决议)

- Q1: 气泡折叠态是否保留 moon icon?→ 简化:折叠 = 36dp pill(只显示 handle bar,无 icon)
- Q2: 倒计时字体 → monospace 等宽,数字不抖动
- Q3: 气泡大小 200dp → 维持原设计

## 8. 引用

- OpenSpec proposal: `openspec/changes/yawn-lock-bugfix-4/proposal.md`
- OpenSpec design (高维): `openspec/changes/yawn-lock-bugfix-4/design.md`
- OpenSpec delta spec: `openspec/changes/yawn-lock-bugfix-4/specs/scheduled-screen-lock/spec.md`
- OpenSpec tasks: `openspec/changes/yawn-lock-bugfix-4/tasks.md`
- Handoff context: `openspec/changes/yawn-lock-bugfix-4/.comet/handoff/design-context.md`
- Web research on ComposeView in WindowManager: `blog.csdn.net/qq_45925230/article/details/123356190`
- Web research on FGS notification dismissal on Android 14: `blog.csdn.net/m0_62153576/article/details/146003588`
