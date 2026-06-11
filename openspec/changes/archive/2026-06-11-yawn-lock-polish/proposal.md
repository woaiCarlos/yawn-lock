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
