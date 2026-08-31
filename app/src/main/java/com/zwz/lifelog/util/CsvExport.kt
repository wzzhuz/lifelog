package com.zwz.lifelog.util

import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.domain.model.Record

/**
 * CSV 导出：方便用 Excel / WPS 打开做透视分析。
 * 加了 UTF-8 BOM，否则 Excel 打开中文会乱码。
 */
object CsvExport {

    fun build(events: List<Event>, records: List<Record>): String {
        val nameOf = events.associateBy { it.id }
        val sb = StringBuilder()
        sb.append('\uFEFF') // BOM
        sb.append("事件,分类,发生时间,距上次(天),备注,有照片,录入时间\n")
        val grouped = records.groupBy { it.eventId }
        nameOf.keys.sortedBy { nameOf[it]?.name }.forEach { eid ->
            val ev = nameOf[eid] ?: return@forEach
            val list = (grouped[eid] ?: emptyList()).sortedBy { it.timestamp }
            list.forEachIndexed { index, r ->
                val gap = if (index == 0) "" else ((r.timestamp - list[index - 1].timestamp) / 86_400_000L).toString()
                sb.append(
                    listOf(
                        csv(ev.name),
                        csv(ev.tag ?: ""),
                        TimeFormatter.full(r.timestamp),
                        gap,
                        csv(r.note ?: ""),
                        if (r.photoName == null) "否" else "是",
                        TimeFormatter.full(r.loggedAt)
                    ).joinToString(",")
                )
                sb.append('\n')
            }
        }
        return sb.toString()
    }

    private fun csv(s: String): String {
        val needsQuote = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val body = s.replace("\"", "\"\"")
        return if (needsQuote) "\"$body\"" else body
    }
}
