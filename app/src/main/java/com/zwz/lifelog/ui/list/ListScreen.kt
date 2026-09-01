package com.zwz.lifelog.ui.list

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zwz.lifelog.domain.model.Templates
import com.zwz.lifelog.ui.component.EventCard
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.text.font.FontWeight
import com.zwz.lifelog.data.HomeLayoutMode
import com.zwz.lifelog.domain.model.EventStatusLite
import com.zwz.lifelog.ui.component.CardDensity
import com.zwz.lifelog.ui.component.DragSortLazyColumn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    vm: ListViewModel,
    onOpenDetail: (Long) -> Unit,
    onCreateEvent: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenSettings: () -> Unit,
    onDataChanged: () -> Unit,
    layoutMode: HomeLayoutMode = HomeLayoutMode.COMPACT,
    collapsedTags: Set<String> = emptySet(),
    onToggleCollapse: (String) -> Unit = {}
) {
    val state by vm.ui.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(layoutMode) { vm.onLayoutMode(layoutMode) }
    val density = when (layoutMode) {
        HomeLayoutMode.COMPACT -> CardDensity.COMPACT
        HomeLayoutMode.COMFORT, HomeLayoutMode.GROUPED -> CardDensity.COMFORT
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = { Text("生活手记", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                actions = {
                    IconButton(onClick = onOpenTimeline) {
                        Icon(Icons.Default.History, contentDescription = "时间线")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onCreateEvent,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "新建事件")
            }
        }
    ) { pad ->
        Column(modifier = Modifier.padding(pad).fillMaxSize()) {

            TextField(
                value = state.keyword,
                onValueChange = vm::onKeyword,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                placeholder = { Text("搜事件名、备注内容…") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                )
            )

            // 「随手一点」：钉选项，点即记
            val pinned = state.all.filter { it.event.isPinned }
            if (pinned.isNotEmpty()) {
                Text(
                    "随手一点",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 6.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pinned, key = { it.event.id }) { s ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            onClick = {
                                vm.quickRecord(s.event.id) { name ->
                                    onDataChanged()
                                    scope.launch {
                                        val r = snack.showSnackbar("已记录：$name", actionLabel = "查看")
                                        if (r == SnackbarResult.ActionPerformed) onOpenDetail(s.event.id)
                                    }
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(s.event.emoji)
                                Spacer(Modifier.width(6.dp))
                                Text(s.event.name, style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (s.daysSince == null) "未记" else "${s.daysSince}天",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            // 筛选：状态与分类是**两个独立的维度**，不是一组互斥选项。
            // 曾经把它们放在同一行、用同样的样式，用户会理所当然认为互斥——
            // 看到「不限」和「汽车」同时高亮就觉得是 bug。
            // 改为两行并各自标注，从视觉上区分维度。
            FilterRow(label = "状态") {
                Chip("不限", state.filter == Filter.ALL) { vm.onFilter(Filter.ALL) }
                Chip("该做了", state.filter == Filter.DUE) { vm.onFilter(Filter.DUE) }
                Chip("待记录", state.filter == Filter.NONE) { vm.onFilter(Filter.NONE) }
                Chip("已钉选", state.filter == Filter.PINNED) { vm.onFilter(Filter.PINNED) }
            }

            // 分类行从**全部事件**推导，而不是当前可见事件。
            // 否则选了「汽车」之后分类行只剩「汽车」一个可点，很怪。
            if (state.allTags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FilterRow(label = "分类") {
                    Chip("不限", state.tagFilter == null) { vm.onTag(null) }
                    state.allTags.forEach { t ->
                        Chip(t, state.tagFilter == t) {
                            vm.onTag(if (state.tagFilter == t) null else t)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            if (state.visible.isEmpty()) {
                EmptyState(hasData = state.all.isNotEmpty()) { vm.showTemplatePicker() }
            } else if (layoutMode == HomeLayoutMode.GROUPED) {
                // 分组模式：按标签归组，吸顶头 + 可折叠
                GroupedList(
                    visible = state.visible,
                    collapsed = collapsedTags,
                    density = density,
                    onToggleCollapse = { onToggleCollapse(it) },
                    onSaveOrder = { ids -> vm.saveGroupOrder(ids) },
                    onOpenDetail = onOpenDetail,
                    onQuickRecord = { id ->
                        vm.quickRecord(id) { name ->
                            onDataChanged()
                            scope.launch {
                                snack.showSnackbar("已记录：$name", actionLabel = "撤销")
                            }
                        }
                    }
                )
            } else {
                // 列表模式（紧凑 / 舒适）：支持长按拖拽排序
                //
                // 关键：onReorder 只改内存里的顺序，松手才写库。
                // 拖拽过程中每移动一格都写库会造成大量无谓 IO。
                val ordered: androidx.compose.runtime.snapshots.SnapshotStateList<EventStatusLite> =
                    remember { mutableStateListOf<EventStatusLite>() }
                // 正在保存时不要同步：保存会触发 Flow 发射，
                // 若此时用数据库回传的（可能滞后的）顺序覆盖本地，
                // 刚拖好的顺序会被冲掉。
                var saving by remember { mutableStateOf(false) }

                LaunchedEffect(state.visible, saving) {
                    if (saving) return@LaunchedEffect
                    val want: List<Long> = state.visible.map { it.event.id }
                    if (ordered.map { it.event.id } != want) {
                        ordered.clear()
                        ordered.addAll(state.visible)
                    }
                }

                DragSortLazyColumn<EventStatusLite>(
                    items = ordered,
                    keyOf = { it.event.id },
                    onReorder = { from, to ->
                        if (from in ordered.indices && to in ordered.indices) {
                            val moved = ordered.removeAt(from)
                            ordered.add(to, moved)
                        }
                    },
                    onDragEnd = {
                        saving = true
                        vm.saveOrder(ordered.map { it.event.id })
                        saving = false
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(
                        if (layoutMode == HomeLayoutMode.COMPACT) 8.dp else 10.dp
                    )
                ) { s, _, _ ->
                    EventCard(
                        status = s,
                        density = density,
                        onClick = { onOpenDetail(s.event.id) },
                        onQuickRecord = {
                            vm.quickRecord(s.event.id) { name ->
                                onDataChanged()
                                scope.launch {
                                    snack.showSnackbar("已记录：$name", actionLabel = "撤销")
                                }
                            }
                        }
                    )
                    if (layoutMode == HomeLayoutMode.COMPACT) {
                        Spacer(Modifier.height(0.dp))
                    }
                }
            }
        }
    }

    if (state.showTemplatePicker) {
        TemplateSheet(vm = vm, onDone = { n ->
            onDataChanged()
            scope.launch { snack.showSnackbar("已导入 $n 个事件") }
        })
    }
}

@Composable
private fun FilterRow(
    label: String,
    items: @androidx.compose.runtime.Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(34.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { items() }
        }
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        shape = RoundedCornerShape(50),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun EmptyState(hasData: Boolean, onPickTemplate: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\uD83D\uDCD2", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(12.dp))
        if (hasData) {
            Text("没有匹配的记录", style = MaterialTheme.typography.bodyLarge)
            Text("换个关键词试试", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("还没有任何事件", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                "点右下角 + 新建，或者从常用模板里挑几个：",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onPickTemplate) { Text("从模板导入") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateSheet(vm: ListViewModel, onDone: (Int) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selected = remember { mutableStateSetOf<String>() }

    ModalBottomSheet(onDismissRequest = vm::hideTemplatePicker, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("选择常用的事件", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text("已存在的同名事件不会被重复导入",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.height(320.dp)) {
                items(Templates.ALL) { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = t.name in selected,
                            onCheckedChange = { on ->
                                if (on) selected.add(t.name) else selected.remove(t.name)
                            }
                        )
                        Text(t.emoji, modifier = Modifier.size(32.dp).padding(top = 8.dp))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                t.tag + (if (t.targetDays != null) " · 约 ${t.targetDays} 天" else " · 无固定周期"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { vm.importTemplates(selected.toSet(), onDone) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("导入 ${selected.size} 个") }
        }
    }
}

/**
 * 分组模式的列表。
 *
 * 三个设计点：
 *
 * **1. 默认全部展开**
 * 点开才看到事件会多一层操作，违背「一眼看到所有该做的事」。
 * 折叠权交给用户。
 *
 * **2. 拖拽仅限组内**
 * 跨组拖拽意味着改分类标签——那是「移动」不是「排序」。
 * 实现上把分组头也作为列表项，用 [GroupRow] 密封类型表达，
 * 拖拽时禁止越过任何分组头。
 *
 * **3. 无标签归入「未分类」**
 * 避免事件凭空消失在分组视图里。
 */
@Composable
private fun GroupedList(
    visible: List<EventStatusLite>,
    collapsed: Set<String>,
    density: CardDensity,
    onToggleCollapse: (String) -> Unit,
    onOpenDetail: (Long) -> Unit,
    onQuickRecord: (Long) -> Unit,
    onSaveOrder: (List<Long>) -> Unit
) {
    val groups = remember(visible) {
        visible
            .groupBy { it.event.tag?.takeIf { t -> t.isNotBlank() } ?: "未分类" }
            .toList()
            .sortedBy { it.first }
    }

    // 扁平化：[头, 事件…, 头, 事件…]
    // 拖拽在这个扁平列表上进行，分组头是天然的边界。
    val flat = remember(groups, collapsed) {
        val out = mutableListOf<GroupRow>()
        groups.forEach { (tag, list) ->
            out.add(GroupRow.Header(tag, list.size, tag in collapsed))
            if (tag !in collapsed) list.forEach { out.add(GroupRow.Item(it)) }
        }
        out
    }

    // 每个位置属于第几组，用于判断能否跨组移动
    val groupIndexOf = remember(flat) {
        var g = -1
        flat.map {
            if (it is GroupRow.Header) g += 1
            g
        }
    }

    val rows = remember { mutableStateListOf<GroupRow>() }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(flat, saving) {
        if (saving) return@LaunchedEffect
        if (rows.toList() != flat) {
            rows.clear()
            rows.addAll(flat)
        }
    }

    DragSortLazyColumn(
        items = rows,
        keyOf = { row ->
            when (row) {
                is GroupRow.Header -> "h:" + row.tag
                is GroupRow.Item -> "i:" + row.status.event.id
            }
        },
        // 关键：不允许跨组（组号必须相同），也不允许拖到第 0 个之前
        canMove = { from, to -> groupIndexOf.getOrNull(from) == groupIndexOf.getOrNull(to) },
        onReorder = { from, to ->
            if (from in rows.indices && to in rows.indices) {
                val moved = rows.removeAt(from)
                rows.add(to, moved)
            }
        },
        onDragEnd = {
            saving = true
            // 只提交所有 Item 的当前顺序。
            // saveGroupOrder 会用组内下标重新编号 sortInGroup，
            // 分组之间本来就按标签名排序，不需要跨组编号。
            onSaveOrder(rows.filterIsInstance<GroupRow.Item>().map { it.status.event.id })
            saving = false
        },
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) { row, _, _ ->
        when (row) {
            is GroupRow.Header -> GroupHeader(
                tag = row.tag,
                count = row.count,
                collapsed = row.collapsed,
                onToggle = { onToggleCollapse(row.tag) }
            )
            is GroupRow.Item -> EventCard(
                status = row.status,
                density = density,
                onClick = { onOpenDetail(row.status.event.id) },
                onQuickRecord = { onQuickRecord(row.status.event.id) }
            )
        }
    }
}

/** 分组列表的行：要么是分组头，要么是事件卡片。 */
private sealed class GroupRow {
    data class Header(val tag: String, val count: Int, val collapsed: Boolean) : GroupRow()
    data class Item(val status: EventStatusLite) : GroupRow()
}

@Composable
private fun GroupHeader(
    tag: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (collapsed) "▸" else "▾",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                tag,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
