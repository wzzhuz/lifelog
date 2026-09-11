package com.zwz.lifelog.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.data.db.isCourseRow
import kotlinx.coroutines.launch

/**
 * 已归档事件。
 *
 * 存在的意义：此前归档是**单向操作**——事件一旦归档就从所有界面消失，
 * 无法查看、无法恢复、无法删除。唯一的出路是导出 JSON 手改字段再导回。
 * 本页就是补上这个缺失的出口。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivedScreen(
    repo: LifeLogRepository,
    onBack: () -> Unit,
    onDataChanged: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val rows by repo.archivedWithCount().collectAsState(initial = emptyList())

    var pendingDelete by remember { mutableStateOf<com.zwz.lifelog.data.db.ArchivedEventRow?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("已归档") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        if (rows.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pad)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("\uD83D\uDCE6", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Text("还没有归档的事件", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "在事件详情页可以归档。归档后不显示在首页，但随时可以从这里恢复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp)
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "共 ${rows.size} 个已归档事件。恢复后回到首页，删除则连同其记录一并清除。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            // 归档列表里同样要看出层级：疗程带子事件数，子事件带所属疗程名
            val childCountOf = rows.filter { it.parentId != null }
                .groupBy { it.parentId }
                .mapValues { it.value.size }
            val parentNameOf = rows.associateBy { it.id }

            rows.forEach { row ->
                ArchivedRow(
                    emoji = row.emoji,
                    name = row.name,
                    recordCount = row.recordCount,
                    hint = when {
                        row.isCourseRow() -> {
                            val n = childCountOf[row.id] ?: 0
                            "疗程 · $n 个子事件"
                        }
                        row.parentId != null ->
                            row.parentId?.let { pid ->
                                "属于 ${parentNameOf[pid]?.name ?: "已删除的疗程"}"
                            }
                        else -> null
                    },
                    onRestore = {
                        scope.launch {
                            // 疗程恢复时把子事件一起带回来，
                            // 否则会出现「疗程在首页、药还在归档里」的半吊子状态
                            if (row.isCourseRow()) repo.restoreCourse(row.id)
                            else repo.setArchived(row.id, false)
                            onDataChanged()
                            snack.showSnackbar("已恢复：${row.name}")
                        }
                    },
                    onDelete = { pendingDelete = row }
                )
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    pendingDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("彻底删除「${row.name}」？") },
            text = {
                Text(
                    if (row.recordCount > 0)
                        "将同时删除它的 ${row.recordCount} 条记录，此操作不可撤销。"
                    else
                        "该事件没有记录。删除后不可撤销。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    scope.launch {
                        repo.deleteEvent(row.id)
                        onDataChanged()
                        snack.showSnackbar("已彻底删除：${row.name}")
                    }
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun ArchivedRow(
    emoji: String,
    name: String,
    recordCount: Int,
    /** 层级提示：疗程显示子事件数，子事件显示所属疗程。 */
    hint: String?,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium)
                Text(
                    if (recordCount > 0) "$recordCount 条记录" else "无记录",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(
                onClick = onRestore,
                modifier = Modifier.height(38.dp),
                shape = RoundedCornerShape(10.dp)
            ) { Text("恢复") }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onDelete,
                modifier = Modifier.height(38.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) { Text("删除") }
        }
    }
}
