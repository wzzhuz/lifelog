package com.zwz.lifelog.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.domain.model.ChildSuggestion
import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.domain.model.EventKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class EditViewModel(
    private val repo: LifeLogRepository,
    private val eventId: Long,
    /**
     * 新建子事件时所属疗程的 id（0 表示新建的是普通事件）。
     *
     * 从疗程详情页「添加子事件」进来时会带上它，
     * 子事件因此自动继承疗程的分类，不用再选一遍。
     */
    private val parentId: Long = 0L
) : ViewModel() {

    private val _draft = MutableStateFlow(Event(id = eventId, name = ""))
    val draft: StateFlow<Event> = _draft

    // 需要查库的场景先不渲染，查完再放开；不需要查库的直接可用。
    // 查询一律包 runCatching：抛异常时也必须把 loaded 置 true，
    // 否则会永远卡在空白页（这一页曾有 init 块整个丢失的事故，
    // 表现为子事件页只剩一个名称框、且保存后不挂到疗程下）。
    private val _loaded = MutableStateFlow(eventId == 0L && parentId == 0L)
    val loaded: StateFlow<Boolean> = _loaded

    init {
        when {
            eventId != 0L -> viewModelScope.launch {
                runCatching { repo.eventById(eventId) }.getOrNull()
                    ?.let { _draft.value = it }
                _loaded.value = true
            }

            parentId != 0L -> viewModelScope.launch {
                val parent = runCatching { repo.eventById(parentId) }.getOrNull()
                // 记住疗程名：子事件靠它只显示本场景的常用项预设
                _parentName.value = parent?.name
                _draft.value = Event(
                    id = 0L,
                    name = "",
                    parentId = parentId,
                    tag = parent?.tag
                )
                _loaded.value = true
            }
        }
    }

    /**
     * 「更多设置」（图标 / 颜色 / 类型 / 分类）是否展开。
     *
     * 选了疗程模板后这四项已被填好，默认收起——
     * 用户要做的只是确认名字。但**不隐藏**：仍可能想换个图标。
     * 未选模板时默认展开，保持普通事件的创建体验不变。
     */
    // 新建时**收起**：「记件事」只需要名字，图标/颜色/分类都有默认值，
    // 不展开也能直接保存。开疗程已分流到独立页面，这里不再摆模板。
    // 编辑已有事件时展开——用户进来多半就是想改这些。
    private val _advancedExpanded = MutableStateFlow(eventId != 0L)
    val advancedExpanded: StateFlow<Boolean> = _advancedExpanded

    fun setAdvancedExpanded(v: Boolean) { _advancedExpanded.value = v }

    fun patch(block: (Event) -> Event) { _draft.value = block(_draft.value) }

    /** 所属疗程名。子事件用它决定显示哪些常用项预设。 */
    private val _parentName = MutableStateFlow<String?>(null)
    val parentName: StateFlow<String?> = _parentName

    /** 应用一个常用项预设：名字、图标、类型、频次/周期一次填好。 */
    fun applyPreset(p: ChildSuggestion) {
        _draft.value = _draft.value.copy(
            name = p.name,
            emoji = p.emoji,
            kind = p.kind,
            timesPerDay = p.timesPerDay,
            targetDays = p.periodDays
        )
    }

    /**
     * @param onDone 回传保存后的事件（新建时带上数据库分配的 id）。
     *               调用方靠它判断去向：疗程要跳详情页接着加药，
     *               普通事件回首页即可。
     */
    fun save(onDone: (Event) -> Unit) = viewModelScope.launch {
        val e = _draft.value
        if (e.name.isBlank()) return@launch
        val trimmed = e.copy(name = e.name.trim())
        val id = repo.upsertEvent(trimmed)
        onDone(if (trimmed.id == 0L) trimmed.copy(id = id) else trimmed)
    }

    companion object {
        val PALETTE = listOf(
            0xFF2F6FED, 0xFF16A34A, 0xFFE08C00, 0xFFE5484D,
            0xFF8B5CF6, 0xFF06B6D4, 0xFFEC4899, 0xFF64748B
        ).map { it.toInt() }

        val EMOJIS = listOf(
            "\u2702\uFE0F", "\uD83D\uDECF\uFE0F", "\uD83C\uDFE5", "\uD83E\uDDB7",
            "\uD83D\uDC8A", "\uD83E\uDDA5", "\uD83E\uDDF4", "\uD83D\uDE97",
            "\uD83D\uDC3E", "\uD83E\uDDEB", "\uD83D\uDCCC", "\u2764\uFE0F"
        )
    }
}
