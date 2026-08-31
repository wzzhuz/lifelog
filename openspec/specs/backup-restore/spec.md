# Backup Restore Specification

## Purpose

数据完全本地且无云同步，导出/导入是用户跨设备迁移和防丢失的唯一手段。

---

## Requirements

### Requirement: 多格式导出

系统 SHALL 支持导出 JSON、ZIP（含照片）、CSV 三种格式。

#### Scenario: 导出 JSON 用于迁移

- **WHEN** 用户选择导出 JSON
- **THEN** 生成包含全部事件与记录的文件

#### Scenario: 导出 ZIP 含照片

- **GIVEN** 有记录附带照片
- **WHEN** 用户选择导出 ZIP
- **THEN** 压缩包内含数据文件与 `photos/` 目录

#### Scenario: 导出 CSV 用于分析

- **WHEN** 用户选择导出 CSV
- **THEN** 生成可用 Excel 打开、适合做透视分析的表格

---

### Requirement: 导入自动合并

系统 SHALL 在导入数据时自动合并，不覆盖已有数据。

#### Scenario: 合并导入

- **GIVEN** 本地已有事件「理发」及 3 条记录
- **WHEN** 导入一份含「理发」2 条新记录与「洗牙」事件的数据
- **THEN** 「理发」共有 5 条记录
- **AND** 「洗牙」被新增
- **AND** 原有数据未丢失

**理由**：导入操作不可逆，宁可冗余也不能让用户丢数据。

---

### Requirement: 与网页版数据互通

系统 SHALL 兼容网页版（`生活手记.html`）导出的 JSON 格式。

#### Scenario: 从网页版迁移

- **GIVEN** 用户此前在网页版记录了若干数据
- **WHEN** 在原生版导入网页版导出的 JSON
- **THEN** 数据正常载入

**理由**：开发过程中先产出网页版用于快速验证需求，
两版数据格式兼容，早期在网页版记的数据不会浪费。
