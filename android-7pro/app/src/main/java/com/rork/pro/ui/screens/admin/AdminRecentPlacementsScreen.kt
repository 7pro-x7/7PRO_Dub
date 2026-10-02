package com.rork.pro.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.rork.pro.data.AdminRepository
import com.rork.pro.data.RecentPlacementResult
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.theme.Ink
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.tr

// Not `private`: androidx's ViewModelProvider constructs this class via reflection from a
// different package (androidx.lifecycle), and Kotlin compiles a top-level `private class` to
// package-private bytecode — a class outside the package can't reach it, so `viewModel()`
// threw an IllegalAccessException the moment this screen tried to compose, and the app closed.
class RecentPlacementsVm : AdminListViewModel<List<RecentPlacementResult>>({
    AdminRepository.recentPlacementResults()
})

@Composable
fun AdminRecentPlacementsScreen(navController: NavHostController) {
    val vm: RecentPlacementsVm = androidx.lifecycle.viewmodel.compose.viewModel()

    AdminScaffold(tr(StrAdmin.recentPlacementResults), navController, vm) { results ->
        item {
            Text(
                tr(StrAdmin.recentPlacementResultsBody),
                color = Ink.TextMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        if (results.isEmpty()) {
            item {
                AdminEmpty(
                    tr(StrAdmin.noRecentPlacementResults),
                    tr(StrAdmin.recentPlacementResultsBody),
                    icon = Icons.Default.DoneAll,
                )
            }
        } else {
            items(results, key = { it.attemptId }) { result ->
                RecentPlacementCard(result, navController)
            }
        }
    }
}

@Composable
private fun RecentPlacementCard(result: RecentPlacementResult, navController: NavHostController) {
    InkCard(
        onClick = {
            navController.navigate(com.rork.pro.ui.navigation.Routes.testResult(result.attemptId))
        }
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    result.fullName.ifBlank { result.email.ifBlank { result.userId } },
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (result.email.isNotBlank()) {
                    Text(result.email, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("${result.percent.toInt()}%", color = Ink.Teal, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminMeta("${tr(StrAdmin.levelLabel)}: ${result.level ?: "—"}", Ink.Amber)
            AdminMeta(formatDate(result.submittedAt), Ink.TextMuted)
        }
    }
}
