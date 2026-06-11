# Spec: scheduled-screen-lock (打哈欠定时锁屏)

## ADDED Requirements

### Requirement: Select Lock Duration

The system SHALL allow the user to select a lock duration on the main timer screen.

The duration SHALL be selectable from four quick presets: 30 seconds, 1 minute, 5 minutes, 10 minutes.

The duration SHALL additionally be adjustable via a custom control with:
- Plus/minus buttons (each step = 1 minute when duration < 10 minutes, 5 minutes otherwise)
- A range slider from 1 to 120 minutes
- Display of the current value with unit (seconds for < 1 min, minutes for ≥ 1 min)

#### Scenario: User selects a quick preset
- **WHEN** the user taps the "5 minutes" preset chip
- **THEN** the timer state updates to 5 minutes
- **AND** the "5 minutes" chip becomes visually active
- **AND** the custom dial displays "5 分钟"

#### Scenario: User adjusts duration with plus button
- **WHEN** the current duration is 5 minutes and the user taps the plus button
- **THEN** the duration increases to 6 minutes
- **WHEN** the current duration is 12 minutes and the user taps the plus button
- **THEN** the duration increases to 17 minutes

#### Scenario: User drags the slider
- **WHEN** the user drags the slider to position 30
- **THEN** the duration updates to 30 minutes
- **AND** the dial displays "30 分钟"

### Requirement: Start Countdown

The system SHALL start a countdown when the user taps the "开始锁屏" (Start Lock) button, provided that the device-admin permission has been granted.

The countdown SHALL:
- Run as a foreground service with a persistent notification showing remaining time
- Schedule an exact alarm via `AlarmManager.setExactAndAllowWhileIdle` at the lock deadline
- Display a full-screen status card on the timer screen with circular progress ring and remaining time
- The "开始锁屏" button SHALL be hidden while countdown is active

#### Scenario: User starts a 5-minute countdown
- **WHEN** the user has granted device-admin permission and taps "开始锁屏" with a 5-minute duration selected
- **THEN** the countdown begins
- **AND** a foreground service starts and shows a notification
- **AND** an exact alarm is scheduled for 5 minutes later
- **AND** the timer screen displays the status card with a 5:00 ring at 0% progress

#### Scenario: User attempts to start without device-admin permission
- **WHEN** the user has not granted device-admin permission and taps "开始锁屏"
- **THEN** the system SHALL navigate to the permissions screen
- **AND** SHALL NOT start a countdown

### Requirement: Pause and Resume Countdown

The system SHALL allow the user to pause and resume an active countdown from the timer screen status card and the floating bubble.

When paused, the alarm SHALL be cancelled and the countdown SHALL freeze; when resumed, a new alarm SHALL be scheduled for the remaining time.

#### Scenario: User pauses at 02:30 remaining
- **WHEN** the countdown is at 02:30 and the user taps "暂停"
- **THEN** the button label changes to "继续"
- **AND** the remaining time freezes at 02:30
- **AND** the scheduled alarm is cancelled

#### Scenario: User resumes the paused countdown
- **WHEN** the countdown is paused at 02:30 and the user taps "继续"
- **THEN** the button label changes to "暂停"
- **AND** a new exact alarm is scheduled 2 minutes 30 seconds in the future

### Requirement: Stop Countdown

The system SHALL allow the user to stop a countdown entirely via a "停止" (Stop) button on the timer screen and the floating bubble.

When stopped, the alarm SHALL be cancelled, the foreground service SHALL stop, and the timer screen SHALL return to the initial state with the "开始锁屏" button visible.

#### Scenario: User stops an active countdown
- **WHEN** the countdown is active and the user taps "停止"
- **THEN** the alarm is cancelled
- **AND** the foreground service stops
- **AND** the status card is hidden
- **AND** the "开始锁屏" button becomes visible again

### Requirement: Lock Screen on Deadline

The system SHALL lock the device screen when the scheduled alarm fires, by calling `DevicePolicyManager.lockNow()` on the registered device-admin component.

If the device-admin permission has been revoked by the time the alarm fires, the system SHALL fall back to displaying a full-screen "lock" overlay on the device, instructing the user to press the power button.

#### Scenario: Alarm fires while device-admin is active
- **WHEN** the scheduled alarm time elapses and the device-admin permission is still granted
- **THEN** `DevicePolicyManager.lockNow()` is invoked
- **AND** the device screen locks immediately
- **AND** the foreground service stops
- **AND** the floating bubble is dismissed

#### Scenario: Alarm fires while device-admin has been revoked
- **WHEN** the scheduled alarm time elapses and the device-admin permission has been revoked
- **THEN** the system shows a full-screen overlay stating "需要重新授权设备管理员,按电源键暂时锁屏"
- **AND** the foreground service continues running until the user acknowledges

### Requirement: Device-Admin Permission

The system SHALL request the `BIND_DEVICE_ADMIN` permission before allowing the user to start a countdown.

The permissions screen SHALL list the device-admin permission with a "已授权" (Granted) or "未授权" (Not Granted) state badge.

Tapping the device-admin row SHALL launch the system "Activate device admin" dialog. After returning, the permissions screen SHALL refresh the state badge.

#### Scenario: User grants device-admin from permissions screen
- **WHEN** the user taps the device-admin row and confirms the system dialog
- **THEN** the row state badge updates to "已授权"
- **AND** the user can return to the timer screen and start a countdown

#### Scenario: User denies or cancels the device-admin dialog
- **WHEN** the user taps the device-admin row and cancels the system dialog
- **THEN** the row state badge remains "未授权"
- **AND** the timer screen "开始锁屏" button remains disabled (or routes to permissions when tapped)

### Requirement: Permission Gating on Timer Screen

The system SHALL visually indicate on the timer screen when one or more required permissions are missing, before the user attempts to start a countdown.

The "开始锁屏" button MAY remain tappable but SHALL route the user to the permissions screen if any required permission is missing.

#### Scenario: Timer screen with no permissions granted
- **WHEN** the user opens the timer screen with no permissions granted
- **THEN** a small warning chip SHALL be visible above the "开始锁屏" button indicating "需要先授予权限"
- **AND** tapping "开始锁屏" navigates to the permissions screen

### Requirement: Notification for Active Countdown

The system SHALL display an ongoing foreground-service notification while a countdown is active, showing:
- App name and moon icon
- Remaining time in `MM:SS` format, updated at least once per second
- A "Stop" action button

#### Scenario: Notification updates each second
- **WHEN** the countdown is at 03:45
- **THEN** the notification displays "03:45"
- **WHEN** one second elapses
- **THEN** the notification updates to "03:44"
