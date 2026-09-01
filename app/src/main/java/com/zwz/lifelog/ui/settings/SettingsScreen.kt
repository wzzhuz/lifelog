package com.zwz.lifelog.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.data.TemplateFileParser
import com.zwz.lifelog.data.TemplateParseResult
import com.zwz.lifelog.data.ThemePrefs
import com.zwz.lifelog.domain.model.Template
import com.zwz.lifelog.domain.model.Templates
import com.zwz.lifelog.util.BackupZip
import com.zwz.lifelog.util.CsvExport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    repo: LifeLogRepository,
    onBack: () -> Unit,
    onOpenYearReview: () -> Unit,
    onOpenUsageGuide: () -> Unit,
    onOpenArchived: () -> Unit,
    onDataChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    // 用专门的计数流，而不是 snapshotFlow()。
    // 后者会把全部记录读进内存只为数一下有多少条——
    // 记录上万时，进设置页会明显卡一下。
    val eventCount by repo.eventCountFlow().collectAsState(initial = 0)
    val recordCount by repo.recordCountFlow().collectAsState(initial = 0)
    val archivedCount by repo.archivedCountFlow().collectAsState(initial = 0)

    // 模板导入相关
    var showTemplatePicker by remember { mutableStateOf(false) }
    var existingNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingExternal by remember { mutableStateOf<List<Template>?>(null) }
    var pendingSourceName by remember { mutableStateOf("") }

    // 导入外部模板文件（JSON）
    val importTemplateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            }.getOrNull()

            if (text.isNullOrBlank()) {
                withContext(Dispatchers.Main) { snack.showSnackbar("读取文件失败") }
                return@launch
            }

            when (val r = TemplateFileParser.parse(text)) {
                is TemplateParseResult.Ok -> {
                    val names = repo.allEvents().map { it.name }.toSet()
                    withContext(Dispatchers.Main) {
                        existingNames = names
                        pendingExternal = r.file.templates
                        pendingSourceName = uri.lastPathSegment ?: "模板文件"
                    }
                }
                is TemplateParseResult.WrongType -> {
                    withContext(Dispatchers.Main) {
                        snack.showSnackbar(
                            if (r.actualType == null) "这不是模板文件（缺少 type 字段）"
                            else "这是 ${r.actualType}，不是模板文件"
                        )
                    }
                }
                is TemplateParseResult.BadFormat -> {
                    withContext(Dispatchers.Main) {
                        snack.showSnackbar("模板格式有误：${r.detail}")
                    }
                }
            }
        }
    }

    // 导出 JSON
    val exportJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                val json = repo.exportJson()
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                snack.showSnackbar("JSON 已导出")
            }.onFailure {
                snack.showSnackbar("导出失败：${it.message}")
            }
        }
    }

    // 导出完整备份（含照片）
    val exportZipLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            runCatching {
                val json = repo.exportJson()
                val tmp = File(context.cacheDir, "lifelog-backup-tmp.zip")
                BackupZip.create(tmp, json, repo.photos.photoDir)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    tmp.inputStream().use { it.copyTo(out) }
                }
                tmp.delete()
            }
            withContext(Dispatchers.Main) {
                runCatching { snack.showSnackbar("完整备份已导出（含照片）") }
            }
        }
    }

    // 导出 CSV
    val exportCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val s = repo.allRaw()
            val csv = CsvExport.build(s.events, s.records)
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(csv.toByteArray(Charsets.UTF_8))
            }
            snack.showSnackbar("CSV 已导出")
        }
    }

    // 导入
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            var ok = false
            var msg = ""
            runCatching {
                val cache = File(context.cacheDir, "import-${System.currentTimeMillis()}")
                if (uri.toString().contains("lifelog") || uri.lastPathSegment?.endsWith(".zip") == true) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        cache.outputStream().use { input.copyTo(it) }
                    }
                    val json = BackupZip.extract(cache, repo.photos.photoDir)
                    repo.importJson(json, merge = true)
                    cache.delete()
                    ok = true
                    msg = "备份已导入"
                } else {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    if (!text.isNullOrBlank()) {
                        repo.importJson(text, merge = true)
                        ok = true
                        msg = "已合并导入"
                    } else msg = "文件内容为空"
                }
            }.onFailure {
                msg = "导入失败：${it.message}"
            }
            withContext(Dispatchers.Main) {
                runCatching { snack.showSnackbar(msg) }
                if (ok) Toast.makeText(context, "导入完成", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                StatCard("$eventCount", "事件数", Modifier.weight(1f))
                Spacer(Modifier.width(10.dp))
                StatCard("$recordCount", "总记录数", Modifier.weight(1f))
            }

            Spacer(Modifier.height(22.dp))
            SectionTitle("整理")
            OutlinedButton(
                onClick = onOpenArchived,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("已归档事件")
                    Text(
                        "$archivedCount 个",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "归档后不显示在首页，可随时恢复。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle("模板")
            OutlinedButton(
                onClick = {
                    scope.launch {
                        existingNames = repo.allEvents().map { it.name }.toSet()
                        showTemplatePicker = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("从内置模板导入（${Templates.ALL.size} 个）") }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { importTemplateLauncher.launch(arrayOf("application/json", "text/*")) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("从文件导入模板（JSON）") }

            Spacer(Modifier.height(8.dp))
            Text(
                "模板就是预置的事件，导入后和普通事件一样可以改名、删除。" +
                    "同名事件不会重复导入。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle("备份与恢复")
            OutlinedButton(
                onClick = { exportJsonLauncher.launch(defaultName("json")) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("导出 JSON（纯数据）") }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { exportZipLauncher.launch(defaultName("zip")) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("导出完整备份（含照片，推荐）") }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { exportCsvLauncher.launch(defaultName("csv")) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("导出 CSV（可用 Excel 打开）") }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("导入备份（自动合并）") }

            Spacer(Modifier.height(8.dp))
            Text(
                "数据存在手机本地，不联网不上传。App 会在每次修改后自动保留最近 4 份本地备份，但清理应用数据或卸载会丢失，建议每月手动导出一次。",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            SectionTitle("外观")
            val mode by ThemePrefs.mode(context).collectAsState(initial = "auto")
            Row {
                listOf("auto" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (k, label) ->
                    val sel = mode == k
                    Button(
                        onClick = { scope.launch { ThemePrefs.setMode(context, k) } },
                        modifier = Modifier.weight(1f).height(44.dp).padding(horizontal = 3.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (sel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (sel) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) { Text(label) }
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionTitle("回顾")
            OutlinedButton(
                onClick = onOpenYearReview,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("年度回顾") }

            Spacer(Modifier.height(24.dp))
            SectionTitle("帮助")
            OutlinedButton(
                onClick = onOpenUsageGuide,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("使用说明（状态色 · 判断基准 · 补录）") }

            Spacer(Modifier.height(24.dp))
            SectionTitle("关于")
            Text(
                "生活手记 v1.0.0\n包名 com.zwz.lifelog\n\n完全离线运行，不申请网络权限，无广告、无统计、无账号。\n源码开源，你可以在自己的 GitHub 上自由修改。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(40.dp))
        }
    }

    // 内置模板导入
    if (showTemplatePicker) {
        TemplatePickerSheet(
            templates = Templates.ALL,
            existingNames = existingNames,
            onDismiss = { showTemplatePicker = false },
            onConfirm = { selected ->
                showTemplatePicker = false
                scope.launch {
                    val n = repo.importTemplates(selected)
                    onDataChanged()
                    snack.showSnackbar(
                        if (n > 0) "已导入 $n 个事件" else "没有新事件可导入"
                    )
                }
            }
        )
    }

    // 外部模板文件导入（先预览再确认）
    pendingExternal?.let { list ->
        ExternalTemplatePreviewSheet(
            templates = list,
            existingNames = existingNames,
            sourceName = pendingSourceName,
            onDismiss = { pendingExternal = null },
            onConfirm = {
                val toImport = list
                pendingExternal = null
                scope.launch {
                    val n = repo.importExternalTemplates(toImport)
                    onDataChanged()
                    snack.showSnackbar(
                        if (n > 0) "已导入 $n 个事件" else "没有新事件可导入"
                    )
                }
            }
        )
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(t: String) {
    Text(t, style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp))
}

private fun defaultName(ext: String): String {
    val s = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "生活手记-$s.$ext"
}
