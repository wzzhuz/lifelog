# 技术设计：归档可恢复

## 根因

归档只有入口没有出口。

`Repository.archivedEvents()` **早已实现**，
但全项目检索无任何 UI 调用——写入路径做了，读取路径漏了。
结果：事件归档后从所有界面消失，无法查看、恢复、删除。

## 为什么没有报错

Kotlin 不会警告「未使用的 public 方法」。
这类缺陷只能靠人工走查或真机使用发现，
每次 push 的编译成功都掩盖了它。

## 方案

### 1. 归档列表 LEFT JOIN 计数

```sql
SELECT e.*, COUNT(r.id) AS recordCount
FROM events e LEFT JOIN records r ON r.eventId = e.id
WHERE e.isArchived = 1 GROUP BY e.id
```

用 LEFT JOIN 而非 INNER JOIN：没有记录的事件也可能是归档状态，
INNER JOIN 会把它漏掉，用户就再也找不回来。

显示条数的用途：让用户在**删除前**知道这个事件记了多少东西。

### 2. 恢复与删除

- 恢复：`setArchived(id, false)`，事件回到首页
- 删除：`deleteEvent(id)`，外键 CASCADE 清记录，
  照片文件在私有目录需应用层单独清理（Repository 已处理）

删除前二次确认，提示将连带删除多少条记录。

### 3. 归档前确认

原实现点「归档」直接生效且无提示。
改为弹确认框并写明恢复路径，避免用户以为数据丢了。

### 4. 详情页分页

替代「归档老记录」的方案：

```sql
SELECT * FROM records WHERE eventId = :eventId
ORDER BY timestamp DESC LIMIT :limit OFFSET :offset
```

**为什么分页而不是归档老记录**：
- 分页不改数据模型，年度回顾、导出、搜索都不受影响
- 归档老记录要求年度回顾跨表查询，把「看全部」变复杂——
  而使用者明确说只有年度回顾需要看全部

## 已确认不做的部分

- 记录级归档（性能收益为零：首页走聚合查询，不看记录行数）
- 自动定期归档（移动端定时不可靠，且收益为零）
