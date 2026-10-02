package com.rork.pro.ui.screens.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rork.pro.data.CourseScope
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.i18n.StrContent
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.screens.teacher.ExerciseStudioScreen
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink

/**
 * "Content": courses and the exercise bank in one place, as two tabs, replacing the separate
 * "My courses" / "My exercises" entries. [scope] MINE is a teacher's (or the owner's own) studio;
 * ALL is the owner/admin console over every course and every teacher's exercises. Each tab is the
 * existing screen shown without its own header, so nothing about editing changes.
 */
@Composable
fun ContentHubScreen(
    navController: NavHostController,
    session: SessionViewModel,
    scope: CourseScope,
    initialTab: Int = 0,
) {
    val sessionState by session.state.collectAsStateWithLifecycle()
    val all = scope == CourseScope.ALL
    val showCourses = !all || sessionState.can("courses.manage") || sessionState.can("course_content.manage")
    val showExercises = !all || sessionState.can("tests.manage")
    var tab by rememberSaveable { mutableIntStateOf(if (showExercises && (initialTab == 1 || !showCourses)) 1 else 0) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(tr(StrContent.hubTitle), onBack = { navController.popBackStack() })
        Text(
            tr(StrContent.hubSub),
            color = Ink.TextMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = Dimens.screenPadding),
        )

        if (showCourses && showExercises) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.screenPadding, vertical = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Ink.SurfaceHigh)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                HubTab(tr(StrContent.tabCourses), tab == 0, Modifier.weight(1f)) { tab = 0 }
                HubTab(tr(StrContent.tabExercises), tab == 1, Modifier.weight(1f)) { tab = 1 }
            }
        }

        Box(Modifier.weight(1f)) {
            if (tab == 0 && showCourses) {
                CourseManagerScreen(navController, session, scope, embedded = true)
            } else if (showExercises) {
                ExerciseStudioScreen(navController, manageAll = all, embedded = true)
            }
        }
    }
}

@Composable
private fun HubTab(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Ink.Amber else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) Ink.OnAmber else Ink.TextSecondary,
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
    }
}
