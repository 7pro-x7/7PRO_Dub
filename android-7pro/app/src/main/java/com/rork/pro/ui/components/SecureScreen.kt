package com.rork.pro.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/** Walks the context chain to the hosting activity, which owns the window this file toggles. */
private fun Context.findHostActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

// FLAG_SECURE belongs to a Window, not to the app or the Composable tree, and more than one
// caller can legitimately want it on the same window at once — e.g. the lesson card's inline
// player and, moments later, its fullscreen Dialog (a second, separate Window). A per-window
// reference count means the flag goes on when the first caller asks and only comes off once
// every caller for that window has left, so two overlapping callers can never clear a flag the
// other still needs.
private val secureRequestCounts = mutableMapOf<Window, Int>()

private fun Window.requestSecure() {
    val count = (secureRequestCounts[this] ?: 0) + 1
    secureRequestCounts[this] = count
    if (count == 1) addFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

private fun Window.releaseSecure() {
    val count = (secureRequestCounts[this] ?: 1) - 1
    if (count <= 0) {
        secureRequestCounts.remove(this)
        clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    } else {
        secureRequestCounts[this] = count
    }
}

/**
 * Blocks screenshots, screen recording, screen mirroring/casting, and the recents-list
 * thumbnail for whichever window is currently hosting this composable — for as long as it
 * stays in composition. Drop this into any composable that shows content that must not be
 * captured off-device, such as course video.
 *
 * Resolves the *actual* window it's drawn into, not always the Activity's: content shown in a
 * Compose [androidx.compose.ui.window.Dialog] lives in its own separate [Window], which needs
 * FLAG_SECURE set on it independently of the main activity window. Calling this once from
 * inline content and again from fullscreen dialog content is safe — see the reference count
 * above.
 */
@Composable
fun SecureScreen() {
    val view = LocalView.current
    val window = (view.parent as? DialogWindowProvider)?.window
        ?: view.context.findHostActivity()?.window

    DisposableEffect(window) {
        if (window == null) return@DisposableEffect onDispose {}
        window.requestSecure()
        onDispose { window.releaseSecure() }
    }
}
