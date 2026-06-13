## Context

- 之前 `preview()` 的 `if (current.isActive) return` 是 a277626 commit 时引入的(也改了 Finished → Idle 重置)
- 这个守卫的本意是防「倒计时中误触滚轮导致 timer 跳变」,但用户体验上反直觉 —— 滚轮既然能滚,用户就期望它生效
- 用户的两个测试用例(30→25、45→15)都确认是 mid-countdown preview 被吞,而不是 TimerScreen 没传值

## Goals / Non-Goals

**Goals:**
- preview() 在 Counting / Paused 状态下也更新 state,durationMs 和 remainingMs 都设为新值
- deadlineElapsed 同步重置,保证下次 tick() 用新 deadline
- Paused 状态下 preview 保持 Paused 状态(用户调时长后还是暂停,需要手动 resume)
- 4 个新单元测试覆盖 4 个核心场景

**Non-Goals:**
- 不改 TimerViewModel / TimerScreen / CountdownService
- 不做「保持已过比例」语义(把 30s 设、跑 15s、滑到 25s → 25s 剩余 + 50s 总时长 这种),太复杂,用户没要求
- 不做「滚轮灰显」UI 改动。滚轮既然能滚,让它生效
- 不改 manifest、不改权限、不改依赖

## Decisions

- **决策 1:preview() 删守卫,不再区分 active/inactive**
  - 理由:用户期望明确 ——「我滚到 25s,就是 25s」。守卫的反直觉代价 >> 误触保护的小收益
  - 备选 A:加 `force: Boolean = false` 参数 —— 加 API surface,默认 false 等于不修 bug,pass true 又得让用户每次滚都传 true,太麻烦
  - 备选 B:新增 `updateActiveDuration()` 方法,只在 active 调 —— 同样加 API,且 Compose 的 onChange 不知道调哪个,得在 ViewModel 里加 if 判断,臃肿
  - 备选 C:让 UI 端(滚轮)在 active 状态 disabled —— UI 复杂度上升,跟当前风格不一致
- **决策 2:preview() 在 active 时,deadlineElapsed 必须同步重置**
  - 理由:tick() = `remaining = deadlineElapsed - now`,不重置 deadline,新 remaining 被旧 deadline 覆盖,bug 表现为「滚到 25s 但仍按 30s 倒计时」(用户报告的就是这个)
- **决策 3:Paused 状态下 preview 保持 Paused,remaining 重置为新 duration**
  - 理由:用户暂停后改时长,通常是想「用新时长重新开始,先别动」,等他自己 resume。保持 Paused + 全 remaining 是最不意外的语义
  - 备选:Paused 时 preview 直接切到 Idle —— 失去「暂停中可改时长」的灵活性
- **决策 4:不写服务端的测试**
  - 理由:`tick()` 的 deadlineElapsed 同步靠反射测试覆盖,已验证 `now+durationMs` 落在合理窗口。服务端的 ticker 是 100ms 节奏的 `tick()`,逻辑跟 pause/resume 共用同一份 deadline,unit test 已经证明 state 在 tick 前正确

## Risks / Trade-offs

- 风险 1:用户滚动滚轮时,bubble 显示的剩余时间会「跳」到新值(从原 remaining 跳到新 duration)
  - 缓解:这是用户明确要求的(他滚就是想让 timer 用新值)。视觉上「跳」是一次性的,符合预期
- 风险 2:误触滚轮会导致 timer 跳变
  - 缓解:release 1.0.0 时期滚轮已经能滚,只是没生效;现在生效而已,不是新增风险
- 风险 3:release 行为变化 —— 1.0.0/1.0.1 用户升级到 1.0.2 后,改时间行为从「no-op」变「重置」
  - 缓解:这是用户主动要求修复的,行为变化是预期内的。在 commit message 和 release notes 里点明

## Migration Plan

- 一次提交完成。revert 即回滚到「mid-countdown preview no-op」状态

## Open Questions

无。
