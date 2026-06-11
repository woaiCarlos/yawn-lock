## Context

- 上次 `d739004` 把 launcher XML 文件名从 `ic_launcher{,_round}.xml` 改成 `yawn_lock_launcher{,_round}.xml`，并相应更新了 `AndroidManifest.xml` 的 `android:icon` / `android:roundIcon` 引用 —— 这一步是 OK 的。
- 同一次提交里把项目根目录的旧 PNG 重命名为 `yawn_lock_icon_source.png`（来源素材），把 432×432 的新前景 PNG 落到 `app/src/main/res/drawable/yawn_lock_foreground.png`。
- **但 launcher XML 内部 `<foreground android:drawable="..."/>` 节点的引用没改**，还是 `@drawable/ic_moon`（一个 24dp viewport 的矢量月亮）。所以新前景 PNG 在工作区里待了一个 commit 周期没人引用，启动器看到的还是旧月亮。

## Goals / Non-Goals

**Goals:**
- 让 adaptive-icon 真正用上新的 `yawn_lock_foreground.png`。
- 提交新 PNG（之前 untracked）。
- 不破坏 bubble、月亮等其他使用 `ic_moon` 的场景。

**Non-Goals:**
- 不重做 icon 设计、不改 background 颜色（保持 `purple_700`，与新前景对比度已由设计师确认）。
- 不删除 `ic_moon.xml`（应用主屏等地方仍使用）。
- 不写 instrumentation/UI 测试（资源接线类改动按项目惯例靠 install + screenshot 验证）。

## Decisions

- **决策 1：直接修改两个 launcher XML 的 `<foreground>` 节点为 `@drawable/yawn_lock_foreground`**
  - 理由：原 XML 已经是 adaptive-icon 写法（`mipmap-anydpi-v26/`），只要换 drawable 引用即可，背景、padding、density 都不用动。
  - 备选：把 PNG 也放到 `mipmap-anydpi-v26/` 之类的位置 —— 不必要，drawable/ 已经合法。
- **决策 2：不动 `ic_moon.xml`，不删除文件**
  - 理由：`ic_moon` 矢量图还出现在主屏 bubble 等 UI 中，删除会引入新的范围与回归风险。
- **决策 3：背景色保持 `purple_700`**
  - 理由：commit `d739004` 之前就是这样，新前景图设计也是按紫色底配的；改背景会偏离设计师意图。

## Risks / Trade-offs

- 风险 1：旧设备缓存的 launcher 快捷方式可能仍是旧图标 → Android 系统一般会在安装新版本时刷新 adaptive-icon 缓存，多数设备重新启动后即生效；无 mitigation 必要。
- 风险 2：新前景图仅一份 432×432，未提供不同 dpi 桶（mdpi/hdpi/xhdpi 等）→ adaptive-icon 在 v26+ 设备上 OK；老设备（pre-Oreo）会回退到默认占位。当前 `minSdk` 已 26+（参考 `AndroidManifest` 与 `build.gradle.kts` 的 compileSdk 关系），无影响。

## Migration Plan

- 一次提交完成，revert 即回滚。

## Open Questions

无。
