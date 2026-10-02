package com.rork.pro.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Which palette the app-wide [Ink] / [HomeInk] tokens resolve to.
 *
 * [sky] = the sky-blue / navy redesign ([HomeSky]). It is now on for every screen in the app —
 * learner screens, the owner / admin console, the teacher studio and the course studio — so the
 * whole product reads as one design. The switch is kept (always `true` from AppNavigation) so the
 * previous warm palette can still be restored by setting it to `false` there.
 */
object AppPalette {
    var sky: Boolean by mutableStateOf(true)
}

/**
 * Palette for the Home tab only — the sky-blue (light) / deep-navy (dark) redesign.
 *
 * Every value was sampled from the approved mockups. It is deliberately separate from [HomeInk]
 * and [Ink]: those two drive every other screen in the app, so touching them would repaint the
 * whole app instead of Home. Like them, each value re-reads [AppAppearance.dark], so switching
 * appearance repaints Home instantly.
 */
object HomeSky {
    val dark: Boolean get() = AppAppearance.dark

    // ---------------------------------------------------------------- page
    val BgTop: Color get() = if (dark) Color(0xFF071A36) else Color(0xFFE5F2FE)
    val BgMid: Color get() = if (dark) Color(0xFF04172F) else Color(0xFFF3FAFE)
    val BgBottom: Color get() = if (dark) Color(0xFF031128) else Color(0xFFE6F2FB)
    val Blob: Color get() = if (dark) Color(0x1A2A5CA8) else Color(0x80D3E8FA)

    fun pageBrush(): Brush = Brush.verticalGradient(0f to BgTop, 0.3f to BgMid, 1f to BgBottom)

    // ---------------------------------------------------------------- app-wide extras
    /** Raised fill for inputs, chips and secondary surfaces on other screens. */
    val CardHigh: Color get() = if (dark) Color(0xFF0F2747) else Color(0xFFEEF4FB)
    /** Hairline for dividers and outlined controls (light cards themselves use a shadow). */
    val Line: Color get() = if (dark) Color(0xFF213F6E) else Color(0xFFDFE8F3)
    val LineStrong: Color get() = if (dark) Color(0xFF2C4E82) else Color(0xFFC9D8EA)
    val TextMuted: Color get() = if (dark) Color(0xFF7F9CC9) else Color(0xFF8399B8)
    val BrandSoft: Color get() = if (dark) Color(0xFF4A2E1F) else Color(0xFFFDEBDD)

    // ---------------------------------------------------------------- text
    val Text: Color get() = if (dark) Color(0xFFFFFFFF) else Color(0xFF0B1B45)
    val TextSecondary: Color get() = if (dark) Color(0xFF9BBEF2) else Color(0xFF56739E)
    val Greeting: Color get() = if (dark) Color(0xFF99C0F8) else Color(0xFF4F719F)
    val Tagline: Color get() = if (dark) Color(0xFFB2D7F9) else Color(0xFF3D5F8F)

    // ---------------------------------------------------------------- brand orange
    val Brand: Color get() = if (dark) Color(0xFFFD8233) else Color(0xFFE8601A)
    val BrandDeep: Color get() = if (dark) Color(0xFFE96308) else Color(0xFFBC4C15)
    val OnBrand: Color get() = Color(0xFFFFFFFF)
    val LevelPillBg: Color get() = if (dark) Color(0x00000000) else Color(0xFFFFF4EA)
    val Badge: Color get() = if (dark) Color(0xFFF26A3A) else Color(0xFFEE4D2D)

    // ---------------------------------------------------------------- header buttons
    val IconBtnBg: Color get() = if (dark) Color(0xFF0A2850) else Color(0xFFFFFFFF)
    val IconBtnBorder: Color get() = if (dark) Color(0xFF244C82) else Color(0x00000000)
    val IconBtnFg: Color get() = if (dark) Color(0xFFFFFFFF) else Color(0xFF0B1B45)

    // ---------------------------------------------------------------- AI tutor bar
    val AiStart: Color get() = if (dark) Color(0xFF024652) else Color(0xFF0E6270)
    val AiEnd: Color get() = if (dark) Color(0xFF023A45) else Color(0xFF0A5563)
    val AiBorder: Color get() = if (dark) Color(0xFF2C8C8A) else Color(0x00000000)
    val AiIconBg: Color get() = if (dark) Color(0xFF80F2D3) else Color(0xFFD5F2EB)
    val AiIconFg: Color get() = if (dark) Color(0xFF023F44) else Color(0xFF04525F)
    val AiButtonBg: Color get() = if (dark) Color(0xFFE2FBFB) else Color(0xFFFFFFFF)
    val AiButtonFg: Color get() = if (dark) Color(0xFF023C3F) else Color(0xFF003C4E)
    val OnAi: Color get() = Color(0xFFFFFFFF)

