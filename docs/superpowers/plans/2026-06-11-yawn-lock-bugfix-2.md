---
change: yawn-lock-bugfix-2
design-doc: openspec/changes/yawn-lock-bugfix-2/design.md
base-ref: 01c78a0d6e8ce7f18c8e4b4ec11f6e7c8a9b0c1d
archived-with: 2026-06-11-yawn-lock-bugfix-2
---

# yawn-lock-bugfix-2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 基于网络调研 + 代码审计的真实根因,修复 2 个仍未解决的运行时 bug(气泡不显示 + 5 秒不锁屏),并补全跨切面诊断日志,让用户能通过 `adb logcat` 自助定位。

**Architecture:** 纯行为修复,无新依赖,无新 capability。修改 ~6 个文件:
- 核心修复:`FloatingBubbleController.kt` (ViewTreeLifecycleOwner 注入)、`CountdownService.kt` (诊断日志 + 授权检查)、`LockedFallbackActivity.kt` (实现 UI 替换 stub)、`LockReceiver.kt` (诊断日志)
- 防御性:`AndroidManifest.xml` (LockReceiver intent-filter)、`NotificationCenter.kt` (新方法 showAdminMissingWarning)、`strings.xml` (2 个新文案)

**Tech Stack:** Kotlin 1.9.24, AGP 8.5.0, Jetpack Compose BOM 2024.06.00, AndroidX Lifecycle 2.8.2, AndroidX SavedState, minSdk 26, targetSdk 34, JDK 17。

**测试策略:** 用户真机冒烟(8 步 v1 + 3 步 polish + 1 步新:锁屏 5 秒后真锁,不再黑屏)。`adb logcat -s CountdownService LockReceiver PermissionChecker FloatingBubble` 用于诊断。

archived-with: 2026-06-11-yawn-lock-bugfix-2
---

## 详细任务

所有任务的完整代码、提交命令、构建命令都在 OpenSpec change 目录:

**`openspec/changes/yawn-lock-bugfix-2/tasks.md`**

执行顺序(按风险递增):

1. **Bug 2 真正修复(气泡)** — `FloatingBubbleController.kt` 显式注入 `ViewTreeLifecycleOwner` / `SavedStateRegistryOwner` / `ViewModelStoreOwner`
2. **Bug 3 真正修复(锁屏)+ 诊断** — 6 个文件改动,加 Log.d 到所有关键路径
3. **Build 验证** — `./gradlew :app:assembleDebug`

每个 task 1-2 个 commit,commit message 模板见 tasks.md。

archived-with: 2026-06-11-yawn-lock-bugfix-2
---

## File Map

| 路径 | 改动 |
|------|------|
| `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt` | 注入 ViewTree owner |
| `app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt` | 诊断日志 + 授权检查 |
| `app/src/main/kotlin/com/example/yawnlock/service/LockReceiver.kt` | 诊断日志 |
| `app/src/main/kotlin/com/example/yawnlock/service/LockedFallbackActivity.kt` | 实际 UI 替换 stub |
| `app/src/main/kotlin/com/example/yawnlock/data/NotificationCenter.kt` | 新方法 showAdminMissingWarning |
| `app/src/main/AndroidManifest.xml` | LockReceiver 加 intent-filter |
| `app/src/main/res/values/strings.xml` | 2 个新文案 |

无新文件,无 spec 变更,无资源 layout 变更。

archived-with: 2026-06-11-yawn-lock-bugfix-2
---

## Self-Review

- [x] **Spec coverage**: 2 个 bug 修复,各对应一组文件改动,符合 hotfix 适用条件
- [x] **Placeholder scan**: 无 TBD/TODO;所有代码块完整
- [x] **Type consistency**: TAG 一次性定义,NotificationCenter.NOTIF_ID + 1 用作 admin warning ID
- [x] **Scope check**: 2 任务组,跨 6 文件,但每个 ≤ 50 行代码变更

archived-with: 2026-06-11-yawn-lock-bugfix-2
---

## End of Plan

实施完成后:
```bash
. ./.env.sh && ./gradlew :app:assembleDebug
```

构建成功,继续 `/comet-build` 退出流程。
