## Why

提交 `d739004`（chore(release): bump to 1.0.0, rename icons, add release signing config）把 launcher XML 从 `ic_launcher{,_round}` 改名为 `yawn_lock_launcher{,_round}`，并把项目根目录的原图挪为 `yawn_lock_icon_source.png`，但漏改了 XML 内部 `<foreground>` 节点的引用 —— 两个 launcher XML 仍指向旧的 `@drawable/ic_moon` 矢量图，而 432×432 的新前景图 `yawn_lock_foreground.png` 已经在 `drawable/` 里、从未被任何 manifest 引用。结果是：用户看到的 launcher 图标还是旧的月亮，应用名却已经是「打哈欠锁屏」+ 新版信息，命名空间与视觉不一致。

## What Changes

- `app/src/main/res/mipmap-anydpi-v26/yawn_lock_launcher.xml` 的 `<foreground>` 从 `@drawable/ic_moon` 改为 `@drawable/yawn_lock_foreground`
- `app/src/main/res/mipmap-anydpi-v26/yawn_lock_launcher_round.xml` 同上
- 将 `app/src/main/res/drawable/yawn_lock_foreground.png` 纳入版本控制（之前 untracked）

不修改 manifest（manifest 已经指向 `@mipmap/yawn_lock_launcher`，与新 XML 名一致）。不修改 adaptive-icon 的 `<background>`（仍使用 `purple_700`，与新前景图对比度经过设计）。不修改 `ic_moon.xml`（在应用内（主屏 bubble 按钮等）仍然使用，不删除）。

## Capabilities

### New Capabilities
无。

### Modified Capabilities
无。本次为资源接线修正，不改变任何 spec 级行为；adaptive-icon 的 capability 本身未变，只是接错了前景资源。无 delta spec。

## Impact

- **代码/资源**：`app/src/main/res/mipmap-anydpi-v26/yawn_lock_launcher{,_round}.xml`、`app/src/main/res/drawable/yawn_lock_foreground.png`（新增跟踪）
- **API / 架构 / 依赖**：无
- **测试**：无新增（icon 资源无单元测试惯例，靠 install + screenshot 视觉验证）
- **manifest**：不变
