package com.rork.pro.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rork.pro.data.MediaRepository
import com.rork.pro.data.toAppError
import com.rork.pro.ui.i18n.StrEx
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.launch

/**
 * One file picker shared by every media slot on a screen.
 *
 * The Android picker is a single launcher, so the slot that asked for a file is remembered
 * here and handed the result — that is what lets one question attach an image, a clip and a
 * video without three separate launchers.
 */
class FilePicker internal constructor(
    private val launch: (String) -> Unit,
    private val pending: MutableState<((Uri) -> Unit)?>,
) {
    fun pick(mime: String, onPicked: (Uri) -> Unit) {
        pending.value = onPicked
        launch(mime)
    }
}

@Composable
fun rememberFilePicker(): FilePicker {
    val pending = remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { pending.value?.invoke(it) }
        pending.value = null
    }
    return remember(launcher) { FilePicker({ mime -> launcher.launch(mime) }, pending) }
}

/**
 * Attaches one file from the phone to a question.
 *
 * The upload happens here and the caller only ever receives the finished public URL, so an
 * image, an audio clip or a video is added the same way anywhere in the app.
 */
@Composable
fun MediaSlot(
    label: String,
    mime: String,
    url: String,
    folder: String,
    picker: FilePicker,
    modifier: Modifier = Modifier,
    onUrl: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uploading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Text(
                    when {
                        uploading -> tr(StrEx.uploading)
                        url.isNotBlank() -> tr(StrEx.attached)
                        else -> "—"
                    },
                    color = if (url.isNotBlank() && !uploading) Ink.Teal else Ink.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryAction(
                    if (url.isBlank()) tr(StrEx.addFromPhone) else tr(StrEx.replaceFile),
                    enabled = !uploading,
                ) {
                    failure = null
                    picker.pick(mime) { uri ->
                        scope.launch {
                            uploading = true
                            runCatching {
                                val file = MediaRepository.read(context, uri)
                                MediaRepository.upload(file, folder)
                            }.onSuccess { onUrl(it) }
                                .onFailure { failure = it.toAppError().message }
                            uploading = false
                        }
                    }
                }
                if (url.isNotBlank() && !uploading) {
                    SecondaryAction(tr(StrEx.removeFile), tint = Ink.Coral) { onUrl("") }
                }
            }
        }
        failure?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
        }
    }
}
