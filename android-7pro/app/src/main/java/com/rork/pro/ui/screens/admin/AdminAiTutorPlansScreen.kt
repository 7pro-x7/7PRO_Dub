package com.rork.pro.ui.screens.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.rork.pro.data.AiTutorPlan
import com.rork.pro.data.AiTutorPlansRepository
import com.rork.pro.data.AiTutorSales
import com.rork.pro.data.ErrorText
import com.rork.pro.tutor.TutorApi
import com.rork.pro.ui.SessionViewModel
import com.rork.pro.ui.components.Divider
import com.rork.pro.ui.components.KeyValueRow
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.formatDate
import com.rork.pro.ui.components.formatMoney
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.StrAiPlans
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.theme.Ink

/** Everything the owner's AI Tutor pricing screen shows. */
data class AiPlansAdminData(
    val plans: List<AiTutorPlan>,
    val sales: AiTutorSales,
    /** The free replies each student gets a day, before any plan (null when it could not be read). */
    val freeReplies: Int?,
)

class AiPlansAdminVm : AdminListViewModel<AiPlansAdminData>({
    AiPlansAdminData(
        plans = AiTutorPlansRepository.allPlans(),
        sales = runCatching { AiTutorPlansRepository.sales() }.getOrDefault(AiTutorSales()),
        freeReplies = runCatching { TutorApi.status().perUserDaily }.getOrNull(),
    )
})

/** The text boxes of the plan form, kept as strings until the owner submits. */
private class PlanDraft(plan: AiTutorPlan?) {
    var name by mutableStateOf(plan?.name.orEmpty())
    var nameAr by mutableStateOf(plan?.nameAr.orEmpty())
    var description by mutableStateOf(plan?.description.orEmpty())
    var descriptionAr by mutableStateOf(plan?.descriptionAr.orEmpty())
    var price by mutableStateOf(plan?.price?.let(::plain).orEmpty())
    var salePrice by mutableStateOf(plan?.salePrice?.let(::plain).orEmpty())
    var saleDays by mutableStateOf("")
    var currency by mutableStateOf(plan?.currency ?: "EGP")
    var period by mutableStateOf((plan?.periodDays ?: 30).toString())
    var replies by mutableStateOf((plan?.replies ?: 100).toString())
    var order by mutableStateOf((plan?.sortOrder ?: 0).toString())
    var active by mutableStateOf(plan?.isActive ?: true)

    fun priceValue() = price.toDoubleOrNull() ?: -1.0
    fun saleValue() = salePrice.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()

    /** A sale price must be lower than the price, or empty. */
    fun saleInvalid(): Boolean {
        if (salePrice.isBlank()) return false
        val sale = saleValue() ?: return true
        return sale < 0 || sale >= priceValue()
    }

    fun valid() = name.isNotBlank() && priceValue() >= 0 && !saleInvalid() &&
        (period.toIntOrNull() ?: 0) in 1..3650 && (replies.toIntOrNull() ?: 0) in 1..1000000 && currency.trim().length == 3

    fun toInput(editing: Boolean): AiTutorPlansRepository.PlanInput {
        val sale = saleValue()
        val days = saleDays.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        return AiTutorPlansRepository.PlanInput(
            name = name, nameAr = nameAr, description = description, descriptionAr = descriptionAr,
            price = priceValue().coerceAtLeast(0.0), salePrice = sale,
            // No sale price means no sale end; when editing, an empty days box leaves the end date alone.
            saleEndsInDays = if (sale == null) 0 else if (editing) days else days ?: 0,
            currency = currency, periodDays = period.toIntOrNull() ?: 30, replies = replies.toIntOrNull() ?: 100,
            sortOrder = order.toIntOrNull() ?: 0, isActive = active,
        )
    }
}

