# Changelog / 变更日志

All notable changes to this project will be documented in this file.

本项目的所有重要变更都记录在此文件。

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.3] - 2026-06-13

### Fixed / 修复
- 🐛 **Stop button now fully clears state** (no more preserved `durationMs`). Previously, after tapping stop, the wheel would still show the old duration and the user could re-tap Start to launch with the same value. Now stop returns to a clean Idle+0+0 state. The user feedback was: "I tapped stop, not pause — it should clear all state."
- 🐛 **Mid-countdown wheel change now requires re-tap Start** (no more auto-continue). Previously (in 1.0.2), changing the wheel while a countdown was running would change the duration and let the timer continue with the new value. Now any wheel change (whether Counting, Paused, or Idle) resets the state to Idle+newDuration, requiring the user to explicitly tap Start to begin a new countdown.
- 🐛 **Paused state cleared on wheel change**. Previously, while Paused, changing the wheel kept the Paused status (forcing the user to find the resume button). Now wheel change clears the pause.

### Changed / 变化
- ⚠️ **Behavior change vs 1.0.0/1.0.1/1.0.2**: the "quickly restart with the same duration after stop" workflow is removed. User explicitly requested stricter semantics.

---

## [1.0.2] - 2026-06-13

### Fixed / 修复
- 🐛 **Mid-countdown wheel change now takes effect**. Previously, changing the wheel while a countdown was running had no effect (the `preview()` function had a guard `if (current.isActive) return` that silently dropped the change). Now `preview()` updates the duration mid-countdown.
- 🐛 **`deadlineElapsed` resync**. When the wheel changes during a countdown, the internal deadline is also resynced so the next tick() uses the new deadline.

> ⚠️ **Superseded by 1.0.3**: the 1.0.2 behavior of "let countdown continue with new full duration after wheel change" was rejected by user testing. 1.0.3 reverts to a stricter "any change requires re-tap Start" model.

---

## [1.0.1] - 2026-06-13

### Fixed / 修复
- 🐛 **Bubble attached state stuck after `wm.removeView` failures**. In `FloatingBubbleController.hide()`, the `attached = false` reset was inside a `try` block, so a transient WindowManager exception (`IllegalStateException` on overlay permission jitter) would leave the controller thinking the bubble was still attached. Subsequent `show()` calls would then bail on `if (attached) return` and the bubble would never re-appear. Fix: use `finally` block to unconditionally reset `attached = false`.
- 🐛 **Second countdown start after early stop would not start**. The first countdown ran fine; if user stopped early and immediately tapped Start again, the bubble would not appear, and the countdown wouldn't visibly run. Root cause was the same `attached` stuck-true bug as above.
- 🐛 **Adaptive launcher icon properly wired**. The new `yawn_lock_foreground.png` (a cute sleeping/yawning cloud on a purple background) was added to `drawable/` but never referenced in `mipmap-anydpi-v26/yawn_lock_launcher.xml`. Fix: point the launcher icon's `<foreground>` element to the new asset.

### Changed / 变化
- 📦 **APK output renamed** to `yawn-lock-{versionName}-{buildType}.apk` via a Gradle task (`renameApksToReleaseConvention`), matching the naming convention documented in `RELEASE.md`. Previously APKs were named `app-release.apk` and required manual `cp` rename.
- 🛠️ Added `scripts/build.sh` wrapper that sets `JAVA_HOME` to Homebrew's openjdk@17 before invoking gradle, because the new strict `comet-guard.sh` build_command validation rejects shell metacharacters (`&&`, `;`, `|`, etc.).

---

## [1.0.0] - 2026-06-12

### Added / 新增
- 🎉 **First public release**.
- ⏱️ Clock-style wheel picker (5 sec to 24 hours)
- 🔒 Forced screen lock at deadline via `DevicePolicyManager.lockNow()`
- 💬 Floating bubble showing remaining time + pause/stop controls
- ⏯️ Pause / resume / stop from the bubble
- 🏠 Auto-go-to-home after start
- 🔋 Foreground service with notification for background persistence
- 🛠️ CN-OEM (Huawei / Xiaomi / OPPO / vivo / OnePlus) battery-whitelist prompt on first launch
- 📚 Documentation: `README.md`, `RELEASE.md`

### Known Issues / 已知问题
- ⚠️ Bubble attached-state bug (see 1.0.1 fix)
- ⚠️ Mid-countdown preview no-op (see 1.0.2 then 1.0.3 for the final fix)
- ⚠️ APK naming required manual `cp` (see 1.0.1)
- ⚠️ Launcher icon didn't use the new foreground asset (see 1.0.1)

---

## Format / 格式说明

- **Added** for new features
- **Changed** for changes in existing functionality
- **Deprecated** for soon-to-be-removed features
- **Removed** for now-removed features
- **Fixed** for any bug fixes
- **Security** for vulnerability fixes
