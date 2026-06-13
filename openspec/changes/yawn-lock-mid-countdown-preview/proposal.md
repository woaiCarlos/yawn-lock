## Why

release 1.0.1 的 bubble-restart 修好之后,用户真机回归又发现第二个 bug:**倒计时进行中,滚轮调时间不生效**。

复现路径(用户报告 100% 复现):
1. 设 30s,点开始,气泡出现,倒计时正常
2. 滚轮从 30s 滑到 25s
3. **气泡和倒计时仍按 30s 跑**,完全忽略新值
4. 取消,设 10s,重启 → 正常(因为 stop 后 isActive=false,preview 才生效)
5. 第二次:设 45s,开始,滑到 15s → 仍按 45s 跑

`app/src/main/kotlin/com/example/yawnlock/domain/TimerRepository.kt` 里的 `preview()` 有个显式守卫:

```kotlin
fun preview(durationMs: Long) {
    val current = _state.value
    if (current.isActive) return   // ← bug:倒计时进行中整个 preview no-op
    ...
}
```

`isActive` = `Counting || Paused`。所以 Counting 和 Paused 状态下,`preview()` 都在第一行直接 return。这是有意设计的(防「倒计时中误触滚轮导致 timer 跳变」),但**用户体验上反直觉** —— 滚轮既然能滚,用户就期望它生效;不让它生效应该禁掉滚轮,而不是静默吞掉。

## What Changes

- `TimerRepository.preview()` 删掉 `if (current.isActive) return` 守卫
- preview() 在 `current.isActive` 时,**额外**把 `deadlineElapsed = now + durationMs`,否则下次 `tick()` 会用旧 deadline 算出错的 remaining(用户报告的「仍按 30s 倒计时」现象,部分原因就是 deadline 还在 30s 后)
- 用户滚到新值 = 倒计时按新值**重置**为完整时长,从新值开始倒计时(语义:「我改主意了,新时长才是我要的」)
- 4 个新单元测试覆盖 Counting / Paused 两条路径 + 较小/较大新值 + deadlineElapsed 同步
- 不动 `TimerViewModel`、`TimerScreen`、`CountdownService`、manifest

## Capabilities

### New Capabilities
无。

### Modified Capabilities
无。本次单文件纯逻辑 fix,不动任何 spec 级验收。

## Impact

- **代码/资源**:`app/src/main/kotlin/com/example/yawnlock/domain/TimerRepository.kt`(1 处修改,删 1 行加 5 行)、`app/src/test/kotlin/com/example/yawnlock/domain/TimerRepositoryStateTest.kt`(1 个旧测试改语义 + 4 个新测试)
- **API / 架构 / 依赖**:无
- **manifest / 权限**:无
- **release 影响**:之前用 1.0.0 / 1.0.1 APK 的用户升级到 1.0.2 后,中间改时间的行为变了(从「no-op」变成「重置为新时长」)。这是用户明确要求的修复,行为变化是预期内的。
