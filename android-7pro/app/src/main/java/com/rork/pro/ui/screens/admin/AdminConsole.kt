package com.rork.pro.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.rork.pro.data.AdminRepository
import com.rork.pro.data.Async
import com.rork.pro.data.CourseEarningsSummary
import com.rork.pro.data.CourseScope
import com.rork.pro.data.toAppError
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.data.ActiveSubscriptionEarnings
import com.rork.pro.data.EarningsWindow
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
import com.rork.pro.ui.screens.teacher.AdminSubscriptionEarningsScreen
import com.rork.pro.ui.screens.teacher.ExerciseQuestionsScreen
import com.rork.pro.ui.screens.teacher.ExerciseStudioScreen
import com.rork.pro.ui.screens.teacher.SubsScope
import com.rork.pro.ui.screens.teacher.SubscriptionGroupDetailScreen
import com.rork.pro.ui.screens.teacher.SubscriptionsScreen
import com.rork.pro.ui.screens.teacher.UpcomingRenewalsScreen
import com.rork.pro.ui.screens.teacher.StudioRow
import com.rork.pro.ui.screens.teacher.int
import com.rork.pro.ui.screens.teacher.num
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.StrAdminHub
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.StrAdminX
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.LockOpen

object AdminRoutes {
    const val CONSOLE = "admin/console"
    const val TEACHERS = "admin/teachers"
    const val ANNOUNCE = "admin/announce"
    const val REVIEW = "admin/review"
    const val PRICING = "admin/pricing"
    const val COUPONS = "admin/coupons"
    const val COURSE_GRANTS = "admin/course-grants"
    const val PAID_STUDENTS = "admin/paid-students"
    /** Registration pattern of the same screen; [paidStudents] builds the link that opens "open a course" on top. */
    const val PAID_STUDENTS_ROUTE = "admin/paid-students?grant={grant}"
    const val PAID_STUDENT = "admin/paid-students/{userId}"
    const val TEACHER_SUBSCRIBERS = "admin/teacher-subscribers"
    const val TEACHER_SUBSCRIBER = "admin/teacher-subscribers/{subId}"
    const val AI_TUTOR_PLANS = "admin/ai-tutor-plans"
    const val FINANCE = "admin/finance"
    const val PAYOUTS = "admin/payouts?teacherId={teacherId}"
    const val USERS = "admin/users"
    const val CMS = "admin/cms"
    const val ADS = "admin/ads"
    const val WEB_ADS = "admin/web-ads"
    const val PAYMENTS = "admin/payments"
    const val BOOKING = "admin/booking"
    const val TEACHER_SHOWCASE = "admin/booking/showcase"
    const val BOOKING_TEACHER_GROUPS = "admin/booking/teacher/{teacherId}?name={teacherName}"
    const val SETTINGS = "admin/settings"
    const val TESTS = "admin/tests"
    const val RECENT_PLACEMENTS = "admin/recent-placements"
    const val ONLINE_NOW = "admin/online-now"
    const val EXERCISES = "admin/exercises"
    const val EXERCISES_ALL = "admin/exercises/all"
    const val EXERCISE_TEACHER_LEVEL = "admin/exercises/teacher/{teacherId}/level/{levelKey}?name={teacherName}"
    const val EXERCISE_TEACHER = "admin/exercises/teacher/{teacherId}?name={teacherName}"
    const val EXERCISE_QUESTIONS = "admin/exercises/{exerciseId}/questions"
    const val MODERATION = "admin/moderation"
    const val SUPPORT = "admin/support"
    const val SUPPORT_TICKETS = "admin/support/tickets"
    const val SUPPORT_CHAT = "admin/support/chat/{chatId}"
    const val AUDIT = "admin/audit"
    const val SUBSCRIPTIONS = "admin/subscriptions?teacherId={teacherId}&name={teacherName}"
    const val SUBSCRIPTION_GROUP = "admin/subscriptions/group/{groupName}?teacherId={teacherId}&name={teacherName}"
    const val UPCOMING_RENEWALS = "admin/subscriptions/renewals?teacherId={teacherId}&name={teacherName}"
    const val SUBSCRIPTION_EARNINGS = "admin/subscription-earnings?teacherId={teacherId}"
    const val APPROVALS = "admin/approvals?teacherId={teacherId}"
    /** One page with every approval queue and its pending count (opened from Home). */
    const val APPROVALS_HUB = "admin/approvals-hub"
    const val TEACHER_DASHBOARD = "admin/teacher-dashboard"
    const val TEACHER_DETAIL = "admin/teacher/{teacherId}"
    const val CLASSROOM = "admin/classroom"
    const val MEETING_SERVERS = "admin/meeting-servers"

