# Tasks: yawn-lock-bugfix-4

> 5 个修复任务:Timer 屏零 UI、气泡 XML 化、ProcessLifecycle 移到 Application、通知固定 + stop action、OEM 引导。

## 1. 气泡 XML 化(根治 WindowManager+Compose 顽疾)

**Files:** `res/layout/floating_bubble.xml`, `res/drawable/bubble_*.xml`, `app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt`

- [ ] **Step 1: 新增 4 个 drawable 资源**

`res/drawable/bubble_bg.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <gradient
        android:angle="315"
        android:startColor="#2D2466"
        android:endColor="#6750A4" />
    <corners android:radius="22dp" />
</shape>
```

`res/drawable/bubble_moon_bg.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#33FFD97A" />
    <corners android:radius="10dp" />
</shape>
```

`res/drawable/bubble_btn_dim.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#29FFFFFF" />
    <corners android:radius="11dp" />
</shape>
```

`res/drawable/bubble_btn_accent.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FFD97A" />
    <corners android:radius="11dp" />
</shape>
```

- [ ] **Step 2: 重写 `floating_bubble.xml` 为 FrameLayout 布局**

替换整个 `res/layout/floating_bubble.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/bubble_root"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:padding="14dp"
    android:background="@drawable/bubble_bg">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:gravity="center_horizontal">

        <View
            android:layout_width="36dp"
            android:layout_height="4dp"
            android:layout_marginBottom="10dp"
            android:background="#FFFFFF"
            android:alpha="0.3" />

        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical">

            <ImageView
                android:layout_width="30dp"
                android:layout_height="30dp"
                android:padding="7dp"
                android:src="@drawable/ic_moon"
                android:background="@drawable/bubble_moon_bg" />

            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginStart="8dp"
                android:orientation="vertical">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Sleepy Lock"
                    android:textColor="#FFFFFF"
                    android:textSize="13sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/bubble_subtitle"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="计时中"
                    android:textColor="#FFFFFF"
                    android:alpha="0.7"
                    android:textSize="11sp" />
            </LinearLayout>
        </LinearLayout>

        <TextView
            android:id="@+id/bubble_time"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="6dp"
            android:layout_marginBottom="10dp"
            android:text="00:00"
            android:textColor="#FFFFFF"
            android:textSize="34sp"
            android:textStyle="bold"
            android:fontFamily="monospace" />

        <LinearLayout
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:orientation="horizontal">

            <Button
                android:id="@+id/bubble_pause"
                android:layout_width="0dp"
                android:layout_weight="1"
                android:layout_height="34dp"
                android:layout_marginEnd="3dp"
                android:background="@drawable/bubble_btn_dim"
                android:text="暂停"
                android:textSize="12sp"
                android:textStyle="bold"
                android:textColor="#FFFFFF" />

            <Button
                android:id="@+id/bubble_stop"
                android:layout_width="0dp"
                android:layout_weight="1"
                android:layout_height="34dp"
                android:layout_marginStart="3dp"
                android:background="@drawable/bubble_btn_accent"
                android:text="停止"
                android:textSize="12sp"
                android:textStyle="bold"
                android:textColor="#1A1A2E" />
        </LinearLayout>
    </LinearLayout>
</FrameLayout>
```

- [ ] **Step 3: 重写 `FloatingBubbleController.kt` 不用 Compose**

替换整个文件:

