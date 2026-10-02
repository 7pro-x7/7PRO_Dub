package com.rork.pro.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Turns a reading lesson into a file the student keeps: a PDF (text and pictures) or a TXT
 * (text only). Everything is generated on the phone — no server, no cost — straight from the
 * lesson's blocks, so a teacher's edit is in the next download automatically.
 *
 * Android 10+ saves into the public Downloads folder; older phones get the share sheet instead
 * (writing to Downloads there would need a storage permission the app doesn't ask for).
 */
object LessonExport {

    sealed interface Saved {
        /** Written to Downloads; [uri] opens it. */
        data class InDownloads(val uri: Uri, val mime: String) : Saved

        /** Older Android: handed to the share sheet instead. */
        data object Shared : Saved
    }

    private const val PAGE_WIDTH = 595 // A4 in PostScript points
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 44

    private val http by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun saveTxt(context: Context, lesson: Lesson): Saved = withContext(Dispatchers.IO) {
        val text = lessonText(lesson)
        val bytes = ("\uFEFF" + text).toByteArray(Charsets.UTF_8) // BOM so Windows Notepad reads Arabic
        save(context, fileName(lesson, "txt"), "text/plain", bytes)
    }

    suspend fun savePdf(context: Context, lesson: Lesson): Saved = withContext(Dispatchers.IO) {
        val bytes = buildPdf(lesson)
        save(context, fileName(lesson, "pdf"), "application/pdf", bytes)
    }

    /** Opens a saved file in whatever app the phone uses for that type. */
    fun open(context: Context, saved: Saved.InDownloads) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(saved.uri, saved.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    fun lessonText(lesson: Lesson): String {
        val blocks = lesson.blocks
        return if (!blocks.isNullOrEmpty()) {
            blocks.toPlainText(lesson.displayTitle)
        } else {
            listOf(lesson.displayTitle, lesson.displayContent).filter { it.isNotBlank() }.joinToString("\n\n")
        }
    }

    // ------------------------------------------------------------------ PDF

    private fun buildPdf(lesson: Lesson): ByteArray {
        val doc = PdfDocument()
        val contentWidth = PAGE_WIDTH - MARGIN * 2
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNumber += 1
            val info = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = doc.startPage(info).also { canvas = it.canvas }
            y = MARGIN.toFloat()
        }

        val bottom = (PAGE_HEIGHT - MARGIN).toFloat()

        /** Draws a text layout line by line, breaking onto new pages where it doesn't fit. */
        fun drawLayout(layout: StaticLayout, gapAfter: Float) {
            var line = 0
            while (line < layout.lineCount) {
                if (y + (layout.getLineBottom(line) - layout.getLineTop(line)) > bottom) newPage()
                val first = line
                var last = line
                while (last + 1 < layout.lineCount &&
                    y + (layout.getLineBottom(last + 1) - layout.getLineTop(first)) <= bottom
                ) {
                    last += 1
                }
                val top = layout.getLineTop(first).toFloat()
                val height = (layout.getLineBottom(last) - layout.getLineTop(first)).toFloat()
                canvas?.let { c ->
                    c.save()
                    c.translate(MARGIN.toFloat(), y - top)
                    c.clipRect(0f, top, contentWidth.toFloat(), top + height)
                    layout.draw(c)
                    c.restore()
                }
                y += height
                line = last + 1
            }
            y += gapAfter
        }

        fun paint(size: Float, bold: Boolean, color: Int = Color.rgb(24, 33, 58)) = TextPaint().apply {
            isAntiAlias = true
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

        fun layout(text: String, paint: TextPaint): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                // Each paragraph follows its own first strong letter: Arabic lines right-aligned,
                // English lines left-aligned, mixed lines shaped correctly.
                .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(0f, 1.25f)
                .setIncludePad(true)
                .build()

        val titlePaint = paint(22f, bold = true)
        val headingPaint = paint(16f, bold = true)
        val bodyPaint = paint(12f, bold = false)
        val captionPaint = paint(10f, bold = false, color = Color.rgb(91, 102, 128))

        newPage()
        drawLayout(layout(lesson.displayTitle, titlePaint), 16f)

        val blocks = lesson.blocks
        if (blocks.isNullOrEmpty()) {
            if (lesson.displayContent.isNotBlank()) drawLayout(layout(lesson.displayContent, bodyPaint), 10f)
        } else {
            for (block in blocks) {
                when (block.type) {
                    LessonBlock.HEADING -> {
                        if (block.text.isNotBlank()) {
                            y += 4f
                            drawLayout(layout(block.text, headingPaint), 8f)
                        }
                    }
                    LessonBlock.LIST -> {
                        for (item in block.items) {
                            if (item.isNotBlank()) drawLayout(layout("•  $item", bodyPaint), 4f)
                        }
                        y += 6f
                    }
                    LessonBlock.IMAGE -> {
                        val bitmap = block.url?.let { loadBitmap(it, contentWidth) }
                        if (bitmap != null) {
                            val scale = minOf(1f, contentWidth.toFloat() / bitmap.width)
                            var w = bitmap.width * scale
                            var h = bitmap.height * scale
                            val maxH = (bottom - MARGIN) * 0.6f
                            if (h > maxH) {
                                w *= maxH / h
                                h = maxH
                            }
                            if (y + h > bottom) newPage()
                            val left = MARGIN + (contentWidth - w) / 2f
                            canvas?.drawBitmap(
                                bitmap,
                                null,
                                Rect(left.toInt(), y.toInt(), (left + w).toInt(), (y + h).toInt()),
                                null,
                            )
                            y += h + 6f
                            bitmap.recycle()
                        }
                        val caption = block.caption.orEmpty()
                        if (caption.isNotBlank()) {
                            drawLayout(layout(caption, captionPaint), 10f)
                        } else {
                            y += 6f
                        }
                    }
                    else -> {
                        if (block.text.isNotBlank()) drawLayout(layout(block.text, bodyPaint), 10f)
                    }
                }
            }
        }

        page?.let { doc.finishPage(it) }
        val out = java.io.ByteArrayOutputStream()
        doc.writeTo(out)
        doc.close()
        return out.toByteArray()
    }

    /** Downloads a lesson picture, decoded at about the size it will be printed. */
    private fun loadBitmap(url: String, targetWidth: Int): Bitmap? = runCatching {
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@runCatching null
            val bytes = response.body?.bytes() ?: return@runCatching null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            // Decode at roughly 2× print width: sharp in the PDF without holding a huge bitmap.
            while (bounds.outWidth / (sample * 2) >= targetWidth * 2) sample *= 2
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()

    // ----------------------------------------------------------------- saving

    private fun fileName(lesson: Lesson, ext: String): String {
        val base = lesson.displayTitle
            .replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(60)
            .ifBlank { "lesson" }
        return "7PRO - $base.$ext"
    }

    private suspend fun save(context: Context, name: String, mime: String, bytes: ByteArray): Saved {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/7PRO")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("DOWNLOAD_FAILED")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("DOWNLOAD_FAILED")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return Saved.InDownloads(uri, mime)
        }

        // Android 7–9: write to the app's cache and offer the share sheet.
        val dir = File(context.cacheDir, "lessons").apply { mkdirs() }
        val file = File(dir, name)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val share = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(share, name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        return Saved.Shared
    }
}
