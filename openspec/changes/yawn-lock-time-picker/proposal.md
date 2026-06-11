# Proposal: yawn-lock-time-picker — 自定义时间改时钟式滚轮选择器

## Why

打完哈欠 v1.x 用户实测反馈:当前自定义时间用的是「超大数字 + ± 按钮 + 一根拖动条」,但**这个滑块的手感不对**:
- 1% 滑动 = 跨越秒/分钟边界,用户难以精细控制到具体秒数
- 滑块没有「我现在到底滑到哪儿了」的视觉反馈,要一直盯着数字
- 用户提到:「我需要的是时钟那种,小时分钟秒分开,上下滚动调」

期望: 改成像 iOS 闹钟设置那样的**三列滚轮**(hour / minute / second),每列上下滚动调一个数,中央高亮显示当前选中的值。

## What Changes

### 1. 新增 `WheelColumn` 通用组件

- 接收 `range: IntRange` + `selected: Int` + `onSelectedChange: (Int) -> Unit`
- 用 `LazyColumn` + `rememberSnapFlingBehavior` 做滚轮 + 居中吸附
- 上下各 padding 一格让边界值也能居中
- 中央行高亮 + 字号大 + 加粗,其他行半透明 + 字号小
- 单元高度 48dp,一次显示 3 行(上 + 中 + 下)

### 2. `CustomDial` 改造

- 删掉: ± 按钮、Slider、`val big` 单元计算逻辑
- 新增: 3 列 `WheelColumn`(hour 0-2, minute 0-59, second 0-59)+ 中间「:」分隔
- 任何一列变化 → 重新组合秒数 `h*3600 + m*60 + s` → `onChange()` → `vm.setSeconds()` → `repo.preview()`
- 时长仍 clamp 到 5..7200 秒
  - 如果用户选到 2:30:00 (9000s) 自动 clamp 到 2:00:00 (7200s)
  - 反向选择:如果小时=2,分钟和秒被强制 0

### 3. 交互细节

- 滚轮自动滚动到当前 `selected` 位置(响应外部 state 变化,比如 preset chip)
- 用户滚动到中点附近时吸附 + 触发 onSelectedChange
- 滚轮上的数字 paddingStart 补 0,统一两位数显示

## Non-Goals

- 不做「AM/PM」(用户都在中国大陆,24h 制)
- 不做日期选择(只选时长)
- 不做无障碍触觉反馈(标准 Compose LazyColumn 滑动)
- 不加新依赖(自己用 LazyColumn + SnapFlingBehavior 写,不引第三方 wheel-picker 库)

## Spec 影响

- `scheduled-screen-lock` 能力新增 Requirement:**Custom Time Picker**
  - MUST: 三个滚轮(hour/minute/second)+ 中央高亮
  - MUST: 滑动后吸附到整数值
  - MUST: 选中的时分秒组合成总秒数 ≤ 7200