```kotlin
package com.example.yawnlock.service

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import com.example.yawnlock.R
import com.example.yawnlock.YawnApplication
import com.example.yawnlock.domain.DurationFormatter
import com.example.yawnlock.domain.TimerStatus

class FloatingBubbleController(private val context: Context) {
    companion object { private const val TAG = "FloatingBubble" }

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val bubbleView: View =
        LayoutInflater.from(context).inflate(R.layout.floating_bubble, null)
    private val root: FrameLayout = bubbleView.findViewById(R.id.bubble_root)
    private val timeView: TextView = bubbleView.findViewById(R.id.bubble_time)
    private val subtitleView: TextView = bubbleView.findViewById(R.id.bubble_subtitle)
    private val pauseBtn: Button = bubbleView.findViewById(R.id.bubble_pause)
    private val stopBtn: Button = bubbleView.findViewById(R.id.bubble_stop)

    private val params = WindowManager.LayoutParams(
        dp(200), WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
        },
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = dp(40)
        y = dp(300)
    }

    private var startX = 0f
    private var startY = 0f
    private var startParamsX = 0
    private var startParamsY = 0
    private var moved = false

    init {
        bubbleView.setOnTouchListener { _, ev -> handleTouch(ev) }
        pauseBtn.setOnClickListener { togglePause() }
        stopBtn.setOnClickListener { stopCountdown() }
    }

    fun show() {
        try {
            wm.addView(bubbleView, params)
        } catch (e: Exception) {
            Log.e(TAG, "addView failed; bubble will not show", e)
            return
        }
        val s = (context.applicationContext as YawnApplication).timerRepository.state.value
        timeView.text = DurationFormatter.toMmSs(s.remainingMs)
        updateStatus(s.status is TimerStatus.Paused)
    }

    fun hide() {
        try { wm.removeView(bubbleView) } catch (_: Exception) {}
    }

    fun updateTime(ms: Long) {
        timeView.text = DurationFormatter.toMmSs(ms)
    }

    fun updateStatus(isPaused: Boolean) {
        subtitleView.text = if (isPaused) "已暂停" else "计时中"
        pauseBtn.text = if (isPaused) "继续" else "暂停"
    }

    private fun togglePause() {
        val repo = (context.applicationContext as YawnApplication).timerRepository
        if (repo.state.value.status is TimerStatus.Paused) {
            repo.resume()
            context.startService(
                Intent(context, CountdownService::class.java).setAction(CountdownService.ACTION_RESUME)
            )
        } else {
            repo.pause()
            context.startService(
                Intent(context, CountdownService::class.java).setAction(CountdownService.ACTION_PAUSE)
            )
        }
    }

    private fun stopCountdown() {
        val repo = (context.applicationContext as YawnApplication).timerRepository
        repo.stop()
        context.startService(
            Intent(context, CountdownService::class.java).setAction(CountdownService.ACTION_STOP)
        )
    }

    private fun handleTouch(ev: MotionEvent): Boolean {
        val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = ev.rawX; startY = ev.rawY
                startParamsX = params.x; startParamsY = params.y
                moved = false
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - startX
                val dy = ev.rawY - startY
                if (kotlin.math.abs(dx) + kotlin.math.abs(dy) > slop) moved = true
                val displayMetrics = context.resources.displayMetrics
                val maxX = displayMetrics.widthPixels - bubbleView.width
                val maxY = displayMetrics.heightPixels - bubbleView.height
                params.x = (startParamsX + dx.toInt()).coerceIn(0, maxX)
                params.y = (startParamsY + dy.toInt()).coerceIn(0, maxY)
                try { wm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
            }
            MotionEvent.ACTION_UP -> {
                if (moved && params.x < dp(36)) collapse()
                else if (!moved) expand()
            }
        }
        return true
    }

    private fun collapse() {
        // 简化:折叠 = 显示小 pill(可后续 polish)
        params.width = dp(36)
        params.x = dp(6)
        try { wm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
    }

    private fun expand() {
        params.width = dp(200)
        params.x = dp(40)
        try { wm.updateViewLayout(bubbleView, params) } catch (_: Exception) {}
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
}
```

- [ ] **Step 4: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 提交**

```bash
git add res/layout/floating_bubble.xml \
        res/drawable/bubble_bg.xml res/drawable/bubble_moon_bg.xml \
        res/drawable/bubble_btn_dim.xml res/drawable/bubble_btn_accent.xml \
        app/src/main/kotlin/com/example/yawnlock/service/FloatingBubbleController.kt
git commit -m "feat(bubble): rewrite as XML/FrameLayout to fix WindowManager+Compose issues"
```

## 2. ProcessLifecycleOwner observer 移到 Application

**Files:** `app/src/main/kotlin/com/example/yawnlock/YawnApplication.kt`, `app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt`

- [ ] **Step 1: YawnApplication 持有 bubbleController 单例 + 注册 observer**

替换 `app/src/main/kotlin/com/example/yawnlock/YawnApplication.kt`:

```kotlin
package com.example.yawnlock

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.yawnlock.domain.TimerRepository
import com.example.yawnlock.service.FloatingBubbleController

class YawnApplication : Application() {
    val timerRepository: TimerRepository by lazy { TimerRepository() }
    val bubbleController: FloatingBubbleController by lazy { FloatingBubbleController(this) }

    private val appLifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                if (timerRepository.state.value.isActive) bubbleController.show()
            }
            Lifecycle.Event.ON_START -> {
                bubbleController.hide()
            }
            else -> {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
    }
}
```

