---
comet_change: yawn-lock
role: technical-design
canonical_spec: openspec
archived-with: 2026-06-11-yawn-lock
status: final
---

# Design: 打哈欠 (yawn-lock) — Android 定时锁屏 v1

> OpenSpec canonical spec: `openspec/changes/yawn-lock/`
> 需求源: `proposal.md`、`specs/scheduled-screen-lock/spec.md`、`specs/floating-countdown-widget/spec.md`
> 本文档不重新定义需求,只补充实现细节、风险、测试策略。

## 0. 决策摘要(已确认)

| # | 决策 | 选择 | 备注 |
|---|------|------|------|
| D-Q1 | 应用图标 | v1 用默认 AdaptiveIcon | 用户稍后提供 |
| D-Q2 | 倒计时"再来 5 分钟"按钮 | **删除** | 保持"强制锁"哲学;spec 无此 requirement,无需回写 |
| D-Q3 | 勿扰模式 | 不做 | proposal 已划入非目标 |
| D-Q4 | Android 包名 | `com.example.yawnlock` | 发布前可重命名 |
| D-T1 | 测试覆盖 | **仅冒烟测试** | 8 步手动路径;不写单元/UI 测试 |

## 1. 架构总览

```
┌────────────────────────────────────────────────────────────┐
│  MainActivity (单 Activity)                                │
│  ├─ YawnLockTheme                                          │
│  └─ NavHost                                                │
│      ├─ "timer"  → TimerScreen  ─┐ 共享 TimerRepository    │
│      └─ "permissions" → PermissionsScreen─┘ (单例)         │
│                                                            │
│  ┌───────────────────────────────────────────────────┐     │
│  │  CountdownService (前台服务)                       │     │
│  │  ├─ Foreground Notification (每秒更新)            │     │
│  │  ├─ AlarmManager.setExactAndAllowWhileIdle       │     │
│  │  └─ FloatingBubbleController.show() / hide()      │     │
│  │       └─ WindowManager.addView(composeView)        │     │
│  │                                                    │     │
│  │  LockReceiver (BroadcastReceiver)                  │     │
│  │  └─ onReceive() → devicePolicyManager.lockNow()    │     │
│  └───────────────────────────────────────────────────┘     │
│                                                            │
│  DeviceAdminReceiver (BIND_DEVICE_ADMIN)                   │
│  PermissionChecker (运行时权限状态查询)                    │
└────────────────────────────────────────────────────────────┘
```

**MVVM + Repository 单例**:
- `YawnApplication` 持有 `TimerRepository` 单例
- 屏幕不持有状态,ViewModel 通过 `viewModel<TimerViewModel> { ... }` 工厂构造,内部 `stateIn()` 收集 Repository 的 `StateFlow`
- Service 持有 Repository 引用(由 Application 注入),Service 内部 ticker 直接更新 Repository 状态

## 2. 文件结构

```
yawn-lock/
├── app/
│   ├── build.gradle.kts
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── kotlin/com/example/yawnlock/
│   │   │   ├── YawnApplication.kt
│   │   │   ├── MainActivity.kt
│   │   │   ├── ui/
│   │   │   │   ├── theme/         # Color.kt, Theme.kt, Type.kt
│   │   │   │   ├── timer/         # TimerScreen.kt, TimerViewModel.kt
│   │   │   │   ├── permissions/   # PermissionsScreen.kt, PermissionsViewModel.kt
│   │   │   │   └── components/    # 通用 Compose 组件 (ProgressRing, BigNumber)
│   │   │   ├── service/
│   │   │   │   ├── CountdownService.kt
│   │   │   │   ├── LockReceiver.kt
│   │   │   │   ├── StopReceiver.kt
│   │   │   │   └── FloatingBubbleController.kt
│   │   │   ├── data/
│   │   │   │   ├── DeviceAdminReceiver.kt
│   │   │   │   ├── PermissionChecker.kt
│   │   │   │   └── NotificationCenter.kt
│   │   │   └── domain/
│   │   │       ├── TimerState.kt
│   │   │       ├── TimerRepository.kt
│   │   │       └── DurationFormatter.kt
│   │   └── res/
│   │       ├── layout/floating_bubble.xml
│   │       ├── values/{strings,colors,themes}.xml
│   │       ├── drawable/ic_*.xml
│   │       ├── mipmap-anydpi-v26/ic_launcher.xml
│   │       └── xml/device_admin_policies.xml
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
└── gradlew
```

## 3. 状态机

### 3.1 TimerState

