# Tasks

## 1. 接线 launcher XML 引用

- [x] 1.1 修改 `app/src/main/res/mipmap-anydpi-v26/yawn_lock_launcher.xml` 的 `<foreground android:drawable="..."/>` 从 `@drawable/ic_moon` 改为 `@drawable/yawn_lock_foreground`
- [x] 1.2 修改 `app/src/main/res/mipmap-anydpi-v26/yawn_lock_launcher_round.xml` 同上

## 2. 提交新前景图

- [x] 2.1 `git add app/src/main/res/drawable/yawn_lock_foreground.png`

## 3. 构建 & 验证

- [x] 3.1 运行 `./gradlew :app:assembleDebug` 确认无资源引用错误 — BUILD SUCCESSFUL
- [x] 3.2 资源层验证 — `aapt2 dump xmltree` 确认两个 launcher XML 前景引用均为 `drawable/yawn_lock_foreground`；APK 内 PNG 哈希与源一致；新 PNG 已用 Read 工具可视化确认内容为「打哈欠云朵 + 月亮 + 星星」品牌图
- [x] 3.3 提交 — commit message: `fix(icon): wire launcher XML to new yawn_lock_foreground.png`

> **已知限制**：项目内无 AVD，`adb devices` 返回空，launcher 最终视觉渲染（圆角裁剪、不同 dpi 桶等）未在真实设备/模拟器上做截图验证。下次 release 前建议跑一次真机/模拟器视觉确认。
