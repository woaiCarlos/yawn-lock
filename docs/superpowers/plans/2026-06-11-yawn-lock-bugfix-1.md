---
change: yawn-lock-bugfix-1
design-doc: openspec/changes/yawn-lock-bugfix-1/design.md
base-ref: 4163368cc8fa7912afb7471cbaa85bb607c7e167
---

# yawn-lock-bugfix-1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复打完哈欠 v1.1 暴露的 3 个运行时 bug(状态栏遮挡/无悬浮窗/5 秒不锁屏)。

**Architecture:** 行为修复,无架构变更,无新依赖。修改 3 个文件:
- `MainActivity.kt` — 加 `windowInsetsPadding(WindowInsets.systemBars)`
- `FloatingBubbleController.kt` — `setContent` 顺序移到 `addView` 之后
- `CountdownService.kt` — 短时长用 `Handler.postDelayed`,长时长保留 `AlarmManager.setExactAndAllowWhileIdle`

**Tech Stack:** Kotlin 1.9.24, AGP 8.5.0, Jetpack Compose BOM 2024.06.00, minSdk 26, targetSdk 34, JDK 17。

**测试策略:** 沿用 v1.1 polish 验证的 3 个新场景 + v1 8 步冒烟回归。

---

## 详细任务

所有任务的完整代码、提交命令、构建命令都在 OpenSpec change 目录:

**`openspec/changes/yawn-lock-bugfix-1/tasks.md`**

执行顺序(按风险递增,先修最严重的):

1. **Bug 3: 5 秒后不锁屏** — `CountdownService.kt` 改 `scheduleAlarm` 为 `scheduleEnd` 混合调度
2. **Bug 2: 授权后无悬浮窗** — `FloatingBubbleController.kt` 把 `setContent` 从 init 移到 `show()` 的 `addView` 之后
3. **Bug 1: 状态栏遮挡** — `MainActivity.kt` 给根 `Surface` 加 `windowInsetsPadding(WindowInsets.systemBars)`
4. **Build 验证** — `./gradlew :app:assembleDebug`

每个任务 1 个 commit,commit message 模板见 tasks.md。

---

## File Map

| 路径 | 修复 |
|------|------|
| `app/src/main/kotlin/com/example/yawnlock/MainActivity.kt` | Bug 1:insets |
| `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt` | Bug 2:setContent 顺序 |
| `app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt` | Bug 3:混合调度 |

无新文件,无资源变更,无 strings.xml 改动。

---

## Self-Review

- [x] **Spec coverage**: 3 个 bug 修复,每个对应一个文件改动,符合 hotfix 适用条件
- [x] **Placeholder scan**: 无 TBD/TODO;所有代码块完整
- [x] **Type consistency**:
  - `endRunnable` 定义在 CountdownService 一次,使用于 handleStart/handleResume
  - `companion object { TAG = "FloatingBubble" }` 一次性定义
  - `triggerLockNow()` 统一锁屏入口
- [x] **Scope check**: 4 任务组 / 约 15 sub-tasks,每个 ≤ 30 行代码变更

---

## End of Plan

实施完成后:
```bash
. ./.env.sh && ./gradlew :app:assembleDebug
```

构建成功 + 3 个 bug 修复,继续 `/comet-build` 退出流程。
