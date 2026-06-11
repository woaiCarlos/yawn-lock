# Tasks: yawn-lock-polish

> 任务清单 — 3 UI 调整 + 3 bug 修复,顺序按依赖排列。
> 全部任务在 Android Studio / Gradle CLI 环境 (JDK 17, AGP 8.5+) 下执行。

## 1. UI: 滑块 / 预设 / CTA 文字

- [x] 1.1 修改 `TimerScreen.kt` 的 `PresetChips`:preset 列表从 `0.5/1.0/5.0/10.0` 改为 `600/1800/3600/7200`(秒),label 文案从 "30/1/5/10 + 秒/分钟" 改为 "10/30/1/2 + 分钟/小时"
- [x] 1.2 修改 `CustomDial`:
  - 内部状态从 `minutes: Double` 改为 `seconds: Long`(精确到秒)
  - 滑块 `valueRange` 从 `1f..120f` 改为 `5f..7200f`
  - 滑块首尾标签从 "1分/120分" 改为 "5秒/2时"
  - ± 按钮步进改为: `< 60` 秒步 5,`60-299` 秒步 30,`>= 300` 秒步 60
  - 显示逻辑:`seconds < 60` → "X 秒",否则 → "X 分钟"
- [x] 1.3 `TimerViewModel.setMinutes(m: Double)` 改为 `setSeconds(s: Long)`,内部转 ms 后 `repo.preview()`
- [x] 1.4 改 `StartCta` 文案 "开始锁屏" → "开始计时"
- [x] 1.5 提交

```bash
git add app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerViewModel.kt
git commit -m "feat(timer): rewrite preset/slider/CTA for second precision"
```

## 2. Bug: Timer 屏幕 → Permissions 屏幕导航入口

- [x] 2.1 在 `HeroCard` 内右上角加 `IconButton`(图标 `Icons.Default.Settings` 或 `Lock`),`onClick = onNavigatePermissions`
- [x] 2.2 把 `onNavigatePermissions` 回调从 `TimerScreen` 顶层签名传到 `HeroCard`
- [x] 2.3 提交

```bash
git add app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt
git commit -m "feat(timer): add permissions entry icon in hero card"
```

## 3. Bug: 权限实时刷新

- [x] 3.1 `MainActivity` 实现 `DefaultLifecycleObserver`,在 `onResume` 调用 `(application as YawnApplication).timerRepository` 的扩展或直接 `permissionsViewModel.refresh()`
- [x] 3.2 `onCreate` 中 `lifecycle.addObserver(observer)`;`onDestroy` 中 `removeObserver(observer)`
- [x] 3.3 提交

```bash
git add app/src/main/kotlin/com/example/yawnlock/MainActivity.kt
git commit -m "fix(perm): refresh permission state on activity resume"
```

## 4. Bug: AppNavHost startDest 响应式 code smell

- [x] 4.1 `AppNavHost`:`val startDest = ...` 改为 `val startDest = remember { if (perms.canStartCountdown) "timer" else "permissions" }`
- [x] 4.2 提交

```bash
git add app/src/main/kotlin/com/example/yawnlock/MainActivity.kt
git commit -m "fix(nav): lock startDest with remember to avoid recompose side-effects"
```

## 5. Bug: 气泡/倒计时 — FloatingBubbleController 健壮性

- [x] 5.1 `FloatingBubbleController.show()`:`catch (e: BadTokenException)` 改为 `catch (e: Exception)`(含 SecurityException / RuntimeException)
- [x] 5.2 `init` 块的 `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` 改为 `DisposeOnDetachedFromWindowOrReleasedFromPool`(WindowManager 视图无 lifecycle owner)
- [x] 5.3 提交

```bash
git add app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt
git commit -m "fix(bubble): broaden exception catch + use detach-based dispose strategy"
```

## 6. Bug: 气泡失败 → ticker 死防御

- [x] 6.1 `CountdownService.handleStart`:`ensureBubble()` 用 try/catch 包住,失败 log warning 但不让 ticker 死
- [x] 6.2 提交

```bash
git add app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt
git commit -m "fix(service): decouple ticker from bubble success in handleStart"
```

## 7. Build + 冒烟

- [x] 7.1 `./gradlew :app:assembleDebug` 通过
- [x] 7.2 APK 重新生成

```bash
. ./.env.sh && ./gradlew :app:assembleDebug 2>&1 | tail -20
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

- [x] 7.3 提交(若 build 改动了配置)

```bash
git add -A
git commit -m "build: assembleDebug after polish changes" || echo "no changes"
```

## Self-Review

- 6 commits 计划(1.5 / 2.3 / 3.3 / 4.2 / 5.3 / 6.2)+ 1 build housekeeping
- 修改文件预计 5-6 个:TimerScreen, TimerViewModel, MainActivity, FloatingBubbleController, CountdownService
- 字符串 strings.xml 暂不动(只在 .comet.yaml 报告里建议 v1.2 统一)
