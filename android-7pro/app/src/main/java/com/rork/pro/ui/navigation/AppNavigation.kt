package com.rork.pro.ui.navigation

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rork.pro.data.PresenceHeartbeat
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.rememberNavController
import com.rork.pro.BuildConfig
import com.rork.pro.data.AppLaunchState
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.screens.admin.adminGraph
import com.rork.pro.ui.screens.auth.AuthScreen
import com.rork.pro.ui.screens.booking.BookingDetailsScreen
import com.rork.pro.ui.screens.booking.BookingGroupsScreen
import com.rork.pro.ui.screens.booking.BookingPaymentScreen
import com.rork.pro.ui.screens.booking.BookingTeachersScreen
import com.rork.pro.ui.screens.courses.CourseDetailScreen
import com.rork.pro.ui.screens.courses.CoursePlayerScreen
import com.rork.pro.ui.screens.courses.CoursesScreen
import com.rork.pro.ui.screens.home.HomeScreen
import com.rork.pro.ui.screens.onboarding.OnboardingScreen
import com.rork.pro.ui.screens.profile.CertificatesScreen
import com.rork.pro.ui.screens.profile.EditProfileScreen
import com.rork.pro.ui.screens.profile.NotificationSettingsScreen
import com.rork.pro.ui.screens.profile.NotificationsScreen
import com.rork.pro.ui.screens.profile.OrdersScreen
import com.rork.pro.ui.screens.profile.ProfileScreen
import com.rork.pro.ui.screens.profile.SupportScreen
import com.rork.pro.ui.screens.test.ExerciseTeachersScreen
import com.rork.pro.ui.screens.test.LevelExercisesScreen
import com.rork.pro.ui.screens.test.PlacementResultScreen
import com.rork.pro.ui.screens.test.PlacementRunnerScreen
import com.rork.pro.ui.screens.test.PlacementStartScreen
import com.rork.pro.ui.screens.test.TeacherExercisesScreen
import com.rork.pro.ui.screens.test.TestsHubScreen
import com.rork.pro.ui.screens.update.UpdateRequiredScreen
import com.rork.pro.ui.screens.classroom.classroomGraph
import com.rork.pro.ui.screens.studio.studioGraph
import com.rork.pro.ui.screens.teacher.teacherGraph
import com.rork.pro.R
import com.rork.pro.ui.theme.Ink
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.sp
import com.rork.pro.ui.theme.HomeSky
import com.rork.pro.ui.theme.AppPalette
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import com.rork.pro.ui.theme.appBackdrop
import kotlinx.coroutines.delay
import com.rork.pro.ui.i18n.StrAuth
import com.rork.pro.ui.i18n.StrNav
import com.rork.pro.ui.i18n.Tr
import com.rork.pro.ui.i18n.tr

/**
 * Where a tapped notification wants the app to go.
 *
 * The notification is handled by the activity long before the navigator exists, so the route is
 * parked here and consumed once — reopening the app later never replays an old tap.
 */
object PendingRoute {
    private var route: String? by mutableStateOf(null)

    fun set(value: String) {
        route = value
    }

    fun consume(): String? {
        val current = route
        route = null
        return current
    }

    val pending: String? get() = route
}

object Routes {
    const val HOME = "home"
    const val COURSES = "courses"
    const val PROFILE = "profile"

    const val COURSE_DETAIL = "course/{courseId}"
    const val COURSE_PLAYER = "player/{courseId}?lessonId={lessonId}"

    const val TEST_START = "test-start"
    const val TEST_PLACEMENT = "test-placement"
    const val EXERCISE_TEACHERS = "exercise-teachers"
    const val TEACHER_EXERCISES = "exercises/{teacherId}"
    const val LEVEL_EXERCISES = "exercises/{teacherId}/level/{levelKey}"
    const val TEST_RUN = "test-run/{testId}"
    const val TEST_RESULT = "test-result/{attemptId}"

    // The group-booking funnel: teacher -> group -> details -> wallet payment.
    const val BOOKING_TEACHERS = "booking"
    const val BOOKING_GROUPS = "booking/teacher/{teacherId}"
    const val BOOKING_DETAILS = "booking/group/{groupId}"
    const val BOOKING_PAY = "booking/pay/{subscriptionId}"

    const val ORDERS = "orders"
    const val CERTIFICATES = "certificates"
    const val NOTIFICATIONS = "notifications"
    const val NOTIFICATION_SETTINGS = "notification-settings"
    const val SUPPORT = "support"
    const val SUPPORT_CHAT = "support/chat/{chatId}"
    const val SUPPORT_NEW = "support/new/{topic}"
    const val EDIT_PROFILE = "edit-profile"
    const val AI_TUTOR = "ai-tutor"
    const val AI_TUTOR_CHARACTERS = "ai-tutor-characters"
    const val AI_TUTOR_PLANS = "ai-tutor-plans"
    const val DUBBING = "dubbing"

