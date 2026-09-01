package com.zwz.lifelog.ui.list

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    vm: ListViewModel,
    onOpenDetail: (Long) -> Unit,
    onCreateEvent: () -> Unit,
    onOpenTimeline: () -> Unit,
    onOpenSettings: () -> Unit,
    onDataChanged: () -> Unit
) {
    val state by vm.ui.collectAsState()
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.visible, key = { it.event.id }) { s ->
                        EventCard(
                            status = s,
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
                    }
                    item { Spacer(Modifier.height(80.dp)) }
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
