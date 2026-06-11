# Delta Spec: yawn-lock-time-picker

## ADDED Requirements

### Requirement: Custom Time Picker

The Timer screen SHALL provide a clock-style time picker with three independent wheel columns for hour, minute, and second.

#### Scenario: Three-column wheel picker

- **WHEN** the user navigates to the custom time section
- **THEN** the UI MUST display three vertical wheel columns side by side, separated by ":"
- **AND** the first column ranges over hours (0..2), the second over minutes (0..59), the third over seconds (0..59)
- **AND** the currently selected value in each column MUST be visually highlighted (larger font + bold)

#### Scenario: Scroll-to-select behavior

- **WHEN** the user scrolls any column
- **THEN** the column MUST snap to integer values when scrolling stops
- **AND** the snap MUST trigger a state update with the newly selected value

#### Scenario: Composed duration clamp

- **WHEN** any column's value changes
- **THEN** the system MUST recompute total seconds as `hours * 3600 + minutes * 60 + seconds`
- **AND** the resulting duration MUST be clamped to the range [5, 7200] seconds
- **AND** the wheel positions MUST be coerced back to the clamped duration's column values
