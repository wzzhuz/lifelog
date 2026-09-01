# Delta for Record Capture

> ⚠️ 本 delta 为事后补记，实际改动已直接应用到 `specs/`。

## MODIFIED Requirements

### Requirement: 搜索覆盖备注全文

搜索 SHALL 下推到数据库层执行，
不得把全部记录读进内存再遍历。

#### Scenario: 输入时不卡顿

- **GIVEN** 已有 1 万条记录
- **WHEN** 用户连续输入关键词
- **THEN** 查询防抖 150ms，停手后才执行一次
- **AND** 打字过程中界面不阻塞

**理由**：原实现是内存全表遍历，记录变多后每次输入都要扫一遍全表。
