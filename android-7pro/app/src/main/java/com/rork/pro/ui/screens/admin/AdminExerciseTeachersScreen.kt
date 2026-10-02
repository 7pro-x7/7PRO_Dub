package com.rork.pro.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AdminRepository
import com.rork.pro.data.ExerciseRepository
import com.rork.pro.data.TeacherProfile
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.StrEx
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.theme.Ink

/** One teacher with how many exercises (any status) they currently have on file. */
data class TeacherExerciseCount(val teacher: TeacherProfile, val count: Int)

// Not `private` — see the note on RecentPlacementsVm: ViewModelProvider builds this by reflection.
class AdminExerciseTeachersVm : AdminListViewModel<List<TeacherExerciseCount>>({
    val teachers = AdminRepository.teachers()
    val counts = ExerciseRepository.all().mapNotNull { it.ownerId }.groupingBy { it }.eachCount()
    teachers
        .map { TeacherExerciseCount(it, counts[it.id] ?: 0) }
        .sortedWith(compareByDescending<TeacherExerciseCount> { it.count }.thenBy { it.teacher.profile?.fullName.orEmpty() })
})

/**
 * Owner/admin entry point for "Teacher exercises": every teacher on the platform, so admin can
 * pick one — including a teacher with nothing published yet — then create, edit, publish or
 * delete exactly that teacher's exercises on the next screen.
 */
@Composable
fun AdminExerciseTeachersScreen(navController: NavHostController) {
    val vm: AdminExerciseTeachersVm = viewModel()

    val myId = com.rork.pro.data.Backend.currentUserId
    AdminScaffold(tr(StrEx.allExercises), navController, vm) { rows ->
        // The owner's own exercises and the flat list of everyone's come first; the per-teacher
        // list below stays for browsing one teacher at a time.
        val total = rows.sumOf { it.count }
        val mine = rows.firstOrNull { it.teacher.id == myId }
        item {
            AdminItemCard(
                title = tr(StrEx.staffMine),
                subtitle = tr(StrEx.staffMineSub),
                leading = { AdminBadgeIcon(Icons.Default.Star, tint = Ink.Amber) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(trf(StrEx.exercisesCount, mine?.count ?: 0), background = Ink.AmberSoft, foreground = Ink.Amber)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink.TextMuted)
                    }
                },
                expandable = false,
                onClick = {
                    val name = mine?.teacher?.profile?.fullName.orEmpty().ifBlank { tr(StrEx.staffMine) }
                    myId?.let { navController.navigate(AdminRoutes.exerciseTeacher(it, name)) }
                },
            )
        }
        item {
            AdminItemCard(
                title = tr(StrEx.staffAll),
                subtitle = tr(StrEx.staffAllSub),
                leading = { AdminBadgeIcon(Icons.Default.Quiz, tint = Ink.Teal) },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(trf(StrEx.exercisesCount, total), background = Ink.Teal.copy(alpha = 0.14f), foreground = Ink.Teal)
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink.TextMuted)
                    }
                },
                expandable = false,
                onClick = { navController.navigate(AdminRoutes.EXERCISES_ALL) },
            )
        }
        if (rows.isNotEmpty()) item { AdminSectionLabel(tr(StrEx.staffByTeacher), rows.size) }
        if (rows.isEmpty()) {
            item { AdminEmpty(tr(StrAdmin.noTeachers), tr(StrAdmin.noTeachersBody)) }
        } else {
            items(rows, key = { it.teacher.id }) { row ->
                val name = row.teacher.profile?.fullName.orEmpty().ifBlank { row.teacher.id }
                AdminItemCard(
                    title = name,
                    subtitle = row.teacher.headline?.takeIf { it.isNotBlank() },
                    leading = { Avatar(row.teacher.photoUrl ?: row.teacher.profile?.avatarUrl, name, 44.dp) },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(
                                trf(StrEx.exercisesCount, row.count),
                                background = Ink.Teal.copy(alpha = 0.14f),
                                foreground = Ink.Teal,
                            )
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink.TextMuted)
                        }
                    },
                    expandable = false,
                    onClick = { navController.navigate(AdminRoutes.exerciseTeacher(row.teacher.id, name)) },
                )
            }
        }
    }
}
