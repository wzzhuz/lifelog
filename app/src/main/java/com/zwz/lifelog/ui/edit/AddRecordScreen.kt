package com.zwz.lifelog.ui.edit

import androidx.compose.foundation.layout.width
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.data.PhotoStore
import com.zwz.lifelog.domain.model.Record
import com.zwz.lifelog.util.TimeFormatter
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRecordScreen(
    repo: LifeLogRepository,
    eventId: Long,
    recordId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    var eventName by remember { mutableStateOf("") }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var note by remember { mutableStateOf("") }
    var photoName by remember { mutableStateOf<String?>(null) }
    var existing: Record? by remember { mutableStateOf(null) }
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(eventId, recordId) {
        repo.allRaw().let { snap ->
            eventName = snap.events.firstOrNull { it.id == eventId }?.name ?: ""
            if (recordId != 0L) {
                snap.records.firstOrNull { it.id == recordId }?.let {
                    existing = it
                    timestamp = it.timestamp
                    note = it.note ?: ""
                    photoName = it.photoName
                }
            }
        }
        ready = true
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val name = repo.savePhoto(uri)
                if (name != null) {
                    photoName = name
                    snack.showSnackbar("照片已添加")
                } else snack.showSnackbar("照片读取失败")
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
                title = { Text(if (recordId == 0L) "记一笔" else "编辑记录") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        if (!ready) { Box(Modifier.fillMaxSize().padding(pad)); return@Scaffold }

        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp)
        ) {
            Text("事件：$eventName", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            Text("时间（可以倒填）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            DateTimeButton(timestamp = timestamp) { timestamp = it }

            Spacer(Modifier.height(16.dp))
            Text("备注",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth().height(130.dp),
                placeholder = { Text("例如：社区医院，血常规正常，花了 120") },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(16.dp))
            Text("照片（可选）",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))

            if (photoName == null) {
                Row {
                    OutlinedButton(
                        onClick = { pickImage.launch("image/*") },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("从相册选")
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val bmp = remember(photoName) { PhotoStore(context).loadBitmap(photoName!!) }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    IconButton(
                        onClick = {
                            photoName?.let { PhotoStore(context).delete(it) }
                            photoName = null
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(34.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = .85f))
                    ) {
                        Icon(Icons.Default.Close, "移除照片", modifier = Modifier.size(18.dp))
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = {
                    scope.launch {
                        if (recordId == 0L) {
                            repo.addRecord(eventId, timestamp, note.ifBlank { null }, photoName)
                        } else {
                            existing?.let {
                                repo.updateRecord(
                                    it.copy(
                                        timestamp = timestamp,
                                        note = note.ifBlank { null },
                                        photoName = photoName
                                    )
                                )
                            }
                        }
                        onSaved()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp)
            ) { Text("保存") }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun DateTimeButton(timestamp: Long, onChange: (Long) -> Unit) {
    val context = LocalContext.current
    val cal = remember(timestamp) {
        Calendar.getInstance().apply { timeInMillis = timestamp }
    }

    fun showPicker() {
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        DatePickerDialog(
            context,
            { _, y, m, d ->
                TimePickerDialog(
                    context,
                    { _, h, min ->
                        val n = Calendar.getInstance().apply {
                            set(y, m, d, h, min, 0)
                            set(Calendar.MILLISECOND, 0)
                        }
                        onChange(n.timeInMillis)
                    },
                    c.get(Calendar.HOUR_OF_DAY),
                    c.get(Calendar.MINUTE),
                    true
                ).show()
            },
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH),
            c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable { showPicker() }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.EditCalendar, null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(TimeFormatter.full(timestamp), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
