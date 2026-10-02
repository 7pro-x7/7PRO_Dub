package com.rork.pro.ui.screens.tutor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.rork.pro.character.BackgroundRemover
import com.rork.pro.character.CharacterRepository
import com.rork.pro.character.CharacterRig
import com.rork.pro.character.FaceRigFinder
import com.rork.pro.character.RigConfidence
import com.rork.pro.character.loadCutout
import com.rork.pro.character.TutorCharacter
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.LoadingBlock
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.i18n.StrCharacters
import com.rork.pro.ui.i18n.StrTutor
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.navigation.DetailHeader
import com.rork.pro.ui.theme.Dimens
import com.rork.pro.ui.theme.Ink
import com.rork.pro.tutor.AudioPayload
import com.rork.pro.tutor.TutorApi
import com.rork.pro.tutor.VoiceCapture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private enum class Step { PICK, CHOOSE, CUT, FACE, DETAILS }
private const val MAX_UPLOAD_SIDE = 1024

/**
 * Owner-only: turn a photo or drawing into a Tutor character. Background removal runs fully
 * on-device (U-2-Net-p via ONNX Runtime — see character/BackgroundRemover.kt), so nothing is
 * uploaded until the owner has already seen and approved the cutout.
 */
@Composable
fun CharacterUploadScreen(navController: NavHostController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val remover = remember { BackgroundRemover(context) }
    DisposableCleanup { remover.close() }

    var step by remember { mutableStateOf(Step.PICK) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var cutout by remember { mutableStateOf<Bitmap?>(null) }
    var removing by remember { mutableStateOf(false) }
    // Where the eyes and mouth are: found automatically when the picture is chosen, then refined by hand.
    var rig by remember { mutableStateOf(CharacterRig.DEFAULT) }
    var rigConfidence by remember { mutableStateOf<RigConfidence?>(null) }
    var rigTouched by remember { mutableStateOf(false) }
    var detecting by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TutorCharacter?>(null) }
    var name by remember { mutableStateOf("") }
    var voice by remember { mutableStateOf("male") }
    var voiceMode by remember { mutableStateOf("system") }
    var voiceSample by remember { mutableStateOf<ByteArray?>(null) }
    var recordingVoice by remember { mutableStateOf(false) }
    val voiceCapture = remember { VoiceCapture() }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var characters by remember { mutableStateOf<List<TutorCharacter>?>(null) }
    var defaultsVisible by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    fun refresh() {
        scope.launch {
            characters = runCatching { CharacterRepository.list() }.getOrNull() ?: emptyList()
            runCatching { CharacterRepository.defaultsVisible() }.onSuccess { defaultsVisible = it }
        }
    }
    fun toggleDefault(voice: String, visible: Boolean) {
        val other = if (voice == "male") "female" else "male"
        val otherVisible = defaultsVisible[other] ?: true
        if (!visible && !otherVisible && characters.isNullOrEmpty()) { error = tr(StrCharacters.keepOneVisible); return }
        error = null
        val previous = defaultsVisible
        defaultsVisible = previous + (voice to visible)
        scope.launch {
            if (runCatching { CharacterRepository.setDefaultVisible(voice, visible) }.isFailure) {
                defaultsVisible = previous
                error = tr(StrCharacters.saveFailed)
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    // Cloned voices need the owner's voice service to be up. Ask the Worker instead of assuming, and
    // only offer the option when it really works — otherwise a "cloned" character would silently talk
    // in the stock voice.
    var cloneAvailable by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val url = runCatching { TutorApi.status().workerUrl }.getOrNull()
        cloneAvailable = !url.isNullOrBlank() && TutorApi.cloningAvailable(url)
        if (!cloneAvailable) voiceMode = "system"
    }

    /** Runs the automatic eye/mouth finder on the current picture and replaces the placement with its result. */
    fun runDetect() {
        val cut = cutout ?: return
        val src = source ?: cut
        scope.launch {
            detecting = true
            val found = runCatching { FaceRigFinder.find(context, src, cut) }.getOrNull()
            if (found != null) {
                rig = found.rig
                rigConfidence = found.confidence
                rigTouched = false
            }
            detecting = false
        }
    }
    fun rigSourceName(): String = when {
        rigTouched || rigConfidence == null -> "manual"
        rigConfidence == RigConfidence.LANDMARKS -> "landmarks"
        else -> "estimated"
    }
    fun leaveEditor() { editing = null; step = Step.PICK }

    fun recordVoiceSample() {
        scope.launch {
            recordingVoice = true
            error = null
            voiceSample = runCatching { voiceCapture.recordSample() }.getOrNull()
            recordingVoice = false
            if (voiceSample == null) error = tr(StrCharacters.voiceRecordingFailed)
        }
    }
    val voicePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) recordVoiceSample() else error = tr(StrCharacters.microphonePermissionRequired)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Decoded already shrunk to ~2048px and never allowed to throw: a full-size 48MP photo
            // (or a file the picker can no longer open) used to take the whole app down here.
            val bmp = withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2048) sample *= 2
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                }.getOrNull()
            } ?: run {
                error = tr(StrCharacters.imageLoadFailed)
                return@launch
            }
            source = bmp
            cutout = null
            error = null
            step = Step.CHOOSE
        }
    }

    Column(Modifier.fillMaxSize().background(Ink.Canvas)) {
        DetailHeader(tr(StrCharacters.screenTitle), onBack = {
            if (step == Step.PICK) navController.popBackStack() else leaveEditor()
        })
        LazyColumn(contentPadding = PaddingValues(Dimens.screenPadding, 8.dp, Dimens.screenPadding, 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            when (step) {
                Step.PICK -> {
                    item { Text(tr(StrCharacters.screenBody), color = Ink.TextSecondary, style = MaterialTheme.typography.bodyMedium) }
                    item {
                        InkCard(onClick = { pickImage.launch("image/*") }) {
                            Text(tr(StrCharacters.addCharacter), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(tr(StrCharacters.choosePhoto), color = Ink.Teal, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    item { Text(tr(StrCharacters.ownerNotice), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall) }
                    item {
                        InkCard {
                            Text(tr(StrCharacters.defaultsTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            Text(tr(StrCharacters.defaultsBody), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            listOf("male" to StrTutor.tutorName, "female" to StrTutor.tutorNameFemale).forEach { (key, label) ->
                                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(tr(label), color = Ink.TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    androidx.compose.material3.Switch(
                                        checked = defaultsVisible[key] ?: true,
                                        onCheckedChange = { toggleDefault(key, it) },
                                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedTrackColor = Ink.Teal),
                                    )
                                }
                            }
                            if (error != null) Text(error!!, color = Ink.Coral, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    val list = characters
                    if (list == null) {
                        item { LoadingBlock() }
                    } else if (list.isEmpty()) {
                        item { Text(tr(StrCharacters.noneYet), color = Ink.TextMuted, style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        items(list, key = { it.id }) { c ->
                            InkCard {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(model = c.imageUrl, contentDescription = null, modifier = Modifier.height(48.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp)))
                                    Spacer(Modifier.width(12.dp))
                                    Text(c.name, color = Ink.TextPrimary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                    androidx.compose.material3.IconButton(onClick = {
                                        scope.launch {
                                            val bmp = loadCutout(context, c.imageUrl)?.asAndroidBitmap()
                                            if (bmp == null) { error = tr(StrCharacters.saveFailed); return@launch }
                                            error = null
                                            cutout = bmp; source = bmp
                                            rig = c.rig(); rigConfidence = null; rigTouched = false
                                            editing = c
                                            step = Step.FACE
                                        }
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = tr(StrCharacters.editFace), tint = Ink.Teal)
                                    }
                                    androidx.compose.material3.IconButton(onClick = { scope.launch { runCatching { CharacterRepository.delete(c.id) }; refresh() } }) {
                                        Icon(Icons.Default.Delete, contentDescription = tr(StrCharacters.deleteCharacter), tint = Ink.Coral)
                                    }
                                }
                            }
                        }
                    }
                }
                Step.CHOOSE -> item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(tr(StrCharacters.imageChoiceTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                        Text(tr(StrCharacters.imageChoiceBody), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp))
                        CheckerPreview(null, source)
                        Spacer(Modifier.height(16.dp))
                        PrimaryAction(tr(StrCharacters.removeBackground), containerColor = Ink.Teal, contentColor = Ink.OnTeal) {
                            val bmp = source ?: return@PrimaryAction
                            error = null
                            removing = true
                            step = Step.CUT
                            scope.launch {
                                val result = runCatching { remover.removeBackground(bmp) }
                                removing = false
                                cutout = result.getOrNull()
                                if (result.isFailure) error = tr(StrCharacters.saveFailed)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        SecondaryAction(tr(StrCharacters.keepOriginal), modifier = Modifier.fillMaxWidth()) {
                            cutout = source
                            error = null
                            step = Step.FACE
                            runDetect()
                        }
                    }
                }
                Step.CUT -> item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CheckerPreview(cutout, source)
                        Spacer(Modifier.height(16.dp))
                        if (removing) {
                            CircularProgressIndicator(color = Ink.Teal)
                            Spacer(Modifier.height(8.dp))
                            Text(tr(StrCharacters.removingBackground), color = Ink.TextSecondary)
                        } else if (cutout != null) {
                            PrimaryAction(tr(StrCharacters.next), containerColor = Ink.Teal, contentColor = Ink.OnTeal) { step = Step.FACE; runDetect() }
                        }
                        if (error != null) {
                            Text(error!!, color = Ink.Coral, modifier = Modifier.padding(top = 8.dp))
                            Spacer(Modifier.height(8.dp))
                            SecondaryAction(tr(StrCharacters.keepOriginal), modifier = Modifier.fillMaxWidth()) {
                                cutout = source
                                error = null
                                step = Step.FACE
                                runDetect()
                            }
                        }
                    }
                }
                Step.FACE -> item {
                    val bmp = cutout
                    if (bmp != null) {
                        Column {
                            Text(tr(StrCharacters.faceTitle), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium)
                            Text(tr(StrCharacters.faceBody), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                            FaceRigEditor(
                                bitmap = bmp,
                                rig = rig,
                                onRigChange = { rig = it; rigTouched = true },
                                suggestion = rigConfidence,
                                detecting = detecting,
                                onAutoDetect = ::runDetect,
                            )
                            Spacer(Modifier.height(16.dp))
                            if (error != null) Text(error!!, color = Ink.Coral, modifier = Modifier.padding(bottom = 8.dp))
                            val target = editing
                            if (target == null) {
                                PrimaryAction(tr(StrCharacters.next), enabled = !detecting, containerColor = Ink.Teal, contentColor = Ink.OnTeal) { step = Step.DETAILS }
                            } else {
                                PrimaryAction(
                                    if (saving) tr(StrCharacters.saving) else tr(StrCharacters.save),
                                    enabled = !detecting, loading = saving, containerColor = Ink.Teal, contentColor = Ink.OnTeal,
                                ) {
                                    saving = true; error = null
                                    scope.launch {
                                        val result = runCatching { CharacterRepository.updateRig(target.id, rig, rigSourceName()) }
                                        saving = false
                                        if (result.isSuccess) { leaveEditor(); refresh() } else error = tr(StrCharacters.saveFailed)
                                    }
                                }
                            }
                        }
                    }
                }
                Step.DETAILS -> item {
                    Column {
                        Text(tr(StrCharacters.nameLabel), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(Ink.Surface).border(1.dp, Ink.Hairline, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                            if (name.isEmpty()) Text(tr(StrCharacters.nameHint), color = Ink.TextMuted)
                            BasicTextField(value = name, onValueChange = { name = it.take(40) }, textStyle = TextStyle(color = Ink.TextPrimary, fontSize = 16.sp), modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(tr(StrCharacters.voiceLabel), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("male" to tr(StrCharacters.voiceMale), "female" to tr(StrCharacters.voiceFemale)).forEach { (key, label) ->
                                Box(
                                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
                                        .background(if (voice == key) Ink.TextPrimary else Ink.Surface)
                                        .border(1.dp, if (voice == key) Ink.TextPrimary else Ink.Hairline, RoundedCornerShape(22.dp))
                                        .clickable { voice = key }.padding(horizontal = 18.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(label, color = if (voice == key) Ink.Canvas else Ink.TextPrimary) }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(tr(StrCharacters.voiceModeLabel), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("system" to tr(StrCharacters.systemVoice), "clone" to tr(StrCharacters.clonedVoice)).filter { it.first != "clone" || cloneAvailable }.forEach { (key, label) ->
                                Box(
                                    Modifier.height(44.dp).clip(RoundedCornerShape(22.dp))
                                        .background(if (voiceMode == key) Ink.TextPrimary else Ink.Surface)
                                        .border(1.dp, if (voiceMode == key) Ink.TextPrimary else Ink.Hairline, RoundedCornerShape(22.dp))
                                        .clickable { voiceMode = key }.padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(label, color = if (voiceMode == key) Ink.Canvas else Ink.TextPrimary) }
                            }
                        }
                        if (!cloneAvailable) {
                            Spacer(Modifier.height(6.dp))
                            Text(tr(StrCharacters.cloneUnavailable), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (voiceMode == "clone") {
                            Spacer(Modifier.height(8.dp))
                            Text(tr(StrCharacters.voiceSampleHint), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            SecondaryAction(
                                label = if (recordingVoice) tr(StrCharacters.recordingVoice) else if (voiceSample != null) tr(StrCharacters.rerecordVoice) else tr(StrCharacters.recordVoice),
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !recordingVoice && !saving,
                                tint = Ink.Teal,
                            ) {
                                error = null
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                    recordVoiceSample()
                                } else {
                                    voicePermission.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            if (recordingVoice) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    CircularProgressIndicator(color = Ink.Teal, modifier = Modifier.height(20.dp).aspectRatio(1f), strokeWidth = 2.dp)
                                    Text(tr(StrCharacters.recordingVoice), color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
                                }
                            } else if (voiceSample != null) {
                                Text(tr(StrCharacters.voiceSampleReady), color = Ink.Teal, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        if (error != null) Text(error!!, color = Ink.Coral, modifier = Modifier.padding(bottom = 8.dp))
                        PrimaryAction(
                            if (saving) tr(StrCharacters.saving) else tr(StrCharacters.save),
                            enabled = !recordingVoice && (voiceMode != "clone" || voiceSample != null),
                            loading = saving,
                            containerColor = Ink.Teal,
                            contentColor = Ink.OnTeal,
                        ) {
                            val name0 = name.trim()
                            if (name0.isEmpty()) { error = tr(StrCharacters.nameRequired); return@PrimaryAction }
                            if (voiceMode == "clone" && voiceSample == null) { error = tr(StrCharacters.recordVoiceFirst); return@PrimaryAction }
                            val wav = if (voiceMode == "clone") AudioPayload.wavBytes(voiceSample!!, VoiceCapture.SAMPLE_RATE) else null
                            val bmp = cutout ?: return@PrimaryAction
                            saving = true; error = null
                            scope.launch {
                                val png = withContext(Dispatchers.Default) {
                                    // Cap the longest side so a full-resolution phone photo (often
                                    // 10+ MB as a lossless PNG with alpha) can't blow past the
                                    // storage bucket's upload limit or bloat every student's download.
                                    val longest = maxOf(bmp.width, bmp.height)
                                    val scaled = if (longest > MAX_UPLOAD_SIDE) {
                                        val f = MAX_UPLOAD_SIDE.toFloat() / longest
                                        Bitmap.createScaledBitmap(bmp, (bmp.width * f).toInt().coerceAtLeast(1), (bmp.height * f).toInt().coerceAtLeast(1), true)
                                    } else bmp
                                    ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                                }
                                val result = runCatching { CharacterRepository.create(name0, voice, png, rig, rigSourceName(), voiceMode, wav) }
                                saving = false
                                if (result.isSuccess) {
                                    step = Step.PICK; source = null; cutout = null; name = ""; voiceMode = "system"; voiceSample = null
                                    rig = CharacterRig.DEFAULT; rigConfidence = null; rigTouched = false
                                    refresh()
                                } else {
                                    error = tr(StrCharacters.saveFailed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckerPreview(cutout: Bitmap?, source: Bitmap?) {
    Box(Modifier.height(260.dp).aspectRatio(1f).clip(RoundedCornerShape(20.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            val cell = 16f
            var y = 0f
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) 0f else cell
                while (x < size.width) {
                    drawRect(Color(0xFFE4D9C7), topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(cell, cell))
                    x += cell * 2
                }
                y += cell; row++
            }
        }
        val bmp = cutout ?: source
        if (bmp != null) Image(bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun DisposableCleanup(onDispose: () -> Unit) {
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { onDispose() } }
}
