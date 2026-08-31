# Data Persistence Specification

## Purpose

数据完全本地存储，不申请任何网络权限。用户的记录是本应用唯一的资产，
写入过程不得因崩溃或断电导致损坏。

---

## Requirements

### Requirement: 完全离线

系统 SHALL NOT 申请 `INTERNET` 权限，代码中 SHALL NOT 声明该权限。

系统 SHALL NOT 提供云同步、账号体系、统计埋点、广告 SDK。

#### Scenario: 权限清单审计

- **WHEN** 检查合并后的 AndroidManifest
- **THEN** 仅包含 `CAMERA`（可选）与 `READ_MEDIA_IMAGES`（可选）

---

### Requirement: 存储格式与位置

系统 SHALL 将数据以 JSON 存储于 `filesDir/lifelog.json`，结构如下：

```json
{
  "version": 1,
  "exportedAt": 1234567890,
  "app": "com.zwz.lifelog",
  "events": [ ... ],
  "records": [ ... ]
}
```

照片 SHALL 单独存放于 `filesDir/photos/`，记录中仅引用文件名。

#### Scenario: 数据文件位置

- **WHEN** 应用首次写入数据
- **THEN** 文件位于应用私有目录，其他应用无法直接访问

---

### Requirement: 原子写入

系统 SHALL 采用「先写临时文件 → 校验 → 原子改名」的写入流程：

```
tmpFile.writeText(json)
if (tmpFile.length() > 0) {
    file.delete()
    tmpFile.renameTo(file)      // 同分区内为原子操作
}
```

#### Scenario: 写入中途崩溃

- **GIVEN** 正在写入新的数据文件
- **WHEN** 进程在写到一半时被杀
- **THEN** 主文件保持为上一次的完整内容
- **AND** 下次启动读到的是有效数据

**理由**：renameTo 在同一分区是原子操作，
这是唯一能保证「要么全写完、要么完全没写」的方式。

---

### Requirement: 滚动备份与自动恢复

系统 SHALL 在每次成功写入后维护 `filesDir/backups/` 下最多 4 份滚动备份。

解析主文件失败时 SHALL 自动从最新备份恢复。

#### Scenario: 主文件损坏

- **GIVEN** 主数据文件解析失败
- **WHEN** 应用启动
- **THEN** 自动载入最新一份备份
- **AND** 提示用户发生了恢复

#### Scenario: 备份数量上限

- **GIVEN** `backups/` 已有 4 份备份
- **WHEN** 发生第 5 次写入
- **THEN** 最旧的一份被删除，仍保持 4 份

---

### Requirement: 照片存储

系统 SHALL 将照片压缩到长边 1600px、JPEG 质量 85% 后存入 `filesDir/photos/`。

#### Scenario: 大图压缩

- **GIVEN** 用户选择一张 4000×3000 的照片
- **WHEN** 保存
- **THEN** 实际存储为长边 1600px 的 JPEG
