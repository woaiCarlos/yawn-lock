## Why

release 1.0.2 的 mid-countdown preview fix (change `yawn-lock-mid-countdown-preview`) 在真机上仍不能让用户满意。

用户实测反馈原文:
> 还是不行,我觉得这个地方如果我点了停止,应该清空所有的这种状态,我点的不是暂停啊,我点的是停止,然后如果点了暂停,然后回去又重新设置了,那也要清空之前的状态,然后重新开始计时

两个具体要求:
1. **stop() 应该完全清空状态**——不再保留 `durationMs`。当前 `stop()` 保留 durationMs(注释说「方便用户一键再开」),用户实测认为反直觉:既然按的是「停止」不是「暂停」,就应该归零,要重新开始就让用户重新选时间。
2. **preview() 任何状态下都重置为 Idle**——当前 preview() 在 Counting 时保留 status=Counting、Paused 时保留 status=Paused,只有 Finished 才转 Idle。用户要求:任何「改主意」动作(改滚轮)都应该彻底清掉旧状态,要求用户重新点 Start 才计时。

## What Changes

- `TimerRepository.stop()`:从「Idle+保留 durationMs+remainingMs=durationMs」改为「完全清空 `TimerState()`」(durationMs=0, remainingMs=0)
- `TimerRepository.preview()`:从「保留 Counting/Paused 状态,只 Finished → Idle」改为「任何状态都直接重置为 Idle+newDuration+newDuration」。同时删除 `deadlineElapsed = now + durationMs` 同步(不再需要,status=Idle 后 tick() 不跑,下次 start() 会重设)
- 6 个新/改的单元测试覆盖 stop 全清、preview 任何状态回 Idle、preview 不再同步 deadlineElapsed
- 1 个旧测试改语义:bug_scenario 里 stop 后的「保留 10s」断言改成「全清 0L」(反映新语义)

## Capabilities

### New Capabilities
无。

### Modified Capabilities
无。本次单文件纯逻辑 fix,不动任何 spec 级验收。

## Impact

- **代码/资源**:`app/src/main/kotlin/com/example/yawnlock/domain/TimerRepository.kt`(2 处函数体改写,删 ~10 行加 ~6 行)、`app/src/test/kotlin/com/example/yawnlock/domain/TimerRepositoryStateTest.kt`(删 1 旧 stop 测试,改 1 旧 bug_scenario 测试,加 4 新测试)
- **API / 架构 / 依赖**:无
- **manifest / 权限**:无
- **release 行为变化**:
  - 1.0.0 / 1.0.1 / 1.0.2:stop 后 wheel 仍显示旧 durationMs,可以直接再点 Start
  - 1.0.3:stop 后 wheel 显示 0,必须重新选时间再 Start
  - 1.0.0 / 1.0.1:mid-countdown 改时间 no-op
  - 1.0.2:mid-countdown 改时间 = timer 按新值继续
  - 1.0.3:mid-countdown / Paused 中改时间 = 完全清空,要求重新点 Start
