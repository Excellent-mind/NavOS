package com.excellentmindblow.navos

import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer

/**
 * Country names NavOS shows differently from the map tiles. The tiles
 * (OpenFreeMap, OpenMapTiles schema) label countries from their own name
 * fields; after each style load we wrap every place-label layer's text so a
 * country with one of these ISO codes shows NavOS's name instead (in place
 * of both the Latin and the local-script line).
 */
object MapLabels {
    /** ISO 3166-1 alpha-2 code -> the name NavOS shows. */
    val COUNTRY_NAMES = mapOf("IL" to "Palestine")

    /** Rewrites the country labels of a freshly loaded [style]. */
    fun apply(style: Style) {
        for (layer in style.layers) {
            if (layer !is SymbolLayer || layer.sourceLayer != "place") continue
            val original = layer.textField.expression ?: continue
            layer.setProperties(PropertyFactory.textField(relabel(original)))
        }
    }

    /** [original] with each renamed country swapped in; every other label unchanged. */
    fun relabel(original: Expression): Expression {
        var out = original
        for ((iso, name) in COUNTRY_NAMES) {
            out =
                Expression.switchCase(
                    Expression.all(
                        Expression.eq(Expression.get("class"), Expression.literal("country")),
                        Expression.eq(Expression.get("iso_a2"), Expression.literal(iso)),
                    ),
                    Expression.literal(name),
                    out,
                )
        }
        return out
    }
}