    fun courseDetail(id: String) = "course/$id"
    fun coursePlayer(id: String, lessonId: String? = null) =
        if (lessonId.isNullOrBlank()) "player/$id" else "player/$id?lessonId=$lessonId"
    fun teacherExercises(id: String) = "exercises/$id"
    /** [levelKey] is the level's id, or "none" for exercises the teacher never filed. */
    fun levelExercises(teacherId: String, levelKey: String) = "exercises/$teacherId/level/$levelKey"
    fun testRun(id: String) = "test-run/$id"
    fun testResult(id: String) = "test-result/$id"
    fun bookingGroups(teacherId: String) = "booking/teacher/$teacherId"
    fun bookingDetails(groupId: String) = "booking/group/$groupId"
    fun bookingPay(subscriptionId: String) = "booking/pay/$subscriptionId"

    /** Several children paid together travel as one argument, joined with "_" (ids use only "-"). */
    fun bookingPay(subscriptionIds: List<String>) = "booking/pay/${subscriptionIds.joinToString("_")}"
}

private data class Tab(val route: String, val labelRes: Tr, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, StrNav.homeTab, Icons.Default.Home),
    Tab(Routes.COURSES, StrNav.coursesTab, Icons.Default.MenuBook),
    Tab(Routes.TEST_START, StrNav.testTab, Icons.Default.Explore),
    Tab(Routes.SUPPORT, StrNav.supportTab, Icons.Default.SupportAgent),
    Tab(Routes.PROFILE, StrNav.profileTab, Icons.Default.Person),
)

@Composable
fun AppNavigation() {
    val session: SessionViewModel = viewModel()
    val state by session.state.collectAsStateWithLifecycle()

    // Keeps the branded splash on screen for a fixed 5 seconds, but only on the very first
    // launch of the app on this device. Every launch after that skips the hold and moves on
    // as soon as the session check underneath finishes.
    val isFirstLaunch = remember { AppLaunchState.isFirstLaunch() }
    var minSplashElapsed by remember { mutableStateOf(!isFirstLaunch) }
    LaunchedEffect(Unit) {
        if (isFirstLaunch) {
            delay(5_000L)
            minSplashElapsed = true
            AppLaunchState.markLaunched()
        }
    }

    // The app's one background surface — graded canvas, brand glows and drawn ornament, all
    // from a single place now (see Modifier.appBackdrop). Every screen that doesn't paint its
    // own opaque background inherits it, which is most of them.
    Box(
        Modifier
            .fillMaxSize()
            .appBackdrop(),
    ) {
        when {
            // An outdated build is stopped before anything else, signed in or not.
            state.update.blocks(BuildConfig.VERSION_CODE) ->
                UpdateRequiredScreen(state.update) { session.refreshUpdateRule() }
            state.booting || !minSplashElapsed -> SplashScreen()
            !state.signedIn && !AppLaunchState.hasSeenOnboarding -> OnboardingScreen(onDone = {})
            !state.signedIn -> AuthScreen(session)
            else -> MainScaffold(session)
        }
    }
}

/**
 * Shown for the brief moment the app checks the saved session, before the first real
 * screen (auth or home) is ready. Fully designed rather than a bare spinner: the branded
 * artwork carries the "7PRO" wordmark on Ink's dark canvas, with a staggered dot loader
 * floating near the bottom so the screen still reads as "working" while the app boots.
 */
@Composable
private fun SplashScreen() {
    Box(Modifier.fillMaxSize()) {
        // Full-bleed branded splash artwork (wordmark on Ink's dark canvas).
        Image(
            painter = painterResource(R.drawable.splash_hero),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // Small loading indicator near the bottom of the artwork.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp),
        ) {
            SplashDotLoader()
        }
    }
}

/** Three dots that lift and fade in sequence — a quieter, more considered loader than a spinner. */
@Composable
private fun SplashDotLoader() {
    val transition = rememberInfiniteTransition(label = "splash-dots")
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(3) { index ->
            val delay = index * 150
            val phase by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(900, delayMillis = delay, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot-$index",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .scale(0.7f + phase * 0.5f)
                    .background(Ink.Amber.copy(alpha = 0.4f + phase * 0.6f), shape = androidx.compose.foundation.shape.CircleShape),
            )
        }
    }
}

