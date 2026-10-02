package com.rork.pro.ui.screens.teacher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rork.pro.data.TestQuestionRow
import com.rork.pro.ui.components.Divider
import com.rork.pro.ui.components.InkCard
import com.rork.pro.ui.components.MediaSlot
import com.rork.pro.ui.components.Pill
import com.rork.pro.ui.components.PrimaryAction
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.components.WizardSteps
import com.rork.pro.ui.components.rememberFilePicker
import com.rork.pro.ui.i18n.Str
import com.rork.pro.ui.i18n.StrEx
import com.rork.pro.ui.i18n.codeLabel
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.theme.Ink

/** Everything [QuestionWizard] gathers for one question, before it's saved via [ExerciseRepository]. */
data class QuestionDraft(
    val kind: String,
    val skill: String,
    val prompt: String,
    val options: List<String>,
    val correctIndexes: List<Int>,
    val correctText: List<String>,
    val explanation: String,
    val imageUrl: String,
    val audioUrl: String,
    val videoUrl: String,
)

private val KINDS = listOf("SINGLE", "MULTI", "TRUE_FALSE", "TEXT")
private val SKILLS = listOf("GRAMMAR", "VOCABULARY", "READING", "LISTENING", "WRITING", "SPEAKING")

private fun kindLabel(kind: String): String = codeLabel(kind)

/**
 * One question, built in four short steps instead of one long form: pick how the student
 * answers, write the content for that kind, attach media, then preview it exactly as the
 * student (or, toggled, the answer key) will see it. The same four steps run for every answer
 * kind, which is what makes the whole question bank feel like one considered tool.
 *
 * [wizardKey] resets all local state when it changes (the exercise id in "add" mode, the
 * question id in "edit" mode) — the same pattern the old inline forms used. [initial] pre-fills
 * every field for editing; leave it null to start blank. [onCancel] is only shown when set
 * (edit mode); "add" mode has no cancel because the composer is always on screen.
 */