```kotlin
sealed interface TimerStatus {
    object Idle : TimerStatus
    object Counting : TimerStatus
    object Paused : TimerStatus
    object Finished : TimerStatus
}

data class TimerState(
    val status: TimerStatus = TimerStatus.Idle,
    val durationMs: Long = 0L,
    val remainingMs: Long = 0L,
    val startedAtElapsed: Long = 0L,  // SystemClock.elapsedRealtime()
)

val progress: Float get() =
    if (durationMs == 0L) 0f else 1f - (remainingMs.toFloat() / durationMs)
```

**转换图**:
```
   ┌──────┐  start()    ┌──────────┐  pause()   ┌──────────┐
   │ Idle │ ─────────→ │ Counting │ ─────────→ │ Paused   │
   └──────┘             └────┬─────┘ ←────────── └──────────┘
       ↑                     │       resume()
       │                     │
       │ stop() / lockNow()  │
       │                     ┌──────────┐
       └─────────────────── │ Finished │
                             └──────────┘
```

**触发器 → 状态变更矩阵**:
| 触发 | 前态 | 后态 | 副作用 |
|------|------|------|--------|
| `start(durationMs)` | Idle | Counting | 启动 Service + Alarm + Bubble;`startedAtElapsed=now` |
| `pause()` | Counting | Paused | 取消 Alarm;`remainingMs` 冻结;Service 停 ticker |
| `resume()` | Paused | Counting | 用冻结的 `remainingMs` 重设 Alarm;ticker 恢复 |
| `stop()` | Counting/Paused | Idle | 取消 Alarm;Service `stopSelf()`;Bubble 移除 |
| `tick()` | Counting | Counting | `remainingMs -= 100`;`progress` 派生 |
| `onAlarmFire()` | Counting/Paused | Finished | `lockNow()`;Service 停;Bubble 移除 |
| `onDeviceAdminRevoked()` | 任意非 Idle | Idle | 取消 Alarm;停 Service;Toast 提示 |

### 3.2 PermissionState(只读,无转换)

```kotlin
data class PermissionState(
    val overlayGranted: Boolean,         // Settings.canDrawOverlays()
    val deviceAdminActive: Boolean,      // DevicePolicyManager.isAdminActive()
    val notificationGranted: Boolean,    // API 33+: NotificationManagerCompat.areNotificationsEnabled()
) {
    val canStartCountdown: Boolean
        get() = overlayGranted && deviceAdminActive
}
```

## 4. 关键技术路径

### 4.1 设备管理员激活

```
PermissionsScreen.onRowClick(DeviceAdmin)
  → ComponentName(this, DeviceAdminReceiver::class.java)
  → Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
       .putExtra(EXTRA_DEVICE_ADMIN, componentName)
       .putExtra(EXTRA_ADD_EXPLANATION, "用于到点强制锁屏,这是核心功能所需")
  → startActivity(intent)
  → 系统弹"激活设备管理员"对话框
  → 用户确认 → onActivityResult (RESULT_OK) → PermissionChecker.refresh()
  → PermissionsViewModel 更新 state
  → MainActivity 重新查询 → 若全部 granted,自动 NavHost 跳回 "timer"
```

**DeviceAdminReceiver 子类** (空实现,关键 override):
- `onEnabled(ctx, intent)`: 写 SharedPreferences
- `onDisableRequested(ctx, intent)`: 返回 `null` (允许用户撤销)
- `onDisabled(ctx, intent)`: 删 SharedPreferences
- **不要** override `onPasswordRequired` / `onPasswordSucceeded` (本应用不处理密码)

**manifest 注册**:
```xml
<receiver
    android:name=".data.DeviceAdminReceiver"
    android:permission="android.permission.BIND_DEVICE_ADMIN"
    android:exported="true">
  <meta-data
      android:name="android.app.device_admin"
      android:resource="@xml/device_admin_policies" />
  <intent-filter>
    <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
  </intent-filter>
</receiver>
```

### 4.2 倒计时启动序列

```
TimerScreen "开始锁屏" click
  → if !PermissionState.canStartCountdown → navigate("permissions"); return
  → TimerRepository.start(durationMs)
       → state.update { Counting(durationMs, durationMs, now) }
       → startService(Intent(this, CountdownService::class.java)
                          .putExtra(EXTRA_DURATION_MS, durationMs)
                          .setAction(ACTION_START))
  → CountdownService.onStartCommand
       → startForeground(NOTIF_ID, NotificationCenter.build(remainingMs), FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
       → alarmManager.setExactAndAllowWhileIdle(
            RTC_WAKEUP,
            SystemClock.elapsedRealtime() + durationMs,
            pendingIntent to LockReceiver)
       → handler.postDelayed(tickRunnable, 100ms)
       → FloatingBubbleController.show()
```

