package com.excellentmindblow.navos.poi

import com.excellentmindblow.navos.routing.RoadPoi
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/**
 * Remembers the last results on disk so the icons appear INSTANTLY the next
 * time the map is opened in (or near) the same place, instead of waiting on
 * the network. One small file per ~1 km grid cell; entries older than
 * [MAX_AGE_MS] are ignored (the caller then fetches fresh data and saves it).
 */
object PoiCache {
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    private fun cell(v: Double) = (v * 100).roundToInt()

    private fun file(
        dir: File,
        prefix: String,
        lat: Double,
        lon: Double,
    ) = File(dir, "navos_${prefix}_${cell(lat)}_${cell(lon)}.json")

    private fun readFresh(f: File): JSONArray? {
        if (!f.exists()) return null
        return try {
            val root = JSONObject(f.readText())
            if (System.currentTimeMillis() - root.optLong("savedAt") > MAX_AGE_MS) null else root.getJSONArray("items")
        } catch (e: Exception) {
            null
        }
    }

    private fun write(
        f: File,
        items: JSONArray,
    ) {
        try {
            f.writeText(JSONObject().put("savedAt", System.currentTimeMillis()).put("items", items).toString())
        } catch (e: Exception) {
            // cache is best-effort
        }
    }

    // ---- toll plazas / speed cameras
    fun loadRoad(
        dir: File,
        lat: Double,
        lon: Double,
    ): List<RoadPoi>? {
        val arr = readFresh(file(dir, "road", lat, lon)) ?: return null
        val out = ArrayList<RoadPoi>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                RoadPoi(
                    kind = o.getString("kind"),
                    latitude = o.getDouble("lat"),
                    longitude = o.getDouble("lon"),
                    name = o.optString("name"),
                    maxspeed = o.optString("maxspeed").ifBlank { null },
                ),
            )
        }
        return out
    }

    fun saveRoad(
        dir: File,
        lat: Double,
        lon: Double,
        items: List<RoadPoi>,
    ) {
        val arr = JSONArray()
        for (p in items) {
            arr.put(
                JSONObject()
                    .put("kind", p.kind).put("lat", p.latitude).put("lon", p.longitude)
                    .put("name", p.name).put("maxspeed", p.maxspeed ?: ""),
            )
        }
        write(file(dir, "road", lat, lon), arr)
    }

    // ---- petrol stations / EV chargers
    fun loadFuelEv(
        dir: File,
        lat: Double,
        lon: Double,
    ): List<Poi>? {
        val arr = readFresh(file(dir, "fuelev", lat, lon)) ?: return null
        val out = ArrayList<Poi>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val facilities = ArrayList<PoiFacility>()
            val fa = o.optJSONArray("facilities") ?: JSONArray()
            for (j in 0 until fa.length()) {
                val f = fa.getJSONObject(j)
                facilities.add(PoiFacility(f.getString("label"), f.getBoolean("listed")))
            }
            out.add(
                Poi(
                    id = o.getString("id"),
                    kind = PoiKind.valueOf(o.getString("kind")),
                    name = o.optString("name"),
                    latitude = o.getDouble("lat"),
                    longitude = o.getDouble("lon"),
                    facilities = facilities,
                    spec = o.optString("spec").ifBlank { null },
                ),
            )
        }
        return out
    }

    fun saveFuelEv(
        dir: File,
        lat: Double,
        lon: Double,
        items: List<Poi>,
    ) {
        val arr = JSONArray()
        for (p in items) {
            val fa = JSONArray()
            for (f in p.facilities) fa.put(JSONObject().put("label", f.label).put("listed", f.listed))
            arr.put(
                JSONObject()
                    .put("id", p.id).put("kind", p.kind.name).put("name", p.name)
                    .put("lat", p.latitude).put("lon", p.longitude)
                    .put("facilities", fa).put("spec", p.spec ?: ""),
            )
        }
        write(file(dir, "fuelev", lat, lon), arr)
    }
}
