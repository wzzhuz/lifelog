package com.zwz.lifelog.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [EventEntity::class, RecordEntity::class],
    version = 1,
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
                    // 破坏性迁移：本项目只在 JSON → Room 那一步导入一次，
                    // 之后没有历史版本需要保留，升级 schema 时直接重建即可。
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
