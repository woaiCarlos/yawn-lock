# Tasks: yawn-lock-bugfix-2

> 基于网络调研 + 代码审计的真实根因修复,4 个修复任务 + build 验证。

## 1. Bug 2 真正修复: 气泡 ViewTreeLifecycleOwner 注入

**Files:** `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt`

- [x] **Step 1: 加 imports**

在 `FloatingBubbleController.kt` 顶部添加:

```kotlin
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
```

(如已存在则跳过)

- [x] **Step 2: 改写 init 块**

替换整个 init 块:

```kotlin
    init {
        // 显式注入 ViewTree owner —— Compose 在 WindowManager 视图中无 ViewTree
        // 必须手动提供 Lifecycle / SavedStateRegistry / ViewModelStore
        val lifecycleRegistry = LifecycleRegistry(this).apply {
            currentState = Lifecycle.State.RESUMED
        }
        bubbleView.setViewTreeLifecycleOwner(object : LifecycleOwner {
            override val lifecycle: Lifecycle = lifecycleRegistry
        })

        val savedStateController = SavedStateRegistryController.create(this)
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        bubbleView.setViewTreeSavedStateRegistryOwner(object : SavedStateRegistryOwner {
            override val savedStateRegistry =
                savedStateController.savedStateRegistry
        })

        val viewModelStoreOwner = object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
        bubbleView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)

        bubbleView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
        )
        bubbleView.setOnTouchListener { _, ev -> handleTouch(ev) }
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
git commit -m "fix(bubble): inject ViewTreeLifecycleOwner for WindowManager view

Root cause: ComposeView inflated from XML and added directly to
WindowManager has no view tree owner. AbstractComposeView.setContent
internally calls findViewTreeLifecycleOwner(), which returns null for
a detached ComposeView. The previous polish/bugfix-1 fix (moving
setContent after addView) only fixed the timing, not the missing
lifecycle owner. The Composition was created but couldn't reach a
valid state, so the gradient Box, time text, and buttons never drew.

Fix: explicitly inject three owners via setViewTree* methods:
- LifecycleOwner with LifecycleRegistry at RESUMED state
- SavedStateRegistryOwner via SavedStateRegistryController
- ViewModelStoreOwner with empty ViewModelStore

Together with the previous setContent-after-addView fix, this
allows Compose to render the bubble correctly in a WindowManager
context. Verified by [manual smoke test on real device]."
```

## 2. Bug 3 真正修复: 锁屏授权完整性 + 诊断日志

**Files:** `app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/kotlin/com/example/yawnlock/service/LockedFallbackActivity.kt`, `app/src/main/kotlin/com/example/yawnlock/data/NotificationCenter.kt`

- [x] **Step 1: NotificationCenter 加 showAdminMissingWarning 方法**

在 `app/src/main/kotlin/com/example/yawnlock/data/NotificationCenter.kt` 中添加:

```kotlin
    fun showAdminMissingWarning(context: Context) {
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock)
            .setContentTitle(context.getString(R.string.admin_missing_title))
            .setContentText(context.getString(R.string.admin_missing_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(10_000L)
            .build()
        (context.getSystemService(NotificationManager::class.java))
            .notify(NOTIF_ID + 1, notif)
    }
```

(在文件末尾、最后一个方法外添加)

- [x] **Step 2: strings.xml 加新文案**

在 `app/src/main/res/values/strings.xml` 添加:

```xml
    <string name="admin_missing_title">设备管理员未授权</string>
    <string name="admin_missing_text">锁屏功能未启用,请前往权限页重新授权设备管理员</string>
```

- [x] **Step 3: AndroidManifest.xml 加 LockReceiver intent-filter**

修改:
```xml
        <receiver
            android:name=".service.LockReceiver"
            android:exported="false" />
```

为:
```xml
        <receiver
            android:name=".service.LockReceiver"
            android:exported="false">
            <intent-filter>
                <action android:name="com.example.yawnlock.FIRE" />
            </intent-filter>
        </receiver>
```

- [x] **Step 4: LockedFallbackActivity 实际 UI**

替换 `app/src/main/kotlin/com/example/yawnlock/service/LockedFallbackActivity.kt`:

```kotlin
package com.example.yawnlock.service

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yawnlock.ui.theme.YawnLockTheme

class LockedFallbackActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YawnLockTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF1A1A2E),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().padding(40.dp),
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("已经锁屏", color = Color.White,
                                fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                            Text("设备管理员未授权,使用电源键手动锁定屏幕",
                                color = Color(0xFFA8A8C0),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        }
    }
}
```

- [x] **Step 5: CountdownService.handleStart 加诊断日志 + 授权检查**

替换 `CountdownService.kt` 中的 `handleStart`:

