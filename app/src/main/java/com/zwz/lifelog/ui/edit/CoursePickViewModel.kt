package com.zwz.lifelog.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.domain.model.CourseTemplate
import com.zwz.lifelog.domain.model.Event
import com.zwz.lifelog.domain.model.EventKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 「开疗程」的视图模型。
 *
 * 与 [EditViewModel] 分开，是因为两者的心智模型完全不同：
 * 编辑页是「填一张表单」，这里是「选一个场景 → 确认名字」。
 * 硬塞进同一个 ViewModel 又要靠分支区分，正是这次要拆掉的东西。
 */
class CoursePickViewModel(
    private val repo: LifeLogRepository
) : ViewModel() {

    private val _selected = MutableStateFlow<CourseTemplate?>(null)
    val selected: StateFlow<CourseTemplate?> = _selected

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    fun setName(v: String) { _name.value = v }

    fun select(t: CourseTemplate) = viewModelScope.launch {
        _selected.value = t
        _name.value = if (t.custom) "" else repo.uniqueCourseName(suggestCourseName(t.namePrefix))
    }

    /**
     * 「前缀 + 今天」，如「感冒 9月11日」。
     *
     * 用「M月d日」而非完整日期：疗程跨度通常只有几周，
     * 年份是冗余信息，而卡片宽度有限。
     */
    internal fun suggestCourseName(prefix: String): String {
        val today = java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA)
            .format(java.util.Date())
        return "$prefix $today"
    }

    /**
     * 创建疗程本体。
     *
     * **不自动创建子事件**——模板里的常用项只是建议，
     * 创建后由疗程详情页以 chip 形式呈现，用户点一下加一个。
     * 每个人的处方不同，擅自替用户决定吃哪种药比让他多点一下更糟。
     */
    fun create(onCreated: (Long) -> Unit) = viewModelScope.launch {
        val t = _selected.value ?: return@launch
        val ev = Event(
            id = 0L,
            name = _name.value.trim().ifBlank { t.namePrefix },
            emoji = t.emoji,
            colorArgb = t.colorArgb,
            kind = EventKind.COURSE,
            tag = t.tag,
            // 疗程没有「期望间隔」，状态由子事件聚合
            targetDays = null,
            timesPerDay = null
        )
        val id = repo.upsertEvent(ev)
        onCreated(id)
    }
}
