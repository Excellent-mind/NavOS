package com.excellentmindblow.navos.search

import android.content.Intent
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder

/**
 * Reads a place out of what other apps hand to NavOS: `geo:` links (WhatsApp,
 * Telegram...), `google.navigation:` and `waze:` links, Google Maps / Waze /
 * Apple Maps / OpenStreetMap / Bing / HERE links - long or short - and plain
 * shared text such as an address.
 *
 * Short links (maps.app.goo.gl, goo.gl/maps, g.co/kgs, any other short-link
 * host in shared text) are expanded by following their redirects.
 *
 * Blocking where it touches the network - call [resolve] off the main thread.
 */
object IncomingLocation {
    sealed class Target {
        data class Coords(val latitude: Double, val longitude: Double, val label: String?) : Target()

        data class Query(val text: String) : Target()
    }

    // "lat,lon", optionally "(Label)" after it, optionally ",zoom" (HERE's map=lat,lon,zoom).
    private val COORDS =
        Regex("""^\s*(-?\d{1,3}(?:\.\d+)?)\s*[,;]\s*(-?\d{1,3}(?:\.\d+)?)(?:\s*,\s*-?\d+(?:\.\d+)?[a-z]?)?\s*(?:\((.*)\))?\s*$""")
    private val URL_IN_TEXT = Regex("""https?://[^\s<>"]+""")
    private val PIN = Regex("""!3d(-?\d+\.\d+)!4d(-?\d+\.\d+)""")
    private val AT = Regex("""@(-?\d+\.\d+),(-?\d+\.\d+)""")
    private val BING_POS = Regex("""pos\.(-?\d+(?:\.\d+)?)_(-?\d+(?:\.\d+)?)""")
    private val OSM_MAP = Regex("""map=\d+(?:\.\d+)?/(-?\d+\.\d+)/(-?\d+\.\d+)""")
    private val WAZE_HASH = Regex("""/ul/h([0-9a-zA-Z]{4,12})""")
    private val SHORT_HOSTS = setOf("maps.app.goo.gl", "goo.gl", "g.co", "share.google")

    // Parameters that can hold a coordinate pair, strongest first, and ones that hold a place name.
    private val COORD_KEYS =
        listOf("ll", "to", "coordinate", "destination", "daddr", "q", "query", "sll", "center", "cp", "map", "where1")
    private val TEXT_KEYS = listOf("destination", "daddr", "q", "query", "address", "where1")