    /** With [teacherId] the screens open scoped to that one teacher, exactly as the teacher sees them. */
    fun subscriptionGroup(name: String, teacherId: String? = null, teacherName: String = "") =
        "admin/subscriptions/group/${java.net.URLEncoder.encode(name, "UTF-8")}" + scopeQuery(teacherId, teacherName)
    fun upcomingRenewals(teacherId: String? = null, teacherName: String = "") =
        "admin/subscriptions/renewals" + scopeQuery(teacherId, teacherName)
    private fun scopeQuery(teacherId: String?, teacherName: String) =
        if (teacherId == null) "" else "?teacherId=$teacherId&name=${java.net.URLEncoder.encode(teacherName, "UTF-8")}"
    fun paidStudents(openGrant: Boolean = false) = "admin/paid-students?grant=$openGrant"
    fun teacherDetail(id: String) = "admin/teacher/$id"
    fun bookingTeacherGroups(teacherId: String, teacherName: String) =
        "admin/booking/teacher/$teacherId?name=${java.net.URLEncoder.encode(teacherName, "UTF-8")}"

    // Every step that shows teacher-specific work (approvals, all
    // subscriptions/groups, earnings, withdrawals) is opened ONLY after a
    // teacher has been chosen on the teacher dashboard — these builders are
    // the single place that constructs those pre-filtered destinations.
    fun approvals(teacherId: String) = "admin/approvals?teacherId=$teacherId"

    /** The pending-requests queue across every teacher (what the Home shortcut opens). */
    fun allApprovals() = "admin/approvals"
    fun subscriptions(teacherId: String, teacherName: String = "") =
        "admin/subscriptions?teacherId=$teacherId&name=${java.net.URLEncoder.encode(teacherName, "UTF-8")}"
    fun subscriptionEarnings(teacherId: String) = "admin/subscription-earnings?teacherId=$teacherId"
    fun payouts(teacherId: String) = "admin/payouts?teacherId=$teacherId"
    fun exerciseQuestions(exerciseId: String) = "admin/exercises/$exerciseId/questions"
    fun exerciseTeacherLevel(teacherId: String, teacherName: String, levelKey: String) =
        "admin/exercises/teacher/$teacherId/level/$levelKey?name=${java.net.URLEncoder.encode(teacherName, "UTF-8")}"
    fun exerciseTeacher(teacherId: String, teacherName: String) =
        "admin/exercises/teacher/$teacherId?name=${java.net.URLEncoder.encode(teacherName, "UTF-8")}"
}

