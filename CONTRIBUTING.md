# 贡献指南 / Contributing

欢迎任何形式的贡献 —— bug 报告、功能建议、文档改进、PR。

We welcome any form of contribution — bug reports, feature requests, doc improvements, PRs.

---

## 中文

### 我想提个 bug / 功能建议

1. **先搜索** 现有 [issues](https://github.com/woaiCarlos/yawn-lock/issues) 看有没有重复
2. **新建 issue**,选 `bug` 或 `enhancement` 模板,尽量填完整:
   - 复现步骤(对 bug)
   - 期望行为 vs 实际行为
   - 设备型号 + Android 版本
   - 应用版本
3. **截图 / logcat** 强烈推荐。bug 报告带 logcat 的我优先看

### 我想提个 PR(改代码)

1. **先开 issue** 讨论大方向,避免做完了才发现方向不对
2. **Fork + 克隆**:
   ```bash
   git clone https://github.com/<your-username>/yawn-lock.git
   cd yawn-lock
   ```
3. **建分支**(参考命名:`fix/short-desc` / `feat/short-desc`):
   ```bash
   git checkout -b fix/bubble-attached-stuck
   ```
4. **改代码**,同时**补/改单元测试**(`app/src/test/kotlin/.../TimerRepositoryStateTest.kt` 是状态机的关键覆盖)
5. **跑单测**:
   ```bash
   ./scripts/build.sh :app:testDebugUnitTest
   # 必须 10/10 通过
   ```
6. **构建 debug APK 自测**:
   ```bash
   ./scripts/build.sh :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/yawn-lock-1.0.3-debug.apk
   # 在真机跑一遍你改的流程,确认没回归
   ```
7. **commit message** 用 `fix: ...` / `feat: ...` / `tweak: ...` 前缀,描述清楚 why + what
8. **push + 开 PR**,PR 描述里引用关联的 issue,描述改动 + 测试方式

### PR 验收标准

- ✅ 所有单测通过
- ✅ 真机(或模拟器)上跑过改动的流程,无回归
- ✅ commit message 解释 why(不只 what)
- ✅ 修改的代码行有合理注释(中文/英文均可)
- ✅ 如果加了用户可见的 UI 改动,附截图
- ✅ 如果改了 spec/能力,在 PR 描述里说明

### 开发流程(本项目用 [Comet](https://github.com/...))

本项目用 [Comet](https://github.com/...) 双星流程(OpenSpec + Superpowers)。每改一个东西,先开一个 **change**(在 `openspec/changes/<name>/` 下),写 proposal / design / tasks,然后实施 + 验证 + 归档。完整流程:

```text
/comet-open      # 起 change
/comet-design    # 复杂改动的 design doc (tweak/hotfix 可跳)
/comet-build     # 实施
/comet-verify    # 验证
/comet-archive   # 归档
```

- **tweak**: 文案、配置、文档等小调整(跳过 design)
- **hotfix**: bug 修复(跳过 brainstorming,直入 build)
- **full**: 复杂改动 / 新能力(走完整 design)

不清楚的可以先 issue 讨论。

### 代码风格

- Kotlin 官方风格(`kotlin.code.style=official` 已在 `gradle.properties`)
- Compose: 函数命名 PascalCase,Composable 名字用名词短语
- 注释: 中文或英文,简洁即可
- 状态机相关的代码(TimerRepository)是核心,改动需要附单测

### 文档

- `README.md` 是中英双语,新功能要同步两边
- `RELEASE.md` 是发布 SOP,改发布流程要先 review
- `docs/superpowers/` 是开发过程记录,通常不用动

---

## English

### I want to report a bug / request a feature

1. **Search** existing [issues](https://github.com/woaiCarlos/yawn-lock/issues) first
2. **Open a new issue** with `bug` or `enhancement` template:
   - Reproduction steps (for bugs)
   - Expected vs actual behavior
   - Device model + Android version
   - App version
3. **Screenshots / logcat** strongly recommended. I prioritize bug reports with logcat.

### I want to submit a PR

1. **Open an issue first** to discuss the direction
2. **Fork + clone**:
   ```bash
   git clone https://github.com/<your-username>/yawn-lock.git
   cd yawn-lock
   ```
3. **Branch** (naming: `fix/short-desc` / `feat/short-desc`):
   ```bash
   git checkout -b fix/bubble-attached-stuck
   ```
4. **Code + tests** (`app/src/test/kotlin/.../TimerRepositoryStateTest.kt` is the key state-machine coverage)
5. **Run unit tests**:
   ```bash
   ./scripts/build.sh :app:testDebugUnitTest
   # must be 10/10 pass
   ```
6. **Build debug APK and smoke test on real device**:
   ```bash
   ./scripts/build.sh :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/yawn-lock-1.0.3-debug.apk
   # exercise your change on a real device
   ```
7. **Commit** with `fix:` / `feat:` / `tweak:` prefix, explain why
8. **Push + open PR**, link related issues in the description

### PR acceptance criteria

- ✅ All unit tests pass
- ✅ Real-device (or emulator) smoke tested, no regressions
- ✅ Commit message explains why (not just what)
- ✅ Reasonable comments on changed lines (Chinese or English OK)
- ✅ UI changes include screenshots
- ✅ Spec/capability changes are documented in the PR

### Development workflow (Comet)

This project uses the [Comet](https://github.com/...) dual-star workflow (OpenSpec + Superpowers). For each change, open a **change** (in `openspec/changes/<name>/`), write proposal/design/tasks, then build + verify + archive:

```text
/comet-open      # open the change
/comet-design    # design doc for complex changes (skippable for tweak/hotfix)
/comet-build     # implement
/comet-verify    # verify
/comet-archive   # archive
```

- **tweak**: docs / config / small text changes (skip design)
- **hotfix**: bug fixes (skip brainstorming, go straight to build)
- **full**: complex changes / new capabilities (full design)

Open an issue if you're unsure.

### Code style

- Kotlin official style (`kotlin.code.style=official` in `gradle.properties`)
- Compose: PascalCase function names, Composable names are noun phrases
- Comments: concise Chinese or English
- State-machine code (TimerRepository) is core; changes need unit tests

### Docs

- `README.md` is bilingual; new features need both sides updated
- `RELEASE.md` is the release SOP
- `docs/superpowers/` is dev-process history, usually untouched

---

## First-time contributors

Look for issues labeled [`good first issue`](https://github.com/woaiCarlos/yawn-lock/issues?q=is%3Aissue+is%3Aopen+label%3A%22good+first+issue%22) — these are scoped, well-documented, and don't require deep codebase knowledge.

## Questions?

Open an issue with the `question` label, or check existing [issues](https://github.com/woaiCarlos/yawn-lock/issues).

## License

By contributing, you agree your contributions will be licensed under the [MIT License](./LICENSE).
