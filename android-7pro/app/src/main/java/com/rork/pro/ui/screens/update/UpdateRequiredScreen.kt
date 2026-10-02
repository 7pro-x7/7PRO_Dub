package com.rork.pro.ui.screens.update

import android.content.Intent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.rork.pro.BuildConfig
import com.rork.pro.data.UpdateRule
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.i18n.StrUpdate
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.i18n.trf
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import com.rork.pro.ui.theme.appBackdrop

/**
 * Full-screen stop shown while the owner requires a newer build.
 *
 * There is deliberately no way past it: the rule exists so nobody keeps using a version the
 * owner has retired. The store button is the only route forward, plus a re-check for people
 * who have just installed the update.
 */
@Composable
fun UpdateRequiredScreen(rule: UpdateRule, onRecheck: () -> Unit) {
    val context = LocalContext.current
    val pulse = rememberInfiniteTransition(label = "update")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulse",
    )

    Column(
        Modifier
            .fillMaxSize()
            .appBackdrop()
            .systemBarsPadding()
            .padding(horizontal = Dimens.screenPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .scale(scale)
                .clip(RoundedCornerShape(30.dp))
                .background(Brush.linearGradient(listOf(Ink.Amber, Ink.AmberPressed))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.RocketLaunch,
                contentDescription = null,
                tint = Ink.OnAmber,
                modifier = Modifier.size(44.dp),
            )
        }

        Spacer(Modifier.height(26.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Ink.Amber, fontWeight = FontWeight.Black)) { append("7") }
                withStyle(SpanStyle(color = Ink.TextPrimary, fontWeight = FontWeight.Black)) { append("PRO") }
            },
            fontSize = 26.sp,
        )

        Spacer(Modifier.height(14.dp))
        Text(
            tr(StrUpdate.title),
            style = MaterialTheme.typography.headlineSmall,
            color = Ink.TextPrimary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            rule.message.ifBlank { tr(StrUpdate.body) },
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        Spacer(Modifier.height(30.dp))
        PrimaryAction(tr(StrUpdate.updateNow)) {
            val target = rule.url.trim().ifBlank {
                "https://play.google.com/store/apps/details?id=${context.packageName}"
            }
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri())) }
        }
        Spacer(Modifier.height(10.dp))
        SecondaryAction(tr(StrUpdate.alreadyUpdated), Modifier.fillMaxWidth(), onClick = onRecheck)

        Spacer(Modifier.height(24.dp))
        Text(
            trf(StrUpdate.versionLine, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString()),
            color = Ink.TextMuted,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
