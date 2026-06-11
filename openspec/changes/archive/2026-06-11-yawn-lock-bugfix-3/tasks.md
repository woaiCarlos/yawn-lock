# Tasks: yawn-lock-bugfix-3

> 气泡条件显示(仅 app 后台时显示)修复,基于 `ProcessLifecycleOwner`。1 个 commit + build 验证。

## 1. 加 `lifecycle-process` 依赖

**Files:** `gradle/libs.versions.toml`, `app/build.gradle.kts`

- [x] **Step 1: libs.versions.toml 加 lifecycle-process**

打开 `gradle/libs.versions.toml`,在 `[libraries]` 区块加:

```toml
androidx-lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle" }
```

- [x] **Step 2: app/build.gradle.kts 加依赖**

打开 `app/build.gradle.kts`,在 `dependencies { ... }` 区块加:

```kotlin
    implementation(libs.androidx.lifecycle.process)
```

(放在 `androidx.lifecycle.viewmodel.compose` 或 `androidx.lifecycle.runtime.ktx` 之后)

- [x] **Step 3: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

## 2. CountdownService 用 ProcessLifecycleOwner 驱动气泡显隐

**Files:** `app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt`

- [x] **Step 1: 加 imports**

在 `CountdownService.kt` 顶部加:

```kotlin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
```

- [x] **Step 2: 加 processLifecycleObserver 字段 + ensureBubble helper**

在 `class CountdownService` 内、`private val handler = Handler(...)` 之后加:

```kotlin
    private val processLifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                // app 进后台:仅在倒计时还在跑时显示气泡
                if (repo.state.value.isActive) {
                    ensureBubble()
                }
            }
            Lifecycle.Event.ON_START -> {
                // app 回前台:隐藏气泡(用户看 Timer 屏幕的 StatusCard 即可)
                bubble?.hide()
            }
            else -> { /* 其他事件忽略 */ }
        }
    }
```

(在 `private val ticker` Runnable 之前)

- [x] **Step 3: `onCreate` 订阅 ProcessLifecycleOwner**

替换 `onCreate`:

```kotlin
    override fun onCreate() {
        super.onCreate()
        NotificationCenter.ensureChannel(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processLifecycleObserver)
    }
```

- [x] **Step 4: `onDestroy` 取消订阅 + 清理**

替换 `onDestroy`:

```kotlin
    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processLifecycleObserver)
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(endRunnable)
        bubble?.hide()
        bubble = null
        NotificationCenter.cancel(this)
        super.onDestroy()
    }
```

- [x] **Step 5: `handleStart` 不再立即 ensureBubble(改由 ON_STOP 触发)**

替换 `handleStart`:

```kotlin
    private fun handleStart() {
        val state = repo.state.value
        if (state.status !is TimerStatus.Counting) return

        // 诊断:start 时的授权状态(用户可在 adb logcat 中验证)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = android.content.ComponentName(this, com.example.yawnlock.data.DeviceAdminReceiver::class.java)
        val isAdmin = dpm.isAdminActive(admin)
        val canOverlay = android.provider.Settings.canDrawOverlays(this)
        android.util.Log.d(TAG, "handleStart: isAdminActive=$isAdmin, canDrawOverlays=$canOverlay, status=${state.status}, remainingMs=${state.remainingMs}")

        if (!isAdmin) {
            android.util.Log.w(TAG, "device admin NOT active; lockNow() will fall back to LockedFallbackActivity")
            com.example.yawnlock.data.NotificationCenter.showAdminMissingWarning(this)
        }
        if (!canOverlay) {
            android.util.Log.w(TAG, "overlay permission NOT granted; bubble may not show")
        }

        startForegroundCompat(state)
        scheduleEnd(state.remainingMs)
        // 不再立即 ensureBubble —— 气泡由 ProcessLifecycle 的 ON_STOP 事件触发
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }
```

(只删除 `try { ensureBubble() } catch (...) { ... }` 那块,其余诊断逻辑保留)

- [x] **Step 6: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 7: 提交**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts \
        app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt
git commit -m "fix(bubble): show only when app is in background

Root cause: The previous bugfix-1/bugfix-2 fixes only made the
bubble's composition actually render, but the show/hide logic was
unconditional — wm.addView() was called in handleStart regardless of
whether the app was in the foreground or background. This contradicts
the spec (floating-countdown-widget) and the original prototype
(Web-Prototype/floating.html), both of which intend: no bubble while
on the Timer screen, bubble only when user leaves the app.

Fix: drive show/hide by ProcessLifecycleOwner events:
- ON_STOP (app goes to background): show bubble (if countdown active)
- ON_START (app returns to foreground): hide bubble
- handleStart no longer calls ensureBubble — bubble creation is
  deferred to the first ON_STOP after countdown starts
- onDestroy removes the ProcessLifecycleOwner observer to prevent
  memory leaks (Service is destroyed by stopSelf)

Drag-to-edge collapse/expand and pause/stop button behavior is
already implemented in v1.1 (FloatingBubbleController.handleTouch
+ BubbleContent) and is not modified by this fix."
```

## 3. Build 验证

- [x] **Step 1: 完整 assembleDebug**

```bash
. ./.env.sh && ./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 2: 验证 APK**

```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

- [x] **Step 3: 提交(若有 build 配置变更)**

```bash
git status
git add -A
git commit -m "build: assembleDebug after bugfix-3" || echo "no changes"
```

---

## Self-Review

- 1 commit(Task 2),修改 3 个文件(gradle/libs.versions.toml + app/build.gradle.kts + CountdownService.kt)
- 现有 `FloatingBubbleController` 不改 — 它的 show/hide/handleTouch/collapse/expand 都已对
- 无 spec 变更
- 无需 delta spec(行为契约不变,实现对齐已有 spec)
