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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zwz.lifelog.data.PhotoStore
import com.zwz.lifelog.domain.model.EventStatusLite
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
    /** 从疗程详情页添加子事件，参数是所属疗程 id。 */
    onAddChild: (Long) -> Unit,
    onOpenChild: (Long) -> Unit,
    onDataChanged: () -> Unit
) {
    val status by vm.status.collectAsState()
    val isLoadingMore by vm.isLoadingMore.collectAsState()
    val children by vm.children.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var askArchive by remember { mutableStateOf(false) }
    var askEndCourse by remember { mutableStateOf(false) }
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
                                onClick = { menu = false; askArchive = true }
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
                                // 吃药这类几小时一次的事件，写死「天前」等于没说，
                                // 24 小时内换成小时。
                                val last = s.lastTimestamp
                                val hours = if (last == null) null
                                else (System.currentTimeMillis() - last) / 3_600_000L
                                val useHours = hours != null && hours < 24
                                Text(
                                    when {
                                        last == null -> "—"
                                        useHours -> "$hours"
                                        else -> "${s.daysAgo}"
                                    },
                                    style = MaterialTheme.typography.headlineLarge
                                )
                                Text(
                                    if (useHours) "小时前" else "天前",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!s.event.note.isNullOrBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(s.event.note, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(Modifier.height(14.dp))

                        Row {
                            // 必须用 recordCount 而非 records.size。
                            // records 是分页后已加载的部分（首屏 20 条），
                            // 用它会导致「累计次数」随加载而变动——
                            // 这正是详情页分页要避免的问题，
                            // 统计值必须由 SQL 聚合给出真实总数。
                            StatBox("${s.recordCount}", "累计次数", Modifier.weight(1f))
                            Spacer(Modifier.width(8.dp))

                            // 按需事件（看病、针灸）：没有周期，展示「判断基准」
                            // 会被读成「系统认为你该多久看一次病」，与不制造焦虑的定位冲突。
                            // 这类事件只回答事实：记过几次、第一次是什么时候。
                            if (s.event.isOnDemand) {
                                StatBox(
                                    s.firstTimestamp?.let { TimeFormatter.dateOnly(it) } ?: "—",
                                    "首次记录", Modifier.weight(1f)
                                )
                            } else {
                                StatBox(
                                    if (s.avgGapMillis != null) TimeFormatter.duration(s.avgGapMillis) else "—",
                                    "平均间隔", Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                StatBox(
                                    // 频次型看「每日几次」，疗程看「挂了几个子事件」，
                                    // 只有周期型才有「判断基准」这回事
                                    value = when {
                                        s.event.timesPerDay != null -> "${s.event.timesPerDay} 次"
                                        s.event.isCourse -> "${s.children.size} 项"
                                        else -> "${s.baselineDays} 天"
                                    },
                                    label = when {
                                        s.event.timesPerDay != null -> "每日"
                                        s.event.isCourse -> "子事件"
                                        else -> "判断基准"
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // 同样用 recordCount：判断的是「这个事件总共记了几次」，
                        // 而不是「屏幕上加载了几条」
                        // 按需事件不产出预测值，这里一并跳过
                        if (s.predictedNextMillis != null && s.recordCount >= 2 && !s.event.isOnDemand) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                // 基准不足 2 天的事件（吃药、测血糖）只给日期等于没给，
                                // 得带上具体时刻才看得出「什么时候该吃下一顿」
                                "按你的节奏，预计下次：${
                                    if (s.baselineDays < 2) TimeFormatter.short(s.predictedNextMillis)
                                    else TimeFormatter.dateOnly(s.predictedNextMillis)
                                }",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 疗程本身不承载记录：记一笔的入口换成子事件列表
            if (s.event.isCourse) {
                item {
                    CourseSection(
                        children = children,
                        onAddChild = { onAddChild(s.event.id) },
                        onQuickRecord = { childId ->
                            vm.quickRecordChild(childId) { name ->
                                onDataChanged()
                                scope.launch { snack.showSnackbar("已记录：$name") }
                            }
                        },
                        onOpenChild = onOpenChild,
                        onEndCourse = { askEndCourse = true }
                    )
                }
            } else {
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
            }

            if (s.event.isCourse) {
                // 时间线对疗程没有意义，记录都在子事件上
            } else if (s.records.isEmpty()) {
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
                    val shown = s.records.size
                    val total = s.recordCount
                    Text(
                        if (shown < total) "最近 $shown 条（共 $total 条）"
                        else "全部 $total 条记录",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                    )
                }
                // 记录已由 ViewModel 按时间倒序给出，无需再排序。
                // 间隔用相邻两项直接相减——旧实现是每行 indexOfFirst 全表查找，
                // 那是 O(n²)，5000 条时会有 2500 万次比较，直接卡死渲染。
                itemsIndexed(s.records, key = { _, r -> r.id }) { i, rec ->
                    val older = s.records.getOrNull(i + 1)
                    RecordRow(
                        rec = rec,
                        gapMillis = if (older != null) rec.timestamp - older.timestamp else null,
                        onClick = { onEditRecord(s.event.id, rec.id) },
                        onDelete = {
                            vm.deleteRecord(rec) {
                                onDataChanged()
                                scope.launch { snack.showSnackbar("已删除一条记录") }
                            }
                        }
                    )
                }
                if (vm.hasMore(s)) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            } else {
                                OutlinedButton(onClick = { vm.loadMore() }) {
                                    Text("加载更早的记录")
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(60.dp)) }
            }
        }
    }

    // 归档前确认：归档曾是无提示的单向操作，
    // 用户不知道去哪找回，这里明确告知恢复路径。
    if (askArchive) {
        AlertDialog(
            onDismissRequest = { askArchive = false },
            title = { Text("归档「${s?.event?.name ?: ""}」？") },
            text = {
                Text(
                    "归档后不显示在首页，但记录会完整保留。\n\n" +
                        "之后可在「设置 → 已归档事件」中查看、恢复或彻底删除。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askArchive = false
                    vm.setArchived(true) { onDataChanged(); onBack() }
                }) { Text("归档") }
            },
            dismissButton = {
                TextButton(onClick = { askArchive = false }) { Text("取消") }
            }
        )
    }

    // 结束疗程：父子一并归档，确认前先算出会带走多少数据
    if (askEndCourse) {
        var summary by remember { mutableStateOf("正在统计…") }
        LaunchedEffect(Unit) {
            val n = vm.activeChildCount()
            val recs = vm.courseRecordCount()
            summary = "将归档 $n 个子事件，共 $recs 条记录。"
        }
        AlertDialog(
            onDismissRequest = { askEndCourse = false },
            title = { Text("结束「${s?.event?.name ?: ""}」？") },
            text = {
                Text(
                    "$summary\n\n结束后不再显示在首页，记录会完整保留。\n" +
                        "之后可在「设置 → 已归档事件」中整体恢复。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askEndCourse = false
                    scope.launch {
                        vm.endCourse()
                        onDataChanged()
                        snack.showSnackbar("疗程已结束")
                        onBack()
                    }
                }) { Text("结束疗程") }
            },
            dismissButton = {
                TextButton(onClick = { askEndCourse = false }) { Text("取消") }
            }
        )
    }
}

