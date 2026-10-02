package com.rork.pro.classroom

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager

/**
 * The green frame around the whole phone screen while the teacher is sharing it — the same cue
 * Zoom draws. It is a click-through overlay window, so it stays on screen over every other app
 * the teacher opens to show the class, which an in-app border could not do. Needs the
 * "display over other apps" permission; without it the call screen draws its own green border
 * instead (see CallScreen), which only shows while 7PRO itself is on screen.
 */
object ScreenShareFrame {
    private var frame: View? = null

    fun show(context: Context) {
        val app = context.applicationContext
        if (frame != null || !Settings.canDrawOverlays(app)) return
        val wm = app.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val density = app.resources.displayMetrics.density
        val view = object : View(app) {
            private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = 0xFF3FAF8E.toInt()
                strokeWidth = 4f * density
            }

            override fun onDraw(canvas: Canvas) {
                val half = paint.strokeWidth / 2f
                val radius = 20f * density
                canvas.drawRoundRect(half, half, width - half, height - half, radius, radius, paint)
            }
        }
        @Suppress("DEPRECATION")
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        runCatching {
            wm.addView(view, params)
            frame = view
        }
    }

    fun hide(context: Context) {
        val view = frame ?: return
        frame = null
        val wm = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        runCatching { wm.removeView(view) }
    }
}
