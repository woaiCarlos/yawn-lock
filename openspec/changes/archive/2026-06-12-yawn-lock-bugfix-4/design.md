# Design: yawn-lock-bugfix-4

> OpenSpec canonical spec: `openspec/changes/yawn-lock-bugfix-4/`
> 已有 main spec: `openspec/specs/scheduled-screen-lock/spec.md`、`openspec/specs/floating-countdown-widget/spec.md`

## Context

打完哈欠 v1.1 + 3 个 bugfix 后,锁屏 ✅。但 3 个 UX 问题:
1. Timer 屏幕 StatusCard 不该显示(用户要前台零 UI)
2. 悬浮窗在桌面/其他 app 上仍不出现
3. app 在后台开其他 app 时被系统杀

经网络调研定位:
- StatusCard 是 TimerScreen.kt 显式渲染
- 气泡不出现是 ComposeView in WindowManager 的 ViewTree 协调问题
- app 被杀是 Android 14+ FGS 通知被 swipe + 国产 ROM 杀后台

## Goals / Non-Goals

**Goals**:
- Timer 屏幕在倒计时中**完全无 UI**(无倒计时数字、无暂停/停止按钮)
- 悬浮窗在桌面/其他 app 上**实际显示**(用 XML/FrameLayout 替代 ComposeView,消除 ViewTree 协调问题)
- ProcessLifecycleOwner observer 在 `YawnApplication.onCreate` 注册,避免 race
- app 在后台被系统杀的概率降低:通知加 stop action + ongoing + OEM 电池优化引导

**Non-Goals**:
- 不做 app 唤醒后自动检查(交给用户)
- 不做深链 OEM 自启动设置 Activity(因 ROM 差异大,用通用 Intent)

## Decisions

### D1. 气泡从 Compose 改为 XML(根治 A1)

**当前问题**:
- `FloatingBubbleController` 继承 `LifecycleOwner` 等接口,这是 bugfix-2 的妥协(让 controller 自身当 owner 传给 bubbleView)
- 但 `setContent` 内部仍可能失败:`setViewTreeViewModelStoreOwner(this)` 的 `this` 是 controller(实现 LifecycleOwner),不是 view,可能导致 ViewModelStore 创建失败
- 静默失败,bugfix-2 后的 try/catch 仍吞掉

**新方案:完全不用 Compose**

**`res/layout/floating_bubble.xml`**(完全重写):
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
            android:id="@+id/bubble_handle"
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
                android:id="@+id/bubble_moon"
                android:layout_width="30dp"
                android:layout_height="30dp"
                android:src="@drawable/ic_moon"
                android:padding="7dp"
                android:background="@drawable/bubble_moon_bg" />

            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:layout_marginStart="8dp">

                <TextView
                    android:id="@+id/bubble_title"
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

**新增 3 个 drawable**:
- `res/drawable/bubble_bg.xml`:圆角 22dp + TL→BR 渐变(`#2D2466` → `#6750A4`) — 模拟原 Compose 渐变
- `res/drawable/bubble_moon_bg.xml`:圆角 10dp,半透明黄底
- `res/drawable/bubble_btn_dim.xml`:圆角 11dp,白色 16% 透明
- `res/drawable/bubble_btn_accent.xml`:圆角 11dp,纯黄(`#FFD97A`)

**`FloatingBubbleController.kt`**(完全重写,无 Compose):
```kotlin
class FloatingBubbleController(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val bubbleView: View = LayoutInflater.from(context).inflate(R.layout.floating_bubble, null)
    private val root: FrameLayout = bubbleView.findViewById(R.id.bubble_root)
    private val timeView: TextView = bubbleView.findViewById(R.id.bubble_time)
    private val subtitleView: TextView = bubbleView.findViewById(R.id.bubble_subtitle)
    private val pauseBtn: Button = bubbleView.findViewById(R.id.bubble_pause)
    private val stopBtn: Button = bubbleView.findViewById(R.id.bubble_stop)

    // LayoutParams + drag logic + collapse/expand (同前,但作用于 View 而非 ComposeView)
    // (略,见 tasks.md Step 1.2)

    fun show() {
        try { wm.addView(bubbleView, params) }
        catch (e: Exception) { Log.e(TAG, "addView failed; bubble will not show", e); return }
        // 无 setContent — XML 布局已 inflate
    }
    fun hide() { ... }
    fun updateTime(ms: Long) { timeView.text = DurationFormatter.toMmSs(ms) }
    fun updateStatus(isPaused: Boolean) {
        subtitleView.text = if (isPaused) "已暂停" else "计时中"
        pauseBtn.text = if (isPaused) "继续" else "暂停"
    }
    // 拖动 / 折叠 / 展开 — 仍作用于 root (View)
}
```

