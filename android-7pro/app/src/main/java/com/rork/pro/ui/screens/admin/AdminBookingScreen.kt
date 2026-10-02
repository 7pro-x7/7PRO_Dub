package com.rork.pro.ui.screens.admin

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AdminBookingGroup
import coil3.compose.AsyncImage
import com.rork.pro.data.AdminRepository
import com.rork.pro.data.MediaRepository
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.components.rememberFilePicker
import com.rork.pro.ui.i18n.StrBooking
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.StrAdminX
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.launch

class BookingAdminVm : AdminListViewModel<List<AdminBookingGroup>>({ AdminRepository.bookingGroups() })

/**
 * Where the owner decides what a seat costs and who is open for booking.
 *
 * Both controls live here rather than on the teacher's own screens, because both are the
 * platform's commercial decisions: a teacher can create and run a group, but cannot price it
 * or put themselves on the booking list. Availability writes the same
 * `accepting_new_students` flag the rest of the platform already reads, so there is one
 * truth about whether a teacher takes new students, not two that can disagree.
 *
 * This screen only lists teachers. Tapping one opens [AdminTeacherBookingGroupsScreen], which
 * shows just that teacher's groups — so the list here stays a clean roster instead of every
 * teacher's groups all being expanded on screen at once.
 */
@Composable
fun AdminBookingScreen(navController: NavHostController) {
    val vm: BookingAdminVm = viewModel()

    AdminScaffold(tr(StrBooking.adminTitle), navController, vm) { groups ->
        if (groups.isNotEmpty()) {
            item {
                val byTeacher = groups.groupBy { it.teacherId }
                AdminSummaryStrip(
                    listOf(
                        AdminStat(byTeacher.size.toString(), tr(StrAdmin.teachers)),
                        AdminStat(byTeacher.values.count { it.first().available }.toString(), tr(StrBooking.adminTeacherAvailable), Ink.Teal),
                        AdminStat(groups.size.toString(), tr(StrAdminX.groupsTotal)),
                        AdminStat(groups.count { it.isOpen }.toString(), tr(StrAdminX.openGroups), Ink.Sky),
                    ),
                )
            }
        }
        item { AdminNote("${tr(StrBooking.adminSubtitle)} ${tr(StrBooking.adminPriceChangeNote)}") }
        // Display order and badges used to be a separate console entry; it belongs with booking.
        item {
            AdminItemCard(
                title = tr(StrAdminX.bookingShowcaseTitle),
                subtitle = tr(StrAdminX.bookingShowcaseSub),
                leading = { AdminBadgeIcon(Icons.Default.WorkspacePremium, tint = Ink.Amber) },
                trailing = { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Ink.TextMuted, modifier = Modifier.size(18.dp)) },
                expandable = false,
                onClick = { navController.navigate(AdminRoutes.TEACHER_SHOWCASE) },
            )
        }
        if (groups.isNotEmpty()) item { AdminSectionLabel(tr(StrAdmin.teachers), groups.map { it.teacherId }.distinct().size) }

        if (groups.isEmpty()) {
            item {
                AdminEmpty(tr(StrBooking.adminNoGroups), tr(StrBooking.adminNoGroupsBody))
            }
        } else {
            groups.groupBy { it.teacherId }.forEach { (teacherId, rows) ->
                val head = rows.first()
                item(key = "teacher-$teacherId") {
                    InkCard(
                        borderColor = if (head.available) Ink.Hairline else Ink.Coral.copy(alpha = 0.3f),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        onClick = {
                            navController.navigate(AdminRoutes.bookingTeacherGroups(teacherId, head.teacherName))
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AdminBadgeIcon(text = initialsOf(head.teacherName), tint = if (head.available) Ink.Teal else Ink.Neutral)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    head.teacherName,
                                    color = Ink.TextPrimary,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    trf(StrAdminX.groupsOpenOf, rows.count { it.isOpen }, rows.size),
                                    color = Ink.TextMuted,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                )
                            }
                            Pill(
                                trf(StrBooking.groupsCount, rows.size),
                                background = Ink.SkySoft,
                                foreground = Ink.Sky,
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                null,
                                tint = Ink.TextMuted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        ToggleRow(tr(StrBooking.adminTeacherAvailable), head.available) { available ->
                            vm.act { AdminRepository.setTeacherBookingAvailable(teacherId, available) }
                        }
                    }
                }
            }
        }
    }
}

