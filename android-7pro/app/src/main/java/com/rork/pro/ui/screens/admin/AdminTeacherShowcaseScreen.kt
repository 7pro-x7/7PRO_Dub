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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.rork.pro.data.AdminRepository
import com.rork.pro.data.AdminShowcaseTeacher
import com.rork.pro.data.MediaRepository
import com.rork.pro.ui.components.Avatar
import com.rork.pro.ui.components.EmptyBlock
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.components.rememberFilePicker
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.StrBooking
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.launch

class TeacherShowcaseVm :
    AdminListViewModel<List<AdminShowcaseTeacher>>({ AdminRepository.showcaseTeachers() })

/**
 * Who students see first on "احجز جروب", who wears the premium badge, and what picture each
 * teacher shows — the three things that were previously decided for the owner rather than by
 * them (order came from the rating, the badge did not exist, and a photo could only be changed
 * by the teacher themselves).
 *
 * Reordering is local until saved. Each nudge rearranges the list on screen instantly and the
 * whole arrangement is then sent in ONE call: a request per tap would let a fast run of
 * up/down taps arrive out of order and leave the server holding an arrangement the owner never
 * actually saw. The badge and the photo are the opposite case — one deliberate act each — so
 * they save immediately.
 */
@Composable
fun AdminTeacherShowcaseScreen(navController: NavHostController) {
    val vm: TeacherShowcaseVm = viewModel()
    val busy by vm.busy.collectAsStateWithLifecycle()

    AdminScaffold(tr(StrBooking.showcaseTitle), navController, vm) { loaded ->
        item { AdminNote(tr(StrBooking.showcaseSubtitle)) }

        if (loaded.isEmpty()) {
            item { AdminEmpty(tr(StrBooking.adminNoGroups), tr(StrBooking.showcaseHiddenWhy)) }
            return@AdminScaffold
        }

        item {
            ShowcaseList(
                loaded = loaded,
                busy = busy,
                onSaveOrder = { ids -> vm.act { AdminRepository.reorderTeachers(ids) } },
                onPremium = { id, on ->
                    vm.act { AdminRepository.setTeacherBadge(id, isPremium = on) }
                },
                onBadgeText = { id, text ->
                    vm.act {
                        AdminRepository.setTeacherBadge(
                            teacherId = id,
                            badge = text.ifBlank { null },
                            clearBadge = text.isBlank(),
                        )
                    }
                },
                onPhoto = { id, url -> vm.act { AdminRepository.setTeacherPhoto(id, url) } },
            )
        }
    }
}