Add imports at top: `androidx.lifecycle.Lifecycle`, `androidx.lifecycle.LifecycleEventObserver`, `androidx.lifecycle.ProcessLifecycleOwner`

- [ ] **Step 2: CountdownService 移除 ProcessLifecycle 订阅,改用 Application 的 bubbleController**

修改 `CountdownService.kt`:
- 移除 imports `Lifecycle`, `LifecycleEventObserver`, `ProcessLifecycleOwner`
- 移除 `processLifecycleObserver` 字段
- 移除 `onCreate` 里 `ProcessLifecycleOwner.get().lifecycle.addObserver(...)`
- 移除 `onDestroy` 里 `ProcessLifecycleOwner.get().lifecycle.removeObserver(...)`
- 修改 `ensureBubble()`:从 `FloatingBubbleController(this).also { it.show() }` 改为 `(application as YawnApplication).bubbleController.also { it.show() }`
- 修改 `bubble` 字段类型为 `val bubble get() = (application as YawnApplication).bubbleController`(getter,无需手动清理)

具体替换:
- `private var bubble: FloatingBubbleController? = null` → `private val bubble get() = (application as YawnApplication).bubbleController`
- `ensureBubble()` 方法简化为 `bubble.show()`(或者直接内联到 `bubble?.show()` 即可)
- 移除 `onDestroy` 里 `bubble?.hide(); bubble = null`(Application 持有单例,Service 不应清理)
- `handleStart`/`handleResume` 里 `try { ensureBubble() } catch (...)` 整段移除 — ProcessLifecycle 接管

- [ ] **Step 3: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 提交**

```bash
git add app/src/main/kotlin/com/example/yawnlock/YawnApplication.kt \
        app/src/main/kotlin/com/example/yawnlock/service/CountdownService.kt
git commit -m "fix(lifecycle): move ProcessLifecycleOwner observer to Application"
```

## 3. Timer 屏幕移除 StatusCard

**Files:** `app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt`

- [ ] **Step 1: 移除 StatusCard 调用**

在 `TimerScreen.kt:75-77`,删除:
```kotlin
            if (state.isActive || state.status is TimerStatus.Finished) {
                StatusCard(state = state, vm = vm)
            }
```

替换为(直接 nothing 或 注释):
```kotlin
            // 不在 Timer 屏幕显示倒计时 UI(spec 变更:仅悬浮窗显示倒计时)
```

- [ ] **Step 2: 删除 StatusCard 整个 Composable 函数**

删除 `TimerScreen.kt:242-280` 整个 `StatusCard` 函数。

- [ ] **Step 3: 清理未用 imports**

`TimerScreen.kt` 中:
- 删除 `import com.example.yawnlock.service.CountdownService` 行(若 StatusCard 内引用)— 检查其他用法,可能还在用
- 删除 `import com.example.yawnlock.domain.TimerState` 行(若 StatusCard 内引用)— TimerState 可能在 ViewModel state 还在用
- 删除 `import com.example.yawnlock.ui.components.ProgressRing` 行(若 StatusCard 内引用)— ProgressRing 可能没在别处用
- 删除 `import com.example.yawnlock.ui.theme.Purple50` / `Rose`(若 StatusCard 内引用)

注意:**只删确实未用的 import**(compileDebugKotlin 会报 unused import warning,不删也行,不影响 build)

- [ ] **Step 4: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 5: 提交**

```bash
git add app/src/main/kotlin/com/example/yawnlock/ui/timer/TimerScreen.kt
git commit -m "feat(timer): remove StatusCard from Timer screen per user feedback"
```

## 4. 通知加 stop action + 完全 ongoing

**Files:** `app/src/main/kotlin/com/example/yawnlock/data/NotificationCenter.kt`

- [ ] **Step 1: 加 stop action + 修正 priority**

替换 `NotificationCenter.build` 方法内的 `addAction` 之前/之后,补:

```kotlin
        val stopIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, com.example.yawnlock.service.StopReceiver::class.java)
                .setAction(com.example.yawnlock.service.StopReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_moon)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(title)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openAppIntent)
            .addAction(R.drawable.ic_stop, context.getString(R.string.notification_stop), stopIntent)
            .build()
```

(把原 stopIntent / addAction 行替换掉)

