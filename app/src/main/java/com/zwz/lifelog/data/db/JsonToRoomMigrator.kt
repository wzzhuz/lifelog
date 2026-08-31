package com.zwz.lifelog.data.db

import android.content.Context
import android.util.Log
import com.zwz.lifelog.data.JsonStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * JSON → Room 的一次性迁移。
 *
 * 迁移发生在首次升级到 Room 版本、应用第一次启动时：
 * 读出旧的 `lifelog.json`，连同 id 一起写进数据库，
 * 然后把原文件改名为 `lifelog.json.migrated` 留档。
 *
 * **为什么不删除原文件**：迁移是不可逆操作，一旦中途出错
 * 或新版本有 bug，留着原文件还能退回去。改名而非删除，
 * 既不占额外逻辑，又保留后悔药。
 */
object JsonToRoomMigrator {

    private const val TAG = "JsonToRoomMigrator"
    private const val PREFS = "lifelog_migration"
    private const val KEY_MIGRATED = "json_to_room_done"

    /**
     * 若尚未迁移且存在旧 JSON 文件，则导入数据库。
     *
     * 可重复调用：迁移成功后写入标记，之后直接返回。
     *
     * @return 导入的记录条数；未发生迁移时返回 0
     */
    suspend fun migrateIfNeeded(context: Context, dao: LifeLogDao): Int =
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (prefs.getBoolean(KEY_MIGRATED, false)) return@withContext 0

            val jsonFile = File(context.filesDir, "lifelog.json")
            if (!jsonFile.exists()) {
                // 全新安装，没有旧数据，直接标记完成
                prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
                return@withContext 0
            }

            return@withContext try {
                val text = jsonFile.readText()
                if (text.isBlank()) {
                    prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
                    return@withContext 0
                }

                val snap = JsonStore(context).parse(text)

                // 先写事件：保留原 id，记录要靠 eventId 关联
                snap.events.forEach { dao.upsertEvent(it.toEntity()) }
                // 再写记录：保留原 id 与 eventId
                snap.records.forEach { dao.insertRecord(it.toEntity()) }

                // 留档后改名，避免下次启动重复导入
                val archived = File(context.filesDir, "lifelog.json.migrated")
                if (archived.exists()) archived.delete()
                jsonFile.renameTo(archived)

                prefs.edit().putBoolean(KEY_MIGRATED, true).apply()

                Log.i(TAG, "迁移完成：${snap.events.size} 个事件 / ${snap.records.size} 条记录")
                snap.records.size
            } catch (e: Exception) {
                // 迁移失败不要写标记，下次启动再试一次；
                // 原文件也没动，数据不会丢。
                Log.e(TAG, "迁移失败，保留原文件待下次重试", e)
                0
            }
        }
}
