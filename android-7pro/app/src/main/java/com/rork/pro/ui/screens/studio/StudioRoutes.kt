package com.rork.pro.ui.screens.studio

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rork.pro.data.CourseScope
import com.rork.pro.ui.SessionViewModel

/** Course authoring destinations, shared by the teacher studio and the owner console. */
object StudioRoutes {
    const val COURSES = "studio/courses?scope={scope}"
    const val EDITOR = "studio/course/{courseId}"
    const val CONTENT = "studio/content?scope={scope}&tab={tab}"

    fun courses(scope: CourseScope): String = "studio/courses?scope=${scope.name}"

    fun editor(courseId: String): String = "studio/course/$courseId"

    /** The Content hub (courses + exercise bank); [tab] 0 = courses, 1 = exercises. */
    fun content(scope: CourseScope, tab: Int = 0): String = "studio/content?scope=${scope.name}&tab=$tab"
}

fun NavGraphBuilder.studioGraph(navController: NavHostController, session: SessionViewModel) {
    composable(
        StudioRoutes.COURSES,
        arguments = listOf(
            navArgument("scope") {
                type = NavType.StringType
                defaultValue = CourseScope.MINE.name
            },
        ),
    ) { entry ->
        val scope = runCatching {
            CourseScope.valueOf(entry.arguments?.getString("scope") ?: CourseScope.MINE.name)
        }.getOrDefault(CourseScope.MINE)
        CourseManagerScreen(navController, session, scope)
    }

    composable(
        StudioRoutes.CONTENT,
        arguments = listOf(
            navArgument("scope") {
                type = NavType.StringType
                defaultValue = CourseScope.MINE.name
            },
            navArgument("tab") {
                type = NavType.IntType
                defaultValue = 0
            },
        ),
    ) { entry ->
        val scope = runCatching {
            CourseScope.valueOf(entry.arguments?.getString("scope") ?: CourseScope.MINE.name)
        }.getOrDefault(CourseScope.MINE)
        ContentHubScreen(navController, session, scope, entry.arguments?.getInt("tab") ?: 0)
    }

    composable(StudioRoutes.EDITOR) { entry ->
        CourseEditorScreen(navController, session, entry.arguments?.getString("courseId").orEmpty())
    }
}