fun NavGraphBuilder.adminGraph(navController: NavHostController, session: SessionViewModel) {
    composable(AdminRoutes.CONSOLE) {
        val sessionState by session.state.collectAsStateWithLifecycle()
        if (!sessionState.isStaff) {
            // Non-staff users cannot access admin routes - redirect to home
            navController.navigate(Routes.HOME) { popUpTo(navController.graph.startDestinationId) { inclusive = true } }
        } else {
            AdminConsoleScreen(navController, session)
        }
    }
    // The old teachers list lives inside the teacher console now; the route stays so any
    // saved link still lands in the right place.
    composable(AdminRoutes.TEACHERS) { AdminTeacherDashboardScreen(navController, session) }
    composable(AdminRoutes.ANNOUNCE) { AdminAnnouncementsScreen(navController) }
    composable(AdminRoutes.REVIEW) { AdminReviewScreen(navController) }
    composable(AdminRoutes.PRICING) { AdminPricingScreen(navController) }
    composable(AdminRoutes.COUPONS) { AdminCouponsScreen(navController, session) }
    // Opening a course for someone is part of the paid-course students screen now. The old route
    // stays so a saved link still lands in the right place.
    composable(AdminRoutes.COURSE_GRANTS) {
        LaunchedEffect(Unit) {
            navController.navigate(AdminRoutes.paidStudents(true)) {
                popUpTo(AdminRoutes.COURSE_GRANTS) { inclusive = true }
            }
        }
    }
    composable(
        AdminRoutes.PAID_STUDENTS_ROUTE,
        arguments = listOf(
            androidx.navigation.navArgument("grant") {
                type = androidx.navigation.NavType.BoolType
                defaultValue = false
            },
        ),
    ) { entry ->
        PaidStudentsScreen(navController, session, startWithGrant = entry.arguments?.getBoolean("grant") == true)
    }
    composable(AdminRoutes.PAID_STUDENT) { entry ->
        PaidStudentDetailScreen(navController, entry.arguments?.getString("userId").orEmpty())
    }
    composable(AdminRoutes.TEACHER_SUBSCRIBERS) { TeacherSubscribersScreen(navController, session) }
    composable(AdminRoutes.TEACHER_SUBSCRIBER) { entry ->
        TeacherSubscriberDetailScreen(navController, entry.arguments?.getString("subId").orEmpty())
    }
    composable(AdminRoutes.AI_TUTOR_PLANS) { AdminAiTutorPlansScreen(navController, session) }
    composable(AdminRoutes.FINANCE) { AdminFinanceScreen(navController) }
    composable(
        AdminRoutes.PAYOUTS,
        arguments = listOf(androidx.navigation.navArgument("teacherId") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null }),
    ) { entry ->
        AdminPayoutsScreen(navController, entry.arguments?.getString("teacherId"))
    }
    composable(AdminRoutes.USERS) { AdminUsersScreen(navController, session) }
    composable(AdminRoutes.CMS) { AdminCmsScreen(navController) }
    composable(AdminRoutes.ADS) { AdminAdsScreen(navController) }
    composable(AdminRoutes.WEB_ADS) { AdminWebAdsScreen(navController) }
    composable(AdminRoutes.PAYMENTS) { AdminPaymentsScreen(navController) }
    composable(AdminRoutes.BOOKING) { AdminBookingScreen(navController) }
    composable(AdminRoutes.TEACHER_SHOWCASE) { AdminTeacherShowcaseScreen(navController) }
    composable(
        AdminRoutes.BOOKING_TEACHER_GROUPS,
        arguments = listOf(
            androidx.navigation.navArgument("teacherId") { type = androidx.navigation.NavType.StringType },
            androidx.navigation.navArgument("teacherName") { type = androidx.navigation.NavType.StringType; defaultValue = "" },
        ),
    ) { entry ->
        val teacherId = entry.arguments?.getString("teacherId").orEmpty()
        val teacherName = java.net.URLDecoder.decode(entry.arguments?.getString("teacherName").orEmpty(), "UTF-8")
        AdminTeacherBookingGroupsScreen(navController, teacherId, teacherName)
    }
    composable(AdminRoutes.SETTINGS) { AdminSettingsScreen(navController, session) }
    composable(AdminRoutes.MEETING_SERVERS) { AdminMeetingServersScreen(navController, session) }
    composable(AdminRoutes.TESTS) { AdminTestsScreen(navController) }
    composable(AdminRoutes.RECENT_PLACEMENTS) { AdminRecentPlacementsScreen(navController) }
    composable(AdminRoutes.ONLINE_NOW) { AdminOnlineNowScreen(navController) }
    composable(AdminRoutes.EXERCISES) { AdminExerciseTeachersScreen(navController) }
    composable(AdminRoutes.EXERCISES_ALL) { ExerciseStudioScreen(navController, manageAll = true) }
    composable(
        AdminRoutes.EXERCISE_TEACHER,
        arguments = listOf(
            androidx.navigation.navArgument("teacherId") { type = androidx.navigation.NavType.StringType },
            androidx.navigation.navArgument("teacherName") { type = androidx.navigation.NavType.StringType; defaultValue = "" },
        ),
    ) { entry ->
        val teacherId = entry.arguments?.getString("teacherId").orEmpty()
        val teacherName = java.net.URLDecoder.decode(entry.arguments?.getString("teacherName").orEmpty(), "UTF-8")
        ExerciseStudioScreen(navController, manageAll = true, teacherId = teacherId, teacherName = teacherName)
    }
    composable(
        AdminRoutes.EXERCISE_TEACHER_LEVEL,
        arguments = listOf(
            androidx.navigation.navArgument("teacherId") { type = androidx.navigation.NavType.StringType },
            androidx.navigation.navArgument("levelKey") { type = androidx.navigation.NavType.StringType },
            androidx.navigation.navArgument("teacherName") { type = androidx.navigation.NavType.StringType; defaultValue = "" },
        ),
    ) { entry ->
        val teacherId = entry.arguments?.getString("teacherId").orEmpty()
        val teacherName = java.net.URLDecoder.decode(entry.arguments?.getString("teacherName").orEmpty(), "UTF-8")
        ExerciseStudioScreen(
            navController,
            manageAll = true,
            levelKey = entry.arguments?.getString("levelKey").orEmpty(),
            teacherId = teacherId,
            teacherName = teacherName,
        )
    }
    composable(AdminRoutes.EXERCISE_QUESTIONS) { entry ->
        ExerciseQuestionsScreen(navController, entry.arguments?.getString("exerciseId").orEmpty(), manageAll = true)
    }
    composable(AdminRoutes.MODERATION) { AdminModerationScreen(navController) }
    composable(AdminRoutes.SUPPORT) {
        com.rork.pro.ui.screens.support.SupportInboxScreen(navController) {
            navController.navigate(AdminRoutes.SUPPORT_TICKETS) { launchSingleTop = true }
        }
    }
    composable(AdminRoutes.SUPPORT_TICKETS) { AdminSupportScreen(navController) }
    composable(AdminRoutes.SUPPORT_CHAT) { entry ->
        val sessionState by session.state.collectAsStateWithLifecycle()
        com.rork.pro.ui.screens.support.SupportChatScreen(
            navController,
            entry.arguments?.getString("chatId"),
            canOpenCourse = sessionState.can("courses.grant"),
            onOpenCourse = { navController.navigate(AdminRoutes.paidStudents(true)) { launchSingleTop = true } },
        )
    }
    composable(AdminRoutes.AUDIT) { AdminAuditScreen(navController) }
    // Subscriptions: same screens as the teacher flow, in Staff scope (all
    // teachers, approve/reject, admin form with a teacher field) — unified
    // to remove the previous duplicate Admin*SubscriptionsScreen set.
    // With a teacher chosen, the screens are that teacher's own (same controls, only their data);
    // without one they stay the all-teachers overview.
    val scopeArgs = listOf(
        androidx.navigation.navArgument("teacherId") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
        androidx.navigation.navArgument("teacherName") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
    )
    fun androidx.navigation.NavBackStackEntry.subsScope(): SubsScope {
        val tid = arguments?.getString("teacherId")?.takeIf { it.isNotBlank() } ?: return SubsScope.Staff
        val name = java.net.URLDecoder.decode(arguments?.getString("teacherName").orEmpty(), "UTF-8")
        return SubsScope.StaffOf(tid, name)
    }
    composable(AdminRoutes.SUBSCRIPTIONS, arguments = scopeArgs) { entry ->
        SubscriptionsScreen(navController, session, scope = entry.subsScope(), teacherIdFilter = entry.arguments?.getString("teacherId"))
    }
    composable(AdminRoutes.SUBSCRIPTION_GROUP, arguments = scopeArgs) { entry ->
        val groupName = java.net.URLDecoder.decode(entry.arguments?.getString("groupName").orEmpty(), "UTF-8")
        SubscriptionGroupDetailScreen(navController, session, groupName, scope = entry.subsScope())
    }
    composable(AdminRoutes.UPCOMING_RENEWALS, arguments = scopeArgs) { entry ->
        UpcomingRenewalsScreen(navController, session, scope = entry.subsScope())
    }
    composable(
        AdminRoutes.SUBSCRIPTION_EARNINGS,
        arguments = listOf(androidx.navigation.navArgument("teacherId") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null }),
    ) { entry ->
        AdminSubscriptionEarningsScreen(navController, session, initialTeacherId = entry.arguments?.getString("teacherId"))
    }
    composable(
        AdminRoutes.APPROVALS,
        arguments = listOf(androidx.navigation.navArgument("teacherId") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null }),
    ) { entry ->
        AdminApprovalsScreen(navController, teacherId = entry.arguments?.getString("teacherId"))
    }
    composable(AdminRoutes.APPROVALS_HUB) { ApprovalsHubScreen(navController, session) }
    composable(AdminRoutes.TEACHER_DASHBOARD) { AdminTeacherDashboardScreen(navController, session) }
    composable(AdminRoutes.TEACHER_DETAIL) { entry ->
        // Teacher profile = the teacher console opened on that teacher.
        AdminTeacherDashboardScreen(navController, session, initialTeacherId = entry.arguments?.getString("teacherId"))
    }
    composable(AdminRoutes.CLASSROOM) {
        com.rork.pro.ui.screens.classroom.ClassroomHomeScreen(
            navController, session, com.rork.pro.ui.screens.classroom.ClassroomScope.STAFF,
        )
    }
}

