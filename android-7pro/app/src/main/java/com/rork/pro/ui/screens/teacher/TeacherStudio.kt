package com.rork.pro.ui.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.text.font.FontWeight
import com.rork.pro.ui.i18n.StrStudioHub
import com.rork.pro.ui.screens.admin.AdminActions
import com.rork.pro.ui.screens.admin.AdminBadgeIcon
import com.rork.pro.ui.screens.admin.AdminButton
import com.rork.pro.ui.screens.admin.AdminMeta
import com.rork.pro.ui.screens.admin.AdminMetaRow
import com.rork.pro.ui.screens.admin.AdminTone
import com.rork.pro.ui.screens.admin.AttentionCard
import com.rork.pro.ui.screens.admin.AttentionItem
import com.rork.pro.ui.screens.admin.ConsoleGroup
import com.rork.pro.ui.screens.admin.ConsoleSectionTitle
import com.rork.pro.ui.screens.admin.ConsoleShortcut
import com.rork.pro.ui.screens.admin.ConsoleStatTile
import com.rork.pro.ui.screens.admin.ShortcutGroupCard
import com.rork.pro.ui.screens.admin.StatTileData
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.rork.pro.data.Async
import com.rork.pro.data.CourseScope
import com.rork.pro.data.TeacherBalance
import com.rork.pro.data.TeacherRepository
import com.rork.pro.data.toAppError
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.EarningsMatrixCard
import com.rork.pro.ui.components.EarningsStream
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.components.SectionHeader
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.navigation.Routes
import com.rork.pro.ui.screens.studio.StudioRoutes
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.StrEx
import com.rork.pro.ui.i18n.StrSub
import com.rork.pro.ui.i18n.StrTeacher
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf

object TeacherRoutes {
    const val STUDIO = "teacher/studio"
    const val EARNINGS = "teacher/earnings"
    const val EXERCISES = "teacher/exercises"
    const val COURSE_GRANTS = "teacher/course-grants"
    const val SUBSCRIPTIONS = "teacher/subscriptions"
    const val SUBSCRIPTION_GROUP = "teacher/subscriptions/group/{groupName}"
    const val UPCOMING_RENEWALS = "teacher/subscriptions/renewals"
    const val SUBSCRIPTION_EARNINGS = "teacher/subscription-earnings"
    const val DASHBOARD = "teacher/dashboard/unified"
    const val EXERCISE_LEVEL = "teacher/exercises-level/{levelKey}"
    const val EXERCISE_RESULTS = "teacher/exercises/{exerciseId}/results"
    const val EXERCISE_QUESTIONS = "teacher/exercises/{exerciseId}/questions"
    const val STUDENT_ATTEMPT = "teacher/attempt/{attemptId}"

    fun exerciseLevel(levelKey: String) = "teacher/exercises-level/$levelKey"
    fun exerciseResults(exerciseId: String) = "teacher/exercises/$exerciseId/results"
    fun exerciseQuestions(exerciseId: String) = "teacher/exercises/$exerciseId/questions"
    fun studentAttempt(attemptId: String) = "teacher/attempt/$attemptId"

    fun subscriptionGroup(name: String) = "teacher/subscriptions/group/${java.net.URLEncoder.encode(name, "UTF-8")}"
}

