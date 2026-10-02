package com.rork.pro.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AdminRepository
import com.rork.pro.data.OnlineUser
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.i18n.StrAdminHub
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.delay

// Not `private` — see the note on RecentPlacementsVm: ViewModelProvider builds this by reflection.
class OnlineNowVm : AdminListViewModel<List<OnlineUser>>({ AdminRepository.onlineUsers() })

/** How often the list re-reads while the screen is open. Presence itself pings every 30 s. */
private const val REFRESH_MS = 15_000L

/**
 * The people behind the owner console's "online now" tile: everyone whose app pinged in the
 * last 90 seconds, newest first. Refreshes itself while open so the list follows the number
 * instead of going stale the moment it is drawn.
 */
@Composable
fun AdminOnlineNowScreen(navController: NavHostController) {
    val vm: OnlineNowVm = viewModel()

    // Quiet reloads keep the current list on screen (no spinner) and ignore a failed tick.
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_MS)
            vm.load(quiet = true)
        }
    }

    AdminScaffold(tr(StrAdminHub.onlineNow), navController, vm) { users ->
        if (users.isEmpty()) {
            item {
                AdminEmpty(
                    tr(StrAdminHub.noOnlineNow),
                    tr(StrAdminHub.noOnlineNowBody),
                    icon = Icons.Default.Wifi,
                )
            }
        } else {
            item {
                AdminSummaryStrip(
                    listOf(
                        AdminStat(users.size.toString(), tr(StrAdminHub.onlineTotal), Ink.Coral),
                        AdminStat(users.count { it.role == "STUDENT" }.toString(), tr(StrAdminHub.students)),
                        AdminStat(users.count { it.role == "TEACHER" }.toString(), tr(StrAdminHub.teachers)),
                    ),
                )
            }
            item {
                Text(
                    tr(StrAdminHub.onlineNowBody),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(users, key = { it.userId }) { user ->
                val name = user.fullName.ifBlank { user.email.ifBlank { user.userId } }
                AdminItemCard(
                    title = name,
                    subtitle = user.email.takeIf { it.isNotBlank() && it != name },
                    leading = { Avatar(user.avatarUrl, name, 44.dp) },
                    trailing = {
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Pill(
                                roleLabel(user.role),
                                background = roleTint(user.role).copy(alpha = 0.14f),
                                foreground = roleTint(user.role),
                            )
                            Text(seenAgo(user.lastSeenAt), color = Ink.TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    },
                    expandable = false,
                    stateKey = user.userId,
                    showBody = false,
                )
            }
        }
    }
}

/** "Just now" / "12 sec ago" from the server's last_seen_at; blank if it can't be parsed. */
private fun seenAgo(iso: String?): String {
    val seen = runCatching { java.time.OffsetDateTime.parse(iso).toInstant() }.getOrNull() ?: return ""
    val secs = java.time.Duration.between(seen, java.time.Instant.now()).seconds.coerceAtLeast(0)
    return if (secs < 10) tr(StrAdminHub.seenJustNow) else trf(StrAdminHub.seenSecondsAgo, secs)
}