- [ ] **Step 2: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/kotlin/com/example/yawnlock/data/NotificationCenter.kt
git commit -m "fix(notification): add stop action + ongoing flag + low priority for Android 14+ FGS"
```

## 5. OEM 电池优化引导(国产 ROM)

**Files:** `app/src/main/res/values/strings.xml`, `app/src/main/kotlin/com/example/yawnlock/MainActivity.kt`

- [ ] **Step 1: 加 strings**

`app/src/main/res/values/strings.xml` 加:
```xml
    <string name="oem_warning_title">需要开启后台运行权限</string>
    <string name="oem_warning_message">检测到您的设备是 %1$s。在该系统上,默认会关闭后台应用的运行,可能导致倒计时不能正常完成。请前往系统设置 → 电池/自启动管理,允许「打哈欠」在后台运行。</string>
    <string name="oem_warning_goto">去设置</string>
    <string name="oem_warning_dismiss">知道了</string>
```

- [ ] **Step 2: MainActivity 加 OEM 检测 + 引导 dialog**

修改 `MainActivity.kt`:

1. 加 imports:
```kotlin
import android.os.Build
import android.content.SharedPreferences
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
```

2. 在 `setContent` 内 `AppNavHost` 之前加:
```kotlin
        LaunchedEffect(Unit) {
            maybeShowOemWarning()
        }
```

3. 加 helper 函数(类内):
```kotlin
    private val oemPrefs: SharedPreferences by lazy {
        getSharedPreferences("yawn_lock_prefs", MODE_PRIVATE)
    }
    private fun isOemWarnedShown(): Boolean = oemPrefs.getBoolean("oem_warned_shown", false)
    private fun markOemWarnedShown() { oemPrefs.edit().putBoolean("oem_warned_shown", true).apply() }

    private fun isAggressiveOem(manufacturer: String): Boolean = when (manufacturer.lowercase()) {
        "xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "vivo", "oneplus" -> true
        else -> false
    }

    private fun maybeShowOemWarning() {
        if (isOemWarnedShown()) return
        val manufacturer = Build.MANUFACTURER
        if (!isAggressiveOem(manufacturer)) {
            markOemWarnedShown()  // 标记已检查(非 OEM 不再检查)
            return
        }
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle(R.string.oem_warning_title)
                .setMessage(getString(R.string.oem_warning_message, manufacturer))
                .setPositiveButton(R.string.oem_warning_goto) { _, _ ->
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                            .setData(Uri.parse("package:$packageName"))
                    )
                    markOemWarnedShown()
                }
                .setNegativeButton(R.string.oem_warning_dismiss) { _, _ -> markOemWarnedShown() }
                .setCancelable(false)
                .show()
        }
    }
```

Add imports for `androidx.compose.material3.AlertDialog` may be missing - but `AlertDialog.Builder` is from `androidx.appcompat.app.AlertDialog`. Use the platform one for simplicity. Add imports:
```kotlin
import androidx.appcompat.app.AlertDialog
import android.provider.Settings
import android.net.Uri
```

- [ ] **Step 3: 验证编译**

```bash
. ./.env.sh && ./gradlew :app:compileDebugKotlin 2>&1 | tail -10
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/values/strings.xml \
        app/src/main/kotlin/com/example/yawnlock/MainActivity.kt
git commit -m "feat(oem): show battery-optimization guidance on first launch for aggressive ROMs"
```

## 6. Build 验证

- [ ] **Step 1: 完整 assembleDebug**

```bash
. ./.env.sh && ./gradlew :app:assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: 验证 APK**

```bash
ls -lh app/build/outputs/apk/debug/app-debug.apk
```

- [ ] **Step 3: 提交(若有 build 配置变更)**

```bash
git status
git add -A
git commit -m "build: assembleDebug after bugfix-4" || echo "no changes"
```

---

## Self-Review

- 5 个 commit(Task 1-5),跨 8 个文件(2 个 layout、4 个 drawable、3 个 .kt、1 个 strings.xml)
- 现有 `FloatingBubbleController` 整个重写(从 ComposeView 改为 XML/FrameLayout)
- `CountdownService` 简化(ProcessLifecycle 移到 Application)
- `TimerScreen` 移除 StatusCard
- `MainActivity` 加 OEM 引导
- `NotificationCenter` 加 stop action
- 气泡 `bubble` 字段从 `var null` 改为 `val` getter
- spec delta 同步(在 scheduled-screen-lock)
