# 任务清单：详情页分页

- [x] 1.1 DAO：observeStatsOf（COUNT + MIN + MAX）
- [x] 1.2 DAO：observeRecordsPage（首屏流）
- [x] 1.3 Repository：statusOfPaged / loadMoreRecords
- [x] 1.4 StatusCalculator.computePaged（接收聚合值）
- [x] 1.5 EventStatus 新增 recordCount
- [x] 2.1 DetailViewModel 改为分页 + loadMore
- [x] 2.2 时间线改用 itemsIndexed，相邻相减算间隔
- [x] 2.3 删除 O(n²) 的 gapBefore
- [x] 2.4 顶部提示「最近 N 条（共 M 条）」
- [x] 2.5 「加载更早的记录」按钮 + 加载指示
- [ ] 2.6 真机验证：记录多时进详情页不卡
- [ ] 2.7 更新 specs/timeline-review
- [ ] 2.8 归档本 change
