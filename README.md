# 打哈欠锁屏 / Yawn Lock

> 一个 Android 锁屏倒计时 app。设好时间,到点自动锁机。帮番茄钟 / 专注场景强制休息。
>
> An Android screen-lock countdown app. Set a duration, phone locks itself when time's up. Forces you to take a break.

<p align="center">
  <img src="app/src/main/res/drawable/yawn_lock_foreground.png" width="160" alt="Yawn Lock app icon"/>
</p>

<p align="center">
  <a href="#功能-features">功能 / Features</a> ·
  <a href="#截图-screenshots">截图 / Screenshots</a> ·
  <a href="#快速开始-quick-start">快速开始 / Quick Start</a> ·
  <a href="#架构-architecture">架构 / Architecture</a> ·
  <a href="#开发-developing">开发 / Developing</a> ·
  <a href="#发布-publishing">发布 / Publishing</a> ·
  <a href="#贡献-contributing">贡献 / Contributing</a> ·
  <a href="#许可-license">许可 / License</a>
</p>

<p align="center">
  <a href="https://github.com/woaiCarlos/yawn-lock/releases"><img alt="Release" src="https://img.shields.io/github/v/release/woaiCarlos/yawn-lock?include_prereleases&sort=semver"/></a>
  <a href="https://github.com/woaiCarlos/yawn-lock/blob/main/LICENSE"><img alt="License" src="https://img.shields.io/github/license/woaiCarlos/yawn-lock"/></a>
  <a href="https://github.com/woaiCarlos/yawn-lock/actions"><img alt="Build" src="https://img.shields.io/badge/build-passing-brightgreen"/></a>
</p>

---

## 中文

### 这是什么

**打哈欠锁屏 (Yawn Lock)** 是一款 Android 锁屏倒计时 app,核心场景是「番茄钟/专注 + 强制休息」:

- 你设好一个倒计时(5 秒到 24 小时)
- app 启动前台 service,保持后台运行
- 时间到了之后,**强制锁屏**(通过设备管理员权限)
- 期间悬浮窗显示剩余时间和暂停/停止按钮,你不用切回 app

跟普通计时器不一样 —— 它在到点时会**真正锁住你的手机**,而不是弹个通知或响个铃。这是为了对抗「再刷 5 分钟短视频就停」的拖延症。

### 适用场景

- 🍅 **番茄钟 / 专注** —— 25 分钟到点强制锁屏,真的得休息
- 📚 **写作业 / 看书** —— 1 小时后强制锁,避免「再看一眼手机就放下」
- 🛏️ **睡前放下手机** —— 设 10 分钟,到点锁屏,逼自己睡觉
- 👶 **小孩/老人限时使用** —— 锁屏后无法解锁(除非输 PIN)

### 核心特性

- ⏱️ **5 秒到 24 小时** 的任意时长倒计时(时钟式滚轮,小时/分钟/秒三列)
- 🔒 **到点强制锁屏** —— 通过 `DevicePolicyManager.lockNow()`,需要授予设备管理员权限
- 💬 **悬浮窗倒计时** —— app 切到后台后,屏幕角落有可拖动的气泡显示剩余时间
- ⏯️ **暂停 / 继续 / 停止** —— 任何时候都能从悬浮窗控制,不用切回 app
- 🏠 **自动跳到主屏** —— 启动倒计时后自动跳到桌面,提示「去专心做别的事」
- 🔋 **后台保活** —— 通过前台 service + 通知,系统不会杀进程
- 🛠️ **国内 ROM 友好** —— 检测华为/小米/OPPO 等激进后台管理,首次启动给提示引导用户加白名单
- 🌙 **Android 13+ 通知权限** —— 启动时申请 `POST_NOTIFICATIONS`(前台 service 需要)

### 需要的权限

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

### 快速开始(用户)

