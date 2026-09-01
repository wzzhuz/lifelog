# OpenSpec 使用指引

本目录是需求与实现的唯一真理源。**改动需求先改这里，再动代码。**

给 AI 助手的说明：本仓库使用 OpenSpec 规范驱动开发。
以下流程用任何 AI 工具都能执行，不依赖特定的斜杠命令。

---

## 目录含义

```
openspec/
├── project.md              项目级约定，跨所有 change 生效
├── specs/                  真理源：系统「现在」是什么样
│   ├── event-management/   事件管理
│   ├── record-capture/     记录入口与摩擦层级
│   ├── status-calculation/ 状态计算（核心）
│   ├── data-persistence/   本地存储与原子写入
│   ├── home-widget/        桌面小组件
│   ├── timeline-review/    时间线与年度回顾
│   └── backup-restore/     导出导入
├── changes/                进行中的提案，每个 change 一个文件夹
└── changes/archive/        已归档的历史 change
```

**核心区分**：`specs/` 描述当前行为；`changes/` 描述将要发生的变动。
两者分开，多个改动可以并行推进而不冲突。

---

## 改动流程

### 1. 先判断要不要建 change

**需要建**：

- 新功能
- 破坏性变更（数据结构、文件格式）
- 架构调整
- 改变系统行为的优化

**不需要建**：

- 修 bug（恢复原本行为）
- 拼写、格式修正
- 非破坏性依赖升级
- 配置文件调整

### 2. 创建 change

在 `openspec/changes/<change-id>/` 下建：

| 文件 | 回答什么问题 |
|---|---|
| `proposal.md` | 为什么改？范围是什么（含「不做什么」）？ |
| `design.md` | 技术上怎么实现？有哪些取舍？ |
| `tasks.md` | 实施步骤清单（带 checkbox） |
| `specs/<domain>/spec.md` | spec 增量：行为怎么变 |

change-id 用动词开头的小写短横线命名，如 `add-photo-attachment`。

### 3. Delta spec 格式

增量描述「相对于现状变了什么」，不重写整个 spec：

```markdown
# Delta for Status Calculation

## ADDED Requirements

### Requirement: 周级别状态
系统 SHALL 支持按周而非天计算间隔。

#### Scenario: 按周设置
- **GIVEN** 用户将期望间隔设为「2 周」
- **WHEN** 计算状态
- **THEN** 基准为 14 天

## MODIFIED Requirements

### Requirement: 三档状态
（写出修改后的完整要求文本，不要只写差异）

## REMOVED Requirements

### Requirement: 某旧行为
（说明删除原因）
```

**格式硬性要求**：

- 标题用 `### Requirement: <名称>`
- 每个 Requirement 至少有一个 `#### Scenario:` 块
- 场景用 GIVEN / WHEN / THEN
- 需求文本用 SHALL / MUST / SHOULD / MAY 表达强度

### 4. 实施

按 `tasks.md` 逐项完成，完成后勾选。被打断时可从上次位置继续。

### 5. 归档

功能上线后：

```
openspec/changes/<change-id>/  →  openspec/changes/archive/<日期>-<change-id>/
```

归档时把 delta 合并回 `openspec/specs/`，使真理源始终反映线上行为。

---

## 给 AI 助手的行为准则

1. **动手前先读** `project.md` 和相关 spec，不要凭印象改代码
2. **需求有歧义就问**，不要自行假设后实现
3. **不要绕过流程**：用户说「加个功能」时，
   先确认是否要建 change，而不是直接改代码
4. **改完代码同步更新 spec**，避免 spec 与实现脱节
5. **spec 与 `docs/` 冲突时以 spec 为准**
   （`docs/` 只保留用户向的使用说明；技术决策在 change 的 design.md，
   稳定约束在 project.md，架构全貌在 README）
6. 本项目的三条铁律（低摩擦、不焦虑、数据本地）**任何改动不得违反**
