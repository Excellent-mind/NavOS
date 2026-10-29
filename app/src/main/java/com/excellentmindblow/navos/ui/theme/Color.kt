package com.excellentmindblow.navos.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Colours taken from Malaysian road furniture: JKR sign panels, route shields, asphalt and paint. */
object Jkr {
    val Green = Color(0xFF00693D) // tolled expressway signs, Go
    val Blue = Color(0xFF0D4C9F) // federal / town road signs
    val Shield = Color(0xFFF6C400) // route number shield
    val ShieldInk = Color(0xFF111111)
    val LimitRing = Color(0xFFD2152A) // speed limit roundel
    val Maroon = Color(0xFF7A1F2C) // tourist signs
}

/** Traffic and alerts: only ever painted on the road or as a warning. */
object Traffic {
    val Slow = Color(0xFFE88600)
    val Jam = Color(0xFFD2283E)
    val Report = Color(0xFFF2A900)
}

/** The app's neutrals and route colour, one set per theme. */
@Immutable
data class JalanColors(
    val sheet: Color,
    val card: Color,
    val field: Color,
    val ink: Color,
    val inkQuiet: Color,
    val onInk: Color,
    val line: Color,
    val asphalt: Color,
    val paint: Color,
    val route: Color,
    val laneStrip: Color,
    val isNight: Boolean,
)

val DayColors =
    JalanColors(
        sheet = Color(0xFFF6F5F1),
        card = Color(0xFFFFFFFF),
        field = Color(0xFFECEAE4),
        ink = Color(0xFF15181C),
        inkQuiet = Color(0xFF5A5F66),
        onInk = Color(0xFFFFFFFF),
        line = Color(0xFFE1DED6),
        asphalt = Color(0xFF4C5057),
        paint = Color(0xFFF7F7F2),
        route = Color(0xFF2F7BFF),
        laneStrip = Color(0xFF0C0F12),
        isNight = false,
    )

val NightColors =
    JalanColors(
        sheet = Color(0xFF16191D),
        card = Color(0xFF1F2328),
        field = Color(0xFF2A2E34),
        ink = Color(0xFFECEEF0),
        inkQuiet = Color(0xFF9AA0A8),
        onInk = Color(0xFF15181C),
        line = Color(0xFF2C3036),
        asphalt = Color(0xFF3A3E45),
        paint = Color(0xFFC9CCD1),
        route = Color(0xFF3D8BFF),
        laneStrip = Color(0xFF0C0F12),
        isNight = true,
    )

val LocalJalanColors = staticCompositionLocalOf { DayColors }
