package com.zwz.lifelog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.app.Application
import com.zwz.lifelog.di.ServiceLocator

class LifeLogApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 启动时预加载数据，避免首屏和小组件出现空窗
        val repo = ServiceLocator.provideRepository(this)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            repo.load()
        }
    }
}
