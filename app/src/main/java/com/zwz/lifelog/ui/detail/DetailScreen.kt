package com.zwz.lifelog.ui.detail

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zwz.lifelog.data.PhotoStore
import com.zwz.lifelog.domain.model.Record
import com.zwz.lifelog.ui.component.freshnessColor
import com.zwz.lifelog.ui.component.freshnessLabel
import com.zwz.lifelog.util.TimeFormatter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    vm: DetailViewModel,
    onBack: () -> Unit,
    onEditEvent: (Long) -> Unit,
    onAddRecord: (Long) -> Unit,
    onEditRecord: (Long, Long) -> Unit,
    onDataChanged: () -> Unit
) {
    val status by vm.status.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val s = status

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text(s?.event?.name ?: "事件") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    s?.let {
                        IconButton(onClick = { menu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(
                                text = { Text(if (it.event.isPinned) "取消钉选" else "钉到顶部") },
                                onClick = {
                                    menu = false
                                    vm.togglePin { onDataChanged() }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("编辑事件") },
                                onClick = { menu = false; onEditEvent(it.event.id) }
                            )
                            DropdownMenuItem(
                                text = { Text("归档") },
                                onClick = {
                                    menu = false
                                    vm.setArchived(true) { onDataChanged(); onBack() }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除事件", color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    menu = false
                                    vm.deleteEvent { onDataChanged(); onBack() }
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { pad ->
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) {
                Text("事件不存在")
            }
            return@Scaffold
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(pad)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.event.emoji, style = MaterialTheme.typography.headlineLarge)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(s.event.name, style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.width(8.dp))
                                    val c = freshnessColor(s.freshness)
                                    Box(
                                        Modifier.clip(RoundedCornerShape(6.dp))
                                            .background(c.copy(alpha = .14f))
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Text(freshnessLabel(s.freshness),
                                            style = MaterialTheme.typography.labelMedium, color = c)
                                    }
                                }
                                if (s.event.tag != null) {
                                    Text(s.event.tag, style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    s.daysSince?.toString() ?: "—",
                                    style = MaterialTheme.typography.headlineLarge
                                )
                                Text("天前", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (!s.event.note.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(s.event.note, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(Modifier.height(14.dp))

                        Row {
                            StatBox("${s.records.size}", "累计次数", Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))
                            StatBox(
                                if (s.avgGapMillis != null) TimeFormatter.duration(s.avgGapMillis) else "—",
                                "平均间隔", Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            StatBox("${s.baselineDays} 天", "判断基准", Modifier.weight(1f))
                        }

                        if (s.predictedNextMillis != null && s.records.size >= 2) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "按你的节奏，预计下次：${TimeFormatter.dateOnly(s.predictedNextMillis)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            vm.quickRecord { name ->
                                onDataChanged()
                                scope.launch { snack.showSnackbar("已记录：$name") }
                            }
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("记现在")
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = { onAddRecord(s.event.id) },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("带备注记")
                    }
                }
            }

            if (s.records.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center) {
                        Text("还没有记录\n点上面两个按钮记第一笔",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item {
                    Text("全部记录", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                }
                items(s.records.sortedByDescending { it.timestamp }, key = { it.id }) { rec ->
                    RecordRow(
                        rec = rec,
                        gapMillis = gapBefore(s.records, rec),
                        onClick = { onEditRecord(s.event.id, rec.id) },
                        onDelete = {
                            vm.deleteRecord(rec) {
                                onDataChanged()
                                scope.launch { snack.showSnackbar("已删除一条记录") }
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(60.dp)) }
            }
        }
    }
}

private fun gapBefore(recordsAsc: List<Record>, r: Record): Long? {
    val idx = recordsAsc.indexOfFirst { it.id == r.id }
    if (idx <= 0) return null
    return r.timestamp - recordsAsc[idx - 1].timestamp
}

@Composable
private fun StatBox(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium,
                maxLines = 1, color = MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RecordRow(
    rec: Record,
    gapMillis: Long?,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.5.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier.size(9.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(top = 6.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(TimeFormatter.full(rec.timestamp), style = MaterialTheme.typography.titleMedium)
                Text(
                    (if (gapMillis != null) "距上次 ${TimeFormatter.duration(gapMillis)} · " else "首次记录 · ") +
                            TimeFormatter.agoWithDate(rec.timestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!rec.note.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(rec.note, style = MaterialTheme.typography.bodyMedium)
                }
                if (rec.photoName != null) {
                    Spacer(Modifier.height(8.dp))
                    PhotoThumb(rec.photoName)
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "删除",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PhotoThumb(name: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bmp = remember(name) { PhotoStore(context).loadBitmap(name) }
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    }
}
