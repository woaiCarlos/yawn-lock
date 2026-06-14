# 打哈欠锁屏

> 一个 Android 锁屏倒计时 app。设好时间,到点自动锁机,强制休息。

<p align="center">
  <img src="app/src/main/res/drawable/yawn_lock_foreground.png" width="160" alt="打哈欠锁屏 app 图标"/>
</p>

<p align="center">
  <a href="https://github.com/woaiCarlos/yawn-lock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/woaiCarlos/yawn-lock?include_prereleases&sort=semver"/></a>
  <a href="https://github.com/woaiCarlos/yawn-lock/blob/main/LICENSE"><img alt="License" src="https://img.shields.io/github/license/woaiCarlos/yawn-lock"/></a>
</p>

---

## 这是什么

**打哈欠锁屏** 是一款 Android 锁屏倒计时 app,核心场景就是「**到点强制锁屏,逼你休息**」:

- 你设好一个倒计时(5 秒到 24 小时)
- app 启动前台 service,保持后台运行
- 时间到了之后,**强制锁屏**(通过设备管理员权限)
- 期间悬浮窗显示剩余时间和暂停/停止按钮,你不用切回 app

跟普通计时器不一样 —— 它在到点时会**真正锁住你的手机**,而不是弹个通知或响个铃。这是为了对抗「再刷 5 分钟短视频就停」的拖延症。

## 适用场景

- 🎯 **专注** —— 25 分钟到点强制锁屏,真的得休息
- 📚 **写作业 / 看书** —— 1 小时后强制锁,避免「再看一眼手机就放下」
- 🛏️ **睡前放下手机** —— 设 10 分钟,到点锁屏,逼自己睡觉
- 👶 **小孩/老人限时使用** —— 锁屏后无法解锁(除非输 PIN)

## 核心特性

- ⏱️ **5 秒到 24 小时** 的任意时长倒计时(时钟式滚轮,小时/分钟/秒三列)
- 🔒 **到点强制锁屏** —— 通过 `DevicePolicyManager.lockNow()`,需要授予设备管理员权限
- 💬 **悬浮窗倒计时** —— app 切到后台后,屏幕角落有可拖动的气泡显示剩余时间
- ⏯️ **暂停 / 继续 / 停止** —— 任何时候都能从悬浮窗控制,不用切回 app
- 🏠 **自动跳到主屏** —— 启动倒计时后自动跳到桌面,提示「去专心做别的事」
- 🔋 **后台保活** —— 通过前台 service + 通知,系统不会杀进程
- 🛠️ **国内 ROM 友好** —— 检测华为/小米/OPPO 等激进后台管理,首次启动给提示引导用户加白名单
- 🌙 **Android 13+ 通知权限** —— 启动时申请 `POST_NOTIFICATIONS`(前台 service 需要)

## 需要的权限

| 权限 | 用途 | 必需? |
|------|------|------|
| `SYSTEM_ALERT_WINDOW` | 悬浮窗 | 是 |
| `FOREGROUND_SERVICE` | 后台保活 | 是 |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ 区分前台服务类型 | 是 |
| `POST_NOTIFICATIONS` | Android 13+ 显示前台服务通知 | 是 |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | 长时倒计时精确闹钟 | 是 |
| `RECEIVE_BOOT_COMPLETED` | 开机后恢复 | 否(暂未用) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 国内 ROM 不被电池优化杀 | 是 |

启动 app 后,系统会一步步引导用户授权。设备管理员权限需要在「系统设置 → 安全 → 设备管理」中手动授权。

## 快速开始(用户)

