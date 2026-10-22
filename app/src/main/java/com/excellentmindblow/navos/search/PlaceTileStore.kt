package com.excellentmindblow.navos.search

import android.content.Context
import android.util.JsonReader
import android.util.JsonWriter
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Gets [PlaceTiles] tiles for the area around the user and keeps the
 * nearest ones in memory.
 *
 * A tile comes from, in order: memory; the app's own copy (bundled in
 * assets/places/v1, when the build includes it); the disk cache; then
 * [remoteBase] over the network - a half-degree pack of 5 x 5 tiles, split
 * into the disk cache so search keeps working offline. A tile that doesn't
 * exist (sea, jungle) is remembered as empty. Blocking: call off the main thread.
 */
class PlaceTileStore(
    context: Context,
    private val remoteBase: String? = null,
) {
    private val assets = context.applicationContext.assets
    private val cacheDir = File(context.cacheDir, "places/v1").apply { mkdirs() }
    private val memory = object : LinkedHashMap<String, List<PlaceTiles.Place>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<PlaceTiles.Place>>?) = size > MAX_TILES_IN_MEMORY
    }

    /** Places in the tiles around ([lat], [lon]); tiles that fail to load are skipped. */
    fun near(
        lat: Double,
        lon: Double,
    ): List<PlaceTiles.Place> {
        val out = ArrayList<PlaceTiles.Place>()
        for (cell in PlaceTiles.cellsAround(lat, lon)) out += tile(cell) ?: continue
        return out
    }

    @Synchronized
    private fun tile(cell: String): List<PlaceTiles.Place>? {
        memory[cell]?.let { return it }
        val json = fromAssets(cell) ?: fromDisk(cell) ?: fromNetwork(cell) ?: return null
        val places = if (json.isEmpty()) emptyList() else PlaceTiles.parse(json)
        memory[cell] = places
        return places
    }

    private fun fromAssets(cell: String): String? =
        try {
            assets.open("places/v1/$cell.json.gz").use { gunzip(it.readBytes()) }
        } catch (e: IOException) {
            null
        }

    private fun fromDisk(cell: String): String? {
        val f = File(cacheDir, "$cell.json.gz")
        if (!f.exists()) return null
        if (System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS) return null
        return if (f.length() == 0L) "" else runCatching { gunzip(f.readBytes()) }.getOrNull()
    }

    /**
     * Downloads the half-degree pack holding [cell] (5 x 5 tiles in one file,
     * see tools/build_place_tiles.py) and splits it into one cache file per
     * tile, streaming so even the 100,000-place KL pack never sits in memory
     * whole. Tiles the pack doesn't list are saved as empty (nothing there).
     * "" = the tile doesn't exist; null = couldn't fetch it now.
     */
    private fun fromNetwork(cell: String): String? {
        val base = remoteBase ?: return null
        val pack = PlaceTiles.packOf(cell)
        val conn = URL("$base/$pack.json.gz").openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 30_000
        try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> conn.inputStream.use { splitPack(GZIPInputStream(it.buffered())) }
                HttpURLConnection.HTTP_NOT_FOUND -> {}
                else -> return null
            }
            for (c in PlaceTiles.cellsInPack(pack)) {
                val f = File(cacheDir, "$c.json.gz")
                if (!f.exists() || System.currentTimeMillis() - f.lastModified() > MAX_AGE_MS) f.writeBytes(ByteArray(0))
            }
        } catch (e: IOException) {
            return null
        } finally {
            conn.disconnect()
        }
        return fromDisk(cell)
    }

    /** {"cells":{"32_1017":[[...],...],...}} -> one {cell}.json.gz per tile, in the tile format. */
    private fun splitPack(input: java.io.InputStream) {
        JsonReader(InputStreamReader(input, Charsets.UTF_8)).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                if (r.nextName() != "cells") {
                    r.skipValue()
                    continue
                }
                r.beginObject()
                while (r.hasNext()) {
                    val cell = r.nextName()
                    val tmp = File(cacheDir, "$cell.json.gz.part")
                    JsonWriter(OutputStreamWriter(GZIPOutputStream(tmp.outputStream().buffered()), Charsets.UTF_8)).use { w ->
                        w.beginObject().name("v").value(1).name("cell").value(cell).name("places").beginArray()
                        r.beginArray()
                        while (r.hasNext()) {
                            r.beginArray()
                            w.beginArray()
                            w.value(r.nextString()).value(r.nextString()).value(r.nextDouble()).value(r.nextDouble()).value(r.nextString())
                            while (r.hasNext()) r.skipValue()
                            w.endArray()
                            r.endArray()
                        }
                        r.endArray()
                        w.endArray().endObject()
                    }
                    tmp.renameTo(File(cacheDir, "$cell.json.gz"))
                }
                r.endObject()
            }
            r.endObject()
        }
    }

    private fun gunzip(bytes: ByteArray): String = GZIPInputStream(bytes.inputStream()).use { it.readBytes().toString(Charsets.UTF_8) }

    companion object {
        /** 3 x 3 around you plus the last few areas searched. */
        const val MAX_TILES_IN_MEMORY = 18

        /** Re-fetch a cached tile after a month (Overture updates monthly). */
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
