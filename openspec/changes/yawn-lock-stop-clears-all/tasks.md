# Tasks

## 1. 写失败测试 (TDD red)

- [x] 1.1 删 `stop_preserves_duration_and_returns_to_Idle`(测的就是要替换的 buggy 行为)
- [x] 1.2 加 `stop_fully_clears_state_no_preservation`:preview(10s) → start → stop → state=Idle+0+0
- [x] 1.3 加 `stop_while_idle_still_resets_to_zero`:preview(10s) → stop → state=Idle+0+0
- [x] 1.4 改写 `bug_scenario`:stop 后的「保留 10s」断言改成「全清 0L」
- [x] 1.5 删 `preview_while_Counting_resets_to_new_full_duration`(1.0.2 行为,被新语义覆盖)
- [x] 1.6 删 `preview_while_Counting_handles_smaller_and_larger_values`(同上)
- [x] 1.7 改写 `preview_while_Paused_resets_to_new_full_duration` → `preview_while_Paused_resets_to_Idle_with_new_duration`:Paused 状态 preview 后 status=Idle
- [x] 1.8 新加 `preview_while_Counting_resets_to_Idle_with_new_duration`:Counting 状态 preview 后 status=Idle
- [x] 1.9 改写 `preview_from_Counting_keeps_status_but_resyncs_deadline` → `preview_from_Counting_does_not_resync_deadline`:验证 deadlineElapsed 不被 preview 重置

## 2. 修 `TimerRepository.stop() + preview()`

- [x] 2.1 stop() 改为 `_state.value = TimerState()`(全清)
- [x] 2.2 preview() 改为无条件 `_state.value = TimerState(status=Idle, durationMs=N, remainingMs=N)`
- [x] 2.3 删除 preview() 里 deadlineElapsed 同步块

## 3. 验证

- [x] 3.1 `./scripts/build.sh :app:testDebugUnitTest` → 10/10 通过
- [x] 3.2 `./scripts/build.sh :app:assembleDebug` → BUILD SUCCESSFUL
- [x] 3.3 `./scripts/build.sh :app:assembleRelease` → BUILD SUCCESSFUL,产出新 release APK
