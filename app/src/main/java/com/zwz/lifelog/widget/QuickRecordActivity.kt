package com.zwz.lifelog.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.zwz.lifelog.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * 小组件 / 快捷方式的静默记录入口：
 * 记录完成后立刻 finish，不加载 Compose UI，所以点下去是「秒记秒退」，没有界面闪烁。
 */
class QuickRecordActivity : ComponentActivity() {

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val eventId = intent?.getLongExtra(EXTRA_EVENT_ID, 0L) ?: 0L

        lifecycleScope.launch {
            var name = ""
            runCatching {
                val repo = ServiceLocator.provideRepository(this@QuickRecordActivity)
                repo.load()
                name = repo.eventById(eventId)?.name ?: ""
                repo.quickRecord(eventId)
            }
            WidgetRefresh.request(this@QuickRecordActivity)
            if (name.isNotBlank()) {
                Toast.makeText(this@QuickRecordActivity, "已记录：$name", Toast.LENGTH_SHORT).show()
            }
            finish()
        }
    }
}