private fun plain(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
private fun PlanForm(draft: PlanDraft, editing: Boolean, actionLabel: String, onSubmit: () -> Unit, onCancel: (() -> Unit)? = null) {
    InkField(draft.name, { draft.name = it }, tr(StrAiPlans.fName))
    Spacer(Modifier.height(10.dp))
    InkField(draft.nameAr, { draft.nameAr = it }, tr(StrAiPlans.fNameAr))
    Spacer(Modifier.height(10.dp))
    InkField(draft.description, { draft.description = it }, tr(StrAiPlans.fDesc))
    Spacer(Modifier.height(10.dp))
    InkField(draft.descriptionAr, { draft.descriptionAr = it }, tr(StrAiPlans.fDescAr))
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { InkField(draft.price, { draft.price = it.filter { c -> c.isDigit() || c == '.' } }, tr(StrAiPlans.fPrice), keyboardType = KeyboardType.Decimal) }
        Box(Modifier.weight(1f)) { InkField(draft.salePrice, { draft.salePrice = it.filter { c -> c.isDigit() || c == '.' } }, tr(StrAiPlans.fSalePrice), keyboardType = KeyboardType.Decimal) }
    }
    Spacer(Modifier.height(10.dp))
    InkField(
        draft.saleDays, { draft.saleDays = it.filter(Char::isDigit) },
        if (editing) tr(StrAiPlans.fSaleDaysEdit) else tr(StrAiPlans.fSaleDays),
        keyboardType = KeyboardType.Number,
    )
    if (draft.saleInvalid()) {
        Spacer(Modifier.height(8.dp))
        AdminNote(tr(StrAiPlans.invalidSale), AdminTone.Danger)
    } else {
        Spacer(Modifier.height(8.dp))
        AdminNote(tr(StrAiPlans.saleNote))
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { InkField(draft.replies, { draft.replies = it.filter(Char::isDigit) }, tr(StrAiPlans.fReplies), keyboardType = KeyboardType.Number) }
        Box(Modifier.weight(1f)) { InkField(draft.period, { draft.period = it.filter(Char::isDigit) }, tr(StrAiPlans.fPeriod), keyboardType = KeyboardType.Number) }
    }
    Spacer(Modifier.height(8.dp))
    AdminNote(tr(StrAiPlans.packNote))
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) { InkField(draft.currency, { draft.currency = it.uppercase().filter(Char::isLetter).take(3) }, tr(StrAiPlans.fCurrency)) }
        Box(Modifier.weight(1f)) { InkField(draft.order, { draft.order = it.filter(Char::isDigit) }, tr(StrAiPlans.fOrder), keyboardType = KeyboardType.Number) }
    }
    ToggleRow(tr(StrAiPlans.activeLabel), draft.active) { draft.active = it }
    AdminActions {
        AdminButton(actionLabel, Modifier.weight(1f), Icons.Default.Check, AdminTone.Primary, enabled = draft.valid()) { onSubmit() }
        if (onCancel != null) AdminButton(tr(StrAdmin.cancel), Modifier.weight(1f), onClick = onCancel)
    }
}

@Composable
private fun FreeRepliesCard(current: Int?, onSave: (Int) -> Unit) {
    AdminFormCard(tr(StrAiPlans.freeTitle), subtitle = current?.let { "$it" }, icon = Icons.Default.Sell) { close ->
        var text by remember(current) { mutableStateOf(current?.toString().orEmpty()) }
        AdminNote(tr(StrAiPlans.freeHint))
        Spacer(Modifier.height(10.dp))
        InkField(text, { text = it.filter(Char::isDigit).take(4) }, tr(StrAiPlans.freeTitle), keyboardType = KeyboardType.Number)
        AdminActions {
            AdminButton(tr(StrAiPlans.freeSave), Modifier.weight(1f), Icons.Default.Check, AdminTone.Primary, enabled = text.toIntOrNull() != null) {
                text.toIntOrNull()?.let(onSave)
                close()
            }
        }
    }
}

