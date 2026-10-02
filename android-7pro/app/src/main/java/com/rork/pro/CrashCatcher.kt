package com.rork.pro

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import com.rork.pro.ui.i18n.Tr
import com.rork.pro.ui.i18n.tr

/**
 * App-wide "why did it close?" net.
 *
 * When the app closes by itself there is no adb or Android Studio on the owner's phone, so the
 * reason was lost. This saves the crash (stack trace, phone, Android version, app build) the
 * moment it happens, then the next time the app opens it shows a short message with a
 * "Copy details" button the owner can paste straight back to the developer.
 *
 * It never swallows the crash — the original handler still runs so Android restarts cleanly.
 * The classroom call screen keeps its own handler, which chains into this one.
 */
object CrashCatcher {
    private const val PREFS = "app_crash_catcher"
    private const val KEY_TEXT = "text"
    private const val KEY_AT = "at"
    private const val SHOW_WINDOW_MS = 24 * 60 * 60_000L

    private val title = Tr("The app closed unexpectedly", "التطبيق قفل فجأة")
    private val body = Tr(
        "Sorry about that. Tap \"Copy details\" and send them to the developer so it can be fixed.",
        "آسفين على كده. دوس «نسخ التفاصيل» وابعتها للمطوّر عشان المشكلة تتصلّح.",
    )
    private val copy = Tr("Copy details", "نسخ التفاصيل")
    private val copied = Tr("Copied", "اتنسخ")
    private val close = Tr("Close", "إغلاق")

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val writer = java.io.StringWriter()
                error.printStackTrace(java.io.PrintWriter(writer))
                val header = buildString {
                    append("7PRO build ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
                    append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                    append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
                    append("Thread: ").append(thread.name).append('\n')
                    append("Time: ").append(java.time.Instant.now().toString()).append("\n\n")
                }
                // commit(), not apply(): the process is about to die, an async write may never land.
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_TEXT, (header + writer.toString()).take(60_000))
                    .putLong(KEY_AT, System.currentTimeMillis())
                    .commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /** Call from the first screen; shows the saved crash once, then forgets it. */
    fun showPendingIfAny(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val text = prefs.getString(KEY_TEXT, null) ?: return
        val at = prefs.getLong(KEY_AT, 0L)
        prefs.edit().remove(KEY_TEXT).remove(KEY_AT).apply()
        if (System.currentTimeMillis() - at > SHOW_WINDOW_MS) return

        runCatching {
            android.app.AlertDialog.Builder(activity)
                .setTitle(tr(title))
                .setMessage(tr(body))
                .setPositiveButton(tr(copy)) { _, _ ->
                    val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("7pro_crash", text))
                    android.widget.Toast.makeText(activity, tr(copied), android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(tr(close), null)
                .show()
        }
    }
}
