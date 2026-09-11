# Delta for Status Calculation

## ADDED Requirements

### Requirement: 按需事件不参与逾期判定

系统 SHALL 对 `kind = ON_DEMAND` 的事件恒定返回 `IDLE`，
无论距上次记录多久、无论历史平均间隔是多少。

系统 SHALL NOT 对按需事件应用历史平均间隔或 30 天兜底基准。

**理由**：感冒这类事没有周期可言。
现有三级回退会让「上次感冒半年前、平均半年一次」被算成逾期，
首页长期挂一个红色「该看病了」——用户从来没表达过要看病有周期。

#### Scenario: 感冒看病

- **GIVEN** 事件「看病」为 `ON_DEMAND`
- **AND** 已有 3 条记录，首尾跨度 180 天
- **WHEN** 距上次记录 182 天时计算状态
- **THEN** 返回 `IDLE`
- **AND** 界面显示「上次距今 182 天」
- **AND** 不出现「该做了」「快到了」的判定

#### Scenario: 从未记录的按需事件

- **GIVEN** 事件「针灸」为 `ON_DEMAND` 且无记录
- **WHEN** 计算状态
- **THEN** 返回 `NONE`（未记录优先于按需）

---

### Requirement: 每日频次状态

系统 SHALL 对设置了 `timesPerDay` 的事件，按**当日**已完成次数判定状态：

```
done = 当日记录条数（按设备时区的自然日）
done >= timesPerDay        → FRESH
done == 0                  → DUE
其余                        → SOON
```

该判定 SHALL 完全独立于「距上次多久」，两者 SHALL NOT 混算。

系统 SHALL 只统计**当日**记录，
SHALL NOT 累计连续天数、SHALL NOT 因往日漏记调整当日判定。

**理由**：一天三次的药，早上吃过一次后是「1/3」，
不会因为距上次只有 4 小时就说「新鲜」——
「距上次多久」与「今天还差几次」是两套口径。

#### Scenario: 今天还没吃

- **GIVEN** 「消炎药」每日 3 次
- **AND** 今日 0 条记录
- **WHEN** 计算状态
- **THEN** 返回 `DUE`

#### Scenario: 吃了一半

- **GIVEN** 「消炎药」每日 3 次
- **AND** 今日已记录 1 次
- **WHEN** 计算状态
- **THEN** 返回 `SOON`
- **AND** 首页显示「今日 1/3」

#### Scenario: 今天齐了

- **GIVEN** 「消炎药」每日 3 次
- **AND** 今日已记录 3 次
- **WHEN** 计算状态
- **THEN** 返回 `FRESH`
- **AND** 当日再记一笔不影响状态（仍为 FRESH）

#### Scenario: 跨天重置

- **GIVEN** 「消炎药」昨日记录 2 次
- **WHEN** 进入次日且尚未记录
- **THEN** 返回 `DUE`，进度为 0/3
- **AND** 不出现「昨日未完成」的补偿或惩罚

---

### Requirement: 疗程状态聚合

系统 SHALL 为 `kind = COURSE` 的疗程，
取其全部**未归档**子事件中优先级最高者作为疗程状态
（`DUE` > `SOON` > `FRESH` > `NONE` > `IDLE`）。

疗程的当日进度 SHALL 为各子事件当日完成数之和 ÷ 目标数之和。

疗程无子事件时 SHALL 返回 `NONE`。

**理由**：疗程本身不承载记录，
它的意义是「这一摊事现在怎么样了」——
只要有一个子项该做了，用户就得看一眼。

#### Scenario: 有一个子项没吃

- **GIVEN** 疗程下有「退烧药」（每日 2 次，今日 2/2）
  与「消炎药」（每日 3 次，今日 0/3）
- **WHEN** 计算疗程状态
- **THEN** 返回 `DUE`
- **AND** 显示「今日 2/5」

## MODIFIED Requirements

### Requirement: 四档状态

系统 SHALL 将每个事件的状态计算为五者之一：
`NONE`、`FRESH`、`SOON`、`DUE`、`IDLE`。

判定规则：

```
若无记录
  → NONE（待记录）
否则若 kind = ON_DEMAND
  → IDLE（按需，不判逾期）
否则若 kind = COURSE
  → 取子事件中最紧急的一档（见「疗程状态聚合」）
否则若 timesPerDay != null
  → 按当日进度判定（见「每日频次状态」）
否则（PERIODIC）
  d     = 距上次记录的天数（真实流逝时长，含小数）
  t     = 判断基准天数
  ratio = d / t
  ratio >= 1.0   → DUE   （该做了，红）
  ratio >= 0.75  → SOON  （快到了，黄）
  其他           → FRESH （新鲜，绿）
```

`IDLE` 事件 SHALL 只展示「上次距今 X 天」，
SHALL NOT 参与「该做 / 待办」类筛选与统计。

**理由**：原来的四档里没有「这件事不需要催」这个位置，
只能靠 `targetDays` 留空间接表达，
而留空又会被历史均值与 30 天兜底重新拉回周期逻辑。
`IDLE` 让「不催」成为一个明确状态。

#### Scenario: 按期望间隔判定

- **GIVEN** 事件「理发」为 `PERIODIC`，期望间隔 45 天
- **AND** 上次记录距今 34 天
- **WHEN** 计算状态
- **THEN** ratio = 0.756，返回 `SOON`

#### Scenario: 逾期判定

- **GIVEN** 事件「换床单」为 `PERIODIC`，期望间隔 30 天
- **AND** 上次记录距今 30 天
- **WHEN** 计算状态
- **THEN** ratio = 1.0，返回 `DUE`

#### Scenario: 按需事件不进待办筛选

- **GIVEN** 用户筛选「该做了」
- **AND** 「看病」（`ON_DEMAND`）距上次已 200 天
- **WHEN** 查看列表
- **THEN** 「看病」不出现在结果中

### Requirement: 判断基准的三级回退

系统 SHALL 按以下优先级决定判断基准天数 `t`：

1. 事件上显式设置的 `targetDays`
2. 历史平均间隔（记录数 ≥ 2 时，取首尾时间跨度除以间隔数）
3. 兜底常量 30 天

该回退 SHALL 仅对 `kind = PERIODIC` 的事件生效。

基准值 SHALL 不小于 1 天，避免除零。

#### Scenario: 无期望间隔时启用历史均值

- **GIVEN** 事件「看病」为 `PERIODIC` 且未设置期望间隔
- **AND** 已有 3 条记录，首尾跨度为 180 天
- **WHEN** 计算状态
- **THEN** 基准 = 180 / 2 = 90 天

#### Scenario: 按需事件不走回退

- **GIVEN** 事件「看病」为 `ON_DEMAND`
- **AND** 已有 3 条记录，首尾跨度 180 天
- **WHEN** 计算状态
- **THEN** 不使用 90 天基准，返回 `IDLE`

**理由**：三级回退的价值是「让系统学会节奏」，
前提是这件事本身有节奏。对按需事件套用，
等于把「半年一次」翻译成「现在该去了」，是误判的来源。

### Requirement: 派生状态单点计算

系统 SHALL 在 `StatusCalculator` 中集中计算状态，
UI 层 SHALL 直接消费 `EventStatus` 而不得自行推算。

`EventStatus` SHALL 包含：事件本体、记录列表、上次时间戳、距今天数、
平均间隔、基准天数、状态枚举、ratio、预测下次时间，
以及**当日已完成次数 `doneToday`**、**子事件状态列表 `children`**。

**理由**：四档变五档、又新增频次与疗程两条判定路径后，
若分散到 UI 层实现，必然出现「首页说该吃、详情页说不用吃」的不一致。
