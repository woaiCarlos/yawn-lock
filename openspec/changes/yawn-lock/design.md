# Design: 打哈欠 (yawn-lock) — Android 定时锁屏

## Context

全新项目,工作目录 `打哈欠/` 当前仅有 `Web-Prototype/` 子目录下的 3 个 HTML 视觉原型(`timer.html` / `overlay.html` / `floating.html`)。本次构建首个可运行的 Android 应用版本(v1)。

**约束**:
- 工作环境 macOS (开发机),无 Android Studio 本地配置,需用 CLI 工具链(Gradle Wrapper + JDK 17)生成 APK
- 包名占位 `com.example.yawnlock`(上线前可改)
- v1 极简原则:不引入 DI 框架、不引入数据库、不引入网络层
- 目标用户:Android 8.0+ (API 26)

**利益相关方**:
- 终端用户:重度手机用户,需要"强制自己放下手机"的心理钩子
- 开发:本项目作者(单兵)
- 系统:Android 权限系统(尤其 Device Admin、悬浮窗的强提示)

## Goals / Non-Goals

**Goals**:
- v1 在真机/模拟器上完整跑通"选时长 → 倒计时 → 到点锁屏 → 唤醒"主流程
- 悬浮气泡可在其他 App 上拖动/收起,符合原型交互
- 权限引导路径清晰:用户首次启动即可在 ≤3 次跳转内授予所有必选权限
- APK 体积 < 5 MB
- 单模块、零三方业务依赖(仅 Compose + WorkManager 等 AndroidX)

**Non-Goals**:
- 勿扰模式自动静音、熄屏曲线、自定义预设记忆(已写进 proposal 的非目标)
- 多语言(仅 zh-CN)
- 后台统计、远程推送、应用商店发布
- 单元/UI 测试覆盖度目标(本版本不写测试,但代码结构应利于后期补测)

## Decisions

### D1. 锁屏机制: DevicePolicyManager.lockNow()

**选型**: 通过 `BIND_DEVICE_ADMIN` 注册为设备管理员后调用 `DevicePolicyManager.lockNow()`。

**为什么**:
- 这是 Android 唯一**任何普通 App 都能调用且能立即锁屏**的官方 API
- 不需要 root、不需要 Accessibility 权限(后者需要用户额外信任"无障碍服务"敏感权限)
- 用户可随时在系统设置 → 安全 → 设备管理应用中撤销,符合"用户可控"

**替代方案对比**:
- AccessibilityService:可模拟电源键,但 1) 用户授权门槛极高,2) Play Store 审核严格
- KeyguardManager.createConfirmDeviceCredentialIntent:仅弹解锁界面,不真锁屏
- Root 命令:不可行,产品面向普通用户

**约束**:
- 卸载前必须先 `DevicePolicyManager.removeActiveAdmin()`(Android 系统强制),需在 App 内提供"取消管理员"入口

### D2. 倒计时执行: Foreground Service + AlarmManager.setExactAndAllowWhileIdle

**选型**: 倒计时开始时启动前台服务(`Service.startForeground()`),服务内 `AlarmManager.setExactAndAllowWhileIdle()` 在指定时刻触发 `PendingIntent` 调用 `lockNow()`。

**为什么**:
- 后台服务在 Android 8+ 限制下,只有"前台服务"类型能长时间运行
- `setExactAndAllowWhileIdle` 比 `set` 精度高,且能绕过 Doze 模式(必要,因为我们需要"到点"的硬保证)
- Android 14+ 要求声明 `FOREGROUND_SERVICE_SPECIAL_USE` 权限

**替代方案对比**:
- WorkManager.OneTimeWorkRequest:不够精确(分钟级),不满足"30 秒"等短时长
- 纯 Handler.postDelayed:进程被杀立即失效
- JobScheduler:同 WorkManager 问题

**注意**: Android 14+ `USE_EXACT_ALARM` 权限受 Play Store 审核约束。我们用 `setExactAndAllowWhileIdle` + `SCHEDULE_EXACT_ALARM`(用户可拒绝)。若拒绝,降级到 `setAndAllowWhileIdle`(允许 ±5 分钟误差)。

### D3. 悬浮气泡: WindowManager + TYPE_APPLICATION_OVERLAY

**选型**: 启动前台服务后,通过 `WindowManager.addView()` 添加 `TYPE_APPLICATION_OVERLAY` 类型的自定义 View。`onTouchListener` 处理拖动手势,接近右边缘自动收起为小药丸(`width=36dp`)。

**为什么**:
- `TYPE_APPLICATION_OVERLAY` 是 API 26+ 唯一合法的"跨 App 悬浮窗"窗口类型
- 需用户先授予 `SYSTEM_ALERT_WINDOW`(在系统设置 → 特殊应用权限 → 显示在其他应用上方)
- Compose 暂未原生支持嵌入 WindowManager,采用 `ComposeView` + AndroidView 包装

