# Tasks: 打哈欠 (yawn-lock) v1

> 任务清单 — 顺序按依赖排列,每个任务可在一个 session 内完成。
> 全部任务在 Android Studio / Gradle CLI 环境 (JDK 17, AGP 8.2+) 下执行。

## 1. Project Scaffold

- [ ] 1.1 用 `gradle init` 或手写生成 Android Gradle 项目结构,包名 `com.example.yawnlock`,`minSdk 26`, `targetSdk 34`
- [ ] 1.2 配置 `gradle/libs.versions.toml`:Compose BOM、Lifecycle、Navigation Compose、WorkManager、Material 3
- [ ] 1.3 写 `app/src/main/AndroidManifest.xml`:声明 `MainActivity`、`CountdownService`、`DeviceAdminReceiver`、`LockReceiver`,声明权限 `SYSTEM_ALERT_WINDOW` / `BIND_DEVICE_ADMIN` / `POST_NOTIFICATIONS` / `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` / `SCHEDULE_EXACT_ALARM`
- [ ] 1.4 写 Material 3 主题:复用原型的紫色调(`#6750A4` accent),浅色主题,定义 `Color.kt`/`Theme.kt`/`Type.kt`
- [ ] 1.5 准备资源:启动图标(🌙 占位即可)、月/星/锁的 vector drawable、`strings.xml` 中文文案

## 2. Permission Module

- [ ] 2.1 实现 `PermissionChecker`:查询 `Settings.canDrawOverlays()` / `DevicePolicyManager.isAdminActive()` / 通知权限(API 33+)
- [ ] 2.2 实现 `PermissionsScreen` Composable:参照 `overlay.html` 的列表样式(icon + name + desc + 状态徽章),点击行跳转系统设置/激活设备管理员
- [ ] 2.3 实现 `PermissionsViewModel`:暴露 3 个权限的当前状态,`refresh()` 在 `onResume` 调用
- [ ] 2.4 配置 `NavHost`:timer 屏幕与 permissions 屏幕的路由
- [ ] 2.5 设备管理员激活:写 `DeviceAdminReceiver` 子类,在 manifest 中声明 `BIND_DEVICE_ADMIN` 和 `device-admin` 资源文件 `res/xml/device_admin_policies.xml`
- [ ] 2.6 悬浮窗引导:`ACTION_MANAGE_OVERLAY_PERMISSION` 跳转 + 启动 Intent,返回后 `refresh()` 状态

## 3. Timer Core (Domain)

- [ ] 3.1 实现 `TimerState` data class:`durationMs`、`remainingMs`、`status` (Idle/Counting/Paused/Finished) + 派生属性 `progress: Float`
- [ ] 3.2 实现 `TimerRepository` 单例(放在 `YawnApplication` 里):`StateFlow<TimerState>`,方法 `start(durationMs)` / `pause()` / `resume()` / `stop()` / `tick()`
- [ ] 3.3 在 `YawnApplication.onCreate()` 初始化 Repository,持有 `CountdownService` 的 binder 引用

## 4. Timer Screen (UI)

- [ ] 4.1 实现 `TimerScreen` Composable:原型 `timer.html` 的布局(hero + 预设 chips + 自定义 dial + 状态 card)
- [ ] 4.2 预设 chips(30s / 1m / 5m / 10m),`+`/`−` 圆形按钮,1-120min 滑块,数字与单位同步
- [ ] 4.3 状态 card:200dp 圆形 SVG ring + 倒计时数字 + `+5 分钟` / `暂停` / `停止` 按钮(在原型基础上,**删除** `+5 分钟` 按钮,见 design Q2 决策)
- [ ] 4.4 FAB 按钮:点击切换悬浮气泡显示/隐藏(通过 `FloatingBubbleController`)
- [ ] 4.5 "开始锁屏" CTA:必选权限未授权时点击跳转 permissions 屏幕,已授权时调用 `TimerRepository.start()`
- [ ] 4.6 `TimerViewModel`:包装 Repository,提供 UI 状态和事件回调