@Composable
fun QuestionWizard(
    wizardKey: String,
    initial: TestQuestionRow?,
    busy: Boolean,
    submitLabel: String,
    onSubmit: (QuestionDraft) -> Unit,
    onCancel: (() -> Unit)?,
) {
    var step by remember(wizardKey) { mutableIntStateOf(0) }
    var justAdded by remember(wizardKey) { mutableStateOf(false) }

    var prompt by remember(wizardKey) { mutableStateOf(initial?.prompt.orEmpty()) }
    var kind by remember(wizardKey) { mutableStateOf(initial?.kind ?: "SINGLE") }
    var skill by remember(wizardKey) { mutableStateOf(initial?.skill ?: "GRAMMAR") }
    var options by remember(wizardKey) { mutableStateOf(initial?.options?.joinToString(", ").orEmpty()) }
    var correct by remember(wizardKey) {
        mutableStateOf(initial?.correctIndexes?.joinToString(",") { (it + 1).toString() }.orEmpty())
    }
    var acceptedText by remember(wizardKey) { mutableStateOf(initial?.correctText?.joinToString(", ").orEmpty()) }
    var explanation by remember(wizardKey) { mutableStateOf(initial?.explanation.orEmpty()) }
    var imageUrl by remember(wizardKey) { mutableStateOf(initial?.imageUrl.orEmpty()) }
    var audioUrl by remember(wizardKey) { mutableStateOf(initial?.audioUrl.orEmpty()) }
    var videoUrl by remember(wizardKey) { mutableStateOf(initial?.videoUrl.orEmpty()) }
    var teacherView by remember(wizardKey) { mutableStateOf(false) }

    val picker = rememberFilePicker()

    fun reset() {
        prompt = ""; kind = "SINGLE"; skill = "GRAMMAR"; options = ""; correct = ""
        acceptedText = ""; explanation = ""; imageUrl = ""; audioUrl = ""; videoUrl = ""
        teacherView = false; step = 0
    }

    val optionList = options.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val accepted = acceptedText.split(",").map { it.trim() }.filter { it.isNotBlank() }
    val correctIndexes = correct.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .map { it - 1 }
        .filter { it >= 0 && it <= optionList.lastIndex }
        .distinct()

    val contentValid = when (kind) {
        "TEXT" -> accepted.isNotEmpty()
        else -> optionList.size >= 2 && correctIndexes.isNotEmpty()
    }
    val canSubmit = prompt.isNotBlank() && contentValid

    fun draft() = QuestionDraft(
        kind = kind,
        skill = skill,
        prompt = prompt,
        options = if (kind == "TEXT") emptyList() else optionList,
        correctIndexes = if (kind == "TEXT") emptyList() else correctIndexes,
        correctText = accepted,
        explanation = explanation,
        imageUrl = imageUrl,
        audioUrl = audioUrl,
        videoUrl = videoUrl,
    )

    InkCard(color = Ink.SurfaceHigh) {
        if (justAdded) {
            QuestionSavedCard(onAddAnother = { reset(); justAdded = false })
            return@InkCard
        }

        val steps = listOf(tr(StrEx.stepType), tr(StrEx.stepContent), tr(StrEx.stepMedia), tr(StrEx.stepPreview))
        WizardSteps(steps, current = step)
        Spacer(Modifier.height(16.dp))

        when (step) {
            0 -> TypeStep(kind, skill, onKind = { newKind ->
                kind = newKind
                if (newKind == "TRUE_FALSE") options = tr(StrEx.trueLabel) + ", " + tr(StrEx.falseLabel)
                if (newKind == "TEXT") options = ""
            }, onSkill = { skill = it })

            1 -> ContentStep(
                kind = kind,
                prompt = prompt,
                onPrompt = { prompt = it },
                options = options,
                onOptions = { options = it },
                correct = correct,
                onCorrect = { correct = it.filter { c -> c.isDigit() || c == ',' } },
                acceptedText = acceptedText,
                onAcceptedText = { acceptedText = it },
            )

            2 -> MediaStep(
                imageUrl = imageUrl,
                onImage = { imageUrl = it },
                audioUrl = audioUrl,
                onAudio = { audioUrl = it },
                videoUrl = videoUrl,
                onVideo = { videoUrl = it },
                explanation = explanation,
                onExplanation = { explanation = it },
                picker = picker,
            )

            else -> PreviewStep(
                wizardKey = wizardKey,
                kind = kind,
                prompt = prompt,
                optionList = optionList,
                correctIndexes = correctIndexes,
                accepted = accepted,
                teacherView = teacherView,
                onToggleView = { teacherView = it },
            )
        }

        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) {
                SecondaryAction(tr(Str.back), Modifier.weight(1f)) { step -= 1 }
            } else if (onCancel != null) {
                SecondaryAction(tr(Str.cancel), Modifier.weight(1f)) { onCancel() }
            }
            if (step < 3) {
                PrimaryAction(
                    tr(Str.next),
                    Modifier.weight(1f),
                    enabled = step != 1 || canSubmit,
                ) { step += 1 }
            } else {
                PrimaryAction(
                    submitLabel,
                    Modifier.weight(1f),
                    enabled = canSubmit,
                    loading = busy,
                ) {
                    onSubmit(draft())
                    if (onCancel == null) justAdded = true
                }
            }
        }
    }
}

