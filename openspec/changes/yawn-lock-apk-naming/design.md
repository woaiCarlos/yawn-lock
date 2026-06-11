## Context

- `app/build.gradle.kts` 的 `android { ... }` 块里目前没有 `androidComponents` 配置，所有 variant 用 Gradle 默认 basename：`app-{buildType}.apk`。
- `versionName = "1.0.0"`、`versionCode = 1` 在 `defaultConfig` 中，是单一来源。
- `RELEASE.md` 用了「`yawn-lock-1.0.0-release.apk`」「`yawn-lock-1.0.0-debug.apk」作为示例名，并要求它们位于项目根，且写明了 `cp` 命令做改名——这是把「Gradle 输出什么」和「发布叫什么」割裂开的妥协。

## Goals / Non-Goals

**Goals:**
- 让 `assembleDebug` / `assembleRelease` 直接产出符合 SOP 命名约定的文件。
- 模板里用 `versionName` 而不是硬编码 `1.0.0`，避免下次发版再次手动改 gradle 脚本。
- 简化 `RELEASE.md`，去掉「`cp`」步骤。

**Non-Goals:**
- 不改 APK 输出目录（仍为 `app/build/outputs/apk/{buildType}/`）。把 APK 直接放到项目根需要更激进的改造（自定义 `outputs` 路径或注册 `Sync` 任务），与本 change 范围不符。
- 不改 `versionName` / `versionCode` / 签名 / 资源。
- 不写「自动 cp 到项目根」的额外任务（按需后续可开新 change）。

## Decisions

- **决策 1：用 `androidComponents.onVariants(selector().all())` 给所有 variant 设 `outputFileName`**
  - 理由：AGP 7.4+ 推荐 API，覆盖 debug + release + 未来可能加的 flavor，类型安全。`outputFileName` 是 `Property<String>`，可以在 `afterEvaluate` 之前完成绑定，符合 lazy configuration 规范。
  - 备选 A：`applicationVariants.all { outputs.all { ... } }` —— AGP 8.x 已 deprecated，且在 `afterEvaluate` 之后才生效。
  - 备选 B：硬编码 `archivesBaseName` —— 不支持 per-variant，且会污染 AAR/JAR 等其他 artifact。
- **决策 2：模板用 `android.defaultConfig.versionName` 而非硬编码字符串**
  - 理由：版本号是 single source of truth；将来 1.0.1 / 1.1.0 / 2.0.0 都不用动 gradle 脚本。
- **决策 3：`RELEASE.md` 删除 cp 行，并明确说明「APK 在 `app/build/outputs/apk/{buildType}/` 下，文件名已是 `yawn-lock-<ver>-<type>.apk`」**
  - 理由：让 SOP 反映「Gradle 一次到位」的真实情况；保留项目根拷贝是用户的可选后续步骤，写在「如果想放到项目根」的小字注释里。
- **决策 4：format 字符串用 `${...}` Kotlin template，而非字符串拼接**
  - 理由：与项目里其他 `app/build.gradle.kts` 风格保持一致（已有 Kotlin DSL 风格）。

## Risks / Trade-offs

- 风险 1：外部 CI / 上传脚本可能仍引用旧名 `app-release.apk`。Mitigation：`grep -r 'app-release\|app-debug' .` 确认仓库内无引用；在 commit message 里点名告知用户检查外部脚本。
- 风险 2：`androidComponents` 块对老版本 AGP 不可用。Mitigation：本项目 AGP 来自 `libs.plugins.android.application`（已是 AGP 8.x），无兼容性问题。
- 风险 3：`outputFileName` 只影响 `outputs/apk/` 下的 basename，**不**会把 APK 移动到项目根。Mitigation：在 `RELEASE.md` 里明确写出当前实际路径；如用户后续想直接出到根，再开新 change。

## Migration Plan

- 一次提交完成。revert 即回滚到「`app-release.apk` / `app-debug.apk`」+ 「手动 cp」旧流程。

## Open Questions

无。
