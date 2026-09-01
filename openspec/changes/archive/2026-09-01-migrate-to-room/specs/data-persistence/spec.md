# Delta for Data Persistence

> ⚠️ **说明**：本 delta 为事后补记。
> 按规范应在实施前写好、归档时合并，实际是先直接改了 `specs/`。
> 此处如实记录本 change 对 spec 的改动内容，供归档追溯。

## ADDED Requirements

### Requirement: 存储方式

系统 SHALL 使用 Room（SQLite）作为主存储。
曾用 JSON 全量文件，因数据量增长到万级后开销线性上升而迁移。

#### Scenario: 记一笔的开销与数据量无关

- **GIVEN** 已有 1 万条记录
- **WHEN** 用户再记一笔
- **THEN** 只插入一行，耗时与只有 10 条时基本相同

### Requirement: 表索引

系统 SHALL 为高频查询建立索引，删除事件时由外键级联清理记录。

#### Scenario: 删除事件级联清理

- **GIVEN** 事件下有 5 条记录
- **WHEN** 删除该事件
- **THEN** 5 条记录由数据库自动删除

### Requirement: 首页不加载记录

首页列表 SHALL NOT 加载任何记录行，
只通过 SQL 聚合（COUNT / MIN / MAX）推导状态。

#### Scenario: 首页渲染开销只与事件数有关

- **GIVEN** 有 1 万条记录、30 个事件
- **WHEN** 渲染首页
- **THEN** 只执行一次 GROUP BY 聚合查询

## REMOVED Requirements

### Requirement: 原子写入

原「先写临时文件 → 校验 → 原子改名」流程随 JSON 存储一并移除。
SQLite 自身通过事务保证写入的原子性，不再需要应用层实现。

### Requirement: 滚动备份与自动恢复

原「每次写入后维护 4 份滚动备份」移除。
**注意**：导出功能保留，用户手动导出仍是唯一的外部保险。

## 理由

原方案每记一笔都要重新序列化整个文件并落盘，
开销随数据量线性增长——一天记 10 条，三年就上万。
搜索也是内存全表遍历。这两点由使用者在试用后指出，
证实了我最初「数据量小、不用换数据库」的判断前提有误。
