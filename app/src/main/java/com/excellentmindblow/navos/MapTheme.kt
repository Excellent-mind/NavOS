package com.excellentmindblow.navos

import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory

/**
 * Tints OpenFreeMap's light "positron" style to the NavOS app design: a cool
 * light-grey base with white roads (positron's own), soft blue water and a
 * hint of green in parks and woods. Applied after each style load, like
 * [MapLabels]; the night style (fiord) is already the design's navy and is
 * left as it is. A layer the style doesn't have is skipped, never an error.
 */
object MapTheme {
    private const val BACKGROUND = "#EEF1F5"
    private const val WATER = "#CFE3F7"
    private const val WATERWAY = "#A9CBEF"
    private const val PARK = "#E2EFDD"
    private const val WOOD = "#DCEBD6"
    private const val BUILDING = "#E4E7EC"

    fun apply(style: Style, night: Boolean) {
        if (night) return
        style.getLayer("background")?.setProperties(PropertyFactory.backgroundColor(BACKGROUND))
        (style.getLayer("water") as? FillLayer)?.setProperties(PropertyFactory.fillColor(WATER))
        (style.getLayer("waterway") as? LineLayer)?.setProperties(PropertyFactory.lineColor(WATERWAY))
        (style.getLayer("park") as? FillLayer)?.setProperties(PropertyFactory.fillColor(PARK))
        (style.getLayer("landcover_wood") as? FillLayer)?.setProperties(PropertyFactory.fillColor(WOOD))
        (style.getLayer("building") as? FillLayer)?.setProperties(PropertyFactory.fillColor(BUILDING))
    }
}
