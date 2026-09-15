package com.zwz.lifelog.ui.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.zwz.lifelog.domain.model.ChildSuggestion
import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.domain.model.EventKind
import com.zwz.lifelog.domain.model.Templates

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditEventScreen(
    vm: EditViewModel,
    onBack: () -> Unit,
    onSaved: (Event) -> Unit
) {
    val draft by vm.draft.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val advancedExpanded by vm.advancedExpanded.collectAsState()
    val parentName by vm.parentName.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text(if (draft.id == 0L) "新建事件" else "编辑事件") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        if (!loaded) { Box(Modifier.fillMaxSize().padding(pad)); return@Scaffold }

        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            FieldLabel("名称")
            OutlinedTextField(
                value = draft.name,
                onValueChange = { v -> vm.patch { ev -> ev.copy(name = v) } },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("例如：理发") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // 新建子事件时给常用项预设。
            // 与首页模板库不同，这里的 chip **允许重复点**——
            // 第二次感冒还要能再加一个「退烧药」。
            if (draft.id == 0L && draft.parentId != null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "常用项（点一下填好名字和频次）",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                ChildPresetChips(parentName = parentName, vm = vm)
            }

            // 「更多设置」：选了疗程模板后这些已被自动填好，默认收起。
            // 刻意不隐藏——用户仍可能想换个图标或颜色。
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { vm.setAdvancedExpanded(!advancedExpanded) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (advancedExpanded) "更多设置 ▾" else "更多设置（已自动填好）▸",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }
            if (advancedExpanded) {
                FieldLabel("图标")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditViewModel.EMOJIS.forEach { e ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (draft.emoji == e) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                                .clickable { vm.patch { it.copy(emoji = e) } },
                            contentAlignment = Alignment.Center
                        ) { Text(e, style = MaterialTheme.typography.titleMedium) }
                    }
                }

                Spacer(Modifier.height(16.dp))
                FieldLabel("颜色")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    EditViewModel.PALETTE.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (draft.colorArgb == c) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape
                                )
                                .clickable { vm.patch { it.copy(colorArgb = c) } }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                FieldLabel("类型")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 子事件不能再是疗程：层级维持两层（疗程 → 子事件），
                    // 三层既没有真实场景支撑，也会让疗程页变成嵌套迷宫
                    val kinds = if (draft.parentId != null) {
                        listOf(EventKind.PERIODIC to "有周期", EventKind.ON_DEMAND to "按需")
                    } else {
                        listOf(
                            EventKind.PERIODIC to "有周期",
                            EventKind.ON_DEMAND to "按需",
                            EventKind.COURSE to "疗程"
                        )
                    }
                    kinds.forEach { (k, label) ->
                        FilterChip(
                            selected = draft.kind == k,
                            onClick = { vm.patch { it.copy(kind = k) } },
                            label = { Text(label) }
                        )
                    }
                }
                Text(
                    when (draft.kind) {
                        EventKind.PERIODIC ->
                            "有周期：按间隔判断「该不该做」。适合理发、换床单、量血压。"
                        EventKind.ON_DEMAND ->
                            "按需：只回答「上次什么时候」，永远不催你。适合感冒看病、针灸。"
                        EventKind.COURSE ->
                            "疗程：一次看病/一段调理的容器，药作为子事件挂在下面，结束即可整体归档。"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )

                Spacer(Modifier.height(16.dp))
                FieldLabel("分类（可选）")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("个人", "健康", "家务", "宠物", "汽车", "数码", "财务", "人际").forEach { t ->
                        FilterChip(
                            selected = draft.tag == t,
                            onClick = { vm.patch { it.copy(tag = if (it.tag == t) null else t) } },
                            label = { Text(t) }
                        )
                    }
                }

                // 疗程没有间隔，只有开始与结束；它的状态由子事件聚合
                if (draft.kind != EventKind.COURSE) {
                    Spacer(Modifier.height(16.dp))
                    FieldLabel("期望间隔天数（留空则按历史自动判断）")
                    OutlinedTextField(
                        value = draft.targetDays?.toString() ?: "",
                        onValueChange = { v ->
                            val n = v.filter { it.isDigit() }.toIntOrNull()
                            vm.patch { it.copy(targetDays = if (v.isBlank()) null else n) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：45") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        "设了间隔才会有「该做了」的判断。不设也能用：记满两次后系统会按你的实际节奏自动估算。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )

                    Spacer(Modifier.height(16.dp))
                    FieldLabel("每日次数（留空则不按频次判断）")
                    OutlinedTextField(
                        value = draft.timesPerDay?.toString() ?: "",
                        onValueChange = { v ->
                            val n = v.filter { it.isDigit() }.toIntOrNull()
                            vm.patch {
                                it.copy(timesPerDay = if (v.isBlank() || n == null || n <= 0) null else n)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：一天三次填 3") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text(
                        "填了就按「今天还差几次」判断，只看当天、不统计连续天数。",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            FieldLabel("备注（可选）")
            OutlinedTextField(
                value = draft.note ?: "",
                onValueChange = { v -> vm.patch { ev -> ev.copy(note = v.ifBlank { null }) } },
                modifier = Modifier.fillMaxWidth().height(100.dp),
                placeholder = { Text("给这个事件写点说明") },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors()
            )

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { vm.save(onSaved) },
                enabled = draft.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("保存") }

            Spacer(Modifier.height(40.dp))
        }
    }
}

/**
 * 子事件的常用项 chip。
 *
 * 疗程「知道」自己是什么场景：从模板创建的疗程只列对应的 3~4 个预设，
 * 而不是把 17 个全摆出来——用上下文替代穷举。
 * 匹配不到（手动建的疗程、用户改过名字）时回退为按场景分组展示全部。
 */
@Composable
private fun ChildPresetChips(parentName: String?, vm: EditViewModel) {
    val scoped = Templates.templateForCourseName(parentName)?.children
    if (scoped != null) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            scoped.forEach { preset -> PresetChip(preset) { vm.applyPreset(preset) } }
        }
        return
    }
    Templates.childPresetsByScene().forEach { (scene, presets) ->
        Text(
            scene,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            presets.forEach { preset -> PresetChip(preset) { vm.applyPreset(preset) } }
        }
    }
}

@Composable
private fun PresetChip(preset: ChildSuggestion, onPick: () -> Unit) {
    SuggestionChip(
        onClick = onPick,
        label = { Text("${preset.emoji} ${preset.name}${preset.hintText}") }
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
