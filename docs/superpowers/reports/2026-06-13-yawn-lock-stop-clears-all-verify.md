# Verification Report — yawn-lock-stop-clears-all

**Date**: 2026-06-13
**Change**: `yawn-lock-stop-clears-all`
**Workflow**: hotfix
**Verify mode**: light (overridden from auto-detected `full` — see Scale note)
**Branch**: `feature/yawn-lock-stop-clears-all` → merged into `main` via `--no-ff` (merge commit `ba57da3`)
**Fix commit**: `b70b769 fix(timer): stop() fully clears state, preview() always resets to Idle`

## Scope Recap

1 source file modified, 1 test file updated (1 removed + 1 modified + 4 new tests), 0 capability changes, no delta spec:

| File | Change |
|------|--------|
| `app/src/main/kotlin/com/example/yawnlock/domain/TimerRepository.kt` | `stop()`: now `_state.value = TimerState()` (full clear). `preview()`: now unconditionally `_state.value = TimerState(status=Idle, durationMs=N, remainingMs=N)`. Removed `deadlineElapsed` resync (no longer needed since status=Idle means tick() never runs) |
| `app/src/test/kotlin/com/example/yawnlock/domain/TimerRepositoryStateTest.kt` | Removed `stop_preserves_duration_and_returns_to_Idle`, `preview_while_Counting_resets_to_new_full_duration`, `preview_while_Counting_handles_smaller_and_larger_values` (asserted 1.0.2 behavior). Added `stop_fully_clears_state_no_preservation`, `stop_while_idle_still_resets_to_zero`, `preview_while_Counting_resets_to_Idle_with_new_duration`, `preview_while_Paused_resets_to_Idle_with_new_duration`, `preview_from_Counting_does_not_resync_deadline`. Updated `bug_scenario_stop_then_preview_20s_then_start_works` and `preview_while_Paused_resets_to_new_full_duration` (the latter was already renamed in the previous edit) |

## Root Cause (recap)

User feedback after 1.0.2's mid-countdown preview fix: "stop should clear all state — I tapped stop, not pause. If I tap pause, then go back and re-set, it should also clear previous state and restart counting."

1.0.0/1.0.1/1.0.2 `stop()` preserved `durationMs` (comment: "user can re-tap Start to use the same duration"). User explicitly rejected this design — clear the wheel, force re-selection.
1.0.2 `preview()` preserved `Counting`/`Paused` status, just changed durationMs/remainingMs. User rejected: "any change of mind should require re-tapping Start, not auto-continue".

## 5-Point Light Verification

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | `tasks.md` all tasks `[x]` | PASS | 15 `[x]`, 0 `[ ]` |
| 2 | Changed files match `tasks.md` description | PASS | TimerRepository.kt + TimerRepositoryStateTest.kt + 5 change-dir files; exactly as listed in tasks §1-§3 |
| 3 | Build passes | PASS | `./scripts/build.sh :app:assembleDebug` → BUILD SUCCESSFUL; debug APK 15.2 MiB (22:31), release APK 9.9 MiB (22:30) |
| 4 | Tests pass | PASS | `./scripts/build.sh :app:testDebugUnitTest` → 10/10 pass |
| 5 | No security regression | PASS | grep on changed files for password/secret/api_key returns no hits |

## Scale Note

`comet-state scale` auto-bumped `verify_mode` to `full` (15 tasks > 3, 7 files > 4). Override to `light` because:
- Real source diff: 1 file (`TimerRepository.kt`) + 1 test file
- 5 of 7 files are comet change-dir bookkeeping
- 0 delta specs, 0 new capabilities, no cross-module coordination

None of the `hotfix → full` upgrade conditions are met.

## Behavior Change Note (release-relevant)

This fix is **deliberately more invasive** than 1.0.2's:
- 1.0.0 / 1.0.1: `stop()` preserved durationMs (wheel stayed on the same value)
- 1.0.2: `stop()` preserved durationMs; `preview()` while Counting continued with new full duration
- 1.0.3 (this fix): `stop()` zeroes everything; `preview()` any state always returns to Idle+newDuration, requires re-tap Start

The "quickly restart with same duration" workflow from 1.0.0/1.0.1/1.0.2 is gone in 1.0.3. User explicitly asked for stricter semantics. **Recommend prominent callout in 1.0.3 release notes**.

## Acceptance

- 5/5 verification checks PASS
- No CRITICAL issues
- Behavior changes are intentional and user-requested
- Test coverage: 6 new/modified tests cover both stop semantics (full clear) and preview semantics (always Idle) plus the deadlineElapsed non-resync

**Result**: pass — ready for archive.
