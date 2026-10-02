package com.rork.pro.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.WebAdSlot
import com.rork.pro.data.WebAdsRepository
import com.rork.pro.ui.components.ConfirmDialog
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.i18n.StrWebAds
import com.rork.pro.ui.i18n.Tr
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.theme.Ink

// ---------------------------------------------------------------- Web ads

/** The four web ad slots as stored, plus the master switch that gates all of them. */
data class WebAdsAdminData(
    val slots: List<WebAdSlot>,
    val globallyEnabled: Boolean,
)

class WebAdsVm : AdminListViewModel<WebAdsAdminData>({
    WebAdsAdminData(
        slots = WebAdsRepository.slots(),
        globallyEnabled = WebAdsRepository.globallyEnabled(),
    )
})

private fun slotLabel(slot: String): Tr = when (slot) {
    "TOP" -> StrWebAds.slotTop
    "QUESTION" -> StrWebAds.slotQuestion
    "RESULT" -> StrWebAds.slotResult
    else -> StrWebAds.slotBottom
}

private fun kindLabel(kind: String): Tr = when (kind) {
    "ADSENSE" -> StrWebAds.kindAdsense
    "HTML" -> StrWebAds.kindHtml
    else -> StrWebAds.kindImage
}

/**
 * Where the owner and admins place ads on the web pages.
 *
 * One card per slot of the teacher-exercises page. Everything typed here is validated again by
 * the database, so a bad value can never reach a student's screen — the checks in this file only
 * keep Save disabled until the values would be accepted.
 */
