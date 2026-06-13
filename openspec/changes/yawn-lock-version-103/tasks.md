# Tasks

## 1. 改 `app/build.gradle.kts`

- [x] 1.1 `versionCode = 1` → `versionCode = 4`
- [x] 1.2 `versionName = "1.0.0"` → `versionName = "1.0.3"`

## 2. 验证

- [x] 2.1 `./scripts/build.sh :app:assembleDebug` → BUILD SUCCESSFUL,产出 `yawn-lock-1.0.3-debug.apk`
- [x] 2.2 `./scripts/build.sh :app:assembleRelease` → BUILD SUCCESSFUL,产出 `yawn-lock-1.0.3-release.apk`
- [x] 2.3 `./scripts/build.sh :app:testDebugUnitTest` → 10/10 仍通过(版本号改动不影响逻辑)
- [x] 2.4 `aapt dump badging` release APK 确认 versionName/versionCode 写入 manifest 正确

## 3. 提交

- [x] 3.1 commit message: `tweak(release): bump versionName 1.0.0→1.0.3, versionCode 1→4`
