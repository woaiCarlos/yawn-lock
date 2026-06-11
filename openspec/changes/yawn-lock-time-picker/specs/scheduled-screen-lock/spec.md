# Delta Spec: yawn-lock-time-picker

## MODIFIED Requirements

### Requirement: Select Lock Duration (preset list 4 → 6)

**变更**: 预设从 4 个 (10/30/1h/2h) 扩展为 6 个 (5/10/20/30/1h/2h 分钟小时),布局从 1 行 × 4 列改为 2 行 × 3 列,文字字号/样式不变。

#### Scenario: Preset chip count and values
- **WHEN** the timer screen renders
- **THEN** it MUST show 6 preset chips in a 2-row × 3-column grid
- **AND** the presets SHALL be: 5 分钟, 10 分钟, 20 分钟, 30 分钟, 1 小时, 2 小时 (in this order)
- **AND** each row MUST have 3 chips with equal width

---

## ADDED Requirements

### Requirement: Custom Time Picker

The Timer screen SHALL provide a clock-style time picker with three independent wheel columns for hour, minute, and second.

#### Scenario: Three-column wheel picker

- **WHEN** the user navigates to the custom time section
- **THEN** the UI MUST display three vertical wheel columns side by side, separated by ":"
- **AND** the first column ranges over hours (0..23), the second over minutes (0..59), the third over seconds (0..59)
- **AND** the currently selected value in each column MUST be visually highlighted (larger font + bold)
- **AND** a pill-shaped highlight bar MUST appear at the center selected row
- **AND** white-to-transparent gradient masks MUST fade the top and bottom items out
- **AND** the selected value's visual center MUST be precisely aligned with the ":" separator (using lineHeight=fontSize + a -2dp offset to compensate for font baseline)

#### Scenario: Scroll-to-select behavior

- **WHEN** the user scrolls any column
- **THEN** the column MUST snap to integer values when scrolling stops
- **AND** the snap MUST trigger a state update with the newly selected value

#### Scenario: Composed duration clamp

- **WHEN** any column's value changes
- **THEN** the system MUST recompute total seconds as `hours * 3600 + minutes * 60 + seconds`
- **AND** the resulting duration MUST be clamped to the range [5, 7200] seconds
- **AND** the wheel positions MUST be coerced back to the clamped duration's column values

#### Scenario: Programmatic scroll must not pollute state

- **WHEN** an external `selected` value change triggers a programmatic `animateScrollToItem` on a wheel
- **THEN** the scroll-to-state listener MUST be suppressed during the animation
- **AND** intermediate scroll positions (e.g. values passed through during the animation) MUST NOT call `onSelectedChange`
- **AND** after the animation completes, the wheel MUST settle exactly at the target index, with state unchanged
- This prevents the bug where clicking a preset chip would land on a passed-through value (e.g. clicking "10 分钟" while at "14 分钟" previously ended up at "14 分钟" because the intermediate `14` propagated to state, cancelled the animation, and left the wheel stuck).
