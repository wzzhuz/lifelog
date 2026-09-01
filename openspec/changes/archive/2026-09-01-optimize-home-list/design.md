# 技术设计：首页三模式 + 拖拽排序

## 两个根因

### 卡片高度：色条写死，与内容不匹配

```kotlin
Box(modifier = Modifier.width(4.dp).height(84.dp).background(barColor))
```

实际卡片由内容撑到约 112~126dp，**色条比卡片矮 30~40dp**，
下方一截没颜色。这是既有的视觉瑕疵。

### 排序：字段存在但从没启用

`sortOrder` 一直存在于表结构，DAO 也按它排：

```sql
ORDER BY isPinned DESC, sortOrder ASC, name ASC
```

但 ViewModel 的实际排序是：

```kotlin
compareByDescending { it.event.isPinned }
    .thenByDescending { it.ratio }        // ← 这里是紧急度，不是 sortOrder
    .thenBy { it.event.name }
```

**没有任何界面能修改 sortOrder**，它等于白留。

## 方案

### 1. 双密度卡片

| | 紧凑 | 舒适 |
|---|---|---|
| 高度 | 72dp | 112dp |
| 进度条 | 贴底 2dp 细线 | 独立一行 5dp |
| 状态 | 8dp 圆点 | 文字 chip |
| 按钮 | 36dp | 40dp |

**高度能降下来的主因**：进度条从独立一行改为贴底细线，
不再占用纵向空间。

### 2. 三模式

| 模式 | 一屏 | 实现 |
|---|---|---|
| 紧凑 | 8~9 | 普通列表 + COMPACT |
| 舒适 | 5~6 | 普通列表 + COMFORT |
| 分组 | 7~8 | 按标签归组 + 可折叠 |

偏好存 DataStore，设置页单选。

### 3. 拖拽排序

`detectDragGesturesAfterLongPress`：

- 长按才进入拖拽，避免与点击冲突
- 被拖项 zIndex 提高 + 放大 1.03 + 透明度 0.92
- 位移超过相邻项高度一半即交换

**松手才写库**：拖拽过程中每移动一格都写库会造成大量无谓 IO。

排序逻辑同步启用 sortOrder：

```kotlin
compareByDescending { it.event.isPinned }
    .thenBy { it.event.sortOrder }     // ← 补上
    .thenByDescending { it.ratio }
    .thenBy { it.event.name }
```

### 4. 分组模式的约束

- **拖拽仅限组内**：跨组拖拽意味着改标签，是「移动」不是「排序」
- 无标签事件归入「未分类」，避免凭空消失
- 默认全部展开：点开才看到会多一层操作，
  违背「一眼看到所有该做的事」

## 一处降级

分组头原计划用 `stickyHeader` 实现吸顶，
但该 Compose BOM 版本下导入路径不确定，连续两次编译失败后
改为普通 `item`。

**分组与折叠能力保留，失去滚动时吸顶的效果。**

## 验证

- [x] 三模式切换正常，选择持久化
- [x] 紧凑模式一屏数量明显增加
- [ ] 真机验证：长按拖拽的误触率
- [ ] 真机验证：分组折叠状态持久化