1. 从 [Releases](https://github.com/woaiCarlos/yawn-lock/releases) 下载最新 `yawn-lock-X.Y.Z-release.apk`(或 `.aab`)
2. 安装到 Android 7.0+ 设备(`minSdk = 26`)
3. 打开 app,按引导授予:悬浮窗权限 / 设备管理员 / 通知权限 / 电池优化白名单
4. 滚轮选好时长(预设 5/10/20/30 分钟,或自定义)
5. 点「开始计时」,app 自动跳到主屏,开始倒计时
6. 时间到 → 手机锁屏
7. 中途想停?点屏幕角落的气泡上的「停止」

## 快速开始(开发者)

```bash
# 1. 克隆
git clone https://github.com/woaiCarlos/yawn-lock.git
cd yawn-lock

# 2. 配置 JDK 17(项目用 AGP 8.5 + Kotlin 1.9 + JDK 17)
brew install openjdk@17
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"

# 3. 构建 debug APK
./scripts/build.sh :app:assembleDebug
# 产出:app/build/outputs/apk/debug/yawn-lock-1.0.3-debug.apk

# 4. 跑单测(10/10 应通过)
./scripts/build.sh :app:testDebugUnitTest
```

> **注意**:`release.keystore` **不会**进 git 仓库(在 `.gitignore` 里)。要构建 release APK,需要先把 keystore 放到 `app/release.keystore`,并在 `~/.gradle/gradle.properties` 配 `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD` / `RELEASE_KEY_ALIAS`。

## 已知限制

- ⚠️ **iOS / HarmonyOS 不支持** —— 只做了 Android(用了 `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` 和 `DevicePolicyManager.lockNow()`)
- ⚠️ **国内 ROM 后台保活** —— 即使加了白名单,部分激进 ROM(尤其是 MIUI 12+ 的某些版本)仍可能杀进程。已加引导但不是 100% 解决

> 真机已测过几天,无问题。代码层还有 10/10 单元测试覆盖核心状态机。

## 技术栈

- **语言**:Kotlin 1.9.24,JVM target 17
- **构建**:Android Gradle Plugin 8.5,Gradle 8.7
- **UI**:Jetpack Compose (BOM 2024.06),Material 3
- **架构**:单 Activity + Compose Navigation,MVVM 风格(StateFlow + ViewModel)
- **状态机**:`TimerState` sealed class(Idle/Counting/Paused/Finished)
- **后台**:`Service` (foreground, specialUse type) + `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`
- **闹钟**:短时(≤ 5 min)用 `Handler.postDelayed`,长时用 `AlarmManager.setExactAndAllowWhileIdle`
- **测试**:JUnit 4 + Robolectric 4.13(纯 JVM,无 Android device 需求)
- **最低 SDK**:26 (Android 8.0)
- **目标 SDK**:34 (Android 14)

## 架构

```
┌────────────────────────────────────────────────────────────┐
│              UI 层 (Jetpack Compose)                       │
│  TimerScreen.kt  WheelColumn.kt  CustomDial.kt  PresetChips│
└────────────────────────────────────────────────────────────┘
                            ↕ StateFlow
┌────────────────────────────────────────────────────────────┐
│         ViewModel + 领域层 (Kotlin coroutines)            │
│  TimerViewModel.kt  TimerRepository.kt  TimerState          │
│  TimerStatus: Idle / Counting / Paused / Finished         │
└────────────────────────────────────────────────────────────┘
                            ↕ Intent (Action: START/STOP/PAUSE/RESUME)
┌────────────────────────────────────────────────────────────┐
│              Android Service 层                            │
│  CountdownService (前台 service, ticker)                   │
│  FloatingBubbleController (WindowManager 悬浮窗)          │
│  NotificationCenter (前台通知)                            │
│  LockReceiver (闹钟触发 → 锁屏)                           │
│  StopReceiver (通知里点停止的广播接收)                    │
│  LockedFallbackActivity (设备管理员被撤销时的降级)        │
└────────────────────────────────────────────────────────────┘
                            ↕ BroadcastReceiver
┌────────────────────────────────────────────────────────────┐
│          系统: AlarmManager + DevicePolicyManager          │
└────────────────────────────────────────────────────────────┘
```

---

## 致谢

- 灵感:「放下手机」挑战
- 用了这些开源项目:
  - [Jetpack Compose](https://developer.android.com/jetpack/compose) (Apache 2.0)
  - [Material 3](https://m3.material.io/) (Apache 2.0)
  - [JUnit 4](https://junit.org/junit4/) (EPL 1.0)
  - [Robolectric](https://robolectric.org/) (MIT)

---

## 许可

本项目采用 **MIT 许可证** —— 详见 [LICENSE](./LICENSE)。

简单说:你可以随便用、商用、修改、分发,只要保留版权声明和许可声明。作者不提供任何担保。

---

**Made with 😴 for people who can't put down their phone.**
