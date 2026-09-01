# 任务清单：排序分开存储

- [x] 1.1 EventEntity / Event 新增 sortInGroup
- [x] 1.2 数据库 v1 → v2 + MIGRATION_1_2
- [x] 1.3 DAO：observeActiveEventsGrouped（ORDER BY tag, sortInGroup）
- [x] 1.4 DAO：updateSortInGroups（整批事务）
- [x] 1.5 Repository：statusesLiteGrouped / saveGroupOrder
- [x] 1.6 ViewModel：按布局模式切换两个数据流
- [x] 1.7 分组拖拽只写 sortInGroup
- [ ] 1.8 真机验证：两模式排序互不干扰
- [ ] 1.9 更新 specs/event-management
- [ ] 1.10 归档本 change