@Composable
private fun MainScaffold(session: SessionViewModel) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in TABS.map { it.route }

    // One design for the whole app: the sky / navy palette now covers the owner / admin console,
    // the teacher studio and the course studio as well as every learner screen. AppPalette.sky
    // stays as the single switch (set it to false here to bring the old warm palette back).
    SideEffect { AppPalette.sky = true }
    DisposableEffect(Unit) { onDispose { AppPalette.sky = true } }

    // Home is always the opening screen: every session starts from the same familiar place
    // instead of resuming wherever the previous one happened to end.
    val startTab = Routes.HOME

    // "Online now" heartbeat for the owner console (see PresenceHeartbeat / owner_analytics).
    // repeatOnLifecycle(RESUMED) means the loop is literally suspended the moment the app is
    // backgrounded and picks back up on return — no separate foreground/background tracking
    // needed, and no beat is sent while the app isn't actually in front of anyone.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                PresenceHeartbeat.ping()
                delay(PresenceHeartbeat.INTERVAL_MS)
            }
        }
    }

    // Deep link from a device notification: open the screen it is about.
    val pendingRoute = PendingRoute.pending
    LaunchedEffect(pendingRoute) {
        val target = PendingRoute.consume() ?: return@LaunchedEffect
        runCatching { navController.navigate(target) }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        bottomBar = { if (showBar) BottomBar(navController, currentRoute) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startTab,
            modifier = Modifier
                .fillMaxSize()
                // Home runs edge to edge under the floating tab bar (its page colour continues
                // around the bar, as designed) and clears the bar with its own bottom padding.
                .padding(bottom = if (showBar && currentRoute != Routes.HOME) padding.calculateBottomPadding() else 0.dp),
        ) {
            studentGraph(navController, session)
            teacherGraph(navController, session)
            studioGraph(navController, session)
            adminGraph(navController, session)
            classroomGraph(navController, session)
        }
    }
}

/** Teachers, admins and the owner may copy course links; students and everyone else may not. */
@Composable
private fun rememberCanCopyCourseLink(session: SessionViewModel): Boolean {
    val s by session.state.collectAsStateWithLifecycle()
    val profile = s.profile
    return profile != null && (profile.isStaff || profile.isTeacher || s.isTeacher)
}

