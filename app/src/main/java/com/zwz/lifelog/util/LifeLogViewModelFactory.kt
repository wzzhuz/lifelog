package com.zwz.lifelog.util

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.ui.detail.DetailViewModel
import com.zwz.lifelog.ui.edit.EditViewModel
import com.zwz.lifelog.ui.list.ListViewModel
import com.zwz.lifelog.ui.timeline.TimelineViewModel

/**
 * 手写 ViewModel 工厂，替代 Hilt 的 @HiltViewModel，减少注解处理器依赖。
 */
@Suppress("UNCHECKED_CAST")
class LifeLogViewModelFactory(
    private val repo: LifeLogRepository,
    private val eventId: Long = 0L
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T = when {
        modelClass.isAssignableFrom(ListViewModel::class.java) ->
            ListViewModel(repo) as T
        modelClass.isAssignableFrom(TimelineViewModel::class.java) ->
            TimelineViewModel(repo) as T
        modelClass.isAssignableFrom(DetailViewModel::class.java) ->
            DetailViewModel(repo, eventId) as T
        modelClass.isAssignableFrom(EditViewModel::class.java) ->
            EditViewModel(repo, eventId) as T
        else -> throw IllegalArgumentException("未知 ViewModel: ${modelClass.name}")
    }
}
