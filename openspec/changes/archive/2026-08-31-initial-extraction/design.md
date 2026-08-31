# 技术决策

## 为什么选 OpenSpec 而非其他框架

| 框架 | 判断 |
|---|---|
| OpenSpec | **选用**。轻量、brownfield 友好、纯 Markdown、工具无关 |
| Spec Kit (GitHub) | 偏绿地项目（0→1），流程重，需 Python 环境 |
| Kiro (AWS) | 锁定特定 IDE 与模型，且收费 |

决定性因素是 **brownfield 优先**：本项目已有完整代码库，
需要的是「描述变更」而非「从零构建」。OpenSpec 的
`specs/`（真理源）+ `changes/`（变更增量）双层结构正是为此设计。

## 为什么用标准目录结构 + AGENTS.md，而非 CLI

OpenSpec 的斜杠命令（`/opsx:propose` 等）只在 Claude Code、
Cursor、Codex、Copilot 等工具里原生生效，当前使用的 AI 助手不支持。

因此采用**工具无关的做法**：

- 严格遵循 OpenSpec 官方目录结构与文件格式
- 用 `AGENTS.md` 手写 AI 行为指引，替代 CLI 生成的那份
- 流程靠文档约定而非命令强制执行

**收益**：这套结构对任何 AI 工具都可读可用。
将来切换到支持斜杠命令的工具时，跑一次 `openspec update`
即可获得原生命令支持，已有的 specs 无需改动。

## 为什么 spec 与 docs 并存而非替换

`docs/` 下的文档包含大量背景推理（为什么选 Kotlin、
为什么不用 Room、为什么不做打卡），这些是**决策理由**，
不属于行为约定，塞进 spec 会让 spec 变得臃肿。

做法：spec 写「系统 SHALL 做什么」，`docs/` 保留「为什么这么做」，
并在 `project.md` 中声明冲突时以 spec 为准。

## 覆盖范围为何是核心模块优先

全部转换会产生约 900 行 spec，其中相当一部分（界面细节、
模板清单）写完就再不会看，反而稀释了真正重要的约定。

优先覆盖三类：

1. **容易改错** —— 状态计算的三级回退逻辑
2. **改了影响大** —— 数据持久化、原子写入
3. **有隐性约束** —— Glance 能力边界（用错会运行时崩溃）

其余等实际要改时再补，避免 spec 写完即过时。

## 格式约定

- 需求用 RFC 2119 关键词（SHALL / MUST / SHOULD / MAY）
- 场景用 GIVEN / WHEN / THEN，保证可测
- 每个 Requirement 至少一个 Scenario
- 关键决策附「理由」，让未来的改动者理解约束来源
