## Context

- 1.0.0 是 `d739004` 设置的:`versionCode = 1`, `versionName = "1.0.0"`
- 之后 3 个 fix 都通过,语义上对应 3 次发版
- `scripts/build.sh` 里 rename 任务读 `android.defaultConfig.versionName`,versionName 改了就自动 rename APK

## Goals / Non-Goals

**Goals:**
- versionName "1.0.0" → "1.0.3"
- versionCode 1 → 4
- 触发 assembleRelease,产出新签名 APK `yawn-lock-1.0.3-release.apk`

**Non-Goals:**
- 不动 applicationId(还是 `com.example.yawnlock`)
- 不动 signing config
- 不动 RELEASE.md 的其他内容(它已经描述了 1.0.0 的发版流程,跟 1.0.3 兼容)
- 不动 spec(这是配置值变更,不是能力变更)
- 不打 git tag(用户没要求,可以在 archive 完之后手动打)

## Decisions

- **决策 1:versionName 1.0.3,versionCode 4**
  - 理由:用户明确选了 1.0.3 / 4。1.0.0 → 1.0.1 → 1.0.2 → 1.0.3 对应 4 个递增的 versionCode
  - 备选 1.1.0:用户没选 minor bump,按用户指令用 1.0.3
- **决策 2:只改 2 个值,不动其他**
  - 理由:这是 tweak,scope 最小化。signing config、applicationId、targetSdk 等都不动

## Risks / Trade-offs

- 风险 1:已经发布的 1.0.0 用户升级时,Play Store 看到 versionCode 4 是个大步进
  - 缓解:每个 fix 都在 commit message 里写了 release notes 用的「行为变化」,可在 store listing 里讲清
  - 缓解:用户可以选择先发 1.0.1、1.0.2、1.0.3 三次小版本(每次 versionCode+1)再上 1.0.3,但用户明确选了 1.0.3/4 一起出,尊重选择

## Migration Plan

- 一次提交。revert 即回到 versionCode=1 状态(任何已安装的 1.0.3 用户升级时会回滚到 1.0.0)

## Open Questions

无。