    /** The text to interpret (a link or a shared string), or null if the intent carries none. */
    fun rawFrom(intent: Intent): String? =
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            // "NavOS" in the select-text menu of any app (Grab, Lalamove, Shopee...).
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }

    @Throws(Exception::class)
    fun resolve(raw: String): Target? {
        // Shared text may be "Name\nhttps://maps.app.goo.gl/xyz" - use the link when there is one.
        val link = URL_IN_TEXT.find(raw)?.value?.trimEnd('.', ',', ')', ';')
        val text = link ?: raw
        val lower = text.lowercase()

        // App links and plain text first - nothing to download.
        if (!lower.startsWith("http")) {
            if (lower.startsWith("geo:") || lower.startsWith("google.navigation:") || lower.startsWith("waze:")) {
                return parse(text)
            }
            return Target.Query(text)
        }

        // A full link usually carries the place itself.
        parse(text)?.let { return it }

        // Short (or otherwise opaque) link: follow its redirects to the real maps URL.
        val expanded = followRedirects(text)
        return parse(expanded)
    }

    fun parse(text: String): Target? {
        val lower = text.lowercase()
        return when {
            lower.startsWith("geo:") -> parseGeo(text.substring(4))
            lower.startsWith("google.navigation:") -> parseQueryOnly(text.substringAfter(':'))
            lower.startsWith("waze:") -> parseQueryOnly(text.substringAfter('?', ""))
            else -> parseWebLink(text)
        }
    }

    private fun parseGeo(ssp: String): Target? {
        // geo: URIs are "opaque" (no //), so Uri.getQueryParameter would throw - split by hand.
        val path = ssp.substringBefore('?')
        val query = ssp.substringAfter('?', "")
        val q = queryParam(query, "q")
        if (q != null) {
            asCoords(q)?.let { return it }
            if (q.isNotBlank()) return Target.Query(q)
        }
        asCoords(path)?.let { c -> if (!(c.latitude == 0.0 && c.longitude == 0.0)) return c }
        return null
    }

    /** google.navigation:q=... and waze://?ll=...&navigate=yes */
    private fun parseQueryOnly(query: String): Target? = fromParams(query, null)

    private fun parseWebLink(url: String): Target? {
        val noFragment = url.substringBefore('#')
        val fragment = url.substringAfter('#', "")
        val path = noFragment.substringBefore('?')
        val query = noFragment.substringAfter('?', "")

        // Waze share link: /ul/h<geohash> is the place, encoded.
        WAZE_HASH.find(path)?.let { m -> decodeGeohash(m.groupValues[1])?.let { (la, lo) -> return Target.Coords(la, lo, null) } }

        // Google place pin (the actual place, not just where the map is centred).
        PIN.find(url)?.let { return Target.Coords(it.groupValues[1].toDouble(), it.groupValues[2].toDouble(), nameFromPath(path)) }

        // OpenStreetMap: ?mlat=..&mlon=.. or ?route=a,b;c,d
        val mlat = queryParam(query, "mlat")?.toDoubleOrNull()
        val mlon = queryParam(query, "mlon")?.toDoubleOrNull()
        if (mlat != null && mlon != null) return Target.Coords(mlat, mlon, null)
        queryParam(query, "route")?.split(';')?.lastOrNull()?.let { asCoords(it)?.let { c -> return c } }

        // Bing routes: rtp=pos.lat_lon~pos.lat_lon (last = destination)
        queryParam(query, "rtp")?.let { rtp ->
            BING_POS.findAll(rtp).lastOrNull()?.let { return Target.Coords(it.groupValues[1].toDouble(), it.groupValues[2].toDouble(), null) }
        }

        fromParams(query, nameFromPath(path), coordsOnly = true)?.let { return it }

        AT.find(path)?.let { return Target.Coords(it.groupValues[1].toDouble(), it.groupValues[2].toDouble(), nameFromPath(path)) }
        OSM_MAP.find(fragment)?.let { return Target.Coords(it.groupValues[1].toDouble(), it.groupValues[2].toDouble(), null) }

        // Path forms: /maps/place/NAME, /maps/search/NAME, /maps/dir/A/B/C, share.here.com/l/lat,lon
        pathCandidate(path)?.let { cand ->
            asCoords(cand)?.let { return it }
            return Target.Query(cand)
        }

        return fromParams(query, null)
    }

    /** Reads a place from query parameters: a coordinate pair if any key has one, otherwise a text query. */
    private fun fromParams(
        query: String,
        label: String?,
        coordsOnly: Boolean = false,
    ): Target? {
        for (key in COORD_KEYS) {
            val v = queryParam(query, key) ?: continue
            asCoords(v)?.let { return if (it.label == null && label != null) it.copy(label = label) else it }
        }
        if (coordsOnly) return null
        for (key in TEXT_KEYS) {
            val v = queryParam(query, key) ?: continue
            if (v.isNotBlank()) return Target.Query(v)
        }
        return null
    }

    private fun nameFromPath(path: String): String? {
        val marker = "/place/"
        val i = path.indexOf(marker)
        if (i < 0) return null
        val name = decode(path.substring(i + marker.length).substringBefore('/'))
        return name.takeIf { it.isNotBlank() && !it.startsWith("@") && asCoords(it) == null }
    }

    private fun pathCandidate(path: String): String? {
        val segments = path.substringAfter("://").substringAfter('/', "").split('/').filter { it.isNotBlank() }
        val start =
            segments.indexOfFirst { it == "place" || it == "search" || it == "dir" || it == "l" }
        if (start < 0) return null
        return segments
            .drop(start + 1)
            .filter { !it.startsWith("@") && !it.startsWith("data=") }
            .lastOrNull()
            ?.let { decode(it) }
            ?.takeIf { it.isNotBlank() }
    }

    private fun asCoords(value: String): Target.Coords? {
        var v = value.trim()
        // Normalise the prefixes / separators other apps use: "loc:", "ll.", Bing's "lat~lon".
        v = v.removePrefix("loc:").removePrefix("ll.").replace('~', ',')
        val m = COORDS.matchEntire(v) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return Target.Coords(lat, lon, m.groupValues[3].trim().takeIf { it.isNotEmpty() })
    }

    private fun queryParam(
        query: String,
        key: String,
    ): String? =
        query.split('&').firstNotNullOfOrNull { part ->
            val k = part.substringBefore('=')
            if (k.equals(key, ignoreCase = true) && part.contains('=')) decode(part.substringAfter('=')) else null
        }

    private fun decode(s: String): String =
        try {
            URLDecoder.decode(s, "UTF-8")
        } catch (e: Exception) {
            s
        }

    /** Standard geohash -> centre of its cell (Waze's /ul/h... links). */
    private fun decodeGeohash(hash: String): Pair<Double, Double>? {
        val alphabet = "0123456789bcdefghjkmnpqrstuvwxyz"
        var latMin = -90.0
        var latMax = 90.0
        var lonMin = -180.0
        var lonMax = 180.0
        var evenBit = true
        for (c in hash.lowercase()) {
            val idx = alphabet.indexOf(c)
            if (idx < 0) return null
            for (bit in 4 downTo 0) {
                val on = (idx shr bit) and 1 == 1
                if (evenBit) {
                    val mid = (lonMin + lonMax) / 2
                    if (on) lonMin = mid else lonMax = mid
                } else {
                    val mid = (latMin + latMax) / 2
                    if (on) latMin = mid else latMax = mid
                }
                evenBit = !evenBit
            }
        }
        return Pair((latMin + latMax) / 2, (lonMin + lonMax) / 2)
    }

    /** Follows a link's redirects (without downloading the page) to the real URL. */
    private fun followRedirects(start: String): String {
        var current = start
        repeat(8) {
            // Google's cookie-consent hop wraps the real address in ?continue=
            val host = runCatching { URL(current).host.lowercase() }.getOrNull().orEmpty()
            if (host.startsWith("consent.")) {
                queryParam(current.substringAfter('?', ""), "continue")?.let { current = it }
            }
            val conn = URL(current).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) NavOS")
                val code = conn.responseCode
                val location = conn.getHeaderField("Location")
                if (code in 300..399 && location != null) {
                    current = if (location.startsWith("http")) location else URL(URL(current), location).toString()
                } else {
                    return current
                }
            } finally {
                conn.disconnect()
            }
        }
        return current
    }
}
