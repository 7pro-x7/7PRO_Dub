package com.rork.pro.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CardMembership
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.AdBanner
import com.rork.pro.ui.components.AdFreeOffer
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.HeaderSwoosh
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LevelBadge
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.Refreshable
import com.rork.pro.ui.components.SectionHeader
import com.rork.pro.ui.components.StatusPill
import com.rork.pro.ui.navigation.Routes
import com.rork.pro.ui.screens.admin.AdminRoutes
import com.rork.pro.ui.screens.teacher.TeacherRoutes
import com.rork.pro.ui.theme.AppAppearance
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import com.rork.pro.ui.theme.ThemeMode
import com.rork.pro.ui.i18n.AppLanguage
import com.rork.pro.ui.i18n.Lang
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.StrProfile
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf

@Composable
fun ProfileScreen(navController: NavHostController, session: SessionViewModel) {
    val state by session.state.collectAsStateWithLifecycle()
    val profile = state.profile
    var languageSheetOpen by remember { mutableStateOf(false) }
    var themeSheetOpen by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()

    LaunchedEffect(Unit) { session.refreshNotifications() }

    if (themeSheetOpen) {
        AppearanceDialog(onDismiss = { themeSheetOpen = false })
    }

    if (languageSheetOpen) {
        LanguageDialog(onDismiss = { languageSheetOpen = false })
    }

    Refreshable(
        refreshing,
        onRefresh = {
            if (!refreshing) {
                refreshScope.launch {
                    refreshing = true
                    session.refreshNotifications()
                    delay(300)
                    refreshing = false
                }
            }
        },
    ) {
    Box(Modifier.fillMaxSize()) {
        HeaderSwoosh()
    LazyColumn(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                tr(StrProfile.profile),
                style = MaterialTheme.typography.headlineMedium,
                color = Ink.TextPrimary,
                modifier = Modifier.padding(horizontal = Dimens.screenPadding, vertical = 10.dp),
            )
        }

        item {
            InkCard(Modifier.padding(horizontal = Dimens.screenPadding)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Avatar(profile?.avatarUrl, profile?.displayName, 58.dp)
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile?.displayName ?: tr(StrProfile.learner),
                            color = Ink.TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            profile?.email.orEmpty(),
                            color = Ink.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    androidx.compose.material3.IconButton(onClick = { navController.navigate(Routes.EDIT_PROFILE) }) {
                        Icon(Icons.Default.Edit, tr(StrProfile.editProfile), tint = Ink.TextSecondary, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (profile?.currentLevel != null) {
                        LevelBadge(profile.currentLevel)
                    } else {
                        Pill(tr(StrProfile.noLevelYet))
                    }
                    Pill(
                        profile?.role?.lowercase()?.replaceFirstChar { it.uppercase() } ?: tr(StrProfile.student),
                        background = Ink.NeutralSoft,
                        foreground = Ink.TextSecondary,
                    )
                    if (profile?.status != null && profile.status != "ACTIVE") {
                        StatusPill(profile.status)
                    }
                }
            }
        }

        if (state.maintenance) {
            item {
                InkCard(
                    Modifier.padding(horizontal = Dimens.screenPadding),
                    borderColor = Ink.Coral.copy(alpha = 0.4f),
                ) {
                    Text(tr(StrProfile.maintenanceOn), color = Ink.Coral, style = MaterialTheme.typography.titleMedium)
                    if (state.maintenanceMessage.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(state.maintenanceMessage, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            SectionHeader(tr(StrProfile.learning), Modifier.padding(horizontal = Dimens.screenPadding))
        }
        item {
            MenuGroup(Modifier.padding(horizontal = Dimens.screenPadding)) {
                MenuRow(Icons.Default.Assessment, tr(StrProfile.placementTest), tr(StrProfile.placementTestSub)) {
                    navController.navigate(Routes.TEST_START)
                }
                MenuDivider()
                MenuRow(Icons.Default.CardMembership, tr(StrProfile.certificates), tr(StrProfile.certificatesSub)) {
                    navController.navigate(Routes.CERTIFICATES)
                }
                MenuDivider()
                MenuRow(Icons.AutoMirrored.Filled.ReceiptLong, tr(StrProfile.paymentsOrders), tr(StrProfile.paymentsOrdersSub)) {
                    navController.navigate(Routes.ORDERS)
                }
                MenuDivider()
                MenuRow(
                    Icons.Default.Notifications,
                    tr(StrProfile.notifications),
                    if (state.unreadCount > 0) trf(StrProfile.unreadCount, state.unreadCount) else tr(StrProfile.allCaughtUp),
                ) { navController.navigate(Routes.NOTIFICATIONS) }
                MenuDivider()
                MenuRow(
                    Icons.Default.Notifications,
                    tr(StrProfile.notificationSettings),
                    tr(StrProfile.notificationSettingsSub),
                ) { navController.navigate(Routes.NOTIFICATION_SETTINGS) }
                MenuDivider()
                MenuRow(Icons.Default.SupportAgent, tr(StrProfile.support), tr(StrProfile.supportSub)) {
                    navController.navigate(Routes.SUPPORT)
                }
            }
        }

        if (state.isTeacher || state.isStaff) {
            item {
                SectionHeader(tr(StrProfile.teaching), Modifier.padding(horizontal = Dimens.screenPadding))
            }
            item {
                MenuGroup(Modifier.padding(horizontal = Dimens.screenPadding)) {
                    if (state.isTeacher) {
                        MenuRow(Icons.Default.WorkspacePremium, tr(StrProfile.teacherStudio), tr(StrProfile.teacherStudioSub)) {
                            navController.navigate(TeacherRoutes.STUDIO)
                        }
                    }
                    if (state.isTeacher && state.isStaff) MenuDivider()
                    if (state.isStaff) {
                        MenuRow(
                            Icons.Default.AdminPanelSettings,
                            if (state.isOwner) tr(StrProfile.ownerConsole) else tr(StrProfile.adminConsole),
                            tr(StrProfile.consoleSub),
                        ) { navController.navigate(AdminRoutes.CONSOLE) }
                    }
                }
            }
        }

        item {
            SectionHeader(tr(Str.settings), Modifier.padding(horizontal = Dimens.screenPadding))
        }
        item {
            MenuGroup(Modifier.padding(horizontal = Dimens.screenPadding)) {
                MenuRow(
                    Icons.Default.Language,
                    tr(Str.language),
                    AppLanguage.current.nativeLabel,
                    neutral = true,
                ) { languageSheetOpen = true }
                MenuDivider()
                MenuRow(
                    Icons.Default.DarkMode,
                    tr(StrProfile.appearance),
                    themeLabel(AppAppearance.mode),
                    neutral = true,
                ) { themeSheetOpen = true }
            }
        }

        // The rewarded ad is an offer, never an ambush: it only appears when an ad actually
        // loaded, and it sits right above the banner it takes away so the trade is obvious.
        item { AdFreeOffer(Modifier.padding(horizontal = Dimens.screenPadding)) }

        item { AdBanner("PROFILE", Modifier.padding(horizontal = Dimens.screenPadding)) }

        item {
            Column(Modifier.padding(horizontal = Dimens.screenPadding)) {
                Spacer(Modifier.height(10.dp))
                MenuRow(Icons.AutoMirrored.Filled.Logout, tr(StrProfile.signOut), null, danger = true, standalone = true) {
                    session.signOut()
                }
            }
        }
    }
    } // Box
    } // Refreshable
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> tr(StrProfile.themeSystem)
    ThemeMode.LIGHT -> tr(StrProfile.themeLight)
    ThemeMode.DARK -> tr(StrProfile.themeDark)
}

/** Light / dark switch. The choice is remembered on this device and applies instantly. */
@Composable
private fun AppearanceDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        titleContentColor = Ink.TextPrimary,
        textContentColor = Ink.TextSecondary,
        title = { Text(tr(StrProfile.appearance), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr(StrProfile.appearanceSubtitle), style = MaterialTheme.typography.bodySmall, color = Ink.TextMuted)
                Spacer(Modifier.height(4.dp))
                ThemeMode.entries.forEach { mode ->
                    val selected = AppAppearance.mode == mode
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Ink.AmberSoft else Ink.SurfaceHigh)
                            .clickable {
                                AppAppearance.set(context, mode)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            themeLabel(mode),
                            color = if (selected) Ink.Amber else Ink.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(Icons.Default.Check, null, tint = Ink.Amber, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr(Str.close), color = Ink.Amber)
            }
        },
    )
}

/** Manual language switch. The choice is saved and always beats device auto-detection. */
@Composable
private fun LanguageDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Surface,
        titleContentColor = Ink.TextPrimary,
        textContentColor = Ink.TextSecondary,
        title = { Text(tr(Str.language), style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr(Str.languageSubtitle), style = MaterialTheme.typography.bodySmall, color = Ink.TextMuted)
                Spacer(Modifier.height(4.dp))
                Lang.entries.forEach { lang ->
                    val selected = AppLanguage.current == lang
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) Ink.AmberSoft else Ink.SurfaceHigh)
                            .clickable {
                                AppLanguage.set(context, lang)
                                onDismiss()
                            }
                            .padding(horizontal = 14.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            lang.nativeLabel,
                            color = if (selected) Ink.Amber else Ink.TextPrimary,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Icon(Icons.Default.Check, null, tint = Ink.Amber, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (!AppLanguage.isExplicit) {
                    Text(
                        tr(Str.followDevice),
                        style = MaterialTheme.typography.bodySmall,
                        color = Ink.TextMuted,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(tr(Str.done), color = Ink.Amber) }
        },
    )
}

/** A rounded card holding several [MenuRow]s separated by [MenuDivider]s — one card per group instead of one per row. */
@Composable
private fun MenuGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Surface)
            .border(1.dp, Ink.Hairline, shape),
        content = content,
    )
}

/** Hairline between two rows of a [MenuGroup], starting where the row text starts (after the icon tile). */
@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 65.dp)
            .height(1.dp)
            .background(Ink.Hairline),
    )
}

/**
 * One tappable row: an icon tile, a title with an optional one-line subtitle, and a chevron.
 * Inside a [MenuGroup] it has no surface of its own; [standalone] gives it a card of its own
 * (sign out), [neutral] uses a grey icon tile for settings, and [danger] a red one.
 */
@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    danger: Boolean = false,
    neutral: Boolean = false,
    standalone: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (standalone) {
                    Modifier.clip(shape).background(Ink.Surface).border(1.dp, Ink.Hairline, shape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        danger -> Ink.CoralSoft
                        neutral -> Ink.SurfaceHigh
                        else -> Ink.AmberSoft
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                null,
                tint = when {
                    danger -> Ink.Coral
                    neutral -> Ink.TextPrimary
                    else -> Ink.Amber
                },
                modifier = Modifier.size(20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (danger) Ink.Coral else Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
            )
            if (subtitle != null) {
                Text(subtitle, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (!danger) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = Ink.TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
