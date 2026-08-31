package com.zwz.lifelog.di

import android.content.Context
import com.zwz.lifelog.data.LifeLogRepository

/**
 * 极简依赖容器。
 * 刻意不引入 Hilt/Koin：本项目只有两三个对象需要共享，
 * 手写容器省掉 KSP 注解处理器，降低 CI 首次编译失败的风险。
 */
object ServiceLocator {

    @Volatile
    private var repository: LifeLogRepository? = null

    fun provideRepository(context: Context): LifeLogRepository =
        repository ?: synchronized(this) {
            repository ?: LifeLogRepository(context.applicationContext).also { repository = it }
        }
}
