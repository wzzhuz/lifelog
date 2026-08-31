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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditEventScreen(
    vm: EditViewModel,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val draft by vm.draft.collectAsState()
    val loaded by vm.loaded.collectAsState()

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

            Spacer(Modifier.height(16.dp))
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

@Composable
private fun FieldLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp)
    )
}
