# Tasks

## 1. 改 `app/build.gradle.kts` 加 APK rename 任务

- [x] 1.1 尝试 `androidComponents { onVariants { variant.outputs.forEach { it.outputFileName.set(...) } } }` —— AGP 8.5 `VariantOutput` public 接口无 `outputFileName`（仅 internal `BaseVariantOutputImpl` 有），改走 `renameApksToReleaseConvention` doLast 后处理 + `afterEvaluate { assemble*.finalizedBy(rename...) }` 模式
- [x] 1.2 rename 任务里读 `android.defaultConfig.versionName`（runtime 时取），按 buildType 输出 `yawn-lock-{ver}-{type}.apk`

## 2. 简化 `RELEASE.md`

- [x] 2.1 删除 SOP 里「改名拷贝到根」整节
- [x] 2.2 更新产物路径与示例命令：直接用 `app/build/outputs/apk/release/yawn-lock-1.0.0-release.apk`
- [x] 2.3 在「当前产物」保留「想放项目根就自己 cp」的小字注释

## 3. 构建 & 验证

- [x] 3.1 `./gradlew clean :app:assembleDebug :app:assembleRelease` → BUILD SUCCESSFUL，rename 任务日志 `renamed app-debug.apk → yawn-lock-1.0.0-debug.apk` / `renamed app-release.apk → yawn-lock-1.0.0-release.apk`
- [x] 3.2 `grep -rn 'app-debug\.apk\|app-release\.apk' .` → 只剩 `app/build.gradle.kts:73`（rename 任务需要旧名做输入）、历史 plan/report（point-in-time 记录）、本 change 的 proposal/design（自指），无活跃脚本或 CI 引用
- [x] 3.3 提交