1. 从 [Releases](https://github.com/woaiCarlos/yawn-lock/releases) 下载最新 `yawn-lock-X.Y.Z-release.apk`(或 `.aab`)
2. 安装到 Android 7.0+ 设备(`minSdk = 26`)
3. 打开 app,按引导授予:悬浮窗权限 / 设备管理员 / 通知权限 / 电池优化白名单
4. 滚轮选好时长(预设 5/10/20/30 分钟,或自定义)
5. 点「开始计时」,app 自动跳到主屏,开始倒计时
6. 时间到 → 手机锁屏
7. 中途想停?点屏幕角落的气泡上的「停止」

### 快速开始(开发者)

```bash
# 1. 克隆
git clone https://github.com/woaiCarlos/yawn-lock.git
cd yawn-lock

# 2. 配置 JDK 17(项目用 AGP 8.5 + Kotlin 1.9 + JDK 17)
# 推荐 Homebrew:
brew install openjdk@17
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"

# 3. 构建 debug APK
./scripts/build.sh :app:assembleDebug
# 产出:app/build/outputs/apk/debug/yawn-lock-1.0.3-debug.apk
```

更详细的发布流程见 [RELEASE.md](./RELEASE.md),架构见 [docs/superpowers/](./docs/superpowers/),开发流程见 [CONTRIBUTING.md](./CONTRIBUTING.md)。

### 已知限制

- ⚠️ **未在真机长期测试** —— 已在 1 台 macOS 开发机上跑过逻辑,未做跨设备 / 跨 Android 版本的回归测试矩阵
- ⚠️ **iOS / HarmonyOS 不支持** —— 只做了 Android(用了 `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` 和 `DevicePolicyManager.lockNow()`)
- ⚠️ **国内 ROM 后台保活** —— 即使加了白名单,部分激进 ROM(尤其是 MIUI 12+ 的某些版本)仍会杀进程。已加引导但不是 100% 解决

---

## English

### What is this

**Yawn Lock (打哈欠锁屏)** is an Android screen-lock countdown app. The core use case is **"Pomodoro / focus + forced break"**:

- Set a duration (5 seconds to 24 hours)
- App starts a foreground service, keeps running in the background
- When time's up, **the phone is forcibly locked** (via device admin)
- During countdown, a floating bubble shows remaining time and pause/stop controls

Unlike a regular timer, this app **actually locks your phone** at the deadline — no notification, no alarm sound. It's designed to fight "let me scroll 5 more minutes" procrastination.

### Use cases

- 🍅 **Pomodoro / focus** — 25 min countdown → forced lock screen, you really have to take a break
- 📚 **Homework / reading** — 1 hour → forced lock, no more "one more phone check"
- 🛏️ **Bedtime phone drop** — 10 min → lock screen, force yourself to sleep
- 👶 **Kid / elderly screen time** — phone locked, can't unlock without PIN

### Core features

- ⏱️ **5 sec to 24 hours** countdown (clock-style wheel: hours / minutes / seconds)
- 🔒 **Forced lock at deadline** — via `DevicePolicyManager.lockNow()`, requires device admin permission
- 💬 **Floating bubble** — draggable, shows remaining time + pause/stop buttons; app stays in background
- ⏯️ **Pause / resume / stop** — from the bubble, no need to switch back to app
- 🏠 **Auto-go to home screen** — after start, app jumps to home, encourages you to focus on other things
- 🔋 **Background persistence** — foreground service + notification, system won't kill the process
- 🛠️ **CN-OEM friendly** — detects Huawei / Xiaomi / OPPO aggressive battery management, prompts user to add to whitelist on first launch
- 🌙 **Android 13+ notification permission** — requested on first launch (required for foreground service)

### Permissions

| Permission | Why | Required? |
|------------|-----|-----------|
| `SYSTEM_ALERT_WINDOW` | Floating bubble | Yes |
| `FOREGROUND_SERVICE` | Background persistence | Yes |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Android 14+ foreground service type | Yes |
| `POST_NOTIFICATIONS` | Android 13+ foreground service notification | Yes |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Long-timer exact alarm | Yes |
| `RECEIVE_BOOT_COMPLETED` | Restore after reboot | No (unused currently) |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | CN-OEM battery whitelist | Yes |

After launch, the app walks you through each permission. Device admin needs manual grant in `Settings → Security → Device admin`.

### Quick start (users)

1. Download the latest `yawn-lock-X.Y.Z-release.apk` (or `.aab`) from [Releases](https://github.com/woaiCarlos/yawn-lock/releases)
2. Install on Android 7.0+ device (`minSdk = 26`)
3. Open app, follow the wizard: grant overlay / device admin / notification / battery whitelist
4. Pick a duration via wheel (presets: 5/10/20/30 min, or custom)
5. Tap "开始计时" / "Start" — app auto-jumps to home, countdown begins
6. Time up → phone locks
7. Want to stop early? Tap "停止" / "Stop" on the floating bubble

### Quick start (developers)

```bash
# 1. Clone
git clone https://github.com/woaiCarlos/yawn-lock.git
cd yawn-lock

# 2. JDK 17 (project uses AGP 8.5 + Kotlin 1.9 + JDK 17)
brew install openjdk@17
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"

# 3. Build debug APK
./scripts/build.sh :app:assembleDebug
# Output: app/build/outputs/apk/debug/yawn-lock-1.0.3-debug.apk
```

For release / signing, see [RELEASE.md](./RELEASE.md). For architecture, see [docs/superpowers/](./docs/superpowers/). For dev workflow, see [CONTRIBUTING.md](./CONTRIBUTING.md).

### Known limitations

- ⚠️ **Not long-term real-device tested** — Logic was unit-tested on a macOS dev machine; no cross-device / cross-Android-version regression matrix yet
- ⚠️ **Android only** — no iOS / HarmonyOS (uses `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY` and `DevicePolicyManager.lockNow()`)
- ⚠️ **CN-OEM background-kill resilience** — even with whitelist, some aggressive ROMs (certain MIUI 12+ builds) still kill the process. A first-run prompt helps but is not 100% solved.

---

## 功能 / Features

|  | 中文 | English |
|--|------|---------|
| ⏱️ | 时钟式滚轮选时长(5 秒 ~ 24 小时) | Clock-style wheel, 5 sec ~ 24 hours |
| 🔒 | 到点强制锁屏(设备管理员权限) | Forced lock at deadline (device admin) |
| 💬 | 悬浮窗倒计时 + 暂停/停止 | Floating bubble + pause/stop |
| 🏠 | 启动后自动跳主屏 | Auto-go to home after start |
| 🔋 | 前台 service + 通知保活 | Foreground service + notification |
| 🛠️ | 国内 ROM 后台白名单引导 | CN-OEM battery whitelist wizard |
| 🌙 | Android 13+ 通知权限请求 | Android 13+ notification permission flow |
| 🐛 | 7+ 单元测试覆盖状态机 | 7+ unit tests for state machine |

## 截图 / Screenshots

(待补: 真机截图。等首次真机测试后,在 `docs/screenshots/` 目录放图,再把相对路径加到这里。)

_(To be added: real-device screenshots. After the first on-device test, drop images in `docs/screenshots/` and add relative paths here.)_

## 架构 / Architecture

```
┌────────────────────────────────────────────────────────────┐
│              UI Layer (Jetpack Compose)                    │
│  TimerScreen.kt  WheelColumn.kt  CustomDial.kt  PresetChips│
└────────────────────────────────────────────────────────────┘
                            ↕ StateFlow
┌────────────────────────────────────────────────────────────┐
│         ViewModel + Domain (Kotlin coroutines)            │
│  TimerViewModel.kt  TimerRepository.kt  TimerState          │
│  TimerStatus: Idle / Counting / Paused / Finished         │
└────────────────────────────────────────────────────────────┘
                            ↕ Intent (Action: START/STOP/PAUSE/RESUME)
┌────────────────────────────────────────────────────────────┐
│              Android Service Layer                         │
│  CountdownService (foreground service, ticker)            │
│  FloatingBubbleController (WindowManager overlay)         │
│  NotificationCenter (foreground notification)            │
│  LockReceiver (alarm fire → triggerLockNow)                │
│  StopReceiver (broadcast stop from notification)           │
│  LockedFallbackActivity (shown if device admin revoked)   │
└────────────────────────────────────────────────────────────┘
                            ↕ BroadcastReceiver
┌────────────────────────────────────────────────────────────┐
│          System: AlarmManager + DevicePolicyManager       │
└────────────────────────────────────────────────────────────┘
```

关键技术决策详见 [docs/superpowers/](./docs/superpowers/) 下的设计文档。

## 技术栈 / Tech Stack

- **语言**: Kotlin 1.9.24, JVM target 17
- **构建**: Android Gradle Plugin 8.5, Gradle 8.7
- **UI**: Jetpack Compose (BOM 2024.06), Material 3
- **架构**: 单 Activity + Compose Navigation, MVVM 风格 (StateFlow + ViewModel)
- **状态机**: `TimerState` sealed class (Idle/Counting/Paused/Finished)
- **后台**: `Service` (foreground, specialUse type) + `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`
- **闹钟**: 短时(≤ 5 min)用 `Handler.postDelayed`,长时用 `AlarmManager.setExactAndAllowWhileIdle`
- **测试**: JUnit 4 + Robolectric 4.13(纯 JVM,无 Android device 需求)
- **最低 SDK**: 26 (Android 8.0)
- **目标 SDK**: 34 (Android 14)
- **开发流程**: [Comet](https://github.com/...) 双星流程(OpenSpec + Superpowers),见 [CONTRIBUTING.md](./CONTRIBUTING.md)

## 开发 / Developing

参见 [CONTRIBUTING.md](./CONTRIBUTING.md)。简单摘要:

```bash
# Setup
git clone https://github.com/woaiCarlos/yawn-lock.git
cd yawn-lock
brew install openjdk@17  # 如果还没装
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"

# 日常开发
./scripts/build.sh :app:assembleDebug      # 构建 debug APK
./scripts/build.sh :app:testDebugUnitTest  # 跑单测 (JUnit + Robolectric)
./scripts/build.sh :app:assembleRelease    # 构建 release APK (需 release.keystore)

# 提交前
./gradlew lint                           # 代码风格检查(可选)
```

> **注意**: `release.keystore` **不会**进 git 仓库(在 `.gitignore` 里)。要构建 release APK,需要先把 keystore 放到 `app/release.keystore`,并在 `~/.gradle/gradle.properties` 配 `RELEASE_STORE_PASSWORD` / `RELEASE_KEY_PASSWORD` / `RELEASE_KEY_ALIAS`。详见 [RELEASE.md](./RELEASE.md)。

## 发布 / Publishing

完整发布 SOP 见 [RELEASE.md](./RELEASE.md)。简版:

```bash
# 1. 改 app/build.gradle.kts 里的 versionName / versionCode

# 2. 重建 release APK / AAB
./scripts/build.sh :app:assembleRelease
./scripts/build.sh :app:bundleRelease   # 产出 .aab 给 Play Store

# 3. apksigner 自检
java -jar ~/Library/Android/sdk/build-tools/34.0.0/lib/apksigner.jar \
  verify --print-certs app/build/outputs/apk/release/yawn-lock-*-release.apk

# 4. 上传到 Google Play / 国内应用市场
```

**注意**: Google Play 8 月起要求新注册开发者上传 `.aab`,不接受 `.apk`。国内商店通常要 `.apk`,且可能要重签。

## 路线图 / Roadmap

- [ ] 真机回归测试矩阵(至少 3 台设备,涵盖 Android 8/10/12/14)
- [ ] 自适应启动器图标(Android 12+ 主题图标)
- [ ] 桌面 widget(快速设时长)
- [ ] 历史记录(每天专注多久,简单统计)
- [ ] 锁屏界面(锁屏后显示「专注了 X 分钟」)
- [ ] 暗色模式优化

## 贡献 / Contributing

欢迎贡献!任何形式都欢迎:bug 报告、功能建议、文档改进、PR。

提交 issue / PR 前请先看 [CONTRIBUTING.md](./CONTRIBUTING.md)。

## 安全 / Security

发现安全漏洞请**不要**在公开 issue 提,私下联系我。详见 [SECURITY.md](./SECURITY.md)。

## 行为准则 / Code of Conduct

参与者请遵守 [CODE_OF_CONDUCT.md](./CODE_OF_CONDUCT.md)。简短版:友善、包容、专业。

## 许可 / License

本项目采用 **MIT 许可证** —— 详见 [LICENSE](./LICENSE)。

简单说:你可以随便用、商用、修改、分发,只要保留版权声明和许可声明。作者不提供任何担保。

---

## 致谢 / Acknowledgments

- 灵感: 番茄钟工作法 + 「放下手机」挑战
- 用了这些开源项目 / 工具:
  - [Jetpack Compose](https://developer.android.com/jetpack/compose) (Apache 2.0)
  - [Material 3](https://m3.material.io/) (Apache 2.0)
  - [JUnit 4](https://junit.org/junit4/) (EPL 1.0)
  - [Robolectric](https://robolectric.org/) (MIT)
  - [Comet](https://github.com/...) 工作流(双星 OpenSpec + Superpowers)(MIT)

## Star History

(如果你喜欢这个项目,点 ⭐ 是最大支持!)

_(If you like this project, please star it! ⭐)_

---

**Made with 😴 for people who can't put down their phone.**
