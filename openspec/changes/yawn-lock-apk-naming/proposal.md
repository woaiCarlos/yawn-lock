## Why

`RELEASE.md` 写得很清楚：APK 产物应该叫 `yawn-lock-1.0.0-release.apk` / `yawn-lock-1.0.0-debug.apk`（`{package}-{versionName}-{buildType}.apk`），且这些文件应当「位于项目根」。但 `app/build.gradle.kts` 完全没有设置 `outputFileName`，Gradle 直接给出默认的 `app-release.apk` / `app-debug.apk`，产物落在 `app/build/outputs/apk/{buildType}/`。SOP 不得不补一句 `cp app/build/outputs/apk/release/app-release.apk ./yawn-lock-1.0.0-release.apk` 让人手动改名——这一步是纯人工的、容易忘、容易打错版本号，是发布流程里的一个坑。

## What Changes

- 在 `app/build.gradle.kts` 里通过 `androidComponents.onVariants { ... }` 给每个 variant 设 `output.outputFileName = "yawn-lock-${versionName}-${buildType}.apk"`，让 Gradle 一次就吐出正确名字的 APK
- 删除 `RELEASE.md` 里「cp」那一行，并相应调整示例命令与上传清单，让 SOP 反映「直接构建就能拿到正确文件」的现实
- 后续 `versionName` 改了，APK 名自动跟着变（模板里直接用 `android.defaultConfig.versionName`）

不动 build 路径（仍为 `app/build/outputs/apk/{buildType}/`，只改 basename）；不动 `versionCode` / 签名 / `applicationId` / 任何运行时行为。

## Capabilities

### New Capabilities
无。

### Modified Capabilities
无。本次为构建产物命名配置 + 文档同步，不影响任何运行时 spec。

## Impact

- **代码/资源**：`app/build.gradle.kts`、`RELEASE.md`（2 个文件）
- **API / 架构 / 依赖**：无
- **测试**：无新增（构建产物命名无单元测试惯例，靠 `ls app/build/outputs/apk/{release,debug}/` 验证）
- **CI / 脚本**：若项目里有引用旧 `app-release.apk` / `app-debug.apk` 路径的脚本，需要同步改——目前仓库内未发现（已 grep），但用户在别处的发布脚本/CI 可能要核对