**关键 manifest 节点**:
```xml
<service
    android:name=".service.CountdownService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
  <property
      android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
      android:value="scheduled_screen_lock_with_countdown_widget" />
</service>
```

### 4.3 悬浮气泡拖动

```kotlin
// 在 WindowManager 全局 View 的 onTouchListener
private fun onTouch(event: MotionEvent): Boolean {
    val params = layoutParams  // WindowManager.LayoutParams
    when (event.action) {
        DOWN -> {
            startRawX = event.rawX; startRawY = event.rawY
            startParamsX = params.x; startParamsY = params.y
            moved = false
        }
        MOVE -> {
            val dx = event.rawX - startRawX
            val dy = event.rawY - startRawY
            if (abs(dx) + abs(dy) > ViewConfiguration.get(ctx).scaledTouchSlop) moved = true
            params.x = (startParamsX - dx.toInt()).coerceIn(0, screenW - bubbleW)
            params.y = (startParamsY + dy.toInt()).coerceIn(0, screenH - bubbleH)
            windowManager.updateViewLayout(this, params)
        }
        UP -> {
            if (moved && params.x < COLLAPSE_THRESHOLD_DP) bubbleController.collapse()
            else if (!moved && bubbleController.isCollapsed) bubbleController.expand()
        }
    }
    return true
}
```

**collapsed 状态切换**:
- expand: `params.width = 200.dp`, `params.x = 40.dp`, 显示完整 UI
- collapse: `params.width = 36.dp`, `params.x = 6.dp`, 只显示 moon icon

### 4.4 闹钟触发与锁屏

```kotlin
// LockReceiver.kt
class LockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = (context.applicationContext as YawnApplication).timerRepository
        val state = repo.state.value
        if (state.status !is TimerStatus.Counting && state.status !is TimerStatus.Paused) return

        val dpm = context.getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DeviceAdminReceiver::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
            repo.onAlarmFired()
            // Service 内部 ticker 监听到 status=Finished,自动 stopSelf + 移除 bubble
        } else {
            // 降级:启动"已经锁屏"全屏 Activity
            val full = Intent(context, LockedFallbackActivity::class.java)
                .addFlags(FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(full)
            repo.onAlarmFired()
        }
    }
}
```

## 5. 错误恢复矩阵

| 故障 | 检测点 | 恢复策略 | 用户感知 |
|------|--------|---------|---------|
| `SCHEDULE_EXACT_ALARM` 权限被拒 | `AlarmManager.canScheduleExactAlarms()` 返回 false | 降级为 `setAndAllowWhileIdle` | 通知文案加"精度 ±5 分钟" |
| 设备管理员被撤销 | Service 内每 30s `dpm.isAdminActive()` 检查 | 取消 Alarm + 停 Service + 弹 Toast | "设备管理员已撤销,倒计时已停止" |
| 倒计时中用户强杀进程 | 不需要检测 | AlarmManager 由系统托管,触发时新进程跑 `LockReceiver` | 无感知 |
| 设备重启 | 用户开 App 时 `repo.state` 重置(进程未持久化) | 静默回 Idle | 看到 Idle UI |
| 倒计时归零但 admin 已撤销 | `dpm.isAdminActive()` 在 `lockNow()` 之前 | 启动 `LockedFallbackActivity` 全屏覆盖 | "按电源键暂时锁屏" |
| `WindowManager.BadTokenException` | bubble `addView()` / `updateViewLayout()` | catch 异常,静默停 bubble,Service 继续 | 倒计时继续,无气泡 |
| 通知权限(API 33+)被拒 | `NotificationManagerCompat.areNotificationsEnabled()` | 仍能 `startForeground`(系统要求),但用户看不到 | 无通知但有锁屏 |
| 进程被 LMK 杀 | 同设备重启 | 同设备重启 | 同设备重启 |

## 6. 并发与线程模型

- **UI**: `Dispatchers.Main` (Compose 默认)
- **状态**: `StateFlow` + `MutableStateFlow.update { }` 原子操作;所有读写在主线程
- **Service ticker**: `Handler(Looper.getMainLooper())` 每 100ms 一次;Service 本身运行在主线程
- **LockReceiver**: `goAsync()` 不用,直接同步执行 `lockNow()`(< 10ms 完成)
- **Repository**: 单例,线程安全
- **Bubble 拖动**: `onTouchEvent` 已在主线程,`layoutParams` 更新无并发问题
- **无 I/O**: v1 无网络/无 DB,无 IO dispatcher 需求
- **避免**: WorkManager(精度不足)、RxJava(无异步流需求)、Hilt(单例够用)

