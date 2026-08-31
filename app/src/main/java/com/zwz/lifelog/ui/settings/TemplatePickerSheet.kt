package com.zwz.lifelog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zwz.lifelog.domain.model.Template

/**
 * 内置模板多选导入。
 *
 * 设置页可重复进入（原入口只在空状态页出现，有事件后就找不到了）。
 * 已存在的同名事件置灰并标注「已添加」，避免重复导入。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerSheet(
    templates: List<Template>,
    existingNames: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val selected = remember {
        mutableStateListOf<String>().apply {
            // 默认不选，让用户自己挑
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "从模板导入",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = {
                    val all = templates.map { it.name }.filter { it !in existingNames }
                    selected.clear()
                    selected.addAll(all)
                }) { Text("全选") }
            }

            Text(
                "共 ${templates.size} 个模板，已添加的会置灰。导入的同名事件会被跳过。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            // 按标签分组展示，模板有 34 个，平铺会很难找
            val grouped = remember(templates) { templates.groupBy { it.tag } }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                grouped.forEach { (tag, list) ->
                    item {
                        Text(
                            tag,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }
                    items(list, key = { it.name }) { t ->
                        TemplateRow(
                            template = t,
                            alreadyExists = t.name in existingNames,
                            checked = t.name in selected,
                            onToggle = {
                                if (it) selected.add(t.name) else selected.remove(t.name)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("取消") }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = { onConfirm(selected.toSet()) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (selected.isEmpty()) "导入" else "导入 ${selected.size} 个") }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TemplateRow(
    template: Template,
    alreadyExists: Boolean,
    checked: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (checked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = { if (!alreadyExists) onToggle(it) },
                enabled = !alreadyExists
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${template.emoji}  ${template.name}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (alreadyExists) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    when {
                        alreadyExists -> "已添加"
                        template.targetDays != null -> "每 ${template.targetDays} 天"
                        else -> "无固定节奏，记两次后自动学习"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 外部模板文件导入前的预览确认。
 *
 * 导入前必须让用户看见会导入什么——外部文件可能是别人给的，
 * 也可能自己记不清里面有什么。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExternalTemplatePreviewSheet(
    templates: List<Template>,
    existingNames: Set<String>,
    sourceName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val newOnes = remember(templates, existingNames) {
        templates.filter { it.name !in existingNames }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "确认导入模板",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "来自：$sourceName\n共 ${templates.size} 个模板，其中 ${newOnes.size} 个是新的" +
                    if (newOnes.size < templates.size) "，${templates.size - newOnes.size} 个已存在会跳过"
                    else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(templates, key = { it.name }) { t ->
                    val exists = t.name in existingNames
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(t.emoji, modifier = Modifier.width(32.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                buildString {
                                    append(t.tag)
                                    if (t.targetDays != null) append(" · 每 ${t.targetDays} 天")
                                    else append(" · 无固定节奏")
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (exists) {
                            Text(
                                "已存在",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("取消") }

                Spacer(Modifier.width(12.dp))

                Button(
                    onClick = onConfirm,
                    enabled = newOnes.isNotEmpty(),
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (newOnes.isEmpty()) "没有新模板" else "导入 ${newOnes.size} 个") }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
