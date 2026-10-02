package com.rork.pro.ui.screens.classroom

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.rork.pro.ui.SessionViewModel

/**
 * Routes for the Virtual Classroom feature.
 *
 * Deliberately NOT one of the four bottom-nav [com.rork.pro.ui.navigation.Routes.HOME]-style
 * tabs: the feature is reached exclusively from the third card on the Tests hub
 * ([com.rork.pro.ui.screens.test.TestsHubScreen], right under the placement test and teacher
 * exercises cards) and, for staff, from the admin console — never from a new tab.
 */
object ClassroomRoutes {
    const val HOME = "classroom/home/{scope}"
    const val CREATE = "classroom/create"
    const val LOBBY = "classroom/lobby/{sessionId}?autoJoin={autoJoin}"

    fun home(scope: ClassroomScope) = "classroom/home/${scope.name}"

    /** [autoJoin] skips the lobby's own "Join now" button — used by the Home "meeting is live"
     *  card, whose tap already means "take me in". Default keeps every other entry unchanged. */
    fun lobby(sessionId: String, autoJoin: Boolean = false) =
        "classroom/lobby/$sessionId" + if (autoJoin) "?autoJoin=true" else ""
}

fun NavGraphBuilder.classroomGraph(navController: NavHostController, session: SessionViewModel) {
    composable(
        ClassroomRoutes.HOME,
        arguments = listOf(navArgument("scope") { type = NavType.StringType }),
    ) { entry ->
        val scope = runCatching {
            ClassroomScope.valueOf(entry.arguments?.getString("scope").orEmpty())
        }.getOrDefault(ClassroomScope.STUDENT)
        ClassroomHomeScreen(navController, session, scope)
    }
    composable(ClassroomRoutes.CREATE) { ClassroomCreateScreen(navController) }
    composable(
        ClassroomRoutes.LOBBY,
        arguments = listOf(
            navArgument("sessionId") { type = NavType.StringType },
            navArgument("autoJoin") { type = NavType.BoolType; defaultValue = false },
        ),
    ) { entry ->
        ClassroomLobbyScreen(
            navController,
            session,
            entry.arguments?.getString("sessionId").orEmpty(),
            autoJoin = entry.arguments?.getBoolean("autoJoin") ?: false,
        )
    }
}
