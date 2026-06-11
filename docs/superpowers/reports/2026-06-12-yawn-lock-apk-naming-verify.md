# Verification Report — yawn-lock-apk-naming

**Date**: 2026-06-12
**Change**: `yawn-lock-apk-naming`
**Workflow**: tweak
**Verify mode**: light (overridden from auto-detected `full` — see Scale note below)
**Branch**: `feature/yawn-lock-apk-naming` → merged into `main` via `--no-ff` (merge commit `bc9f97c`)
**Commits on branch**:
- `066e739 chore: override verify_mode to light for tweak scope (2 source files only)`
- `008f3d2 tweak(build): name APKs to RELEASE.md convention (yawn-lock-{ver}-{type})`

## Scope Recap

2 source files changed, 0 capability changes, no delta spec:

| File | Change |
|------|--------|
| `app/build.gradle.kts` | +31 lines: add `renameApksToReleaseConvention` doLast task + `afterEvaluate { assemble*.finalizedBy(...) }` hook |
| `RELEASE.md` | rewrite the "当前产物" + "构建 release APK" sections to drop the manual `cp` step, point to the now-correctly-named outputs |

(The 5 comet change-dir files in `openspec/changes/yawn-lock-apk-naming/` are comet bookkeeping, not source scope — see Scale note.)

## 5-Point Light Verification

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | `tasks.md` all tasks `[x]` | PASS | 8 `[x]`, 0 `[ ]` |
| 2 | Changed source files match `tasks.md` description | PASS | 2 source files exactly as listed in tasks §1–§2 |
| 3 | Build passes | PASS | `./gradlew clean :app:assembleDebug :app:assembleRelease` → BUILD SUCCESSFUL; rename task logs `renamed app-debug.apk → yawn-lock-1.0.0-debug.apk` and `renamed app-release.apk → yawn-lock-1.0.0-release.apk` |
| 4 | APK outputs are correctly named | PASS | `app/build/outputs/apk/debug/yawn-lock-1.0.0-debug.apk` (15.2 MiB) and `app/build/outputs/apk/release/yawn-lock-1.0.0-release.apk` (9.9 MiB) exist; old `app-debug.apk` / `app-release.apk` do not |
| 5 | No security regression (no hardcoded secrets in changed code) | PASS | grep on `app/build.gradle.kts` returns no passwords/secrets/tokens; rename task only renames files |

## Why the public API path didn't work (one-time decision note)

Initial attempt used `androidComponents { onVariants { variant.outputs[].outputFileName.set(...) } }` per AGP docs. This fails to compile in AGP 8.5: `com.android.build.api.variant.VariantOutput` (the public interface returned by `ApplicationVariant.outputs`) does **not** expose `outputFileName` — that property only exists on the internal `BaseVariantOutputImpl`. Verified by `javap -public` on `gradle-api-8.5.0.jar`:

```
public interface com.android.build.api.variant.VariantOutput {
  Property<Integer>  getVersionCode();
  Property<String>   getVersionName();
  Property<Boolean>  getEnabled();
  Property<Boolean>  getEnable();
}
```

The proper public-API path requires the artifacts API + a transform task, which is overkill for renaming. The doLast rename is the pragmatic alternative and is documented in the commit message. This is a project-level footnote, not a defect to fix.

## Scale Note

`comet-state scale` auto-bumped `verify_mode` to `full` because the file count (7) exceeded the 4-file light threshold. Override to `light` was applied because:

- Real source diff: 2 files (`app/build.gradle.kts`, `RELEASE.md`)
- 5 of the 7 files are comet's own change-dir files (`proposal.md`, `design.md`, `tasks.md`, `.comet.yaml`, `.openspec.yaml`) — bookkeeping, not code
- 0 delta specs, 0 new capabilities, no cross-module coordination

None of the `tweak → full` upgrade conditions are met for the actual code change.

## Acceptance

- 5/5 verification checks PASS
- No CRITICAL issues
- One informational note: rename is a doLast post-process rather than a true `outputFileName` configuration (AGP 8.5 public API limitation; not a defect)

**Result**: pass — ready for archive.
