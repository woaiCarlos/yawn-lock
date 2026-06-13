## Context

- 1.0.0/1.0.1 行为:preview() 在 Counting/Paused 时 no-op,stop() 保留 durationMs
- 1.0.2 行为(yawn-lock-mid-countdown-preview):preview() 在 Counting/Paused 时改 duration/remaining 但保留 status;同步 deadlineElapsed;stop 仍保留 durationMs
- 1.0.3 行为(本 change):preview() 任何状态都直接 Idle+newDuration;stop() 完全清空
- 用户反馈路径:release 1.0.2 真机跑用户原话「还是不行」—— 之前的 fix 方向错了

## Goals / Non-Goals

**Goals:**
- stop() 完全清空所有状态(`TimerState()` = Idle+0+0)
- preview() 任何状态都重置为 Idle+newDuration+newDuration,要求重新点 Start
- 6 个单测覆盖新语义(包括 stop 全清、preview 任何状态回 Idle、deadline 不再同步)
- 旧 bug_scenario 测试更新语义(stop 后不再保留 10s)

**Non-Goals:**
- 不动 TimerViewModel、TimerScreen、CountdownService
- 不动 spec
- 不动 manifest / 依赖
- 不做「保留已过比例」语义(用户没要求)
- 不做 UI 改动(滚轮灰显等)

## Decisions

- **决策 1:stop() 改为完全清空 `TimerState()`**
  - 理由:用户明确要求「点了停止 = 我不干了」语义,清空比保留更符合直觉
  - 备选:保留 status=Paused+新 duration(像「软重置」)—— 用户没说,也不在「停止」语义范围内
- **决策 2:preview() 任何状态都直接重置为 Idle**
  - 理由:用户明确要求「清掉之前的状态,重新开始计时」(要重新点 Start 才计时)
  - 备选 A:Preview 在 Counting 时继续按新值跑(1.0.2 行为) —— 用户实测反馈不行
  - 备选 B:Preview 区分 Counting/Paused/Finished 三种情况 —— 复杂,且跟「改主意就清」语义不符
  - 备选 C:Preview 在 Paused 时也清掉 pause —— 跟 Counting 统一,简化逻辑
- **决策 3:不再同步 `deadlineElapsed`**
  - 理由:preview 之后 status=Idle,tick() 不会跑;下次 start() 会重新设。保留旧值无意义
  - 旧行为同步 deadlineElapsed 是为了「preview 后 tick() 用新 deadline」,但既然 status 变 Idle 了 tick() 根本不会跑
- **决策 4:不动 vm.start() 的 Idle 守卫**
  - 理由:vm.start() 已经要求 status=Idle 才能调 repo.start(),跟新语义「preview 后必须重新点 Start」天然契合
  - 备选:让 vm.start() 在 Paused 时自动 resume —— 跟「要求重新点 Start」冲突,且语义混乱
- **决策 5:Paused 状态下点 stop 按钮走 stop() 而非 resume() 后 stop()**
  - 理由:bubble.stopCountdown 直接调 repo.stop(),不走 resume。stop() 的「全清」语义对 Paused 同样适用
  - 备选:在 stop() 里特判 Paused 走不同路径 —— 增加复杂度,无收益

## Risks / Trade-offs

- 风险 1:**release 行为大变化** —— 1.0.0/1.0.1/1.0.2 用户的「stop 后 wheel 还显示旧值,可以一键再开」工作流被破坏
  - 缓解:这是用户明确要求的修复,行为变化是预期内的。在 commit message 和 release notes 里强调
- 风险 2:用户连续改 wheel 会丢失「快速回到原值」的能力
  - 缓解:用户的语义是「我改主意了,新值才是我要的」,不再需要回到旧值
- 风险 3:bubble 显示与 stop 后状态不一致
  - 缓解:stop() 调用方是 bubble.stopCountdown(),它本身先调 repo.stop() 再调 hide(),bubble 永远看不到 stop 后的状态;bubble 被 hide 后,新 Start 走 timerScreen 入口

## Migration Plan

- 一次提交完成。revert 即回滚到 1.0.2「preview 改时间但 timer 继续按新值跑」+「stop 保留 durationMs」状态

## Open Questions

无。
