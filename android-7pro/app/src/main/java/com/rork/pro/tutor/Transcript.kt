package com.rork.pro.tutor

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.activity.result.contract.ActivityResultContract
import java.io.ByteArrayOutputStream

enum class LineRole { TUTOR, STUDENT }

/**
 * One line of the call as it happened. [arabic] is the on-screen Arabic translation of a tutor line;
 * [corrected] is the student's sentence written correctly, when the tutor fixed something.
 */
data class TranscriptLine(
    val role: LineRole,
    val text: String,
    val arabic: String = "",
    val corrected: String = "",
    val atSec: Int = 0,
)

/** Everything the exported file needs, already in the app's current language. */
data class TranscriptDoc(
    val title: String,
    val subtitle: String,
    val tutorLabel: String,
    val youLabel: String,
    val lines: List<TranscriptLine>,
    val correctionsTitle: String,
    val corrections: List<TutorCorrection>,
    val newWordsTitle: String,
    val newWords: List<String>,
    val footer: String,
)

/** "Save as…" picker for a new file: input = (mime type, suggested file name); output = where the user chose, or null. */
class CreateExportDocument : ActivityResultContract<Pair<String, String>, Uri?>() {
    override fun createIntent(context: Context, input: Pair<String, String>): Intent =
        Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(input.first)
            .putExtra(Intent.EXTRA_TITLE, input.second)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Turns a finished call into copyable text, a .txt file or a .pdf file. Nothing here touches the network. */
object TranscriptExport {

    private fun clock(sec: Int) = "%02d:%02d".format(sec / 60, sec % 60)

    fun plainText(doc: TranscriptDoc): String = buildString {
        appendLine(doc.title)
        appendLine(doc.subtitle)
        appendLine("=".repeat(40))
        appendLine()
        for (line in doc.lines) {
            val who = if (line.role == LineRole.TUTOR) doc.tutorLabel else doc.youLabel
            appendLine("[${clock(line.atSec)}] $who: ${line.text}")
            if (line.arabic.isNotBlank()) appendLine("        ${line.arabic}")
            if (line.corrected.isNotBlank()) appendLine("        ✓ ${line.corrected}")
            appendLine()
        }
        if (doc.corrections.isNotEmpty()) {
            appendLine(doc.correctionsTitle)
            appendLine("-".repeat(40))
            for (c in doc.corrections) {
                appendLine("${c.wrong}  →  ${c.right}")
                if (c.explainAr.isNotBlank()) appendLine("   ${c.explainAr}")
            }
            appendLine()
        }
        if (doc.newWords.isNotEmpty()) {
            appendLine(doc.newWordsTitle)
            appendLine("-".repeat(40))
            appendLine(doc.newWords.joinToString(", "))
            appendLine()
        }
        appendLine(doc.footer)
    }

    fun write(context: Context, uri: Uri, bytes: ByteArray) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("cannot open $uri")
        out.use { it.write(bytes) }
    }

    /**
     * A4 PDF drawn with Android's own text engine, so Arabic is shaped and laid out right-to-left
     * correctly and English stays left-to-right, even inside one line. Long messages continue on the next page.
     */
    fun pdfBytes(doc: TranscriptDoc): ByteArray {
        val pageW = 595
        val pageH = 842
        val margin = 42f
        val contentW = (pageW - 2 * margin).toInt()
        val bottom = pageH - margin - 14f

        fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            this.color = color
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val titleP = paint(19f, Color.rgb(17, 17, 17), bold = true)
        val metaP = paint(10f, Color.rgb(110, 110, 110))
        val tutorLabelP = paint(9f, Color.rgb(15, 118, 110), bold = true)
        val youLabelP = paint(9f, Color.rgb(154, 52, 18), bold = true)
        val bodyP = paint(11.5f, Color.rgb(26, 26, 26))
        val arabicP = paint(11f, Color.rgb(85, 85, 85))
        val correctedP = paint(11f, Color.rgb(15, 122, 90))
        val headingP = paint(13f, Color.rgb(17, 17, 17), bold = true)
        val footerP = paint(8.5f, Color.rgb(140, 140, 140)).apply { textAlign = Paint.Align.CENTER }

        fun layout(text: String, p: TextPaint, width: Int, rtl: Boolean = false): StaticLayout =
            StaticLayout.Builder.obtain(text, 0, text.length, p, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
                .setLineSpacing(2.5f, 1f)
                .setIncludePad(false)
                .build()

        val pdf = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        lateinit var canvas: Canvas
        var y = margin

        fun finishPage() {
            val p = page ?: return
            canvas.drawText("${doc.footer}  ·  $pageNo", pageW / 2f, pageH - 22f, footerP)
            pdf.finishPage(p)
            page = null
        }
        fun newPage() {
            finishPage()
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNo).create())
            canvas = page!!.canvas
            y = margin
        }