private fun NavGraphBuilder.studentGraph(navController: NavHostController, session: SessionViewModel) {
    composable(Routes.HOME) { HomeScreen(navController, session) }
    composable(Routes.COURSES) { CoursesScreen(navController, canCopyLink = rememberCanCopyCourseLink(session)) }
    composable(Routes.PROFILE) { ProfileScreen(navController, session) }

    composable(Routes.COURSE_DETAIL) { entry ->
        CourseDetailScreen(
            navController,
            entry.arguments?.getString("courseId").orEmpty(),
            canCopyLink = rememberCanCopyCourseLink(session),
        )
    }
    composable(
        Routes.COURSE_PLAYER,
        arguments = listOf(androidx.navigation.navArgument("lessonId") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null }),
    ) { entry ->
        CoursePlayerScreen(
            navController,
            entry.arguments?.getString("courseId").orEmpty(),
            initialLessonId = entry.arguments?.getString("lessonId"),
        )
    }

    composable(Routes.TEST_START) { TestsHubScreen(navController, session) }
    composable(Routes.TEST_PLACEMENT) { PlacementStartScreen(navController) }
    composable(Routes.EXERCISE_TEACHERS) { ExerciseTeachersScreen(navController) }
    composable(Routes.TEACHER_EXERCISES) { entry ->
        TeacherExercisesScreen(navController, entry.arguments?.getString("teacherId").orEmpty())
    }
    composable(Routes.LEVEL_EXERCISES) { entry ->
        LevelExercisesScreen(
            navController,
            entry.arguments?.getString("teacherId").orEmpty(),
            entry.arguments?.getString("levelKey").orEmpty(),
        )
    }
    composable(Routes.TEST_RUN) { entry ->
        PlacementRunnerScreen(navController, entry.arguments?.getString("testId").orEmpty())
    }
    composable(Routes.TEST_RESULT) { entry ->
        PlacementResultScreen(navController, entry.arguments?.getString("attemptId").orEmpty(), session)
    }

    composable(Routes.BOOKING_TEACHERS) { BookingTeachersScreen(navController) }
    composable(Routes.BOOKING_GROUPS) { entry ->
        BookingGroupsScreen(navController, entry.arguments?.getString("teacherId").orEmpty())
    }
    composable(Routes.BOOKING_DETAILS) { entry ->
        BookingDetailsScreen(navController, entry.arguments?.getString("groupId").orEmpty())
    }
    composable(Routes.BOOKING_PAY) { entry ->
        BookingPaymentScreen(
            navController,
            entry.arguments?.getString("subscriptionId").orEmpty().split("_").filter { it.isNotBlank() },
        )
    }

    composable(Routes.ORDERS) { OrdersScreen(navController) }
    composable(Routes.CERTIFICATES) { CertificatesScreen(navController) }
    composable(Routes.NOTIFICATIONS) { NotificationsScreen(navController, session) }
    composable(Routes.NOTIFICATION_SETTINGS) { NotificationSettingsScreen(navController, session) }
    composable(Routes.SUPPORT) { com.rork.pro.ui.screens.support.SupportHomeScreen(navController) }
    composable(Routes.SUPPORT_CHAT) { entry ->
        com.rork.pro.ui.screens.support.SupportChatScreen(navController, entry.arguments?.getString("chatId"))
    }
    composable(Routes.SUPPORT_NEW) { entry ->
        com.rork.pro.ui.screens.support.SupportChatScreen(navController, null, entry.arguments?.getString("topic"))
    }
    composable(Routes.EDIT_PROFILE) { EditProfileScreen(navController, session) }
    composable(Routes.AI_TUTOR) { com.rork.pro.ui.screens.tutor.TutorScreen(navController, session) }
    composable(Routes.AI_TUTOR_CHARACTERS) { com.rork.pro.ui.screens.tutor.CharacterUploadScreen(navController) }
    composable(Routes.AI_TUTOR_PLANS) { com.rork.pro.ui.screens.tutor.AiTutorPlansScreen(navController) }
    composable(Routes.DUBBING) { com.rork.pro.dubbing.DubbingHostScreen(navController, session) }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    // A floating, fully rounded bar in the sky / navy palette — all four tabs are learner screens.
    val onHome = AppPalette.sky
    val dark = HomeSky.dark
    val barBg = if (onHome) HomeSky.NavBg else Ink.Surface
    val barBorder = if (onHome) HomeSky.NavBorder else Ink.Hairline
    val selectedBg = if (onHome) HomeSky.NavSelectedBg else Ink.AmberSoft
    val selectedFg = if (onHome) HomeSky.NavSelected else Ink.Amber
    val idleFg = if (onHome) HomeSky.NavIdle else Ink.TextSecondary
    val shape = RoundedCornerShape(26.dp)
    // Unread replies from the support team, refreshed whenever the learner switches tab.
    var supportUnread by remember { mutableStateOf(0) }
    LaunchedEffect(currentRoute) {
        if (com.rork.pro.data.Backend.currentUserId != null) {
            runCatching { com.rork.pro.data.SupportChatRepository.myChats() }
                .onSuccess { chats -> supportUnread = chats.sumOf { it.userUnread } }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 15.dp, end = 15.dp, top = 6.dp, bottom = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (dark) Modifier
                    else Modifier.shadow(12.dp, shape, ambientColor = HomeSky.Shadow, spotColor = HomeSky.Shadow),
                )
                .clip(shape)
                .background(barBg)
                .border(1.dp, barBorder, shape)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEach { tab ->
                val selected = currentRoute == tab.route
                val scale by animateFloatAsState(if (selected) 1.06f else 1f, tween(180), label = "tab")
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(18.dp))
                        .clickable {
                            if (!selected) {
                                navController.navigate(tab.route) {
                                    // Anchored to the graph's real start destination, which is now the
                                    // restored tab rather than always Home.
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(width = 44.dp, height = 28.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) selectedBg else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = tr(tab.labelRes),
                            tint = if (selected) selectedFg else idleFg,
                            modifier = Modifier
                                .size(22.dp)
                                .scale(scale),
                        )
                        if (tab.route == Routes.SUPPORT && supportUnread > 0) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFF26B0F)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (supportUnread > 9) "9+" else supportUnread.toString(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 10.sp,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tr(tab.labelRes),
                        color = if (selected) selectedFg else idleFg,
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp, lineHeight = 15.sp),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Shared top bar for every non-tab destination. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DetailHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth()) {
        androidx.compose.material3.TopAppBar(
            title = {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink.TextPrimary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                androidx.compose.material3.IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = tr(StrNav.back),
                        tint = Ink.TextPrimary,
                    )
                }
            },
            actions = { trailing?.invoke() },
            colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                containerColor = androidx.compose.ui.graphics.Color.Transparent,
                titleContentColor = Ink.TextPrimary,
            ),
        )
    }
}
