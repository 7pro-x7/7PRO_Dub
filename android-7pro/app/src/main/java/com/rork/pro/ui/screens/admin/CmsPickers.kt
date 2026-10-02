package com.rork.pro.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rork.pro.data.GrantCourse
import com.rork.pro.data.MediaRepository
import com.rork.pro.data.toAppError
import com.rork.pro.ui.components.CoverImage
import com.rork.pro.ui.components.SecondaryAction
import com.rork.pro.ui.components.rememberFilePicker
import com.rork.pro.ui.i18n.StrAdminX
import com.rork.pro.ui.i18n.Tr
import com.rork.pro.ui.i18n.tr
import com.rork.pro.ui.screens.auth.InkField
import com.rork.pro.ui.theme.Ink
import kotlinx.coroutines.launch

// Pickers for the home-content screen. The owner used to type raw keys ("classroom", "course:<id>",
// "url:<link>"), paste an image link and type an emoji. Everything here writes the same strings the
// Home screen already understands, so nothing downstream changes.

/** One place a banner button or a home shortcut can lead to. [key] is the exact target string stored. */
internal class HomeDest(val key: String, val label: Tr, val icon: ImageVector)

internal val HOME_DESTS = listOf(
    HomeDest("courses", StrAdminX.cmsDestCourses, Icons.Default.LibraryBooks),
    HomeDest("classroom", StrAdminX.cmsDestClassroom, Icons.Default.Videocam),
    HomeDest("tutor", StrAdminX.cmsDestTutor, Icons.Default.Translate),
    HomeDest("dubbing", StrAdminX.cmsDestDubbing, Icons.Default.Mic),
    HomeDest("test", StrAdminX.cmsDestTest, Icons.Default.Quiz),
    HomeDest("profile", StrAdminX.cmsDestProfile, Icons.Default.Person),
    HomeDest("orders", StrAdminX.cmsDestOrders, Icons.AutoMirrored.Filled.ReceiptLong),
    HomeDest("certificates", StrAdminX.cmsDestCertificates, Icons.Default.WorkspacePremium),
    HomeDest("support", StrAdminX.cmsDestSupport, Icons.Default.SupportAgent),
)

private const val KEY_NONE = ""
private const val KEY_COURSE = "course"
private const val KEY_URL = "url"

/** Which option a stored target string corresponds to. Unknown free text behaves like "courses" on Home. */
private fun keyOf(target: String): String = when {
    target.isBlank() -> KEY_NONE
    target.startsWith("course:") -> KEY_COURSE
    target.startsWith("url:") -> KEY_URL
    HOME_DESTS.any { it.key == target } -> target
    else -> "courses"
}

/** Human wording for a stored target — what the owner sees instead of the raw key. */
internal fun destinationLabel(target: String?, courses: List<GrantCourse>): String {
    val t = target.orEmpty()
    return when (keyOf(t)) {
        KEY_NONE -> tr(StrAdminX.cmsDestNone)
        KEY_COURSE -> courses.firstOrNull { it.id == t.removePrefix("course:") }?.displayTitle ?: tr(StrAdminX.cmsDestCourse)
        KEY_URL -> t.removePrefix("url:")
        else -> HOME_DESTS.firstOrNull { it.key == keyOf(t) }?.let { tr(it.label) } ?: tr(StrAdminX.cmsDestCourses)
    }
}

/** False while a course or link was chosen as the kind but not filled in yet. */
internal fun destinationValid(target: String, allowNone: Boolean): Boolean = when (keyOf(target)) {
    KEY_NONE -> allowNone
    KEY_COURSE -> target.removePrefix("course:").isNotBlank()
    KEY_URL -> target.removePrefix("url:").trim().startsWith("http", ignoreCase = true)
    else -> true
}

/**
 * "Where does it lead?" as a list of named places instead of a text box. A course opens a list of
 * courses to choose from, an external link a single field; everything else is one tap.
 */
@Composable
internal fun DestinationField(
    value: String,
    onChange: (String) -> Unit,
    allowNone: Boolean,
    courses: List<GrantCourse>,
) {
    var open by remember { mutableStateOf(false) }
    val key = keyOf(value)
    val shape = RoundedCornerShape(16.dp)

    Text(tr(StrAdminX.cmsDestination), color = Ink.TextSecondary, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Surface)
            .border(1.dp, if (value.isBlank() && !allowNone) Ink.Hairline else Ink.Amber, shape)
            .clickable { open = !open }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = if (value.isBlank() && !allowNone) tr(StrAdminX.cmsNeedDestination) else destinationLabel(value, courses)
        Text(
            label,
            Modifier.weight(1f),
            color = if (value.isBlank() && !allowNone) Ink.TextMuted else Ink.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
        )
        Icon(Icons.Default.ArrowDropDown, null, tint = Ink.TextMuted)
    }

    if (open) {
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.SurfaceHigh)) {
            if (allowNone) {
                DestRow(Icons.Default.Block, tr(StrAdminX.cmsDestNone), key == KEY_NONE) { onChange(""); open = false }
            }
            HOME_DESTS.forEach { d ->
                DestRow(d.icon, tr(d.label), key == d.key) { onChange(d.key); open = false }
            }
            DestRow(Icons.Default.School, tr(StrAdminX.cmsDestCourse), key == KEY_COURSE) {
                if (key != KEY_COURSE) onChange("course:")
                open = false
            }
            DestRow(Icons.Default.Link, tr(StrAdminX.cmsDestUrl), key == KEY_URL) {
                if (key != KEY_URL) onChange("url:")
                open = false
            }
        }
    }

    when (key) {
        KEY_COURSE -> {
            Spacer(Modifier.height(8.dp))
            CoursePickList(selectedId = value.removePrefix("course:"), courses = courses) { onChange("course:$it") }
        }
        KEY_URL -> {
            Spacer(Modifier.height(8.dp))
            InkField(value.removePrefix("url:"), { onChange("url:" + it.trim()) }, tr(StrAdminX.cmsLinkHint))
        }
        else -> Unit
    }
}

