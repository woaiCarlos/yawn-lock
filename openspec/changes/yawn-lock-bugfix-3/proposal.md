# Proposal: yawn-lock-bugfix-3 — 气泡应仅在 app 后台时显示

## Why

打完哈欠 v1.1 + bugfix-1 + bugfix-2 后,锁屏 ✅ 已修。但悬浮气泡仍不对:

- 用户在 Timer 屏幕点"开始计时" → **气泡**已经在 Timer 屏幕的**首页/Activity 之上**显示了(应该不显示,因为还在 app 内)
- 用户切到桌面 → 气泡应该跟随,现在仍不显示(因为 v1.1 + bugfix-1 + bugfix-2 修复的 ViewTreeLifecycleOwner 仅让气泡能渲染,但前台/后台切换的逻辑没实现)

按 Web-Prototype/floating.html 原型:**用户停留在 Timer 屏幕时,无气泡;用户离开 app 切到桌面/其他 app 时,气泡应出现**。

## What Changes

### 修复 1: 气泡条件显示(核心)

**根因**:`CountdownService.handleStart` 在前台时无条件调 `ensureBubble()` → `FloatingBubbleController.show()` → `wm.addView(bubbleView, params)`。代码不管 app 是在前台还是后台都添加气泡。

**期望行为**(对齐 Web-Prototype/floating.html):
- 倒计时开始 → 状态进入 Counting,但**不立即显示气泡**
- app 切到后台(用户按 home / 切到其他 app)→ **显示气泡**
- app 回到前台 → **隐藏气泡**(用户在自己 app 看到 Timer 屏幕的 StatusCard 即可)
- 倒计时结束 / 暂停 / 停止 → 隐藏气泡(不依赖 app 前后台)

**修复**:
- 引入 `androidx.lifecycle:lifecycle-process` 库提供 `ProcessLifecycleOwner`(AndroidX 官方)
- `CountdownService` 在 `onCreate` 订阅 `ProcessLifecycleOwner.lifecycle`,监听 `ON_START`(app 进前台)和 `ON_STOP`(app 进后台)事件
- 进后台 → `bubbleController?.show()`;进前台 → `bubbleController?.hide()`
- **关键**:`handleStart` 不再立即调 `ensureBubble()`,只在进后台事件触发时才 show

### 修复 2: 拖动到边缘缩小 / 拖出展开(已是 v1.1 实现,需验证)

**当前代码**: `FloatingBubbleController.handleTouch` 已有:
- `ACTION_MOVE`: 更新 params.x/y,clamp 到屏幕边界
- `ACTION_UP`: `if (moved && params.x < dp(36)) collapse(); else if (!moved && collapsed) expand()`
- `collapse()`: width=36dp, x=6dp, 显示 moon icon
- `expand()`: width=200dp, x=40dp, 显示完整面板

按用户描述"和原型图一致" → v1.1 实现已对,**只验证即可**。

### 修复 3: 倒计时时间 + 暂停/停止按钮(已是 v1.1 实现,需验证)

**当前代码**:`BubbleContent` 已显示:
- 倒计时时间(`DurationFormatter.toMmSs(remainingMs)`)
- "暂停 / 继续"按钮(调 `repo.pause/resume`)
- "停止"按钮(调 `repo.stop`)
- "Sleepy Lock" 标题 + 计时中/已暂停状态
- 紫色渐变背景

**按用户描述"和原型图一致"** → v1.1 实现已对,**只验证即可**。

## Capabilities

### New Capabilities

无

### Modified Capabilities

无(spec 行为契约不变 — 原 spec 早就有"离开 app 时显示气泡"语义,只是实现未做)

## Impact

- **修改文件**:
  - `app/build.gradle.kts` + `gradle/libs.versions.toml`(加 `lifecycle-process` 依赖)
  - `app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt`(ProcessLifecycleOwner 订阅)
  - (可能)`app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt`(已有 show/hide,可能需要 minor 调整)
- **新依赖**:`androidx.lifecycle:lifecycle-process:2.8.2`(AndroidX 官方库,无新风险)
- **API 变更**:无
- **风险**:
  - `ProcessLifecycleOwner` 注册到 Service 的 application context,需要用 `application as LifecycleOwner`
  - 监听生命周期事件需在 `onDestroy` 取消订阅,避免内存泄漏

## Non-Goals

- 不动 `floating-countdown-widget` spec
- 不做锁屏(已修)
- 不做权限管理(已修)
- 不做 UI 调整(已修)
