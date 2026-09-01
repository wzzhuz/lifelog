# Data Persistence Specification

## Purpose

数据完全本地存储，不申请任何网络权限。用户的记录是本应用唯一的资产，
写入过程不得因崩溃或断电导致损坏。

---

## Requirements

### Requirement: 完全离线

系统 SHALL NOT 申请 `INTERNET` 权限，代码中 SHALL NOT 声明该权限。

系统 SHALL NOT 提供云同步、账号体系、统计埋点、广告 SDK。

#### Scenario: 权限清单审计

- **WHEN** 检查合并后的 AndroidManifest
- **THEN** 仅包含 `CAMERA`（可选）与 `READ_MEDIA_IMAGES`（可选）

---

### Requirement: 存储方式

系统 SHALL 使用 Room（SQLite）作为主存储，数据库文件位于应用私有目录。

**曾用 JSON 全量文件，因数据量增长到万级后开销线性上升而迁移**——
原方案每记一笔都要重新序列化整个文件并落盘，
如今只写变更的那一行，开销与总记录数无关。

#### Scenario: 记一笔的开销与数据量无关

- **GIVEN** 已有 1 万条记录
- **WHEN** 用户再记一笔
- **THEN** 只插入一行，耗时与只有 10 条记录时基本相同

#### Scenario: 数据位置

- **WHEN** 应用首次写入数据
- **THEN** 数据库位于应用私有目录，其他应用无法直接访问

---

### Requirement: 表索引

系统 SHALL 为高频查询建立索引：

| 索引 | 服务谁 |
|---|---|
| `records(eventId, timestamp)` | 详情页时间线 |
| `records(timestamp)` | 全局时间线、年度回顾区间查询 |
| `events(name)` | 模板导入查重 |
| `events(tag)` | 标签筛选 |

删除事件时 SHALL 由外键 `ON DELETE CASCADE` 级联清理其记录。

#### Scenario: 删除事件级联清理

- **GIVEN** 事件下有 5 条记录
- **WHEN** 删除该事件
- **THEN** 5 条记录由数据库自动删除
- **AND** 照片文件由应用层单独清理（数据库不知道文件在哪）

---

### Requirement: 首页不加载记录

首页列表 SHALL NOT 加载任何记录行，
只通过 SQL 聚合（`COUNT` / `MIN` / `MAX`）得出每个事件的
记录条数、最早时间、最近时间，再据此推导状态。

完整记录列表仅在**详情页、时间线、导出**这些确实需要的场景加载。

#### Scenario: 首页渲染开销只与事件数有关

- **GIVEN** 有 1 万条记录、30 个事件
- **WHEN** 渲染首页
- **THEN** 只执行一次 GROUP BY 聚合查询
- **AND** 开销与 1 万条这个数字无关

**理由**：原实现给每个事件都装配完整记录列表，
哪怕界面上只是要显示「共几次」。这是比换数据库本身更大的浪费。

---

### Requirement: 导出与导入

系统 SHALL 支持导出 JSON / ZIP（含照片）/ CSV，
格式与 Room 之前的 JSON 版本**保持一致**，便于外部工具处理。

导入 SHALL 自动合并，不覆盖已有数据。

#### Scenario: 导出格式保持兼容

- **WHEN** 用户导出 JSON
- **THEN** 结构与旧版一致（`version` / `events` / `records`）

---

### Requirement: 照片存储

系统 SHALL 将照片压缩到长边 1600px、JPEG 质量 85% 后存入 `filesDir/photos/`。

#### Scenario: 大图压缩

- **GIVEN** 用户选择一张 4000×3000 的照片
- **WHEN** 保存
- **THEN** 实际存储为长边 1600px 的 JPEG

---

<!-- change: detail-pagination -->

### Requirement: 详情页时间线分页

详情页 SHALL 分页加载记录，首屏加载最近 20 条，
用户可按需加载更早的记录。

系统 SHALL NOT 在打开详情页时一次性加载该事件的全部记录。

#### Scenario: 首屏只加载 20 条

- **GIVEN** 某事件有 5000 条记录
- **WHEN** 进入详情页
- **THEN** 只加载最近 20 条
- **AND** 顶部显示「最近 20 条（共 5000 条）」

#### Scenario: 加载更早的记录

- **GIVEN** 已显示最近 20 条（共 5000 条）
- **WHEN** 用户点击「加载更早的记录」
- **THEN** 追加下 20 条，显示变为 40 条

**理由**：记录类应用的数据会持续增长，
一次性全量加载在记录上千后会造成明显的卡顿与内存压力。

---

### Requirement: 统计值不随分页变动

详情页的统计信息（累计次数、平均间隔、判断基准、预测下次）
SHALL 基于**全量**记录，由 SQL 聚合得出，
SHALL NOT 依赖已加载的分页结果。

#### Scenario: 累计次数不随加载变化

- **GIVEN** 某事件共 137 条记录
- **WHEN** 进入详情页，再多次加载更早的记录
- **THEN** 「累计次数」始终显示 137
- **AND** 不随已加载条数从 20 变到 40

**理由**：分页后内存里只有 20 条，
用 `records.size` 当总数会让统计值随用户操作而跳变——
这正是分页改造要避免的问题。

平均间隔 SHALL 用「首末时间差 ÷ 间隔个数」计算，
一次聚合即可得出，SHALL NOT 逐条两两计算。
