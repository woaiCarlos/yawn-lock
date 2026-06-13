# Tasks

## 1. 写失败测试 (TDD red)

- [x] 1.1 删掉 `preview_while_Active_is_noop` 旧测试(测的就是 buggy 行为,不再有意义)
- [x] 1.2 加 `preview_while_Counting_resets_to_new_full_duration`:30s → start → preview(25s) → state=Counting+25s+25s
- [x] 1.3 加 `preview_while_Counting_handles_smaller_and_larger_values`:45s → start → preview(15s) → state=Counting+15s+15s
- [x] 1.4 加 `preview_while_Paused_resets_to_new_full_duration`:30s → start → pause → preview(25s) → state=Paused+25s+25s
- [x] 1.5 加 `preview_from_Counting_keeps_status_but_resyncs_deadline`:反射读 `deadlineElapsed`,确认等于 `now + newDuration` ± 100ms

## 2. 修 `TimerRepository.preview()`

- [x] 2.1 删掉 `if (current.isActive) return` 守卫
- [x] 2.2 `current.isActive` 时额外执行 `deadlineElapsed = now + durationMs`(用 `SystemClock.elapsedRealtime()`)

## 3. 验证

- [x] 3.1 `./scripts/build.sh :app:testDebugUnitTest` → 10/10 通过(6 旧 + 4 新)
- [x] 3.2 `./scripts/build.sh :app:assembleDebug` → BUILD SUCCESSFUL
- [x] 3.3 `./scripts/build.sh :app:assembleRelease` → BUILD SUCCESSFUL,产出新 release APK(签名沿用 release.keystore,指纹会变)
