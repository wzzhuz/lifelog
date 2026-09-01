package com.zwz.lifelog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EventEntity::class, RecordEntity::class],
    version = 2,
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
                    .addMigrations(MIGRATION_1_2)
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