class AdminConsoleViewModel : ViewModel() {
    private val _state = MutableStateFlow<Async<JsonObject>>(Async.Loading)
    val state: StateFlow<Async<JsonObject>> = _state.asStateFlow()

    /**
     * Transfers waiting on a decision.
     *
     * Kept beside the analytics rather than inside them: a learner who has already sent money is
     * blocked until someone approves it, so the count belongs on the first screen staff open.
     */
    private val _pendingTransfers = MutableStateFlow(0)
    val pendingTransfers: StateFlow<Int> = _pendingTransfers.asStateFlow()

    // The two revenue streams the platform actually has — course sales and teacher
    // subscriptions — each fetched and shown as its own card (see AdminConsoleScreen) instead
    // of the old single "net revenue" figure, which was built from orders alone and silently
    // left teacher-subscription income out entirely.
    private val _courseEarnings = MutableStateFlow<Async<CourseEarningsSummary>>(Async.Loading)
    val courseEarnings: StateFlow<Async<CourseEarningsSummary>> = _courseEarnings.asStateFlow()

    // Subscription income is read from the subscriptions that exist and are active — not from
    // the accrual ledger prorated over a window, which reported a live 400/month subscription
    // as a fraction of itself depending on what day it started.
    private val _subscriptionEarnings = MutableStateFlow<Async<ActiveSubscriptionEarnings>>(Async.Loading)
    val subscriptionEarnings: StateFlow<Async<ActiveSubscriptionEarnings>> = _subscriptionEarnings.asStateFlow()