fun NavGraphBuilder.teacherGraph(navController: NavHostController, session: SessionViewModel) {
    composable(TeacherRoutes.STUDIO) { TeacherStudioScreen(navController, session) }
    composable(TeacherRoutes.EARNINGS) { TeacherEarningsScreen(navController) }
    composable(TeacherRoutes.EXERCISES) { ExerciseStudioScreen(navController, manageAll = false) }
    composable(TeacherRoutes.COURSE_GRANTS) { com.rork.pro.ui.screens.admin.CourseGrantsScreen(navController, staff = false) }
    composable(TeacherRoutes.EXERCISE_LEVEL) { entry ->
        ExerciseStudioScreen(
            navController,
            manageAll = false,
            levelKey = entry.arguments?.getString("levelKey").orEmpty(),
        )
    }
    composable(TeacherRoutes.EXERCISE_RESULTS) { entry ->
        ExerciseResultsScreen(navController, entry.arguments?.getString("exerciseId").orEmpty())
    }
    composable(TeacherRoutes.EXERCISE_QUESTIONS) { entry ->
        ExerciseQuestionsScreen(navController, entry.arguments?.getString("exerciseId").orEmpty(), manageAll = false)
    }
    composable(TeacherRoutes.STUDENT_ATTEMPT) { entry ->
        StudentAttemptScreen(navController, entry.arguments?.getString("attemptId").orEmpty())
    }
    composable(TeacherRoutes.SUBSCRIPTIONS) { SubscriptionsScreen(navController, session) }
    composable(TeacherRoutes.SUBSCRIPTION_GROUP) { entry ->
        val groupName = java.net.URLDecoder.decode(entry.arguments?.getString("groupName").orEmpty(), "UTF-8")
        SubscriptionGroupDetailScreen(navController, session, groupName)
    }
    composable(TeacherRoutes.UPCOMING_RENEWALS) { UpcomingRenewalsScreen(navController, session) }
    composable(TeacherRoutes.SUBSCRIPTION_EARNINGS) { SubscriptionEarningsScreen(navController, session) }
    composable(TeacherRoutes.DASHBOARD) {
        val sessionState by session.state.collectAsStateWithLifecycle()
        if (!sessionState.signedIn || !sessionState.isTeacher) {
            navController.navigate(Routes.HOME) { popUpTo(navController.graph.startDestinationId) { inclusive = true } }
        } else {
            TeacherDashboardScreen(navController, session)
        }
    }
}

data class StudioData(
    val analytics: JsonObject,
    val balance: TeacherBalance,
    val subStats: com.rork.pro.data.SubscriptionStats? = null,
    /**
     * The teacher's own share of the last 30 days, per revenue stream — null when the figure
     * could not be fetched, so the card shows a dash rather than a zero that would read as
     * "you earned nothing". Weekly is derived from these by EarningsMatrixCard.
     */
    val monthlyCourseEarnings: Double? = null,
    val monthlySubscriptionEarnings: Double? = null,
    /** Subscription requests still waiting on the owner — shown under "needs attention". */
    val pendingRequests: Int = 0,
    /** The teacher's subscription profit share in percent, when one is set. */
    val subscriptionRate: Double? = null,
)

class TeacherStudioViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<StudioData>>(Async.Loading)
    val state: StateFlow<Async<StudioData>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { load() }

    fun refresh() {
        _refreshing.value = true
        load(quiet = true)
    }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            if (!quiet) _state.value = Async.Loading
            // Course + subscription earnings come from ONE server call — the same one the owner
            // reads for this teacher — so the two views cannot disagree.
            val overview = runCatching { TeacherRepository.myEarningsOverview() }.getOrNull()
            runCatching {
                StudioData(
                    analytics = TeacherRepository.analytics(),
                    balance = TeacherRepository.balance(),
                    subStats = runCatching { TeacherRepository.subscriptionStats() }.getOrNull(),
                    monthlyCourseEarnings = overview?.course?.teacherShare,
                    monthlySubscriptionEarnings = overview?.subscriptions?.teacherShare,
                    pendingRequests = runCatching { TeacherRepository.myPendingApprovals().size }.getOrDefault(0),
                    subscriptionRate = runCatching { TeacherRepository.mySubscriptionRate().percentage }.getOrNull()?.takeIf { it > 0 },
                )
            }
                .onSuccess { _state.value = Async.Success(it) }
                .onFailure { if (!quiet) _state.value = Async.Failure(it.toAppError()) }
            _refreshing.value = false
        }
    }
}

fun JsonObject.num(key: String): Double =
    runCatching { this[key]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0 }.getOrDefault(0.0)

fun JsonObject.int(key: String): Int = num(key).toInt()

