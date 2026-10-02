package com.rork.pro.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.ErrorText
import com.rork.pro.data.MeetingServer
import com.rork.pro.data.MeetingServersRepository
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.ConfirmDialog
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.i18n.StrMeetingServers
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.launch

class MeetingServersVm : AdminListViewModel<List<MeetingServer>>({ MeetingServersRepository.list() }) {
    /** Shown once after a switch: how many upcoming classes moved, how many live ones stayed. */
    var lastSwitch by mutableStateOf<String?>(null)
}

private fun providerName(code: String): String = when (code) {
    MeetingServersRepository.PROVIDER_CONTABO -> tr(StrMeetingServers.providerContabo)
    MeetingServersRepository.PROVIDER_HETZNER -> tr(StrMeetingServers.providerHetzner)
    MeetingServersRepository.PROVIDER_COMMUNITY -> tr(StrMeetingServers.providerCommunity)
    else -> tr(StrMeetingServers.providerOther)
}

/** Text boxes of the add/edit form, kept as strings until saved. */
private class ServerDraft(server: MeetingServer?) {
    val id = server?.id
    val hadSecret = server?.hasJwtSecret == true
    var label by mutableStateOf(server?.label.orEmpty())
    var provider by mutableStateOf(server?.provider ?: MeetingServersRepository.PROVIDER_CONTABO)
    var domain by mutableStateOf(server?.domain.orEmpty())
    var appId by mutableStateOf(server?.jwtAppId.orEmpty())
    var secret by mutableStateOf("")
    var removeJwt by mutableStateOf(false)
    var notes by mutableStateOf(server?.notes.orEmpty())

    fun valid() = label.isNotBlank()

    /** null keeps the saved secret, "" clears it. */
    fun secretToSend(): String? = when {
        removeJwt -> ""
        secret.isNotBlank() -> secret.trim()
        else -> null
    }

    fun appIdToSend(): String = if (removeJwt) "" else appId
}

