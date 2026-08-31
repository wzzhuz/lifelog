# Status Calculation Specification

## Purpose

把「距今天数」翻译成「现在该不该做」的判断。这是本应用区别于同类
（如 TimeJot 只显示天数）的核心价值，也是「做出决策」的落点。

---

## Requirements

### Requirement: 三档状态

系统 SHALL 将每个事件的状态计算为四者之一：`NONE`、`FRESH`、`SOON`、`DUE`。

判定规则：

```
若无记录
  → NONE（待记录）
否则
  d     = 距上次记录的天数
  t     = 判断基准天数
  ratio = d / t
  ratio >= 1.0   → DUE   （该做了，红）
  ratio >= 0.75  → SOON  （快到了，黄）
  其他           → FRESH （新鲜，绿）
```

#### Scenario: 未记录过的事件

- **GIVEN** 事件「洗牙」无任何记录
- **WHEN** 计算状态
- **THEN** 返回 `NONE`
- **AND** 距今天数显示为 null

#### Scenario: 按期望间隔判定

- **GIVEN** 事件「理发」期望间隔 45 天
- **AND** 上次记录距今 34 天
- **WHEN** 计算状态
- **THEN** ratio = 0.756，返回 `SOON`

#### Scenario: 逾期判定

- **GIVEN** 事件「换床单」期望间隔 30 天
- **AND** 上次记录距今 30 天
- **WHEN** 计算状态
- **THEN** ratio = 1.0，返回 `DUE`

---

### Requirement: 判断基准的三级回退

系统 SHALL 按以下优先级决定判断基准天数 `t`：

1. 事件上显式设置的 `targetDays`
2. 历史平均间隔（记录数 ≥ 2 时，取首尾时间跨度除以间隔数）
3. 兜底常量 30 天

基准值 SHALL 不小于 1 天，避免除零。

#### Scenario: 无期望间隔时启用历史均值

- **GIVEN** 事件「看病」未设置期望间隔
- **AND** 已有 3 条记录，首尾跨度为 180 天
- **WHEN** 计算状态
- **THEN** 基准 = 180 / 2 = 90 天

**理由**：看病这类事没有规律，用户不可能提前设间隔。
记满两次后系统自动学会节奏，这就是「自动决策」的关键。

#### Scenario: 记录不足两条时用兜底值

- **GIVEN** 事件「体检」未设置期望间隔
- **AND** 仅有 1 条记录
- **WHEN** 计算状态
- **THEN** 基准 = 30 天（兜底常量）

---

### Requirement: 不强制设置期望间隔

系统 SHALL NOT 要求用户必须填写期望间隔才能使用。

**理由**：设间隔本身就是摩擦。用户刚装应用时根本不知道自己多久理一次发。
允许「先记着，之后系统自己算」能大幅降低起步门槛。

---

### Requirement: 预测下次时间

系统 SHALL 基于判断基准推算下次预计时间：`lastTimestamp + t 天`。

#### Scenario: 预测值展示

- **GIVEN** 事件「理发」上次记录在 8 月 1 日，基准 45 天
- **WHEN** 查看详情页
- **THEN** 显示预测下次时间约为 9 月 15 日

---

### Requirement: 派生状态单点计算

系统 SHALL 在 `StatusCalculator` 中集中计算状态，
UI 层 SHALL 直接消费 `EventStatus` 而不得自行推算。

`EventStatus` SHALL 包含：事件本体、记录列表、上次时间戳、距今天数、
平均间隔、基准天数、状态枚举、ratio、预测下次时间。

**理由**：避免多处实现导致状态不一致。
