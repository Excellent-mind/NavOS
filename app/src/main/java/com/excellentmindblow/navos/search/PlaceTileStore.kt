package com.excellentmindblow.navos.search

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Gets [PlaceTiles] tiles for the area around the user and keeps the
 * nearest ones in memory.
 *
 * A tile comes from, in order: memory; the app's own copy (bundled in
 * assets/places/v1, when the build includes it); the disk cache; then
 * [remoteBase] over the network (saved to the disk cache, so search keeps
 * working offline). A tile that doesn't exist (sea, jungle) is remembered
 * as empty. Blocking: call off the main thread.
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

    /** "" = the tile doesn't exist (nothing there); null = couldn't fetch it now. */
    private fun fromNetwork(cell: String): String? {
        val base = remoteBase ?: return null
        val conn = URL("$base/$cell.json.gz").openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 10_000
        return try {
            when (conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val bytes = conn.inputStream.use { it.readBytes() }
                    File(cacheDir, "$cell.json.gz").writeBytes(bytes)
                    gunzip(bytes)
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    File(cacheDir, "$cell.json.gz").writeBytes(ByteArray(0))
                    ""
                }
                else -> null
            }
        } catch (e: IOException) {
            null
        } finally {
            conn.disconnect()
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
