package com.zwz.lifelog.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.collectAsState
import com.zwz.lifelog.data.HomeLayoutMode
import com.zwz.lifelog.data.HomeLayoutPrefs
import com.zwz.lifelog.data.LifeLogRepository
import com.zwz.lifelog.di.ServiceLocator
import com.zwz.lifelog.ui.detail.DetailScreen
import com.zwz.lifelog.ui.detail.DetailViewModel
import com.zwz.lifelog.ui.edit.AddRecordScreen
import com.zwz.lifelog.ui.edit.EditEventScreen
import com.zwz.lifelog.ui.edit.EditViewModel
import com.zwz.lifelog.ui.list.ListScreen
import com.zwz.lifelog.ui.list.ListViewModel
import com.zwz.lifelog.ui.review.YearReviewScreen
import com.zwz.lifelog.ui.settings.ArchivedScreen
import com.zwz.lifelog.ui.settings.SettingsScreen
import com.zwz.lifelog.ui.settings.UsageGuideScreen
import com.zwz.lifelog.ui.timeline.TimelineScreen
import com.zwz.lifelog.ui.timeline.TimelineViewModel
import com.zwz.lifelog.util.LifeLogViewModelFactory

object Route {
    const val LIST = "list"
    const val TIMELINE = "timeline"
    const val SETTINGS = "settings"
    const val DETAIL = "detail/{eventId}"
    const val EDIT_EVENT = "edit_event/{eventId}"
    const val ADD_RECORD = "add_record/{eventId}/{recordId}"
    const val YEAR_REVIEW = "year_review"
    const val USAGE_GUIDE = "usage_guide"
    const val ARCHIVED = "archived"

    fun detail(eventId: Long) = "detail/$eventId"
    fun editEvent(eventId: Long) = "edit_event/$eventId"
    fun addRecord(eventId: Long, recordId: Long = 0L) = "add_record/$eventId/$recordId"
}

@Composable
fun AppNav(
    repo: LifeLogRepository,
    onDataChanged: () -> Unit
) {
    val nav: NavHostController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val layoutMode by HomeLayoutPrefs.modeFlow(context)
        .collectAsState(initial = HomeLayoutMode.COMPACT)
    val collapsedTags by HomeLayoutPrefs.collapsedFlow(context)
        .collectAsState(initial = emptySet())

    NavHost(navController = nav, startDestination = Route.LIST) {

        composable(Route.LIST) {
            val vm: ListViewModel = viewModel(factory = LifeLogViewModelFactory(repo))
            ListScreen(
                vm = vm,
                onOpenDetail = { nav.navigate(Route.detail(it)) },
                onCreateEvent = { nav.navigate(Route.editEvent(0L)) },
                onOpenTimeline = { nav.navigate(Route.TIMELINE) },
                onOpenSettings = { nav.navigate(Route.SETTINGS) },
                onDataChanged = onDataChanged,
                layoutMode = layoutMode,
                collapsedTags = collapsedTags,
                onToggleCollapse = { tag ->
                    scope.launch { HomeLayoutPrefs.toggleCollapsed(context, tag) }
                }
            )
        }

        composable(Route.TIMELINE) {
            val vm: TimelineViewModel = viewModel(factory = LifeLogViewModelFactory(repo))
            TimelineScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onOpenDetail = { nav.navigate(Route.detail(it)) }
            )
        }

        composable(Route.SETTINGS) {
            SettingsScreen(
                repo = repo,
                onBack = { nav.popBackStack() },
                onOpenYearReview = { nav.navigate(Route.YEAR_REVIEW) },
                onOpenUsageGuide = { nav.navigate(Route.USAGE_GUIDE) },
                onOpenArchived = { nav.navigate(Route.ARCHIVED) },
                onDataChanged = onDataChanged
            )
        }

        composable(Route.YEAR_REVIEW) {
            YearReviewScreen(repo = repo, onBack = { nav.popBackStack() })
        }

        composable(Route.USAGE_GUIDE) {
            UsageGuideScreen(onBack = { nav.popBackStack() })
        }

        composable(Route.ARCHIVED) {
            ArchivedScreen(
                repo = repo,
                onBack = { nav.popBackStack() },
                onDataChanged = onDataChanged
            )
        }

        composable(
            Route.DETAIL,
            arguments = listOf(navArgument("eventId") { type = NavType.LongType })
        ) { backStack ->
            val id = backStack.arguments?.getLong("eventId") ?: 0L
            val vm: DetailViewModel = viewModel(factory = LifeLogViewModelFactory(repo, id))
            DetailScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onEditEvent = { nav.navigate(Route.editEvent(it)) },
                onAddRecord = { nav.navigate(Route.addRecord(it)) },
                onEditRecord = { eid, rid -> nav.navigate(Route.addRecord(eid, rid)) },
                onDataChanged = onDataChanged
            )
        }

        composable(
            Route.EDIT_EVENT,
            arguments = listOf(navArgument("eventId") { type = NavType.LongType })
        ) { backStack ->
            val id = backStack.arguments?.getLong("eventId") ?: 0L
            val vm: EditViewModel = viewModel(factory = LifeLogViewModelFactory(repo, id))
            EditEventScreen(
                vm = vm,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack(); onDataChanged() }
            )
        }

        composable(
            Route.ADD_RECORD,
            arguments = listOf(
                navArgument("eventId") { type = NavType.LongType },
                navArgument("recordId") { type = NavType.LongType }
            )
        ) { backStack ->
            val eventId = backStack.arguments?.getLong("eventId") ?: 0L
            val recordId = backStack.arguments?.getLong("recordId") ?: 0L
            val vm: EditViewModel = viewModel(factory = LifeLogViewModelFactory(repo, eventId))
            AddRecordScreen(
                repo = repo,
                eventId = eventId,
                recordId = recordId,
                onBack = { nav.popBackStack() },
                onSaved = { nav.popBackStack(); onDataChanged() }
            )
        }
    }
}