@Composable
fun AdminWebAdsScreen(navController: NavHostController) {
    val vm: WebAdsVm = viewModel()

    AdminScaffold(tr(StrWebAds.title), navController, vm) { data ->
        item { AdminNote(tr(StrWebAds.note)) }

        item {
            InkCard {
                ToggleRow(tr(StrWebAds.globalToggle), data.globallyEnabled) { value ->
                    vm.act { WebAdsRepository.setGloballyEnabled(value) }
                }
                Text(
                    tr(StrWebAds.globalHelp),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        WebAdsRepository.SLOTS.forEach { name ->
            item(key = name) {
                WebAdSlotCard(
                    slot = name,
                    existing = data.slots.firstOrNull { it.slot == name },
                    onSave = { saved -> vm.act { WebAdsRepository.save(saved) } },
                    onDelete = { id -> vm.act { WebAdsRepository.delete(id) } },
                )
            }
        }
    }
}

@Composable
private fun WebAdSlotCard(
    slot: String,
    existing: WebAdSlot?,
    onSave: (WebAdSlot) -> Unit,
    onDelete: (String) -> Unit,
) {
    var open by remember(slot) { mutableStateOf(false) }
    var kind by remember(existing) { mutableStateOf(existing?.kind ?: "IMAGE") }
    var enabled by remember(existing) { mutableStateOf(existing?.isEnabled ?: true) }
    var imageUrl by remember(existing) { mutableStateOf(existing?.imageUrl.orEmpty()) }
    var linkUrl by remember(existing) { mutableStateOf(existing?.linkUrl.orEmpty()) }
    var client by remember(existing) { mutableStateOf(existing?.adsenseClient.orEmpty()) }
    var unit by remember(existing) { mutableStateOf(existing?.adsenseSlot.orEmpty()) }
    var html by remember(existing) { mutableStateOf(existing?.htmlCode.orEmpty()) }
    var height by remember(existing) { mutableStateOf((existing?.heightPx ?: 120).toString()) }
    var confirmDelete by remember { mutableStateOf(false) }

    val heightValue = height.toIntOrNull()
    val heightOk = heightValue != null && heightValue in 40..700
    val fieldsOk = when (kind) {
        "IMAGE" ->
            imageUrl.trim().startsWith("https://", ignoreCase = true) &&
                (linkUrl.isBlank() || Regex("(?i)^https?://.+").matches(linkUrl.trim()))
        "ADSENSE" ->
            Regex("^ca-pub-[0-9]{6,20}$").matches(client.trim()) &&
                Regex("^[0-9]{4,20}$").matches(unit.trim())
        else -> html.isNotBlank() && html.length <= 20000
    }
    val valid = heightOk && fieldsOk

    InkCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AdminBadgeIcon(
                Icons.Default.Campaign,
                tint = if (existing?.isEnabled == true) Ink.Teal else Ink.Neutral,
            )
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(
                    tr(slotLabel(slot)),
                    color = Ink.TextPrimary,
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (existing == null) tr(StrWebAds.notSet) else tr(kindLabel(existing.kind)),
                    color = Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (existing != null) {
                Pill(
                    if (existing.isEnabled) tr(StrWebAds.enabled) else "—",
                    background = if (existing.isEnabled) Ink.TealSoft else Ink.SurfaceHigh,
                    foreground = if (existing.isEnabled) Ink.Teal else Ink.TextMuted,
                )
            }
            androidx.compose.material3.IconButton(onClick = { open = !open }) {
                Icon(
                    if (open) Icons.Default.Close else Icons.Default.Edit,
                    contentDescription = null,
                    tint = Ink.Amber,
                )
            }
        }

        if (open) {
            Spacer(Modifier.height(10.dp))
            AdminChips(
                listOf("IMAGE", "ADSENSE", "HTML").map { it to tr(kindLabel(it)) },
                kind,
                { kind = it },
                tint = Ink.Sky,
            )
            Spacer(Modifier.height(10.dp))

            when (kind) {
                "IMAGE" -> {
                    InkField(imageUrl, { imageUrl = it }, tr(StrWebAds.imageUrl), keyboardType = KeyboardType.Uri)
                    Spacer(Modifier.height(8.dp))
                    InkField(linkUrl, { linkUrl = it }, tr(StrWebAds.linkUrl), keyboardType = KeyboardType.Uri)
                }
                "ADSENSE" -> {
                    InkField(client, { client = it }, tr(StrWebAds.adsenseClient))
                    Spacer(Modifier.height(8.dp))
                    InkField(unit, { unit = it.filter(Char::isDigit) }, tr(StrWebAds.adsenseSlot), keyboardType = KeyboardType.Number)
                    Spacer(Modifier.height(4.dp))
                    Text(tr(StrWebAds.adsenseHelp), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    InkField(html, { html = it }, tr(StrWebAds.htmlCode), singleLine = false, minLines = 4)
                    Spacer(Modifier.height(4.dp))
                    Text(tr(StrWebAds.htmlHelp), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                }
            }

            Spacer(Modifier.height(8.dp))
            InkField(
                height,
                { height = it.filter(Char::isDigit) },
                tr(StrWebAds.heightPx),
                keyboardType = KeyboardType.Number,
            )
            Spacer(Modifier.height(8.dp))
            ToggleRow(tr(StrWebAds.enabled), enabled) { enabled = it }

            if (!valid) {
                Spacer(Modifier.height(4.dp))
                Text(tr(StrWebAds.invalid), color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
            }

            AdminActions {
                AdminButton(
                    tr(StrWebAds.save),
                    Modifier.weight(1f),
                    Icons.Default.Check,
                    AdminTone.Primary,
                    enabled = valid,
                ) {
                    onSave(
                        WebAdSlot(
                            id = existing?.id,
                            slot = slot,
                            kind = kind,
                            isEnabled = enabled,
                            imageUrl = imageUrl.trim(),
                            linkUrl = linkUrl.trim(),
                            adsenseClient = client.trim(),
                            adsenseSlot = unit.trim(),
                            htmlCode = html,
                            heightPx = heightValue ?: 120,
                        ),
                    )
                    open = false
                }
                if (existing?.id != null) {
                    AdminButton(
                        tr(StrWebAds.remove),
                        Modifier.weight(1f),
                        Icons.Outlined.Delete,
                        AdminTone.Danger,
                    ) { confirmDelete = true }
                }
            }
        }

        if (confirmDelete) {
            ConfirmDialog(
                title = tr(StrWebAds.remove),
                body = tr(StrWebAds.removeConfirm),
                confirmLabel = tr(StrWebAds.remove),
                destructive = true,
                onDismiss = { confirmDelete = false },
                onConfirm = {
                    confirmDelete = false
                    existing?.id?.let(onDelete)
                    open = false
                },
            )
        }
    }
}