        /** Draws [l] at ([x], current y), continuing on new pages when it does not fit. */
        fun paragraph(l: StaticLayout, x: Float) {
            var start = 0
            while (start < l.lineCount) {
                val avail = bottom - y
                var end = start
                while (end < l.lineCount && l.getLineBottom(end) - l.getLineTop(start) <= avail) end++
                if (end == start) { newPage(); continue }
                val top = l.getLineTop(start).toFloat()
                val bot = l.getLineBottom(end - 1).toFloat()
                val save = canvas.save()
                canvas.translate(x, y - top)
                canvas.clipRect(0f, top, l.width.toFloat(), bot)
                l.draw(canvas)
                canvas.restoreToCount(save)
                y += bot - top
                start = end
                if (start < l.lineCount) newPage()
            }
        }
        fun gap(h: Float) { y += h }
        fun ensure(h: Float) { if (y + h > bottom) newPage() }

        newPage()
        paragraph(layout(doc.title, titleP, contentW), margin)
        gap(4f)
        paragraph(layout(doc.subtitle, metaP, contentW), margin)
        gap(16f)

        val indent = 14f
        for (line in doc.lines) {
            val tutor = line.role == LineRole.TUTOR
            ensure(70f) // keep a name and the start of its message together
            val who = if (tutor) doc.tutorLabel else doc.youLabel
            paragraph(layout("$who  ·  ${clock(line.atSec)}", if (tutor) tutorLabelP else youLabelP, contentW), margin)
            gap(2f)
            paragraph(layout(line.text, bodyP, contentW - indent.toInt()), margin + indent)
            if (line.arabic.isNotBlank()) {
                gap(3f)
                paragraph(layout(line.arabic, arabicP, contentW - indent.toInt(), rtl = true), margin + indent)
            }
            if (line.corrected.isNotBlank()) {
                gap(3f)
                paragraph(layout("✓ ${line.corrected}", correctedP, contentW - indent.toInt()), margin + indent)
            }
            gap(12f)
        }

        if (doc.corrections.isNotEmpty()) {
            ensure(80f)
            gap(6f)
            paragraph(layout(doc.correctionsTitle, headingP, contentW), margin)
            gap(6f)
            for (c in doc.corrections) {
                ensure(40f)
                paragraph(layout("${c.wrong}  →  ${c.right}", bodyP, contentW - indent.toInt()), margin + indent)
                if (c.explainAr.isNotBlank()) paragraph(layout(c.explainAr, arabicP, contentW - indent.toInt(), rtl = true), margin + indent)
                gap(8f)
            }
        }
        if (doc.newWords.isNotEmpty()) {
            ensure(60f)
            gap(6f)
            paragraph(layout(doc.newWordsTitle, headingP, contentW), margin)
            gap(6f)
            paragraph(layout(doc.newWords.joinToString(",  "), bodyP, contentW - indent.toInt()), margin + indent)
        }

        finishPage()
        val out = ByteArrayOutputStream()
        pdf.writeTo(out)
        pdf.close()
        return out.toByteArray()
    }
}
