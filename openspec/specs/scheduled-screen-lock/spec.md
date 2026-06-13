# Spec: scheduled-screen-lock

## Purpose

(主 spec。`## MODIFIED Requirements` 是 delta-only 头, 改名为 `## Requirements`。文件顶部的
「delta for yawn-lock-polish」注释和「变更」字样都是从旧 delta 残留下来,清掉。)

## Requirements

### Requirement: Select Lock Duration

滑块从"分钟级"改为"秒级"精度,范围 5 秒 - 2 小时;预设从短时段(30s/1m/5m/10m)改为中长时段(10m/30m/1h/2h)。

The system SHALL allow the user to select a lock duration on the main timer screen.

The duration SHALL be selectable from four quick presets: **10 minutes, 30 minutes, 1 hour, 2 hours**.

The duration SHALL additionally be adjustable via a custom control with:
- Plus/minus buttons (each step = 5 seconds when duration < 60s, 30 seconds when 60s ≤ duration < 300s, 60 seconds when duration ≥ 300s)
- A range slider from **5 seconds to 7200 seconds (2 hours), with 1-second precision**
- Display of the current value with unit (seconds for < 60s, minutes for ≥ 60s)

#### Scenario: User selects a quick preset
- **WHEN** the user taps the "10 分钟" preset chip
- **THEN** the timer state updates to 10 minutes (600 seconds)
- **AND** the "10 分钟" chip becomes visually active
- **AND** the custom dial displays "10 分钟"

#### Scenario: User adjusts duration with plus button
- **WHEN** the current duration is 30 seconds and the user taps the plus button
- **THEN** the duration increases to 35 seconds (5-second step)
- **WHEN** the current duration is 5 minutes (300 seconds) and the user taps the plus button
- **THEN** the duration increases to 6 minutes (60-second step)

#### Scenario: User drags the slider
- **WHEN** the user drags the slider to position 1800 (seconds)
- **THEN** the duration updates to 1800 seconds (30 minutes)
- **AND** the dial displays "30 分钟"

#### Scenario: User adjusts duration to sub-minute value
- **WHEN** the user sets duration to 45 seconds
- **THEN** the dial displays "45 秒" (not "0 分钟")

---

### Requirement: Start Countdown

**变更**: 启动倒计时的"开始"按钮文案从"开始锁屏"改为"开始计时";Timer 屏幕现在有一个"权限"入口可主动跳转到 Permissions 屏幕(不再依赖"开始"按钮触发跳转)。

The system SHALL start a countdown when the user taps the "**开始计时**" (Start Countdown) button, provided that the device-admin permission and overlay permission have been granted.

The countdown SHALL:
- Run as a foreground service with a persistent notification showing remaining time
- Schedule an exact alarm via `AlarmManager.setExactAndAllowWhileIdle` at the lock deadline
- Display a full-screen status card on the timer screen with circular progress ring and remaining time
- The "开始计时" button SHALL be hidden while countdown is active

The timer screen SHALL provide a **permissions/settings entry point** (e.g., icon in the hero card or a top-right icon button) that navigates to the permissions screen regardless of whether permissions are currently granted.

#### Scenario: User starts a 10-minute countdown
- **WHEN** the user has granted both device-admin and overlay permissions, selected 10 minutes, and taps "开始计时"
- **THEN** the countdown begins
- **AND** a foreground service starts and shows a notification
- **AND** an exact alarm is scheduled for 10 minutes later
- **AND** the timer screen displays the status card with a 10:00 ring at 0% progress
- **AND** the floating bubble appears on the home screen / other apps

#### Scenario: User attempts to start without device-admin permission
- **WHEN** the user has not granted device-admin permission and taps "开始计时"
- **THEN** the system SHALL navigate to the permissions screen
- **AND** SHALL NOT start a countdown

#### Scenario: User navigates from timer to permissions via icon
- **WHEN** the user is on the timer screen with all permissions granted and taps the permissions icon
- **THEN** the system SHALL navigate to the permissions screen
- **AND** the permissions list SHALL show the current state of all permissions

---

### Requirement: Permission Gating on Timer Screen

**变更**: 权限状态从"启动时静态检查"改为"实时刷新"。`MainActivity` 监听 `ON_RESUME`,每次从后台返回都重新查询权限,推送给 UI。

The system SHALL visually indicate on the timer screen when one or more required permissions are missing, before the user attempts to start a countdown.

The "开始计时" button MAY remain tappable but SHALL route the user to the permissions screen if any required permission is missing.

The system SHALL re-query the permission state whenever the `MainActivity` resumes (e.g., after returning from system settings), and update the UI within 100ms of the resume event.

#### Scenario: Timer screen with no permissions granted
- **WHEN** the user opens the timer screen with no permissions granted
- **THEN** a small warning chip SHALL be visible above the "开始计时" button indicating "需要先授予权限"
- **AND** tapping "开始计时" navigates to the permissions screen

#### Scenario: User grants permission and returns to app
- **WHEN** the user is on the permissions screen, taps the overlay row, opens system settings, enables the toggle, presses back, and returns to the app
- **THEN** within 100ms of the app becoming visible, the permissions screen's row state badge updates to "已授权"
- **AND** if the user navigates back to the timer screen, the warning chip disappears
- **AND** the "开始计时" button becomes enabled (if a duration is selected)
