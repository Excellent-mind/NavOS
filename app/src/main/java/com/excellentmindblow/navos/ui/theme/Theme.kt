package com.excellentmindblow.navos.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

val JalanShapes =
    Shapes(
        extraSmall = RoundedCornerShape(6.dp), // shields, plates
        small = RoundedCornerShape(12.dp), // chips, small cards
        medium = RoundedCornerShape(16.dp), // cards, signs
        large = RoundedCornerShape(24.dp), // sheets
        extraLarge = RoundedCornerShape(30.dp), // the floating search bar
    )

/** NavOS "Jalan" theme: day or night, Material 3 underneath with the Jalan colours and Overpass. */
@Composable
fun NavOSTheme(
    night: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val c = if (night) NightColors else DayColors
    val scheme =
        if (night) {
            darkColorScheme(
                primary = Jkr.Green, onPrimary = androidx.compose.ui.graphics.Color.White,
                secondary = Jkr.Blue, background = c.sheet, surface = c.sheet, onSurface = c.ink, onBackground = c.ink,
                surfaceVariant = c.field, onSurfaceVariant = c.inkQuiet, outline = c.line, error = Traffic.Jam,
            )
        } else {
            lightColorScheme(
                primary = Jkr.Green, onPrimary = androidx.compose.ui.graphics.Color.White,
                secondary = Jkr.Blue, background = c.sheet, surface = c.sheet, onSurface = c.ink, onBackground = c.ink,
                surfaceVariant = c.field, onSurfaceVariant = c.inkQuiet, outline = c.line, error = Traffic.Jam,
            )
        }
    CompositionLocalProvider(LocalJalanColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = JalanTypography, shapes = JalanShapes, content = content)
    }
}

/** The Jalan colours of the current theme. */
object Jalan {
    val colors: JalanColors
        @Composable @ReadOnlyComposable
        get() = LocalJalanColors.current
}
