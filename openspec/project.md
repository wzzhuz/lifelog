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
| 持久化 | 本地 JSON 文件（`filesDir/lifelog.json`） |
| 小组件 | Glance |
| 最低版本 | Android 8.0（API 26） |
| 包名 | `com.zwz.lifelog` |

**刻意不引入**：Room、Hilt/Koin、Coil/Glide。
数据量小（个人记录，千级），JSON 文件直读直写更简单可控。

## 已确认不做

以下功能明确砍掉，提 change 时不要重新引入：

- Quick Settings 磁贴（用户不需要锁屏记录）
- 到期提醒通知（用户习惯是主动打开查看，提醒是打扰）
- 云同步、账号体系
- 连续打卡、成就徽章、排行榜
- 统计埋点、广告 SDK

## 编码约定

- 状态计算集中在 `StatusCalculator`，UI 层不得自行推算
- 小组件只用 Glance 基础组件，禁止 LazyColumn / 自定义绘制 / 复杂动画
- 数据写入必须走原子流程（临时文件 → 校验 → renameTo）
- R8 混淆默认关闭：Glance 依赖反射实例化，开了有崩溃风险
- `debug.keystore` 提交进仓库以保证 CI 签名一致

## 构建与发布

- `build.yml`：push 自动编译 debug APK
- `release.yml`：手动触发，编译 release APK，可选打 tag 发布
- Release 构建会跑 `lintVital`，fatal 级 Lint 错误会中断构建；
  debug 构建不跑这个检查，所以 Lint 问题往往到出 release 包时才暴露

## 参考文档

历史文档保留在 `docs/`，spec 与文档冲突时**以 spec 为准**：

- `docs/需求设计.md` —— 原始需求（v1 快照）
- `docs/技术设计.md` —— 技术选型与实现细节
- `docs/使用说明.md` —— 用户向说明