/**
 * Teacher Studio home, top-down by urgency — the same layout as the owner console:
 *  1. wallet (balance, shares, one door to withdrawals),
 *  2. what needs attention (pending requests, renewals due / overdue),
 *  3. earnings by stream,
 *  4. performance,
 *  5. every tool, grouped, one tap away.
 * Balance, subscription counts and earnings each appear here once; the subscriptions board no
 * longer repeats them, and withdrawals live only in the wallet.
 */
@Composable
fun TeacherStudioScreen(navController: NavHostController, session: SessionViewModel) {
    val vm: TeacherStudioViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load(quiet = true) }

    // The tile only shows for a teacher the owner switched it on for, with at least one course.
    val canGrant by androidx.compose.runtime.produceState(false) {
        value = runCatching { com.rork.pro.data.CourseGrantRepository.myScope() }
            .getOrNull()?.let { it.enabled && it.courses.isNotEmpty() } ?: false
    }

    fun go(route: String) = navController.navigate(route) { launchSingleTop = true }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(tr(StrTeacher.teacherStudio), onBack = { navController.popBackStack() })

        when (val s = state) {
            is Async.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is Async.Failure -> ErrorBlock(s.error, Modifier.fillMaxSize()) { vm.load() }
            is Async.Success -> Refreshable(refreshing, { vm.refresh() }) {
                val a = s.value.analytics
                val balance = s.value.balance
                val stats = s.value.subStats
                LazyColumn(
                    contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    // ── 1. Wallet ──
                    item {
                        val teacher = sessionState.teacherProfile
                        InkCard(borderColor = Ink.Amber.copy(alpha = 0.3f), onClick = { go(TeacherRoutes.EARNINGS) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(tr(StrTeacher.availableBalance), color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        formatMoney(balance.available, balance.currency),
                                        color = Ink.Amber,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                    )
                                }
                                AdminBadgeIcon(Icons.Default.AccountBalanceWallet, tint = Ink.Amber, size = 44)
                            }
                            AdminMetaRow {
                                AdminMeta(trf(StrStudioHub.pendingShort, formatMoney(balance.pending, balance.currency)))
                                AdminMeta(trf(StrStudioHub.withdrawnShort, formatMoney(balance.paid, balance.currency)))
                                teacher?.commissionRate?.let { AdminMeta(trf(StrStudioHub.courseShare, (it * 100).toInt()), Ink.Teal) }
                                s.value.subscriptionRate?.let { AdminMeta(trf(StrStudioHub.subscriptionShare, it.toInt()), Ink.Teal) }
                            }
                            AdminActions {
                                AdminButton(tr(StrStudioHub.wallet), Modifier.weight(1f), Icons.Default.Payments, AdminTone.Primary) {
                                    go(TeacherRoutes.EARNINGS)
                                }
                            }
                        }
                    }

                    // ── 2. Needs attention ──
                    val attention = listOfNotNull(
                        AttentionItem(s.value.pendingRequests, tr(StrStudioHub.requestsShort), Icons.Default.HourglassTop, TeacherRoutes.DASHBOARD)
                            .takeIf { it.count > 0 },
                        stats?.dueThisWeek?.takeIf { it > 0 }?.let {
                            AttentionItem(it, tr(StrStudioHub.dueShort), Icons.Default.Event, TeacherRoutes.UPCOMING_RENEWALS)
                        },
                        stats?.overdue?.takeIf { it > 0 }?.let {
                            AttentionItem(it, tr(StrStudioHub.overdueShort), Icons.Default.Warning, TeacherRoutes.UPCOMING_RENEWALS)
                        },
                    )
                    if (attention.isNotEmpty()) {
                        item { ConsoleSectionTitle(tr(StrStudioHub.needsAttention)) }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                attention.forEach { att -> AttentionCard(att, Modifier.weight(1f)) { go(att.route) } }
                            }
                        }
                    }

                    // ── 3. Earnings ──
                    item {
                        EarningsMatrixCard(
                            title = tr(Str.earningsTitle),
                            monthlyLabel = tr(Str.monthlyLabel),
                            weeklyLabel = tr(Str.weeklyLabel),
                            currency = balance.currency,
                            streams = listOf(
                                EarningsStream(label = tr(Str.myCoursesStream), monthly = s.value.monthlyCourseEarnings, accent = Ink.Teal),
                                EarningsStream(label = tr(Str.mySubscriptionsStream), monthly = s.value.monthlySubscriptionEarnings, accent = Ink.Amber),
                            ),
                        )
                    }

                    // ── 4. Performance ──
                    item { ConsoleSectionTitle(tr(StrTeacher.performance)) }
                    item {
                        val rating = if (a.int("rating_count") > 0) {
                            String.format(com.rork.pro.ui.i18n.AppLanguage.locale(), "%.1f", a.num("rating"))
                        } else "—"
                        val tiles = listOf(
                            StatTileData(a.int("students").toString(), tr(StrTeacher.students), Icons.Default.School, Ink.Sky, null),
                            StatTileData(a.int("enrollments").toString(), tr(StrTeacher.enrollments), Icons.Default.HowToReg, Ink.Sky, null),
                            StatTileData(a.int("courses_published").toString(), tr(StrTeacher.coursesLive), Icons.Default.MenuBook, Ink.Teal, StudioRoutes.content(CourseScope.MINE)),
                            StatTileData("${a.num("completion_rate").toInt()}%", tr(StrTeacher.completion), Icons.Default.TaskAlt, Ink.Teal, null),
                            StatTileData(rating, tr(StrTeacher.ratingLabel), Icons.Default.Star, Ink.Amber, null),
                            StatTileData((stats?.totalSubscriptions ?: 0).toString(), tr(StrStudioHub.activeSubs), Icons.Default.Groups, Ink.Amber, TeacherRoutes.DASHBOARD),
                        )
                        // Plain rows: the old fixed 180dp grid had room for four tiles and hid the fifth.
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            tiles.chunked(2).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { t -> ConsoleStatTile(t, Modifier.weight(1f)) { t.route?.let { r -> go(r) } } }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // ── 5. Quick access ──
                    item { ConsoleSectionTitle(tr(StrStudioHub.quickAccess)) }
                    item {
                        ShortcutGroupCard(
                            ConsoleGroup(
                                tr(StrStudioHub.groupContent), Ink.Teal, Ink.TealSoft,
                                buildList {
                                    // Courses, lessons and exercises now live in one place.
                                    add(ConsoleShortcut(Icons.Default.MenuBook, tr(com.rork.pro.ui.i18n.StrContent.tile), StudioRoutes.content(CourseScope.MINE)))
                                    if (canGrant) {
                                        add(ConsoleShortcut(Icons.Default.LockOpen, tr(com.rork.pro.ui.i18n.StrAdminX.grantTile), TeacherRoutes.COURSE_GRANTS))
                                    }
                                },
                            ),
                        ) { go(it) }
                    }
                    item {
                        ShortcutGroupCard(
                            ConsoleGroup(
                                tr(StrStudioHub.groupSubscriptions), Ink.Amber, Ink.AmberSoft,
                                listOf(
                                    ConsoleShortcut(Icons.Default.Dashboard, tr(StrStudioHub.tileSubsDashboard), TeacherRoutes.DASHBOARD),
                                    ConsoleShortcut(Icons.Default.Groups, tr(StrStudioHub.tileGroups), TeacherRoutes.SUBSCRIPTIONS),
                                    ConsoleShortcut(Icons.Default.CalendarMonth, tr(StrStudioHub.tileRenewals), TeacherRoutes.UPCOMING_RENEWALS),
                                    ConsoleShortcut(Icons.Default.BarChart, tr(StrStudioHub.tileReports), TeacherRoutes.SUBSCRIPTION_EARNINGS),
                                ),
                            ),
                        ) { go(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(value, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
        Text(label, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun StudioRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ink.Surface)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Ink.AmberSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Ink.Amber, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = Ink.TextMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}