    // Weekly counterparts — no longer their own API call. They're a straight quarter of the
    // rolling 30-day figure above (30 days ≈ 4 weeks), so the two week cards stay in sync with
    // the monthly ones by construction instead of drifting from a second, separately-fetched
    // "week" filter.
    /** Drives the pull-to-refresh indicator: true until every parallel load below settles. */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { load() }

    /**
     * Reloads everything without blanking the screen first — for pull-to-refresh, and for
     * coming back to this screen after approving a transfer or a subscription, where the
     * numbers on the cards are exactly what changed.
     */
    fun refresh() {
        _refreshing.value = true
        load(quiet = true)
    }

    fun load(quiet: Boolean = false) {
        viewModelScope.launch {
            // Course transfers and group-subscription transfers are reviewed on the same
            // screen, so the badge counts both — a queue the owner cannot see is a learner
            // and a student left waiting.
            val courses = runCatching { AdminRepository.manualPaymentPendingCount() }.getOrDefault(0)
            val subscriptions = AdminRepository.subscriptionPaymentPendingCount()
            _pendingTransfers.value = courses + subscriptions
        }
        viewModelScope.launch {
            if (!quiet) _state.value = Async.Loading
            runCatching { AdminRepository.analytics() }
                .onSuccess { _state.value = Async.Success(it) }
                .onFailure { _state.value = Async.Failure(it.toAppError()) }
        }
        viewModelScope.launch {
            // Both earnings queries run in parallel inside one scope, so the refresh indicator
            // can clear when the slower of the two has actually landed — not when whichever
            // one happens to finish first does.
            kotlinx.coroutines.coroutineScope {
                launch {
                    if (!quiet) _courseEarnings.value = Async.Loading
                    runCatching {
                        // EarningsWindow is the one definition of this period, shared with the
                        // teacher studio — so the owner and a teacher are never comparing
                        // different windows.
                        AdminRepository.allCourseEarningsSummary(
                            filter = "custom",
                            fromDate = EarningsWindow.from(),
                            toDate = EarningsWindow.to(),
                        )
                    }.onSuccess { _courseEarnings.value = Async.Success(it) }
                        .onFailure { _courseEarnings.value = Async.Failure(it.toAppError()) }
                }
                launch {
                    if (!quiet) _subscriptionEarnings.value = Async.Loading
                    runCatching { AdminRepository.activeSubscriptionEarnings() }
                        .onSuccess { _subscriptionEarnings.value = Async.Success(it) }
                        .onFailure { _subscriptionEarnings.value = Async.Failure(it.toAppError()) }
                }
            }
            _refreshing.value = false
        }
    }
}

/**
 * The one number the console still needs from each stream: the 30-day total, or null while it
 * is loading or after it failed — which the card renders as a dash instead of a zero that
 * would read as "you earned nothing".
 *
 * Two distinctly-named functions rather than one overloaded on receiver type: Async<X> and
 * Async<Y> both erase to plain Async on the JVM, so a single name for both is a platform
 * declaration clash — legal-looking Kotlin that Kotlin itself refuses to compile.
 */
@JvmName("courseMonthlyTotal")
private fun Async<CourseEarningsSummary>.monthlyTotal(): Double? =
    (this as? Async.Success)?.value?.totalEarned

@JvmName("subscriptionMonthlyTotal")
private fun Async<ActiveSubscriptionEarnings>.monthlyTotal(): Double? =
    (this as? Async.Success)?.value?.total

/**
 * The owner's net earnings over the same window as the two streams beside it: the owner share
 * of course sales (already net of refunds — see CourseOrderRow.net) plus the owner share of the
 * active teacher subscriptions. Null unless BOTH streams have loaded, so a half-loaded or failed
 * stream shows a dash rather than a figure that quietly leaves one of them out.
 */
private fun ownerNetMonthly(
    courses: Async<CourseEarningsSummary>,
    subscriptions: Async<ActiveSubscriptionEarnings>,
): Double? {
    val c = (courses as? Async.Success)?.value ?: return null
    val s = (subscriptions as? Async.Success)?.value ?: return null
    return c.ownerShare + s.ownerShare
}

