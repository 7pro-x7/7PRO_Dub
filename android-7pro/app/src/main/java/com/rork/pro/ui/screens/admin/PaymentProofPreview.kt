package com.rork.pro.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.rork.pro.data.MediaRepository
import com.rork.pro.ui.i18n.StrAdmin
import com.rork.pro.ui.i18n.StrApprovalX
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.theme.Ink

/**
 * The transfer screenshot the student attached, shown right on the card so the owner does not
 * have to open anything to see it; a tap opens it full screen.
 *
 * The proofs bucket is private, so the image comes through a short-lived signed link that is
 * created only when the card is actually on screen (list items compose lazily).
 *
 * [whenText] is the exact date and time the transfer was sent; it is repeated in the full-screen
 * header so it stays visible next to the image.
 */
@Composable
internal fun PaymentProofPreview(
    proofId: String,
    proofPath: String,
    senderPhone: String,
    whenText: String? = null,
    modifier: Modifier = Modifier,
) {
    var url by remember(proofId, proofPath) { mutableStateOf<String?>(null) }
    var failed by remember(proofId, proofPath) { mutableStateOf(false) }
    var open by remember(proofId) { mutableStateOf(false) }

    LaunchedEffect(proofId, proofPath) {
        runCatching { MediaRepository.paymentProofUrl(proofPath) }
            .onSuccess { url = it }
            .onFailure { failed = true }
    }

    if (open) ProofViewer(proofId, proofPath, senderPhone, whenText) { open = false }

    Text(
        tr(StrApprovalX.paymentProof),
        color = Ink.TextSecondary,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(bottom = 6.dp),
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.SurfaceHigh)
            .border(1.dp, Ink.Hairline, RoundedCornerShape(14.dp))
            .clickable(enabled = url != null) { open = true },
        contentAlignment = Alignment.Center,
    ) {
        when {
            failed -> Text(
                tr(StrAdmin.reviewProofFailed),
                color = Ink.Coral,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )

            url == null -> CircularProgressIndicator(
                color = Ink.Amber,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(28.dp),
            )

            else -> AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    if (url != null) {
        Text(
            tr(StrApprovalX.tapToEnlarge),
            color = Ink.TextMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
