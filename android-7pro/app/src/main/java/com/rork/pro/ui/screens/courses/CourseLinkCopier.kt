package com.rork.pro.ui.screens.courses

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.rork.pro.data.CourseWeb
import com.rork.pro.ui.i18n.StrCourseLink
import com.rork.pro.ui.i18n.tr

/**
 * Copies the public courses link (or one course's link) to the clipboard.
 *
 * Only teachers, admins and the owner may copy these links; for any other user [allowed] is
 * false and the returned action does nothing (the buttons are hidden for them as well).
 * A null id copies the link to all courses.
 */
@Composable
internal fun rememberCourseLinkCopier(allowed: Boolean): (String?) -> Unit {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return remember(clipboard, context, allowed) {
        copy@{ courseId: String? ->
            if (!allowed) return@copy
            if (courseId.isNullOrBlank()) {
                clipboard.setText(AnnotatedString(CourseWeb.catalogUrl()))
                Toast.makeText(context, tr(StrCourseLink.copiedAll), Toast.LENGTH_SHORT).show()
            } else {
                clipboard.setText(AnnotatedString(CourseWeb.courseUrl(courseId)))
                Toast.makeText(context, tr(StrCourseLink.copiedOne), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
