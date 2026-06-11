# ABANDONED — superseded by main work

**Status**: archived as abandoned (superseded-by-main-spec)
**Archived on**: 2026-06-12
**Original change**: `yawn-lock-time-picker` (full workflow, build phase)

## Why this change was abandoned

When this change was opened (2026-06-11), it had a 5-task plan to introduce a `WheelColumn` component and wire a clock-style wheel time picker into the timer screen. By the time of archiving, 0/11 tasks in `tasks.md` were completed, no `feature/yawn-lock-time-picker` branch was ever created, and no commits relating to the planned work landed via this change's intended path.

However, the **planned work itself is functionally present in `main`** — implemented piecemeal by 15+ subsequent commits between 51c1d2d and bf43216 (covering `style(wheel)`, `fix(wheel)`, `polish(wheel)`, `perf(wheel)`, and the actual `feat(time): 5min/20min presets + wheel sync`):

- `app/src/main/kotlin/com/example/yawnlock/ui/timer/WheelColumn.kt` exists and is the component this change set out to design
- `CustomDial` exists and is the secondary component
- The timer screen consumes both
- The user-reported issues listed in this change's `proposal.md` (sloppy slider, no visual feedback) have all been addressed in those follow-up commits

Net: the work this change planned has been delivered; the change itself is just a stale shell.

## Archive rationale

- `verify_result: pass` (with this note) — the work the change was meant to deliver is in `main` and visible; nothing to verify on this change's branch
- `branch_status: handled` — there is no branch to handle
- `archived: true` — moving out of `openspec/changes/` to clear it from the active list
- `.comet.yaml` shows `phase: archive` at the time of archive

The change's `proposal.md` / `design.md` / `tasks.md` are preserved verbatim in the archive directory for historical reference — they document the original design intent, which is interesting context for anyone reading the eventual wheel implementation.

## Known issue: delta spec sync was destructive, reverted

`comet-archive.sh sync_delta_specs` step replaces `openspec/specs/<capability>/spec.md` with the change's delta spec — it does **not** merge delta into main. The main `scheduled-screen-lock` spec had 97 lines of accumulated content from `yawn-lock`, `yawn-lock-polish`, and prior bugfix delta syncs; this change's delta had 52 lines covering only `Custom Time Picker` + a `Select Lock Duration` modification. Running the archive overwrote main with the delta, losing the prior accumulated content.

Reverted the main spec back via `git checkout HEAD -- openspec/specs/scheduled-screen-lock/spec.md` after the archive. The archive itself (the move into `archive/`) is still valid; only the side-effect of the sync step was undone.

**Implication for future abandoned-change archives**: if the change has a delta spec, the sync step will destroy prior delta-sync content in main. Either (a) archive without a delta spec by deleting `specs/` from the change dir first, or (b) be ready to revert main after the archive. This is a process gap in the archive script, not something this change was responsible for.