@Composable
private fun QuestionSavedCard(onAddAnother: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.size(56.dp).clip(CircleShape).background(Ink.TealSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Ink.Teal, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(tr(StrEx.questionAdded), color = Ink.TextPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(
            tr(StrEx.questionAddedBody),
            color = Ink.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        PrimaryAction(tr(StrEx.addAnotherQuestion)) { onAddAnother() }
    }
}

@Composable
private fun StepLabel(text: String) {
    Text(text, color = Ink.TextSecondary, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun TypeStep(kind: String, skill: String, onKind: (String) -> Unit, onSkill: (String) -> Unit) {
    Text(tr(StrEx.chooseKind), color = Ink.TextPrimary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    StepLabel(tr(StrEx.questionKind))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(KINDS) { option ->
            Pill(
                kindLabel(option),
                Modifier.clickable { onKind(option) },
                background = if (kind == option) Ink.AmberSoft else Ink.Surface,
                foreground = if (kind == option) Ink.Amber else Ink.TextMuted,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    StepLabel(tr(StrEx.skill))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(SKILLS) { option ->
            Pill(
                codeLabel(option),
                Modifier.clickable { onSkill(option) },
                background = if (skill == option) Ink.AmberSoft else Ink.Surface,
                foreground = if (skill == option) Ink.Amber else Ink.TextMuted,
            )
        }
    }
}

@Composable
private fun ContentStep(
    kind: String,
    prompt: String,
    onPrompt: (String) -> Unit,
    options: String,
    onOptions: (String) -> Unit,
    correct: String,
    onCorrect: (String) -> Unit,
    acceptedText: String,
    onAcceptedText: (String) -> Unit,
) {
    InkField(prompt, onPrompt, tr(StrEx.questionField), singleLine = false, minLines = 2)
    Spacer(Modifier.height(12.dp))
    when (kind) {
        "TEXT" -> InkField(acceptedText, onAcceptedText, tr(StrEx.correctText))
        else -> {
            InkField(options, onOptions, tr(StrEx.optionsField))
            Spacer(Modifier.height(8.dp))
            InkField(
                correct,
                onCorrect,
                if (kind == "MULTI") tr(StrEx.correctAnswers) else tr(StrEx.correctOption),
                keyboardType = KeyboardType.Number,
            )
        }
    }
}

@Composable
private fun MediaStep(
    imageUrl: String,
    onImage: (String) -> Unit,
    audioUrl: String,
    onAudio: (String) -> Unit,
    videoUrl: String,
    onVideo: (String) -> Unit,
    explanation: String,
    onExplanation: (String) -> Unit,
    picker: com.rork.pro.ui.components.FilePicker,
) {
    StepLabel(tr(StrEx.media))
    MediaSlot(tr(StrEx.image), "image/*", imageUrl, "questions", picker, onUrl = onImage)
    Spacer(Modifier.height(8.dp))
    MediaSlot(tr(StrEx.audio), "audio/*", audioUrl, "questions", picker, onUrl = onAudio)
    Spacer(Modifier.height(8.dp))
    MediaSlot(tr(StrEx.video), "video/*", videoUrl, "questions", picker, onUrl = onVideo)
    Spacer(Modifier.height(12.dp))
    InkField(explanation, onExplanation, tr(StrEx.explanation))
}

@Composable
private fun PreviewStep(
    wizardKey: String,
    kind: String,
    prompt: String,
    optionList: List<String>,
    correctIndexes: List<Int>,
    accepted: List<String>,
    teacherView: Boolean,
    onToggleView: (Boolean) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(
            tr(StrEx.studentView),
            Modifier.weight(1f).clickable { onToggleView(false) },
            background = if (!teacherView) Ink.AmberSoft else Ink.Surface,
            foreground = if (!teacherView) Ink.Amber else Ink.TextMuted,
        )
        Pill(
            tr(StrEx.teacherView),
            Modifier.weight(1f).clickable { onToggleView(true) },
            background = if (teacherView) Ink.AmberSoft else Ink.Surface,
            foreground = if (teacherView) Ink.Amber else Ink.TextMuted,
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(tr(StrEx.previewHint), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(12.dp))

    InkCard {
        Text(
            prompt.ifBlank { tr(StrEx.questionField) },
            color = Ink.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(14.dp))
        if (kind == "TEXT") {
            InkCard(color = Ink.SurfaceHigh, contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                Text(tr(StrEx.previewTypedAnswer), color = Ink.TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
            if (teacherView && accepted.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Divider()
                Spacer(Modifier.height(10.dp))
                Text(tr(StrEx.correctText), color = Ink.TextSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(4.dp))
                Text(accepted.joinToString(" · "), color = Ink.Teal, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        } else {
            optionList.forEachIndexed { index, text ->
                val isCorrect = index in correctIndexes
                PreviewOptionRow(index + 1, text, revealCorrect = teacherView && isCorrect)
                if (index != optionList.lastIndex) Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/** Non-interactive stand-in for the student's answer row, used only to preview a question. */
@Composable
private fun PreviewOptionRow(number: Int, text: String, revealCorrect: Boolean) {
    val accent = if (revealCorrect) Ink.Teal else Ink.TextMuted
    Row(
        Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(if (revealCorrect) Ink.TealSoft else Ink.SurfaceHigh)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (revealCorrect) Ink.Teal else Ink.Surface),
            contentAlignment = Alignment.Center,
        ) {
            if (revealCorrect) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Ink.OnAmber, modifier = Modifier.size(15.dp))
            } else {
                Text(number.toString(), color = Ink.TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
        Text(
            text,
            color = if (revealCorrect) Ink.Teal else Ink.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (revealCorrect) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (revealCorrect) {
            Text(tr(StrEx.correctAnswerLabel), color = Ink.Teal, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}