@Composable
private fun DestRow(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) Ink.Teal else Ink.TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium)
        if (selected) Icon(Icons.Default.Check, null, tint = Ink.Teal, modifier = Modifier.size(18.dp))
    }
}

/** The chosen course as a summary line; tapping it (or having none yet) shows the searchable list. */
@Composable
private fun CoursePickList(selectedId: String, courses: List<GrantCourse>, onPick: (String) -> Unit) {
    var showList by remember(selectedId.isBlank()) { mutableStateOf(selectedId.isBlank()) }
    var q by remember { mutableStateOf("") }
    val chosen = courses.firstOrNull { it.id == selectedId }

    if (!showList && selectedId.isNotBlank()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Ink.TealSoft)
                .clickable { showList = true }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.School, null, tint = Ink.Teal, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                chosen?.displayTitle ?: tr(StrAdminX.cmsDestCourse),
                Modifier.weight(1f),
                color = Ink.Teal,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
            )
        }
        return
    }

    Text(tr(StrAdminX.cmsPickCourse), color = Ink.TextSecondary, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    if (courses.size > 5) {
        AdminSearchField(q, { q = it }, tr(StrAdminX.couponSearchCourse))
        Spacer(Modifier.height(6.dp))
    }
    val shown = remember(courses, q) {
        courses.filter { SearchMatch.matches(q, it.title, it.titleAr, it.titleEn) }
            .sortedByDescending { SearchMatch.rank(q, it.displayTitle) }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Ink.SurfaceHigh)
            .verticalScroll(rememberScrollState()),
    ) {
        if (shown.isEmpty()) {
            Text(tr(StrAdminX.couponNoCourseMatch), Modifier.padding(14.dp), color = Ink.TextMuted, style = MaterialTheme.typography.bodySmall)
        }
        shown.forEach { c ->
            DestRow(Icons.Default.School, c.displayTitle, c.id == selectedId) {
                onPick(c.id); showList = false; q = ""
            }
        }
    }
}

private val ICON_CHOICES = listOf(
    "⭐", "📚", "🎓", "🎯", "💬", "🗣️", "🎧", "🏆", "🔥", "💡", "📝", "🧠",
    "🎬", "📞", "👤", "🛒", "📜", "🆘", "🤖", "🌍", "✨", "❤️", "🎁", "🕒",
)

/** Pick an icon by tapping it, instead of typing an emoji into a four-character box. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IconPicker(value: String, onChange: (String) -> Unit) {
    Text(tr(StrAdminX.cmsPickIcon), color = Ink.TextSecondary, style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // An icon typed in earlier that is not in the grid still shows, as the selected one.
        val all = if (value.isNotBlank() && value !in ICON_CHOICES) listOf(value) + ICON_CHOICES else ICON_CHOICES
        all.forEach { e ->
            val selected = e == value
            Box(
                Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) Ink.AmberSoft else Ink.SurfaceHigh)
                    .border(if (selected) 2.dp else 1.dp, if (selected) Ink.Amber else Ink.Hairline, RoundedCornerShape(14.dp))
                    .clickable { onChange(e) },
                contentAlignment = Alignment.Center,
            ) { Text(e, fontSize = 22.sp, textAlign = TextAlign.Center) }
        }
    }
}

/** A wide image slot: tap to choose a picture from the phone, it is uploaded and previewed right there. */
@Composable
internal fun BannerImagePicker(url: String, onUrl: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberFilePicker()
    var uploading by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(16.dp)

    fun choose() {
        failure = null
        picker.pick("image/*") { uri ->
            scope.launch {
                uploading = true
                runCatching {
                    val file = MediaRepository.read(context, uri)
                    MediaRepository.upload(file, "cms")
                }.onSuccess { onUrl(it) }
                    .onFailure { failure = it.toAppError().message }
                uploading = false
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(shape)
                .background(Ink.SurfaceHigh)
                .border(1.dp, Ink.Hairline, shape)
                .clickable(enabled = !uploading) { choose() },
            contentAlignment = Alignment.Center,
        ) {
            if (url.isNotBlank()) {
                CoverImage(url, Modifier.fillMaxSize())
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddPhotoAlternate, null, tint = Ink.TextMuted, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(6.dp))
                    Text(tr(StrAdminX.cmsPickImage), color = Ink.TextMuted, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (uploading) {
                Box(Modifier.fillMaxSize().background(Ink.Surface.copy(alpha = 0.75f)), contentAlignment = Alignment.Center) {
                    Text(tr(StrAdminX.cmsUploading), color = Ink.TextPrimary, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (url.isNotBlank() && !uploading) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryAction(tr(StrAdminX.cmsChangeImage)) { choose() }
                SecondaryAction(tr(StrAdminX.cmsRemoveImage), tint = Ink.Coral) { onUrl("") }
            }
        }
        failure?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = Ink.Coral, style = MaterialTheme.typography.bodySmall)
        }
    }
}
