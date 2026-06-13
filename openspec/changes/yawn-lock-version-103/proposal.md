## Why

1.0.0 release 之后 3 个 bug fix 全部完成且通过测试:
- `yawn-lock-bubble-restart` (commit ad98161): 修第二次 start 气泡不出现的 bug
- `yawn-lock-mid-countdown-preview` (commit 3534e00): 修倒计时中滚轮调时间不生效的 bug(初版)
- `yawn-lock-stop-clears-all` (commit b70b769): 用户实测后改 stop/preview 语义,要求更严格

3 个 fix 都通过了单测(10/10)和真机测试。现在用户实测 1.0.3 行为(最新 fix)通过,需要把版本号从 1.0.0 提到 1.0.3 准备发新版。

`RELEASE.md` 当前示例是 `yawn-lock-1.0.0-release.apk`,跟着 versionName 改才会自洽。

## What Changes

- `app/build.gradle.kts` 的 `versionName`: `"1.0.0"` → `"1.0.3"`
- `app/build.gradle.kts` 的 `versionCode`: `1` → `4`(1.0.0=1,1.0.1=2,1.0.2=3,1.0.3=4)

`scripts/build.sh` 的 rename 任务会读 `versionName` 自动产出 `yawn-lock-1.0.3-{debug,release}.apk`,无需额外改动。

不动 `applicationId`、不动任何代码逻辑、不动 manifest、不动依赖。

## Capabilities

### New Capabilities
无。

### Modified Capabilities
无。本次纯配置值修改。

## Impact

- **代码/资源**:`app/build.gradle.kts`(2 行值修改)
- **APK 产物**:`yawn-lock-1.0.3-debug.apk` / `yawn-lock-1.0.3-release.apk`(自动)
- **API / 架构 / 依赖**:无
- **manifest**:无(applicationId、targetSdk 等不动)
- **Play Store / 上架**:versionCode 必须单调递增,1→4 符合要求。Play Store 会把这个 APK 识别为同一应用的升级
- **已发布用户**:1.0.0 用户升级时 Play Store 会提示更新到 1.0.3
