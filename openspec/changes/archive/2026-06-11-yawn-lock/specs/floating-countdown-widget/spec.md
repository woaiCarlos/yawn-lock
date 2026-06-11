# Spec: floating-countdown-widget (打哈欠悬浮倒计时气泡)

## ADDED Requirements

### Requirement: Display Floating Bubble During Countdown

The system SHALL display a floating bubble above other applications during an active countdown, provided the `SYSTEM_ALERT_WINDOW` (overlay) permission has been granted.

The bubble SHALL appear automatically when the countdown starts and SHALL be removed when the countdown ends, is stopped, or the screen is locked.

The bubble SHALL contain:
- A drag handle bar at the top (4dp tall, 36dp wide, semi-transparent)
- An app icon (moon) and "Sleepy Lock" label
- A circular progress ring indicating countdown progress
- The remaining time in `MM:SS` format (monospace digits)
- A "暂停 / 继续" (Pause / Resume) button
- A "停止" (Stop) button

#### Scenario: Bubble appears on countdown start
- **WHEN** a countdown starts and overlay permission is granted
- **THEN** a floating bubble is added via `WindowManager.addView()` with `LayoutParams.TYPE_APPLICATION_OVERLAY`
- **AND** the bubble is positioned in the upper-right region (top 35%, right 40dp)

#### Scenario: Bubble disappears on lock
- **WHEN** the device screen is locked by the app
- **THEN** the floating bubble is removed from the window manager within 200ms
- **AND** the bubble SHALL NOT be visible on the lock screen

### Requirement: Permission Gating for Floating Bubble

The system SHALL request the `SYSTEM_ALERT_WINDOW` (overlay) permission before allowing the user to start a countdown.

The permissions screen SHALL list the overlay permission with a state badge ("已授权" / "未授权") and a description "在其他 App 上方显示倒计时与控制气泡".

Tapping the overlay row SHALL open the system settings page for the app, where the user can toggle "显示在其他应用的上层".

#### Scenario: User grants overlay permission
- **WHEN** the user taps the overlay row, opens system settings, and enables the toggle
- **AND** the user returns to the app
- **THEN** the row state badge updates to "已授权"
- **AND** the user can return to the timer screen and start a countdown (the bubble will appear)

#### Scenario: User attempts to start countdown without overlay permission
- **WHEN** the user taps "开始锁屏" with overlay permission not granted
- **THEN** the system navigates to the permissions screen
- **AND** SHALL NOT start a countdown

### Requirement: Drag the Floating Bubble

The system SHALL allow the user to drag the floating bubble anywhere within the screen bounds, via a pointer-down and pointer-move gesture.

During a drag:
- The bubble's position SHALL update on each `pointermove` event
- The bubble SHALL NOT move if the total pointer displacement is < 3px (treated as a click, not a drag)
- The bubble's right edge SHALL be clamped to `[0, screenWidth - bubbleWidth]`
- The bubble's top edge SHALL be clamped to `[0, screenHeight - bubbleHeight]`

#### Scenario: User drags bubble from right to center
- **WHEN** the user pointer-downs on the bubble at right=40, top=35%-of-screen
- **AND** the user drags left by 200px and down by 100px
- **THEN** the bubble's right position becomes `screenWidth - bubbleWidth - 200px` (clamped if needed)
- **AND** the bubble's top position becomes `35%-of-screen + 100px` (clamped if needed)

### Requirement: Auto-Collapse on Right Edge

The system SHALL auto-collapse the floating bubble into a small pill (36dp wide, showing only a moon icon) when the user releases the bubble within 36dp of the right screen edge.

When collapsed:
- The handle, time, ring, and control buttons SHALL be hidden
- A moon icon SHALL be visible
- The bubble width SHALL be 36dp

Tapping the collapsed pill (without dragging) SHALL expand the bubble back to its full size.

#### Scenario: User releases bubble near right edge
- **WHEN** the user releases the bubble at right < 36px from the screen right edge
- **THEN** the bubble collapses to the pill state
- **AND** its right position is snapped to 6px from the screen right edge

#### Scenario: User taps collapsed pill
- **WHEN** the bubble is collapsed and the user taps it
- **THEN** the bubble expands to its full size
- **AND** the bubble's right position is set to 40px from the screen right edge

### Requirement: Pause and Stop from Floating Bubble

The system SHALL allow the user to pause/resume and stop the countdown directly from the floating bubble, without opening the app.

The bubble's pause/resume button SHALL behave identically to the timer screen's pause/resume button.
The bubble's stop button SHALL behave identically to the timer screen's stop button.

#### Scenario: User pauses from floating bubble
- **WHEN** the bubble is visible and the user taps "暂停"
- **THEN** the countdown pauses
- **AND** the bubble's button label changes to "继续"

### Requirement: Survive App Backgrounding

The floating bubble SHALL remain visible and interactive when the user navigates away from the app (e.g., opens another app or returns to the home screen).

The bubble SHALL be drawn on top of the system UI (status bar, navigation bar) but SHALL NOT cover system dialogs (e.g., permission prompts).

#### Scenario: User opens another app while countdown is running
- **WHEN** the countdown is active and the user presses the home button
- **THEN** the floating bubble remains visible on top of the home screen and other apps
- **AND** the bubble's controls remain tappable

### Requirement: Bubble Cleanup on Countdown End

The system SHALL remove the floating bubble when the countdown ends (alarm fires) or is stopped, and SHALL ensure no orphan bubble views remain in the WindowManager.

#### Scenario: Bubble removed on countdown completion
- **WHEN** the alarm fires and the screen is locked
- **THEN** the bubble is removed via `WindowManager.removeView()` within 200ms
- **AND** no leak warning is logged

#### Scenario: Bubble removed when user stops countdown
- **WHEN** the user taps "停止" in the bubble
- **THEN** the bubble is removed within 200ms
- **AND** the user can no longer see the bubble
