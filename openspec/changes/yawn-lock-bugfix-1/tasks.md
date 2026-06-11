# Tasks: yawn-lock-bugfix-1

> 3 个 bug 修复,顺序按风险递减(优先修最严重的 bug 3 锁屏失效,再修 bug 2 气泡不显示,最后修 bug 1 状态栏)。

## 1. Bug 3: 5 秒后不锁屏 — 混合调度(short=Handler, long=AlarmManager)

**Files:** `app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt`

- [x] **Step 1: 添加 endRunnable 和 scheduleEnd 调度方法**

在 `CountdownService.kt` 的 `ticker` Runnable **之前**添加:

```kotlin
    private val endRunnable = Runnable {
        val s = repo.state.value
        if (s.status !is TimerStatus.Counting) return@Runnable
        triggerLockNow()
    }

    private fun triggerLockNow() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.DevicePolicyManager
        val admin = android.content.ComponentName(this, com.example.yawnlock.data.DeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
        } else {
            val fallback = android.content.Intent(this, LockedFallbackActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(fallback)
        }
        repo.onAlarmFired()
    }

    private fun scheduleEnd(durationMs: Long) {
        handler.removeCallbacks(endRunnable)
        if (durationMs <= 5L * 60L * 1000L) {
            // 短时长:进程内 Handler 调度,可靠不依赖 OS scheduler
            handler.postDelayed(endRunnable, durationMs)
        } else {
            // 长时长:AlarmManager(走原 scheduleAlarm 路径)
            val state = repo.state.value
            scheduleAlarm(state)
        }
    }
```

Add import at top: `import com.example.yawnlock.data.DeviceAdminReceiver` (if not already imported)

- [x] **Step 2: 在 `handleStart` 和 `handleResume` 用 `scheduleEnd` 替换 `scheduleAlarm`**

Find in `CountdownService.kt`:

```kotlin
    private fun handleStart() {
        val state = repo.state.value
        if (state.status !is TimerStatus.Counting) return
        startForegroundCompat(state)
        scheduleAlarm(state)  // <-- 改这行
        try { ensureBubble() } catch (e: Exception) { ... }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun handleResume() {
        ...
        repo.resume()
        scheduleAlarm(state)  // <-- 改这行
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }
```

Replace `scheduleAlarm(state)` with `scheduleEnd(state.remainingMs)` in both methods.

- [x] **Step 3: 在 `handlePause` 中清理 endRunnable**

Find in `CountdownService.kt`:
```kotlin
    private fun handlePause() {
        val state = repo.state.value
        if (state.status !is TimerStatus.Counting) return
        repo.pause()
        cancelAlarm()
        NotificationCenter.update(this, state.remainingMs, isPaused = true)
    }
```

Add `handler.removeCallbacks(endRunnable)` before `cancelAlarm()`:
```kotlin
    private fun handlePause() {
        val state = repo.state.value
        if (state.status !is TimerStatus.Counting) return
        repo.pause()
        handler.removeCallbacks(endRunnable)  // <-- 新增
        cancelAlarm()
        NotificationCenter.update(this, state.remainingMs, isPaused = true)
    }
```

- [x] **Step 4: 在 `ACTION_STOP` 处理中清理 endRunnable**

Find in `onStartCommand`:
```kotlin
            ACTION_STOP -> { cancelAlarm(); repo.stop(); stopSelf() }
```

Replace with:
```kotlin
            ACTION_STOP -> { handler.removeCallbacks(endRunnable); cancelAlarm(); repo.stop(); stopSelf() }
```

- [x] **Step 5: 在 `onDestroy` 中清理 endRunnable**

Find:
```kotlin
    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        bubble?.hide()
        bubble = null
        NotificationCenter.cancel(this)
        super.onDestroy()
    }
```

Add `handler.removeCallbacks(endRunnable)` after `handler.removeCallbacks(ticker)`:
```kotlin
    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(endRunnable)  // <-- 新增
        bubble?.hide()
        bubble = null
        NotificationCenter.cancel(this)
        super.onDestroy()
    }
```

- [x] **Step 6: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 7: 提交**

```bash
git add app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt
git commit -m "fix(lock): use Handler.postDelayed for short timers (≤ 5 min)

Root cause: am.setAndAllowWhileIdle() (the fallback when
canScheduleExactAlarms() returns false) has a 15-minute batch window
per app, so 5-second timers never fire reliably on devices/OEMs that
don't grant USE_EXACT_ALARM.

Fix: hybrid scheduling
- durationMs <= 5 min: Handler.postDelayed in Service (in-process,
  no OS scheduler delay, no permission needed)
- durationMs > 5 min: AlarmManager.setExactAndAllowWhileIdle
  (existing path, may be inexact on some devices but acceptable for
  long durations)

The endRunnable calls dpm.lockNow() directly when it fires. Cleanup
in handlePause, ACTION_STOP, and onDestroy prevents stale callbacks.

This unifies lock execution in one place (triggerLockNow) used by
both Handler and AlarmManager paths."
```

## 2. Bug 2: 授权后无悬浮窗 — setContent 顺序修正

**Files:** `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt`

- [x] **Step 1: 移除 init 块中的 setContent,加 TAG 常量**

