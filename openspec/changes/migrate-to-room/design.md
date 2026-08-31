# 技术设计：持久化迁移到 Room

## 现状

`JsonStore` 把全部数据装在两个 List 里，靠一个 JSON 文件落地：

```kotlin
data class Snapshot(val events: List<Event>, val records: List<Record>)
```

任何一次写入都走 `persistLocked()` → 全量序列化 → 写整个文件。
搜索则在内存里遍历所有事件的全部记录做字符串匹配。

**两个问题都随数据量线性增长。**

## 方案

### 1. 表结构与索引

```kotlin
@Entity(tableName = "events", indices = [Index("name"), Index("tag")])
data class EventEntity(...)      // 与 Event 字段一一对应

@Entity(tableName = "records",
        foreignKeys = [ForeignKey(..., onDelete = CASCADE)],
        indices = [Index("eventId", "timestamp"), Index("timestamp")])
data class RecordEntity(...)     // 与 Record 字段一一对应
```

索引选择：

| 索引 | 服务谁 |
|---|---|
| `records(eventId, timestamp)` | 详情页时间线，最高频 |
| `records(timestamp)` | 全局时间线、年度回顾、区间查询 |
| `events(name)` | 模板导入查重 |
| `events(tag)` | 标签筛选 |

外键用 `CASCADE`：删事件时数据库自动清记录，不用手写。

### 2. 首页不加载记录（关键设计）

新增 `EventStatusLite`——**不含 records 列表**：

```kotlin
data class EventStatusLite(
    val event: Event,
    val recordCount: Int,      // ← 只需个数，不需要列表
    val lastTimestamp: Long?,
    ...
)
```

一次 `GROUP BY` 拿全部事件的统计值：

```sql
SELECT eventId, COUNT(*) AS `count`,
       MIN(timestamp) AS firstTs, MAX(timestamp) AS lastTs
FROM records GROUP BY eventId
```

然后 `StatusCalculator.computeLite()` 用这三个值推导状态：
平均间隔 = `(last - first) / (count - 1)`，与原逻辑等价。

**开销从「与总记录数相关」变成「与事件数相关」。**

### 3. 搜索下推到 SQL

```kotlin
@Query("SELECT DISTINCT eventId FROM records WHERE note LIKE '%' || :kw || '%'")
suspend fun searchEventIdsByNote(kw: String): List<Long>
```

防抖 150ms，停手才查。事件名和标签仍走内存匹配（只有几十个，立即响应），
备注全文由 SQL 异步补上——**打字不卡，结果不漏**。

### 4. 数据迁移

`JsonToRoomMigrator` 在首次启动时执行：

```
读 lifelog.json → 保留原 id 写入数据库 → 改名 .migrated → 写完成标记
```

**为什么改名不删除**：迁移不可逆，留着原文件是最后一道后悔药。
**为什么失败不写标记**：下次启动会再试一次，而原文件也没动。

### 5. 接口签名保持不变

`LifeLogRepository` 的方法签名尽量不动，
换掉内部实现即可。这样 30+ 个 UI 文件无需大改，降低风险。

## 考虑过的其他方案

| 方案 | 优点 | 为什么没选 |
|---|---|---|
| 只修两处浪费，不换库（我上次的建议） | 改动小、零风险 | **前提错了**——没算搜索开销，<br>也没考虑数据量会到万级 |
| 分片存储（按月拆 JSON） | 不用改架构 | 跨片查询复杂，搜索仍要遍历 |
| 直接用 SQLiteOpenHelper | 无需注解处理器 | 要手写 SQL 与对象映射，<br>类型安全全靠自觉 |

## 取舍

- **牺牲**：引入 KSP 注解处理器，增加首次编译的复杂度
  （这正是当初避开 Room 的原因，那个阶段的判断是对的）
- **换取**：写入与搜索的开销不再随数据量增长

现在是换的正确时机：数据量还小，迁移快；
代码结构还清晰，改起来不伤筋动骨。

## 风险

| 风险 | 缓解 |
|---|---|
| KSP 版本与 Kotlin 不匹配导致编译失败 | 已查证：Kotlin 2.3.21 → KSP 2.3.21-2.0.2 |
| 迁移中途出错丢数据 | 原文件改名留档 + 失败重试 |
| 新实现与旧行为不一致 | 平迁，字段一一对应，计算逻辑等价 |

## 验证

- [ ] 分支编译通过
- [ ] 旧数据完整迁移（事件数、记录数对得上）
- [ ] 1 万条压测：记一笔、搜索、进设置页耗时
- [ ] 确认无回退后合入主线
