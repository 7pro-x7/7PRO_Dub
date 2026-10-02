package com.rork.pro.ui.screens.courses

/**
 * One-shot hand-off from the Home search button to the Courses screen: Home raises it, the
 * Courses screen reads it once and puts the cursor in its search box.
 */
object CourseSearchLaunch {
    @Volatile private var pending = false

    fun request() { pending = true }

    fun consume(): Boolean = pending.also { pending = false }
}
