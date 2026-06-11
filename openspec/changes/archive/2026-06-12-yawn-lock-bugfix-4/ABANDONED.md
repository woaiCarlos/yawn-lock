# ABANDONED — superseded by main work

**Status**: archived as abandoned (superseded-by-main-spec)
**Archived on**: 2026-06-12
**Original change**: `yawn-lock-bugfix-4` (full workflow, build phase)

## Why this change was abandoned

When this change was opened (2026-06-11), it had a 3-fix plan based on real user feedback after v1.1:

1. Remove `StatusCard` from Timer screen (user wanted zero foreground UI — countdown visible only in floating bubble)
2. Convert `FloatingBubble` from ComposeView to XML/FrameLayout (WindowManager+Compose had stability issues)
3. Prevent app being killed in background (ProcessLifecycleOwner observer, OEM battery dialog, etc.)

By the time of archiving, 0/24 tasks in `tasks.md` were completed, **no `feature/yawn-lock-bugfix-4` branch was ever created**, the `.comet.yaml` is even malformed (`build_mode: null`, `isolation: null` — doesn't satisfy the state machine), and no commits landed via this change's intended path.

However, the **planned work itself is functionally present in `main`**, in a single commit:

- `a277626 fix(timer): complete bugfix-4 batch — status card, bubble XML, OEM, idempotent pause`
  (note: `Co-authored-by: Comet <noreply>` in the trailer, suggesting this was produced by the comet workflow but via a different change path)

That commit covers all three planned fixes:

- `StatusCard` Composable removed from `TimerScreen.kt`
- `FloatingBubble` switched to XML/FrameLayout (`bubble_bg.xml`, `bubble_btn_accent.xml`, `bubble_btn_dim.xml`, `bubble_moon_bg.xml` are all the new XML-based assets; the Kotlin file `FloatingBubbleController.kt` no longer uses Compose)
- `ProcessLifecycleOwner` observer moved to `YawnApplication`, plus OEM battery-optimization dialog for first launch, plus idempotent pause/resume and other hardening

The delta spec at `specs/scheduled-screen-lock/spec.md` in this change directory was the **intended** post-implementation spec — it describes the **target** state for `Start Countdown` (no full-screen status card) and other changes. Since the implementation was delivered (a277626), the spec is correct; the change was just a stale shell.

## Archive rationale

- `verify_result: pass` (with this note) — the work the change was meant to deliver is in `main` and visible; nothing to verify on this change's branch
- `branch_status: handled` — there is no branch to handle
- `archived: true` — moving out of `openspec/changes/` to clear it from the active list
- `.comet.yaml` shows `phase: archive` at the time of archive

## Note on .comet.yaml state at archive

The `.comet.yaml` was in a malformed state at the time of archive:

```yaml
build_mode: null
isolation: null
verify_mode: null
```

These three nulls violate the state machine constraints for `phase: build` (the state we transitioned from). The fix is moot since the change is being archived, not advanced — but the original session that left it in this state should be aware that the work was never progressed through proper comet tracking.

## Known issue: delta spec sync was destructive, reverted

Same as `yawn-lock-time-picker` archive: `comet-archive.sh sync_delta_specs` replaces the main spec with the change's delta spec (it does **not** merge delta into main). The main `scheduled-screen-lock/spec.md` had accumulated content from `yawn-lock`, `yawn-lock-polish`, `yawn-lock-bugfix-1/2/3`, and `yawn-lock-time-picker`; this change's delta was a fresh rewrite of the `Start Countdown` requirement. Running the archive overwrote main with the partial delta, losing prior content.

**Mitigation taken**: reverted the main spec via `git checkout HEAD -- openspec/specs/scheduled-screen-lock/spec.md` after the archive script reported `[OK] Delta spec synced`. The archive itself (the move into `archive/`) is valid; only the side-effect of the sync step was undone.

**Process gap**: the archive script needs a `--skip-delta-sync` option (or a delta-aware merge instead of `cp`) so that archiving an abandoned change doesn't destroy prior delta-sync content in main. Documented in the time-picker ABANDONED.md too; should be raised as a separate fix to the comet tooling.
