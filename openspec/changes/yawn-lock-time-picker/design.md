# Design: yawn-lock-time-picker — 自定义时间改时钟式滚轮选择器

> OpenSpec canonical spec: `openspec/changes/yawn-lock-time-picker/`
> 已有 main spec: `openspec/specs/scheduled-screen-lock/spec.md`(本 change 在该 capability 上新增 Requirement)

## Context

打完哈欠 v1.x 后,`CustomDial` 的滑块体验反馈差(1% 滑动跨越秒/分钟边界),用户明确要求改成「时钟式滚轮选择器」(iOS alarm 风格)。本 change 完成该改造。

## Goals / Non-Goals

**Goals**:
- `CustomDial` 改造:删除滑块,改为「小时:分钟:秒」三列滚轮
- `WheelColumn` 通用组件:可指定 range + selected + onSelectedChange,中央行高亮、上下吸附
- 任意滚轮变化 → 重新组合成秒数 → 走原有 `vm.setSeconds` → `repo.preview` 链路
- 总时长 clamp 5..7200 秒
- 不加第三方依赖,纯 Compose + LazyColumn + SnapFlingBehavior

**Non-Goals**:
- 不做 AM/PM
- 不做日期选择
- 不做无障碍触觉反馈
- 不做深色模式适配(本项目目前无 light/dark 主题切换)

## Decisions

### D1. 不引第三方 wheel-picker 库

**方案**:
- `io.github.rogueai.compose-wheel-picker-android` 等第三方库都还在 v0.x,稳定性未知
- 自己用 `LazyColumn` + `rememberSnapFlingBehavior` 写,**50 行 Compose 代码**就够
- 标准 Compose API,无新依赖

### D2. 滚轮物理模型

- 单元高度 48dp(中等,可点中)
- 一次显示 3 行(上 padding + 中 + 下 padding)→ 控件总高 144dp
- 上下各 `contentPadding(vertical = 48.dp)` 让首尾值也能居中显示
- 居中行字号 28sp + 加粗 + Purple900
- 上下行字号 18sp + 浅灰

### D3. 选中值同步策略(双向)

**问题**: `selected` 是从父级 state 计算的(从 `state.durationMs / 1000` 得到),滚轮内部 listState 必须跟 selected 同步。

**方案**:
- **滚轮 → state**: 用 `LaunchedEffect(listState.firstVisibleItemIndex)` 监听滚动位置,吸附完成后调用 `onSelectedChange(range.first + index)`
- **state → 滚轮**: 用 `LaunchedEffect(selected)` 监听外部 selected 变化,变化时 `listState.animateScrollToItem(target)`
- 两个 LaunchedEffect 互相不会死循环:scrollToItem 不改变 firstVisibleItemIndex 的最终值,所以不会重复触发 onSelectedChange

### D4. 范围 5..7200 秒的 clamp 策略

**问题**: 用户可能在三列独立选,组合后超过 7200(比如 2:30:00 = 9000s)。

**方案**:
- `vm.setSeconds(s)` 已经在 repo 里 `coerceIn(5L, 7200L)`,所以超出会被 clamp
- 表现:用户选到 2:30 → 显示自动跳回 2:00(滚轮被 state→scroll 拉回去)
- 这是良性反馈,不弹错

### D5. 时长为 0 的边界

**问题**: 全部 0 = 0 秒,但 `preview` 要求 `durationMs > 0`。

**方案**:
- 在 CustomDial 父级(`TimerScreen`)已有保护:`if (state.durationMs > 0 && !state.isActive) { seconds = state.durationMs / 1000L }`
- 滚轮默认 0:0:5(5 秒最小值),不会出 0

### D6. 文件改动

| 文件 | 改动 |
|------|------|
| `ui/timer/WheelColumn.kt` | 新建 (~70 行 Composable) |
| `ui/timer/TimerScreen.kt` | `CustomDial` 函数体重写,删除 Slider/± 按钮,新增 3 列 WheelColumn |
| `openspec/specs/scheduled-screen-lock/spec.md` | 新增 Requirement:**Custom Time Picker** |
| 任务执行后:`openspec/changes/yawn-lock-time-picker/specs/scheduled-screen-lock/spec.md` | delta 同步 |

## Risks / Open Questions

- **滚动性能**: LazyColumn 60 项已测过很流畅,无问题
- **小时数 0-2 太短?**: 用户上限 2 小时,hour 0-2 够用,分钟/秒 0-59 完整
- **Preset chip 与滚轮同步**: 点 10 分钟 chip → state.durationMs = 600000 → 滚轮要滚到 0:10:00,LaunchedEffect(selected) 处理
- **横屏 / 大屏**: 滚轮等比放大,144dp 高,不会撑爆布局

## Self-Review

- 4 个文件改动(2 代码 + 1 spec 主 + 1 delta),跨 1 个 module
- 无新依赖
- 走完 full 流程 → 归档后 main spec 同步