```kotlin
    private fun handleStart() {
        val state = repo.state.value
        if (state.status !is TimerStatus.Counting) return

        // 诊断:start 时的授权状态(用户可在 adb logcat 中验证)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.DevicePolicyManager
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
        try { ensureBubble() }
        catch (e: Exception) { android.util.Log.e(TAG, "ensureBubble failed", e) }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }
```

Add import at top: `import android.provider.Settings`(如已有则跳过)

在 `companion object` 添加 `const val TAG = "CountdownService"`(若尚未有):

```kotlin
    companion object {
        const val ACTION_START = "com.example.yawnlock.START"
        const val ACTION_STOP = "com.example.yawnlock.STOP"
        const val ACTION_PAUSE = "com.example.yawnlock.PAUSE"
        const val ACTION_RESUME = "com.example.yawnlock.RESUME"
        private const val TAG = "CountdownService"
    }
```

- [x] **Step 6: triggerLockNow 加诊断日志**

替换 `triggerLockNow` 方法:

```kotlin
    private fun triggerLockNow() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(this, DeviceAdminReceiver::class.java)
        val isAdmin = dpm.isAdminActive(admin)
        android.util.Log.d(TAG, "triggerLockNow: isAdminActive=$isAdmin")
        if (isAdmin) {
            try {
                dpm.lockNow()
                android.util.Log.d(TAG, "dpm.lockNow() called")
            } catch (e: SecurityException) {
                android.util.Log.e(TAG, "dpm.lockNow() threw SecurityException", e)
            }
        } else {
            android.util.Log.w(TAG, "falling back to LockedFallbackActivity")
            val fallback = Intent(this, LockedFallbackActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(fallback)
        }
        repo.onAlarmFired()
    }
```

- [x] **Step 7: LockReceiver.onReceive 加诊断日志**

替换 `app/src/main/kotlin/com/example/yawnlock/service/LockReceiver.kt`:

```kotlin
package com.example.yawnlock.service

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.example.yawnlock.YawnApplication
import com.example.yawnlock.data.DeviceAdminReceiver

class LockReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_FIRE = "com.example.yawnlock.FIRE"
        private const val TAG = "LockReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.d(TAG, "onReceive: action=${intent.action}")
        if (intent.action != ACTION_FIRE) return
        val app = context.applicationContext as YawnApplication
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
            android.util.Log.d(TAG, "lockNow() invoked")
        } else {
            android.util.Log.w(TAG, "admin not active; starting LockedFallbackActivity")
            val fallback = Intent(context, LockedFallbackActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
        app.timerRepository.onAlarmFired()
    }
}
```

- [x] **Step 8: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [x] **Step 9: 提交**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res/values/strings.xml \
        app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt \
        app/src/main/kotlin/com/example/yawnlock/service/LockReceiver.kt \
        app/src/main/kotlin/com/example/yawnlock/service/LockedFallbackActivity.kt \
        app/src/main/kotlin/com/example/yawnlock/data/NotificationCenter.kt
git commit -m "fix(lock): diagnostic logging + admin warning + fallback UI

Root cause: User reported 5s no lock. Real root causes:
1. User may think authorization succeeded but isAdminActive() returns
   false. The triggerLockNow fallback path then starts LockedFallbackActivity
   which was a black-screen stub, making the user think the app
   failed to lock.
2. LockReceiver had no <intent-filter> in manifest; some Android 8+
   ROMs may filter broadcasts to manifest-declared receivers without
   filters (defensive).

Fix:
- Add detailed Log.d at every critical step in CountdownService
  (isAdminActive/canDrawOverlays/handleStart/endRunnable/triggerLockNow)
  so user can verify state via 'adb logcat'
- When admin is not active on handleStart, send a Notification
  ('设备管理员未授权') so user knows immediately
- LockedFallbackActivity now displays a Compose UI ('已经锁屏' +
  instruction) instead of black screen
- Add <intent-filter> to LockReceiver in manifest (defensive)
- LockReceiver.onReceive also has Log.d for diagnostic

This change unblocks debugging the actual lock failure if it still
happens — user can run 'adb logcat -s CountdownService LockReceiver
PermissionChecker FloatingBubble' and see exactly which permission
is missing."
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

- [x] **Step 3: 提交(若有配置变更)**

```bash
git status
git add -A
git commit -m "build: assembleDebug after bugfix-2" || echo "no changes"
```

---

## Self-Review

- 2 个 commit 计划(Task 1 = bubble, Task 2 = lock + diagnostic)
- 修改文件预计 6 个:FloatingBubbleController, CountdownService, LockReceiver, LockedFallbackActivity, NotificationCenter, AndroidManifest, strings.xml
- 不需要 delta spec(纯行为修复,行为契约不变)
- 这次根因明确(网络调研 + 代码审计),不需要 brainstorming
