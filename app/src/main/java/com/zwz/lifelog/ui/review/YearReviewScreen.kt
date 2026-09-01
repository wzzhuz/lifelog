package com.zwz.lifelog.ui.review

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.domain.model.Event
import java.util.Calendar

data class YearStat(
    val event: Event,
    val count: Int,
    val monthLabels: List<String>
)

/** 取某个时间戳所属年份，避免复用 Calendar 实例带来的串扰。 */
private fun yearOf(timestamp: Long): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    return cal.get(Calendar.YEAR)
}

/** 取某个时间戳的月份（1-12）。 */
private fun monthOf(timestamp: Long): Int {
    val cal = Calendar.getInstance()
    cal.timeInMillis = timestamp
    return cal.get(Calendar.MONTH) + 1
}

/** 某年的起止时间戳 [1月1日 00:00, 次年1月1日 00:00)。 */
private fun yearRange(year: Int): Pair<Long, Long> {
    val from = Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
    val to = Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, year + 1)
        set(Calendar.MONTH, Calendar.JANUARY)
        set(Calendar.DAY_OF_MONTH, 1)
    }.timeInMillis
    return from to to
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearReviewScreen(repo: LifeLogRepository, onBack: () -> Unit) {
    val currentYear: Int = remember { Calendar.getInstance().get(Calendar.YEAR) }
    var year: Int by remember { mutableIntStateOf(currentYear) }

    // produceState 的参数名是 initialValue，不是 initial
    val data: List<YearStat> by produceState(
        initialValue = emptyList<YearStat>(),
        key1 = year,
        key2 = repo
    ) {
        // 只查这一年的记录，而不是把全部记录读出来再按年份过滤
        val (from, to) = yearRange(year)
        val yearRecords = repo.recordsBetween(from, to)
        val byEvent = yearRecords.groupBy { it.eventId }

        val list: List<YearStat> = repo.allEvents()
            .filter { !it.isArchived }
            .map { ev ->
                val times: List<Long> =
                    (byEvent[ev.id] ?: emptyList()).map { it.timestamp }.sorted()
                YearStat(
                    event = ev,
                    count = times.size,
                    monthLabels = times.map { t -> "${monthOf(t)}月" }
                )
            }
            .filter { stat -> stat.count > 0 }
            .sortedByDescending { stat -> stat.count }
        value = list
    }

    val totalCount: Int = data.sumOf { stat -> stat.count }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("年度回顾") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { pad ->
        Column(modifier = Modifier.padding(pad).fillMaxSize()) {

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(onClick = { year = year - 1 }) {
                    Text("‹", style = MaterialTheme.typography.headlineMedium)
                }
                Text(
                    "$year 年",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.width(100.dp),
                    textAlign = TextAlign.Center
                )
                IconButton(onClick = { if (year < currentYear) year = year + 1 }) {
                    Text("›", style = MaterialTheme.typography.headlineMedium)
                }
            }

            if (data.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "$year 年还没有记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Text(
                                    "$year 年，你一共记了 $totalCount 笔",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "覆盖 ${data.size} 类生活琐事",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    items(data, key = { stat -> stat.event.id }) { stat ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stat.event.emoji,
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stat.event.name,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                    Text(
                                        stat.monthLabels.joinToString("、"),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "${stat.count}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    " 次",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(60.dp)) }
                }
            }
        }
    }
}