@Composable
private fun ShowcaseList(
    loaded: List<AdminShowcaseTeacher>,
    busy: Boolean,
    onSaveOrder: (List<String>) -> Unit,
    onPremium: (String, Boolean) -> Unit,
    onBadgeText: (String, String) -> Unit,
    onPhoto: (String, String?) -> Unit,
) {
    // Seeded from the server and re-seeded whenever the server's own order changes, so a save
    // (or someone else's change arriving on refresh) lands cleanly instead of being overwritten
    // by a stale local copy.
    var order by remember { mutableStateOf(loaded) }
    LaunchedEffect(loaded.map { it.teacherId }) { order = loaded }
    val dirty = order.map { it.teacherId } != loaded.map { it.teacherId }

    fun move(index: Int, to: Int) {
        if (to !in order.indices) return
        val next = order.toMutableList()
        next.add(to, next.removeAt(index))
        order = next
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (dirty) {
            InkCard(borderColor = Ink.Amber.copy(alpha = 0.45f)) {
                Text(
                    tr(StrBooking.showcaseUnsaved),
                    color = Ink.Amber,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                PrimaryAction(tr(StrBooking.showcaseSaveOrder), loading = busy) {
                    onSaveOrder(order.map { it.teacherId })
                }
                Spacer(Modifier.height(8.dp))
                SecondaryAction(tr(Str.cancel)) { order = loaded }
            }
        }

        order.forEachIndexed { index, teacher ->
            ShowcaseCard(
                teacher = teacher,
                position = index + 1,
                busy = busy,
                canMoveUp = index > 0,
                canMoveDown = index < order.lastIndex,
                onMoveUp = { move(index, index - 1) },
                onMoveDown = { move(index, index + 1) },
                onMoveTop = { move(index, 0) },
                onMoveBottom = { move(index, order.lastIndex) },
                onPremium = { on -> onPremium(teacher.teacherId, on) },
                onBadgeText = { text -> onBadgeText(teacher.teacherId, text) },
                onPhoto = { url -> onPhoto(teacher.teacherId, url) },
            )
        }
    }
}

@Composable
private fun ShowcaseCard(
    teacher: AdminShowcaseTeacher,
    position: Int,
    busy: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveTop: () -> Unit,
    onMoveBottom: () -> Unit,
    onPremium: (Boolean) -> Unit,
    onBadgeText: (String) -> Unit,
    onPhoto: (String?) -> Unit,
) {
    var badgeText by remember(teacher.teacherId, teacher.badge) {
        mutableStateOf(teacher.badge.orEmpty())
    }

    InkCard(
        borderColor = if (teacher.isPremium) Ink.Amber.copy(alpha = 0.55f) else Ink.Hairline,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                trf(StrBooking.showcasePosition, position),
                color = Ink.TextMuted,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(10.dp))
            PhotoSlot(
                photoUrl = teacher.photoUrl,
                name = teacher.fullName,
                busy = busy,
                onPicked = onPhoto,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    teacher.fullName,
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                teacher.headline?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        color = Ink.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (teacher.isPremium) {
                        Pill(badgeText.ifBlank { tr(StrBooking.showcaseBadgeDefault) })
                        Spacer(Modifier.width(6.dp))
                    }
                    // Stated rather than left to be noticed: a teacher can be arranged here and
                    // still be invisible to students, and that is confusing without a reason.
                    if (!teacher.visible) {
                        Pill(
                            tr(StrBooking.showcaseHidden),
                            background = Ink.SurfaceHigh,
                            foreground = Ink.TextMuted,
                        )
                    }
                }
            }

            // Order controls: single step on the near side, jump-to-end on the far side, so a
            // teacher can be sent to the very top of a long roster without twenty taps.
            Column {
                Row {
                    MoveButton(Icons.Default.KeyboardDoubleArrowUp, tr(StrBooking.showcaseMoveTop), canMoveUp, onMoveTop)
                    MoveButton(Icons.Default.KeyboardArrowUp, tr(StrBooking.showcaseMoveUp), canMoveUp, onMoveUp)
                }
                Row {
                    MoveButton(Icons.Default.KeyboardDoubleArrowDown, tr(StrBooking.showcaseMoveBottom), canMoveDown, onMoveBottom)
                    MoveButton(Icons.Default.KeyboardArrowDown, tr(StrBooking.showcaseMoveDown), canMoveDown, onMoveDown)
                }
            }
        }

        if (!teacher.visible) {
            Spacer(Modifier.height(4.dp))
            Text(
                tr(StrBooking.showcaseHiddenWhy),
                color = Ink.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(6.dp))
        ToggleRow(tr(StrBooking.showcasePremium), teacher.isPremium, onPremium)

        if (teacher.isPremium) {
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = badgeText,
                onValueChange = { badgeText = it.take(24) },
                label = { Text(tr(StrBooking.showcaseBadgeLabel), color = Ink.TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Ink.Amber,
                    unfocusedBorderColor = Ink.Hairline,
                    cursorColor = Ink.Amber,
                    focusedTextColor = Ink.TextPrimary,
                    unfocusedTextColor = Ink.TextPrimary,
                ),
            )
            Text(
                tr(StrBooking.showcaseBadgeHint),
                color = Ink.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            SecondaryAction(tr(StrBooking.adminSave), enabled = !busy) { onBadgeText(badgeText.trim()) }
        }
    }
}

@Composable
private fun MoveButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(34.dp)) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) Ink.Teal else Ink.TextMuted.copy(alpha = 0.4f),
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The teacher's picture with a camera badge on it: picks from the phone, uploads, and hands
 * back the finished public URL — or null when the red X clears it back to their account avatar.
 */
@Composable
private fun PhotoSlot(
    photoUrl: String?,
    name: String,
    busy: Boolean,
    onPicked: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberFilePicker()
    var uploading by remember { mutableStateOf(false) }

    Box {
        Box(Modifier.size(52.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
            if (!photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Avatar(null, name, size = 52.dp)
            }
            if (uploading) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp)
                .clip(CircleShape)
                .background(Ink.Amber)
                .clickable(enabled = !uploading && !busy) {
                    picker.pick("image/*") { uri ->
                        scope.launch {
                            uploading = true
                            runCatching {
                                val file = MediaRepository.read(context, uri, MediaRepository.MAX_AVATAR_BYTES)
                                MediaRepository.upload(file, "teacher-photos")
                            }.onSuccess { onPicked(it) }
                            uploading = false
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.CameraAlt,
                contentDescription = tr(StrBooking.showcaseEditPhoto),
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
        }
        if (!photoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Ink.Coral)
                    .clickable(enabled = !uploading && !busy) { onPicked(null) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}