/**
 * 疗程的子事件区。
 *
 * 疗程本身不记任何东西，用户在这里看到的是「这次开的几种药今天吃了几顿」，
 * 以及一条随时可点的「结束疗程」。
 */
@Composable
private fun CourseSection(
    children: List<EventStatusLite>,
    onAddChild: () -> Unit,
    onQuickRecord: (Long) -> Unit,
    onOpenChild: (Long) -> Unit,
    onEndCourse: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "子事件",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onAddChild) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加")
            }
        }

        if (children.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "还没有子事件\n点「添加」把这次开的药加进来",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            children.forEach { c ->
                ChildRow(
                    status = c,
                    onClick = { onOpenChild(c.event.id) },
                    onQuickRecord = { onQuickRecord(c.event.id) }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onEndCourse,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp)
        ) { Text("结束疗程") }
        Text(
            "疗程结束后整体归档，记录完整保留，可随时恢复。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun ChildRow(
    status: EventStatusLite,
    onClick: () -> Unit,
    onQuickRecord: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.5.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(status.event.emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(status.event.name, style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    status.todayProgress
                        ?: (if (status.lastTimestamp == null) "还没记录" else "已记 ${status.recordCount} 次"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onQuickRecord,
                modifier = Modifier.size(36.dp),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = "记一笔",
                    modifier = Modifier.size(18.dp))
            }
        }
    }
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
