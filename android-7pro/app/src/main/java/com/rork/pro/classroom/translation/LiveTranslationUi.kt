package com.rork.pro.classroom.translation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rork.pro.ui.i18n.StrLiveTranslation
import com.rork.pro.ui.i18n.liveTranslationLanguageName
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.delay

/** The in-meeting translation sheet: my language, "translate my voice", connection state. */
@Composable
fun LiveTranslationPanel(
    state: TranslationUiState,
    micLocked: Boolean,
    onLanguage: (String) -> Unit,
    onToggleSpeaking: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Ink.Surface)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Translate, null, tint = Ink.Amber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                tr(StrLiveTranslation.title),
                color = Ink.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null, tint = Ink.TextMuted) }
        }
        Text(tr(StrLiveTranslation.subtitle), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)

        Spacer(Modifier.height(10.dp))
        val (statusText, statusColor) = when (state.status) {
            TranslationStatus.LIVE -> tr(StrLiveTranslation.statusLive) to Ink.Teal
            TranslationStatus.ERROR -> tr(StrLiveTranslation.statusError) to Ink.Coral
            else -> tr(StrLiveTranslation.statusConnecting) to Ink.Amber
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(6.dp))
            Text(statusText, color = statusColor, style = MaterialTheme.typography.labelMedium)
        }
        if (state.status == TranslationStatus.LIVE) {
            Text(
                tr(if (state.cloning) StrLiveTranslation.voiceCloningOn else StrLiveTranslation.voiceCloningOff),
                color = Ink.TextMuted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        Text(tr(StrLiveTranslation.myLanguage), color = Ink.TextSecondary, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.languages.forEach { code ->
                val selected = code == state.language
                Text(
                    liveTranslationLanguageName(code),
                    color = if (selected) Ink.OnAmber else Ink.TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) Ink.Amber else Ink.SurfaceHigh)
                        .clickable { onLanguage(code) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(StrLiveTranslation.translateMyVoice), color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                Text(tr(StrLiveTranslation.translateMyVoiceHint), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = state.speaking,
                enabled = state.speaking || (state.status == TranslationStatus.LIVE && !micLocked),
                onCheckedChange = { onToggleSpeaking() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ink.Canvas,
                    checkedTrackColor = Ink.Amber,
                    uncheckedThumbColor = Ink.TextMuted,
                    uncheckedTrackColor = Ink.SurfaceHigh,
                ),
            )
        }

        val error = when {
            micLocked -> StrLiveTranslation.errMicLocked
            state.errorCode == null -> null
            state.errorCode == "MIC_LOCKED" -> StrLiveTranslation.errMicLocked
            state.errorCode == "MIC_UNAVAILABLE" -> StrLiveTranslation.errMicUnavailable
            else -> StrLiveTranslation.errGeneric
        }
        if (error != null) {
            Text(
                tr(error),
                color = Ink.Coral,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

/** The latest translated sentence, shown briefly over the call so a missed word can be read. */
@Composable
fun LiveTranslationCaption(caption: TranslationCaption?, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(caption?.at) {
        if (caption == null) { visible = false; return@LaunchedEffect }
        visible = true
        delay(6_000)
        visible = false
    }
    AnimatedVisibility(visible = visible && caption != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        val c = caption ?: return@AnimatedVisibility
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .border(1.dp, Ink.Amber.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Translate, null, tint = Ink.Amber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                if (c.speakerName.isNotBlank()) {
                    Text(c.speakerName, color = Ink.Amber, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                Text(
                    c.text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
