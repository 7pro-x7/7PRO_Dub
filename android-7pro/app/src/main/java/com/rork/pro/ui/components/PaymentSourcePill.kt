package com.rork.pro.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.rork.pro.data.PaymentSource
import com.rork.pro.ui.i18n.StrApprovalX
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.theme.Ink

/** One small label saying how a subscription was paid for. Draws nothing for an unknown source. */
@Composable
fun PaymentSourcePill(source: String?, modifier: Modifier = Modifier) {
    when (source) {
        PaymentSource.APP_PAID -> Pill(tr(StrApprovalX.sourceAppPaid), modifier, Ink.TealSoft, Ink.Teal)
        PaymentSource.TEACHER_MANUAL -> Pill(tr(StrApprovalX.sourceTeacherManual), modifier, Ink.SkySoft, Ink.Sky)
        PaymentSource.NO_PAYMENT -> Pill(tr(StrApprovalX.sourceNoPayment), modifier, Ink.CoralSoft, Ink.Coral)
        PaymentSource.AWAITING_REVIEW -> Pill(tr(StrApprovalX.sourceAwaitingReview), modifier, Ink.AmberSoft, Ink.Amber)
        PaymentSource.AWAITING_PAYMENT -> Pill(tr(StrApprovalX.sourceAwaitingPayment), modifier, Ink.AmberSoft, Ink.Amber)
        else -> Unit
    }
}

/** "Vodafone Cash"-style name for the stored brand code. */
fun paymentBrandLabel(brand: String?): String? = when (brand?.uppercase()) {
    null, "" -> null
    "VODAFONE" -> "Vodafone Cash"
    "ETISALAT" -> "Etisalat Cash"
    "ORANGE" -> "Orange Cash"
    "WE" -> "WE Pay"
    "INSTAPAY" -> "InstaPay"
    else -> brand.lowercase().replaceFirstChar { it.uppercase() }
}
