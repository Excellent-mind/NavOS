package com.excellentmindblow.navos

import org.junit.Assert.assertTrue
import org.junit.Test
import org.maplibre.android.style.expressions.Expression

class MapLabelsTest {
    @Test
    fun israelIsLabelledPalestineAndOtherLabelsKeepTheirText() {
        val original = Expression.coalesce(Expression.get("name_en"), Expression.get("name"))
        val json = MapLabels.relabel(original).toString()
        assertTrue(json, json.contains("\"iso_a2\""))
        assertTrue(json, json.contains("\"IL\""))
        assertTrue(json, json.contains("\"Palestine\""))
        assertTrue(json, json.contains("\"country\""))
        // The original text is the fallback for every other place.
        assertTrue(json, json.endsWith(original.toString() + "]"))
    }
}