Find in `FloatingBubbleController.kt`:
```kotlin
    init {
        bubbleView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        bubbleView.setContent {
            BubbleContent(
                remainingMs = remainingMs,
                isPaused = isPaused,
                collapsed = collapsed,
                onPauseToggle = ::togglePause,
                onStop = ::stopCountdown,
            )
        }
        bubbleView.setOnTouchListener { _, ev -> handleTouch(ev) }
    }
```

Add `companion object` with TAG constant at class level (place after the closing brace of the class, before `@Composable private fun BubbleContent`):

```kotlin
    companion object {
        private const val TAG = "FloatingBubble"
    }
```

(Place inside the class body, after the class properties)

Replace init block:
```kotlin
    init {
        bubbleView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
        bubbleView.setOnTouchListener { _, ev -> handleTouch(ev) }
    }
```

- [x] **Step 2: 改写 show() — 先 addView,再 setContent**

Find in `FloatingBubbleController.kt`:
```kotlin
    fun show() {
        try {
            wm.addView(bubbleView, params)
        } catch (e: Exception) {
            // 含 BadTokenException / SecurityException / RuntimeException,静默忽略
        }
        // 启动时同步当前状态
        val s = (context.applicationContext as YawnApplication).timerRepository.state.value
        remainingMs = s.remainingMs
        isPaused = s.status is com.example.yawnlock.domain.TimerStatus.Paused
    }
```

Replace with:
```kotlin
    fun show() {
        try {
            wm.addView(bubbleView, params)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "addView failed; bubble will not show", e)
            return  // addView failed, don't setContent on a not-attached view
        }
        // view is now attached; safe to setContent (Compose can find view tree owner)
        bubbleView.setContent {
            BubbleContent(
                remainingMs = remainingMs,
                isPaused = isPaused,
                collapsed = collapsed,
                onPauseToggle = ::togglePause,
                onStop = ::stopCountdown,
            )
        }
        // sync initial state
        val s = (context.applicationContext as YawnApplication).timerRepository.state.value
        remainingMs = s.remainingMs
        isPaused = s.status is com.example.yawnlock.domain.TimerStatus.Paused
    }
```

- [x] **Step 3: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 4: 提交**

```bash
git add app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt
git commit -m "fix(bubble): setContent after addView to fix WindowManager attach order

Root cause: setContent() was called in the init block, before the
ComposeView was attached to any window. setContent creates a Composition
but cannot find a view tree LifecycleOwner (or SavedStateRegistryOwner)
when called on a detached view. After wm.addView() attached the view,
the existing Composition was not re-rendered because no state change
triggered recomposition. The view was added to WindowManager but the
gradient Box, time text, and buttons never drew.

Fix: move setContent out of init, into show() AFTER wm.addView succeeds.
The view is attached at that point, so Compose's internal setParent
context resolution works correctly.

Also adds:
- Log.w on addView failure (instead of silent swallow) for diagnosability
- companion TAG constant 'FloatingBubble'
- early return on addView failure (don't setContent on a non-attached view)"
```

## 3. Bug 1: 状态栏遮挡 — Insets 处理

**Files:** `app/src/main/kotlin/com/example/yawnlock/MainActivity.kt`

- [x] **Step 1: 改 setContent,加 windowInsetsPadding**

Find in `MainActivity.kt`:
```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super<ComponentActivity>.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycle.addObserver(this)
        setContent {
            YawnLockTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(permsVm = permsVm)
                }
            }
        }
    }
```

Replace with:
```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        super<ComponentActivity>.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycle.addObserver(this)
        setContent {
            YawnLockTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    AppNavHost(permsVm = permsVm)
                }
            }
        }
    }
```

Add imports at top of `MainActivity.kt`:
```kotlin
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
```

- [x] **Step 2: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 3: 提交**

```bash
git add app/src/main/kotlin/com/example/yawnlock/MainActivity.kt
git commit -m "fix(ui): add windowInsetsPadding(systemBars) to root Surface

Root cause: enableEdgeToEdge() extends content under the system bars
(transparent status + navigation), but the root Surface used
.fillMaxSize() with no insets consumption. HeroCard, SectionHeader,
and other top content drew at y=0, behind the status bar.

Fix: wrap Surface with windowInsetsPadding(WindowInsets.systemBars).
This shifts the entire content area inward from both status bar and
navigation bar. Pairs correctly with enableEdgeToEdge()."
```

## 4. Build 验证

- [x] **Step 1: 完整 assembleDebug**

```bash
. ./.env.sh && ./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL` + APK at `app/build/outputs/apk/debug/app-debug.apk`

- [x] **Step 2: 验证 APK**

```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

- [x] **Step 3: 提交(若有 build 配置变更)**

```bash
git status
git add -A
git commit -m "build: assembleDebug after bugfix-1" || echo "no changes"
```

---

## Self-Review

- 3 个 fix 各 1 个 commit,共 3-4 个 commits
- 3 个文件被修改:CountdownService.kt, FloatingBubbleController.kt, MainActivity.kt
- 字符串 strings.xml 暂不动
- 不需要 delta spec(spec 行为契约不变)
- 不需要 brainstorming(根因明确,修复方案单一)
