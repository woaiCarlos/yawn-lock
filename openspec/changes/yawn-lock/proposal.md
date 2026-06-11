# Proposal: 打哈欠 (yawn-lock) — Android 定时锁屏应用

## Why

深夜用手机时一抬头已经凌晨 2 点,再抬头已经 4 点——这是几乎所有重度手机用户的痛点。系统自带的"定时开关机"和"健康使用手机"功能要么粗粒度(整机关闭)、要么需要绕开家长控制(未成年人模式),无法满足成年人"我只想在指定时长后强制锁屏让自己去睡"这一窄而真实的需求。

我们要构建的 **打哈欠** 是一个极简的 Android 应用:选一个时长(30 秒到 120 分钟),到点强制锁屏,期间可以在其他 App 上方显示一个可拖动的倒计时气泡。整个交互控制在 3 个屏幕以内,UI 借鉴原型的紫色"晚安"主题。

## What Changes

- **新增** Android 应用 `yawn-lock` (Kotlin + Jetpack Compose),包名 `com.example.yawnlock`
- **新增** 主计时器屏幕:快速预设(30s/1m/5m/10m)+ 自定义面板(±键 + 1-120min 滑块)
- **新增** 倒计时执行:通过 `WorkManager` 调度 + `AlarmManager` 精确触发;前台服务保持计时
- **新增** 到点锁屏:通过 `BIND_DEVICE_ADMIN` (设备管理员) 权限调用 `DevicePolicyManager.lockNow()`
- **新增** 跨 App 悬浮气泡:通过 `SYSTEM_ALERT_WINDOW` 权限,显示迷你倒计时 + 暂停/停止
- **新增** 权限引导页:列出"悬浮窗""设备管理员"两项必授权限,引导用户跳转系统设置
- **修改** N/A (全新项目,无既有代码)

## Capabilities

### New Capabilities

- `scheduled-screen-lock`: 选时长、倒计时、到点强制锁屏,以及相关权限管理(设备管理员)
- `floating-countdown-widget`: 跨 App 显示的可拖动/可收起悬浮倒计时气泡(系统级悬浮窗)

### Modified Capabilities

- 无 (本目录为全新项目,`openspec/specs/` 不存在)

## Impact

- **新建项目**:无既有代码影响
- **API/依赖**:
  - Android SDK 26+ (minSdk 26, targetSdk 34)
  - `androidx.work:work-runtime-ktx` (前台服务)
  - `androidx.compose.*` (UI)
  - `androidx.lifecycle:lifecycle-viewmodel-compose` (ViewModel)
  - 无第三方三方依赖 (不引入 Hilt/Room/Retrofit 等,保持 v1 极简)
- **权限**:
  - `SYSTEM_ALERT_WINDOW` — 悬浮窗
  - `BIND_DEVICE_ADMIN` — 设备管理员(锁屏)
  - `POST_NOTIFICATIONS` — 前台服务通知 (Android 13+)
  - `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` — 倒计时前台服务
- **首次安装路径**:用户开 App → 看到"必授权限"提示 → 分别跳转系统设置授予 → 返回后可见计时器
- **边界场景**:
  - 锁屏后用户按电源键唤醒:屏幕确实被锁(电源键开屏需要解锁)
  - 倒计时中用户卸载 App:卸载需先取消设备管理员
  - 设备重启:AlarmManager 会被清空,目前 v1 不做持久化恢复(下次启动重新开始)
- **非目标**:
  - 勿扰模式自动静音
  - 自定义预设记忆
  - 多语言(仅 zh-CN)
  - 锁屏密码/指纹保护(系统自带即可)
  - 熄屏曲线 / 智能省电联动
  - iOS / 鸿蒙版本
