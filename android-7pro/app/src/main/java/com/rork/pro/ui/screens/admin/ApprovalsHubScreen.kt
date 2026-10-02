package com.rork.pro.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.rork.pro.data.AdminRepository
import com.rork.pro.data.AppError
import com.rork.pro.data.toAppError
import com.rork.pro.ui.SessionState
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.ErrorBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.StrAdminHub
import com.rork.pro.ui.i18n.StrHome
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.launch

/** Pending approvals by kind: teacher requests, payment transfers, courses awaiting review. */
data class StaffApprovals(val requests: Int, val transfers: Int, val reviews: Int) {
    val total: Int get() = requests + transfers + reviews
}

/** Which of the three queues the signed-in staff member may open. */
data class ApprovalAccess(val requests: Boolean, val transfers: Boolean, val reviews: Boolean) {
    val any: Boolean get() = requests || transfers || reviews
}

/**
 * The same permissions the admin console gates its own counts and tiles on, so an approvals
 * entry never promises a queue its owner cannot open: the owner sees everything, an admin only
 * what their permissions allow.
 */
fun SessionState.approvalAccess() = ApprovalAccess(
    requests = isStaff && (isOwner || can("teachers.manage")),
    transfers = isStaff && (can("finance.manage") || can("settings.manage")),
    reviews = isStaff && can("courses.manage"),
)

/**
 * Reads the pending count of each queue [access] allows. A queue that cannot be read counts as
 * zero rather than failing the whole read, so one slow table never blanks the others.
 */
suspend fun loadStaffApprovals(access: ApprovalAccess): StaffApprovals {
    val requests = if (access.requests) {
        runCatching { AdminRepository.pendingRequestCounts().sumOf { it.pendingCount }.toInt() }.getOrDefault(0)
    } else 0
    val transfers = if (access.transfers) {
        runCatching { AdminRepository.manualPaymentPendingCount() }.getOrDefault(0) +
            AdminRepository.subscriptionPaymentPendingCount()
    } else 0
    val reviews = if (access.reviews) {
        runCatching { AdminRepository.coursesForReview("PENDING_REVIEW").size }.getOrDefault(0)
    } else 0
    return StaffApprovals(requests, transfers, reviews)
}

/**
 * Every approval queue on one page of its own: the total at the top, then one card per queue
 * with its pending count, each opening that queue. Counts are re-read each time the page comes
 * back on screen (that is when something was just decided) and on pull-to-refresh.
 */
@Composable
fun ApprovalsHubScreen(navController: NavHostController, session: SessionViewModel) {
    val sessionState by session.state.collectAsStateWithLifecycle()
    val access = sessionState.approvalAccess()

    var data by remember { mutableStateOf<StaffApprovals?>(null) }
    var error by remember { mutableStateOf<AppError?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        runCatching { loadStaffApprovals(access) }
            .onSuccess { data = it; error = null }
            .onFailure { error = it.toAppError() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(access) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { load() }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        DetailHeader(tr(StrHome.approvalsTitle), onBack = { navController.popBackStack() })

        val failure = error
        val counts = data
        when {
            !access.any -> EmptyBlock(tr(StrAdminHub.allClear), tr(StrAdminHub.allClear))
            counts == null && failure != null -> ErrorBlock(failure, Modifier.fillMaxSize()) { scope.launch { load() } }
            counts == null -> LoadingBlock(Modifier.fillMaxSize())
            else -> Refreshable(refreshing, {
                scope.launch {
                    refreshing = true
                    load()
                    refreshing = false
                }
            }) {
                LazyColumn(
                    contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        AdminSummaryStrip(
                            listOf(
                                AdminStat(
                                    counts.total.toString(),
                                    tr(StrAdmin.totalPendingAll),
                                    if (counts.total > 0) Ink.Coral else Ink.Teal,
                                ),
                            ),
                        )
                    }
                    if (access.requests) {
                        item {
                            ApprovalQueueCard(Icons.Default.Groups, tr(StrHome.teacherRequests), counts.requests) {
                                navController.navigate(AdminRoutes.allApprovals())
                            }
                        }
                    }
                    if (access.transfers) {
                        item {
                            ApprovalQueueCard(Icons.Default.Payments, tr(StrAdminHub.transfersShort), counts.transfers) {
                                navController.navigate(AdminRoutes.PAYMENTS)
                            }
                        }
                    }
                    if (access.reviews) {
                        item {
                            ApprovalQueueCard(Icons.Default.FactCheck, tr(StrAdminHub.reviewShort), counts.reviews) {
                                navController.navigate(AdminRoutes.REVIEW)
                            }
                        }
                    }
                    if (counts.total == 0) {
                        item {
                            InkCard(color = Ink.TealSoft, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Ink.Teal, modifier = Modifier.size(20.dp))
                                    Text(tr(StrAdminHub.allClear), color = Ink.Teal, style = MaterialTheme.typography.titleSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One queue: an icon tile, its name, how many are waiting, and a chevron — the whole card opens it. */
@Composable
private fun ApprovalQueueCard(icon: ImageVector, label: String, count: Int, onClick: () -> Unit) {
    InkCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Ink.AmberSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = Ink.Amber, modifier = Modifier.size(25.dp))
            }
            Text(
                label,
                modifier = Modifier.weight(1f),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            CountPill(count)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Ink.TextMuted, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun CountPill(count: Int) {
    val bg: Color = if (count > 0) Ink.Amber else Ink.SurfaceHigh
    val fg: Color = if (count > 0) Ink.OnAmber else Ink.TextSecondary
    Text(
        if (count > 99) "99+" else count.toString(),
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = fg,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
    )
}
