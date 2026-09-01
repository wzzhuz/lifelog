# 生活手记 · 项目约定

跨所有 change 生效的通用约束。写 spec 或改代码前先读这里。

## 产品定位

**不是待办清单，不是习惯打卡，是「上次什么时候做」的外部记忆。**

三条铁律，任何改动都不得违反：

1. **记录成本必须低到没有借口** —— 最快路径是一次点击，
   不是「打开 App → 找到事件 → 记一笔」
2. **不打卡、不算连续天数、不制造焦虑** —— 晚两天换床单不是失败
3. **数据是你的** —— 不申请任何网络权限，代码里连 `INTERNET` 都不声明

## 技术栈

| 项 | 选择 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose（Material 3） |
| 架构 | 单向数据流 + Repository，无 DI 框架 |
| 持久化 | Room（SQLite）+ KSP |
| 小组件 | Glance |
| 最低版本 | Android 8.0（API 26） |
| 包名 | `com.zwz.lifelog` |

**刻意不引入**：Hilt/Koin、Coil/Glide。

**关于 Room**：初版用 JSON 全量文件，刻意避开 Room 是为了降低
CI 首次编译的失败率（引入 KSP 注解处理器会增加复杂度）。
这个取舍在数据量小时是对的，但「数据量小（千级）」的前提
在高频记录场景下不成立——一天记 10 条，三年就上万，
而 JSON 全量读写的开销随数据量**线性增长**。
因此已迁移到 Room，详见 `changes/archive/` 中的迁移记录。

仍不引入 Hilt/Koin、Coil/Glide：数据量与图片量都不需要。

## 已确认不做

以下功能明确砍掉，提 change 时不要重新引入：

- Quick Settings 磁贴（用户不需要锁屏记录）
- JSON → Room 的数据迁移代码（试用阶段无历史数据，已按用户要求移除）
- 到期提醒通知（用户习惯是主动打开查看，提醒是打扰）
- 云同步、账号体系
- 连续打卡、成就徽章、排行榜
- 统计埋点、广告 SDK
- 记录级归档、自动定期归档（首页走 SQL 聚合，
  归档老记录对性能收益为零，却会让年度回顾需要跨表查询）
- 首页宫格模式（卡片核心信息是数字，横向越窄越难读）

## 编码约定

- 状态计算集中在 `StatusCalculator`，UI 层不得自行推算
- 小组件只用 Glance 基础组件，禁止 LazyColumn / 自定义绘制 / 复杂动画
- 数据写入必须走原子流程（临时文件 → 校验 → renameTo）
- R8 混淆默认关闭：Glance 依赖反射实例化，开了有崩溃风险
- `debug.keystore` 提交进仓库以保证 CI 签名一致
- 分页场景下，统计值 SHALL 走 SQL 聚合，
  SHALL NOT 用 `records.size` 当总数（那是已加载数，会随加载跳变）
- 列表渲染 SHALL NOT 出现「每行一次全表查找」的写法，
  排序后相邻两项相减即可得到间隔

## 构建与发布

- `build.yml`：push 自动编译 debug APK
- `release.yml`：手动触发，编译 release APK，可选打 tag 发布
- Release 构建会跑 `lintVital`，fatal 级 Lint 错误会中断构建；
  debug 构建不跑这个检查，所以 Lint 问题往往到出 release 包时才暴露
- `build.yml` 的 push 触发 SHALL 覆盖**所有**分支（`'**'`）。
  曾写成 `[main, master, 'feature/**']`，导致 `fix/` 前缀分支
  推送后 Actions 静默不跑——分支在、提交也在，
  但编译列表里查不到，极易被误读成「已编译通过」
- 用 Python 拼接 Kotlin 代码后，SHALL 运行
  `python3 scripts/check_kotlin_strings.py`。
  源码里的 `\n` 会被 Python 转义成真实换行，
  导致 Kotlin 字符串字面量跨行断开，编译报错且错误信息指不到真因
  （已犯过两次，均在使用说明页）
- 改动涉及技术选型、功能增删时，SHALL 运行
  `python3 scripts/check_docs_consistency.py`。
  `docs/` 与 `README.md` 记录的是决策理由，改代码时常常只改代码，
  导致「为什么不用 Room」这类段落与实现长期矛盾
  （已发生过一次，持续了数个版本才被发现）

## 参考文档

历史文档保留在 `docs/`，spec 与文档冲突时**以 spec 为准**：

- `docs/需求设计.md` —— 原始需求（v1 快照）
- `docs/技术设计.md` —— 技术选型与实现细节
- `docs/使用说明.md` —— 用户向说明
