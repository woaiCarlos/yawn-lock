# Tasks

## 1. 修 `FloatingBubbleController.hide()`

- [x] 1.1 把 `attached = false` 移到 `finally` 块,保证 `wm.removeView` 抛不抛都重置
- [x] 1.2 catch 里加 Log.w 记录 removeView 失败,方便后续真机日志排查

## 2. 修 `FloatingBubbleController.show()`

- [x] 2.1 `attached = true` 移到 try 之外(同一种脆弱模式的对称修复)

## 3. 加 TimerRepository 状态机测试

- [x] 3.1 `app/build.gradle.kts` 加 `testImplementation("junit:junit:4.13.2")` 和 `testImplementation("org.robolectric:robolectric:4.13")`
- [x] 3.2 写 `app/src/test/kotlin/com/example/yawnlock/domain/TimerRepositoryStateTest.kt`,7 个测试覆盖:
  - fresh repo = Idle+0
  - preview 从 Idle 设 duration
  - start 从 Idle → Counting
  - stop 保留 duration 回 Idle
  - **bug 场景:stop → preview 20s → start 完整路径**
  - preview 在 Active 时是 no-op
  - preview 从 Finished 重置成 Idle

## 4. 验证

- [x] 4.1 `./gradlew :app:testDebugUnitTest` → 7/7 通过
- [x] 4.2 `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL,rename 任务产出 `yawn-lock-1.0.0-debug.apk`
