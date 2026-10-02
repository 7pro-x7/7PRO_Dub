package com.rork.pro.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The previous Home palette (warm paper / charcoal), which [Ink] is also built from.
 *
 * While [AppPalette.sky] is on (every learner-facing screen) each surface, text and brand token
 * resolves to the [HomeSky] redesign instead; staff-only areas switch it off and get these
 * original values back.
 *
 * Home was redesigned with its own warm-paper light mode and a soft charcoal dark mode
 * (lighter than the app-wide near-black, to be easier on the eyes). It is kept separate from
 * [Ink] on purpose: [Ink] drives every other screen, and changing it here would repaint the
 * whole app. Like [Ink], every value re-reads [AppAppearance.dark], so switching appearance
 * repaints Home instantly.
 */
object HomeInk {
    private val dark: Boolean get() = AppAppearance.dark
    private val sky: Boolean get() = AppPalette.sky

    val Bg: Color get() = if (sky) HomeSky.BgMid else if (dark) Color(0xFF1E1D1C) else Color(0xFFF7F3EC)
    val Card: Color get() = if (sky) HomeSky.Card else if (dark) Color(0xFF2A2827) else Color(0xFFFFFFFF)
    val Chip: Color get() = if (sky) HomeSky.CardHigh else if (dark) Color(0xFF322F2D) else Color(0xFFEFE7DA)
    val Line: Color get() = if (sky) HomeSky.Line else if (dark) Color(0xFF3A3735) else Color(0xFFE7DDCF)

    val Text: Color get() = if (sky) HomeSky.Text else if (dark) Color(0xFFECE7E1) else Color(0xFF1F1812)
    val TextSecondary: Color get() = if (sky) HomeSky.TextSecondary else if (dark) Color(0xFFABA49C) else Color(0xFF6B5D50)

    /** Burnt orange (the "7" of the logo, progress, selected chip). */
    val Brand: Color get() = if (sky) HomeSky.Brand else if (dark) Color(0xFFE9A471) else Color(0xFFC2561A)
    val OnBrand: Color get() = if (sky) HomeSky.OnBrand else if (dark) Color(0xFF25190F) else Color(0xFFFFFFFF)

    /** Text on the inverted button that sits on a solid [Brand] card (the card's own on-colour is the button fill). */
    val BrandButtonText: Color get() = if (sky) HomeSky.BrandDeep else if (dark) Color(0xFFF3C9A6) else Color(0xFF9E4311)
    val BrandSoft: Color get() = if (sky) HomeSky.BrandSoft else if (dark) Color(0xFF3F3023) else Color(0xFFFBE9DB)

    /** The AI tutor card: the one deep-green solid surface on the screen. */
    val Ai: Color get() = if (sky) HomeSky.AiStart else if (dark) Color(0xFF244B45) else Color(0xFF0B4A42)
    val OnAi: Color get() = if (sky) HomeSky.OnAi else if (dark) Color(0xFFEEF9F6) else Color(0xFFFFFFFF)
    val AiAccent: Color get() = if (sky) HomeSky.AiIconBg else if (dark) Color(0xFF8ADBC3) else Color(0xFFBFEBDD)

    /** Teacher-exercises tile: a fresher emerald, distinct from the tutor's teal-green. */
    val Green: Color get() = if (dark) Color(0xFF7FD6A2) else Color(0xFF1E7A4C)
    val GreenBg: Color get() = if (dark) Color(0xFF243A2D) else Color(0xFFE4F3E8)
    val GreenLine: Color get() = if (dark) Color(0xFF365A44) else Color(0xFFB7DDC3)
    val GreenInk: Color get() = if (dark) Color(0xFFE0F4E8) else Color(0xFF0F3D26)

    val Danger: Color get() = if (dark) Color(0xFFF0B5BC) else Color(0xFF8E2430)
    val Sky: Color get() = if (dark) Color(0xFF8EC1FF) else Color(0xFF1868C7)

    /** Icon colour on a solid accent tile: the page colour itself, so it flips with the mode. */
    val OnAccent: Color get() = Bg
}