@Composable
private fun ServerForm(draft: ServerDraft, onSave: () -> Unit, onCancel: (() -> Unit)?) {
    InkField(draft.label, { draft.label = it }, tr(StrMeetingServers.label))
    Spacer(Modifier.height(10.dp))
    Text(tr(StrMeetingServers.provider), color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(6.dp))
    AdminChips(
        options = MeetingServersRepository.PROVIDERS.map { it to providerName(it) },
        selected = draft.provider,
        onSelect = { draft.provider = it },
    )
    Spacer(Modifier.height(10.dp))
    InkField(draft.domain, { draft.domain = it.trim() }, tr(StrMeetingServers.domain), keyboardType = KeyboardType.Uri)
    Spacer(Modifier.height(6.dp))
    AdminNote(tr(StrMeetingServers.domainHint))

    Spacer(Modifier.height(14.dp))
    Text(tr(StrMeetingServers.jwtSection), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
    Spacer(Modifier.height(4.dp))
    Text(tr(StrMeetingServers.jwtHint), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
    if (!draft.removeJwt) {
        Spacer(Modifier.height(8.dp))
        InkField(draft.appId, { draft.appId = it.trim() }, tr(StrMeetingServers.jwtAppId))
        Spacer(Modifier.height(8.dp))
        InkField(
            draft.secret,
            { draft.secret = it.trim() },
            tr(if (draft.hadSecret) StrMeetingServers.jwtSecretKeep else StrMeetingServers.jwtSecret),
            visualTransformation = PasswordVisualTransformation(),
        )
    }
    if (draft.hadSecret) {
        ToggleRow(tr(StrMeetingServers.jwtRemove), draft.removeJwt) { draft.removeJwt = it }
    }

    Spacer(Modifier.height(10.dp))
    InkField(draft.notes, { draft.notes = it }, tr(StrMeetingServers.notes), singleLine = false, minLines = 2)
    AdminActions {
        AdminButton(tr(StrMeetingServers.save), Modifier.weight(1f), Icons.Default.Check, AdminTone.Primary, enabled = draft.valid()) { onSave() }
        if (onCancel != null) AdminButton(tr(StrMeetingServers.cancel), Modifier.weight(1f), onClick = onCancel)
    }
}

@Composable
fun AdminMeetingServersScreen(navController: NavHostController, session: SessionViewModel) {
    val vm: MeetingServersVm = viewModel()
    val sessionState by session.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var editingId by remember { mutableStateOf<String?>(null) }
    var confirmActivate by remember { mutableStateOf<MeetingServer?>(null) }
    var confirmDelete by remember { mutableStateOf<MeetingServer?>(null) }
    var testingId by remember { mutableStateOf<String?>(null) }
    val testResults = remember { mutableStateMapOf<String, String?>() }
    val newDraft = remember { ServerDraft(null) }

    AdminScaffold(tr(StrMeetingServers.title), navController, vm) { servers ->
        if (!sessionState.can("meeting_servers.manage")) {
            item { AdminNote(tr(ErrorText.forbidden), AdminTone.Danger) }
            return@AdminScaffold
        }
        item { AdminNote(tr(StrMeetingServers.intro)) }
        vm.lastSwitch?.let { msg -> item { AdminNote(msg, AdminTone.Success) } }

        items(servers, key = { it.id }) { server ->
            ServerCard(
                server = server,
                editing = editingId == server.id,
                testing = testingId == server.id,
                testResult = testResults[server.id],
                hasTestResult = testResults.containsKey(server.id),
                onEdit = { editingId = if (editingId == server.id) null else server.id },
                onSave = { d ->
                    vm.act {
                        MeetingServersRepository.save(d.id, d.label, d.provider, d.domain, d.appIdToSend(), d.secretToSend(), d.notes)
                    }
                    editingId = null
                },
                onTest = {
                    if (server.domain.isBlank()) {
                        testResults[server.id] = tr(StrMeetingServers.testNeedsDomain)
                    } else {
                        testingId = server.id
                        scope.launch {
                            val reason = MeetingServersRepository.test(server)
                            testResults[server.id] = reason
                            testingId = null
                            vm.load(quiet = true)
                        }
                    }
                },
                onActivate = { confirmActivate = server },
                onDelete = { confirmDelete = server },
            )
        }

        item {
            AdminFormCard(tr(StrMeetingServers.addServer), icon = Icons.Default.Dns) { close ->
                ServerForm(newDraft, onSave = {
                    val d = newDraft
                    vm.act {
                        MeetingServersRepository.save(null, d.label, d.provider, d.domain, d.appIdToSend(), d.secretToSend(), d.notes)
                    }
                    newDraft.label = ""; newDraft.domain = ""; newDraft.appId = ""; newDraft.secret = ""; newDraft.notes = ""
                    close()
                }, onCancel = null)
            }
        }
    }

    confirmActivate?.let { server ->
        val untested = server.lastTestOk != true
        ConfirmDialog(
            title = tr(StrMeetingServers.confirmActivateTitle),
            body = trf(StrMeetingServers.confirmActivateBody, server.label) +
                if (untested) "\n\n" + tr(StrMeetingServers.confirmActivateUntested) else "",
            confirmLabel = tr(StrMeetingServers.activate),
            onDismiss = { confirmActivate = null },
            onConfirm = {
                confirmActivate = null
                vm.act {
                    val r = MeetingServersRepository.activate(server.id)
                    vm.lastSwitch = trf(StrMeetingServers.switched, r.movedScheduled, r.liveLeft)
                }
            },
        )
    }
    confirmDelete?.let { server ->
        ConfirmDialog(
            title = tr(StrMeetingServers.confirmDeleteTitle),
            body = tr(StrMeetingServers.confirmDeleteBody),
            confirmLabel = tr(StrMeetingServers.delete),
            destructive = true,
            onDismiss = { confirmDelete = null },
            onConfirm = {
                confirmDelete = null
                vm.act { MeetingServersRepository.delete(server.id) }
            },
        )
    }
}

@Composable
private fun ServerCard(
    server: MeetingServer,
    editing: Boolean,
    testing: Boolean,
    testResult: String?,
    hasTestResult: Boolean,
    onEdit: () -> Unit,
    onSave: (ServerDraft) -> Unit,
    onTest: () -> Unit,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    InkCard(borderColor = if (server.isActive) Ink.Teal.copy(alpha = 0.6f) else Ink.Hairline) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(server.label, color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                Text(
                    server.domain.ifBlank { tr(StrMeetingServers.notReady) },
                    color = if (server.domain.isBlank()) Ink.Coral else Ink.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (server.isActive) Pill(tr(StrMeetingServers.active), background = Ink.TealSoft, foreground = Ink.Teal)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(providerName(server.provider), background = Ink.SurfaceHigh, foreground = Ink.TextSecondary, bold = false)
            if (server.isReady) {
                Pill(
                    tr(if (server.usesJwt) StrMeetingServers.jwtOn else StrMeetingServers.jwtOff),
                    background = if (server.usesJwt) Ink.TealSoft else Ink.SurfaceHigh,
                    foreground = if (server.usesJwt) Ink.Teal else Ink.TextMuted,
                    bold = false,
                )
            }
        }
        if (server.liveSessions > 0 || server.scheduledSessions > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                trf(StrMeetingServers.liveCount, server.liveSessions) + " · " + trf(StrMeetingServers.scheduledCount, server.scheduledSessions),
                color = Ink.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        server.lastTestOk?.let { ok ->
            Text(
                tr(if (ok) StrMeetingServers.lastTestOk else StrMeetingServers.lastTestFail) + " — " + formatDate(server.lastTestedAt),
                color = if (ok) Ink.Teal else Ink.Coral,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (server.notes.isNotBlank()) {
            Text(server.notes, color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
        }
        if (hasTestResult) {
            Spacer(Modifier.height(8.dp))
            if (testResult == null) {
                AdminNote(tr(StrMeetingServers.testOk), AdminTone.Success)
            } else {
                AdminNote(
                    if (testResult == tr(StrMeetingServers.testNeedsDomain)) testResult else trf(StrMeetingServers.testFail, testResult),
                    AdminTone.Danger,
                )
            }
        }

        AdminActions {
            AdminButton(
                tr(if (testing) StrMeetingServers.testing else StrMeetingServers.test),
                Modifier.weight(1f), Icons.Default.NetworkCheck, AdminTone.Info, enabled = !testing && server.isReady,
            ) { onTest() }
            AdminButton(tr(StrMeetingServers.edit), Modifier.weight(1f), Icons.Default.Edit) { onEdit() }
        }
        if (!server.isActive) {
            AdminActions {
                AdminButton(
                    tr(StrMeetingServers.activate), Modifier.weight(1f), Icons.Default.CheckCircle, AdminTone.Success,
                    enabled = server.isReady,
                ) { onActivate() }
                AdminButton(tr(StrMeetingServers.delete), Modifier.weight(1f), Icons.Outlined.Delete, AdminTone.Danger) { onDelete() }
            }
        }

        if (editing) {
            Spacer(Modifier.height(12.dp))
            val draft = remember(server) { ServerDraft(server) }
            ServerForm(draft, onSave = { onSave(draft) }, onCancel = onEdit)
        }
    }
}