**手势设计**(沿用原型):
- 拖动距离 < 3px:视为点击
- 距离右边缘 < 28px:展开"贴边提示"
- 释放时若 right < 36px:收起为小药丸
- 点击小药丸:展开

### D4. 状态管理: ViewModel + StateFlow + Compose collectAsStateWithLifecycle

**选型**: 每个屏幕一个 `ViewModel`,内部 `MutableStateFlow<TimerUiState>`,Compose 端 `collectAsStateWithLifecycle()` 订阅。

**为什么**:
- `StateFlow` 是 Android 官方推荐的状态容器,天然防泄漏(lifecycle-aware)
- 倒计时这种高频更新场景下,`MutableState` 会触发全 Composable 重组;`StateFlow` + `distinctUntilChanged` 更稳
- 不引入 Hilt/DI 框架,ViewModel 用 `viewModel { ... }` lambda 工厂

### D5. 屏幕结构: 3 个 Composable 屏幕 + Navigation Compose

```
MainActivity
  ├── NavHost
  │   ├── "timer"  → TimerScreen()       (原型 timer.html)
  │   ├── "permissions" → PermissionsScreen() (原型 overlay.html)
  │   └── (悬浮气泡不在 NavHost 中,而是 WindowManager 全局 View)
  └── YawnApplication (持有 TimerRepository 单例)
```

### D6. 项目结构

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
│   │   │   │   ├── theme/         # 颜色/字体/形状 Material 3 主题
│   │   │   │   ├── timer/         # TimerScreen + TimerViewModel
│   │   │   │   ├── permissions/   # PermissionsScreen + PermissionsViewModel
│   │   │   │   └── components/    # 通用 Compose 组件
│   │   │   ├── service/
│   │   │   │   ├── CountdownService.kt   # 前台服务
│   │   │   │   ├── LockReceiver.kt       # 触发锁屏的 BroadcastReceiver
│   │   │   │   └── FloatingBubbleController.kt
│   │   │   ├── data/
│   │   │   │   ├── DeviceAdminReceiver.kt  # DeviceAdminReceiver 子类
│   │   │   │   └── PermissionChecker.kt    # 运行时权限状态查询
│   │   │   └── domain/
│   │   │       └── TimerRepository.kt       # 全局计时状态单例
│   │   └── res/
│   │       ├── values/strings.xml, colors.xml, themes.xml
│   │       ├── drawable/                  # 月亮/星星/锁图标 (SVG 转 vector)
│   │       └── mipmap/                    # 启动图标 (5 个密度)
│   └── proguard-rules.pro
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
└── gradlew
```

## Risks / Trade-offs

- **[风险] 设备管理员被用户撤销** → 启动倒计时前 `isAdminActive()` 检查,撤销时引导重新授权;锁屏前再次检查
- **[风险] Android 14 精确闹钟需 `SCHEDULE_EXACT_ALARM` 用户授权** → 拒绝时降级到 `setAndAllowWhileIdle`,UI 提示"精确度 ±5 分钟"
- **[风险] 悬浮窗在小米/华为/OPPO 等国产 ROM 上行为不一致** → 引导用户到对应 ROM 的"自启动/悬浮窗"专项设置;v1 至少保证 Pixel/原生系统正常
- **[风险] 锁屏后 JobScheduler/AlarmManager 行为变化** → 锁屏本身不影响 AlarmManager 触发,且锁屏后用户必须按键唤醒,App 自然不需运行
- **[风险] 倒计时中用户强杀进程** → `AlarmManager.setExactAndAllowWhileIdle` 由系统托管,即使 App 进程被杀,闹钟仍会触发;BroadcastReceiver 在新进程中执行 `lockNow()`
- **[权衡] 零三方依赖意味着手写 ViewModel 工厂、Resource 读取等** → 接受 v1 重复样板;v2 引入 Hilt 收益才大
- **[权衡] 悬浮气泡不用 Compose 原生 Window API** → 用 AndroidView 包 ComposeView,失去部分重组优化,但保持与 WindowManager 生态兼容

## Migration Plan

全新项目,无迁移需求。

## Open Questions

- Q1: 应用图标(月亮 + Z 字母组合)是否手绘矢量,还是先用 emoji 占位? **倾向**:用 emoji 🌙 占位 + 文本标签,后续替换
- Q2: 倒计时中是否需要"再来 5 分钟"按钮? 原型 timer.html 有,但若开着悬浮窗再点扩展,可能与"到点强制锁"的产品哲学冲突。**倾向**:在悬浮窗上提供"暂停"和"停止",不提供延长,保持强制力
- Q3: 是否需要"勿扰模式"功能? proposal 已划入非目标。**确认**: v1 不做
- Q4: 包名 `com.example.yawnlock` 是否要换成更正式的(如 `cn.carlos.yawnlock`)? **倾向**:v1 用 `com.example.yawnlock`,发布前再换
