package com.arena.arabicdub.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException

/**
 * حفظ الملف النهائي في مكتبة المستخدم (Movies/ArabicDub)
 * مع دعم MediaStore على Android 10+ والممسوح الكلاسيكي قبلها.
 */
object OutputSaver {

    fun save(context: Context, file: File, title: String, isVideo: Boolean): File {
        val ext = if (isVideo) "mp4" else "mp3"
        val name = "${sanitize(title)} - dub-ar.$ext"

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val collection =
                if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "audio/mpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/ArabicDub")
            }
            val uri = context.contentResolver.insert(collection, values)
                ?: throw IOException("تعذر إنشاء الملف في مكتبة الفيديو")
            val out = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("تعذر الكتابة في الملف")
            out.use { stream -> file.inputStream().use { it.copyTo(stream) } }
            // نسخة مرجعية من المسار (للعرض فقط — المشاركة تستخدم URI من MediaStore)
            File(Environment.getExternalStorageDirectory(), "Movies/ArabicDub/$name")
        } else {
            @Suppress("DEPRECATION")
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "ArabicDub",
            ).apply { mkdirs() }
            val target = File(dir, name)
            file.copyTo(target, overwrite = true)
            target
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(80).ifBlank { "video" }
}