## 7. Android API 兼容性

| API | 用法 | 适配点 |
|-----|------|--------|
| 26 | `minSdk` 起点 | `TYPE_APPLICATION_OVERLAY` 已存在 |
| 31 | 通知运行时权限变更 | 启动前台服务前请求 `POST_NOTIFICATIONS` |
| 33 | `POST_NOTIFICATIONS` 强制 | `PermissionChecker.notificationGranted` |
| 34 | `FOREGROUND_SERVICE_SPECIAL_USE` 强制 | service 节点 + `<property>` 子类型说明 |
| 34 | `SCHEDULE_EXACT_ALARM` 用户可拒 | `canScheduleExactAlarms()` 检查 + 降级 |
| 34 | `USE_EXACT_ALARM` 受 Play 审核约束 | **不用**此权限,改用 `SCHEDULE_EXACT_ALARM` |

## 8. 资源与依赖

**build.gradle.kts (app)** 关键依赖:
```kotlin
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.core:core-ktx:1.13.1")
    // 无 Hilt / Room / Retrofit / OkHttp
}
```

**libs.versions.toml** 固定:
```toml
[versions]
agp = "8.5.0"
kotlin = "1.9.24"
compose-bom = "2024.06.00"
minSdk = "26"
targetSdk = "34"
```

**APK 体积目标**: < 5 MB (Compose 本身 ~2 MB,业务代码预计 < 1 MB)

## 9. 冒烟测试 (8 步,手动)

1. **全新安装** → `adb install` → 启动 → 自动跳 Permissions
2. **授权** → 看到 3 项均"未授权" → 依次点悬浮窗(跳系统设置,授权,返回)→ 点设备管理员(弹系统对话框,确认)
3. **状态刷新** → 两项变"已授权" → 返回 Timer
4. **选时长** → 选 30s 预设 → 点"开始锁屏"
5. **后台可见** → 按 home 键 → 看到悬浮气泡(显示 00:30)
6. **拖动收起** → 拖到右边缘附近释放 → 收起为小药丸;点击药丸 → 展开
7. **倒计时归零** → 等到 00:00 → 屏幕立即锁;按电源键唤醒
8. **长时验证** → 重新打开 → 选 5min → 验证后台行为 + 通知显示正确

## 10. 待解决的 Spec Patch

经查 `specs/scheduled-screen-lock/spec.md`,其中 **没有针对 `+5 分钟` 按钮的 requirement 或 scenario**。该按钮仅在原型 `timer.html` 中存在,未沉淀到 delta spec。

**结论**: `+5 分钟` 决策(D-Q2)对 spec 内容**无影响**;Spec Patch = null,不需要回写。

## 11. 已知限制与未来工作

- **设备重启后状态丢失** — v1 不持久化 TimerState,AlarmManager 也被清空。后续可加 WorkManager + Room 持久化
- **国产 ROM 适配** — 小米/华为/OPPO 的"自启动""悬浮窗"专项可能需引导,本版本未覆盖
- **多语言** — 硬编码 `strings.xml` zh-CN,后续可加 `values-en/`
- **iOS 版本** — 非目标
- **锁屏音效** — 不在 v1 范围,后续可加 `RingtoneManager` 短促提示音
- **Widget 桌面小部件** — 后续可加 `AppWidgetProvider` 提供桌面快速启动
- **统计/数据** — 不在 v1 范围,后续可加匿名"今日锁屏次数"统计

## 12. 引用

- OpenSpec proposal: `openspec/changes/yawn-lock/proposal.md`
- OpenSpec design (high-level): `openspec/changes/yawn-lock/design.md`
- OpenSpec spec #1: `openspec/changes/yawn-lock/specs/scheduled-screen-lock/spec.md`
- OpenSpec spec #2: `openspec/changes/yawn-lock/specs/floating-countdown-widget/spec.md`
- OpenSpec tasks: `openspec/changes/yawn-lock/tasks.md`
- Handoff context: `openspec/changes/yawn-lock/.comet/handoff/design-context.md`
- Android 原型 #1: `Web-Prototype/timer.html`
- Android 原型 #2: `Web-Prototype/overlay.html`
- Android 原型 #3: `Web-Prototype/floating.html`