    // ---------------------------------------------------------------- cards
    val Card: Color get() = if (dark) Color(0xFF0A1E3A) else Color(0xFFFFFFFF)
    val CardBorder: Color get() = if (dark) Color(0xFF213F6E) else Color(0x00000000)
    val Shadow: Color get() = if (dark) Color(0x00000000) else Color(0x332A5C9A)
    val Track: Color get() = if (dark) Color(0xFF1C3660) else Color(0xFFDCE6F2)
    val RingTrack: Color get() = if (dark) Color(0xFF223C64) else Color(0xFFE1E9F4)
    val NewPillBg: Color get() = if (dark) Color(0xFF284979) else Color(0xFFE6F0FC)
    val NewPillFg: Color get() = if (dark) Color(0xFFD6E5FB) else Color(0xFF5876A2)
    val FreeBg: Color get() = if (dark) Color(0xFF0F4A3E) else Color(0xFFDDF5EC)
    val FreeFg: Color get() = if (dark) Color(0xFF7FE6C6) else Color(0xFF0F7A5A)

    // ---------------------------------------------------------------- category chips
    val ChipBg: Color get() = if (dark) Color(0xFF061E3E) else Color(0xFFF2EEEA)
    val ChipBorder: Color get() = if (dark) Color(0xFF234981) else Color(0xFFE6DFD8)
    val ChipText: Color get() = if (dark) Color(0xFFE6F2FD) else Color(0xFF2F2019)
    val ChipSelStart: Color get() = if (dark) Color(0xFFFF8A1E) else Color(0xFFE9661F)
    val ChipSelEnd: Color get() = if (dark) Color(0xFFF87103) else Color(0xFFD4511A)

    // ---------------------------------------------------------------- bottom navigation
    val NavBg: Color get() = if (dark) Color(0xFF041A35) else Color(0xFFFFFFFF)
    val NavBorder: Color get() = if (dark) Color(0xFF22497F) else Color(0x00000000)
    val NavSelectedBg: Color get() = if (dark) Color(0xFF5A3724) else Color(0xFFFDEBDD)
    val NavSelected: Color get() = Brand
    val NavIdle: Color get() = if (dark) Color(0xFFA0BFEF) else Color(0xFF5C779E)

    // ---------------------------------------------------------------- the two side-by-side tiles
    /** One colour family for a half-width tile (teacher exercises, group subscription states). */
    class Tile(
        val top: Color,
        val bottom: Color,
        val border: Color,
        val iconStart: Color,
        val iconEnd: Color,
        val chevron: Color,
    )

    val TileBlue: Tile
        get() = if (dark) {
            Tile(Color(0xFF1B4384), Color(0xFF0B2048), Color(0xFF3C7FE0), Color(0xFF1A8AE0), Color(0xFF0060AE), Color(0xFFFFFFFF))
        } else {
            Tile(Color(0xFFEAF5FE), Color(0xFFD5EAFD), Color(0xFFB5D6F5), Color(0xFF3B8BCB), Color(0xFF1F74BA), Color(0xFF0A5A94))
        }

    val TileOrange: Tile
        get() = if (dark) {
            Tile(Color(0xFF3E2A27), Color(0xFF2A1F22), Color(0xFFE0702E), Color(0xFFFF8A1E), Color(0xFFE05A04), Color(0xFFFD8233))
        } else {
            Tile(Color(0xFFFEF2E6), Color(0xFFFCEBDD), Color(0xFFF6D7BC), Color(0xFFF0802F), Color(0xFFD8561A), Color(0xFFD95711))
        }

    val TileGreen: Tile
        get() = if (dark) {
            Tile(Color(0xFF173F35), Color(0xFF0C2622), Color(0xFF2FA37F), Color(0xFF2FC08F), Color(0xFF16876A), Color(0xFF7FE6C6))
        } else {
            Tile(Color(0xFFEAF8F1), Color(0xFFD7F0E3), Color(0xFFB4DFC8), Color(0xFF2E9A68), Color(0xFF1E7A4C), Color(0xFF1E7A4C))
        }

    val TileRed: Tile
        get() = if (dark) {
            Tile(Color(0xFF45232A), Color(0xFF2B171C), Color(0xFFE0566A), Color(0xFFF0657A), Color(0xFFC23A50), Color(0xFFF0B5BC))
        } else {
            Tile(Color(0xFFFDEEF0), Color(0xFFF9DEE2), Color(0xFFF1C2C9), Color(0xFFD0475B), Color(0xFFA62F42), Color(0xFF8E2430))
        }
}