/** That one teacher's groups, opened from a tap on their card in [AdminBookingScreen]. */
@Composable
fun AdminTeacherBookingGroupsScreen(navController: NavHostController, teacherId: String, teacherName: String) {
    val vm: BookingAdminVm = viewModel()

    AdminScaffold(teacherName, navController, vm) { allGroups ->
        val rows = allGroups.filter { it.teacherId == teacherId }

        if (rows.isEmpty()) {
            item {
                AdminEmpty(tr(StrBooking.adminNoGroups), tr(StrBooking.adminNoGroupsBody))
            }
        } else {
            item { AdminNote(tr(StrBooking.adminPriceChangeNote)) }
            rows.forEach { group ->
                item(key = group.groupId) {
                    GroupBookingEditor(
                        group = group,
                        onPhoto = { url -> vm.act { AdminRepository.setGroupPhoto(group.groupId, url) } },
                    ) { price, currency, open, capacity, schedule ->
                        vm.act {
                            AdminRepository.saveGroupBooking(
                                groupId = group.groupId,
                                monthlyPrice = price,
                                currency = currency,
                                isOpen = open,
                                capacity = capacity,
                                schedule = schedule,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupBookingEditor(
    group: AdminBookingGroup,
    onPhoto: (String?) -> Unit,
    onSave: (Double?, String?, Boolean?, Int?, String?) -> Unit,
) {
    var price by remember(group.groupId, group.monthlyPrice) {
        mutableStateOf(if (group.monthlyPrice > 0) group.monthlyPrice.toInt().toString() else "")
    }
    var currency by remember(group.groupId) { mutableStateOf(group.currency) }
    var capacity by remember(group.groupId, group.capacity) { mutableStateOf(group.capacity.toString()) }
    var schedule by remember(group.groupId) { mutableStateOf(group.schedule.orEmpty()) }

    val accent = if (group.isOpen && group.monthlyPrice > 0) Ink.Teal else Ink.Amber

    // Distinguished by an accent-tinted border rather than a special background, since this
    // card is a normal top-level list item on its own screen (not nested inside another card).
    InkCard(borderColor = accent.copy(alpha = 0.28f), contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The group's own picture, editable right here: it is what a student sees on the
            // group card, and until now only the teacher could change it.
            GroupPhotoEditor(group.photoUrl, group.name, onPhoto)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    group.name,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    trf(StrBooking.members, group.members),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // A group with no price cannot be booked at all, so that is stated plainly rather
            // than being left to be inferred from an empty field.
            Text(
                if (group.monthlyPrice > 0) {
                    formatMoney(group.monthlyPrice, group.currency)
                } else {
                    tr(StrBooking.adminNoPrice)
                },
                color = if (group.monthlyPrice > 0) Ink.Amber else Ink.Coral,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(6.dp))
        ToggleRow(tr(StrBooking.adminGroupOpen), group.isOpen) { open ->
            onSave(null, null, open, null, null)
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminField(
                value = price,
                onChange = { price = it.filter(Char::isDigit).take(7) },
                label = tr(StrBooking.adminPrice),
                keyboard = KeyboardType.Number,
                modifier = Modifier.weight(1.4f),
            )
            AdminField(
                value = currency,
                onChange = { currency = it.uppercase().take(4) },
                label = tr(StrBooking.adminCurrency),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        AdminField(
            value = capacity,
            onChange = { capacity = it.filter(Char::isDigit).take(4) },
            label = tr(StrBooking.adminCapacity),
            keyboard = KeyboardType.Number,
        )
        Spacer(Modifier.height(10.dp))
        AdminField(
            value = schedule,
            onChange = { schedule = it },
            label = tr(StrBooking.adminSchedule),
        )

        Spacer(Modifier.height(12.dp))
        PrimaryAction(tr(StrBooking.adminSave)) {
            onSave(
                price.toDoubleOrNull(),
                currency,
                null,
                capacity.toIntOrNull(),
                schedule,
            )
        }
    }
}

/**
 * The group's picture with a camera badge: picks from the phone, uploads, and saves the finished
 * URL straight away; the red X clears it back to the plain initials badge. Same control the
 * teacher has on their own group screen, placed here so the owner never has to ask them for it.
 */
@Composable
private fun GroupPhotoEditor(photoUrl: String?, name: String, onPicked: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberFilePicker()
    var uploading by remember { mutableStateOf(false) }

    Box {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(Ink.SurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(Icons.Default.Groups, null, tint = Ink.TextMuted, modifier = Modifier.size(20.dp))
            }
            if (uploading) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(26.dp)
                .clip(CircleShape)
                .background(Ink.Amber)
                .clickable(enabled = !uploading) {
                    picker.pick("image/*") { uri ->
                        scope.launch {
                            uploading = true
                            runCatching {
                                val file = MediaRepository.read(context, uri, MediaRepository.MAX_AVATAR_BYTES)
                                MediaRepository.upload(file, "group-photos")
                            }.onSuccess { onPicked(it) }
                            uploading = false
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = tr(StrBooking.showcaseGroupPhoto),
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
        if (!photoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Ink.Coral)
                    .clickable(enabled = !uploading) { onPicked(null) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(13.dp))
            }
        }
    }
}

@Composable
private fun AdminField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = Ink.TextMuted) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Ink.Amber,
            unfocusedBorderColor = Ink.Hairline,
            cursorColor = Ink.Amber,
            focusedTextColor = Ink.TextPrimary,
            unfocusedTextColor = Ink.TextPrimary,
        ),
    )
}
