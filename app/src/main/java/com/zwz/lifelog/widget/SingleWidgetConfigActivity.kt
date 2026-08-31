package com.zwz.lifelog.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.zwz.lifelog.di.ServiceLocator
import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.ui.theme.LifeLogTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 单事件小组件的配置页：添加小组件时弹出，让用户选择要显示哪个事件。
 */
class SingleWidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // 用户还没选就退出时，按规范返回取消结果
        setResult(
            RESULT_CANCELED,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )

        setContent {
            LifeLogTheme {
                ConfigScreen(
                    onPicked = { event -> onEventPicked(event) },
                    onCancel = { finish() }
                )
            }
        }
    }

    private fun onEventPicked(event: Event) {
        // 先落盘，保证小组件重建时也能读到
        WidgetPrefs.setEventId(this, event.id)

        val targetId = appWidgetId
        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
            if (targetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val glanceId = withContext(Dispatchers.IO) {
                    runCatching {
                        GlanceAppWidgetManager(this@SingleWidgetConfigActivity)
                            .getGlanceIdBy(targetId)
                    }.getOrNull()
                }
                if (glanceId != null) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            SingleWidget().update(this@SingleWidgetConfigActivity, glanceId)
                        }
                    }
                }
                setResult(
                    RESULT_OK,
                    Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, targetId)
                )
            }
            Toast.makeText(this@SingleWidgetConfigActivity, "已选择：${event.name}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigScreen(onPicked: (Event) -> Unit, onCancel: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val events = remember { mutableStateListOf<Event>() }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val repo = ServiceLocator.provideRepository(context)
            repo.load()
            val list = repo.allRaw().events.filter { !it.isArchived }.sortedBy { it.name }
            withContext(Dispatchers.Main) {
                events.clear()
                events.addAll(list)
                loading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择要显示的事件") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("加载中…")
            }
        } else if (events.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("还没有事件，请先打开应用添加")
            }
        } else {
            LazyColumn(modifier = Modifier.padding(pad)) {
                items(events, key = { it.id }) { ev ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                            .clickable { onPicked(ev) },
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ev.emoji, modifier = Modifier.size(28.dp))
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(ev.name, style = MaterialTheme.typography.titleMedium)
                                if (ev.tag != null) {
                                    Text(
                                        ev.tag,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
