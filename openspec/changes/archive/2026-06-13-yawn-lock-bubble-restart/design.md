## Context

- `FloatingBubbleController` 是单例,在 `YawnApplication.onCreate` 里通过 `bubbleController` lazy 创建,跨整个 app 生命周期
- 同一实例上,`attached: Boolean` 是「气泡当前是否在 WindowManager 里」的唯一事实来源
- `show()` / `hide()` 反复被调用:每次 start 倒计时调 show,每次 stop 调 hide,用户每次回前台 ON_START 调 hide

## Goals / Non-Goals

**Goals:**
- `hide()` 在任何情况下(成功/异常)都必须把 `attached` 重置为 false
- `show()` 在 addView 成功后才把 `attached` 置 true(失败保持 false,允许下次重试)
- 加单元测试,固化 TimerRepository 状态机行为,防止后续退化解

**Non-Goals:**
- 不重写 `FloatingBubbleController` 的整体架构(动画、拖动、折叠等都不动)
- 不引入新依赖(Robolectric 4.13 是为了 test 需要,但 main src 不依赖)
- 不改 `MainActivity.onUserLeaveHint` / `YawnApplication` 的 `ProcessLifecycle` 观察者(它们的逻辑是对的,问题是 bubble 内部状态机)
- 不改 `CountdownService` ticker(它在服务被销毁后会被新实例的 handler 重新拉起,问题不在 ticker)

## Decisions

- **决策 1:`hide()` 用 `finally` 重置 attached**
  - 理由:`attached` 是 controller 内部状态,不受 `wm.removeView` 副作用结果影响。WindowManager 抛异常不代表 bubble view 还在 window 里(view 可能已经 detach 了,只是我们没成功 remove);也可能 view 真的还在,但下一次 `show()` 调 `wm.addView` 会因为「View already attached」抛 BadTokenException,这时候我们还是想让 `attached` 反映「我们打算认为它没在 window」,然后下一次 show 的 addView 会失败、attached 还是 false,可以再重试
  - 备选 A:把 `attached = false` 放 try 之外(不带 finally)—— 也能工作,但 finally 语义更清晰
  - 备选 B:在 catch 里加 retry 逻辑 —— 过度工程,根因不是 view 没被 remove,是状态标志位没重置
- **决策 2:`show()` 的 `attached = true` 移到 try 之外**
  - 理由:对称修复。如果 `addView` 失败,`attached` 保持 false,下次 show 还能重试
  - 跟 hide 对称:两个方向的「副作用 → 状态」都不在 try 里
- **决策 3:加 Robolectric 跑 TimerRepository 测试,不真模拟 FloatingBubbleController**
  - 理由:FloatingBubbleController 构造时 `LayoutInflater.from(context).inflate(R.layout.floating_bubble, null)` 在 JVM unit test 里 Robolectric 默认不 merge res/,会 NotFoundException。要测试 hide/show 的 `attached` 行为需要先解决这个
  - 但 TimerRepository 是纯逻辑(只依赖 `SystemClock.elapsedRealtime()`),Robolectric 直接给,7 个测试覆盖完整的 state 转换路径,把状态机这层固化住
  - 真要测 FloatingBubbleController 的话需要走 androidTest(instrumented),release 1.0.0 没这个基建,先不做
- **决策 4:不在 release note 里高调宣传这个 fix**
  - 理由:这是 1.0.0 发布后才修的二次补丁,不是大功能。直接 merge 到 main 之后随下一个小版本走

## Risks / Trade-offs

- 风险 1:加了 Robolectric 依赖(~5MB 下载),build 时间略增。Mitigation:Robolectric 只在 testImplementation 里,不进 production APK
- 风险 2:Robolectric 跑测试比纯 JVM 慢。Mitigation:7 个测试现在跑 ~30s,可接受
- 风险 3:`hide()` 用 `finally` 之后,如果 wm.removeView 真的成功了但 finally 抛了别的异常(理论上不会),attached 也会重置 —— 但这是好事(状态机优先于外部副作用)

## Migration Plan

- 一次提交完成。revert 即回滚到 1.0.0 的脆弱行为,用户能立刻看到 bubble 卡死的 bug 复现

## Open Questions

无。
