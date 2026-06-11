# Spec: scheduled-screen-lock (delta for yawn-lock-bugfix-4)

> 本 delta 修改 `openspec/specs/scheduled-screen-lock/spec.md`,记录 yawn-lock v1.1 → v1.2 之间的行为变更(从用户实测反馈调整)。
> 其他 capability (`floating-countdown-widget`) 行为契约不变,不在此 delta 范围。

## MODIFIED Requirements

### Requirement: Start Countdown

**变更**:**移除** "Display a full-screen status card on the timer screen" 行为 — 用户实测反馈 Timer 屏幕在前台时**不应该显示倒计时 UI**(无倒计时数字、无暂停/停止按钮);倒计时信息**仅**在悬浮窗里展示,前台的 Timer 屏幕只显示选时控件。

The system SHALL start a countdown when the user taps the "**开始计时**" (Start Countdown) button, provided that the device-admin permission and overlay permission have been granted.

The countdown SHALL:
- Run as a foreground service with a persistent notification showing remaining time (with `setOngoing(true)` to prevent user dismissal)
- Schedule an exact alarm via `AlarmManager.setExactAndAllowWhileIdle` at the lock deadline (for durations > 5 min) OR use a `Handler.postDelayed` (for durations ≤ 5 min)
- **NOT display any countdown UI on the timer screen** during the active countdown — countdown information is shown ONLY in the floating bubble when the user has left the app

The timer screen SHALL provide a permissions/settings entry point (e.g., icon in the hero card or a top-right icon button) that navigates to the permissions screen regardless of whether permissions are currently granted.

#### Scenario: User starts a 10-minute countdown
- **WHEN** the user has granted both device-admin and overlay permissions, selected 10 minutes, and taps "开始计时"
- **THEN** the countdown begins
- **AND** a foreground service starts and shows a notification
- **AND** an exact alarm (or Handler callback) is scheduled for 10 minutes later
- **AND** the timer screen continues to show the time-selection controls without displaying the countdown
- **AND** the floating bubble appears on the home screen / other apps (when user leaves the Timer screen)

#### Scenario: User attempts to start without device-admin permission
- **WHEN** the user has not granted device-admin permission and taps "开始计时"
- **THEN** the system SHALL navigate to the permissions screen
- **AND** SHALL NOT start a countdown

#### Scenario: User navigates from timer to permissions via icon
- **WHEN** the user is on the timer screen with all permissions granted and taps the permissions icon
- **THEN** the system SHALL navigate to the permissions screen
- **AND** the permissions list SHALL show the current state of all permissions

#### Scenario: Active countdown, user stays on timer screen
- **WHEN** the countdown is active and the user is on the timer screen
- **THEN** the timer screen SHALL NOT display a countdown status card, progress ring, or pause/stop buttons
- **AND** only the time-selection controls SHALL be visible

#### Scenario: Active countdown, user backgrounds the app
- **WHEN** the countdown is active and the user presses Home (or switches to another app)
- **THEN** the floating bubble SHALL appear on the home screen / other app
- **AND** the bubble SHALL show the remaining countdown time, a pause/resume button, and a stop button
- **AND** the user CAN drag the bubble to the screen edge to collapse it
- **AND** the user CAN drag it back to expand it
- **WHEN** the user returns to the timer screen
- **THEN** the bubble SHALL hide
- **AND** the timer screen SHALL show the time-selection controls (still no countdown UI)
