package com.zwz.lifelog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EventEntity::class, RecordEntity::class],
    version = 4,
    exportSchema = false
)
abstract class LifeLogDatabase : RoomDatabase() {

    abstract fun dao(): LifeLogDao

    companion object {
        private const val DB_NAME = "lifelog.db"

        @Volatile
        private var INSTANCE: LifeLogDatabase? = null

        fun get(context: Context): LifeLogDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    LifeLogDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    // 兜底：将来若从更老的版本升级且没写对应 migration，
                    // 才走破坏性重建。当前使用者确认数据为模拟数据可弃，
                    // 但保留这个分支，避免将来有真实数据时被静默清空。
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

/**
 * 1 → 2：新增 sortInGroup 列。
 *
 * 列表模式与分组模式的排序语义不同，共用一个 sortOrder 字段时
 * 会互相污染，因此拆成两个字段。
 *
 * 新增列的初值直接取现有 sortOrder，
 * 这样升级后两种模式的顺序表现一致，用户不会感到突变。
 */
val MIGRATION_1_2 = androidx.room.migration.Migration(1, 2) {
    it.execSQL("ALTER TABLE events ADD COLUMN sortInGroup INTEGER NOT NULL DEFAULT 0")
    it.execSQL("UPDATE events SET sortInGroup = sortOrder")
}

/**
 * 2 → 3：事件层级与类型。
 *
 * 新增三列全部带默认值，既有事件一律是 `PERIODIC` 且无父事件，
 * 升级前后行为完全一致——用户不会感到突变。
 */
val MIGRATION_2_3 = androidx.room.migration.Migration(2, 3) {
    it.execSQL("ALTER TABLE events ADD COLUMN parentId INTEGER")
    it.execSQL("ALTER TABLE events ADD COLUMN timesPerDay INTEGER")
    it.execSQL("ALTER TABLE events ADD COLUMN kind TEXT NOT NULL DEFAULT 'PERIODIC'")
}

/**
 * 3 → 4：事件的派生列 + parentId 索引。
 *
 * 四列全部带默认值，加列本身是安全的（旧行取默认值，行为不变）。
 * 但**加完之后必须重算一次**：否则已有记录的事件会显示成「0 次、从没记过」，
 * 直到用户再记一笔才恢复。重算由 `LifeLogRepository` 在迁移后调用
 * [com.zwz.lifelog.data.LifeLogRepository.recomputeDerived] 完成。
 *
 * 记录表不动，因此**没有任何数据丢失风险**。
 */
val MIGRATION_3_4 = androidx.room.migration.Migration(3, 4) {
    it.execSQL("ALTER TABLE events ADD COLUMN firstTs INTEGER")
    it.execSQL("ALTER TABLE events ADD COLUMN lastTs INTEGER")
    it.execSQL("ALTER TABLE events ADD COLUMN recordCount INTEGER NOT NULL DEFAULT 0")
    it.execSQL("ALTER TABLE events ADD COLUMN lastRecordDay INTEGER")
    it.execSQL("ALTER TABLE events ADD COLUMN todayCount INTEGER NOT NULL DEFAULT 0")
    it.execSQL("CREATE INDEX IF NOT EXISTS index_events_parentId ON events (parentId)")
}
