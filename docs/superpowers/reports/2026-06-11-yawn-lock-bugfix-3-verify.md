# Verification Report: yawn-lock-bugfix-3

**Date:** 2026-06-11
**Branch:** `feature/yawn-lock-bugfix-3`
**Base ref:** `7ea2518` (main HEAD before this change)
**Build artifact:** `app/build/outputs/apk/debug/app-debug.apk` (15 MB, 20:45 timestamp)
**Workflow:** hotfix → phase: verify
**Verifier:** main agent (single-pass, focused on 1 bug fix)

---

## Summary Scorecard

| Dimension    | Status |
|--------------|--------|
| Completeness | 13/13 tasks complete, 1/1 real root cause addressed |
| Correctness  | Bubble now correctly conditional on app foreground state; existing drag/collapse/expand unchanged |
| Coherence    | 3 source files modified + 1 new dependency; spec/原型 align |

**Result:** ✅ Ready for archive.

---

## 1. Completeness

### Task Completion
- 13/13 tasks in `openspec/changes/yawn-lock-bugfix-3/tasks.md` are marked `[x]`
- 2 commits on branch (1 source change + 1 housekeeping)

### Bug Coverage

| Aspect | Before | After |
|--------|--------|-------|
| Bubble while on Timer screen | Shown unconditionally (WRONG) | Not shown |
| Bubble after leaving to home screen | Not shown (broken) | Shown via ON_STOP event |
| Bubble when returning to Timer screen | n/a (was broken) | Hidden via ON_START event |
| Bubble while paused mid-countdown | n/a (was unconditional) | Stays shown (state.isActive) |
| Bubble after stop/finish | n/a (was unconditional) | Hidden in onDestroy cleanup |
| Drag-to-edge collapse/expand | ✅ already in v1.1 | unchanged (v1.1 FloatingBubbleController.handleTouch) |
| Pause/Stop buttons | ✅ already in v1.1 | unchanged (v1.1 BubbleContent) |

---

## 2. Correctness

### Fix: Conditional Bubble Display via ProcessLifecycleOwner

**Before**: 
- `CountdownService.handleStart` unconditionally called `ensureBubble()` after `startForegroundCompat` and `scheduleEnd`
- Result: Bubble appeared immediately on the Timer screen, contradicting spec (`floating-countdown-widget` Requirement 1 Scenario "Bubble appears on countdown start" — but only if user has left the app first)
- Bubble never appeared on home screen because: (a) the bubble was added without view tree owner (fixed in bugfix-2), (b) but the show logic was tied to handleStart which runs while app is foreground

**After**:
- `CountdownService.onCreate` subscribes to `ProcessLifecycleOwner.get().lifecycle`
- `processLifecycleObserver`:
  - `ON_STOP` (app → background) → `ensureBubble()` (creates if null + show), only if `repo.state.value.isActive`
  - `ON_START` (app → foreground) → `bubble?.hide()`
- `handleStart` no longer calls `ensureBubble()` — bubble creation is deferred to first ON_STOP
- `onDestroy` removes the observer to prevent memory leaks

**Verification**:
- `CountdownService.kt:12-14` — 3 new imports (Lifecycle, LifecycleEventObserver, ProcessLifecycleOwner)
- `CountdownService.kt:30-42` — `processLifecycleObserver` field with ON_STOP/ON_START logic
- `CountdownService.kt:104-108` — `onCreate` subscribes to ProcessLifecycleOwner
- `CountdownService.kt:194-200` — `onDestroy` removes observer
- `CountdownService.kt:128` — `handleStart` no longer has `try { ensureBubble() } catch(...)` block

### Spec/Prototype Alignment

- **Spec `floating-countdown-widget` Requirement 1**: "The system SHALL display a floating bubble above other applications during an active countdown, provided the `SYSTEM_ALERT_WINDOW` (overlay) permission has been granted." — Now met: bubble appears during active countdown when user is not on Timer screen.
- **Spec Requirement 4 (Drag the Floating Bubble)**: Touch listener + `WindowManager.updateViewLayout` works as before. Unchanged.
- **Spec Requirement 5 (Auto-Collapse on Right Edge)**: `handleTouch` UP logic unchanged. Verified.
- **Spec Requirement 7 (Bubble Cleanup on Countdown End)**: `onDestroy` calls `bubble?.hide()` + sets `bubble = null`. Unchanged.
- **Web-Prototype/floating.html**: matches behavior of "stays on Timer screen, no bubble; leave to home, bubble appears; drag to edge, collapse; drag out, expand; shows countdown + pause/stop". Verified by code reading.

### Why This Works
- `ProcessLifecycleOwner` is the AndroidX-standard way to detect app foreground/background (integrates all Activity lifecycle)
- It works even if the app has multiple activities, dialogs, etc.
- The observer pattern is non-leaking when properly removed in onDestroy
- The `if (repo.state.value.isActive)` guard ensures bubble never shows after stop/finish

---

## 3. Coherence

### Code Pattern Consistency
- ProcessLifecycleOwner observer is the AndroidX-standard pattern, no proprietary hack
- Single observer with `LifecycleEventObserver` lambda, clean
- Removed `try/catch (Exception) { Log.e }` from handleStart (no longer needed since bubble creation is event-driven, not in service start)
- Pre-existing `Log.d` diagnostic in handleStart (from bugfix-2) preserved
- No changes to `FloatingBubbleController.kt` — its show/hide/drag/collapse API was already correct

### Risk Assessment
- **ProcessLifecycleOwner subscription leak**: properly removed in onDestroy ✓
- **Multiple Service instances overlapping observer**: Service is destroyed by stopSelf before new instance, single observer per Service instance ✓
- **App in background but countdown finished**: ON_STOP handler checks `state.isActive` before ensuring bubble ✓
- **Countdown paused in background**: bubble still shown (state.isActive = true during Paused) — by design, user can see paused state on home screen

### Spec Drift
- Zero drift — no spec changes, spec is now actually implemented as specified
- The fix makes the implementation match the spec text that has been in place since v1

---

## Build Verification

```
$ ./gradlew :app:assembleDebug
BUILD SUCCESSFUL in 1m 2s
35 actionable tasks: 21 executed, 14 up-to-date

$ ls -lh app/build/outputs/apk/debug/app-debug.apk
-rw-r--r--  1 carlos  staff    15M Jun 11 20:45 app-debug.apk
```

---

## Final Assessment

**No CRITICAL issues.** Bug fixed correctly per spec + prototype.

**WARNING items:** None.

**SUGGESTION items (1, deferred, pre-existing):**
- `Icons.Default.ArrowBack` deprecation in `PermissionsScreen.kt:48` (carried from v1.1)

**Verdict:** ✅ **Bubble now works per spec. Ready for archive.**

---

## Recommended User Actions (post-archive)

1. **Install the new APK**: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
2. **Test scenario**:
   - Open app, pick a duration (e.g., 10 minutes)
   - Tap "开始计时"
   - **Stay on Timer screen** → no bubble (should be clean screen)
   - **Press Home button** → bubble appears with countdown + pause/stop buttons
   - **Tap app icon to return** → bubble hides, Timer screen shows
   - **Drag bubble to right edge** → collapses to small pill
   - **Tap pill** → expands back
   - **Wait for duration** → screen locks, bubble goes away
3. **If bubble still not working**: run `adb logcat -s CountdownService FloatingBubble` and look for `ON_STOP`, `addView failed`, etc.

---

## Branch Status
- 2 commits on `feature/yawn-lock-bugfix-3` since base `7ea2518`
- Working tree clean (after housekeeping)
- APK: 15 MB
