package com.excellentmindblow.navos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.excellentmindblow.navos.R

/** Overpass: the free face based on the highway sign alphabet. */
val Overpass =
    FontFamily(
        Font(R.font.overpass_regular, FontWeight.Normal),
        Font(R.font.overpass_bold, FontWeight.Bold),
        Font(R.font.overpass_extrabold, FontWeight.ExtraBold),
    )

/** Numbers the driver reads (distance, ETA, speed): tabular, so they don't jump while counting down. */
private val tnum = "tnum"

/**
 * Type scale. Sign / Display for the big glanceable numbers, Title for
 * places and roads, Body for everything else, Label for chips and buttons.
 */
object JalanType {
    val signDistance = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.ExtraBold, fontSize = 44.sp, lineHeight = 44.sp, fontFeatureSettings = tnum, letterSpacing = (-0.01).em)
    val signRoad = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp, lineHeight = 22.sp, letterSpacing = 0.02.em)
    val eta = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.ExtraBold, fontSize = 36.sp, lineHeight = 38.sp, fontFeatureSettings = tnum, letterSpacing = (-0.01).em)
    val cardTime = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 30.sp, fontFeatureSettings = tnum)
    val title = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp)
    val bodyStrong = body.copy(fontWeight = FontWeight.Bold)
    val label = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp, fontFeatureSettings = tnum)
    val overline = TextStyle(fontFamily = Overpass, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.12.em)
}

val JalanTypography =
    Typography(
        displayLarge = JalanType.signDistance,
        headlineMedium = JalanType.eta,
        titleLarge = JalanType.title,
        titleMedium = JalanType.title.copy(fontSize = 16.sp),
        bodyLarge = JalanType.body,
        bodyMedium = JalanType.body.copy(fontSize = 15.sp),
        bodySmall = JalanType.caption,
        labelLarge = JalanType.label.copy(fontSize = 16.sp),
        labelMedium = JalanType.label,
        labelSmall = JalanType.overline,
    )