/** One tile in the console's quick-access grid. [badge] > 0 draws a count on the icon. */
internal data class ConsoleShortcut(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String,
    val route: String,
    val badge: Int = 0,
)

internal data class ConsoleGroup(val title: String, val tint: Color, val tintSoft: Color, val items: List<ConsoleShortcut>)

internal data class AttentionItem(val count: Int, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String)

/**
 * Owner / admin console.
 *
 * Laid out top-down by how urgently each thing is needed:
 *  1. what is blocking someone right now (transfers, course reviews, tickets) — tappable counts;
 *  2. the two revenue streams;
 *  3. platform numbers, each one a shortcut to the screen it describes;
 *  4. every tool, grouped by area as a compact icon grid — one tap from here, no long list.
 * Everything is still gated on the same permissions as before; an admin simply sees fewer tiles.
 */
@Composable
fun AdminConsoleScreen(navController: NavHostController, session: SessionViewModel) {
    val vm: AdminConsoleViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val pendingTransfers by vm.pendingTransfers.collectAsStateWithLifecycle()
    val courseEarnings by vm.courseEarnings.collectAsStateWithLifecycle()
    val subscriptionEarnings by vm.subscriptionEarnings.collectAsStateWithLifecycle()
    val sessionState by session.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.load(quiet = true) }

    fun go(route: String) = navController.navigate(route) { launchSingleTop = true }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(
            if (sessionState.isOwner) tr(StrAdminHub.ownerConsole) else tr(StrAdminHub.adminConsole),
            onBack = { navController.popBackStack() },
        )

        Refreshable(refreshing, { vm.refresh() }) {
        LazyColumn(
            contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (val s = state) {
                is Async.Loading -> item { LoadingBlock() }
                is Async.Failure -> item { ErrorBlock(s.error) { vm.load() } }
                is Async.Success -> {
                    val a = s.value
                    val pendingCourses = a.int("courses_pending")
                    val openTickets = a.int("open_tickets")
                    val canMoney = sessionState.can("finance.manage") || sessionState.can("settings.manage")
                    val canBooking = sessionState.can("pricing.manage") || sessionState.can("settings.manage") || sessionState.isOwner

                    // ── 1. Needs attention ──
                    val attention = buildList {
                        if (canMoney) add(AttentionItem(pendingTransfers, tr(StrAdminHub.transfersShort), Icons.Default.Payments, AdminRoutes.PAYMENTS))
                        if (sessionState.can("courses.manage")) add(AttentionItem(pendingCourses, tr(StrAdminHub.reviewShort), Icons.Default.FactCheck, AdminRoutes.REVIEW))
                        if (sessionState.can("support.manage")) add(AttentionItem(openTickets, tr(com.rork.pro.ui.i18n.StrSupport.attentionLabel), Icons.Default.SupportAgent, AdminRoutes.SUPPORT))
                    }
                    if (attention.isNotEmpty()) {
                        item { ConsoleSectionTitle(tr(StrAdminHub.needsAttention)) }
                        item {
                            if (attention.all { it.count == 0 }) {
                                InkCard(color = Ink.TealSoft, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Default.CheckCircle, null, tint = Ink.Teal, modifier = Modifier.size(20.dp))
                                        Text(tr(StrAdminHub.allClear), color = Ink.Teal, style = MaterialTheme.typography.titleSmall)
                                    }
                                }
                            } else {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    attention.forEach { att ->
                                        AttentionCard(att, Modifier.weight(1f)) { go(att.route) }
                                    }
                                }
                            }
                        }
                    }

                    // ── 2. Earnings ──
                    item {
                        EarningsMatrixCard(
                            title = tr(Str.earningsTitle),
                            monthlyLabel = tr(Str.monthlyLabel),
                            weeklyLabel = tr(Str.weeklyLabel),
                            streams = listOf(
                                EarningsStream(label = tr(Str.coursesStream), monthly = courseEarnings.monthlyTotal(), accent = Ink.Teal),
                                EarningsStream(label = tr(Str.subscriptionsStream), monthly = subscriptionEarnings.monthlyTotal(), accent = Ink.Amber),
                                // The platform's own cut of the two streams above. Weekly is derived
                                // (÷ 4) inside EarningsMatrixCard like every other row.
                                EarningsStream(
                                    label = tr(Str.ownerNetStream),
                                    monthly = ownerNetMonthly(courseEarnings, subscriptionEarnings),
                                    accent = Ink.Sky,
                                ),
                            ),
                        )
                    }

                    // ── 3. Platform numbers (each a shortcut) ──
                    item { ConsoleSectionTitle(tr(StrAdminHub.platform)) }
                    item {
                        val canUsers = sessionState.can("users.manage") || sessionState.isOwner
                        val stats = listOf(
                            StatTileData(a.int("users_total").toString(), tr(StrAdminHub.users), Icons.Default.Groups, Ink.Sky, AdminRoutes.USERS.takeIf { canUsers }),
                            StatTileData(a.int("teachers").toString(), tr(StrAdminHub.teachers), Icons.Default.ManageAccounts, Ink.Amber, AdminRoutes.TEACHER_DASHBOARD),
                            StatTileData(a.int("courses_published").toString(), tr(StrAdminHub.coursesLive), Icons.Default.LibraryBooks, Ink.Teal, StudioRoutes.content(CourseScope.ALL).takeIf { sessionState.can("courses.manage") || sessionState.can("course_content.manage") }),
                            StatTileData(a.int("paid_buyers").toString(), tr(StrAdminX.tilePaidBuyers), Icons.Default.Sell, Ink.Teal, AdminRoutes.PAID_STUDENTS.takeIf { sessionState.can("courses.grant") || sessionState.can("finance.read") }),
                            StatTileData(a.int("teacher_subscribers").toString(), tr(StrAdminX.tileSubscribers), Icons.Default.School, Ink.Amber, AdminRoutes.TEACHER_SUBSCRIBERS.takeIf { sessionState.can("teachers.manage") }),
                            StatTileData(a.int("online_now").toString(), tr(StrAdminHub.onlineNow), Icons.Default.Wifi, Ink.Coral, AdminRoutes.ONLINE_NOW.takeIf { sessionState.can("analytics.read") }),
                            StatTileData(a.int("support_waiting").toString(), tr(StrAdminX.tileSupportWaiting), Icons.Default.SupportAgent, Ink.Coral, AdminRoutes.SUPPORT.takeIf { sessionState.can("support.manage") }),
                        )
                        // Plain rows rather than a fixed-height lazy grid: the old 240dp grid only
                        // had room for six tiles and silently cut off refunds and payouts.
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            stats.chunked(2).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { st -> ConsoleStatTile(st, Modifier.weight(1f)) { st.route?.let { r -> go(r) } } }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    // ── 4. Quick access, grouped ──
                    val groups = listOf(
                        ConsoleGroup(tr(StrAdminHub.groupTeachers), Ink.Amber, Ink.AmberSoft, buildList {
                            // One teacher hub (accounts, approvals, subscriptions, earnings, payouts)
                            // and one booking entry (prices + display order).
                            if (canBooking) add(ConsoleShortcut(Icons.Default.EventSeat, tr(StrAdminHub.tileBooking), AdminRoutes.BOOKING))
                        }),
                        ConsoleGroup(tr(StrAdminHub.groupContent), Ink.Teal, Ink.TealSoft, buildList {
                            // One entry for every course, its lessons, and all teachers' exercises.
                            if (sessionState.can("courses.manage") || sessionState.can("course_content.manage") || sessionState.can("tests.manage")) {
                                add(ConsoleShortcut(Icons.Default.LibraryBooks, tr(com.rork.pro.ui.i18n.StrContent.tile), StudioRoutes.content(CourseScope.ALL)))
                            }
                            if (sessionState.can("courses.manage")) {
                                add(ConsoleShortcut(Icons.Default.FactCheck, tr(StrAdminHub.tileReview), AdminRoutes.REVIEW))
                            }
                            if (sessionState.can("tests.manage")) {
                                add(ConsoleShortcut(Icons.Default.Quiz, tr(StrAdminHub.tileTests), AdminRoutes.TESTS))
                                add(ConsoleShortcut(Icons.Default.DoneAll, tr(StrAdmin.recentPlacementResults), AdminRoutes.RECENT_PLACEMENTS))
                            }
                            if (sessionState.can("classroom.manage")) add(ConsoleShortcut(Icons.Default.Videocam, tr(StrAdminHub.tileClassrooms), AdminRoutes.CLASSROOM))
                            if (sessionState.can("reviews.manage")) add(ConsoleShortcut(Icons.Default.Gavel, tr(StrAdminHub.tileRatings), AdminRoutes.MODERATION))
                        }),
                        ConsoleGroup(tr(StrAdminHub.groupMoney), Ink.Sky, Ink.SkySoft, buildList {
                            if (canMoney) add(ConsoleShortcut(Icons.Default.Payments, tr(StrAdminHub.tilePayments), AdminRoutes.PAYMENTS))
                            if (sessionState.can("finance.read")) add(ConsoleShortcut(Icons.AutoMirrored.Filled.ReceiptLong, tr(StrAdminHub.tileOrders), AdminRoutes.FINANCE))
                            if (sessionState.can("pricing.manage")) add(ConsoleShortcut(Icons.Default.CurrencyExchange, tr(StrAdminHub.tilePricing), AdminRoutes.PRICING))
                            if (sessionState.can("coupons.manage")) add(ConsoleShortcut(Icons.Default.Discount, tr(StrAdminHub.tileCoupons), AdminRoutes.COUPONS))
                            if (sessionState.can("ai_tutor.manage")) add(ConsoleShortcut(Icons.Default.Sell, tr(com.rork.pro.ui.i18n.StrAiPlans.adminTile), AdminRoutes.AI_TUTOR_PLANS))
                        }),
                        ConsoleGroup(tr(StrAdminHub.groupMarketing), Ink.Coral, Ink.CoralSoft, buildList {
                            if (sessionState.can("notifications.manage")) add(ConsoleShortcut(Icons.Default.Campaign, tr(StrAdminHub.tileAnnounce), AdminRoutes.ANNOUNCE))
                            if (sessionState.can("cms.manage")) add(ConsoleShortcut(Icons.Default.ViewCarousel, tr(StrAdminHub.tileHome), AdminRoutes.CMS))
                            if (sessionState.can("ads.manage")) add(ConsoleShortcut(Icons.Default.Tv, tr(StrAdminHub.tileAds), AdminRoutes.ADS))
                            if (sessionState.can("ads.manage")) add(ConsoleShortcut(Icons.Default.Campaign, tr(com.rork.pro.ui.i18n.StrWebAds.tile), AdminRoutes.WEB_ADS))
                        }),
                        ConsoleGroup(tr(StrAdminHub.groupSystem), Ink.Neutral, Ink.NeutralSoft, buildList {
                            if (sessionState.can("settings.manage")) add(ConsoleShortcut(Icons.Default.Settings, tr(StrAdminHub.tileSettings), AdminRoutes.SETTINGS))
                            if (sessionState.can("meeting_servers.manage")) add(ConsoleShortcut(Icons.Default.Dns, tr(com.rork.pro.ui.i18n.StrMeetingServers.tile), AdminRoutes.MEETING_SERVERS))
                            if (sessionState.can("audit.read")) add(ConsoleShortcut(Icons.Default.History, tr(StrAdminHub.tileAudit), AdminRoutes.AUDIT))
                        }),
                    ).filter { it.items.isNotEmpty() }

                    item { ConsoleSectionTitle(tr(StrAdminHub.quickAccess)) }
                    groups.forEach { group ->
                        item(key = group.title) { ShortcutGroupCard(group) { go(it) } }
                    }
                }
            }
        }
        }
    }
}

