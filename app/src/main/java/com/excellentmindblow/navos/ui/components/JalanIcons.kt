package com.excellentmindblow.navos.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Icons the core Material set lacks, drawn on a 24 grid with a 2 px round stroke (Lucide style). */
object JalanIcons {
    private fun icon(
        name: String,
        block: ImageVector.Builder.() -> Unit,
    ): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    private fun ImageVector.Builder.stroke(d: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
        path(stroke = SolidColor(Color.Black), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = d)

    /** Work: a briefcase. */
    val Work =
        icon("work") {
            stroke {
                moveTo(4f, 7f); lineTo(20f, 7f); lineTo(20f, 20f); lineTo(4f, 20f); close()
                moveTo(9f, 7f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 7f)
                moveTo(4f, 13f); lineTo(20f, 13f)
            }
        }

    /** Recent: a clock with a back arrow. */
    val History =
        icon("history") {
            stroke {
                moveTo(3f, 12f); arcTo(9f, 9f, 0f, true, true, 5.6f, 5.6f)
                moveTo(3f, 4f); lineTo(3f, 9f); lineTo(8f, 9f)
                moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15f, 14f)
            }
        }

    /** Petrol pump. */
    val Fuel =
        icon("fuel") {
            stroke {
                moveTo(4f, 21f); lineTo(4f, 5f); arcTo(2f, 2f, 0f, false, true, 6f, 3f); lineTo(12f, 3f); arcTo(2f, 2f, 0f, false, true, 14f, 5f); lineTo(14f, 21f)
                moveTo(3f, 21f); lineTo(15f, 21f)
                moveTo(4f, 10f); lineTo(14f, 10f)
                moveTo(14f, 8f); lineTo(17f, 8f); lineTo(19f, 10f); lineTo(19f, 17f); arcTo(1.5f, 1.5f, 0f, false, true, 16f, 17f); lineTo(16f, 14f); lineTo(14f, 14f)
            }
        }

    /** EV charging: a bolt. */
    val Bolt =
        icon("bolt") {
            stroke { moveTo(13f, 2f); lineTo(4f, 14f); lineTo(12f, 14f); lineTo(11f, 22f); lineTo(20f, 10f); lineTo(12f, 10f); close() }
        }

    /** Parking: P in a rounded square. */
    val Parking =
        icon("parking") {
            stroke {
                moveTo(5f, 3f); lineTo(19f, 3f); arcTo(2f, 2f, 0f, false, true, 21f, 5f); lineTo(21f, 19f); arcTo(2f, 2f, 0f, false, true, 19f, 21f)
                lineTo(5f, 21f); arcTo(2f, 2f, 0f, false, true, 3f, 19f); lineTo(3f, 5f); arcTo(2f, 2f, 0f, false, true, 5f, 3f); close()
                moveTo(9f, 17f); lineTo(9f, 7f); lineTo(13f, 7f); arcTo(3f, 3f, 0f, false, true, 13f, 13f); lineTo(9f, 13f)
            }
        }

    /** Where to: a map pin. */
    val Pin =
        icon("pin") {
            stroke {
                moveTo(12f, 21f); curveTo(12f, 21f, 5f, 14.8f, 5f, 9.5f); arcTo(7f, 7f, 0f, false, true, 19f, 9.5f); curveTo(19f, 14.8f, 12f, 21f, 12f, 21f); close()
                moveTo(14.4f, 9.5f); arcTo(2.4f, 2.4f, 0f, true, true, 9.6f, 9.5f); arcTo(2.4f, 2.4f, 0f, true, true, 14.4f, 9.5f)
            }
        }

    /** Voice on: a speaker with waves. */
    val Volume =
        icon("volume") {
            stroke {
                moveTo(11f, 5f); lineTo(6f, 9f); lineTo(2f, 9f); lineTo(2f, 15f); lineTo(6f, 15f); lineTo(11f, 19f); close()
                moveTo(15.5f, 8.5f); arcTo(5f, 5f, 0f, false, true, 15.5f, 15.5f)
                moveTo(19f, 5f); arcTo(10f, 10f, 0f, false, true, 19f, 19f)
            }
        }

    /** Voice off: a speaker with a cross. */
    val VolumeOff =
        icon("volume_off") {
            stroke {
                moveTo(11f, 5f); lineTo(6f, 9f); lineTo(2f, 9f); lineTo(2f, 15f); lineTo(6f, 15f); lineTo(11f, 19f); close()
                moveTo(22f, 9f); lineTo(16f, 15f)
                moveTo(16f, 9f); lineTo(22f, 15f)
            }
        }

    /** Train: front of a carriage on rails. */
    val Train =
        icon("train") {
            stroke {
                moveTo(7f, 3f); lineTo(17f, 3f); arcTo(2f, 2f, 0f, false, true, 19f, 5f); lineTo(19f, 15f); arcTo(2f, 2f, 0f, false, true, 17f, 17f)
                lineTo(7f, 17f); arcTo(2f, 2f, 0f, false, true, 5f, 15f); lineTo(5f, 5f); arcTo(2f, 2f, 0f, false, true, 7f, 3f); close()
                moveTo(5f, 10f); lineTo(19f, 10f)
                moveTo(9f, 13.5f); lineTo(9.01f, 13.5f)
                moveTo(15f, 13.5f); lineTo(15.01f, 13.5f)
                moveTo(8f, 17f); lineTo(6f, 21f)
                moveTo(16f, 17f); lineTo(18f, 21f)
            }
        }
}
