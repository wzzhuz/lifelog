# 增量：状态计算与详情页指标

## MODIFIED Requirements

### Requirement: 按需事件不参与周期判定

按需事件（`EventKind.ON_DEMAND`）的状态 SHALL 恒为 `IDLE`，
不产出预测值、不参与「判断基准」的三级回退。

详情页的指标区 SHALL 按事件类型分派，
**按需事件 SHALL NOT 展示任何带判断语义的指标**：

| 指标 | 周期型 | 频次型 | 按需型 |
|---|---|---|---|
| 累计次数 | 显示 | 显示 | 显示 |
| 平均间隔 | 显示 | 隐藏 | **隐藏** |
| 判断基准 | 显示 | 改为「每日 N 次」 | **隐藏（改为二宫格）** |
| 预计下次 | 有预测值时显示 | 隐藏 | **隐藏** |

按需事件 SHALL 改为展示纯事实指标：累计次数与首次记录时间。
顶部大数字区保留「上次距今」——这是按需事件唯一该回答的问题。

理由：感冒这类事没有周期，展示「判断基准 30 天」会让用户误读为
"系统认为我该多久看一次病"，与产品定位（不制造焦虑）冲突。

#### Scenario: 看病事件不显示判断基准

- **GIVEN** 存在一个 `ON_DEMAND` 类型的「看病」事件，有 3 条记录
- **WHEN** 用户打开它的详情页
- **THEN** 指标区显示「累计次数」与「首次记录」
- **AND** 不出现「判断基准」「平均间隔」「预计下次」

#### Scenario: 周期型事件不受影响

- **GIVEN** 存在一个周期型事件「换床单」，设了期望间隔 14 天
- **WHEN** 用户打开它的详情页
- **THEN** 仍显示累计次数、平均间隔、判断基准三项

## ADDED Requirements

### Requirement: 派生状态由写入时维护

事件的最近记录时间、累计次数、当日完成次数 SHALL 存储为 `events` 表的派生列，
由 Repository 在**写入记录时于同一事务内**维护，
首页列表 SHALL NOT 通过对 `records` 表做 GROUP BY 来获取这些值。

派生列包括：`lastTs`、`recordCount`、`lastRecordDay`、`todayCount`。

`todayCount` 的「今天」边界 SHALL 由 Kotlin 侧按**设备时区**计算，
不得使用 SQLite 的 `date('now')`（其为 UTC，与设备时区不一致）。

实现 SHALL 提供幂等的全量重算入口 `recomputeDerived()`，
写入路径遗漏时能一键修复。设置页 SHALL 暴露该入口。

任何绕过 Repository 直接写 `records` 的路径都会破坏一致性，
SHALL 在代码注释中显式声明这条约束。

#### Scenario: 首页不再等待记录表聚合

- **WHEN** 首页加载
- **THEN** 事件列表通过单表查询获得
- **AND** 不对 `records` 表执行 GROUP BY

#### Scenario: 派生值与实际记录不一致时可修复

- **GIVEN** 因异常路径导致 `recordCount` 与实际记录数不符
- **WHEN** 用户在设置页触发「重建派生数据」
- **THEN** 全部派生列按实际记录重算，恢复一致

### Requirement: 当日次数读时校正

`todayCount` SHALL 在**读取时**校正：
一旦发现 `lastRecordDay` 不等于今天，SHALL 按 0 处理，且不写回数据库。

理由：App 可能跨越零点未打开，写入时维护的 `todayCount` 会过期。
写回会产生一次多余的数据库写入并触发列表刷新。

#### Scenario: App 挂了一整夜后打开

- **GIVEN** 昨天记录过 2 次，`lastRecordDay` 为昨天
- **WHEN** 今天首次打开首页
- **THEN** 该事件显示的当日次数为 0，而非昨天的 2
- **AND** 不触发额外的数据库写入

### Requirement: 详情页不订阅无关的全表聚合

详情页 SHALL 仅在事件类型为疗程时订阅子事件状态流；
其他类型 SHALL 短路为空列表，不得订阅记录表的全表聚合。

理由：非疗程事件的子事件查询恒为空，
而聚合查询每次都跑一遍，是详情页进出耗时的主要来源。

#### Scenario: 打开普通事件详情页

- **GIVEN** 存在周期型事件「理发」
- **WHEN** 用户打开它的详情页
- **THEN** 不订阅任何针对 `records` 表的 GROUP BY 查询
