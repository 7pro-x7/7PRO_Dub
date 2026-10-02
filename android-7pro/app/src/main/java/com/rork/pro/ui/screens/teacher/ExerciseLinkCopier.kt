package com.rork.pro.ui.screens.teacher

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import com.rork.pro.data.ExerciseWeb
import com.rork.pro.ui.i18n.StrExLink
import com.rork.pro.ui.i18n.tr

/**
 * Copies a link to a teacher's exercises (or to one exercise) to the clipboard.
 *
 * Arguments are (teacherId, exerciseId?): a null exercise copies the teacher-wide link. Whether
 * someone may actually open it is the server's decision, not this button's.
 */
@Composable
internal fun rememberExerciseLinkCopier(): (String?, String?) -> Unit {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    return remember(clipboard, context) {
        { teacherId: String?, exerciseId: String? ->
            if (teacherId.isNullOrBlank()) {
                Toast.makeText(context, tr(StrExLink.noTeacher), Toast.LENGTH_SHORT).show()
            } else {
                val url = if (exerciseId.isNullOrBlank()) {
                    ExerciseWeb.teacherUrl(teacherId)
                } else {
                    ExerciseWeb.exerciseUrl(teacherId, exerciseId)
                }
                clipboard.setText(AnnotatedString(url))
                Toast.makeText(context, tr(StrExLink.copied), Toast.LENGTH_LONG).show()
            }
        }
    }
}
