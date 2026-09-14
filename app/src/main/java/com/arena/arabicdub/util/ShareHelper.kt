package com.arena.arabicdub.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File

/**
 * مشاركة الملف النهائي.
 * - Android 10+: يتم البحث عن URI في MediaStore (الملف محفوظ في المكتبة).
 * - قبله: FileProvider مع إذن قراءة مؤقت.
 */
object ShareHelper {

    fun share(context: Context, file: File, isVideo: Boolean) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findMediaStoreUri(context, file.name, isVideo) ?: fileUri(context, file)
        } else {
            fileUri(context, file)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/mp4" else "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الملف").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun fileUri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    private fun findMediaStoreUri(context: Context, displayName: String, isVideo: Boolean): Uri? {
        val collection =
            if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?",
            arrayOf(displayName),
            null,
        )?.use { c ->
            if (c.moveToFirst()) {
                return Uri.withAppendedPath(collection, c.getLong(0).toString())
            }
        }
        return null
    }
}