## 5. Countdown Service & Alarm

- [ ] 5.1 实现 `CountdownService` (前台服务):`onStartCommand` 启动前台 + 启动 `AlarmManager.setExactAndAllowWhileIdle`
- [ ] 5.2 实现 `LockReceiver` (`BroadcastReceiver`):收到闹钟后调用 `DevicePolicyManager.lockNow()`,然后停服务、停悬浮窗
- [ ] 5.3 实现服务内部倒计时 ticker(`Handler` 每 100ms `tick()`),同步更新 Repository 状态
- [ ] 5.4 暂停/恢复:取消旧 PendingIntent,重设 `setExactAndAllowWhileIdle` 用剩余时间
- [ ] 5.5 停止:取消 PendingIntent + `stopSelf()` + 通知消失
- [ ] 5.6 配置 Android 14 的 `FOREGROUND_SERVICE_SPECIAL_USE` + 在 manifest 提供 `<property>` 解释 service 用途

## 6. Foreground Notification

- [ ] 6.1 创建通知 channel (`YawnLockChannel`, IMPORTANCE_LOW,不响铃不震动)
- [ ] 6.2 构建通知:小图标(月亮)、`setContentTitle("打哈欠")`、内容 "剩余 03:45"、Stop action
- [ ] 6.3 每秒更新通知文本(`NotificationManagerCompat.notify` with same id)
- [ ] 6.4 Stop action 触发 `ACTION_STOP` PendingIntent,Receiver 调 `TimerRepository.stop()`

## 7. Floating Bubble

- [ ] 7.1 实现 `FloatingBubbleController`(单例,持有 `WindowManager.LayoutParams` 和 bubble view)
- [ ] 7.2 bubble 布局文件 `res/layout/floating_bubble.xml`:`ComposeView` 根,内含 200dp 圆角紫色面板、handle、moon icon、ring、time、两个按钮
- [ ] 7.3 通过 `AndroidView` 在 `ComposeView` 中渲染,`setContent { FloatingBubbleContent(...) }`
- [ ] 7.4 `onTouchListener`:实现 pointerdown/move/up 拖动逻辑(原型 `floating.html` 的算法)
- [ ] 7.5 边缘自动收起:在 `onTouch UP` 时若 `right < 36px` 切换为 collapsed 状态
- [ ] 7.6 collapsed 状态:宽度 36dp、仅显示 moon icon、点击展开
- [ ] 7.7 按钮事件:暂停/继续 → `TimerRepository.pause()/resume()`,停止 → `TimerRepository.stop()` 并 `removeView()`
- [ ] 7.8 与 `CountdownService` 联动:服务 `onCreate` 时 `bubbleController.show()`,`onDestroy` 时 `bubbleController.hide()`

## 8. Wire Up & Polish

- [ ] 8.1 `MainActivity`:`setContent { YawnLockTheme { NavHost(...) } }`,`onResume` 调用 `PermissionChecker.refresh()`
- [ ] 8.2 启动路径:首次启动时若权限缺失,自动跳到 permissions 屏幕;否则直接 timer
- [ ] 8.3 倒计时归零的"已经锁屏"全屏覆盖:在 `LockReceiver` 触发后,服务弹一个 `Activity` 全屏显示"按电源键唤醒"
- [ ] 8.4 设置页入口(可选,v1 最小):`MainActivity` 提供"取消设备管理员"按钮,长按/隐藏
- [ ] 8.5 关闭悬浮窗后再开 App:Repository 检测到状态 Counting 但服务没运行 → 优雅降级为停止状态(避免 service 漏挂)
- [ ] 8.6 ProGuard 规则:保留 Compose 反射元数据、保留 Receiver/Service 类名
- [ ] 8.7 构建 `assembleDebug` 通过,生成 APK
- [ ] 8.8 真机/Pixel 模拟器冒烟测试主流程(选时长→倒计时→悬浮窗→到点锁屏→电源键唤醒)