@Composable
internal fun ConsoleSectionTitle(text: String) {
    Text(
        text,
        color = Ink.TextPrimary,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
internal fun AttentionCard(item: AttentionItem, modifier: Modifier, onClick: () -> Unit) {
    val active = item.count > 0
    val tint = if (active) Ink.Coral else Ink.TextMuted
    InkCard(
        modifier = modifier,
        onClick = onClick,
        color = if (active) Ink.CoralSoft else Ink.Surface,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(item.icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Text(item.count.toString(), color = if (active) Ink.Coral else Ink.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        Text(item.label, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

internal data class StatTileData(
    val value: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val route: String?,
)

@Composable
internal fun ConsoleStatTile(data: StatTileData, modifier: Modifier, onClick: () -> Unit) {
    InkCard(
        modifier = modifier,
        onClick = if (data.route != null) onClick else null,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(data.tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(data.icon, null, tint = data.tint, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f)) {
                Text(data.value, color = Ink.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(data.label, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
        }
    }
}

@Composable
internal fun ShortcutGroupCard(group: ConsoleGroup, onOpen: (String) -> Unit) {
    InkCard(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp)) {
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.size(width = 4.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(group.tint))
            Text(group.title, color = Ink.TextSecondary, style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(10.dp))
        // Four across keeps every group to one or two short rows; empty cells hold the grid.
        group.items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { sc -> ShortcutTile(sc, group, Modifier.weight(1f)) { onOpen(sc.route) } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun ShortcutTile(sc: ConsoleShortcut, group: ConsoleGroup, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(16.dp)).background(group.tintSoft),
                contentAlignment = Alignment.Center,
            ) { Icon(sc.icon, null, tint = group.tint, modifier = Modifier.size(24.dp)) }
            if (sc.badge > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-4).dp)
                        .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                        .clip(CircleShape)
                        .background(Ink.Coral)
                        .padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (sc.badge > 99) "99+" else sc.badge.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            sc.label,
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            minLines = 2,
        )
    }
}