@Composable
fun AdminAiTutorPlansScreen(navController: NavHostController, session: SessionViewModel) {
    val vm: AiPlansAdminVm = viewModel()
    val sessionState by session.state.collectAsStateWithLifecycle()
    var editingId by remember { mutableStateOf<String?>(null) }
    val newDraft = remember { PlanDraft(null) }

    AdminScaffold(tr(StrAiPlans.adminTitle), navController, vm) { data ->
        if (!sessionState.can("ai_tutor.manage")) {
            item { AdminNote(tr(ErrorText.forbidden), AdminTone.Danger) }
            return@AdminScaffold
        }

        item {
            val revenue = data.sales.revenue.entries.joinToString(" · ") { formatMoney(it.value, it.key) }.ifBlank { "0" }
            AdminSummaryStrip(
                listOf(
                    AdminStat(data.sales.activeSubscribers.toString(), tr(StrAiPlans.subscribers), Ink.Teal),
                    AdminStat(data.sales.paidOrders.toString(), tr(StrAiPlans.paidOrders)),
                    AdminStat(revenue, tr(StrAiPlans.revenue)),
                ),
            )
        }
        item {
            AdminSummaryStrip(
                listOf(
                    AdminStat(data.sales.repliesSold.toString(), tr(StrAiPlans.repliesSold), Ink.Teal),
                    AdminStat(data.sales.repliesUsed.toString(), tr(StrAiPlans.repliesUsed)),
                    AdminStat(data.sales.repliesExpired.toString(), tr(StrAiPlans.repliesExpired), Ink.Coral),
                ),
            )
        }
        item { FreeRepliesCard(data.freeReplies) { count -> vm.act { AiTutorPlansRepository.setFreeDailyReplies(count) } } }
        item {
            AdminFormCard(tr(StrAiPlans.newPlan), icon = Icons.Default.Sell) { close ->
                PlanForm(newDraft, editing = false, actionLabel = tr(StrAiPlans.createPlan), onSubmit = {
                    vm.act { AiTutorPlansRepository.createPlan(newDraft.toInput(editing = false)) }
                    close()
                })
            }
        }

        if (data.plans.isEmpty()) {
            item { AdminEmpty(tr(StrAiPlans.noPlansAdmin), tr(StrAiPlans.noPlansAdminBody), icon = Icons.Default.Sell) }
        }
        items(data.plans, key = { it.id }) { plan ->
            val onSale = plan.saleActive()
            AdminItemCard(
                title = if (plan.nameAr.isNotBlank()) "${plan.name} · ${plan.nameAr}" else plan.name,
                subtitle = "${trf(StrAiPlans.repliesCount, plan.replies)} · ${trf(StrAiPlans.validFor, plan.periodDays)}",
                leading = { AdminBadgeIcon(Icons.Default.Sell, tint = if (plan.isActive) Ink.Teal else Ink.Neutral) },
                trailing = {
                    Pill(
                        formatMoney(plan.effectivePrice(), plan.currency),
                        background = if (plan.isActive) Ink.TealSoft else Ink.SurfaceHigh,
                        foreground = if (plan.isActive) Ink.Teal else Ink.TextMuted,
                    )
                },
                stateKey = plan.id,
            ) {
                if (onSale) {
                    KeyValueRow(tr(StrAiPlans.fPrice), "${formatMoney(plan.price, plan.currency)} → ${formatMoney(plan.effectivePrice(), plan.currency)}")
                    plan.saleEndsAt?.let { KeyValueRow(tr(StrAiPlans.offerEndsLabel), formatDate(it)) }
                }
                ToggleRow(tr(StrAiPlans.activeLabel), plan.isActive) { on -> vm.act { AiTutorPlansRepository.setActive(plan.id, on) } }
                Divider()
                if (editingId == plan.id) {
                    val draft = remember(plan.id) { PlanDraft(plan) }
                    Spacer(Modifier.height(12.dp))
                    PlanForm(draft, editing = true, actionLabel = tr(StrAiPlans.savePlan), onSubmit = {
                        vm.act { AiTutorPlansRepository.updatePlan(plan.id, draft.toInput(editing = true)) }
                        editingId = null
                    }, onCancel = { editingId = null })
                } else {
                    AdminActions {
                        AdminButton(tr(StrAiPlans.editPlan), Modifier.weight(1f), Icons.Default.Edit, AdminTone.Primary) { editingId = plan.id }
                        AdminDeleteIcon("${tr(StrAiPlans.deletePlan)} · ${plan.name}", tr(StrAiPlans.deleteConfirm)) {
                            vm.act { AiTutorPlansRepository.deletePlan(plan.id) }
                        }
                    }
                }
            }
        }
    }
}