**为什么这样改**:
- 无 Compose,无 ViewTree 协调问题
- 标准 Android 视图,WindowManager 行为 100% 可预测
- 渐变背景用 GradientDrawable(已是 Android 平台 API)
- 性能更好(无重组开销)

### D2. ProcessLifecycleOwner observer 移到 Application

**当前**:`CountdownService.onCreate` 注册 observer → 启动 Service 较晚可能错过 `ON_STOP` 事件

**新**:
```kotlin
// YawnApplication.kt
class YawnApplication : Application() {
    val timerRepository: TimerRepository by lazy { TimerRepository() }
    private val bubbleController: FloatingBubbleController by lazy { FloatingBubbleController(this) }
    
    private val appLifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_STOP -> {
                if (timerRepository.state.value.isActive) bubbleController.show()
            }
            Lifecycle.Event.ON_START -> bubbleController.hide()
            else -> {}
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(appLifecycleObserver)
    }
}
```

**CountdownService** 移除 ProcessLifecycle 相关代码(订阅、取消、observer 字段)。`bubble` 字段简化为普通 `FLoatingBubbleController?`(不再由 ProcessLifecycle 创建),在 `handleStart` 中创建,`onDestroy` 中清理。

### D3. 通知固定 + 加 stop action

**当前**:`NotificationCompat.Builder` 已设 `setOngoing(true)`(沿用 v1.1),但**没加 Stop action 按钮**。Android 14 用户 swipe 通知会杀服务。

**新增**:`addAction(R.drawable.ic_stop, "停止", stopPI)`,PI 触发 `StopReceiver` 直接调 `repo.stop()`

`NotificationCenter.kt` 修改:
```kotlin
fun build(...) {
    val stopIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, StopReceiver::class.java).setAction(StopReceiver.ACTION_STOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return NotificationCompat.Builder(context, CHANNEL_ID)
        ...
        .setOngoing(true)  // 不可滑除
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .addAction(R.drawable.ic_stop, "停止", stopIntent)
        .build()
}
```

### D4. OEM 检测 + 电池优化引导(一次性 Dialog)

**当前**:无 OEM 处理,国产 ROM 用户会遇"开了倒计时 → 按 home → 开浏览器 → app 消失"

**新**:
- `MainActivity` 启动时 `sharedPreferences` 检查 `oem_warned_shown` 标记
- 首次启动时,根据 `Build.MANUFACTURER` 判断:
  - `Xiaomi` / `Redmi` / `POCO` / `huawei` / `HONOR` / `OPPO` / `realme` / `vivo` / `OnePlus` → 显示一次性 Dialog,告知用户需要开启"自启动"和"电池优化白名单"
  - 其他 → 不显示(Android 原生处理够)
- 提供"去设置"按钮,Intent 为 `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- 用户点"知道了" → 写 `oem_warned_shown = true`,不再显示

`MainActivity.kt` 新增:
```kotlin
class MainActivity : ComponentActivity(), ... {
    override fun onCreate(...) {
        super.onCreate(...)
        if (!isOemWarnedShown()) {
            val oem = Build.MANUFACTURER
            if (isAggressiveOem(oem)) {
                showOemWarningDialog(oem)
            }
        }
    }
}
```

## Risks / Trade-offs

- **[风险] XML 气泡视觉与 Compose 略不同** — 渐变、字体、间距可能略差异,功能等价
- **[风险] OEM 引导 Dialog 只能引导到"电池优化",不能直接到"自启动"设置** — 后者因 ROM 而异,无法跨平台
- **[风险] `oem_warned_shown` 在 sharedPreferences,卸载重装会重置** — 这是期望行为
- **[权衡] ProcessLifecycle observer 永久在 Application 内存中** — 单例,无影响

## Migration Plan

无 — 同分支修改,向后兼容。

## Open Questions

- Q1: 气泡折叠态(36dp pill)是否保留 XML 版?倾向保留 — 折叠时显示 moon icon
- Q2: 倒计时文本字体 — 用 monospace 等宽字体让数字不抖动
- Q3: 气泡大小 200dp 是否合适?维持原设计
