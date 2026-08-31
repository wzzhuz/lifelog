# Home Widget Specification

## Purpose

把「记一笔」的摩擦压到一次点击。桌面小组件是 L0 层级入口，
是本应用区别于网页版和普通笔记应用的关键能力。

---

## Requirements

### Requirement: 两种形态都提供

系统 SHALL 提供列表型与单事件型两种桌面小组件。

| 形态 | 尺寸 | 行为 |
|---|---|---|
| 列表型 | 4×2 | 显示最多 5 个事件，点击打开应用 |
| 单事件型 | 2×2 | 大字显示距今天数，**点击直接记一笔** |

#### Scenario: 单事件型一步记录

- **GIVEN** 桌面放置了「理发」的单事件小组件
- **WHEN** 用户点击它
- **THEN** 写入一条记录
- **AND** 不弹出应用界面（通过透明 Activity 静默完成）

#### Scenario: 列表型跳转

- **WHEN** 用户点击列表型小组件
- **THEN** 打开应用主界面

---

### Requirement: 列表型排序规则

列表型小组件 SHALL 最多展示 5 个事件，排序优先级为：
钉选事件优先，其余按「最久没做」降序。

#### Scenario: 钉选项置顶

- **GIVEN** 用户钉选了「换床单」
- **WHEN** 小组件刷新
- **THEN** 「换床单」排在最前

---

### Requirement: 单事件型绑定配置

添加单事件型小组件时 SHALL 弹出配置页，让用户选择要显示哪个事件。

绑定关系 SHALL 存于 SharedPreferences。

#### Scenario: 配置流程

- **WHEN** 用户从桌面添加单事件型小组件
- **THEN** 弹出事件选择列表
- **AND** 选中后小组件立即显示该事件的距今天数

#### Scenario: 未选择事件

- **GIVEN** 小组件未绑定任何事件
- **WHEN** 渲染
- **THEN** 显示「未选择事件 / 重新添加可绑定」

**实现说明**：绑定状态用 SharedPreferences 而非 Glance 的 DataStore state。
Glance 状态 API 在不同版本间签名有差异，且 lambda 返回值类型要求严格；
只存一个 Long 用 SharedPreferences 行为更确定。

---

### Requirement: Glance 能力边界约束

小组件实现 SHALL 仅使用 Glance 支持的基础组件。

具体禁止：LazyColumn、自定义绘制、复杂动画。

Glance 不是完整 Compose，使用上述能力会导致运行时崩溃。

#### Scenario: 列表渲染

- **WHEN** 渲染列表型小组件的 5 行事件
- **THEN** 使用 Row + Column 静态布局，而非 LazyColumn

---

### Requirement: 刷新时机

系统 SHALL 在记录写入后主动触发小组件更新。

#### Scenario: 记录后立即刷新

- **GIVEN** 用户在应用内记了一笔
- **WHEN** 写入完成
- **THEN** 桌面小组件的距今天数同步更新为 0
