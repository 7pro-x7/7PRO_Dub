package com.arena.arabicdub.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arena.arabicdub.domain.SourceSpec
import com.arena.arabicdub.ui.DubViewModel
import com.arena.arabicdub.ui.UiState

@Composable
fun HomeScreen(
    state: UiState,
    onStart: (SourceSpec) -> Unit,
    viewModel: DubViewModel,
) {
    var ytUrl by remember { mutableStateOf("") }

    val pickVideo = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onStart(SourceSpec.LocalVideo(uri))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(6.dp))

        // ---------- 1) ملف من الجهاز ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1) اختر فيديو من جهازك", style = MaterialTheme.typography.titleMedium)
                Text(
                    "أي ملف فيديو أو صوت يحتوي كلامًا بالإنجليزية",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = { pickVideo.launch(arrayOf("video/*", "audio/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.VideoFile, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("اختيار ملف")
                }
            }
        }

        // ---------- 2) رابط يوتيوب ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("2) أو الصق رابط يوتيوب", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = ytUrl,
                    onValueChange = { ytUrl = it },
                    label = { Text("https://www.youtube.com/watch?v=...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Go,
                        keyboardType = KeyboardType.Uri,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = ytUrl.isNotBlank(),
                    onClick = { onStart(SourceSpec.YouTubeLink(ytUrl)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Link, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("دبّل هذا الفيديو")
                }
                Text(
                    "يُجلب عبر Piped (مشروع مفتوح المصدر) — يُنصح بالاستخدام الشخصي مع احترام حقوق النشر",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }

        // ---------- 3) الإعدادات ----------
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("إعدادات الدبلجة", style = MaterialTheme.typography.titleMedium)

                Text("نموذج التعرف على الكلام (Whisper)", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "tiny" to "Tiny\nسريع",
                        "base" to "Base\nمتوازن",
                        "small" to "Small\nأدق",
                        "medium" to "Medium\nالأدق",
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = state.whisperModel == key,
                            onClick = { viewModel.update { s -> s.copy(whisperModel = key) } },
                            label = { Text(label, maxLines = 2) },
                        )
                    }
                }

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("الترجمة عبر MyMemory (مجاني عبر الإنترنت)")
                        Text(
                            "معطّلًا: تتم الترجمة محليًا داخل Whisper",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Switch(
                        checked = state.onlineTranslate,
                        onCheckedChange = { checked ->
                            viewModel.update { s -> s.copy(onlineTranslate = checked) }
                        },
                    )
                }

                Column {
                    Text(
                        "مستوى الصوت الأصلي (موسيقى/أصوات) في الخلفية: ${state.backgroundVolume}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Slider(
                        value = state.backgroundVolume.toFloat(),
                        onValueChange = { v ->
                            viewModel.update { s ->
                                s.copy(backgroundVolume = v.toInt().coerceIn(0, 100))
                            }
                        },
                        valueRange = 0f..100f,
                    )
                }

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("حرق الترجمة العربية داخل الفيديو")
                        Text(
                            "يضيف شريط ترجمة أسفل الشاشة (يتطلب إعادة ترميز)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Switch(
                        checked = state.burnSubtitles,
                        onCheckedChange = { checked ->
                            viewModel.update { s -> s.copy(burnSubtitles = checked) }
                        },
                    )
                }
            }
        }

        Text(
            "عند أول تشغيل يحمّل التطبيق تلقائيًا نموذج التعرف والصوت العربي (يُنصح بالواي فاي). " +
                "كل المعالجة تتم على جهازك مجانًا، وبدون أي حسابات.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(16.dp))
    }
}
